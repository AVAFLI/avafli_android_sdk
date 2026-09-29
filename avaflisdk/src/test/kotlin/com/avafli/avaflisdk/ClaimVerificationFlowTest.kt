package com.avafli.avaflisdk

import com.avafli.avaflisdk.domain.ClaimVerificationBlock
import com.avafli.avaflisdk.domain.Giveaway
import com.avafli.avaflisdk.domain.PrizeClaimBlock
import com.avafli.avaflisdk.domain.PrizeClaimForm
import com.avafli.avaflisdk.network.AvafliApi
import com.avafli.avaflisdk.services.Logger
import com.avafli.avaflisdk.storage.PreferencesStorage
import com.avafli.avaflisdk.ui.AvafliExperienceViewModel
import com.avafli.avaflisdk.ui.ClaimCodeSend
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

/**
 * Claim email-ownership step (3.2.0): a winner enters a six-digit code sent
 * to the address on file before the claim form opens.
 *
 * Non-negotiables under test: the SERVER holds all state (a fresh view-model
 * resumes from the block alone, and nothing is written to local storage), an
 * absent block is today's flow, and no failure is a dead end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClaimVerificationFlowTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var api: AvafliApi
    private lateinit var storage: PreferencesStorage

    /** The device clock the view-model reads (2026-09-29T18:00:00Z). */
    private var now = Instant.parse("2026-09-29T18:00:00Z").toEpochMilli()

    private val giveaway = Giveaway(id = "gw-123", title = "Win", prizeDescription = "Cash")
    private val masked = "d********r@avafli.example.com"

    private fun iso(ms: Long) = Instant.ofEpochMilli(ms).toString()

    private fun claim(verification: ClaimVerificationBlock?) = PrizeClaimBlock(
        status = "pending",
        giveawayId = "gw-123",
        prizeDescription = "Cash",
        prizeValue = 1000.0,
        maskedEmail = masked,
        verification = verification,
    )

    /** A block whose code went out [sentAgoMs] ago. */
    private fun liveCode(sentAgoMs: Long = 0L): ClaimVerificationBlock {
        val sent = now - sentAgoMs
        return ClaimVerificationBlock(
            required = true,
            codeSentAt = iso(sent),
            codeExpiresAt = iso(sent + 10 * 60 * 1000L),
            resendAvailableAt = iso(sent + 60 * 1000L),
        )
    }

    private val validForm = PrizeClaimForm(
        firstName = "Ada",
        lastName = "Lovelace",
        phone = "5551234567",
        street = "1 Analytical Way",
        apt = "4B",
        city = "Brooklyn",
        state = "New York",
        zip = "11201",
        photoBase64 = "aGVsbG8=",
        authorizesLikeness = true,
    )

    private fun serverError(
        http: Int,
        status: String,
        message: String,
        details: String? = null,
    ) = AvafliError.ServerError(
        http,
        """{"error":{"status":"$status","message":"$message"${details?.let { ""","details":$it""" } ?: ""}}}""",
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        api = mockk()
        storage = mockk(relaxed = true)
        every { storage.isEmailSubmitted() } returns true
        every { storage.getLastClaimDate() } returns null
        coEvery { api.claimDailyEntries(any()) } returns AvafliApi.ClaimDailyEntriesResponse(
            entries = 10, streakDay = 1, totalEntries = 10,
            weeklyBonusEntries = null, monthlyBonusEntries = null, milestone = null,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = AvafliExperienceViewModel(
        api = api,
        preferencesStorage = storage,
        logger = Logger(),
    ).also { it.nowMs = { now } }

    /** A view-model opened on the winner splash with [verification] in its block. */
    private fun TestScope.openOnSplash(verification: ClaimVerificationBlock?): AvafliExperienceViewModel {
        coEvery { api.getActiveGiveaway() } returns AvafliApi.GetActiveGiveawayResponse(
            giveaway = giveaway,
            claimedToday = true,
            prizeClaim = claim(verification),
        )
        val viewModel = newViewModel()
        viewModel.load()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.screen is ExperienceScreen.WinnerClaim)
        assertEquals(WinnerClaimStep.Splash, viewModel.uiState.value.winnerClaimStep)
        return viewModel
    }

    private fun stubSend(sent: Boolean, block: ClaimVerificationBlock? = liveCode()) {
        coEvery { api.sendClaimVerificationCode("gw-123", any()) } returns
            AvafliApi.SendClaimCodeResponse(sent = sent, verification = block)
    }

    /** A view-model sitting on the code screen with its open-time send settled. */
    private fun TestScope.openOnCode(sent: Boolean = true): AvafliExperienceViewModel {
        stubSend(sent)
        val viewModel = openOnSplash(ClaimVerificationBlock(required = true))
        viewModel.winnerClaimContinue()
        advanceUntilIdle()
        assertEquals(WinnerClaimStep.Code, viewModel.uiState.value.winnerClaimStep)
        return viewModel
    }

    private fun AvafliExperienceViewModel.block() =
        (uiState.value.screen as ExperienceScreen.WinnerClaim).claim.verification

    // ── Backwards compatibility ──

    @Test
    fun `an absent block is today's flow - splash straight to the form, no code call`() = runTest(dispatcher) {
        val viewModel = openOnSplash(verification = null)
        viewModel.winnerClaimContinue()
        advanceUntilIdle()

        assertEquals(WinnerClaimStep.Form, viewModel.uiState.value.winnerClaimStep)
        coVerify(exactly = 0) { api.sendClaimVerificationCode(any(), any()) }
    }

    @Test
    fun `required false goes to the form directly`() = runTest(dispatcher) {
        val viewModel = openOnSplash(ClaimVerificationBlock(required = false))
        viewModel.winnerClaimContinue()
        advanceUntilIdle()

        assertEquals(WinnerClaimStep.Form, viewModel.uiState.value.winnerClaimStep)
        coVerify(exactly = 0) { api.sendClaimVerificationCode(any(), any()) }
    }

    // ── Opening the code screen ──

    @Test
    fun `required true paints the code screen at once with the masked email`() = runTest(dispatcher) {
        stubSend(sent = true)
        val viewModel = openOnSplash(ClaimVerificationBlock(required = true))

        viewModel.winnerClaimContinue()

        // First frame: no coroutine has run yet — the screen is already the
        // code screen, naming the masked address, with nothing blocking it.
        val first = viewModel.uiState.value
        assertEquals(WinnerClaimStep.Code, first.winnerClaimStep)
        val shown = (first.screen as ExperienceScreen.WinnerClaim).claim
        assertEquals(
            "Enter the 6-digit code we sent to $masked",
            AvafliV2Strings.claimCodeSubtitle(shown.maskedEmail),
        )
        assertEquals(ClaimCodeSend.Sending, first.claimCodeSend)
        assertFalse(first.isVerifyingCode)

        advanceUntilIdle()
        coVerify(exactly = 1) { api.sendClaimVerificationCode("gw-123", false) }
        coVerify(exactly = 0) { api.sendClaimVerificationCode(any(), true) }
        assertEquals(ClaimCodeSend.Sent, viewModel.uiState.value.claimCodeSend)
    }

    @Test
    fun `a live code in the block still makes one idempotent call and shows no banner`() = runTest(dispatcher) {
        val live = liveCode(sentAgoMs = 20_000L)
        stubSend(sent = false, block = live)
        val viewModel = openOnSplash(live)

        viewModel.winnerClaimContinue()
        // The countdown is already running off the block, before any answer.
        assertEquals(now + 40_000L, viewModel.uiState.value.claimCodeResendAtMs)
        advanceUntilIdle()

        coVerify(exactly = 1) { api.sendClaimVerificationCode("gw-123", false) }
        assertEquals(ClaimCodeSend.Idle, viewModel.uiState.value.claimCodeSend)
        assertNull(viewModel.uiState.value.claimCodeInfo)
        assertEquals(now + 40_000L, viewModel.uiState.value.claimCodeResendAtMs)
    }

    @Test
    fun `an inbox proven in the meantime skips the code`() = runTest(dispatcher) {
        stubSend(sent = false, block = ClaimVerificationBlock(required = false))
        val viewModel = openOnSplash(ClaimVerificationBlock(required = true))

        viewModel.winnerClaimContinue()
        advanceUntilIdle()

        assertEquals(WinnerClaimStep.Form, viewModel.uiState.value.winnerClaimStep)
        assertEquals(false, viewModel.block()?.required)
    }

    // ── Checking the code ──

    @Test
    fun `the right code confirms briefly, opens the form and stops re-asking`() = runTest(dispatcher) {
        val viewModel = openOnCode()
        coEvery { api.confirmClaimVerificationCode("gw-123", "123456") } returns
            AvafliApi.ConfirmClaimCodeResponse(verified = true, verification = ClaimVerificationBlock(required = false))

        viewModel.submitClaimCode("123456")
        runCurrent()

        // The brief confirmation, still on the code screen.
        assertTrue(viewModel.uiState.value.claimCodeVerified)
        assertEquals(WinnerClaimStep.Code, viewModel.uiState.value.winnerClaimStep)

        advanceUntilIdle()
        val ui = viewModel.uiState.value
        assertEquals(WinnerClaimStep.Form, ui.winnerClaimStep)
        assertFalse(ui.claimCodeVerified)
        assertFalse(ui.isVerifyingCode)
        // In-memory block updated: the rest of the session does not re-ask.
        assertEquals(false, viewModel.block()?.required)

        viewModel.winnerClaimContinue()
        advanceUntilIdle()
        assertEquals(WinnerClaimStep.Form, viewModel.uiState.value.winnerClaimStep)
        coVerify(exactly = 1) { api.sendClaimVerificationCode(any(), any()) }
    }

    @Test
    fun `a wrong code says how many tries are left and clears the field`() = runTest(dispatcher) {
        val viewModel = openOnCode()
        val resetBefore = viewModel.uiState.value.claimCodeFieldReset
        coEvery { api.confirmClaimVerificationCode(any(), any()) } throws serverError(
            403, "PERMISSION_DENIED", "That code didn't match. Check the email and try again.",
            """{"reason":"code_mismatch","attemptsRemaining":3}""",
        )

        viewModel.submitClaimCode("000000")
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        assertEquals("That code didn't match. 3 tries left.", ui.codeError)
        assertEquals(resetBefore + 1, ui.claimCodeFieldReset)
        assertEquals(WinnerClaimStep.Code, ui.winnerClaimStep)
        assertFalse(ui.isVerifyingCode)
    }

    @Test
    fun `the last try reads in the singular`() {
        assertEquals("That code didn't match. 1 try left.", AvafliV2Strings.claimCodeMismatch(1))
        assertEquals("That code didn't match. 4 tries left.", AvafliV2Strings.claimCodeMismatch(4))
        assertEquals(AvafliV2Strings.CODE_MISMATCH, AvafliV2Strings.claimCodeMismatch(null))
    }

    @Test
    fun `fresh_code_sent is a notice, clears the field and restarts the countdown`() = runTest(dispatcher) {
        val viewModel = openOnCode()
        // Eleven minutes on: the code the screen opened with has expired.
        now += 11 * 60 * 1000L
        val resetBefore = viewModel.uiState.value.claimCodeFieldReset
        val fresh = liveCode()
        coEvery { api.confirmClaimVerificationCode(any(), any()) } throws serverError(
            400, "FAILED_PRECONDITION", "That code expired, so we sent you a new one. Check your email.",
            """{"reason":"fresh_code_sent","verification":{"required":true,"codeSentAt":"${fresh.codeSentAt}","codeExpiresAt":"${fresh.codeExpiresAt}","resendAvailableAt":"${fresh.resendAvailableAt}"}}""",
        )

        viewModel.submitClaimCode("123456")
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        assertEquals("That code expired, so we sent you a new one. Check your email.", ui.claimCodeInfo)
        assertNull(ui.codeError)
        assertEquals(ClaimCodeSend.Idle, ui.claimCodeSend)
        assertEquals(resetBefore + 1, ui.claimCodeFieldReset)
        assertEquals(now + 60_000L, ui.claimCodeResendAtMs)
        // The server's new block is the one in memory.
        assertEquals(fresh, viewModel.block())
        assertEquals(WinnerClaimStep.Code, ui.winnerClaimStep)
    }

    @Test
    fun `a network failure on the check keeps what they typed`() = runTest(dispatcher) {
        val viewModel = openOnCode()
        val resetBefore = viewModel.uiState.value.claimCodeFieldReset
        coEvery { api.confirmClaimVerificationCode(any(), any()) } throws AvafliError.NetworkError("offline")

        viewModel.submitClaimCode("123456")
        advanceUntilIdle()

        val ui = viewModel.uiState.value
        assertEquals(
            "We couldn't reach the server. Check your connection and try again.",
            ui.codeError,
        )
        assertEquals(resetBefore, ui.claimCodeFieldReset)
        assertFalse(ui.isVerifyingCode)

        // The same code goes through once the connection is back.
        coEvery { api.confirmClaimVerificationCode("gw-123", "123456") } returns
            AvafliApi.ConfirmClaimCodeResponse(verified = true, verification = ClaimVerificationBlock(required = false))
        viewModel.submitClaimCode("123456")
        advanceUntilIdle()
        assertEquals(WinnerClaimStep.Form, viewModel.uiState.value.winnerClaimStep)
    }

    @Test
    fun `an expired claim leaves the code screen for the dashboard and says why`() = runTest(dispatcher) {
        val viewModel = openOnCode()
        coEvery { api.confirmClaimVerificationCode(any(), any()) } throws serverError(
            400, "FAILED_PRECONDITION", "The claim window for this prize has expired",
        )

        viewModel.submitClaimCode("123456")
        runCurrent()

        val ui = viewModel.uiState.value
        assertTrue(ui.screen is ExperienceScreen.Streak)
        assertEquals("The claim window for this prize has expired", ui.dashboardNotice?.message)
        assertEquals(WinnerClaimStep.Splash, ui.winnerClaimStep)
    }

    // ── Send a new code ──

    @Test
    fun `send a new code waits for resendAvailableAt, then sends with resend true`() = runTest(dispatcher) {
        val viewModel = openOnCode(sent = true)
        assertEquals(now + 60_000L, viewModel.uiState.value.claimCodeResendAtMs)

        // Inside the cooldown: nothing goes out.
        now += 59_000L
        viewModel.resendClaimCode()
        advanceUntilIdle()
        coVerify(exactly = 0) { api.sendClaimVerificationCode(any(), true) }

        // The moment it lifts: one call, with resend.
        now += 1_000L
        stubSend(sent = true, block = liveCode())
        viewModel.resendClaimCode()
        advanceUntilIdle()
        coVerify(exactly = 1) { api.sendClaimVerificationCode("gw-123", true) }
        assertEquals(ClaimCodeSend.Sent, viewModel.uiState.value.claimCodeSend)
        assertEquals(now + 60_000L, viewModel.uiState.value.claimCodeResendAtMs)
    }

    @Test
    fun `a server clock ahead of the device never stretches the wait past a minute`() = runTest(dispatcher) {
        // The server's resendAvailableAt is 9 minutes ahead of this device.
        val skewed = liveCode(sentAgoMs = -8 * 60 * 1000L)
        stubSend(sent = false, block = skewed)
        val viewModel = openOnSplash(skewed)

        viewModel.winnerClaimContinue()
        advanceUntilIdle()

        assertEquals(now + 60_000L, viewModel.uiState.value.claimCodeResendAtMs)
    }

    @Test
    fun `resend_cooldown and send_limit drive the countdown from retryAfterSeconds`() = runTest(dispatcher) {
        val viewModel = openOnCode(sent = false)
        now += 61_000L

        coEvery { api.sendClaimVerificationCode(any(), true) } throws serverError(
            429, "RESOURCE_EXHAUSTED", "Please wait 42 seconds before requesting another code.",
            """{"reason":"resend_cooldown","retryAfterSeconds":42}""",
        )
        viewModel.resendClaimCode()
        advanceUntilIdle()
        var ui = viewModel.uiState.value
        assertEquals(AvafliV2Strings.CLAIM_CODE_COOLDOWN, ui.claimCodeInfo)
        assertEquals(now + 42_000L, ui.claimCodeResendAtMs)
        assertEquals(ClaimCodeSend.Idle, ui.claimCodeSend)

        now += 42_000L
        coEvery { api.sendClaimVerificationCode(any(), true) } throws serverError(
            429, "RESOURCE_EXHAUSTED",
            "You've requested several codes. Please try again in a little while, or contact info@avafli.com.",
            """{"reason":"send_limit","retryAfterSeconds":1800}""",
        )
        viewModel.resendClaimCode()
        advanceUntilIdle()
        ui = viewModel.uiState.value
        assertEquals(
            ClaimCodeSend.Failed(
                "You've requested several codes. Please try again in a little while, or contact info@avafli.com.",
                retryable = false,
            ),
            ui.claimCodeSend,
        )
        assertEquals(now + 1_800_000L, ui.claimCodeResendAtMs)
        // Still a way on: the code already in their inbox can be entered.
        assertEquals(WinnerClaimStep.Code, ui.winnerClaimStep)
    }

    // ── Send failure ──

    @Test
    fun `a failed send is inline and retryable, and the field stays usable`() = runTest(dispatcher) {
        coEvery { api.sendClaimVerificationCode("gw-123", false) } throws serverError(
            503, "UNAVAILABLE", "We couldn't send your code just now. Please try again in a minute.",
            """{"reason":"send_failed"}""",
        )
        val viewModel = openOnSplash(ClaimVerificationBlock(required = true))
        viewModel.winnerClaimContinue()
        advanceUntilIdle()

        var ui = viewModel.uiState.value
        assertEquals(WinnerClaimStep.Code, ui.winnerClaimStep)
        assertEquals(
            ClaimCodeSend.Failed(
                "We couldn't send your code just now. Please try again in a minute.",
                retryable = true,
                resend = false,
            ),
            ui.claimCodeSend,
        )

        // A code from an earlier send may still be in their inbox — it works.
        coEvery { api.confirmClaimVerificationCode(any(), any()) } throws serverError(
            403, "PERMISSION_DENIED", "That code didn't match. Check the email and try again.",
            """{"reason":"code_mismatch","attemptsRemaining":4}""",
        )
        viewModel.submitClaimCode("111111")
        advanceUntilIdle()
        coVerify(exactly = 1) { api.confirmClaimVerificationCode("gw-123", "111111") }
        assertEquals("That code didn't match. 4 tries left.", viewModel.uiState.value.codeError)

        // Retry repeats the send that failed (still without resend).
        stubSend(sent = true)
        viewModel.retryClaimCodeSend()
        advanceUntilIdle()
        ui = viewModel.uiState.value
        coVerify(exactly = 2) { api.sendClaimVerificationCode("gw-123", false) }
        coVerify(exactly = 0) { api.sendClaimVerificationCode(any(), true) }
        assertEquals(ClaimCodeSend.Sent, ui.claimCodeSend)
    }

    @Test
    fun `an offline send reads as a connection problem`() = runTest(dispatcher) {
        coEvery { api.sendClaimVerificationCode(any(), any()) } throws AvafliError.NetworkError("offline")
        val viewModel = openOnSplash(ClaimVerificationBlock(required = true))
        viewModel.winnerClaimContinue()
        advanceUntilIdle()

        assertEquals(
            ClaimCodeSend.Failed(AvafliV2Strings.CLAIM_CODE_NETWORK, retryable = true, resend = false),
            viewModel.uiState.value.claimCodeSend,
        )
    }

    @Test
    fun `no email on file points at the contact address and offers no pointless retry`() = runTest(dispatcher) {
        coEvery { api.sendClaimVerificationCode(any(), any()) } throws serverError(
            400, "FAILED_PRECONDITION",
            "We don't have an email on file for this account. Contact info@avafli.com to claim your prize.",
            """{"reason":"no_email_on_file"}""",
        )
        val viewModel = openOnSplash(ClaimVerificationBlock(required = true))
        viewModel.winnerClaimContinue()
        advanceUntilIdle()

        val failed = viewModel.uiState.value.claimCodeSend as ClaimCodeSend.Failed
        assertTrue(failed.message.contains("info@avafli.com"))
        assertFalse(failed.retryable)
        assertEquals(WinnerClaimStep.Code, viewModel.uiState.value.winnerClaimStep)
    }

    // ── Back ──

    @Test
    fun `back returns to the splash without sending or invalidating anything`() = runTest(dispatcher) {
        val viewModel = openOnCode()
        val blockBefore = viewModel.block()

        viewModel.claimCodeBack()
        advanceUntilIdle()

        assertEquals(WinnerClaimStep.Splash, viewModel.uiState.value.winnerClaimStep)
        assertEquals(blockBefore, viewModel.block())
        coVerify(exactly = 1) { api.sendClaimVerificationCode(any(), any()) }
        coVerify(exactly = 0) { api.sendClaimVerificationCode(any(), true) }
        coVerify(exactly = 0) { api.confirmClaimVerificationCode(any(), any()) }
    }

    // ── Claim form ──

    @Test
    fun `claim_verification_required routes to the code screen and keeps the form`() = runTest(dispatcher) {
        // The block said the inbox was proven; the state changed underneath us.
        val viewModel = openOnSplash(ClaimVerificationBlock(required = false))
        viewModel.winnerClaimContinue()
        assertEquals(WinnerClaimStep.Form, viewModel.uiState.value.winnerClaimStep)

        coEvery {
            api.submitPrizeClaim(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )
        } throws serverError(
            400, "FAILED_PRECONDITION", "Verify your email to claim your prize.",
            """{"reason":"claim_verification_required"}""",
        )
        stubSend(sent = true)
        viewModel.submitPrizeClaim(validForm)
        advanceUntilIdle()

        var ui = viewModel.uiState.value
        assertEquals(WinnerClaimStep.Code, ui.winnerClaimStep)
        assertEquals(validForm, ui.claimFormDraft)
        assertFalse(ui.isSubmittingClaim)
        assertNull(ui.claimSubmitError)
        assertEquals(true, viewModel.block()?.required)
        coVerify(exactly = 1) { api.sendClaimVerificationCode("gw-123", false) }

        // After the code: back on the form, every field as they left it.
        coEvery { api.confirmClaimVerificationCode("gw-123", "123456") } returns
            AvafliApi.ConfirmClaimCodeResponse(verified = true, verification = ClaimVerificationBlock(required = false))
        viewModel.submitClaimCode("123456")
        advanceUntilIdle()
        ui = viewModel.uiState.value
        assertEquals(WinnerClaimStep.Form, ui.winnerClaimStep)
        assertEquals(validForm, ui.claimFormDraft)

        // The re-submit goes through and the draft is dropped.
        coEvery {
            api.submitPrizeClaim(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )
        } returns AvafliApi.SubmitPrizeClaimResponse("9876543210", "2026-09-29T18:05:00.000Z")
        viewModel.submitPrizeClaim(ui.claimFormDraft!!)
        advanceUntilIdle()
        ui = viewModel.uiState.value
        assertTrue(ui.winnerClaimStep is WinnerClaimStep.Share)
        assertEquals(validForm, ui.submittedClaimForm)
        assertNull(ui.claimFormDraft)
    }

    // ── Closing and reopening: the server is the only memory ──

    @Test
    fun `a fresh view-model resumes on the live code the block carries`() = runTest(dispatcher) {
        // First open: the code goes out, then the drawer is closed (or the
        // app killed) with the code screen up.
        openOnCode(sent = true)

        // 30 seconds later, a brand-new view-model: only the block knows.
        now += 30_000L
        val live = liveCode(sentAgoMs = 30_000L)
        stubSend(sent = false, block = live)
        val reopened = openOnSplash(live)
        reopened.winnerClaimContinue()
        advanceUntilIdle()

        val ui = reopened.uiState.value
        assertEquals(WinnerClaimStep.Code, ui.winnerClaimStep)
        // The live code is re-used — no second email, no "Code sent".
        assertEquals(ClaimCodeSend.Idle, ui.claimCodeSend)
        assertEquals(now + 30_000L, ui.claimCodeResendAtMs)
        coVerify(exactly = 0) { api.sendClaimVerificationCode(any(), true) }
    }

    @Test
    fun `a fresh view-model after verifying goes straight to the form`() = runTest(dispatcher) {
        val reopened = openOnSplash(ClaimVerificationBlock(required = false))
        reopened.winnerClaimContinue()
        advanceUntilIdle()

        assertEquals(WinnerClaimStep.Form, reopened.uiState.value.winnerClaimStep)
        coVerify(exactly = 0) { api.sendClaimVerificationCode(any(), any()) }
    }

    @Test
    fun `nothing about the step is written to local storage`() = runTest(dispatcher) {
        val viewModel = openOnCode()
        coEvery { api.confirmClaimVerificationCode(any(), any()) } throws serverError(
            403, "PERMISSION_DENIED", "That code didn't match. Check the email and try again.",
            """{"reason":"code_mismatch","attemptsRemaining":4}""",
        )
        viewModel.submitClaimCode("000000")
        advanceUntilIdle()
        coEvery { api.confirmClaimVerificationCode(any(), any()) } returns
            AvafliApi.ConfirmClaimCodeResponse(verified = true, verification = ClaimVerificationBlock(required = false))
        viewModel.submitClaimCode("123456")
        advanceUntilIdle()
        assertEquals(WinnerClaimStep.Form, viewModel.uiState.value.winnerClaimStep)

        verify(exactly = 0) { storage.putString(any(), any()) }
        verify(exactly = 0) { storage.remove(any()) }
        verify(exactly = 0) { storage.saveEmailSubmitted(any()) }
        verify(exactly = 0) { storage.saveStreakDay(any()) }
        verify(exactly = 0) { storage.saveOptedOutUntil(any()) }
    }

    // ── Copy ──

    @Test
    fun `the countdown reads as minutes and seconds`() {
        assertEquals("Send a new code in 0:42", AvafliV2Strings.claimCodeResendCountdown(42))
        assertEquals("Send a new code in 1:00", AvafliV2Strings.claimCodeResendCountdown(60))
        assertEquals("Send a new code in 29:05", AvafliV2Strings.claimCodeResendCountdown(1745))
        assertEquals("Send a new code in 0:00", AvafliV2Strings.claimCodeResendCountdown(-3))
    }

    @Test
    fun `the code screen says code, never OTP, 2FA or token`() {
        val copy = listOf(
            AvafliV2Strings.claimCodeSubtitle(masked),
            AvafliV2Strings.claimCodeSubtitle(null),
            AvafliV2Strings.CLAIM_CODE_SENDING,
            AvafliV2Strings.CLAIM_CODE_SENT,
            AvafliV2Strings.claimCodeResendCountdown(30),
            AvafliV2Strings.CLAIM_CODE_HELP_PREFIX + AvafliV2Strings.SUPPORT_EMAIL,
            AvafliV2Strings.claimCodeMismatch(2),
            AvafliV2Strings.CLAIM_CODE_NETWORK,
            AvafliV2Strings.CLAIM_CODE_SEND_FAILED,
            AvafliV2Strings.CLAIM_CODE_COOLDOWN,
            AvafliV2Strings.CLAIM_CODE_SEND_LIMIT,
            AvafliV2Strings.CLAIM_CODE_FRESH_SENT,
            AvafliV2Strings.CLAIM_NO_EMAIL_ON_FILE,
            AvafliV2Strings.CLAIM_CODE_INVALID,
            AvafliV2Strings.CLAIM_CODE_FAILED,
        )
        copy.forEach { line ->
            listOf("OTP", "2FA", "token").forEach { banned ->
                assertFalse("'$banned' in: $line", line.contains(banned, ignoreCase = true))
            }
        }
        assertEquals(
            "Can't get to this email? Contact info@avafli.com",
            AvafliV2Strings.CLAIM_CODE_HELP_PREFIX + AvafliV2Strings.SUPPORT_EMAIL,
        )
    }
}
