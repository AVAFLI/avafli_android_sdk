package com.avafli.winrsdk

/**
 * Defines the environment for the WINR SDK.
 */
enum class WINREnvironment(val baseUrl: String) {
    Production("https://us-central1-winr-9c11f.cloudfunctions.net");

    val isProduction: Boolean get() = this == Production
}
