package com.avafli.winrsdk.network

import com.avafli.winrsdk.WINRError
import com.avafli.winrsdk.domain.Giveaway
import com.avafli.winrsdk.domain.DailyEntryGrant
import com.avafli.winrsdk.domain.Milestone
import com.avafli.winrsdk.domain.SdkBranding
import com.avafli.winrsdk.domain.SdkConfig
import com.avafli.winrsdk.domain.*
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
     * Register the device and get initial tokens + giveaway.
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
        val giveaway = response["giveaway"]?.let { parseGiveaway(it.jsonObject) }
        val sdkConfig = response["sdkConfig"]?.jsonObject?.let { parseSdkConfig(it) }

        return RegisterDeviceResponse(
            token = token,
            refreshToken = refreshToken,
            uuid = uuid,
            giveaway = giveaway,
            sdkConfig = sdkConfig
        )
    }

    /**
     * Get the active giveaway and latest SDK config.
     */
    suspend fun getActiveGiveaway(): GetActiveGiveawayResponse {
        val response = networkClient.authenticatedPost("getActiveGiveaway")
        val giveawayJson = response["giveaway"]?.jsonObject
            ?: throw WINRError.NoGiveaway()
        val giveaway = parseGiveaway(giveawayJson)
        val sdkConfig = response["sdkConfig"]?.jsonObject?.let { parseSdkConfig(it) }
        return GetActiveGiveawayResponse(giveaway = giveaway, sdkConfig = sdkConfig)
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
     * Submit user email with marketing consent and optional publisher user ID.
     */
    suspend fun submitEmail(email: String, marketingConsent: Boolean = false, publisherUserId: String? = null): Boolean {
        val body = buildMap<String, JsonElement> {
            put("email", JsonPrimitive(email))
            put("marketingConsent", JsonPrimitive(marketingConsent))
            publisherUserId?.let { put("publisherUserId", JsonPrimitive(it)) }
        }
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
        maidId: String? = null,
        publisherUserId: String? = null
    ): Boolean {
        val body = buildMap<String, JsonElement> {
            firstName?.let { put("firstName", JsonPrimitive(it)) }
            lastName?.let { put("lastName", JsonPrimitive(it)) }
            phone?.let { put("phone", JsonPrimitive(it)) }
            put("smsConsent", JsonPrimitive(smsConsent))
            maidId?.let { put("maidId", JsonPrimitive(it)) }
            publisherUserId?.let { put("publisherUserId", JsonPrimitive(it)) }
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
        val giveaway: Giveaway?,
        val sdkConfig: SdkConfig? = null
    )

    data class GetActiveGiveawayResponse(
        val giveaway: Giveaway,
        val sdkConfig: SdkConfig? = null
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

    private fun parseGiveaway(obj: JsonObject): Giveaway {
        return json.decodeFromJsonElement(Giveaway.serializer(), obj)
    }

    private fun parseSdkConfig(obj: JsonObject): SdkConfig {
        val brandingJson = obj["branding"]?.jsonObject
        val copyJson = obj["copy"]?.jsonObject
        val mediaJson = obj["media"]?.jsonObject

        val branding = brandingJson?.let {
            SdkBranding(
                primaryColor = it["primaryColor"]?.jsonPrimitive?.contentOrNull,
                secondaryColor = it["secondaryColor"]?.jsonPrimitive?.contentOrNull,
                backgroundColor = it["backgroundColor"]?.jsonPrimitive?.contentOrNull,
                logoUrl = it["logoUrl"]?.jsonPrimitive?.contentOrNull,
                fontFamily = it["fontFamily"]?.jsonPrimitive?.contentOrNull
            )
        }

        val copy = copyJson?.let {
            SdkCopy(
                // Parse nested per-screen copy objects
                emailCapture = it["emailCapture"]?.jsonObject?.let { parseEmailCaptureCopy(it) },
                streakDashboard = it["streakDashboard"]?.jsonObject?.let { parseStreakDashboardCopy(it) },
                alreadyClaimed = it["alreadyClaimed"]?.jsonObject?.let { parseAlreadyClaimedCopy(it) },
                bonusEntries = it["bonusEntries"]?.jsonObject?.let { parseBonusEntriesCopy(it) },
                milestone = it["milestone"]?.jsonObject?.let { parseMilestoneCopy(it) },
                completed = it["completed"]?.jsonObject?.let { parseCompletedCopy(it) },
                error = it["error"]?.jsonObject?.let { parseErrorCopy(it) },
                howItWorks = it["howItWorks"]?.jsonObject?.let { parseHowItWorksCopy(it) },
                loading = it["loading"]?.jsonObject?.let { parseLoadingCopy(it) },
                // Parse flat backward compatibility fields
                welcomeTitle = it["welcomeTitle"]?.jsonPrimitive?.contentOrNull,
                welcomeSubtitle = it["welcomeSubtitle"]?.jsonPrimitive?.contentOrNull,
                dailyClaimButton = it["dailyClaimButton"]?.jsonPrimitive?.contentOrNull,
                streakMessage = it["streakMessage"]?.jsonPrimitive?.contentOrNull,
                emailConsentText = it["emailConsentText"]?.jsonPrimitive?.contentOrNull,
                ageGateText = it["ageGateText"]?.jsonPrimitive?.contentOrNull,
                rulesLinkText = it["rulesLinkText"]?.jsonPrimitive?.contentOrNull
            )
        }

        val media = mediaJson?.let { parseSdkMedia(it) }

        val bonusEntriesEnabled = try { obj["bonusEntriesEnabled"]?.jsonPrimitive?.boolean ?: false } catch (_: Exception) { false }

        return SdkConfig(branding = branding, copy = copy, media = media, bonusEntriesEnabled = bonusEntriesEnabled)
    }

    private fun parseMilestone(obj: JsonObject): Milestone {
        return Milestone(
            day = obj["day"]?.jsonPrimitive?.int ?: 0,
            bonusEntries = obj["bonusEntries"]?.jsonPrimitive?.int ?: 0,
            badge = obj["badge"]?.jsonPrimitive?.contentOrNull
        )
    }

    private fun parseEmailCaptureCopy(obj: JsonObject): EmailCaptureCopy {
        return EmailCaptureCopy(
            title = obj["title"]?.jsonPrimitive?.contentOrNull,
            subtitle = obj["subtitle"]?.jsonPrimitive?.contentOrNull,
            prizeHeadline = obj["prizeHeadline"]?.jsonPrimitive?.contentOrNull,
            emailLabel = obj["emailLabel"]?.jsonPrimitive?.contentOrNull,
            emailPlaceholder = obj["emailPlaceholder"]?.jsonPrimitive?.contentOrNull,
            ageGateText = obj["ageGateText"]?.jsonPrimitive?.contentOrNull,
            submitButton = obj["submitButton"]?.jsonPrimitive?.contentOrNull,
            rulesPrefix = obj["rulesPrefix"]?.jsonPrimitive?.contentOrNull,
            rulesLinkText = obj["rulesLinkText"]?.jsonPrimitive?.contentOrNull,
            emailConsentText = obj["emailConsentText"]?.jsonPrimitive?.contentOrNull
        )
    }

    private fun parseStreakDashboardCopy(obj: JsonObject): StreakDashboardCopy {
        return StreakDashboardCopy(
            streakMessage = obj["streakMessage"]?.jsonPrimitive?.contentOrNull,
            prizeHeadline = obj["prizeHeadline"]?.jsonPrimitive?.contentOrNull,
            upcomingLabel = obj["upcomingLabel"]?.jsonPrimitive?.contentOrNull,
            claimButton = obj["claimButton"]?.jsonPrimitive?.contentOrNull,
            dayRewardLabel = obj["dayRewardLabel"]?.jsonPrimitive?.contentOrNull,
            claimDescription = obj["claimDescription"]?.jsonPrimitive?.contentOrNull,
            entriesLabel = obj["entriesLabel"]?.jsonPrimitive?.contentOrNull,
            bonusProgressLabel = obj["bonusProgressLabel"]?.jsonPrimitive?.contentOrNull,
            weekLabel = obj["weekLabel"]?.jsonPrimitive?.contentOrNull,
            monthLabel = obj["monthLabel"]?.jsonPrimitive?.contentOrNull,
            bonusEarnedText = obj["bonusEarnedText"]?.jsonPrimitive?.contentOrNull
        )
    }

    private fun parseAlreadyClaimedCopy(obj: JsonObject): AlreadyClaimedCopy {
        return AlreadyClaimedCopy(
            title = obj["title"]?.jsonPrimitive?.contentOrNull,
            subtitle = obj["subtitle"]?.jsonPrimitive?.contentOrNull,
            doneButton = obj["doneButton"]?.jsonPrimitive?.contentOrNull
        )
    }

    private fun parseBonusEntriesCopy(obj: JsonObject): BonusEntriesCopy {
        return BonusEntriesCopy(
            title = obj["title"]?.jsonPrimitive?.contentOrNull,
            subtitle = obj["subtitle"]?.jsonPrimitive?.contentOrNull,
            watchButton = obj["watchButton"]?.jsonPrimitive?.contentOrNull,
            skipText = obj["skipText"]?.jsonPrimitive?.contentOrNull
        )
    }

    private fun parseMilestoneCopy(obj: JsonObject): MilestoneCopy {
        return MilestoneCopy(
            title = obj["title"]?.jsonPrimitive?.contentOrNull,
            subtitle = obj["subtitle"]?.jsonPrimitive?.contentOrNull,
            continueButton = obj["continueButton"]?.jsonPrimitive?.contentOrNull
        )
    }

    private fun parseCompletedCopy(obj: JsonObject): CompletedCopy {
        return CompletedCopy(
            title = obj["title"]?.jsonPrimitive?.contentOrNull,
            subtitle = obj["subtitle"]?.jsonPrimitive?.contentOrNull,
            closeButton = obj["closeButton"]?.jsonPrimitive?.contentOrNull
        )
    }

    private fun parseErrorCopy(obj: JsonObject): ErrorCopy {
        return ErrorCopy(
            title = obj["title"]?.jsonPrimitive?.contentOrNull,
            subtitle = obj["subtitle"]?.jsonPrimitive?.contentOrNull,
            closeButton = obj["closeButton"]?.jsonPrimitive?.contentOrNull
        )
    }

    private fun parseHowItWorksCopy(obj: JsonObject): HowItWorksCopy {
        return HowItWorksCopy(
            title = obj["title"]?.jsonPrimitive?.contentOrNull,
            subtitle = obj["subtitle"]?.jsonPrimitive?.contentOrNull,
            step1Title = obj["step1Title"]?.jsonPrimitive?.contentOrNull,
            step1Desc = obj["step1Desc"]?.jsonPrimitive?.contentOrNull,
            step2Title = obj["step2Title"]?.jsonPrimitive?.contentOrNull,
            step2Desc = obj["step2Desc"]?.jsonPrimitive?.contentOrNull,
            step3Title = obj["step3Title"]?.jsonPrimitive?.contentOrNull,
            step3Desc = obj["step3Desc"]?.jsonPrimitive?.contentOrNull,
            step4Title = obj["step4Title"]?.jsonPrimitive?.contentOrNull,
            step4Desc = obj["step4Desc"]?.jsonPrimitive?.contentOrNull,
            tipText = obj["tipText"]?.jsonPrimitive?.contentOrNull,
            gotItButton = obj["gotItButton"]?.jsonPrimitive?.contentOrNull
        )
    }

    private fun parseLoadingCopy(obj: JsonObject): LoadingCopy {
        return LoadingCopy(
            text = obj["text"]?.jsonPrimitive?.contentOrNull
        )
    }

    private fun parseSdkMedia(obj: JsonObject): SdkMedia {
        return SdkMedia(
            emailCapture = obj["emailCapture"]?.jsonObject?.let { parseScreenMedia(it) },
            streakDashboard = obj["streakDashboard"]?.jsonObject?.let { parseScreenMedia(it) },
            bonusEntries = obj["bonusEntries"]?.jsonObject?.let { parseScreenMedia(it) },
            milestone = obj["milestone"]?.jsonObject?.let { parseScreenMedia(it) },
            completed = obj["completed"]?.jsonObject?.let { parseScreenMedia(it) },
            howItWorks = obj["howItWorks"]?.jsonObject?.let { parseScreenMedia(it) }
        )
    }

    private fun parseScreenMedia(obj: JsonObject): ScreenMedia {
        return ScreenMedia(
            imageUrl = obj["imageUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() },
            lottieUrl = obj["lottieUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
        )
    }

    companion object {
        const val SDK_VERSION = "1.0.0"
    }
}
