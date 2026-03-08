package com.avafli.winrsdk

/**
 * Represents user information for the WINR SDK.
 *
 * Only the user ID is required. Email and consent are handled
 * internally by the SDK's capture flow — publishers don't set them.
 */
data class WINRUser(
    /** Your app's unique user ID (Firebase UID, database ID, etc.) */
    val id: String,
    /** Optional first name for profile enrichment */
    val firstName: String? = null,
    /** Optional last name for profile enrichment */
    val lastName: String? = null,
    /** Optional phone number */
    val phone: String? = null
) {
    // Internal state managed by the SDK
    internal var email: String? = null
    internal var isEmailPermissioned: Boolean = false
    internal var isSMSPermissioned: Boolean = false

    companion object {
        fun anonymous(deviceId: String) = WINRUser(id = "anon-$deviceId")
    }
}
