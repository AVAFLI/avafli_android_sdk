package com.avafli.winrsdk

/**
 * Represents user information for the WINR SDK.
 *
 * User ID, first name, and last name are required.
 * Phone is optional. Email and consent are handled
 * internally by the SDK's capture flow — publishers don't set them.
 */
data class WINRUser(
    /** Your app's unique user ID (Firebase UID, database ID, etc.) */
    val id: String,
    /** User's first name */
    val firstName: String,
    /** User's last name */
    val lastName: String,
    /** Optional phone number */
    val phone: String? = null
)
