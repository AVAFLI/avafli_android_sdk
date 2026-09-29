package com.avafli.avaflisdk

import com.avafli.avaflisdk.network.AvafliApi
import com.avafli.avaflisdk.network.NetworkClient
import com.avafli.avaflisdk.services.Logger
import com.avafli.avaflisdk.storage.SecureStorage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Wire contract for the claim email-ownership step (3.2.0): the
 * `prizeClaim.verification` block, the two code callables, the
 * `supportsClaimVerification` flag on submit, and the callable error
 * `details` the flow reads (FIXED contract with functions/src/claimverify.ts).
 */
class ClaimVerificationApiTest {

    private lateinit var networkClient: NetworkClient
    private lateinit var api: AvafliApi

    @Before
    fun setup() {
        networkClient = mockk()
        api = AvafliApi(networkClient, Logger(isDebug = false))
    }

    private fun jsonObj(raw: String) = Json.parseToJsonElement(raw).jsonObject

    private fun statusWithClaim(verification: String?) = jsonObj(
        """
        {
          "prizeClaim": {
            "status": "pending",
            "giveawayId": "gw-123",
            "prizeDescription": "Cash",
            "prizeValue": 1000,
            "maskedEmail": "d********r@avafli.example.com"
            ${verification?.let { ""","verification": $it""" } ?: ""}
          }
        }
        """
    )

    // ── prizeClaim.verification block ──

    @Test
    fun `an absent verification block decodes to null`() = runTest {
        coEvery { networkClient.authenticatedPost("getActiveGiveaway", any()) } returns statusWithClaim(null)
        val claim = api.getActiveGiveaway().prizeClaim
        assertNotNull(claim)
        assertNull(claim!!.verification)
    }

    @Test
    fun `required false decodes with no code times`() = runTest {
        coEvery { networkClient.authenticatedPost("getActiveGiveaway", any()) } returns
            statusWithClaim("""{"required": false}""")
        val verification = api.getActiveGiveaway().prizeClaim!!.verification
        assertNotNull(verification)
        assertFalse(verification!!.required)
        assertNull(verification.codeSentAt)
        assertNull(verification.resendAvailableAt)
    }

    @Test
    fun `a live code's times decode from the block`() = runTest {
        coEvery { networkClient.authenticatedPost("getActiveGiveaway", any()) } returns statusWithClaim(
            """
            {
              "required": true,
              "codeSentAt": "2026-09-29T18:00:00.000Z",
              "codeExpiresAt": "2026-09-29T18:10:00.000Z",
              "resendAvailableAt": "2026-09-29T18:01:00.000Z"
            }
            """
        )
        val verification = api.getActiveGiveaway().prizeClaim!!.verification!!
        assertTrue(verification.required)
        assertEquals("2026-09-29T18:00:00.000Z", verification.codeSentAt)
        assertEquals("2026-09-29T18:10:00.000Z", verification.codeExpiresAt)
        assertEquals("2026-09-29T18:01:00.000Z", verification.resendAvailableAt)
    }

    @Test
    fun `registerDevice carries the verification block too`() = runTest {
        coEvery { networkClient.post("registerDevice", any()) } returns jsonObj(
            """
            {
              "token": "t", "refreshToken": "r", "uuid": "u",
              "prizeClaim": {
                "status": "pending", "giveawayId": "gw-9",
                "verification": {"required": true}
              }
            }
            """
        )
        val claim = api.registerDevice("key", "fp", "bundle", "UTC").prizeClaim
        assertTrue(claim!!.verification!!.required)
    }

