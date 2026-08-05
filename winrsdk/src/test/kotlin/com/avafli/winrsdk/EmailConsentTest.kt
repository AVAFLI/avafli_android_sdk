package com.avafli.winrsdk

import com.avafli.winrsdk.domain.Giveaway
import com.avafli.winrsdk.network.NetworkClient
import com.avafli.winrsdk.network.WinrApi
import com.avafli.winrsdk.services.Logger
import com.avafli.winrsdk.storage.PreferencesStorage
import com.avafli.winrsdk.ui.WINRExperienceViewModel
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins the 2.4.0 email-capture consent contract: the capture screen's TWO
 * checkboxes — the 18+ age gate (must be ticked to submit) and the pre-checked
 * email/marketing consent (optional, never gates entry) — are transmitted
 * verbatim as `ageConfirmed` / `emailConsent` on submitEmail, alongside the
 * legacy `marketingConsent` field older backends still read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmailConsentTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var networkClient: NetworkClient
    private lateinit var api: WinrApi

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        networkClient = mockk()
        api = WinrApi(networkClient, Logger(isDebug = false))
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
            emailConsent = true,
        )

        val body = bodySlot.captured
        assertEquals("ada@example.com", body["email"]?.jsonPrimitive?.content)
        assertEquals(true, body["ageConfirmed"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(true, body["emailConsent"]?.jsonPrimitive?.booleanOrNull)
        // Legacy field kept for older backends.
        assertEquals(true, body["marketingConsent"]?.jsonPrimitive?.booleanOrNull)
    }

    @Test
    fun `submitEmail carries a declined email consent with a confirmed age`() = runTest {
        val bodySlot = slot<Map<String, JsonElement>>()
        coEvery {
            networkClient.authenticatedPost("submitEmail", capture(bodySlot))
        } returns jsonObj("""{"success": true}""")

        // The user unticked the pre-checked email box but still entered.
        api.submitEmail(
            email = "ada@example.com",
            marketingConsent = false,
            publisherUserId = null,
            ageConfirmed = true,
            emailConsent = false,
        )

        val body = bodySlot.captured
        assertEquals(true, body["ageConfirmed"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(false, body["emailConsent"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(false, body["marketingConsent"]?.jsonPrimitive?.booleanOrNull)
    }

    @Test
    fun `view model forwards the checkbox states verbatim`() = runTest(dispatcher) {
        val mockApi = mockk<WinrApi>()
        val storage = mockk<PreferencesStorage>(relaxed = true)
        every { storage.isEmailSubmitted() } returns true
        every { storage.getStreakDay() } returns 0
        every { storage.getTotalEntries() } returns 0
        every { storage.getLastClaimDate() } returns null

        val ageSlot = slot<Boolean>()
        val consentSlot = slot<Boolean>()
        coEvery {
            mockApi.submitEmail(any(), any(), any(), capture(ageSlot), capture(consentSlot))
        } returns WinrApi.SubmitEmailResult(success = true)
        coEvery { mockApi.getActiveGiveaway() } returns WinrApi.GetActiveGiveawayResponse(
            giveaway = Giveaway(id = "g1", title = "T", prizeDescription = "P", streakLadder = listOf(1)),
            claimedToday = true,
            streakDay = 1,
            totalEntries = 1,
        )

        val viewModel = WINRExperienceViewModel(
            api = mockApi,
            preferencesStorage = storage,
            logger = Logger(),
        )

        viewModel.submitEmail(
            email = "ada@example.com",
            marketingConsent = false,
            ageConfirmed = true,
            emailConsent = false,
        )
        advanceUntilIdle()

        assertTrue(ageSlot.captured)
        assertFalse(consentSlot.captured)
    }
}
