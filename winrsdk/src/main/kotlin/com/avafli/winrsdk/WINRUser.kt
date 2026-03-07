package com.avafli.winrsdk

/**
 * Represents user information for the WINR SDK.
 */
data class WINRUser(
    val id: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val smsConsent: Boolean = false
)
