package com.avafli.winrsdk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avafli.winrsdk.WINR
import com.avafli.winrsdk.domain.DailyEntryGrant
import com.avafli.winrsdk.domain.Giveaway
import com.avafli.winrsdk.domain.SdkConfig
import com.avafli.winrsdk.domain.StreakEngine
import com.avafli.winrsdk.domain.StreakState
import com.avafli.winrsdk.network.WinrApi
import com.avafli.winrsdk.services.Logger
import com.avafli.winrsdk.services.analytics.AnalyticsAdapter
import com.avafli.winrsdk.storage.PreferencesStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.util.TimeZone

// ── V2 state machine (mirrors iOS WINRExperienceViewModel) ──
//
// loading → emailCapture (until consent) → streak dashboard, with an automatic
// claim on open. Day 1 lands on the celebration modal (dailyConfirmed); Day 2+
// holds yesterday's numbers behind a "CLAIM N ENTRIES" pill on the dashboard
// (the reveal flow). The rewarded-video bonus flow is parked (Phase 1) and has
// been removed.

internal sealed class ExperienceScreen {
    object Loading : ExperienceScreen()
    object NoActiveGiveaway : ExperienceScreen()
    object EmailCapture : ExperienceScreen()
    data class Streak(
        val streakState: StreakState,
        val entriesToday: Int,
        val ladder: List<Int>
    ) : ExperienceScreen()

    /** Celebration modal over the (blurred) dashboard. */
    data class DailyConfirmed(
        val grant: DailyEntryGrant,
        val totalEntries: Int
    ) : ExperienceScreen()

    object HowItWorks : ExperienceScreen()
    data class Error(val message: String) : ExperienceScreen()
}

internal data class ExperienceUiState(
    val screen: ExperienceScreen = ExperienceScreen.Loading,
    val claimedToday: Boolean = false,
    val giveaway: Giveaway? = null,
    val sdkConfig: SdkConfig? = null,
    val isSubmittingEmail: Boolean = false,
    /** Current streak day for display (backend truth, falling back to local). */
    val displayStreakDay: Int = 1,
    val displayTotalEntries: Int = 0,

    // ── V2 reveal flow (Day 2+) — mirrors iOS ──
    //
    // The auto-claim on open grants entries server-side immediately, but the UI
    // holds the previous day's numbers until the user taps "CLAIM N ENTRIES".
    // That tap flips claimRevealed — the day tile checks off with confetti, the
    // streak label and totals advance, and the pill becomes "GOT IT".

    /**
     * The grant held back for the reveal (null when nothing is pending). Its
     * `entries` carries base + all streak bonuses, like the modal grant.
     */
    val pendingRevealGrant: DailyEntryGrant? = null,
    /** Whether the user has tapped CLAIM and seen the in-place celebration. */
    val claimRevealed: Boolean = false,
    /** Total entries as of before today's claim, for pre-reveal display. */
    val preClaimTotalEntries: Int? = null,
)

