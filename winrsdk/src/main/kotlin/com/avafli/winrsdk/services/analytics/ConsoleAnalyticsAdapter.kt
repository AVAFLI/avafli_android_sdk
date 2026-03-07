package com.avafli.winrsdk.services.analytics

import android.util.Log

/**
 * Debug analytics adapter that logs events to Logcat.
 */
class ConsoleAnalyticsAdapter : AnalyticsAdapter {

    override fun trackEvent(name: String, params: Map<String, Any?>) {
        Log.d(TAG, "Event: $name | Params: $params")
    }

    override fun trackScreenView(screenName: String) {
        Log.d(TAG, "Screen: $screenName")
    }

    override fun setUserProperty(key: String, value: String) {
        Log.d(TAG, "UserProperty: $key = $value")
    }

    companion object {
        private const val TAG = "WINR-Analytics"
    }
}
