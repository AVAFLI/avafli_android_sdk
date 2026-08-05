package com.avafli.winrsdk

import com.avafli.winrsdk.domain.Giveaway
import com.avafli.winrsdk.network.WinrApi
import com.avafli.winrsdk.services.Logger
import com.avafli.winrsdk.storage.PreferencesStorage
import com.avafli.winrsdk.ui.ExperienceScreen
import com.avafli.winrsdk.ui.WINRExperienceViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins the 2.3.3 cache-first render: when the device already holds a giveaway
 * config AND a persisted streak AND the user has consented, the drawer paints
 * the real dashboard on its FIRST frame — before any network call resolves —
 * and the fresh response reconciles silently in place. Everything else (cold
 * cache, no streak, and above all an UNCONSENTED user) keeps the skeleton up
 * and takes the genuine loading path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CacheFirstRenderTest {

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
        // The warm device: consented, four days of streak, 100 entries banked.
        every { storage.isEmailSubmitted() } returns true
        every { storage.getStreakDay() } returns 4
        every { storage.getTotalEntries() } returns 100
        every { storage.getLastClaimDate() } returns null
        every { storage.getCompletedDays() } returns List(7) { false }
        viewModel = WINRExperienceViewModel(
            api = api,
            preferencesStorage = storage,
            // Relaxed: the error paths under test call Logger.error, and
            // android.util.Log is not available to plain JVM unit tests.
            logger = mockk<Logger>(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A getActiveGiveaway that never lands within the window under test. */
    private fun stubSlowNetwork() {
        coEvery { api.getActiveGiveaway() } coAnswers {
            delay(10_000)
            WinrApi.GetActiveGiveawayResponse(
                giveaway = giveaway,
                claimedToday = false,
                streakDay = 4,
                totalEntries = 100,
            )
        }
        coEvery { api.claimDailyEntries(any()) } returns WinrApi.ClaimDailyEntriesResponse(
            entries = 5, streakDay = 4, totalEntries = 105,
            weeklyBonusEntries = null, monthlyBonusEntries = null, milestone = null,
        )
    }

    @Test
    fun `warm cache paints the dashboard before any network call resolves`() = runTest(dispatcher) {
        stubSlowNetwork()

        viewModel.load(giveaway)
        // No runCurrent / advanceUntilIdle: nothing asynchronous has run yet.
        val ui = viewModel.uiState.value
        val screen = ui.screen as ExperienceScreen.Streak
        assertEquals(4, screen.streakState.currentDay)
        assertEquals(100, screen.streakState.totalEntries)
        assertEquals(5, screen.entriesToday) // ladder[3]
        assertEquals(ladder, screen.ladder)
        assertEquals(giveaway, ui.giveaway)
        assertEquals(4, ui.displayStreakDay)
        assertEquals(100, ui.displayTotalEntries)
    }

    @Test
    fun `unconsented user never sees a cached dashboard`() = runTest(dispatcher) {
        every { storage.isEmailSubmitted() } returns false
        stubSlowNetwork()

        viewModel.load(giveaway)

        // The email gate outranks the cache: skeleton stays up, and the load
        // lands on email capture, never on a dashboard.
        assertTrue(viewModel.uiState.value.screen is ExperienceScreen.Loading)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.screen is ExperienceScreen.EmailCapture)
    }

    @Test
    fun `no cached giveaway keeps the skeleton up`() = runTest(dispatcher) {
        stubSlowNetwork()

        viewModel.load(null)

        assertTrue(viewModel.uiState.value.screen is ExperienceScreen.Loading)
    }

    @Test
    fun `no persisted streak keeps the skeleton up`() = runTest(dispatcher) {
        every { storage.getStreakDay() } returns 0
        stubSlowNetwork()

        viewModel.load(giveaway)

        assertTrue(viewModel.uiState.value.screen is ExperienceScreen.Loading)
    }

    @Test
    fun `cache render never stages a celebration of its own`() = runTest(dispatcher) {
        stubSlowNetwork()

        viewModel.load(giveaway)

        // A cache render is a plain resting dashboard: the reveal flags are
        // untouched, so the celebration is staged only by the network path and
        // therefore can only ever fire once.
        val ui = viewModel.uiState.value
        assertEquals(null, ui.pendingRevealGrant)
        assertEquals(false, ui.claimRevealed)
        assertEquals(null, ui.preClaimTotalEntries)
    }

    @Test
    fun `fresh response reconciles the cache-rendered dashboard silently`() = runTest(dispatcher) {
        // Server truth differs from the cache (day 5, 200 banked) — it must
        // replace the cached numbers in place, on the same dashboard.
        coEvery { api.getActiveGiveaway() } returns WinrApi.GetActiveGiveawayResponse(
            giveaway = giveaway,
            claimedToday = false,
            streakDay = 5,
            totalEntries = 200,
        )
        coEvery { api.claimDailyEntries(any()) } returns WinrApi.ClaimDailyEntriesResponse(
            entries = 8, streakDay = 5, totalEntries = 208,
            weeklyBonusEntries = null, monthlyBonusEntries = null, milestone = null,
        )

        viewModel.load(giveaway)
        val cached = viewModel.uiState.value.screen as ExperienceScreen.Streak
        assertEquals(4, cached.streakState.currentDay)

        advanceUntilIdle()

        val ui = viewModel.uiState.value
        val screen = ui.screen as ExperienceScreen.Streak
        assertEquals(5, screen.streakState.currentDay)
        assertEquals(208, screen.streakState.totalEntries)
        assertTrue(ui.claimedToday)
        // The celebration staged after the cache render still plays — exactly
        // once, off the single staged grant.
        assertNotNull(ui.pendingRevealGrant)
        assertEquals(8, ui.pendingRevealGrant?.entries)
        assertEquals(200, ui.preClaimTotalEntries)
        assertTrue(ui.claimRevealed)
    }

    @Test
    fun `local streak failure after a cache render keeps the dashboard`() = runTest(dispatcher) {
        // Offline (no fresh status), and the local streak engine then blows up
        // reading storage: the live cache-rendered dashboard must survive it.
        coEvery { api.getActiveGiveaway() } throws RuntimeException("network down")
        coEvery { api.claimDailyEntries(any()) } throws RuntimeException("network down")
        every { storage.getStreakDay() } returns 4 andThenThrows RuntimeException("prefs corrupt")

        viewModel.load(giveaway)
        assertTrue(viewModel.uiState.value.screen is ExperienceScreen.Streak)

        advanceUntilIdle()

        val screen = viewModel.uiState.value.screen as ExperienceScreen.Streak
        assertEquals(4, screen.streakState.currentDay)
    }

    @Test
    fun `local streak failure without a cache render surfaces the error screen`() =
        runTest(dispatcher) {
            // Same failure, but nothing was painted from cache (no giveaway to
            // hydrate from) — the user must be told, not left on a skeleton.
            coEvery { api.getActiveGiveaway() } throws RuntimeException("network down")
            every { storage.getStreakDay() } throws RuntimeException("prefs corrupt")

            viewModel.load(null)
            assertTrue(viewModel.uiState.value.screen is ExperienceScreen.Loading)

            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.screen is ExperienceScreen.Error)
        }

    @Test
    fun `a resolved screen is never stomped by a late cache hydrate`() = runTest(dispatcher) {
        coEvery { api.getActiveGiveaway() } returns WinrApi.GetActiveGiveawayResponse(
            giveaway = null,
            claimedToday = null,
            streakDay = null,
            totalEntries = null,
        )

        viewModel.load(null)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.screen is ExperienceScreen.NoActiveGiveaway)

        // A cache hydrate arriving after fresh truth resolved is a no-op.
        viewModel.hydrateFromCache(giveaway)
        runCurrent()
        assertTrue(viewModel.uiState.value.screen is ExperienceScreen.NoActiveGiveaway)
    }
}
