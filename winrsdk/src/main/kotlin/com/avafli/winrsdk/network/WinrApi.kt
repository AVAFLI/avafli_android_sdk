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
            "platformOS" to JsonPrimitive("Android"),
            "sdkVersion" to JsonPrimitive(SDK_VERSION)
        )

        val response = networkClient.post("registerDevice", body)

        val token = response["token"]?.jsonPrimitive?.contentOrNull
            ?: throw WINRError.RegistrationFailed("Missing token in response")
        val refreshToken = response["refreshToken"]?.jsonPrimitive?.contentOrNull
            ?: throw WINRError.RegistrationFailed("Missing refreshToken in response")
        val uuid = response["uuid"]?.jsonPrimitive?.contentOrNull
            ?: throw WINRError.RegistrationFailed("Missing uuid in response")
        val giveaway = (response["giveaway"] as? JsonObject)?.let { parseGiveaway(it) }
        val sdkConfig = (response["sdkConfig"] as? JsonObject)?.let { parseSdkConfig(it) }

        return RegisterDeviceResponse(
            token = token,
            refreshToken = refreshToken,
            uuid = uuid,
            giveaway = giveaway,
            sdkConfig = sdkConfig,
            claimedToday = response["claimedToday"]?.jsonPrimitive?.booleanOrNull,
            streakDay = response["streakDay"]?.jsonPrimitive?.intOrNull,
            totalEntries = response["totalEntries"]?.jsonPrimitive?.intOrNull,
            optedOut = response["optedOut"]?.jsonPrimitive?.booleanOrNull,
            prizeClaim = (response["prizeClaim"] as? JsonObject)?.let { parsePrizeClaim(it) },
            // Soft email verification (2.7.0): explicit `false` means the person
            // typed a brand-new email that hasn't been confirmed. Absent/null for
            // verified, partner-passed, adoption-verified, and no-email users.
            emailVerified = response["emailVerified"]?.jsonPrimitive?.booleanOrNull,
        )
    }

    /**
     * Get the active giveaway and latest SDK config.
     */
    suspend fun getActiveGiveaway(): GetActiveGiveawayResponse {
        val response = networkClient.authenticatedPost("getActiveGiveaway")
        val giveaway = (response["giveaway"] as? JsonObject)?.let { parseGiveaway(it) }
        val sdkConfig = (response["sdkConfig"] as? JsonObject)?.let { parseSdkConfig(it) }
        return GetActiveGiveawayResponse(
            giveaway = giveaway,
            sdkConfig = sdkConfig,
            // Backend is the source of truth for the streak. Surfacing these lets the
            // UI reflect the authoritative streak — essential after cross-device
            // adoption, where the local streak belongs to a throwaway device-user.
            claimedToday = response["claimedToday"]?.jsonPrimitive?.booleanOrNull,
            streakDay = response["streakDay"]?.jsonPrimitive?.intOrNull,
            totalEntries = response["totalEntries"]?.jsonPrimitive?.intOrNull,
            emailConsentStatus = response["emailConsentStatus"]?.jsonPrimitive?.booleanOrNull,
            optedOut = response["optedOut"]?.jsonPrimitive?.booleanOrNull,
            prizeClaim = (response["prizeClaim"] as? JsonObject)?.let { parsePrizeClaim(it) },
            // Soft email verification (2.7.0): explicit `false` → unverified.
            emailVerified = response["emailVerified"]?.jsonPrimitive?.booleanOrNull,
        )
    }

    /**
     * Winner prize claim: submit the claim form for [giveawayId]. Field names are
     * a FIXED contract with the backend's submitPrizeClaim callable
     * (functions/src/prizeclaim.ts) — do not rename.
     */
    suspend fun submitPrizeClaim(
        giveawayId: String,
        firstName: String,
        lastName: String,
        phone: String? = null,
        street: String,
        apt: String? = null,
        city: String,
        state: String,
        zip: String,
        country: String,
        photoBase64: String? = null,
        story: String? = null,
    ): SubmitPrizeClaimResponse {
        val body = buildMap<String, JsonElement> {
            put("giveawayId", JsonPrimitive(giveawayId))
            put("firstName", JsonPrimitive(firstName))
            put("lastName", JsonPrimitive(lastName))
            put("street", JsonPrimitive(street))
            put("city", JsonPrimitive(city))
            put("state", JsonPrimitive(state))
            put("zip", JsonPrimitive(zip))
            put("country", JsonPrimitive(country))
            phone?.takeIf { it.isNotEmpty() }?.let { put("phone", JsonPrimitive(it)) }
            apt?.takeIf { it.isNotEmpty() }?.let { put("apt", JsonPrimitive(it)) }
            photoBase64?.let { put("photoBase64", JsonPrimitive(it)) }
            story?.takeIf { it.isNotEmpty() }?.let { put("story", JsonPrimitive(it)) }
        }

        val response = networkClient.authenticatedPost("submitPrizeClaim", body)

        return SubmitPrizeClaimResponse(
            claimNumber = response["claimNumber"]?.jsonPrimitive?.contentOrNull ?: "",
            submittedAt = response["submittedAt"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }

    /**
     * Claim daily entries.
     */
    suspend fun claimDailyEntries(timezone: String): ClaimDailyEntriesResponse {
        val body = mapOf(
            "timezone" to JsonPrimitive(timezone),
            "platformOS" to JsonPrimitive("Android"),
            "sdkVersion" to JsonPrimitive(SDK_VERSION)
        )

        val response = networkClient.authenticatedPost("claimDailyEntries", body)

        return ClaimDailyEntriesResponse(
            entries = response["entries"]?.jsonPrimitive?.int ?: 0,
            streakDay = response["streakDay"]?.jsonPrimitive?.int ?: 1,
            totalEntries = response["totalEntries"]?.jsonPrimitive?.int ?: 0,
            weeklyBonusEntries = response["weeklyBonusEntries"]?.jsonPrimitive?.intOrNull,
            monthlyBonusEntries = response["monthlyBonusEntries"]?.jsonPrimitive?.intOrNull,
            milestone = response["milestone"]?.jsonObject?.let { parseMilestone(it) },
            monthlyMilestone = response["monthlyMilestone"]?.jsonObject?.let { parseMilestone(it) }
        )
    }

    /**
     * RTD opt-out: tombstones the person on the backend (identity-wide, PII anonymized,
     * email suppressed). The SDK must never present the experience again afterwards.
     */
    suspend fun optOut(): Boolean {
        val response = networkClient.authenticatedPost("optOut")
        return response["success"]?.jsonPrimitive?.booleanOrNull ?: false
    }

    /**
     * Submit user email with the capture screen's two consent states and an
     * optional publisher user ID.
     *
     * [ageConfirmed] is the 18+ age-gate checkbox (required to submit) and
     * [marketingConsent] is the marketing-email checkbox (pre-checked, but the
     * user may untick it and still enter). Both carry the real checkbox states
     * and are stored server-side. [marketingConsent] governs MARKETING email
     * only — winner contact is operational and happens regardless.
     * [ageConfirmed] is always sent: the backend reads it to detect a 2.4.0+
     * client.
     */
    suspend fun submitEmail(
        email: String,
        marketingConsent: Boolean = false,
        publisherUserId: String? = null,
        ageConfirmed: Boolean = false,
    ): SubmitEmailResult {
        val body = buildMap<String, JsonElement> {
            put("email", JsonPrimitive(email))
            put("ageConfirmed", JsonPrimitive(ageConfirmed))
            put("marketingConsent", JsonPrimitive(marketingConsent))
            publisherUserId?.let { put("publisherUserId", JsonPrimitive(it)) }
        }
        val response = networkClient.authenticatedPost("submitEmail", body)
        val result = SubmitEmailResult(
            success = response["success"]?.jsonPrimitive?.booleanOrNull ?: false,
            adopted = response["adopted"]?.jsonPrimitive?.booleanOrNull ?: false,
            verificationRequired = response["verificationRequired"]?.jsonPrimitive?.booleanOrNull ?: false,
            uuid = response["uuid"]?.jsonPrimitive?.contentOrNull,
            token = response["token"]?.jsonPrimitive?.contentOrNull,
            refreshToken = response["refreshToken"]?.jsonPrimitive?.contentOrNull,
            // Soft email verification (2.7.0): a brand-new typed email comes back
            // `emailVerified: false` (and often `emailVerificationSent: true`),
            // which drives the persistent "Verify your email" dashboard chip.
            emailVerified = response["emailVerified"]?.jsonPrimitive?.booleanOrNull,
            emailVerificationSent = response["emailVerificationSent"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
        // Cross-device streak unification: this email already belonged to an
        // existing user under this publisher — switch to that canonical user so
        // the person keeps one streak per publisher and can't double-claim.
        if (result.adopted && result.token != null && result.uuid != null) {
            networkClient.saveSession(result.token, result.refreshToken, result.uuid)
            logger.info("Adopted existing account — streak unified across devices")
        }
        return result
    }

    /**
     * Completes a verification-gated adoption with the emailed 6-digit code.
     * Approved → the same session switch as a direct adoption.
     */
    suspend fun verifyAdoptionCode(code: String): SubmitEmailResult {
        val response = networkClient.authenticatedPost(
            "verifyAdoptionCode",
            mapOf("code" to JsonPrimitive(code)),
        )
        val result = SubmitEmailResult(
            success = response["success"]?.jsonPrimitive?.booleanOrNull ?: false,
            adopted = response["adopted"]?.jsonPrimitive?.booleanOrNull ?: false,
            uuid = response["uuid"]?.jsonPrimitive?.contentOrNull,
            token = response["token"]?.jsonPrimitive?.contentOrNull,
            refreshToken = response["refreshToken"]?.jsonPrimitive?.contentOrNull,
        )
        if (result.adopted && result.token != null && result.uuid != null) {
            networkClient.saveSession(result.token, result.refreshToken, result.uuid)
            logger.info("Adoption verified — streak unified across devices")
        }
        return result
    }

    /**
     * Confirms soft email verification (2.7.0) with the emailed 6-digit code.
     * Same request/response envelope as [verifyAdoptionCode]: returns
     * `{ verified: true }` on success and THROWS on failure with a message
     * carrying the backend's "expired" / "attempts" / mismatch text, which the
     * caller maps to the shared code-error taxonomy. Unlike adoption, this makes
     * no session switch — it only clears the unverified state for prize-draw
     * eligibility (never gates daily play).
     */
    suspend fun confirmEmailVerification(code: String): Boolean {
        val response = networkClient.authenticatedPost(
            "confirmEmailVerification",
            mapOf("code" to JsonPrimitive(code)),
        )
        return response["verified"]?.jsonPrimitive?.booleanOrNull ?: false
    }

    /**
     * Requests a fresh soft-verification email (2.7.0). No args; returns whether
     * the backend re-sent the code. Mirrors the adoption resend flow.
     */
    suspend fun resendEmailVerification(): Boolean {
        val response = networkClient.authenticatedPost("resendEmailVerification")
        return response["sent"]?.jsonPrimitive?.booleanOrNull ?: false
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
        val sdkConfig: SdkConfig? = null,
        val claimedToday: Boolean? = null,
        val streakDay: Int? = null,
        val totalEntries: Int? = null,
        /** True when this device/person has opted out (RTD) — never auto-present. */
        val optedOut: Boolean? = null,
        /**
         * Present only when this person is the drawn winner of one of this
         * publisher's giveaways (winner prize-claim flow).
         */
        val prizeClaim: PrizeClaimBlock? = null,
        /**
         * Soft email verification (2.7.0). Explicit `false` → the person typed a
         * brand-new email that hasn't been confirmed (show the chip). Absent/null
         * for verified, partner-passed, adoption-verified, and no-email users —
         * treat ONLY explicit `false` as unverified.
         */
        val emailVerified: Boolean? = null,
    )

    data class GetActiveGiveawayResponse(
        val giveaway: Giveaway?,
        val sdkConfig: SdkConfig? = null,
        val claimedToday: Boolean? = null,
        val streakDay: Int? = null,
        val totalEntries: Int? = null,
        val emailConsentStatus: Boolean? = null,
        /** True when this person has opted out (RTD) — the SDK must never auto-present. */
        val optedOut: Boolean? = null,
        /**
         * Present only when this person is the drawn winner of one of this
         * publisher's giveaways and the winner record is still claimable.
         * `status == "pending"` drives the winner splash → claim form flow;
         * `"submitted"` means the form was already sent (normal dashboard shows).
         */
        val prizeClaim: PrizeClaimBlock? = null,
        /**
         * Soft email verification (2.7.0). Explicit `false` → unverified (show the
         * chip); absent/null otherwise. Authoritative status echoed on every open.
         */
        val emailVerified: Boolean? = null,
    )

    data class SubmitPrizeClaimResponse(
        val claimNumber: String,
        /** ISO date. */
        val submittedAt: String,
    )

    /**
     * Result of submitEmail. When the email matched an existing account under this
     * publisher (another device/SDK), the backend adopts that canonical user so the
     * streak follows the person across devices — [adopted] is true and
     * [token]/[refreshToken]/[uuid] carry the canonical user's credentials, which the
     * SDK must switch to.
     */
    data class SubmitEmailResult(
        val success: Boolean,
        val adopted: Boolean = false,
        /** The typed email matches an EXISTING account and the OTP gate is on:
         *  no merge happened — show the code screen and call verifyAdoptionCode. */
        val verificationRequired: Boolean = false,
        val uuid: String? = null,
        val token: String? = null,
        val refreshToken: String? = null,
        /**
         * Soft email verification (2.7.0). `false` when the just-typed email is a
         * brand-new, unconfirmed address → the SDK shows the persistent "Verify
         * your email" chip. Absent/null for partner-passed or adoption paths.
         */
        val emailVerified: Boolean? = null,
        /** True when the backend dispatched a verification email for this submit. */
        val emailVerificationSent: Boolean = false,
    )

    data class ClaimDailyEntriesResponse(
        val entries: Int,
        val streakDay: Int,
        val totalEntries: Int,
        val weeklyBonusEntries: Int?,
        val monthlyBonusEntries: Int?,
        val milestone: Milestone?,
        val monthlyMilestone: Milestone? = null
    )

    // --- Parsing helpers ---

    private fun parseGiveaway(obj: JsonObject): Giveaway {
        return json.decodeFromJsonElement(Giveaway.serializer(), obj)
    }

    private fun parsePrizeClaim(obj: JsonObject): PrizeClaimBlock? = try {
        json.decodeFromJsonElement(PrizeClaimBlock.serializer(), obj)
    } catch (e: Exception) {
        logger.warn("Failed to parse prizeClaim block: ${e.message}")
        null
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

        val experience = obj["experience"]?.jsonObject?.let {
            ExperienceConfig(
                autoOpenEnabled = it["autoOpenEnabled"]?.jsonPrimitive?.booleanOrNull,
                unregisteredImpressionCap = it["unregisteredImpressionCap"]?.jsonPrimitive?.intOrNull,
                requireDismissClick = it["requireDismissClick"]?.jsonPrimitive?.booleanOrNull
            )
        }

        return SdkConfig(
            branding = branding,
            copy = copy,
            media = media,
            bonusEntriesEnabled = bonusEntriesEnabled,
            rulesUrl = obj["rulesUrl"]?.jsonPrimitive?.contentOrNull,
            ageGateMinAge = obj["ageGateMinAge"]?.jsonPrimitive?.intOrNull,
            experience = experience
        )
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
        // Single source of truth for the wire-format sdk_version. Keep in sync
        // with the Maven publish version in winrsdk/build.gradle.kts.
        const val SDK_VERSION = "2.7.0"
    }
}
