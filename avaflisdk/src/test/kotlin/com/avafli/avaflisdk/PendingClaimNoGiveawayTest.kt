package com.avafli.avaflisdk

import android.app.Activity
import android.content.Context
import com.avafli.avaflisdk.domain.ClaimVerificationBlock
import com.avafli.avaflisdk.domain.ExperienceConfig
import com.avafli.avaflisdk.domain.Giveaway
import com.avafli.avaflisdk.domain.PrizeClaimBlock
import com.avafli.avaflisdk.domain.PrizeClaimForm
import com.avafli.avaflisdk.domain.SdkConfig
import com.avafli.avaflisdk.network.AvafliApi
import com.avafli.avaflisdk.services.Logger
import com.avafli.avaflisdk.storage.PreferencesStorage
import com.avafli.avaflisdk.storage.SecureStorage
import com.avafli.avaflisdk.ui.AvafliExperienceViewModel
import com.avafli.avaflisdk.ui.ExperienceScreen
import com.avafli.avaflisdk.ui.WinnerClaimStep
import com.avafli.avaflisdk.ui.v2.AvafliV2Strings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * A pending claim opens without an active giveaway (3.2.0).
 *
 * The NORMAL case for a real winner: a giveaway has ended by the time its
 * winner is drawn, so unless the publisher already started the next one the
 * status response is `{ giveaway: null, prizeClaim: {...} }`. The drawer must
 * open on the winner flow, and leaving that flow must dismiss the drawer —
 * never a dashboard or an empty state painted behind it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingClaimNoGiveawayTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var api: AvafliApi
    private lateinit var secureStorage: SecureStorage
    private lateinit var prefs: PreferencesStorage
    private lateinit var activity: Activity

    private var storedToken: String? = null
    private var lastAutoPresentDay: String? = null
    private var lastClaimAutoPresentAt: Long? = null
    private var unregisteredImpressions = 0

    private var now = Instant.parse("2026-09-29T18:00:00Z").toEpochMilli()

    private val giveaway = Giveaway(id = "gw-next", title = "Win", prizeDescription = "Cash")

    private fun claim(
        status: String = "pending",
        verification: ClaimVerificationBlock? = null,
    ) = PrizeClaimBlock(
        status = status,
        giveawayId = "gw-ended",
        prizeDescription = "Cash",
        prizeValue = 1000.0,
        maskedEmail = "d********r@avafli.example.com",
        verification = verification,
    )

    private val validForm = PrizeClaimForm(
        firstName = "Ada",
        lastName = "Lovelace",
        street = "1 Analytical Way",
        city = "Brooklyn",
        state = "New York",
        zip = "11201",
    )

    private fun serverError(http: Int, status: String, message: String) = AvafliError.ServerError(
        http,
        """{"error":{"status":"$status","message":"$message"}}""",
    )

    private fun stubRegister(
        giveaway: Giveaway? = null,
        claimStatus: String? = "pending",
        sdkConfig: SdkConfig? = null,
    ) {
        coEvery { api.registerDevice(any(), any(), any(), any()) } returns AvafliApi.RegisterDeviceResponse(
            token = "tok",
            refreshToken = "ref",
            uuid = "uuid",
            giveaway = giveaway,
            sdkConfig = sdkConfig,
            prizeClaim = claimStatus?.let { claim(it) },
        )
    }

    private fun stubStatus(
        giveaway: Giveaway? = null,
        claim: PrizeClaimBlock? = claim(),
    ) {
        coEvery { api.getActiveGiveaway() } returns AvafliApi.GetActiveGiveawayResponse(
            giveaway = giveaway,
            prizeClaim = claim,
        )
    }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        Avafli.resetForTests()
        Avafli.nowMs = { now }
        storedToken = null
        lastAutoPresentDay = null
        lastClaimAutoPresentAt = null
        unregisteredImpressions = 0

        api = mockk()
        stubRegister()
        stubStatus()
        coEvery { api.submitUserProfile(any(), any(), any(), any(), any(), any()) } returns true

        secureStorage = mockk(relaxed = true)
        every { secureStorage.getToken() } answers { storedToken }
        every { secureStorage.saveToken(any()) } answers { storedToken = firstArg() }

        prefs = mockk(relaxed = true)
        every { prefs.isOptedOut() } returns false
        every { prefs.getOptedOutUntil() } returns null
        every { prefs.getString(any()) } returns null
        every { prefs.isEmailSubmitted() } returns true
        every { prefs.getLastClaimDate() } returns null
        every { prefs.getLastAutoPresentDay() } answers { lastAutoPresentDay }
        every { prefs.saveLastAutoPresentDay(any()) } answers { lastAutoPresentDay = firstArg() }
        every { prefs.getLastClaimAutoPresentAt() } answers { lastClaimAutoPresentAt }
        every { prefs.saveLastClaimAutoPresentAt(any()) } answers { lastClaimAutoPresentAt = firstArg() }
        every { prefs.getUnregisteredImpressions() } answers { unregisteredImpressions }
        every { prefs.saveUnregisteredImpressions(any()) } answers { unregisteredImpressions = firstArg() }

        activity = mockk(relaxed = true)
        every { activity.isFinishing } returns false
        every { activity.isDestroyed } returns false
    }

    @After
    fun tearDown() {
        Avafli.resetForTests()
        Dispatchers.resetMain()
    }

    private fun configure(autoOpen: AvafliAutoOpen = AvafliAutoOpen.ALWAYS) {
        Avafli.testDependencies = Avafli.TestDependencies(secureStorage, prefs, api)
        Avafli.nowMs = { now }
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.packageName } returns "com.example.host"
        Avafli.configure(
            AvafliConfiguration(
                context = context,
                apiKey = "avafli_test_key",
                user = AvafliUser(id = "user-1"),
                options = AvafliOptions(enableCertificatePinning = false, enablePushReminders = false),
                autoOpen = autoOpen,
            )
        )
    }

    private fun opens(times: Int) = verify(exactly = times) { activity.startActivity(any()) }

    /**
     * The view-model exactly as the experience Activity builds it, with every
     * screen it ever showed recorded in order.
     */
    private fun TestScope.openDrawer(
        screens: MutableList<ExperienceScreen> = mutableListOf(),
    ): Pair<AvafliExperienceViewModel, List<ExperienceScreen>> {
        val viewModel = AvafliExperienceViewModel(api, prefs, Logger())
        viewModel.nowMs = { now }
        viewModel.setPrizeClaimPending(Avafli.isPrizeClaimPending())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { screens += it.screen }
        }
        viewModel.load(Avafli.getCachedGiveaway())
        advanceUntilIdle()
        return viewModel to screens
    }

    /** Only the loading skeleton and the winner flow — never anything behind it. */
    private fun assertOnlyWinnerFlow(screens: List<ExperienceScreen>) {
        assertTrue(screens.isNotEmpty())
        screens.forEach { screen ->
            assertTrue(
                "Painted $screen around the winner flow",
                screen is ExperienceScreen.Loading || screen is ExperienceScreen.WinnerClaim,
            )
        }
        assertTrue(screens.last() is ExperienceScreen.WinnerClaim)
    }

    // ── 1. The drawer opens ──

    @Test
    fun `registerDevice with no giveaway and a pending claim opens on configure, on the winner splash`() = runTest(dispatcher) {
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        opens(1)
        assertNull(Avafli.getCachedGiveaway())
        assertTrue(Avafli.isPrizeClaimPending())
        assertEquals(now, lastClaimAutoPresentAt)

        storedToken = "tok"
        val (viewModel, screens) = openDrawer()
        val ui = viewModel.uiState.value
        assertTrue(ui.screen is ExperienceScreen.WinnerClaim)
        assertEquals(WinnerClaimStep.Splash, ui.winnerClaimStep)
        assertNull(ui.giveaway)
        // The prize text comes from the claim block, not from a giveaway.
        assertEquals("Cash", (ui.screen as ExperienceScreen.WinnerClaim).claim.prizeDescription)
        assertEquals(1000.0, (ui.screen as ExperienceScreen.WinnerClaim).claim.prizeValue, 0.0)
        assertOnlyWinnerFlow(screens)
        // No giveaway is running: there is nothing to claim daily entries into.
        coVerify(exactly = 0) { api.claimDailyEntries(any()) }
    }

    @Test
    fun `an already registered device learns of it from the status refresh`() = runTest(dispatcher) {
        storedToken = "existing-token"
        lastAutoPresentDay = LocalDate.now().toString()

        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        coVerify(exactly = 0) { api.registerDevice(any(), any(), any(), any()) }
        opens(1)
    }

    @Test
    fun `present() opens in the same state`() = runTest(dispatcher) {
        configure(AvafliAutoOpen.NEVER)
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        opens(0)

        var result: Result<*>? = null
        Avafli.present(activity) { result = it }

        opens(1)
        assertNull(result)
    }

    // ── 4. Nothing pending, nothing running: unchanged ──

    @Test
    fun `no giveaway and no claim still declines`() = runTest(dispatcher) {
        stubRegister(claimStatus = null)
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        opens(0)

        var result: Result<*>? = null
        Avafli.present(activity) { result = it }
        opens(0)
        assertTrue(result?.exceptionOrNull() is AvafliError.NoGiveaway)
    }

    @Test
    fun `a submitted claim and no giveaway does not auto-open`() = runTest(dispatcher) {
        stubRegister(claimStatus = "submitted")
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        now += 31 * 60 * 1000L
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        opens(0)
        assertNull(lastClaimAutoPresentAt)

        var result: Result<*>? = null
        Avafli.present(activity) { result = it }
        opens(0)
        assertTrue(result?.exceptionOrNull() is AvafliError.NoGiveaway)
    }

    // ── 1 (cont.). The first addendum's rules still apply ──

    @Test
    fun `hold, the kill switch, NEVER and the throttle still apply without a giveaway`() = runTest(dispatcher) {
        // Held.
        Avafli.holdAutoOpen()
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        opens(0)
        Avafli.releaseAutoOpen()
        opens(1)

        // Throttle on resume.
        Avafli.noteExperienceClosed()
        now += 29 * 60 * 1000L
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        opens(1)
        now += 60 * 1000L
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        opens(2)
        Avafli.noteExperienceClosed()

        // Kill switch.
        storedToken = null
        stubRegister(sdkConfig = SdkConfig(experience = ExperienceConfig(autoOpenEnabled = false)))
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        opens(2)

        // Server NEVER.
        storedToken = null
        stubRegister(sdkConfig = SdkConfig(experience = ExperienceConfig(autoOpenMode = AvafliAutoOpen.NEVER)))
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        opens(2)
    }

    @Test
    fun `an opted-out winner with no giveaway is shown nothing`() = runTest(dispatcher) {
        every { prefs.isOptedOut() } returns true
        every { prefs.getOptedOutUntil() } answers { now + 60 * 60 * 1000L }
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        opens(0)
        var result: Result<*>? = null
        Avafli.present(activity) { result = it }
        assertTrue(result?.exceptionOrNull() is AvafliError.OptedOut)
    }

    // ── 2 + 3. Inside the drawer ──

    @Test
    fun `closing the winner flow leaves no dashboard frame, and the winner can come back`() = runTest(dispatcher) {
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        opens(1)
        storedToken = "tok"
        val (viewModel, screens) = openDrawer()

        // The close button (and system back on the splash) finishes the
        // Activity directly; this is what the Activity then reports.
        Avafli.noteExperienceClosed()

        assertOnlyWinnerFlow(screens)
        assertFalse(viewModel.uiState.value.dismissRequested)
        // Still pending: the next launch brings the winner straight back.
        assertTrue(Avafli.isPrizeClaimPending())
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        opens(2)
    }

    @Test
    fun `the whole flow runs without a giveaway - code, form, submit, confirmation`() = runTest(dispatcher) {
        stubStatus(claim = claim(verification = ClaimVerificationBlock(required = true)))
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        storedToken = "tok"
        coEvery { api.sendClaimVerificationCode("gw-ended", false) } returns
            AvafliApi.SendClaimCodeResponse(sent = true, verification = ClaimVerificationBlock(required = true))
        coEvery { api.confirmClaimVerificationCode("gw-ended", "123456") } returns
            AvafliApi.ConfirmClaimCodeResponse(verified = true, verification = ClaimVerificationBlock(required = false))
        coEvery {
            api.submitPrizeClaim(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )
        } returns AvafliApi.SubmitPrizeClaimResponse("9876543210", "2026-09-29T18:05:00.000Z")

        val (viewModel, screens) = openDrawer()
        viewModel.winnerClaimContinue()
        advanceUntilIdle()
        assertEquals(WinnerClaimStep.Code, viewModel.uiState.value.winnerClaimStep)
        viewModel.submitClaimCode("123456")
        advanceUntilIdle()
        assertEquals(WinnerClaimStep.Form, viewModel.uiState.value.winnerClaimStep)
        viewModel.submitPrizeClaim(validForm)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.winnerClaimStep is WinnerClaimStep.Share)
        viewModel.winnerShareDone()
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        assertEquals(
            WinnerClaimStep.Confirmation("9876543210", "2026-09-29T18:05:00.000Z"),
            ui.winnerClaimStep,
        )
        // DONE on the confirmation is the drawer's dismiss; nothing was ever
        // painted behind the flow on the way here.
        assertOnlyWinnerFlow(screens)
        coVerify(exactly = 1) { api.getActiveGiveaway() }

        // Submitted: no longer pending, so nothing reopens.
        assertFalse(Avafli.isPrizeClaimPending())
        Avafli.noteExperienceClosed()
        now += 31 * 60 * 1000L
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        opens(1)
    }

    @Test
    fun `a claim that turns out expired dismisses the drawer instead of showing an empty state`() = runTest(dispatcher) {
        stubStatus(claim = claim(verification = ClaimVerificationBlock(required = true)))
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        storedToken = "tok"
        coEvery { api.sendClaimVerificationCode(any(), any()) } throws serverError(
            400, "FAILED_PRECONDITION", "The claim window for this prize has expired",
        )

        val (viewModel, screens) = openDrawer()
        viewModel.winnerClaimContinue()
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        assertTrue(ui.dismissRequested)
        assertOnlyWinnerFlow(screens)
        assertNull(ui.dashboardNotice)
        // No reload was made to find something to show behind the flow.
        coVerify(exactly = 1) { api.getActiveGiveaway() }
        assertFalse(Avafli.isPrizeClaimPending())
    }

    @Test
    fun `a rejected submit dismisses the drawer when there is no giveaway`() = runTest(dispatcher) {
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        storedToken = "tok"
        coEvery {
            api.submitPrizeClaim(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )
        } throws serverError(403, "PERMISSION_DENIED", "Not the winner")

        val (viewModel, screens) = openDrawer()
        viewModel.winnerClaimContinue()
        viewModel.submitPrizeClaim(validForm)
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        assertTrue(ui.dismissRequested)
        assertFalse(ui.isSubmittingClaim)
        assertOnlyWinnerFlow(screens)
        coVerify(exactly = 1) { api.getActiveGiveaway() }
    }

    @Test
    fun `with a giveaway running the same rejection still falls back to the dashboard`() = runTest(dispatcher) {
        stubRegister(giveaway = giveaway)
        stubStatus(giveaway = giveaway)
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        storedToken = "tok"
        coEvery {
            api.submitPrizeClaim(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )
        } throws serverError(403, "PERMISSION_DENIED", "Not the winner")
        coEvery { api.claimDailyEntries(any()) } returns AvafliApi.ClaimDailyEntriesResponse(
            entries = 10, streakDay = 1, totalEntries = 10,
            weeklyBonusEntries = null, monthlyBonusEntries = null, milestone = null,
        )

        val (viewModel, _) = openDrawer()
        viewModel.winnerClaimContinue()
        viewModel.submitPrizeClaim(validForm)
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        assertFalse(ui.dismissRequested)
        assertTrue(ui.screen is ExperienceScreen.Streak)
    }

    // ── 5. Offline ──

    @Test
    fun `offline with a pending claim and no giveaway shows the retry state, then the splash`() = runTest(dispatcher) {
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        opens(1)
        storedToken = "tok"
        coEvery { api.getActiveGiveaway() } throws AvafliError.NetworkError("offline")

        val (viewModel, screens) = openDrawer()

        assertEquals(ExperienceScreen.Offline, viewModel.uiState.value.screen)
        assertEquals(listOf(ExperienceScreen.Loading, ExperienceScreen.Offline), screens.distinct())
        coVerify(exactly = 0) { api.claimDailyEntries(any()) }
        assertEquals("We couldn't reach the server.", AvafliV2Strings.OFFLINE_HEADLINE)
        assertEquals("Check your connection and try again.", AvafliV2Strings.OFFLINE_BODY)

        // RETRY with the connection back.
        stubStatus()
        viewModel.retryAfterOffline()
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        assertTrue(ui.screen is ExperienceScreen.WinnerClaim)
        assertEquals(WinnerClaimStep.Splash, ui.winnerClaimStep)
        screens.forEach {
            assertFalse(it is ExperienceScreen.Streak)
            assertFalse(it is ExperienceScreen.EmailCapture)
            assertFalse(it is ExperienceScreen.NoActiveGiveaway)
        }
    }

    @Test
    fun `RETRY that fails again stays on the retry state`() = runTest(dispatcher) {
        coEvery { api.getActiveGiveaway() } throws AvafliError.NetworkError("offline")
        val viewModel = AvafliExperienceViewModel(api, prefs, Logger())
        viewModel.setPrizeClaimPending(true)
        viewModel.load(null)
        advanceUntilIdle()
        assertEquals(ExperienceScreen.Offline, viewModel.uiState.value.screen)

        viewModel.retryAfterOffline()
        advanceUntilIdle()
        assertEquals(ExperienceScreen.Offline, viewModel.uiState.value.screen)
        coVerify(exactly = 2) { api.getActiveGiveaway() }
    }

    @Test
    fun `offline without a pending claim, or with a cached giveaway, is what it always was`() = runTest(dispatcher) {
        coEvery { api.getActiveGiveaway() } throws AvafliError.NetworkError("offline")

        // No pending claim: the retry state is not involved.
        val plain = AvafliExperienceViewModel(api, prefs, Logger())
        plain.load(giveaway)
        advanceUntilIdle()
        assertTrue(plain.uiState.value.screen is ExperienceScreen.Streak)

        // Pending claim but a giveaway in cache: the cached dashboard, as before.
        val cached = AvafliExperienceViewModel(api, prefs, Logger())
        cached.setPrizeClaimPending(true)
        cached.load(giveaway)
        advanceUntilIdle()
        assertTrue(cached.uiState.value.screen is ExperienceScreen.Streak)
    }
}
