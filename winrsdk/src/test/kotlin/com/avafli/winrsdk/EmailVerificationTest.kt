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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Soft email verification (2.7.0). Pins the unverified-state exposure that
 * drives the persistent "Verify your email" dashboard chip: ONLY an explicit
 * `emailVerified == false` from the backend marks the person unverified — every
 * other case (absent/null, or true) leaves the chip hidden. Also covers the
 * post-confirm transition back to the dashboard with the chip cleared.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmailVerificationTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var api: WinrApi
    private lateinit var storage: PreferencesStorage
    private lateinit var viewModel: WINRExperienceViewModel

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
        every { storage.isEmailSubmitted() } returns true
        every { storage.getStreakDay() } returns 3
        every { storage.getTotalEntries() } returns 103
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

    /** Already-claimed open so the load rests on the dashboard (no claim call). */
    private fun stubStatus(emailVerified: Boolean?) {
        coEvery { api.getActiveGiveaway() } returns WinrApi.GetActiveGiveawayResponse(
            giveaway = giveaway,
            claimedToday = true,
            streakDay = 3,
            totalEntries = 103,
            emailVerified = emailVerified,
        )
    }

    @Test
    fun `emailVerified false marks the state unverified - chip shows`() = runTest(dispatcher) {
        stubStatus(emailVerified = false)

        viewModel.load()
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        assertTrue(ui.screen is ExperienceScreen.Streak)
        assertTrue("explicit false must expose unverified", ui.unverified)
    }

    @Test
    fun `absent emailVerified leaves the state verified - chip hidden`() = runTest(dispatcher) {
        stubStatus(emailVerified = null)

        viewModel.load()
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        assertTrue(ui.screen is ExperienceScreen.Streak)
        assertFalse("absent/null must NOT expose unverified", ui.unverified)
    }

    @Test
    fun `emailVerified true leaves the state verified - chip hidden`() = runTest(dispatcher) {
        stubStatus(emailVerified = true)

        viewModel.load()
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        assertFalse(ui.unverified)
    }

    @Test
    fun `successful confirm clears unverified and returns to the dashboard`() = runTest(dispatcher) {
        stubStatus(emailVerified = false)
        coEvery { api.confirmEmailVerification(any()) } returns true

        viewModel.load()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.unverified)

        // Open the verification screen, then confirm a code.
        viewModel.showEmailVerification()
        assertTrue(viewModel.uiState.value.screen is ExperienceScreen.EmailVerify)

        viewModel.submitEmailVerificationCode("123456")
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        assertFalse("verify clears the chip", ui.unverified)
        assertTrue("verify returns to the dashboard", ui.screen is ExperienceScreen.Streak)
    }

    @Test
    fun `cancel returns to the dashboard with the chip still showing`() = runTest(dispatcher) {
        stubStatus(emailVerified = false)

        viewModel.load()
        advanceUntilIdle()

        viewModel.showEmailVerification()
        assertTrue(viewModel.uiState.value.screen is ExperienceScreen.EmailVerify)

        viewModel.cancelEmailVerification()

        val ui = viewModel.uiState.value
        assertTrue(ui.screen is ExperienceScreen.Streak)
        assertTrue("cancel gates nothing — chip stays", ui.unverified)
    }
}
