package com.avafli.winrsdk

/**
 * Represents user information for the WINR SDK.
 *
 * User ID, first name, and last name are required. Phone and email are optional.
 * Consent is always handled inside the SDK's capture flow — publishers never set it.
 */
data class WINRUser(
    /** Your app's unique user ID (Firebase UID, database ID, etc.) */
    val id: String,
    /** User's first name */
    val firstName: String,
    /** User's last name */
    val lastName: String,
    /** Optional phone number */
    val phone: String? = null,
    /**
     * The user's email from YOUR authenticated session (optional).
     *
     * When supplied, the WINR capture screen shows it pre-filled and READ-ONLY —
     * the user cannot swap in a different address. Deliberate: WINR links accounts
     * across devices by email, so a free-typed address lets a user attach
     * themselves to someone else's record, and a partner-authenticated address is
     * the most reliable destination for a winner notification.
     *
     * Supplying it never records any consent — the user still sees the address,
     * ticks the boxes, and submits inside the WINR flow. A malformed value is
     * ignored and the field stays editable.
     */
    val email: String? = null
)
