package com.avafli.avaflisdk

import com.avafli.avaflisdk.domain.Giveaway
import com.avafli.avaflisdk.network.AvafliApi
import com.avafli.avaflisdk.services.Logger
import com.avafli.avaflisdk.storage.PreferencesStorage
import com.avafli.avaflisdk.ui.AvafliExperienceViewModel
import com.avafli.avaflisdk.ui.ExperienceScreen
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
 * Sept 2026 field report: a device with a parked cross-device link painted
 * the cached "day 1" dashboard for the network round-trips, mailed a code,
 * and only then switched to the code screen. With the backend's consent echo
 * seeded locally, later opens skipped the code screen entirely. The code
 * screen must be the FIRST frame, before the email gate, and codes must not
 * be re-sent on every open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdoptionReentryFirstFrameTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var api: AvafliApi
    private lateinit var storage: PreferencesStorage
    private lateinit var viewModel: AvafliExperienceViewModel

    private val giveaway = Giveaway(
        id = "g1",
        title = "Test",
        prizeDescription = "Win!",
        streakLadder = listOf(1, 2, 3, 5, 8, 13, 21),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        api = mockk()
        storage = mockk(relaxed = true)
        // The exact state of the field report: warm cache AND the local
        // consent flag already seeded from the backend's echo.
        every { storage.isEmailSubmitted() } returns true
        every { storage.getStreakDay() } returns 4
        every { storage.getTotalEntries() } returns 100
        every { storage.getLastClaimDate() } returns null
        every { storage.getCompletedDays() } returns List(7) { false }
        every { storage.getString("winr_adoption_code_sent_at") } returns null
        viewModel = AvafliExperienceViewModel(
            api = api,
            preferencesStorage = storage,
            logger = mockk<Logger>(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stubSlowNetwork() {
        coEvery { api.getActiveGiveaway() } coAnswers {
            delay(10_000)
            AvafliApi.GetActiveGiveawayResponse(giveaway = giveaway, claimedToday = false, streakDay = 4, totalEntries = 100)
        }
    }

    private fun stubFastNetwork() {
        coEvery { api.getActiveGiveaway() } returns
            AvafliApi.GetActiveGiveawayResponse(giveaway = giveaway, claimedToday = false, streakDay = 4, totalEntries = 100)
        coEvery { api.restageAdoption() } returns true
    }

    @Test
    fun `a parked link paints the code screen as the first frame, never the cached dashboard`() {
        stubSlowNetwork()
        viewModel.setAdoptionPending(true)
        viewModel.load(giveaway)
        val screen = viewModel.uiState.value.screen
        assertTrue("expected CodeEntry, got $screen", screen is ExperienceScreen.CodeEntry)
        assertTrue((screen as ExperienceScreen.CodeEntry).restaged)
    }

    @Test
    fun `the same warm consented cache without a parked link still paints the dashboard first`() {
        stubSlowNetwork()
        viewModel.setAdoptionPending(false)
        viewModel.load(giveaway)
        assertTrue(viewModel.uiState.value.screen is ExperienceScreen.Streak)
    }

    @Test
    fun `the re-entry runs before the email gate and re-sends a stale code`() {
        stubFastNetwork()
        viewModel.setAdoptionPending(true)
        viewModel.load(giveaway)
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { api.restageAdoption() }
        verify { storage.putString("winr_adoption_code_sent_at", any()) }
        assertTrue(viewModel.uiState.value.screen is ExperienceScreen.CodeEntry)
    }

    @Test
    fun `a code mailed inside the cooldown is not re-sent, the code screen still shows`() {
        stubFastNetwork()
        every { storage.getString("winr_adoption_code_sent_at") } returns System.currentTimeMillis().toString()
        viewModel.setAdoptionPending(true)
        viewModel.load(giveaway)
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { api.restageAdoption() }
        assertEquals(ExperienceScreen.CodeEntry(email = "", restaged = true), viewModel.uiState.value.screen)
    }
}