package com.avafli.winrsdk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avafli.winrsdk.WINR
import com.avafli.winrsdk.WINRError
import com.avafli.winrsdk.WINRUser
import com.avafli.winrsdk.domain.DailyEntryGrant
import com.avafli.winrsdk.domain.WINRFieldValidation
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
import com.avafli.winrsdk.ui.v2.V2Strings
import com.avafli.winrsdk.ui.v2.WINRV2ImageWarmer
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
// claim on open. Day 1 AND Day 2+ celebrate IN PLACE (the Day-1 "You're in!"
// modal is gone): the dashboard mounts as the first-frame celebration — Day 1
// stages the REAL grant from the awaited email-path claim, Day 2+ stages a
// PREDICTED grant from the pre-claim status response (reveal ~0.15s after
// mount — no pinned pre-claim flash, no claim tap) while the real claim runs
// in the background and reconciles silently. Only the toast headline differs
// ("YOU'RE IN!" vs "YOU'RE ON A ROLL!"). The rewarded-video bonus flow is
// parked (Phase 1) and has been removed.

internal sealed class ExperienceScreen {
    object Loading : ExperienceScreen()
    object NoActiveGiveaway : ExperienceScreen()
    object EmailCapture : ExperienceScreen()

    /** Verified adoption: the typed email matches an existing account; the
     *  merge waits on the 6-digit code sent to that inbox. */
    data class CodeEntry(val email: String) : ExperienceScreen()
    data class Streak(
        val streakState: StreakState,
        val entriesToday: Int,
        val ladder: List<Int>
    ) : ExperienceScreen()

    object HowItWorks : ExperienceScreen()

    /**
     * This person is the drawn winner and hasn't submitted their claim yet —
     * the drawer shows the winner splash → claim form → confirmation flow
     * instead of the dashboard. Takes precedence on open.
     */
    data class WinnerClaim(val claim: PrizeClaimBlock) : ExperienceScreen()

    /**
     * The backend geo-fence rejected this person (US-only promotion, fails
     * closed on inconclusive lookups). Dedicated state — never the generic
     * empty state — so the user learns WHY nothing is available.
     */
    object GeoBlocked : ExperienceScreen()

    /**
     * Token refresh failed (refresh token missing/rejected — local tokens are
     * cleared by the NetworkClient). The RETRY affordance re-registers the
     * device and reloads.
     */
    object SessionExpired : ExperienceScreen()

    data class Error(val message: String) : ExperienceScreen()
}

/**
 * A non-blocking notice rendered as a banner on the streak dashboard.
 * [retryable] notices carry a TRY AGAIN affordance and persist until retried
 * or cleared; non-retryable ones auto-dismiss after a few seconds.
 */
internal data class DashboardNotice(val message: String, val retryable: Boolean)

/**
 * The "Privacy choices" → "Delete my data & stop participating" flow on the
 * how-it-works screen:
 *
 *     Idle → Confirming → InFlight → Done → (screen dismisses the experience)
 *                     ↘ Failed (inline error, retryable) ↗
 *
 * Failure NEVER pretends success — the confirmation stays up with the error
 * and the destructive button remains available to retry.
 */
internal sealed class OptOutPhase {
    /** Nothing showing (the "Privacy choices" link is idle). */
    object Idle : OptOutPhase()

    /** The destructive confirmation is up. */
    object Confirming : OptOutPhase()

    /** DELETE MY DATA tapped; the /optOut round-trip is in flight. */
    object InFlight : OptOutPhase()

    /** The round-trip failed — confirmation stays up with this inline error. */
    data class Failed(val message: String) : OptOutPhase()

    /**
     * Deleted. "Your data has been deleted." holds briefly, then the whole
     * experience dismisses.
     */
    object Done : OptOutPhase()
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
    val isVerifyingCode: Boolean = false,
    val codeError: String? = null,
    /**
     * Transport failure of the email submit itself — surfaced inline on the
     * capture screen (the user stays there and can retry; a failed submit is
     * never silently treated as success).
     */
    val emailSubmitError: String? = null,
    /** Non-blocking dashboard banner (duplicate-entry / claim-transport notices). */
    val dashboardNotice: DashboardNotice? = null,
    /** The "Privacy choices" → delete-my-data flow (how-it-works screen). */
    val optOutPhase: OptOutPhase = OptOutPhase.Idle,
    /** Current streak day for display (backend truth, falling back to local). */
    val displayStreakDay: Int = 1,
    val displayTotalEntries: Int = 0,

