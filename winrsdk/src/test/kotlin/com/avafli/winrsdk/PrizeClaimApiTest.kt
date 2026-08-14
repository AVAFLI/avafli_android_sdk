package com.avafli.winrsdk

import com.avafli.winrsdk.network.NetworkClient
import com.avafli.winrsdk.network.WinrApi
import com.avafli.winrsdk.services.Logger
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract tests for the winner prize-claim wire format: the `prizeClaim`
 * response block on registerDevice/getActiveGiveaway and the submitPrizeClaim
 * request/response field names (FIXED contract with functions/src/prizeclaim.ts).
 */
class PrizeClaimApiTest {

    private lateinit var networkClient: NetworkClient
    private lateinit var api: WinrApi

    @Before
    fun setup() {
        networkClient = mockk()
        api = WinrApi(networkClient, Logger(isDebug = false))
    }

    private fun jsonObj(raw: String) = Json.parseToJsonElement(raw).jsonObject

    // ── prizeClaim block parsing ──

    @Test
    fun `getActiveGiveaway parses pending prizeClaim block`() = runTest {
        coEvery { networkClient.authenticatedPost("getActiveGiveaway", any()) } returns jsonObj(
            """
            {
              "giveaway": null,
              "claimedToday": false,
              "prizeClaim": {
                "status": "pending",
                "giveawayId": "gw-123",
                "prizeDescription": "Cash",
                "prizeValue": 1000
              }
            }
            """
        )

        val response = api.getActiveGiveaway()
        val claim = response.prizeClaim
        assertNotNull(claim)
        assertTrue(claim!!.isPending)
        assertEquals("pending", claim.status)
        assertEquals("gw-123", claim.giveawayId)
        assertEquals("Cash", claim.prizeDescription)
        assertEquals(1000.0, claim.prizeValue, 0.0)
        assertNull(claim.claimNumber)
        assertNull(claim.submittedAt)
        // maskedEmail is absent from older backends — decodes to null.
        assertNull(claim.maskedEmail)
        // Pending winner flow works even when the giveaway is null.
        assertNull(response.giveaway)
    }

    @Test
    fun `prizeClaim block decodes the masked winning email`() = runTest {
        coEvery { networkClient.authenticatedPost("getActiveGiveaway", any()) } returns jsonObj(
            """
            {
              "prizeClaim": {
                "status": "pending",
                "giveawayId": "gw-123",
                "prizeDescription": "Cash",
                "prizeValue": 1000,
                "maskedEmail": "d********r@winr.example.com"
              }
            }
            """
        )

        val claim = api.getActiveGiveaway().prizeClaim
        assertNotNull(claim)
        assertEquals("d********r@winr.example.com", claim!!.maskedEmail)
    }

    @Test
    fun `getActiveGiveaway parses submitted prizeClaim block`() = runTest {
        coEvery { networkClient.authenticatedPost("getActiveGiveaway", any()) } returns jsonObj(
            """
            {
              "prizeClaim": {
                "status": "submitted",
                "giveawayId": "gw-123",
                "prizeDescription": "PS5 Bundle",
                "prizeValue": 499.99,
                "claimNumber": "0123456789",
                "submittedAt": "2026-08-04T18:30:00.000Z"
              }
            }
            """
        )

        val claim = api.getActiveGiveaway().prizeClaim
        assertNotNull(claim)
        assertFalse(claim!!.isPending)
        assertEquals("0123456789", claim.claimNumber)
        assertEquals("2026-08-04T18:30:00.000Z", claim.submittedAt)
        assertEquals(499.99, claim.prizeValue, 0.0)
    }

    @Test
    fun `getActiveGiveaway without prizeClaim yields null block`() = runTest {
        coEvery { networkClient.authenticatedPost("getActiveGiveaway", any()) } returns
            jsonObj("""{"claimedToday": true}""")
        assertNull(api.getActiveGiveaway().prizeClaim)
    }

