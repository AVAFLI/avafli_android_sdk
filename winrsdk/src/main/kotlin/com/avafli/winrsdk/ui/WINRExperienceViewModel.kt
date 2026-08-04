package com.avafli.winrsdk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avafli.winrsdk.WINR
import com.avafli.winrsdk.WINRUser
import com.avafli.winrsdk.domain.DailyEntryGrant
import com.avafli.winrsdk.domain.Giveaway
import com.avafli.winrsdk.domain.PrizeClaimBlock
import com.avafli.winrsdk.domain.PrizeClaimForm
import com.avafli.winrsdk.domain.SdkConfig
import com.avafli.winrsdk.domain.StreakEngine
import com.avafli.winrsdk.domain.StreakState
import com.avafli.winrsdk.network.WinrApi
import com.avafli.winrsdk.services.Logger
import com.avafli.winrsdk.services.analytics.AnalyticsAdapter
import com.avafli.winrsdk.storage.PreferencesStorage
import kotlinx.coroutines.delay
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
// stages a PREDICTED grant from the pre-claim status response so the
// celebration is the dashboard's FIRST visible frame (reveal ~0.15s after
// mount — no pinned pre-claim flash, no claim tap), while the real claim runs
// in the background and reconciles silently. The rewarded-video bonus flow is
// parked (Phase 1) and has been removed.

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

    /**
     * This person is the drawn winner and hasn't submitted their claim yet —
     * the drawer shows the winner splash → claim form → confirmation flow
     * instead of the dashboard. Takes precedence on open.
     */
    data class WinnerClaim(val claim: PrizeClaimBlock) : ExperienceScreen()

    data class Error(val message: String) : ExperienceScreen()
}

/** Sub-screen of the winner claim flow (`screen is WinnerClaim`). */
internal sealed class WinnerClaimStep {
    object Splash : WinnerClaimStep()
    object Form : WinnerClaimStep()
    data class Confirmation(val claimNumber: String, val submittedAt: String) : WinnerClaimStep()
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
    // The celebration is the dashboard's FIRST visible frame: before the
    // dashboard state is entered, a PREDICTED grant (ladder value for today's
    // streak day) is staged from the pre-claim status response, and the reveal
    // fires on the first composition (~0.15s after mount) — the day tile
    // checks off with confetti, the totals count up, and the bar opens ON the
    // "YOU'RE ON A ROLL!" toast. The real claim runs in the background and
    // reconciles the numbers silently (no second celebration; a failure
    // settles back to server truth quietly). The pill reads "GOT IT" the
    // whole time — there is no claim tap.

    /**
     * The grant staged for the auto-reveal (null when nothing is pending):
     * the PREDICTED grant at load, replaced by the real grant (base + all
     * streak bonuses) when the background claim lands.
     */
    val pendingRevealGrant: DailyEntryGrant? = null,
    /** Whether the in-place celebration has played. */
    val claimRevealed: Boolean = false,
    /** Total entries as of before today's claim, for the pre-reveal frame. */
    val preClaimTotalEntries: Int? = null,

    // ── Winner prize claim (mirrors iOS) ──

    /** Which screen of the winner claim flow is showing. */
    val winnerClaimStep: WinnerClaimStep = WinnerClaimStep.Splash,
    /** Spinner state for the claim form's SUBMIT pill. */
    val isSubmittingClaim: Boolean = false,
    /**
     * Transport-level submit failure surfaced inline on the form ("Not the
     * winner"/"Already submitted" instead fall back to the dashboard silently).
     */
    val claimSubmitError: String? = null,
    /** The submitted form, kept for the confirmation screen's winner card. */
    val submittedClaimForm: PrizeClaimForm? = null,
)

