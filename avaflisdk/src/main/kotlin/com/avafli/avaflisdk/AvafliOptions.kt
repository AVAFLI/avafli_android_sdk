package com.avafli.avaflisdk

/**
 * Additional options for configuring the Avafli SDK.
 *
 * The V2 experience is server-driven: branding (logo, prize image, primary
 * color) comes from the Avafli dashboard, and the experience auto-opens on the
 * first app-open of each day (server kill-switch: sdkConfig.experience).
 * The rewarded-video provider option has been removed (bonus flow is parked).
 */
data class AvafliOptions(
    /** Enable verbose logging. */
    val debugLogging: Boolean = false,
    /** Analytics adapter for tracking events. */
    val analyticsAdapter: com.avafli.avaflisdk.services.analytics.AnalyticsAdapter? = null,
    /** Enable streak push reminders (requires Firebase Cloud Messaging in the host app). */
    val enablePushReminders: Boolean = true,
    /** Enable certificate pinning (recommended for production). */
    val enableCertificatePinning: Boolean = true,
    /** Custom timeout for network requests in seconds. */
    val networkTimeoutSeconds: Long = 30
)
