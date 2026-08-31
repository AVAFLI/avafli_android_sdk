package com.avafli.avaflisdk

import com.avafli.avaflisdk.domain.ExperienceConfig
import com.avafli.avaflisdk.network.NetworkClient
import com.avafli.avaflisdk.network.AvafliApi
import com.avafli.avaflisdk.services.Logger
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract tests for `sdkConfig.experience.winnerBannerEnabled` (Aug 31 GTM
 * decision): the "WE HAVE A WINNER!" dashboard banner — and the winner-feed
 * modal it alone opens — shows ONLY when the server sends exactly `true`.
 * Absent, `false`, or `null` all mean hidden (default OFF).
 */
class WinnerBannerFlagTest {

    private lateinit var networkClient: NetworkClient
    private lateinit var api: AvafliApi

    @Before
    fun setup() {
        networkClient = mockk()
        api = AvafliApi(networkClient, Logger(isDebug = false))
    }

    private fun jsonObj(raw: String) = Json.parseToJsonElement(raw).jsonObject

    private fun enqueueGiveawayResponse(experienceJson: String) {
        coEvery { networkClient.authenticatedPost("getActiveGiveaway", any()) } returns jsonObj(
            """
            {
              "giveaway": null,
              "claimedToday": false,
              "sdkConfig": { "experience": $experienceJson }
            }
            """
        )
    }

    // ── Parsing ──

    @Test
    fun `winnerBannerEnabled true parses as true`() = runTest {
        enqueueGiveawayResponse("""{ "winnerBannerEnabled": true }""")
        val experience = api.getActiveGiveaway().sdkConfig?.experience
        assertEquals(true, experience?.winnerBannerEnabled)
    }

    @Test
    fun `winnerBannerEnabled false parses as false`() = runTest {
        enqueueGiveawayResponse("""{ "winnerBannerEnabled": false }""")
        val experience = api.getActiveGiveaway().sdkConfig?.experience
        assertEquals(false, experience?.winnerBannerEnabled)
    }

    @Test
    fun `winnerBannerEnabled absent parses as null`() = runTest {
        enqueueGiveawayResponse("""{ "autoOpenEnabled": true }""")
        val experience = api.getActiveGiveaway().sdkConfig?.experience
        assertNull(experience?.winnerBannerEnabled)
        // Sibling flags are untouched by the new field.
        assertEquals(true, experience?.autoOpenEnabled)
    }

    @Test
    fun `winnerBannerEnabled json null parses as null`() = runTest {
        enqueueGiveawayResponse("""{ "winnerBannerEnabled": null }""")
        assertNull(api.getActiveGiveaway().sdkConfig?.experience?.winnerBannerEnabled)
    }

    // ── Gate semantics (the exact condition the dashboard uses) ──

    private fun bannerGate(experience: ExperienceConfig?): Boolean =
        experience?.winnerBannerEnabled == true

    @Test
    fun `banner shows only on exactly true`() {
        assertTrue(bannerGate(ExperienceConfig(winnerBannerEnabled = true)))
        assertFalse(bannerGate(ExperienceConfig(winnerBannerEnabled = false)))
        assertFalse(bannerGate(ExperienceConfig(winnerBannerEnabled = null)))
        assertFalse(bannerGate(ExperienceConfig()))
        assertFalse(bannerGate(null))
    }

    @Test
    fun `model default is hidden`() {
        assertNull(ExperienceConfig().winnerBannerEnabled)
    }
}