internal class WINRExperienceViewModel(
    private val api: WinrApi,
    private val preferencesStorage: PreferencesStorage,
    private val logger: Logger,
    private val analytics: AnalyticsAdapter? = null,
) : ViewModel() {

    companion object {
        /**
         * Delay between staging the (predicted) grant and the celebration
         * firing — one composition beat, so the dashboard's first visible
         * frame IS the celebration: the springs get a baseline frame to
         * animate from, with no perceptible pre-claim flash.
         */
        internal const val AUTO_REVEAL_DELAY_MS = 150L
    }

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

    /**
     * Set after a "Not the winner"/"Already submitted" rejection so the next
     * load skips the winner flow and lands on the normal dashboard.
     */
    private var suppressWinnerClaim = false

    /** Host-app-provided identity used to prefill the claim form. */
    private var prefillUser: WINRUser? = null

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

    fun setPrefillUser(user: WINRUser?) {
        prefillUser = user
    }

    /** Prefill for the claim form (host-app-provided identity). */
    fun claimFormPrefill(): PrizeClaimForm = PrizeClaimForm(
        firstName = prefillUser?.firstName ?: "",
        lastName = prefillUser?.lastName ?: "",
        phone = prefillUser?.phone ?: "",
    )

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
        var pendingPrizeClaim: PrizeClaimBlock? = null

        try {
            val response = api.getActiveGiveaway()

            // RTD: an opted-out person never sees the experience content.
            if (response.optedOut == true) {
                setScreen(ExperienceScreen.NoActiveGiveaway)
                return
            }

            // Winner prize claim: a PENDING block takes precedence over the
            // dashboard on open (routed below, once the caches are synced).
            // A "submitted" block is ignored — the normal dashboard shows.
            if (response.prizeClaim?.isPending == true && !suppressWinnerClaim) {
                pendingPrizeClaim = response.prizeClaim
            }

            // Check if backend returned no active giveaway. (A pending prize
            // claim can outlive its giveaway — the winner flow still shows.)
            if (response.giveaway == null && pendingPrizeClaim == null) {
                activeGiveaway = null
                setScreen(ExperienceScreen.NoActiveGiveaway)
                return
            }

            response.giveaway?.let { activeGiveaway = it }
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

        // Winner prize claim takes precedence over auto-claim/dashboard on open —
        // but the daily auto-claim still fires silently in the background so the
        // winner's entries keep accruing. Shown even before the email gate: the
        // claim is keyed to the account server-side.
        pendingPrizeClaim?.let { claim ->
            _uiState.value = _uiState.value.copy(
                screen = ExperienceScreen.WinnerClaim(claim),
                winnerClaimStep = WinnerClaimStep.Splash,
                giveaway = activeGiveaway,
                sdkConfig = sdkConfig,
            )
            analytics?.trackEvent(
                "winr_winner_claim_shown",
                mapOf("giveaway_id" to claim.giveawayId),
            )
            if (backendClaimedToday != true && preferencesStorage.isEmailSubmitted()) {
                silentDailyClaim()
            }
            return
        }

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
        // Judged against SERVER truth, not the UI flag: the Day 2+ predicted
        // reveal stages claimedToday=true optimistically while the real claim
        // still needs to fire. Failures reconcile quietly.
        val unclaimed = backendClaimedToday == false ||
            (backendClaimedToday == null && !_uiState.value.claimedToday)
        if (_uiState.value.screen is ExperienceScreen.Streak && unclaimed) {
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

            // Sync local state with the backend — always SERVER truth, never
            // the prediction below (seed running totals from the backend so
            // entries claimed on OTHER devices are reflected).
            val today = LocalDate.now().toString()
            val state = StreakState(
                currentDay = day,
                claimedToday = backendClaimedToday,
                totalEntries = total,
                lastClaimDate = if (backendClaimedToday) today else preferencesStorage.getLastClaimDate(),
            )
            saveStreakState(state)

            // V2 predicted reveal (Day 2+ unclaimed): the celebration is the
            // dashboard's FIRST visible frame — no pinned pre-claim flash and
            // no wait for the claim round-trip. Stage a PREDICTED grant from
            // the pre-claim response (the ladder value for today's streak day)
            // BEFORE entering the dashboard state and arm the reveal for the
            // first composition; the real claim (kicked off by the caller)
            // reconciles silently when it returns.
            if (!backendClaimedToday && day >= 2) {
                val predictedEntries = entriesToday
                val predictedTotal = total + predictedEntries
                val predictedState = StreakState(
                    currentDay = day,
                    claimedToday = true,
                    totalEntries = predictedTotal,
                    lastClaimDate = today,
                )
                _uiState.value = _uiState.value.copy(
                    screen = ExperienceScreen.Streak(predictedState, entriesToday, ladder),
                    claimedToday = true,
                    giveaway = activeGiveaway,
                    sdkConfig = sdkConfig,
                    displayStreakDay = day,
                    displayTotalEntries = predictedTotal,
                    pendingRevealGrant = DailyEntryGrant(
                        entries = predictedEntries,
                        streakDay = day,
                        totalEntries = predictedTotal,
                    ),
                    claimRevealed = false,
                    preClaimTotalEntries = total,
                )
                scheduleAutoReveal()
                return
            }

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
     * V2 reveal (Day 2+): flips the UI into the celebration state. The staged
     * grant is (typically) the PREDICTED one — the real claim reconciles the
     * numbers silently when it lands; the springs on the dashboard (total
     * count-up, tile check + confetti burst, toast-first bar) animate the
     * flip. Idempotent — double-fires are harmless.
     */
    fun revealClaim() {
        val ui = _uiState.value
        if (ui.pendingRevealGrant == null || ui.claimRevealed) return
        _uiState.value = ui.copy(claimRevealed = true)
    }

    /**
     * Schedules the automatic celebration one composition beat after the
     * grant is staged (Joe's prototype — no claim tap). Armed when the
     * predicted grant is staged at load, and again from the claim success
     * path for the rare open where no prediction was staged (offline-cache
     * load whose claim still reached the server) — [revealClaim] is
     * idempotent, so double-arming is harmless.
     */
    private fun scheduleAutoReveal() {
        val ui = _uiState.value
        if (ui.pendingRevealGrant == null || ui.claimRevealed) return
        viewModelScope.launch {
            delay(AUTO_REVEAL_DELAY_MS)
            revealClaim()
        }
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
                // celebration ("YOU EARNED N" / "N ENTRIES ADDED") is complete.
                val grantEntries = response.entries + streakBonusEntries

                // V2 auto-claim routing (mirrors iOS):
                // - Day 1 (brand-new or restarted streak, typically right after
                //   email capture): the "You're in!" celebration modal is the
                //   reveal.
                // - Day 2+: no modal, no claim tap. Land on the dashboard pinned
                //   to yesterday's numbers; the celebration fires on its own a
                //   beat later (Joe's Slice Day 2+ flow).
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
                    // Reconcile the predicted reveal with server truth
                    // SILENTLY: totals/streak update in place. claimRevealed
                    // is deliberately left untouched — the celebration that
                    // (typically) already played from the predicted grant is
                    // never replayed.
                    _uiState.value = _uiState.value.copy(
                        screen = ExperienceScreen.Streak(updatedStreak, response.entries, displayLadder),
                        claimedToday = true,
                        displayStreakDay = response.streakDay,
                        displayTotalEntries = response.totalEntries,
                        pendingRevealGrant = grant.copy(entries = grantEntries),
                        preClaimTotalEntries = response.totalEntries - grantEntries,
                    )
                    // No-op if the predicted reveal already played; arms the
                    // celebration for the offline-cache open that staged no
                    // prediction.
                    scheduleAutoReveal()
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

        // Auto-claim failures are SILENT by design: settle back to server
        // truth quietly — drop the predicted grant (if one was staged) and
        // rest on the pre-claim numbers in the unclaimed state. Never fake a
        // local success for an auto-claim.
        if (auto) {
            logger.debug("Auto-claim declined: ${e.message}")
            val settled = screen.streakState.copy(
                claimedToday = false,
                totalEntries = _uiState.value.preClaimTotalEntries
                    ?: screen.streakState.totalEntries,
                lastClaimDate = preferencesStorage.getLastClaimDate(),
            )
            _uiState.value = _uiState.value.copy(
                screen = ExperienceScreen.Streak(settled, screen.entriesToday, screen.ladder),
                claimedToday = false,
                displayTotalEntries = settled.totalEntries,
                pendingRevealGrant = null,
                claimRevealed = false,
                preClaimTotalEntries = null,
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

    // ── Winner prize claim ──

    /**
     * Fire-and-forget daily claim while the winner flow is on screen — the
     * winner still accrues their streak entries, but nothing is revealed.
     */
    private fun silentDailyClaim() {
        viewModelScope.launch {
            try {
                val response = api.claimDailyEntries(TimeZone.getDefault().id)
                cachedBackendClaimedToday = true
                cachedBackendStreakDay = response.streakDay
                cachedBackendTotalEntries = response.totalEntries
                _uiState.value = _uiState.value.copy(claimedToday = true)
                logger.debug("Silent daily claim during winner flow: +${response.entries}")
            } catch (e: Exception) {
                logger.debug("Silent daily claim declined during winner flow: ${e.message}")
            }
        }
    }

    /** Splash CONTINUE → the claim form. */
    fun winnerClaimContinue() {
        if (_uiState.value.screen !is ExperienceScreen.WinnerClaim) return
        _uiState.value = _uiState.value.copy(winnerClaimStep = WinnerClaimStep.Form)
    }

    /**
     * SUBMIT on the claim form. Success → confirmation screen. A backend
     * "Not the winner"/"Already submitted" rejection falls back to the normal
     * dashboard silently (logged); transport failures surface inline.
     */
    fun submitPrizeClaim(form: PrizeClaimForm) {
        val screen = _uiState.value.screen as? ExperienceScreen.WinnerClaim ?: return
        if (_uiState.value.isSubmittingClaim) return
        if (!form.isValid) return
        _uiState.value = _uiState.value.copy(claimSubmitError = null, isSubmittingClaim = true)

        viewModelScope.launch {
            try {
                val response = api.submitPrizeClaim(
                    giveawayId = screen.claim.giveawayId,
                    firstName = form.firstName.trim(),
                    lastName = form.lastName.trim(),
                    phone = form.phone.trim().ifEmpty { null },
                    street = form.street.trim(),
                    apt = form.apt.trim().ifEmpty { null },
                    city = form.city.trim(),
                    state = form.state.trim(),
                    zip = form.zip.trim(),
                    country = form.country,
                    photoBase64 = form.photoBase64,
                    story = null,
                )
                _uiState.value = _uiState.value.copy(
                    isSubmittingClaim = false,
                    submittedClaimForm = form,
                    winnerClaimStep = WinnerClaimStep.Confirmation(
                        claimNumber = response.claimNumber,
                        submittedAt = response.submittedAt,
                    ),
                )
                analytics?.trackEvent(
                    "winr_prize_claim_submitted",
                    mapOf(
                        "giveaway_id" to screen.claim.giveawayId,
                        "claim_number" to response.claimNumber,
                    ),
                )
            } catch (e: Exception) {
                val message = e.message ?: ""
                if (message.contains("Not the winner") || message.contains("Already submitted")) {
                    // Stale/duplicate winner state — never trap the user in the
                    // claim flow. Fall back to the normal dashboard silently.
                    logger.info("Prize claim rejected ($message) — falling back to dashboard")
                    suppressWinnerClaim = true
                    _uiState.value = _uiState.value.copy(
                        isSubmittingClaim = false,
                        screen = ExperienceScreen.Loading,
                    )
                    loadInternal()
                    return@launch
                }
                logger.error("Prize claim submit failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isSubmittingClaim = false,
                    claimSubmitError = "Something went wrong. Please check your connection and try again.",
                )
            }
        }
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
