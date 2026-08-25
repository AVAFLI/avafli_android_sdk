package com.avafli.avaflisdk

import android.content.Context

/**
 * Configuration for the Avafli SDK.
 *
 * @param context Application or Activity context
 * @param apiKey Your publisher API key
 * @param environment Target environment (default: Production)
 * @param user The authenticated user for this session
 * @param options Additional configuration options
 */
data class AvafliConfiguration(
    val context: Context,
    val apiKey: String,
    val environment: AvafliEnvironment = AvafliEnvironment.Production,
    val user: AvafliUser,
    val options: AvafliOptions = AvafliOptions()
) {
    val baseUrl: String get() = environment.baseUrl
    val isDebug: Boolean get() = options.debugLogging
}