    @Test
    fun `registerDevice parses prizeClaim block`() = runTest {
        coEvery { networkClient.post("registerDevice", any()) } returns jsonObj(
            """
            {
              "token": "t", "refreshToken": "r", "uuid": "u",
              "prizeClaim": {
                "status": "pending",
                "giveawayId": "gw-9",
                "prizeDescription": "Cash",
                "prizeValue": 500
              }
            }
            """
        )

        val response = api.registerDevice("key", "fp", "bundle", "America/New_York")
        assertNotNull(response.prizeClaim)
        assertTrue(response.prizeClaim!!.isPending)
        assertEquals("gw-9", response.prizeClaim!!.giveawayId)
    }

    // ── submitPrizeClaim request/response contract ──

    @Test
    fun `submitPrizeClaim sends the exact backend field names`() = runTest {
        val bodySlot = slot<Map<String, JsonElement>>()
        coEvery {
            networkClient.authenticatedPost("submitPrizeClaim", capture(bodySlot))
        } returns jsonObj("""{"claimNumber": "9876543210", "submittedAt": "2026-08-04T19:00:00.000Z"}""")

        val response = api.submitPrizeClaim(
            giveawayId = "gw-123",
            firstName = "Ada",
            lastName = "Lovelace",
            phone = "5551234567",
            street = "1 Analytical Way",
            apt = "4B",
            city = "Brooklyn",
            state = "New York",
            zip = "11201",
            country = "United States",
            photoBase64 = "aGVsbG8=",
            story = "Buying my mom dinner.",
            promoConsentGranted = true,
        )

        val body = bodySlot.captured
        // Field names are a FIXED contract with functions/src/prizeclaim.ts.
        assertEquals("gw-123", body["giveawayId"]?.jsonPrimitive?.content)
        assertEquals("Ada", body["firstName"]?.jsonPrimitive?.content)
        assertEquals("Lovelace", body["lastName"]?.jsonPrimitive?.content)
        assertEquals("5551234567", body["phone"]?.jsonPrimitive?.content)
        assertEquals("1 Analytical Way", body["street"]?.jsonPrimitive?.content)
        assertEquals("4B", body["apt"]?.jsonPrimitive?.content)
        assertEquals("Brooklyn", body["city"]?.jsonPrimitive?.content)
        assertEquals("New York", body["state"]?.jsonPrimitive?.content)
        assertEquals("11201", body["zip"]?.jsonPrimitive?.content)
        assertEquals("United States", body["country"]?.jsonPrimitive?.content)
        assertEquals("aGVsbG8=", body["photoBase64"]?.jsonPrimitive?.content)
        assertEquals("Buying my mom dinner.", body["story"]?.jsonPrimitive?.content)
        // 2.9: the OPTIONAL likeness/promo checkbox state, transmitted verbatim.
        assertEquals("true", body["promoConsentGranted"]?.jsonPrimitive?.content)

        assertEquals("9876543210", response.claimNumber)
        assertEquals("2026-08-04T19:00:00.000Z", response.submittedAt)
    }

    @Test
    fun `submitPrizeClaim omits empty optional fields`() = runTest {
        val bodySlot = slot<Map<String, JsonElement>>()
        coEvery {
            networkClient.authenticatedPost("submitPrizeClaim", capture(bodySlot))
        } returns jsonObj("""{"claimNumber": "1", "submittedAt": "2026-08-04"}""")

        api.submitPrizeClaim(
            giveawayId = "gw-123",
            firstName = "Ada",
            lastName = "Lovelace",
            phone = null,
            street = "1 Analytical Way",
            apt = null,
            city = "Brooklyn",
            state = "New York",
            zip = "11201",
            country = "United States",
            photoBase64 = null,
        )

        val body = bodySlot.captured
        assertNull(body["phone"])
        assertNull(body["apt"])
        assertNull(body["photoBase64"])
        assertNull(body["story"])
        // promoConsentGranted is ALWAYS sent (2.9) — declined consent must
        // reach the backend as an explicit false, never an absence.
        assertEquals("false", body["promoConsentGranted"]?.jsonPrimitive?.content)
    }
}
