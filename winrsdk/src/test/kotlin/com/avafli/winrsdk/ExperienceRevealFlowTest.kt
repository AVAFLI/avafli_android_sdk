package com.avafli.winrsdk

import com.avafli.winrsdk.domain.Giveaway
import com.avafli.winrsdk.network.WinrApi
import com.avafli.winrsdk.services.Logger
import com.avafli.winrsdk.storage.PreferencesStorage
import com.avafli.winrsdk.ui.ExperienceScreen
import com.avafli.winrsdk.ui.WINRExperienceViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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

/**
 * Pins the V2 Day 2+ predicted-reveal flow: the celebration is the dashboard's
 * FIRST visible frame. The pre-claim status response stages a PREDICTED grant
 * (ladder value for today's streak day; postTotal = preTotal + predicted), the
 * reveal fires one composition beat (~0.15s) later, and the real claim
 * reconciles silently in the background — no second celebration, and a failed
 * claim settles back to server truth quietly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExperienceRevealFlowTest {

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

    /** Day 3 open, 100 entries banked, claim still in flight. */
    private fun stubDay3Open() {
        coEvery { api.getActiveGiveaway() } returns WinrApi.GetActiveGiveawayResponse(
            giveaway = giveaway,
            claimedToday = false,
            streakDay = 3,
            totalEntries = 100,
        )
    }

    @Test
    fun `day2plus open stages predicted grant before the dashboard state`() = runTest(dispatcher) {
        stubDay3Open()
        coEvery { api.claimDailyEntries(any()) } coAnswers {
            delay(1_000) // claim still in flight while the reveal plays
            WinrApi.ClaimDailyEntriesResponse(
                entries = 3, streakDay = 3, totalEntries = 103,
                weeklyBonusEntries = null, monthlyBonusEntries = null, milestone = null,
            )
        }

        viewModel.load()
        runCurrent()

        val ui = viewModel.uiState.value
        val screen = ui.screen as ExperienceScreen.Streak
        // Predicted grant: ladder value for streakDay 3; postTotal = 100 + 3.
        assertNotNull(ui.pendingRevealGrant)
        assertEquals(3, ui.pendingRevealGrant?.entries)
        assertEquals(103, ui.pendingRevealGrant?.totalEntries)
        assertEquals(100, ui.preClaimTotalEntries)
        assertEquals(103, screen.streakState.totalEntries)
        assertTrue(ui.claimedToday)
        // Staged, not yet revealed: the first frame is the animation baseline.
        assertFalse(ui.claimRevealed)
    }

    @Test
    fun `reveal fires one composition beat after mount without waiting for the claim`() =
        runTest(dispatcher) {
            stubDay3Open()
            coEvery { api.claimDailyEntries(any()) } coAnswers {
                delay(10_000) // claim MUCH slower than the reveal beat
                WinrApi.ClaimDailyEntriesResponse(
                    entries = 3, streakDay = 3, totalEntries = 103,
                    weeklyBonusEntries = null, monthlyBonusEntries = null, milestone = null,
                )
            }

            viewModel.load()
            runCurrent()
            assertFalse(viewModel.uiState.value.claimRevealed)

            advanceTimeBy(WINRExperienceViewModel.AUTO_REVEAL_DELAY_MS + 1)
            runCurrent()
            assertTrue(viewModel.uiState.value.claimRevealed)
        }

    @Test
    fun `real claim reconciles silently with no second celebration`() = runTest(dispatcher) {
        stubDay3Open()
        coEvery { api.claimDailyEntries(any()) } coAnswers {
            delay(1_000)
            // Server truth differs from the prediction: a weekly bonus rode along.
            WinrApi.ClaimDailyEntriesResponse(
                entries = 3, streakDay = 3, totalEntries = 108,
                weeklyBonusEntries = 5, monthlyBonusEntries = null, milestone = null,
            )
        }

        viewModel.load()
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        val screen = ui.screen as ExperienceScreen.Streak
        // Reconciled in place: server totals, grant carries base + bonuses.
        assertEquals(108, screen.streakState.totalEntries)
        assertEquals(8, ui.pendingRevealGrant?.entries)
        assertEquals(100, ui.preClaimTotalEntries)
        assertTrue(ui.claimedToday)
        // No second celebration: the predicted reveal stays played.
        assertTrue(ui.claimRevealed)
    }

    @Test
    fun `failed claim settles back to server truth quietly`() = runTest(dispatcher) {
        stubDay3Open()
        coEvery { api.claimDailyEntries(any()) } coAnswers {
            delay(1_000)
            throw RuntimeException("network down")
        }

        viewModel.load()
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        val screen = ui.screen as ExperienceScreen.Streak
        // Prediction dropped; pre-claim numbers, unclaimed state, no reveal.
        assertEquals(100, screen.streakState.totalEntries)
        assertFalse(ui.claimedToday)
        assertNull(ui.pendingRevealGrant)
        assertNull(ui.preClaimTotalEntries)
        assertFalse(ui.claimRevealed)
    }

    @Test
    fun `day1 open celebrates in place - no modal state`() = runTest(dispatcher) {
        // Unified Day-1 flow (Aug 2026 CTO decision): the "You're in!" modal
        // is gone — Day 1 stages the grant and celebrates on the dashboard
        // exactly like Day 2+, only the toast headline differs.
        coEvery { api.getActiveGiveaway() } returns WinrApi.GetActiveGiveawayResponse(
            giveaway = giveaway,
            claimedToday = false,
            streakDay = 1,
            totalEntries = 0,
        )
        coEvery { api.claimDailyEntries(any()) } returns WinrApi.ClaimDailyEntriesResponse(
            entries = 1, streakDay = 1, totalEntries = 1,
            weeklyBonusEntries = null, monthlyBonusEntries = null, milestone = null,
        )

        viewModel.load()
        runCurrent()
        // Day 1 stages the predicted grant like any other unclaimed open.
        val staged = viewModel.uiState.value
        assertTrue(staged.screen is ExperienceScreen.Streak)
        assertEquals(1, staged.pendingRevealGrant?.entries)
        assertEquals(0, staged.preClaimTotalEntries)

        advanceUntilIdle()
        val ui = viewModel.uiState.value
        // Still the dashboard — reconciled in place, never a modal state.
        val screen = ui.screen as ExperienceScreen.Streak
        assertEquals(1, screen.streakState.totalEntries)
        assertTrue(ui.claimedToday)
        assertEquals(1, ui.pendingRevealGrant?.entries)
        assertEquals(0, ui.preClaimTotalEntries)
        assertTrue(ui.claimRevealed)
    }

    @Test
    fun `email path claims before the dashboard and stages the real grant`() = runTest(dispatcher) {
        // Day-1 email submit: the claim is awaited INSIDE the re-load (capture
        // spinner still up), the dashboard mounts with the REAL grant staged
        // (0 → N count-up under the "YOU'RE IN!" toast), and the background
        // auto-claim is skipped — exactly one claim call.
        coEvery { api.submitEmail(any(), any(), any()) } returns WinrApi.SubmitEmailResult(success = true)
        coEvery { api.getActiveGiveaway() } returns WinrApi.GetActiveGiveawayResponse(
            giveaway = giveaway,
            claimedToday = false,
            streakDay = 1,
            totalEntries = 0,
        )
        coEvery { api.claimDailyEntries(any()) } returns WinrApi.ClaimDailyEntriesResponse(
            entries = 1, streakDay = 1, totalEntries = 1,
            weeklyBonusEntries = null, monthlyBonusEntries = null, milestone = null,
        )

        viewModel.submitEmail("ada@example.com")
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        val screen = ui.screen as ExperienceScreen.Streak
        assertTrue(ui.claimedToday)
        assertEquals(1, screen.streakState.totalEntries)
        // The staged grant is the REAL claim response, from a 0 baseline.
        assertEquals(1, ui.pendingRevealGrant?.entries)
        assertEquals(0, ui.preClaimTotalEntries)
        coVerify(exactly = 1) { api.claimDailyEntries(any()) }
    }

    @Test
    fun `already claimed open rests on the settled dashboard`() = runTest(dispatcher) {
        coEvery { api.getActiveGiveaway() } returns WinrApi.GetActiveGiveawayResponse(
            giveaway = giveaway,
            claimedToday = true,
            streakDay = 3,
            totalEntries = 103,
        )

        viewModel.load()
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        val screen = ui.screen as ExperienceScreen.Streak
        assertEquals(103, screen.streakState.totalEntries)
        assertTrue(ui.claimedToday)
        assertNull(ui.pendingRevealGrant)
    }
}
