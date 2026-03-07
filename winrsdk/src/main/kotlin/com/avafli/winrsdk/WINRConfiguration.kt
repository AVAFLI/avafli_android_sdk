package com.avafli.winrsdk

import android.content.Context

/**
 * Internal configuration holder for the WINR SDK.
 */
internal data class WINRConfiguration(
    val context: Context,
    val publisherKey: String,
    val environment: WINREnvironment,
    val options: WINROptions = WINROptions()
) {
    val baseUrl: String get() = environment.baseUrl
    val isDebug: Boolean get() = options.debugLogging
    val branding: WINRBranding get() = options.branding
}
