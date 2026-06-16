package com.avafli.winrsdk

/**
 * Defines the environment for the WINR SDK.
 */
enum class WINREnvironment(val baseUrl: String) {
    Production("https://us-central1-winr-9c11f.cloudfunctions.net"),
    // TODO(infra): Staging/Development currently point at the PRODUCTION host because no
    // separate staging/dev backends exist yet. Replace these with real environment URLs
    // before relying on them — until then, selecting Staging/Development hits production.
    Staging("https://us-central1-winr-9c11f.cloudfunctions.net"),
    Development("https://us-central1-winr-9c11f.cloudfunctions.net");

    val isProduction: Boolean get() = this == Production
}
