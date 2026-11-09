package com.avafli.avaflisdk

import com.avafli.avaflisdk.domain.Giveaway
import com.avafli.avaflisdk.network.AvafliApi
import com.avafli.avaflisdk.network.NetworkClient
import com.avafli.avaflisdk.services.Logger
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 3.3.0 — contract tests for the network-giveaway fields on the giveaway
 * payload: `scope` ("network" | "publisher" | absent) and `sponsorDisclosure`
 * (the Sponsor-of-Record line the SDK must show under the prize). Both are
 * OPTIONAL; an exclusive giveaway omits them and parses exactly as before.
 */
class NetworkGiveawayDisclosureTest {

    private lateinit var networkClient: NetworkClient
    private lateinit var api: AvafliApi

    @Before
    fun setup() {
        networkClient = mockk()
        api = AvafliApi(networkClient, Logger(isDebug = false))
    }

    private fun jsonObj(raw: String) = Json.parseToJsonElement(raw).jsonObject

    private fun giveawayJson(extraFields: String = ""): String = """
        {
          "id": "gw_1",
          "title": "Weekly Cash",
          "prizeDescription": "Cash",
          "prizeValue": "1000"
          $extraFields
        }
    """

    private fun enqueueActiveGiveaway(giveawayJson: String) {
        coEvery { networkClient.authenticatedPost("getActiveGiveaway", any()) } returns jsonObj(
            """{ "giveaway": $giveawayJson, "claimedToday": false }"""
        )
    }

    private fun enqueueRegisterDevice(giveawayJson: String) {
        coEvery { networkClient.post("registerDevice", any()) } returns jsonObj(
            """
            {
              "token": "t",
              "refreshToken": "r",
              "uuid": "u",
              "giveaway": $giveawayJson
            }
            """
        )
    }

    // ── getActiveGiveaway ──

    @Test
    fun `network giveaway parses scope and sponsorDisclosure`() = runTest {
        enqueueActiveGiveaway(
            giveawayJson(""", "scope": "network", "sponsorDisclosure": "Sponsored by Avafli LLC" """)
        )
        val giveaway = api.getActiveGiveaway().giveaway
        assertNotNull(giveaway)
        assertEquals("network", giveaway!!.scope)
        assertEquals("Sponsored by Avafli LLC", giveaway.sponsorDisclosure)
        assertTrue(giveaway.isNetwork)
        // Siblings untouched.
        assertEquals("Cash", giveaway.prizeDescription)
        assertEquals("1000", giveaway.prizeValue)
    }

    @Test
    fun `exclusive giveaway without the fields parses them as null`() = runTest {
        enqueueActiveGiveaway(giveawayJson())
        val giveaway = api.getActiveGiveaway().giveaway
        assertNotNull(giveaway)
        assertNull(giveaway!!.scope)
        assertNull(giveaway.sponsorDisclosure)
        assertFalse(giveaway.isNetwork)
    }

    @Test
    fun `explicit publisher scope is not a network giveaway`() = runTest {
        enqueueActiveGiveaway(giveawayJson(""", "scope": "publisher" """))
        val giveaway = api.getActiveGiveaway().giveaway!!
        assertEquals("publisher", giveaway.scope)
        assertNull(giveaway.sponsorDisclosure)
        assertFalse(giveaway.isNetwork)
    }

    @Test
    fun `json null values parse as null`() = runTest {
        enqueueActiveGiveaway(giveawayJson(""", "scope": null, "sponsorDisclosure": null """))
        val giveaway = api.getActiveGiveaway().giveaway!!
        assertNull(giveaway.scope)
        assertNull(giveaway.sponsorDisclosure)
        assertFalse(giveaway.isNetwork)
    }

    // ── registerDevice carries the same giveaway object ──

    @Test
    fun `registerDevice giveaway parses the network fields`() = runTest {
        enqueueRegisterDevice(
            giveawayJson(""", "scope": "network", "sponsorDisclosure": "Sponsored by Avafli LLC" """)
        )
        val giveaway = api.registerDevice("key", "fp", "com.example", "UTC").giveaway
        assertNotNull(giveaway)
        assertEquals("network", giveaway!!.scope)
        assertEquals("Sponsored by Avafli LLC", giveaway.sponsorDisclosure)
    }

    @Test
    fun `registerDevice exclusive giveaway leaves the fields null`() = runTest {
        enqueueRegisterDevice(giveawayJson())
        val giveaway = api.registerDevice("key", "fp", "com.example", "UTC").giveaway!!
        assertNull(giveaway.scope)
        assertNull(giveaway.sponsorDisclosure)
    }

    // ── Model defaults + cache round-trip ──

    @Test
    fun `model defaults are null and not network`() {
        val giveaway = Giveaway(id = "", title = "", prizeDescription = "")
        assertNull(giveaway.scope)
        assertNull(giveaway.sponsorDisclosure)
        assertFalse(giveaway.isNetwork)
    }

    @Test
    fun `fields survive the cache serialization round-trip`() {
        val json = Json { ignoreUnknownKeys = true }
        val original = Giveaway(
            id = "gw_1", title = "Weekly Cash", prizeDescription = "Cash",
            scope = "network", sponsorDisclosure = "Sponsored by Avafli LLC",
        )
        val restored = json.decodeFromString(Giveaway.serializer(), json.encodeToString(Giveaway.serializer(), original))
        assertEquals("network", restored.scope)
        assertEquals("Sponsored by Avafli LLC", restored.sponsorDisclosure)
        assertTrue(restored.isNetwork)
    }
}
