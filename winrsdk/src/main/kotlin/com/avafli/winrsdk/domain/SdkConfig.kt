package com.avafli.winrsdk.domain

/**
 * Server-driven SDK configuration returned by registerDevice / getActiveCampaign.
 * Contains branding overrides and copy text that the backend controls.
 */
data class SdkConfig(
    val branding: SdkBranding? = null,
    val copy: SdkCopy? = null
)

/**
 * Server-driven branding overrides.
 * All fields are optional — nil values fall back to code-level WINRBranding defaults.
 */
data class SdkBranding(
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val backgroundColor: String? = null,
    val logoUrl: String? = null,
    val fontFamily: String? = null
)

/**
 * Server-driven copy/text overrides for the experience screens.
 * All fields are optional — nil values fall back to hardcoded defaults.
 */
data class SdkCopy(
    val welcomeTitle: String? = null,
    val welcomeSubtitle: String? = null,
    val claimButtonTitle: String? = null,
    val bonusTitle: String? = null,
    val bonusSubtitle: String? = null,
    val completedTitle: String? = null,
    val completedSubtitle: String? = null
)
