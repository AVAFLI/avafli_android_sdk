package com.avafli.winrsdk.services.analytics

/**
 * Interface for analytics tracking.
 * Implement this to integrate with your analytics provider.
 */
interface AnalyticsAdapter {
    /** Track an event with optional parameters. */
    fun trackEvent(name: String, params: Map<String, Any?> = emptyMap())

    /** Track a screen view. */
    fun trackScreenView(screenName: String)

    /** Set a user property. */
    fun setUserProperty(key: String, value: String)
}
