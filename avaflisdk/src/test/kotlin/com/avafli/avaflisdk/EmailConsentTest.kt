package com.avafli.avaflisdk

import com.avafli.avaflisdk.domain.Giveaway
import com.avafli.avaflisdk.network.NetworkClient
import com.avafli.avaflisdk.network.AvafliApi
import com.avafli.avaflisdk.services.Logger
import com.avafli.avaflisdk.storage.PreferencesStorage
import com.avafli.avaflisdk.ui.AvafliExperienceViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins the 2.4.0 email-capture consent contract: the capture screen's TWO
 * checkboxes — the 18+ age gate (must be ticked to submit) and the pre-checked
 * MARKETING consent (optional, never gates entry or winner contact) — are
 * transmitted verbatim as `ageConfirmed` / `marketingConsent` on submitEmail.
 * `ageConfirmed` is always sent: the backend reads it to detect a 2.4.0+
 * client.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmailConsentTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var networkClient: NetworkClient
    private lateinit var api: AvafliApi

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        networkClient = mockk()
        api = AvafliApi(networkClient, Logger(isDebug = false))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun jsonObj(raw: String) = Json.parseToJsonElement(raw).jsonObject

    @Test
    fun `submitEmail sends both consent flags`() = runTest {
        val bodySlot = slot<Map<String, JsonElement>>()
        coEvery {
            networkClient.authenticatedPost("submitEmail", capture(bodySlot))
        } returns jsonObj("""{"success": true}""")

        api.submitEmail(
            email = "ada@example.com",
            marketingConsent = true,
            publisherUserId = null,
            ageConfirmed = true,
        )

        val body = bodySlot.captured
        assertEquals("ada@example.com", body["email"]?.jsonPrimitive?.content)
        assertEquals(true, body["ageConfirmed"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(true, body["marketingConsent"]?.jsonPrimitive?.booleanOrNull)
        // `marketingConsent` is the only consent key on the wire — the
        // short-lived `emailConsent` name was renamed before release.
        assertNull(body["emailConsent"])
    }

    @Test
    fun `submitEmail carries a declined marketing consent with a confirmed age`() = runTest {
        val bodySlot = slot<Map<String, JsonElement>>()
        coEvery {
            networkClient.authenticatedPost("submitEmail", capture(bodySlot))
        } returns jsonObj("""{"success": true}""")

        // The user unticked the pre-checked marketing box but still entered —
        // and is still contactable as a winner, which no checkbox gates.
        api.submitEmail(
            email = "ada@example.com",
            marketingConsent = false,
            publisherUserId = null,
            ageConfirmed = true,
        )

        val body = bodySlot.captured
        assertEquals(true, body["ageConfirmed"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(false, body["marketingConsent"]?.jsonPrimitive?.booleanOrNull)
        assertNull(body["emailConsent"])
    }

    @Test
    fun `view model forwards the checkbox states verbatim`() = runTest(dispatcher) {
        val mockApi = mockk<AvafliApi>()
        val storage = mockk<PreferencesStorage>(relaxed = true)
        every { storage.isEmailSubmitted() } returns true
        every { storage.getStreakDay() } returns 0
        every { storage.getTotalEntries() } returns 0
        every { storage.getLastClaimDate() } returns null

        val consentSlot = slot<Boolean>()
        val ageSlot = slot<Boolean>()
        coEvery {
            mockApi.submitEmail(any(), capture(consentSlot), any(), capture(ageSlot))
        } returns AvafliApi.SubmitEmailResult(success = true)
        coEvery { mockApi.getActiveGiveaway() } returns AvafliApi.GetActiveGiveawayResponse(
            giveaway = Giveaway(id = "g1", title = "T", prizeDescription = "P", streakLadder = listOf(1)),
            claimedToday = true,
            streakDay = 1,
            totalEntries = 1,
        )

        val viewModel = AvafliExperienceViewModel(
            api = mockApi,
            preferencesStorage = storage,
            logger = Logger(),
        )

        viewModel.submitEmail(
            email = "ada@example.com",
            marketingConsent = false,
            ageConfirmed = true,
        )
        advanceUntilIdle()

        assertTrue(ageSlot.captured)
        assertFalse(consentSlot.captured)
    }
}
