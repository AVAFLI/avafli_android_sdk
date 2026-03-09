package com.avafli.winrsdk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avafli.winrsdk.domain.Campaign
import com.avafli.winrsdk.domain.DailyEntryGrant
import com.avafli.winrsdk.domain.Milestone
import com.avafli.winrsdk.domain.StreakEngine
import com.avafli.winrsdk.domain.StreakState
import com.avafli.winrsdk.network.WinrApi
import com.avafli.winrsdk.services.Logger
import com.avafli.winrsdk.storage.PreferencesStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.TimeZone

// ── State machine (mirrors iOS WINRExperienceViewModel.State) ──

internal sealed class ExperienceScreen {
    object Loading : ExperienceScreen()
    object EmailCapture : ExperienceScreen()
    data class Streak(
        val streakState: StreakState,
        val entriesToday: Int,
        val ladder: List<Int>
    ) : ExperienceScreen()
    data class Bonus(val grant: DailyEntryGrant) : ExperienceScreen()
    data class MilestoneCelebration(
        val milestone: Milestone,
        val grant: DailyEntryGrant
    ) : ExperienceScreen()
    data class Completed(val grant: DailyEntryGrant) : ExperienceScreen()
    object HowItWorks : ExperienceScreen()
    data class Error(val message: String) : ExperienceScreen()
}

internal data class ExperienceUiState(
    val screen: ExperienceScreen = ExperienceScreen.Loading,
    val claimedToday: Boolean = false,
    val campaign: Campaign? = null,
    // Legacy flat fields kept for backward compat
    val isLoading: Boolean = true,
    val streakState: StreakState = StreakState(),
    val isClaiming: Boolean = false,
    val hasClaimed: Boolean = false,
    val entriesEarned: Int = 0,
    val totalEntries: Int = 0,
    val canDouble: Boolean = false,
    val isDoubled: Boolean = false,
    val isSubmittingEmail: Boolean = false,
    val emailSubmitted: Boolean = false,
    val error: String? = null,
    val lastGrant: DailyEntryGrant? = null
)

