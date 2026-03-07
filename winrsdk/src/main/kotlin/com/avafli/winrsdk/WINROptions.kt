package com.avafli.winrsdk

/**
 * Additional options for configuring the WINR SDK.
 */
data class WINROptions(
    /** Enable verbose logging. */
    val debugLogging: Boolean = false,
    /** Custom branding for the experience UI. */
    val branding: WINRBranding = WINRBranding(),
    /** Analytics adapter for tracking events. */
    val analyticsAdapter: com.avafli.winrsdk.services.analytics.AnalyticsAdapter? = null,
    /** Enable certificate pinning (recommended for production). */
    val enableCertificatePinning: Boolean = true,
    /** Custom timeout for network requests in seconds. */
    val networkTimeoutSeconds: Long = 30
)