    @Test
    fun `a malformed verification block never costs the winner their claim block`() = runTest {
        coEvery { networkClient.authenticatedPost("getActiveGiveaway", any()) } returns
            statusWithClaim(""""soon"""")
        val claim = api.getActiveGiveaway().prizeClaim
        assertNotNull(claim)
        assertTrue(claim!!.isPending)
        assertEquals("d********r@avafli.example.com", claim.maskedEmail)
        assertNull(claim.verification)
    }

    // ── sendClaimVerificationCode ──

    @Test
    fun `opening the code screen sends the giveaway id and no resend flag`() = runTest {
        val body = slot<Map<String, JsonElement>>()
        coEvery { networkClient.authenticatedPost("sendClaimVerificationCode", capture(body)) } returns jsonObj(
            """
            {"sent": true, "verification": {
              "required": true,
              "codeSentAt": "2026-09-29T18:00:00.000Z",
              "codeExpiresAt": "2026-09-29T18:10:00.000Z",
              "resendAvailableAt": "2026-09-29T18:01:00.000Z"
            }}
            """
        )

        val response = api.sendClaimVerificationCode("gw-123")

        assertEquals("gw-123", body.captured["giveawayId"]?.jsonPrimitive?.content)
        assertNull(body.captured["resend"])
        assertEquals(1, body.captured.size)
        assertTrue(response.sent)
        assertEquals("2026-09-29T18:01:00.000Z", response.verification?.resendAvailableAt)
    }

    @Test
    fun `send a new code sends resend true`() = runTest {
        val body = slot<Map<String, JsonElement>>()
        coEvery { networkClient.authenticatedPost("sendClaimVerificationCode", capture(body)) } returns
            jsonObj("""{"sent": true, "verification": {"required": true}}""")

        api.sendClaimVerificationCode("gw-123", resend = true)

        assertEquals("true", body.captured["resend"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a re-used live code comes back as sent false`() = runTest {
        coEvery { networkClient.authenticatedPost("sendClaimVerificationCode", any()) } returns
            jsonObj("""{"sent": false, "verification": {"required": true, "codeSentAt": "2026-09-29T18:00:00.000Z"}}""")
        val response = api.sendClaimVerificationCode("gw-123")
        assertFalse(response.sent)
        assertTrue(response.verification!!.required)
    }

    // ── confirmClaimVerificationCode ──

    @Test
    fun `confirm sends the giveaway id and the code`() = runTest {
        val body = slot<Map<String, JsonElement>>()
        coEvery { networkClient.authenticatedPost("confirmClaimVerificationCode", capture(body)) } returns
            jsonObj("""{"verified": true, "verification": {"required": false}}""")

        val response = api.confirmClaimVerificationCode("gw-123", "123456")

        assertEquals("gw-123", body.captured["giveawayId"]?.jsonPrimitive?.content)
        assertEquals("123456", body.captured["code"]?.jsonPrimitive?.content)
        assertTrue(response.verified)
        assertFalse(response.verification!!.required)
    }

    // ── submitPrizeClaim ──

    @Test
    fun `submitPrizeClaim always declares supportsClaimVerification`() = runTest {
        val body = slot<Map<String, JsonElement>>()
        coEvery { networkClient.authenticatedPost("submitPrizeClaim", capture(body)) } returns
            jsonObj("""{"claimNumber": "1", "submittedAt": "2026-09-29"}""")

        api.submitPrizeClaim(
            giveawayId = "gw-123",
            firstName = "Ada",
            lastName = "Lovelace",
            street = "1 Analytical Way",
            city = "Brooklyn",
            state = "New York",
            zip = "11201",
            country = "United States",
        )

        assertEquals("true", body.captured["supportsClaimVerification"]?.jsonPrimitive?.content)
    }

    // ── 24-hour rejoin: optedOutUntil ──

    @Test
    fun `optedOutUntil is read from both status responses and absent on older backends`() = runTest {
        coEvery { networkClient.authenticatedPost("getActiveGiveaway", any()) } returns
            jsonObj("""{"optedOut": true, "optedOutUntil": "2026-09-30T18:00:00.000Z"}""")
        val status = api.getActiveGiveaway()
        assertEquals(true, status.optedOut)
        assertEquals("2026-09-30T18:00:00.000Z", status.optedOutUntil)

        coEvery { networkClient.post("registerDevice", any()) } returns jsonObj(
            """{"token":"t","refreshToken":"r","uuid":"u","optedOut":true,"optedOutUntil":"2026-09-30T18:00:00.000Z"}"""
        )
        assertEquals("2026-09-30T18:00:00.000Z", api.registerDevice("k", "fp", "b", "UTC").optedOutUntil)

        coEvery { networkClient.authenticatedPost("getActiveGiveaway", any()) } returns
            jsonObj("""{"optedOut": true}""")
        assertNull(api.getActiveGiveaway().optedOutUntil)
    }

    // ── Callable error details ──

    @Test
    fun `callable error details reach the caller with their reason and fields`() {
        val error = NetworkClient.callableError(
            AvafliError.ServerError(
                403,
                """{"error":{"status":"PERMISSION_DENIED","message":"That code didn't match. Check the email and try again.","details":{"reason":"code_mismatch","attemptsRemaining":3}}}""",
            )
        )
        assertNotNull(error)
        assertEquals(403, error!!.httpCode)
        assertEquals("PERMISSION_DENIED", error.status)
        assertEquals("That code didn't match. Check the email and try again.", error.message)
        assertEquals("code_mismatch", error.reason)
        assertEquals(3, error.detailInt("attemptsRemaining"))
        assertNull(error.detailInt("retryAfterSeconds"))
    }

    @Test
    fun `a nested verification block is readable from the details`() {
        val error = NetworkClient.callableError(
            AvafliError.ServerError(
                400,
                """{"error":{"status":"FAILED_PRECONDITION","message":"That code expired, so we sent you a new one. Check your email.","details":{"reason":"fresh_code_sent","verification":{"required":true,"resendAvailableAt":"2026-09-29T18:01:00.000Z"}}}}""",
            )
        )
        val block = AvafliApi.parseClaimVerification(error!!.detailObject("verification"))
        assertEquals("fresh_code_sent", error.reason)
        assertTrue(block!!.required)
        assertEquals("2026-09-29T18:01:00.000Z", block.resendAvailableAt)
    }

    @Test
    fun `an error without details has no reason`() {
        val error = NetworkClient.callableError(
            AvafliError.ServerError(
                400,
                """{"error":{"status":"FAILED_PRECONDITION","message":"The claim window for this prize has expired"}}""",
            )
        )
        assertEquals("FAILED_PRECONDITION", error!!.status)
        assertNull(error.reason)
        assertNull(error.details)
    }

    @Test
    fun `non-server errors and unparseable bodies yield no callable error`() {
        assertNull(NetworkClient.callableError(AvafliError.NetworkError("offline")))
        assertNull(NetworkClient.callableError(RuntimeException("boom")))
        assertNull(NetworkClient.callableError(AvafliError.ServerError(500, "Internal Server Error")))
        assertNull(NetworkClient.callableError(AvafliError.ServerError(500, "{}")))
    }

    @Test
    fun `details survive the real network layer`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val secureStorage = mockk<SecureStorage>(relaxed = true)
            every { secureStorage.getToken() } returns null
            val client = NetworkClient(
                AvafliConfiguration(
                    context = mockk(relaxed = true),
                    apiKey = "test-key",
                    user = AvafliUser(id = "test-user"),
                    options = AvafliOptions(enableCertificatePinning = false, networkTimeoutSeconds = 5),
                ),
                secureStorage,
                Logger(isDebug = false),
                baseUrlOverride = server.url("/").toString().trimEnd('/'),
            )
            server.enqueue(
                MockResponse().setResponseCode(429).setBody(
                    """{"error":{"status":"RESOURCE_EXHAUSTED","message":"Please wait 42 seconds before requesting another code.","details":{"reason":"resend_cooldown","retryAfterSeconds":42}}}"""
                )
            )

            try {
                client.post("sendClaimVerificationCode")
                fail("Expected a ServerError")
            } catch (e: AvafliError.ServerError) {
                // Unchanged for every other caller: same type, same code, raw body in the message.
                assertEquals(429, e.code)
                assertTrue(e.message!!.contains("resend_cooldown"))
                val error = NetworkClient.callableError(e)
                assertEquals("resend_cooldown", error!!.reason)
                assertEquals(42, error.detailInt("retryAfterSeconds"))
            }
        } finally {
            server.shutdown()
        }
    }
}