    // ── V2 reveal flow (Day 1 AND Day 2+, unified) — mirrors iOS ──
    //
    // The celebration is the dashboard's FIRST visible frame: before the
    // dashboard state is entered, a grant is staged — Day 1 the REAL grant
    // from the awaited email-path claim, Day 2+ a PREDICTED grant (ladder
    // value for today's streak day) from the pre-claim status response — and
    // the reveal fires on the first composition (~0.15s after mount): the day
    // tile checks off with confetti, the totals count up, and the bar opens
    // ON the toast ("YOU'RE IN!" on Day 1, "YOU'RE ON A ROLL!" on Day 2+).
    // Any background claim reconciles the numbers silently (no second
    // celebration; a failure settles back to server truth quietly). The pill
    // reads "GOT IT" the whole time — there is no claim tap.

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

        /** How long a non-retryable dashboard notice stays up before auto-dismissing. */
        internal const val DASHBOARD_NOTICE_AUTO_DISMISS_MS = 6_000L

        /**
         * How long "Your data has been deleted." holds before the experience
         * dismisses itself (opt-out success).
         */
        internal const val OPT_OUT_SUCCESS_HOLD_MS = 1_400L
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

    /** Consents from the ORIGINAL submit — resend must reuse them (fabricating
     *  values would overwrite the person's real marketing choice). Memory only. */
    private var pendingVerification: Triple<String, Boolean, Boolean>? = null

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

