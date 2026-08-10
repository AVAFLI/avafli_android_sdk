package com.avafli.winrsdk

import com.avafli.winrsdk.domain.Giveaway
import com.avafli.winrsdk.network.WinrApi
import com.avafli.winrsdk.services.Logger
import com.avafli.winrsdk.storage.PreferencesStorage
import com.avafli.winrsdk.ui.ExperienceScreen
import com.avafli.winrsdk.ui.WINRExperienceViewModel
import com.avafli.winrsdk.ui.v2.V2Strings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Pins the 2.6.0 user-facing error messaging (Master Field List "User Message
 * (UI)" column): honest failure states — a failed email submit never proceeds
 * as success, a failed auto-claim says so with a retry, a duplicate same-day
 * entry is announced, and geo-block / session-expiry get dedicated states.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExperienceErrorMessagingTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var api: WinrApi
    private lateinit var storage: PreferencesStorage
    private lateinit var viewModel: WINRExperienceViewModel

    private val ladder = listOf(1, 2, 3, 5, 8, 13, 21)
    private val giveaway = Giveaway(
        id = "g1",
        title = "Test",
        prizeDescription = "Win!",
        streakLadder = ladder,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        api = mockk()
        storage = mockk(relaxed = true)
        every { storage.isEmailSubmitted() } returns true
        every { storage.getStreakDay() } returns 0
        every { storage.getTotalEntries() } returns 0
        every { storage.getLastClaimDate() } returns null
        viewModel = WINRExperienceViewModel(
            api = api,
            preferencesStorage = storage,
            logger = Logger(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stubOpen(claimedToday: Boolean = false, streakDay: Int = 3, total: Int = 100) {
        coEvery { api.getActiveGiveaway() } returns WinrApi.GetActiveGiveawayResponse(
            giveaway = giveaway,
            claimedToday = claimedToday,
            streakDay = streakDay,
            totalEntries = total,
        )
    }

    // ── Failed email submit is no longer swallowed ──

    @Test
    fun `failed email submit keeps the user on capture with an inline error`() = runTest(dispatcher) {
        every { storage.isEmailSubmitted() } returns false
        stubOpen()
        coEvery { api.submitEmail(any(), any(), any(), any()) } throws RuntimeException("boom")

        viewModel.load()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.screen is ExperienceScreen.EmailCapture)

        viewModel.submitEmail("ada@example.com", marketingConsent = false, ageConfirmed = true)
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        assertTrue(ui.screen is ExperienceScreen.EmailCapture)
        assertEquals(V2Strings.EMAIL_SUBMIT_FAILED, ui.emailSubmitError)
        assertFalse(ui.isSubmittingEmail)
        // The registration flag is only earned by an ACCEPTED submit.
        verify(exactly = 0) { storage.saveEmailSubmitted(true) }
    }

    @Test
    fun `successful email submit persists the flag and clears the error`() = runTest(dispatcher) {
        every { storage.isEmailSubmitted() } returns false
        stubOpen(claimedToday = true, streakDay = 1, total = 1)
        coEvery { api.submitEmail(any(), any(), any(), any()) } returns
            WinrApi.SubmitEmailResult(success = true)

        viewModel.load()
        advanceUntilIdle()
        viewModel.submitEmail("ada@example.com", marketingConsent = false, ageConfirmed = true)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.emailSubmitError)
        verify(exactly = 1) { storage.saveEmailSubmitted(true) }
    }

    // ── Geo-blocked and session-expired dedicated states ──

    @Test
    fun `geo-blocked email submit routes to the dedicated geo state`() = runTest(dispatcher) {
        every { storage.isEmailSubmitted() } returns false
        stubOpen()
        coEvery { api.submitEmail(any(), any(), any(), any()) } throws WINRError.GeoBlocked()

        viewModel.load()
        advanceUntilIdle()
        viewModel.submitEmail("ada@example.com", marketingConsent = false, ageConfirmed = true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.screen is ExperienceScreen.GeoBlocked)
    }

    @Test
    fun `geo-blocked auto-claim routes to the dedicated geo state`() = runTest(dispatcher) {
        stubOpen()
        coEvery { api.claimDailyEntries(any()) } throws WINRError.GeoBlocked()

        viewModel.load()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.screen is ExperienceScreen.GeoBlocked)
    }

    @Test
    fun `token refresh failure on load routes to the session-expired state`() = runTest(dispatcher) {
        coEvery { api.getActiveGiveaway() } throws WINRError.TokenRefreshFailed()

        viewModel.load()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.screen is ExperienceScreen.SessionExpired)
    }

    // ── Duplicate same-day entry notice ──

    @Test
    fun `already-claimed rejection the device did not know about shows the duplicate notice`() =
        runTest(dispatcher) {
            stubOpen() // status says unclaimed → auto-claim fires
            coEvery { api.claimDailyEntries(any()) } throws WINRError.AlreadyClaimed()

            viewModel.load()
            advanceTimeBy(1_000)
            runCurrent()

            val ui = viewModel.uiState.value
            assertTrue(ui.screen is ExperienceScreen.Streak)
            assertTrue(ui.claimedToday)
            // The staged celebration is dropped — no confetti for a grant that
            // did not happen.
            assertNull(ui.pendingRevealGrant)
            val notice = ui.dashboardNotice
            assertNotNull(notice)
            assertEquals(V2Strings.ALREADY_ENTERED_TODAY, notice?.message)
            assertFalse(notice!!.retryable)

            // Transient: it auto-dismisses.
            advanceTimeBy(WINRExperienceViewModel.DASHBOARD_NOTICE_AUTO_DISMISS_MS + 1_000)
            runCurrent()
            assertNull(viewModel.uiState.value.dashboardNotice)
        }

    @Test
    fun `already-claimed rejection the device already knew about stays silent`() =
        runTest(dispatcher) {
            every { storage.getLastClaimDate() } returns LocalDate.now().toString()
            stubOpen()
            coEvery { api.claimDailyEntries(any()) } throws WINRError.AlreadyClaimed()

            viewModel.load()
            advanceTimeBy(1_000)
            runCurrent()

            val ui = viewModel.uiState.value
            assertTrue(ui.claimedToday)
            assertNull(ui.dashboardNotice)
        }

    // ── Auto-claim transport failure: honest unclaimed state + retry ──

    @Test
    fun `auto-claim transport failure shows the not-recorded notice with retry`() =
        runTest(dispatcher) {
            stubOpen()
            coEvery { api.claimDailyEntries(any()) } throws RuntimeException("network down")

            viewModel.load()
            advanceUntilIdle()

            val ui = viewModel.uiState.value
            val screen = ui.screen as ExperienceScreen.Streak
            // Honest rollback (pre-existing behavior): unclaimed, pre-claim totals.
            assertFalse(ui.claimedToday)
            assertEquals(100, screen.streakState.totalEntries)
            assertNull(ui.pendingRevealGrant)
            // New: the failure is SAID, with a retry affordance.
            val notice = ui.dashboardNotice
            assertNotNull(notice)
            assertEquals(V2Strings.ENTRY_NOT_RECORDED, notice?.message)
            assertTrue(notice!!.retryable)
        }

    @Test
    fun `retry after a transport failure claims and clears the notice`() = runTest(dispatcher) {
        stubOpen()
        var fail = true
        coEvery { api.claimDailyEntries(any()) } answers {
            if (fail) throw RuntimeException("network down")
            WinrApi.ClaimDailyEntriesResponse(
                entries = 3, streakDay = 3, totalEntries = 103,
                weeklyBonusEntries = null, monthlyBonusEntries = null, milestone = null,
            )
        }

        viewModel.load()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.dashboardNotice)

        fail = false
        viewModel.retryDailyClaim()
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        assertNull(ui.dashboardNotice)
        assertTrue(ui.claimedToday)
        assertEquals(103, (ui.screen as ExperienceScreen.Streak).streakState.totalEntries)
    }
}