internal class WINRExperienceViewModel(
    private val api: WinrApi,
    private val preferencesStorage: PreferencesStorage,
    private val logger: Logger
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExperienceUiState())
    val uiState: StateFlow<ExperienceUiState> = _uiState.asStateFlow()

    private var streakEngine: StreakEngine? = null
    private var onResult: ((Result<DailyEntryGrant>) -> Unit)? = null

    /** Saved primary screen so "How It Works" can navigate back. */
    private var lastPrimaryScreen: ExperienceScreen? = null

    fun setResultCallback(callback: ((Result<DailyEntryGrant>) -> Unit)?) {
        onResult = callback
    }

    // ── Load ──

    fun loadCampaign(existingCampaign: Campaign?) {
        viewModelScope.launch {
            try {
                val campaign = existingCampaign ?: api.getActiveCampaign()
                streakEngine = StreakEngine(campaign)

                val savedState = loadStreakState()
                streakEngine?.setState(savedState)
                streakEngine?.checkStreakReset()

                val state = streakEngine?.getState() ?: StreakState()
                val alreadyClaimed = streakEngine?.hasClaimedToday() ?: false
                val emailDone = preferencesStorage.isEmailSubmitted()

                if (!emailDone) {
                    _uiState.value = _uiState.value.copy(
                        screen = ExperienceScreen.EmailCapture,
                        isLoading = false,
                        campaign = campaign,
                        streakState = state,
                        hasClaimed = alreadyClaimed,
                        claimedToday = alreadyClaimed,
                        totalEntries = state.totalEntries,
                        emailSubmitted = false
                    )
                    return@launch
                }

                moveToStreakDashboard(campaign, state, alreadyClaimed)
            } catch (e: Exception) {
                logger.error("Failed to load campaign: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    screen = ExperienceScreen.Error(e.message ?: "Failed to load campaign"),
                    isLoading = false,
                    error = e.message ?: "Failed to load campaign"
                )
            }
        }
    }

    private fun moveToStreakDashboard(campaign: Campaign, state: StreakState, claimed: Boolean) {
        val ladder = campaign.streakLadder.map { it * campaign.maxDailyBaseEntries }
        val dayIndex = (state.currentDay.coerceAtLeast(1) - 1).coerceIn(0, ladder.size - 1)
        val entriesToday = ladder[dayIndex]

        _uiState.value = _uiState.value.copy(
            screen = ExperienceScreen.Streak(state, entriesToday, ladder),
            isLoading = false,
            campaign = campaign,
            streakState = state,
            hasClaimed = claimed,
            claimedToday = claimed,
            totalEntries = state.totalEntries,
            canDouble = campaign.doublingEnabled && !claimed,
            emailSubmitted = true
        )
    }

    // ── Email ──

    fun submitEmail(email: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmittingEmail = true, error = null)
            try {
                val success = api.submitEmail(email)
                if (success) {
                    preferencesStorage.saveEmailSubmitted(true)
                    _uiState.value = _uiState.value.copy(
                        isSubmittingEmail = false,
                        emailSubmitted = true
                    )
                    // Advance to streak dashboard
                    val campaign = _uiState.value.campaign ?: return@launch
                    val state = streakEngine?.getState() ?: StreakState()
                    val claimed = streakEngine?.hasClaimedToday() ?: false
                    moveToStreakDashboard(campaign, state, claimed)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSubmittingEmail = false,
                        error = "Failed to submit email"
                    )
                }
            } catch (e: Exception) {
                logger.error("Failed to submit email: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isSubmittingEmail = false,
                    error = e.message ?: "Failed to submit email"
                )
            }
        }
    }

    fun skipEmailCapture() {
        // Email is required — stay on email capture (matches iOS)
        _uiState.value = _uiState.value.copy(screen = ExperienceScreen.EmailCapture)
    }

    // ── How It Works ──

    fun showHowItWorks() {
        lastPrimaryScreen = _uiState.value.screen
        _uiState.value = _uiState.value.copy(screen = ExperienceScreen.HowItWorks)
    }

    fun hideHowItWorks() {
        val prev = lastPrimaryScreen
        if (prev != null) {
            _uiState.value = _uiState.value.copy(screen = prev)
        } else {
            loadCampaign(_uiState.value.campaign)
        }
    }

    fun primaryFromHowItWorks() {
        hideHowItWorks()
    }

    // ── Claim ──

    fun claimDailyEntries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isClaiming = true, error = null)
            try {
                val timezone = TimeZone.getDefault().id
                val response = api.claimDailyEntries(timezone)

                val grant = streakEngine?.processDailyClaim(
                    entries = response.entries,
                    streakDay = response.streakDay,
                    totalEntries = response.totalEntries,
                    weeklyBonusEntries = response.weeklyBonusEntries,
                    monthlyBonusEntries = response.monthlyBonusEntries,
                    milestone = response.milestone
                ) ?: DailyEntryGrant(
                    entries = response.entries,
                    streakDay = response.streakDay,
                    totalEntries = response.totalEntries
                )

                val state = streakEngine?.getState() ?: StreakState()
                saveStreakState(state)

                val campaign = _uiState.value.campaign

                // Check milestone → bonus → complete
                val nextScreen: ExperienceScreen = when {
                    response.milestone != null ->
                        ExperienceScreen.MilestoneCelebration(response.milestone, grant)
                    campaign?.doublingEnabled == true ->
                        ExperienceScreen.Bonus(grant)
                    else ->
                        ExperienceScreen.Completed(grant)
                }

                _uiState.value = _uiState.value.copy(
                    screen = nextScreen,
                    isClaiming = false,
                    hasClaimed = true,
                    claimedToday = true,
                    entriesEarned = grant.entries,
                    totalEntries = grant.totalEntries,
                    streakState = state,
                    canDouble = campaign?.doublingEnabled == true,
                    lastGrant = grant
                )

                onResult?.invoke(Result.success(grant))
            } catch (e: Exception) {
                logger.error("Failed to claim entries: ${e.message}", e)

                if (e.message?.contains("Already claimed") == true) {
                    // Treat as success — update UI
                    val state = streakEngine?.getState() ?: StreakState()
                    val campaign = _uiState.value.campaign
                    val ladder = campaign?.streakLadder?.map {
                        it * (campaign.maxDailyBaseEntries)
                    } ?: emptyList()
                    val dayIndex = (state.currentDay.coerceAtLeast(1) - 1)
                        .coerceIn(0, ladder.size.coerceAtLeast(1) - 1)
                    val entriesToday = ladder.getOrElse(dayIndex) { 1 }

                    _uiState.value = _uiState.value.copy(
                        screen = ExperienceScreen.Streak(state, entriesToday, ladder),
                        isClaiming = false,
                        hasClaimed = true,
                        claimedToday = true
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    isClaiming = false,
                    error = e.message ?: "Failed to claim entries"
                )
                onResult?.invoke(Result.failure(e))
            }
        }
    }

    // ── Bonus (rewarded ad) ──

    fun watchAdAndDouble() {
        viewModelScope.launch {
            try {
                val timezone = TimeZone.getDefault().id
                val response = api.claimBonusEntries(timezone)

                val currentGrant = _uiState.value.lastGrant ?: return@launch
                val doubledGrant = streakEngine?.doubleEntries(currentGrant) ?: currentGrant

                _uiState.value = _uiState.value.copy(
                    screen = ExperienceScreen.Completed(doubledGrant),
                    entriesEarned = doubledGrant.entries,
                    totalEntries = response.totalEntries,
                    canDouble = false,
                    isDoubled = true,
                    lastGrant = doubledGrant
                )
            } catch (e: Exception) {
                logger.error("Failed to claim bonus entries: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to double entries"
                )
            }
        }
    }

    fun skipBonus() {
        val grant = (_uiState.value.screen as? ExperienceScreen.Bonus)?.grant ?: return
        _uiState.value = _uiState.value.copy(screen = ExperienceScreen.Completed(grant))
    }

    fun dismissMilestoneCelebration() {
        val screen = _uiState.value.screen as? ExperienceScreen.MilestoneCelebration ?: return
        val campaign = _uiState.value.campaign
        val nextScreen = if (campaign?.doublingEnabled == true) {
            ExperienceScreen.Bonus(screen.grant)
        } else {
            ExperienceScreen.Completed(screen.grant)
        }
        _uiState.value = _uiState.value.copy(screen = nextScreen)
    }

    // ── Helpers ──

    private fun loadStreakState(): StreakState {
        return StreakState(
            currentDay = preferencesStorage.getStreakDay(),
            totalEntries = preferencesStorage.getTotalEntries(),
            weeklyDaysCompleted = preferencesStorage.getWeeklyDaysCompleted(),
            monthlyDaysCompleted = preferencesStorage.getMonthlyDaysCompleted(),
            lastClaimDate = preferencesStorage.getLastClaimDate(),
            completedDays = preferencesStorage.getCompletedDays()
        )
    }

    private fun saveStreakState(state: StreakState) {
        preferencesStorage.saveStreakDay(state.currentDay)
        preferencesStorage.saveTotalEntries(state.totalEntries)
        preferencesStorage.saveWeeklyDaysCompleted(state.weeklyDaysCompleted)
        preferencesStorage.saveMonthlyDaysCompleted(state.monthlyDaysCompleted)
        state.lastClaimDate?.let { preferencesStorage.saveLastClaimDate(it) }
        preferencesStorage.saveCompletedDays(state.completedDays)
    }
}
