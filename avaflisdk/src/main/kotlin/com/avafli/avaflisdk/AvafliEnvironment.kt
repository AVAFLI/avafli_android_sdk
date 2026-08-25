package com.avafli.avaflisdk

/**
 * Defines the environment for the Avafli SDK.
 */
enum class AvafliEnvironment(val baseUrl: String) {
    Production("https://us-central1-winr-9c11f.cloudfunctions.net");

    val isProduction: Boolean get() = this == Production
}
