package com.avafli.winrsdk

import android.content.Context

/**
 * Configuration for the WINR SDK.
 *
 * @param context Application or Activity context
 * @param apiKey Your publisher API key
 * @param environment Target environment (default: Production)
 * @param options Additional configuration options
 */
data class WINRConfiguration(
    val context: Context,
    val apiKey: String,
    val environment: WINREnvironment = WINREnvironment.Production,
    val options: WINROptions = WINROptions()
) {
    val baseUrl: String get() = environment.baseUrl
    val isDebug: Boolean get() = options.debugLogging
    val branding: WINRBranding get() = options.branding
}
