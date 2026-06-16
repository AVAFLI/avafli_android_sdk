package com.avafli.winrsdk.domain

/**
 * Server-driven SDK configuration returned by registerDevice / getActiveGiveaway.
 * Contains branding overrides and copy text that the backend controls.
 */
data class SdkConfig(
    val branding: SdkBranding? = null,
    val copy: SdkCopy? = null,
    val media: SdkMedia? = null,
    /** Feature flag: show bonus entries (watch video) screen in SDK flow. */
    val bonusEntriesEnabled: Boolean = false
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
    // Nested per-screen copy
    val emailCapture: EmailCaptureCopy? = null,
    val streakDashboard: StreakDashboardCopy? = null,
    val alreadyClaimed: AlreadyClaimedCopy? = null,
    val bonusEntries: BonusEntriesCopy? = null,
    val milestone: MilestoneCopy? = null,
    val completed: CompletedCopy? = null,
    val error: ErrorCopy? = null,
    val howItWorks: HowItWorksCopy? = null,
    val loading: LoadingCopy? = null,
    // Flat backward compatibility fields
    val welcomeTitle: String? = null,
    val welcomeSubtitle: String? = null,
    val dailyClaimButton: String? = null,
    val streakMessage: String? = null,
    val emailConsentText: String? = null,
    val ageGateText: String? = null,
    val rulesLinkText: String? = null,
    val completedTitle: String? = null,
    val completedSubtitle: String? = null,
    val bonusTitle: String? = null,
    val bonusSubtitle: String? = null,
)

data class EmailCaptureCopy(
    val title: String? = null,
    val subtitle: String? = null,
    val prizeHeadline: String? = null,
    val emailLabel: String? = null,
    val emailPlaceholder: String? = null,
    val ageGateText: String? = null,
    val submitButton: String? = null,
    val rulesPrefix: String? = null,
    val rulesLinkText: String? = null,
    val emailConsentText: String? = null,
)

data class StreakDashboardCopy(
    val streakMessage: String? = null,
    val prizeHeadline: String? = null,
    val upcomingLabel: String? = null,
    val claimButton: String? = null,
    val dayRewardLabel: String? = null,
    val claimDescription: String? = null,
    val entriesLabel: String? = null,
    val bonusProgressLabel: String? = null,
    val weekLabel: String? = null,
    val monthLabel: String? = null,
    val bonusEarnedText: String? = null,
)

data class AlreadyClaimedCopy(
    val title: String? = null,
    val subtitle: String? = null,
    val doneButton: String? = null
)

data class BonusEntriesCopy(
    val title: String? = null,
    val subtitle: String? = null,
    val watchButton: String? = null,
    val skipText: String? = null
)

data class MilestoneCopy(
    val title: String? = null,
    val subtitle: String? = null,
    val continueButton: String? = null
)

data class CompletedCopy(
    val title: String? = null,
    val subtitle: String? = null,
    val closeButton: String? = null
)

data class ErrorCopy(
    val title: String? = null,
    val subtitle: String? = null,
    val closeButton: String? = null
)

data class HowItWorksCopy(
    val title: String? = null,
    val subtitle: String? = null,
    val step1Title: String? = null,
    val step1Desc: String? = null,
    val step2Title: String? = null,
    val step2Desc: String? = null,
    val step3Title: String? = null,
    val step3Desc: String? = null,
    val step4Title: String? = null,
    val step4Desc: String? = null,
    val tipText: String? = null,
    val gotItButton: String? = null,
)

data class LoadingCopy(
    val text: String? = null
)

/**
 * Server-driven media overrides for screen backgrounds and hero areas.
 * All fields are optional — nil values fall back to hardcoded defaults.
 */
data class SdkMedia(
    val emailCapture: ScreenMedia? = null,
    val streakDashboard: ScreenMedia? = null,
    val bonusEntries: ScreenMedia? = null,
    val milestone: ScreenMedia? = null,
    val completed: ScreenMedia? = null,
    val howItWorks: ScreenMedia? = null
)

/**
 * Per-screen media URLs that can be used for hero images or backgrounds.
 * Either imageUrl or lottieUrl (or both) can be set.
 */
data class ScreenMedia(
    val imageUrl: String? = null,
    val lottieUrl: String? = null
)