internal class WINRExperienceViewModel(
    private val api: WinrApi,
    private val preferencesStorage: PreferencesStorage,
    private val logger: Logger,
    private val analytics: AnalyticsAdapter? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExperienceUiState())
    val uiState: StateFlow<ExperienceUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var onResult: ((Result<DailyEntryGrant>) -> Unit)? = null
    private var publisherUserId: String? = null

    private var activeGiveaway: Giveaway? = null
    private var sdkConfig: SdkConfig? = null
    private var cachedBackendClaimedToday: Boolean? = null
    private var cachedBackendStreakDay: Int? = null
    private var cachedBackendTotalEntries: Int? = null
    private var isClaimingDaily = false

    /** One-shot guard for the "already claimed on another device" re-sync. */
    private var didResyncAfterAlreadyClaimed = false

    /** Saved primary screen so "How It Works" can navigate back. */
    private var lastPrimaryScreen: ExperienceScreen? = null

    fun setSdkConfig(config: SdkConfig?) {
        sdkConfig = config
        _uiState.value = _uiState.value.copy(sdkConfig = config)
    }

    fun setResultCallback(callback: ((Result<DailyEntryGrant>) -> Unit)?) {
        onResult = callback
    }

    fun setPublisherUserId(id: String?) {
        publisherUserId = id
    }

    // ── Display helpers ──

    /** The effective reward ladder (giveaway config, else Android defaults). */
    private val displayLadder: List<Int>
        get() = activeGiveaway?.streakLadder?.takeIf { it.isNotEmpty() }
            ?: Giveaway(id = "", title = "", prizeDescription = "").streakLadder

    // ── Load ──

    fun load(cachedGiveaway: Giveaway? = null) {
        viewModelScope.launch { loadInternal(cachedGiveaway) }
    }

    private suspend fun loadInternal(cachedGiveaway: Giveaway? = null) {
        // Always fetch fresh claim status from the backend. The giveaway config
        // may already be cached, but claimedToday can change between opens.
        var backendClaimedToday: Boolean? = null
        var backendStreakDay: Int? = null

        try {
            val response = api.getActiveGiveaway()

            // RTD: an opted-out person never sees the experience content.
            if (response.optedOut == true) {
                setScreen(ExperienceScreen.NoActiveGiveaway)
                return
            }

            if (response.giveaway == null) {
                activeGiveaway = null
                setScreen(ExperienceScreen.NoActiveGiveaway)
                return
            }

            activeGiveaway = response.giveaway
            response.sdkConfig?.let {
                sdkConfig = it
                WINR.updateSdkConfig(it)
            }
            backendClaimedToday = response.claimedToday
            backendStreakDay = response.streakDay
            cachedBackendTotalEntries = response.totalEntries

            // Backend is the source of truth for email consent. If it confirms an
            // email on file, seed the local "submitted" flag so a user whose local
            // flag was lost (e.g. reinstall) isn't re-prompted for email.
            if (response.emailConsentStatus == true) {
                preferencesStorage.saveEmailSubmitted(true)
                WINR.noteEmailConsent()
            }
        } catch (e: Exception) {
            // Offline fallback: use the cached giveaway.
            if (activeGiveaway == null) activeGiveaway = cachedGiveaway
            logger.debug("Using cached giveaway (offline): ${e.message}")
        }

        cachedBackendClaimedToday = backendClaimedToday
        cachedBackendStreakDay = backendStreakDay

        // Email-capture gate: shown until the user completes the consent flow.
        if (!preferencesStorage.isEmailSubmitted()) {
            _uiState.value = _uiState.value.copy(
                screen = ExperienceScreen.EmailCapture,
                giveaway = activeGiveaway,
                sdkConfig = sdkConfig,
            )
            return
        }

        computeStreakAndMoveToDashboard(backendClaimedToday, backendStreakDay)

        // V2 experience: entries are granted automatically when the drawer opens —
        // no tap required. Registered + consented + not-yet-claimed → claim now.
        // Failures are silent (the dashboard just shows the unclaimed state).
        if (_uiState.value.screen is ExperienceScreen.Streak && !_uiState.value.claimedToday) {
            claimDailyEntries(auto = true)
        }
    }

    // ── Dashboard state ──

    private fun computeStreakAndMoveToDashboard(
        backendClaimedToday: Boolean? = null,
        backendStreakDay: Int? = null,
    ) {
        val ladder = displayLadder

        // ─── Backend is source of truth for claim status ───
        if (backendClaimedToday != null) {
            val day = backendStreakDay
                ?: preferencesStorage.getStreakDay().takeIf { it > 0 }
                ?: 1
            val entriesToday = ladder[(day - 1).coerceIn(0, ladder.size - 1)]
            val total = cachedBackendTotalEntries ?: preferencesStorage.getTotalEntries()

            // Sync local state with the backend (seed running totals from the
            // backend so entries claimed on OTHER devices are reflected).
            val today = LocalDate.now().toString()
            val state = StreakState(
                currentDay = day,
                claimedToday = backendClaimedToday,
                totalEntries = total,
                lastClaimDate = if (backendClaimedToday) today else preferencesStorage.getLastClaimDate(),
            )
            saveStreakState(state)

            _uiState.value = _uiState.value.copy(
                screen = ExperienceScreen.Streak(state, entriesToday, ladder),
                claimedToday = backendClaimedToday,
                giveaway = activeGiveaway,
                sdkConfig = sdkConfig,
                displayStreakDay = day,
                displayTotalEntries = total,
            )
            return
        }

        // ─── Offline fallback: local streak engine ───
        val engine = StreakEngine(
            activeGiveaway ?: Giveaway(id = "", title = "", prizeDescription = "")
        )
        engine.setState(loadStreakState())
        engine.checkStreakReset()
        val state = engine.getState()
        val claimed = engine.hasClaimedToday()
        val day = state.currentDay.coerceAtLeast(1)
        val entriesToday = ladder[(day - 1).coerceIn(0, ladder.size - 1)]

        _uiState.value = _uiState.value.copy(
            screen = ExperienceScreen.Streak(state, entriesToday, ladder),
            claimedToday = claimed,
            giveaway = activeGiveaway,
            sdkConfig = sdkConfig,
            displayStreakDay = day,
            displayTotalEntries = state.totalEntries,
        )
    }

    /**
     * V2 reveal (Day 2+): the "CLAIM N ENTRIES" tap. The claim already succeeded
     * server-side on open; this only flips the UI into the celebration state.
     */
    fun revealClaim() {
        val ui = _uiState.value
        if (ui.pendingRevealGrant == null || ui.claimRevealed) return
        _uiState.value = ui.copy(claimRevealed = true)
    }

    // ── Email capture ──

    fun submitEmail(email: String, marketingConsent: Boolean = true) {
        if (email.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmittingEmail = true)

            // NOTE: we deliberately do NOT persist the raw email locally (PII-High).
            // Registration state is the non-PII "email submitted" flag.
            preferencesStorage.saveEmailSubmitted(true)

            try {
                // Cross-device streak unification: if this email already belonged to
                // an existing user under this publisher, WinrApi switches to the
                // canonical user's credentials internally (adopted == true).
                api.submitEmail(email, marketingConsent, publisherUserId)
                WINR.noteEmailConsent()
                logger.debug("Email submitted to backend")
            } catch (e: Exception) {
                logger.error("Email submit to backend failed (will retry later): ${e.message}", e)
            }

            _uiState.value = _uiState.value.copy(isSubmittingEmail = false)

            // Re-load so the (possibly switched) canonical user's authoritative
            // streak + claim status drive the UI.
            loadInternal()
        }
    }

    // ── How It Works ──

    fun showHowItWorks() {
        lastPrimaryScreen = _uiState.value.screen
        setScreen(ExperienceScreen.HowItWorks)
    }

    fun hideHowItWorks() {
        val previous = lastPrimaryScreen
        if (previous != null) {
            setScreen(previous)
        } else {
            setScreen(ExperienceScreen.Loading)
            load()
        }
    }

    // ── Daily claim (V2: automatic on open) ──

    fun claimDailyEntries(auto: Boolean = false) {
        val screen = _uiState.value.screen as? ExperienceScreen.Streak ?: return
        if (isClaimingDaily) return
        isClaimingDaily = true

        viewModelScope.launch {
            try {
                val response = api.claimDailyEntries(TimeZone.getDefault().id)

                // Bonus entries from all sources (weekly/monthly bonuses, milestones).
                val streakBonusEntries = (response.weeklyBonusEntries ?: 0) +
                    (response.monthlyBonusEntries ?: 0) +
                    (response.milestone?.bonusEntries ?: 0) +
                    (response.monthlyMilestone?.bonusEntries ?: 0)

                val grant = DailyEntryGrant(
                    entries = response.entries,
                    streakDay = response.streakDay,
                    totalEntries = response.totalEntries,
                    weeklyBonusEntries = response.weeklyBonusEntries,
                    monthlyBonusEntries = response.monthlyBonusEntries,
                    milestone = response.milestone,
                )

                // Keep the display caches in sync so the post-celebration dashboard
                // shows the fresh totals (not the pre-claim snapshot).
                cachedBackendTotalEntries = response.totalEntries
                cachedBackendStreakDay = response.streakDay
                cachedBackendClaimedToday = true

                val updatedStreak = StreakState(
                    currentDay = response.streakDay,
                    claimedToday = true,
                    totalEntries = response.totalEntries,
                    lastClaimDate = LocalDate.now().toString(),
                )
                saveStreakState(updatedStreak)

                // The grant's entries carry base + streak bonuses so the
                // celebration ("YOU EARNED N" / "CLAIM N ENTRIES") is complete.
                val grantEntries = response.entries + streakBonusEntries

                // V2 auto-claim routing (mirrors iOS):
                // - Day 1 (brand-new or restarted streak, typically right after
                //   email capture): the "You're in!" celebration modal is the
                //   reveal.
                // - Day 2+: no modal. Land on the dashboard pinned to yesterday's
                //   numbers with a "CLAIM N ENTRIES" pill; the tap reveals the
                //   celebration in place (Joe's Slice Day 2+ flow).
                if (response.streakDay <= 1) {
                    _uiState.value = _uiState.value.copy(
                        screen = ExperienceScreen.DailyConfirmed(
                            grant = grant.copy(entries = grantEntries),
                            totalEntries = response.totalEntries,
                        ),
                        claimedToday = true,
                        displayStreakDay = response.streakDay,
                        displayTotalEntries = response.totalEntries,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        screen = ExperienceScreen.Streak(updatedStreak, response.entries, displayLadder),
                        claimedToday = true,
                        displayStreakDay = response.streakDay,
                        displayTotalEntries = response.totalEntries,
                        pendingRevealGrant = grant.copy(entries = grantEntries),
                        claimRevealed = false,
                        preClaimTotalEntries = response.totalEntries - grantEntries,
                    )
                }
                isClaimingDaily = false

                analytics?.trackEvent(
                    "winr_daily_entry_claimed",
                    buildMap {
                        put("day", response.streakDay)
                        put("entries", response.entries)
                        response.weeklyBonusEntries?.let { put("weekly_bonus", it) }
                        response.monthlyBonusEntries?.let { put("monthly_bonus", it) }
                        response.milestone?.let { put("milestone_day", it.day) }
                    }
                )
                onResult?.invoke(Result.success(grant))
            } catch (e: Exception) {
                isClaimingDaily = false
                handleClaimFailure(e, screen, auto)
            }
        }
    }

    private suspend fun handleClaimFailure(
        e: Exception,
        screen: ExperienceScreen.Streak,
        auto: Boolean,
    ) {
        // "Already claimed" means the user already got their entries today —
        // show the dashboard claimed state silently.
        if (isAlreadyClaimedError(e)) {
            logger.debug("Already claimed today — updating local state")
            val updated = screen.streakState.copy(
                claimedToday = true,
                lastClaimDate = LocalDate.now().toString(),
            )
            saveStreakState(updated)
            _uiState.value = _uiState.value.copy(
                screen = ExperienceScreen.Streak(updated, screen.entriesToday, screen.ladder),
                claimedToday = true,
            )
            // Another device beat us between the status fetch and the claim, so
            // our cached totals are one claim behind — re-load once to pull the
            // authoritative streak/total. One-shot: never loop if status + claim
            // keep disagreeing.
            if (auto && !didResyncAfterAlreadyClaimed) {
                didResyncAfterAlreadyClaimed = true
                loadInternal()
            }
            return
        }

        // Auto-claim failures are SILENT by design: the dashboard simply shows
        // the unclaimed state. Never fake a local success for an auto-claim.
        if (auto) {
            logger.debug("Auto-claim declined: ${e.message}")
            _uiState.value = _uiState.value.copy(
                screen = ExperienceScreen.Streak(screen.streakState, screen.entriesToday, screen.ladder),
                claimedToday = false,
            )
            return
        }

        logger.error("Failed to claim entries: ${e.message}", e)
        setScreen(ExperienceScreen.Error(e.message ?: "Failed to claim entries"))
        onResult?.invoke(Result.failure(e))
    }

    /**
     * Determine whether [e] represents an "already claimed today" rejection.
     * Prefers the server's structured error code/type (parsed from the ServerError
     * body) and falls back to message-substring matching only as a last resort.
     */
    private fun isAlreadyClaimedError(e: Throwable): Boolean {
        if (e is com.avafli.winrsdk.WINRError.AlreadyClaimed) return true

        if (e is com.avafli.winrsdk.WINRError.ServerError) {
            val structured = try {
                val body = e.message ?: ""
                val start = body.indexOf('{')
                if (start >= 0) {
                    val obj = json.parseToJsonElement(body.substring(start)).jsonObject
                    val code = (obj["error"]?.jsonObject?.get("status")
                        ?: obj["error"]?.jsonObject?.get("code")
                        ?: obj["code"]
                        ?: obj["status"])?.jsonPrimitive?.contentOrNull
                    code?.uppercase()?.let {
                        it == "ALREADY_CLAIMED" || it == "FAILED_PRECONDITION"
                    } ?: false
                } else false
            } catch (_: Exception) {
                false
            }
            if (structured) return true
        }

        return e.message?.contains("Already claimed", ignoreCase = true) == true
    }

    // ── Helpers ──

    private fun setScreen(screen: ExperienceScreen) {
        _uiState.value = _uiState.value.copy(
            screen = screen,
            giveaway = activeGiveaway ?: _uiState.value.giveaway,
            sdkConfig = sdkConfig ?: _uiState.value.sdkConfig,
        )
    }

    private fun loadStreakState(): StreakState = StreakState(
        currentDay = preferencesStorage.getStreakDay(),
        totalEntries = preferencesStorage.getTotalEntries(),
        lastClaimDate = preferencesStorage.getLastClaimDate(),
        completedDays = preferencesStorage.getCompletedDays(),
    )

    private fun saveStreakState(state: StreakState) {
        preferencesStorage.saveStreakDay(state.currentDay)
        preferencesStorage.saveTotalEntries(state.totalEntries)
        state.lastClaimDate?.let { preferencesStorage.saveLastClaimDate(it) }
        preferencesStorage.saveCompletedDays(state.completedDays)
    }
}
