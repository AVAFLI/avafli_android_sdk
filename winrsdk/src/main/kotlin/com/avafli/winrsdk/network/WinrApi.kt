package com.avafli.winrsdk.network

import com.avafli.winrsdk.WINRError
import com.avafli.winrsdk.domain.Campaign
import com.avafli.winrsdk.domain.DailyEntryGrant
import com.avafli.winrsdk.domain.Milestone
import com.avafli.winrsdk.services.Logger
import kotlinx.serialization.json.*

/**
 * WINR API client — all endpoint interactions.
 */
internal class WinrApi(
    private val networkClient: NetworkClient,
    private val logger: Logger
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /**
     * Register the device and get initial tokens + campaign.
     */
    suspend fun registerDevice(
        apiKey: String,
        deviceFingerprint: String,
        bundleId: String,
        timezone: String
    ): RegisterDeviceResponse {
        val body = mapOf(
            "apiKey" to JsonPrimitive(apiKey),
            "deviceFingerprint" to JsonPrimitive(deviceFingerprint),
            "bundleId" to JsonPrimitive(bundleId),
            "timezone" to JsonPrimitive(timezone),
            "platformOS" to JsonPrimitive("android"),
            "sdkVersion" to JsonPrimitive(SDK_VERSION)
        )

        val response = networkClient.post("registerDevice", body)

        val token = response["token"]?.jsonPrimitive?.contentOrNull
            ?: throw WINRError.RegistrationFailed("Missing token in response")
        val refreshToken = response["refreshToken"]?.jsonPrimitive?.contentOrNull
            ?: throw WINRError.RegistrationFailed("Missing refreshToken in response")
        val uuid = response["uuid"]?.jsonPrimitive?.contentOrNull
            ?: throw WINRError.RegistrationFailed("Missing uuid in response")
        val campaign = response["campaign"]?.let { parseCampaign(it.jsonObject) }

        return RegisterDeviceResponse(
            token = token,
            refreshToken = refreshToken,
            uuid = uuid,
            campaign = campaign
        )
    }

    /**
     * Get the active campaign.
     */
    suspend fun getActiveCampaign(): Campaign {
        val response = networkClient.authenticatedPost("getActiveCampaign")
        val campaignJson = response["campaign"]?.jsonObject
            ?: throw WINRError.NoCampaign()
        return parseCampaign(campaignJson)
    }

    /**
     * Claim daily entries.
     */
    suspend fun claimDailyEntries(timezone: String): ClaimDailyEntriesResponse {
        val body = mapOf(
            "timezone" to JsonPrimitive(timezone),
            "platformOS" to JsonPrimitive("android"),
            "sdkVersion" to JsonPrimitive(SDK_VERSION)
        )

        val response = networkClient.authenticatedPost("claimDailyEntries", body)

        return ClaimDailyEntriesResponse(
            entries = response["entries"]?.jsonPrimitive?.int ?: 0,
            streakDay = response["streakDay"]?.jsonPrimitive?.int ?: 1,
            totalEntries = response["totalEntries"]?.jsonPrimitive?.int ?: 0,
            weeklyBonusEntries = response["weeklyBonusEntries"]?.jsonPrimitive?.intOrNull,
            monthlyBonusEntries = response["monthlyBonusEntries"]?.jsonPrimitive?.intOrNull,
            milestone = response["milestone"]?.jsonObject?.let { parseMilestone(it) }
        )
    }

    /**
     * Claim bonus entries (after watching rewarded video).
     */
    suspend fun claimBonusEntries(timezone: String): ClaimBonusEntriesResponse {
        val body = mapOf(
            "timezone" to JsonPrimitive(timezone),
            "platformOS" to JsonPrimitive("android"),
            "sdkVersion" to JsonPrimitive(SDK_VERSION)
        )

        val response = networkClient.authenticatedPost("claimBonusEntries", body)

        return ClaimBonusEntriesResponse(
            bonusEntries = response["bonusEntries"]?.jsonPrimitive?.int ?: 0,
            totalEntries = response["totalEntries"]?.jsonPrimitive?.int ?: 0
        )
    }

    /**
     * Submit user email.
     */
    suspend fun submitEmail(email: String): Boolean {
        val body = mapOf("email" to JsonPrimitive(email))
        val response = networkClient.authenticatedPost("submitEmail", body)
        return response["success"]?.jsonPrimitive?.booleanOrNull ?: false
    }

    /**
     * Submit user profile data.
     */
    suspend fun submitUserProfile(
        firstName: String? = null,
        lastName: String? = null,
        phone: String? = null,
        smsConsent: Boolean = false,
        maidId: String? = null
    ): Boolean {
        val body = buildMap<String, JsonElement> {
            firstName?.let { put("firstName", JsonPrimitive(it)) }
            lastName?.let { put("lastName", JsonPrimitive(it)) }
            phone?.let { put("phone", JsonPrimitive(it)) }
            put("smsConsent", JsonPrimitive(smsConsent))
            maidId?.let { put("maidId", JsonPrimitive(it)) }
        }

        val response = networkClient.authenticatedPost("submitUserProfile", body)
        return response["success"]?.jsonPrimitive?.booleanOrNull ?: false
    }

    /**
     * Delete all user data (GDPR right-to-be-forgotten).
     */
    suspend fun deleteUserData(): DeleteUserDataResponse {
        val response = networkClient.authenticatedPost("deleteUserData")
        return DeleteUserDataResponse(
            success = response["success"]?.jsonPrimitive?.booleanOrNull ?: false,
            deletedEntries = response["deletedEntries"]?.jsonPrimitive?.intOrNull ?: 0
        )
    }

    /**
     * Register push notification token.
     */
    suspend fun registerPushToken(token: String): Boolean {
        val body = mapOf(
            "token" to JsonPrimitive(token),
            "platform" to JsonPrimitive("android")
        )
        val response = networkClient.authenticatedPost("registerPushToken", body)
        return response["success"]?.jsonPrimitive?.booleanOrNull ?: false
    }

    // --- Response models ---

    data class RegisterDeviceResponse(
        val token: String,
        val refreshToken: String,
        val uuid: String,
        val campaign: Campaign?
    )

    data class ClaimDailyEntriesResponse(
        val entries: Int,
        val streakDay: Int,
        val totalEntries: Int,
        val weeklyBonusEntries: Int?,
        val monthlyBonusEntries: Int?,
        val milestone: Milestone?
    )

    data class ClaimBonusEntriesResponse(
        val bonusEntries: Int,
        val totalEntries: Int
    )

    data class DeleteUserDataResponse(
        val success: Boolean,
        val deletedEntries: Int
    )

    // --- Parsing helpers ---

    private fun parseCampaign(obj: JsonObject): Campaign {
        return json.decodeFromJsonElement(Campaign.serializer(), obj)
    }

    private fun parseMilestone(obj: JsonObject): Milestone {
        return Milestone(
            day = obj["day"]?.jsonPrimitive?.int ?: 0,
            bonusEntries = obj["bonusEntries"]?.jsonPrimitive?.int ?: 0,
            badge = obj["badge"]?.jsonPrimitive?.contentOrNull
        )
    }

    companion object {
        const val SDK_VERSION = "1.0.0"
    }
}