    /**
     * Partner-authenticated email for the capture screen's read-only pre-fill.
     * Null when the publisher didn't supply one via WINRUser.email.
     */
    fun prefilledEmail(): String? = prefillUser?.email

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
        // Paint the dashboard from cache on the FIRST frame — before any
        // network call resolves — then let [loadInternal] reconcile silently.
        hydrateFromCache(cachedGiveaway)
        viewModelScope.launch { loadInternal(cachedGiveaway) }
    }

    // ── Cache-first render ──

    /**
     * True once the dashboard has been painted from cached values, so the
     * network reconcile knows it is updating a LIVE dashboard rather than
     * replacing a loading screen.
     */
    private var hydratedFromCache = false

    /**
     * Renders the dashboard IMMEDIATELY from cached state (the giveaway config
     * the SDK already holds from registration + the persisted streak), skipping
     * the loading phase entirely.
     *
     * The drawer used to sit on a spinner for as long as its `getActiveGiveaway`
     * → claim round-trips took (seconds on a slow network) even though every
     * value it needed was already on the device. Everything here is a LOCAL
     * read, so it lands synchronously with the Activity's `setContent`;
     * [loadInternal] then reconciles the same way the celebration staging
     * already does — silently, in place, with the reveal flags untouched so a
     * staged celebration still fires exactly once.
     *
     * Bails out (leaving the skeleton up) when anything is missing or when a
     * fresh response already resolved the screen: a first-ever open, an
     * unconsented user who must see email capture first, or a cold cache all
     * take the genuine loading path.
     */
    internal fun hydrateFromCache(cachedGiveaway: Giveaway? = null) {
        // Never stomp fresher truth.
        if (_uiState.value.screen !is ExperienceScreen.Loading) return

        val giveaway = cachedGiveaway ?: activeGiveaway ?: return

        // Day 1 / unconsented users must land on email capture, never on a
        // cached dashboard.
        if (!preferencesStorage.isEmailSubmitted()) return

        val day = preferencesStorage.getStreakDay()
        if (day <= 0) return

        activeGiveaway = giveaway
        val ladder = displayLadder
        val entriesToday = ladder[(day - 1).coerceIn(0, ladder.size - 1)]
        val total = preferencesStorage.getTotalEntries()
        val lastClaimDate = preferencesStorage.getLastClaimDate()
        val claimedToday = lastClaimDate == LocalDate.now().toString()
        val state = StreakState(
            currentDay = day,
            claimedToday = claimedToday,
            totalEntries = total,
            lastClaimDate = lastClaimDate,
            completedDays = preferencesStorage.getCompletedDays(),
        )

        hydratedFromCache = true
        // pendingRevealGrant / claimRevealed are deliberately left alone: this
        // is a plain resting dashboard, and the celebration (if today has one)
        // is staged by the network path exactly as it always was.
        _uiState.value = _uiState.value.copy(
            screen = ExperienceScreen.Streak(state, entriesToday, ladder),
            claimedToday = claimedToday,
            giveaway = giveaway,
            sdkConfig = sdkConfig,
            displayStreakDay = day,
            displayTotalEntries = total,
        )
        logger.debug("Dashboard painted from cache (day $day) — reconciling in background")
    }

    private suspend fun loadInternal(
        cachedGiveaway: Giveaway? = null,
        claimBeforeDashboard: Boolean = false,
    ) {
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

            response.giveaway?.let {
                activeGiveaway = it
                // Keep the prize art warm across prize changes mid-session.
                WINRV2ImageWarmer.prewarm(it.prizeImageUrl)
            }
            response.sdkConfig?.let {
                sdkConfig = it
                WINR.updateSdkConfig(it)
                WINRV2ImageWarmer.prewarm(it.branding?.logoUrl)
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
        } catch (e: WINRError.GeoBlocked) {
            // US-only geo-fence: a dedicated, honest state — never the generic
            // empty state (and never raw backend text).
            logger.info("Geo-fence rejection on load — showing the geo-blocked state")
            setScreen(ExperienceScreen.GeoBlocked)
            return
        } catch (e: WINRError.TokenRefreshFailed) {
            // The refresh token is gone/rejected and local tokens were cleared:
            // every subsequent call would fail. Surface the dedicated state with
            // the RETRY (re-register + reload) affordance.
            logger.info("Session expired on load — showing the session-expired state")
            setScreen(ExperienceScreen.SessionExpired)
            return
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

        // Email path (Day 1): claim FIRST, while the capture view's spinner is
        // still up. On success the celebration is staged from the REAL
        // response (synchronous with the transition — no prediction needed)
        // and the caches below already reflect the claim, so the predicted
        // staging is naturally skipped. On failure this is a no-op and the
        // normal predict → auto-claim → reconcile path takes over.
        if (claimBeforeDashboard && backendClaimedToday == false) {
            claimAndStageBeforeDashboard()
            backendClaimedToday = cachedBackendClaimedToday
            backendStreakDay = cachedBackendStreakDay
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

            // V2 predicted reveal (Day 1 AND Day 2+ unclaimed): the
            // celebration is the dashboard's FIRST visible frame — no pinned
            // pre-claim flash and no wait for the claim round-trip. Stage a
            // PREDICTED grant from the pre-claim response (the ladder value
            // for today's streak day) BEFORE entering the dashboard state and
            // arm the reveal for the first composition; the real claim
            // (kicked off by the caller) reconciles silently when it returns.
            // Day 1 normally never reaches this — the email path claims
            // before the dashboard — but an interrupted Day-1 open (killed
            // app between email submit and claim) falls through here and
            // still celebrates in place.
            if (!backendClaimedToday && day >= 1) {
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
            // Email-path Day 1: the grant staged from the awaited claim fires
            // as the dashboard's first frame. No-op without a staged grant
            // (plain already-claimed reopens).
            scheduleAutoReveal()
            return
        }

        // ─── Offline fallback: local streak engine ───
        val local = runCatching {
            val engine = StreakEngine(
                activeGiveaway ?: Giveaway(id = "", title = "", prizeDescription = "")
            )
            engine.setState(loadStreakState())
            engine.checkStreakReset()
            engine
        }.getOrElse { e ->
            // A cache-rendered dashboard is already on screen and correct
            // enough — a local streak-engine hiccup is not worth replacing it
            // with an error screen.
            if (hydratedFromCache) {
                logger.debug("Local streak engine failed after a cache render — keeping the dashboard: ${e.message}")
            } else {
                logger.error("Local streak engine failed: ${e.message}", e)
                setScreen(ExperienceScreen.Error(e.message ?: "Failed to load your streak"))
            }
            return
        }

        val state = local.getState()
        val claimed = local.hasClaimedToday()
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

    /**
     * Email path (Day 1): awaited claim BEFORE the dashboard ever mounts. On
     * success the celebration is staged from the REAL response, so the first
     * dashboard frame counts 0 → N under the "YOU'RE IN!" toast. On failure
     * this is a no-op — loadInternal falls through to the predicted staging
     * and the silent auto-claim retry, the same as any other open.
     */
    private suspend fun claimAndStageBeforeDashboard() {
        try {
            val response = api.claimDailyEntries(TimeZone.getDefault().id)
            val grantEntries = response.entries +
                (response.weeklyBonusEntries ?: 0) +
                (response.monthlyBonusEntries ?: 0) +
                (response.milestone?.bonusEntries ?: 0) +
                (response.monthlyMilestone?.bonusEntries ?: 0)

            cachedBackendTotalEntries = response.totalEntries
            cachedBackendStreakDay = response.streakDay
            cachedBackendClaimedToday = true

            saveStreakState(
                StreakState(
                    currentDay = response.streakDay,
                    claimedToday = true,
                    totalEntries = response.totalEntries,
                    lastClaimDate = LocalDate.now().toString(),
                )
            )

            _uiState.value = _uiState.value.copy(
                pendingRevealGrant = DailyEntryGrant(
                    entries = grantEntries,
                    streakDay = response.streakDay,
                    totalEntries = response.totalEntries,
                    weeklyBonusEntries = response.weeklyBonusEntries,
                    monthlyBonusEntries = response.monthlyBonusEntries,
                    milestone = response.milestone,
                ),
                claimRevealed = false,
                preClaimTotalEntries = response.totalEntries - grantEntries,
            )

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
        } catch (e: Exception) {
            logger.info("Day-1 claim after email submit failed (dashboard will retry): ${e.message}")
        }
    }

    // ── Email capture ──

    /**
     * [ageConfirmed] and [marketingConsent] are the capture screen's two
     * checkbox states, transmitted verbatim. The age gate must be ticked for
     * the CTA to enable; the marketing-email checkbox is unchecked by default
     * and optional — declining it affects neither entry nor winner contact.
     *
     * Both parameters default to FALSE on purpose. A default of true meant a
     * caller omitting the argument silently recorded consent the user never
     * gave — and for [ageConfirmed], fabricated an age attestation. Absence of
     * an affirmation must never become an affirmation.
     */
    fun submitEmail(
        email: String,
        marketingConsent: Boolean = false,
        ageConfirmed: Boolean = false,
    ) {
        if (email.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmittingEmail = true, emailSubmitError = null)

            try {
                // Cross-device streak unification: if this email already belonged to
                // an existing user under this publisher, WinrApi switches to the
                // canonical user's credentials internally (adopted == true).
                val result = api.submitEmail(
                    email = email,
                    marketingConsent = marketingConsent,
                    publisherUserId = publisherUserId,
                    ageConfirmed = ageConfirmed,
                )
                if (result.verificationRequired) {
                    // The merge is parked until the person proves the inbox is
                    // theirs. Raw email stays in view-model memory only.
                    pendingVerification = Triple(email, ageConfirmed, marketingConsent)
                    _uiState.value = _uiState.value.copy(
                        isSubmittingEmail = false,
                        codeError = null,
                        screen = ExperienceScreen.CodeEntry(email),
                    )
                    return@launch
                }
                // Persist the non-PII "email submitted" flag only AFTER the
                // backend accepted the submit. (The raw email is deliberately
                // never stored locally — PII-High.) Setting it before/despite
                // a failed submit fabricated a registration that never
                // happened: the next open skipped capture and the person was
                // never actually entered.
                preferencesStorage.saveEmailSubmitted(true)
                // Refresh the SDK-level consent cache HERE, on the submit
                // itself, rather than waiting for the next getActiveGiveaway
                // to echo emailConsentStatus back. Today a stale `false` is
                // masked because auto-present checks the once-per-day mark
                // before the consent flag, so the unregistered-impression
                // counter never gets the chance to burn an open on a user who
                // just registered — but correctness must not depend on that
                // ordering.
                WINR.noteEmailConsent()
                logger.debug("Email submitted to backend")
            } catch (e: Exception) {
                logger.error("Email submit to backend failed: ${e.message}", e)
                when (e) {
                    // Geo-fenced on submitEmail (the backend blocks BEFORE any
                    // PII is stored) — dedicated state, not an inline error.
                    is WINRError.GeoBlocked -> setScreen(ExperienceScreen.GeoBlocked)
                    is WINRError.TokenRefreshFailed -> setScreen(ExperienceScreen.SessionExpired)
                    // Everything else: stay on capture with an inline error and
                    // let the person retry — a failed submit must never proceed
                    // as if it succeeded.
                    else -> _uiState.value = _uiState.value.copy(
                        emailSubmitError = V2Strings.EMAIL_SUBMIT_FAILED,
                    )
                }
                _uiState.value = _uiState.value.copy(isSubmittingEmail = false)
                return@launch
            }

            _uiState.value = _uiState.value.copy(isSubmittingEmail = false)

            // Re-load so the (possibly switched) canonical user's authoritative
            // streak + claim status drive the UI. The Day-1 claim is awaited
            // INSIDE the load (capture spinner stays up) so the dashboard
            // mounts already celebrating — never an uncelebrated streak page.
            loadInternal(claimBeforeDashboard = true)
        }
    }

    /** Check the 6-digit adoption code; approved → the API has switched the
     *  session to the canonical user, so reload into the dashboard. */
    fun submitVerificationCode(code: String) {
        if (_uiState.value.isVerifyingCode) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isVerifyingCode = true, codeError = null)
            try {
                api.verifyAdoptionCode(code)
                // The merge completed — the email is on file for the (now
                // canonical) user, so the registration flag is earned here.
                preferencesStorage.saveEmailSubmitted(true)
                WINR.noteEmailConsent()
                pendingVerification = null
                _uiState.value = _uiState.value.copy(isVerifyingCode = false)
                loadInternal(claimBeforeDashboard = true)
            } catch (e: Exception) {
                logger.error("Adoption code check failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isVerifyingCode = false,
                    codeError = V2Strings.CODE_MISMATCH,
                )
            }
        }
    }

    /** Request a fresh code by re-submitting the ORIGINAL email + consents. */
    fun resendVerificationCode() {
        val (email, age, marketing) = pendingVerification ?: return
        _uiState.value = _uiState.value.copy(screen = ExperienceScreen.EmailCapture)
        submitEmail(email, marketingConsent = marketing, ageConfirmed = age)
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

    // ── Privacy choices / RTD opt-out (how-it-works screen) ──

    /** "Privacy choices" tapped — raise the destructive confirmation. */
    fun showOptOutConfirmation() {
        if (_uiState.value.optOutPhase == OptOutPhase.Idle) {
            _uiState.value = _uiState.value.copy(optOutPhase = OptOutPhase.Confirming)
        }
    }

    /** Cancel / tap-outside. A no-op while in flight or after the deletion. */
    fun cancelOptOut() {
        when (_uiState.value.optOutPhase) {
            OptOutPhase.Confirming, is OptOutPhase.Failed ->
                _uiState.value = _uiState.value.copy(optOutPhase = OptOutPhase.Idle)
            else -> Unit
        }
    }

    /**
     * DELETE MY DATA tapped (initial attempt or retry after failure): performs
     * the RTD opt-out against the backend, persists the local silence flags,
     * and lands on [OptOutPhase.Done] — the screen then holds the success copy
     * for [OPT_OUT_SUCCESS_HOLD_MS] and dismisses the whole experience.
     * Failure keeps the confirmation up with [V2Strings.OPT_OUT_FAILED]; we
     * never pretend the deletion succeeded.
     */
    fun confirmOptOut() {
        when (_uiState.value.optOutPhase) {
            OptOutPhase.Confirming, is OptOutPhase.Failed -> Unit
            else -> return
        }
        _uiState.value = _uiState.value.copy(optOutPhase = OptOutPhase.InFlight)
        viewModelScope.launch {
            try {
                val ok = api.optOut()
                if (!ok) throw IllegalStateException("optOut returned success=false")
                preferencesStorage.saveOptedOut(true)
                WINR.noteOptedOut()
                analytics?.trackEvent("winr_opted_out")
                logger.info("User opted out of WINR (RTD) — experience permanently silenced")
                _uiState.value = _uiState.value.copy(optOutPhase = OptOutPhase.Done)
            } catch (e: Exception) {
                logger.error("Opt-out failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    optOutPhase = OptOutPhase.Failed(V2Strings.OPT_OUT_FAILED),
                )
            }
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

                // V2 auto-claim routing (Day 1 AND Day 2+, unified — the
                // Day-1 celebration modal is gone): the celebration already
                // played (or is playing) at mount off the staged grant —
                // reconcile the staged numbers with server truth SILENTLY:
                // totals/streak update in place. claimRevealed is deliberately
                // left untouched — the celebration is never replayed. Day 1
                // only differs in the toast headline ("YOU'RE IN!" instead of
                // "YOU'RE ON A ROLL!").
                _uiState.value = _uiState.value.copy(
                    screen = ExperienceScreen.Streak(updatedStreak, response.entries, displayLadder),
                    claimedToday = true,
                    displayStreakDay = response.streakDay,
                    displayTotalEntries = response.totalEntries,
                    pendingRevealGrant = grant.copy(entries = grantEntries),
                    preClaimTotalEntries = response.totalEntries - grantEntries,
                    // A successful claim supersedes any "couldn't record your
                    // entry" notice (e.g. the retry just worked).
                    dashboardNotice = null,
                )
                // No-op if the reveal already played; arms the celebration
                // for the offline-cache open that staged no prediction.
                scheduleAutoReveal()
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
        // settle the dashboard into the claimed state. When local state did
        // NOT already know (this open predicted/celebrated a fresh grant, but
        // another device or an earlier open beat it), tell the user honestly
        // instead of silently celebrating entries this claim never granted.
        if (isAlreadyClaimedError(e)) {
            logger.debug("Already claimed today — updating local state")
            val today = LocalDate.now().toString()
            val localAlreadyKnew = preferencesStorage.getLastClaimDate() == today
            val updated = screen.streakState.copy(
                claimedToday = true,
                lastClaimDate = today,
            )
            saveStreakState(updated)
            _uiState.value = _uiState.value.copy(
                screen = ExperienceScreen.Streak(updated, screen.entriesToday, screen.ladder),
                claimedToday = true,
                // Drop any staged celebration: it was for a grant this claim
                // did not make. The one-shot resync below pulls server truth.
                pendingRevealGrant = null,
                claimRevealed = false,
                preClaimTotalEntries = null,
            )
            if (!localAlreadyKnew) {
                postDashboardNotice(V2Strings.ALREADY_ENTERED_TODAY, retryable = false)
            }
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

        // Geo-fence / expired-session rejections get their dedicated states —
        // an auto-claim that can never succeed must not fail silently.
        if (e is WINRError.GeoBlocked) {
            logger.info("Geo-fence rejection on claim — showing the geo-blocked state")
            setScreen(ExperienceScreen.GeoBlocked)
            if (!auto) onResult?.invoke(Result.failure(e))
            return
        }
        if (e is WINRError.TokenRefreshFailed) {
            logger.info("Session expired on claim — showing the session-expired state")
            setScreen(ExperienceScreen.SessionExpired)
            if (!auto) onResult?.invoke(Result.failure(e))
            return
        }

        // Auto-claim transport failure: settle back to server truth — drop the
        // predicted grant (if one was staged) and rest on the pre-claim numbers
        // in the UNCLAIMED state (never fake a local success) — and say so with
        // a non-blocking notice that carries a retry affordance.
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
            postDashboardNotice(V2Strings.ENTRY_NOT_RECORDED, retryable = true)
            return
        }

        logger.error("Failed to claim entries: ${e.message}", e)
        setScreen(ExperienceScreen.Error(e.message ?: "Failed to claim entries"))
        onResult?.invoke(Result.failure(e))
    }

    // ── Dashboard notices ──

    /**
     * Posts a non-blocking dashboard banner. Non-retryable notices are
     * transient (auto-dismiss); retryable ones persist until retried/cleared.
     */
    private fun postDashboardNotice(message: String, retryable: Boolean) {
        _uiState.value = _uiState.value.copy(
            dashboardNotice = DashboardNotice(message, retryable),
        )
        if (!retryable) {
            viewModelScope.launch {
                delay(DASHBOARD_NOTICE_AUTO_DISMISS_MS)
                if (_uiState.value.dashboardNotice?.message == message) {
                    clearDashboardNotice()
                }
            }
        }
    }

    fun clearDashboardNotice() {
        _uiState.value = _uiState.value.copy(dashboardNotice = null)
    }

    /** TRY AGAIN on the "couldn't record today's entry" notice. */
    fun retryDailyClaim() {
        clearDashboardNotice()
        claimDailyEntries(auto = true)
    }

    /**
     * RETRY on the session-expired state: re-registers the device (the expired
     * session's tokens are already cleared, so registration mints fresh ones
     * for the same device fingerprint) and reloads the experience.
     */
    fun retryAfterSessionExpiry() {
        if (_uiState.value.screen !is ExperienceScreen.SessionExpired) return
        viewModelScope.launch {
            setScreen(ExperienceScreen.Loading)
            val reRegistered = WINR.reRegisterAfterSessionExpiry()
            if (!reRegistered) {
                setScreen(ExperienceScreen.SessionExpired)
                return@launch
            }
            loadInternal()
        }
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
                    // Send the bare validated 10-digit number (blank → omitted).
                    phone = WINRFieldValidation.normalizedPhoneOrNull(form.phone),
                    street = form.street.trim(),
                    apt = form.apt.trim().ifEmpty { null },
                    city = form.city.trim(),
                    state = form.state.trim(),
                    zip = form.zip.trim(),
                    country = form.country,
                    photoBase64 = form.photoBase64,
                    story = form.story.trim().ifEmpty { null },
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
                    claimSubmitError = V2Strings.CLAIM_SUBMIT_FAILED,
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
