package com.avafli.winrsdk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avafli.winrsdk.domain.Campaign
import com.avafli.winrsdk.domain.DailyEntryGrant
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

internal data class ExperienceUiState(
    val isLoading: Boolean = true,
    val campaign: Campaign? = null,
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

    fun setResultCallback(callback: ((Result<DailyEntryGrant>) -> Unit)?) {
        onResult = callback
    }

    fun loadCampaign(existingCampaign: Campaign?) {
        viewModelScope.launch {
            try {
                val campaign = existingCampaign ?: api.getActiveCampaign()
                streakEngine = StreakEngine(campaign)

                // Restore state from preferences
                val savedState = loadStreakState()
                streakEngine?.setState(savedState)
                streakEngine?.checkStreakReset()

                val state = streakEngine?.getState() ?: StreakState()
                val alreadyClaimed = streakEngine?.hasClaimedToday() ?: false

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    campaign = campaign,
                    streakState = state,
                    hasClaimed = alreadyClaimed,
                    totalEntries = state.totalEntries,
                    canDouble = campaign.doublingEnabled && !alreadyClaimed,
                    emailSubmitted = preferencesStorage.isEmailSubmitted()
                )
            } catch (e: Exception) {
                logger.error("Failed to load campaign: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load campaign"
                )
            }
        }
    }

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

                _uiState.value = _uiState.value.copy(
                    isClaiming = false,
                    hasClaimed = true,
                    entriesEarned = grant.entries,
                    totalEntries = grant.totalEntries,
                    streakState = state,
                    canDouble = _uiState.value.campaign?.doublingEnabled == true,
                    lastGrant = grant
                )

                onResult?.invoke(Result.success(grant))
            } catch (e: Exception) {
                logger.error("Failed to claim entries: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isClaiming = false,
                    error = e.message ?: "Failed to claim entries"
                )
                onResult?.invoke(Result.failure(e))
            }
        }
    }

    fun watchAdAndDouble() {
        viewModelScope.launch {
            try {
                val timezone = TimeZone.getDefault().id
                val response = api.claimBonusEntries(timezone)

                val currentGrant = _uiState.value.lastGrant ?: return@launch
                val doubledGrant = streakEngine?.doubleEntries(currentGrant) ?: currentGrant

                _uiState.value = _uiState.value.copy(
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
