package com.avafli.winrsdk

import com.avafli.winrsdk.network.WinrApi
import com.avafli.winrsdk.services.Logger
import com.avafli.winrsdk.storage.PreferencesStorage
import com.avafli.winrsdk.ui.OptOutPhase
import com.avafli.winrsdk.ui.WINRExperienceViewModel
import com.avafli.winrsdk.ui.v2.V2Strings
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The 2.6.1 in-experience privacy opt-out ("Privacy choices" on the
 * how-it-works screen):
 *
 *     Idle → Confirming → InFlight → Done → (screen dismisses the experience)
 *                     ↘ Failed (inline error, retryable) ↗
 *
 * Failure NEVER pretends success — the confirmation stays up with the error
 * and nothing is persisted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExperienceOptOutTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var api: WinrApi
    private lateinit var storage: PreferencesStorage
    private lateinit var viewModel: WINRExperienceViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        api = mockk()
        storage = mockk(relaxed = true)
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

    // ── Raising / cancelling the confirmation ──

    @Test
    fun `privacy choices raises the confirmation from idle only`() {
        assertEquals(OptOutPhase.Idle, viewModel.uiState.value.optOutPhase)
        viewModel.showOptOutConfirmation()
        assertEquals(OptOutPhase.Confirming, viewModel.uiState.value.optOutPhase)
        // Re-tapping while already confirming is a no-op.
        viewModel.showOptOutConfirmation()
        assertEquals(OptOutPhase.Confirming, viewModel.uiState.value.optOutPhase)
    }

    @Test
    fun `cancel returns to idle from confirming`() {
        viewModel.showOptOutConfirmation()
        viewModel.cancelOptOut()
        assertEquals(OptOutPhase.Idle, viewModel.uiState.value.optOutPhase)
    }

    @Test
    fun `confirm from idle is a no-op`() = runTest(dispatcher) {
        viewModel.confirmOptOut()
        advanceUntilIdle()
        assertEquals(OptOutPhase.Idle, viewModel.uiState.value.optOutPhase)
    }

    // ── Confirm → success ──

    @Test
    fun `successful opt-out persists the silence flag and lands on Done`() = runTest(dispatcher) {
        coEvery { api.optOut() } returns true
        viewModel.showOptOutConfirmation()
        viewModel.confirmOptOut()
        assertEquals(OptOutPhase.InFlight, viewModel.uiState.value.optOutPhase)
        advanceUntilIdle()

        assertEquals(OptOutPhase.Done, viewModel.uiState.value.optOutPhase)
        verify(exactly = 1) { storage.saveOptedOut(true) }
    }

    // ── Confirm → failure: honest, retryable, nothing persisted ──

    @Test
    fun `failed opt-out shows the connection error and persists nothing`() = runTest(dispatcher) {
        coEvery { api.optOut() } throws RuntimeException("boom")
        viewModel.showOptOutConfirmation()
        viewModel.confirmOptOut()
        advanceUntilIdle()

        assertEquals(
            OptOutPhase.Failed(V2Strings.OPT_OUT_FAILED),
            viewModel.uiState.value.optOutPhase,
        )
        verify(exactly = 0) { storage.saveOptedOut(any()) }
    }

    @Test
    fun `backend success=false is a failure, not a pretended success`() = runTest(dispatcher) {
        coEvery { api.optOut() } returns false
        viewModel.showOptOutConfirmation()
        viewModel.confirmOptOut()
        advanceUntilIdle()

        assertEquals(
            OptOutPhase.Failed(V2Strings.OPT_OUT_FAILED),
            viewModel.uiState.value.optOutPhase,
        )
        verify(exactly = 0) { storage.saveOptedOut(any()) }
    }

    @Test
    fun `the destructive button retries from Failed`() = runTest(dispatcher) {
        coEvery { api.optOut() } throws RuntimeException("boom")
        viewModel.showOptOutConfirmation()
        viewModel.confirmOptOut()
        advanceUntilIdle()

        coEvery { api.optOut() } returns true
        viewModel.confirmOptOut()
        advanceUntilIdle()
        assertEquals(OptOutPhase.Done, viewModel.uiState.value.optOutPhase)
    }
}
