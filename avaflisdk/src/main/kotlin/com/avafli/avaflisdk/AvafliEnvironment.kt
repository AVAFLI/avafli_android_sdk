package com.avafli.avaflisdk

/**
 * The backend the SDK talks to. Production is the only backend: sandbox
 * (`avafli_test_…`) keys hit the same host and isolate users and entries in a
 * sandbox tenant, so there is no separate staging value.
 */
enum class AvafliEnvironment(val baseUrl: String) {
    Production("https://us-central1-winr-9c11f.cloudfunctions.net");

    val isProduction: Boolean get() = this == Production
}
