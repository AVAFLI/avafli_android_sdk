package com.avafli.avaflisdk

import android.content.Context

/**
 * Configuration for the Avafli SDK.
 *
 * @param context Any context — the SDK keeps only `applicationContext`
 * @param apiKey Your publisher API key (`avafli_live_…` for release builds,
 *   `avafli_test_…` sandbox key for debug builds)
 * @param environment Backend to talk to. [AvafliEnvironment.Production] is the
 *   only value; test against the sandbox key, not a separate environment
 * @param user The signed-in user (only `id` is required) or [AvafliUser.GUEST]
 * @param options Additional configuration options
 * @param autoOpen When the SDK auto-opens the experience (default
 *   [AvafliAutoOpen.ALWAYS] — once per calendar day, today's behavior). See
 *   [AvafliAutoOpen]; registration/analytics run on [Avafli.configure] regardless.
 */
data class AvafliConfiguration(
    val context: Context,
    val apiKey: String,
    val environment: AvafliEnvironment = AvafliEnvironment.Production,
    val user: AvafliUser,
    val options: AvafliOptions = AvafliOptions(),
    val autoOpen: AvafliAutoOpen = AvafliAutoOpen.ALWAYS
) {
    val baseUrl: String get() = environment.baseUrl
    val isDebug: Boolean get() = options.debugLogging
}
