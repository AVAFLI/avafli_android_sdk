package com.avafli.avaflisdk

/**
 * Represents user information for the Avafli SDK.
 *
 * Only `id` is required; everything else is optional and the SDK captures what's
 * missing (email via its capture screen, name via the winner prize-claim form).
 * Consent is always handled inside the SDK's capture flow — publishers never set it.
 */
data class AvafliUser(
    /** Your app's unique user ID (Firebase UID, database ID, etc.) */
    val id: String,
    /** User's first name (optional — the SDK collects it at prize-claim if missing) */
    val firstName: String = "",
    /** User's last name (optional — the SDK collects it at prize-claim if missing) */
    val lastName: String = "",
    /** Optional phone number */
    val phone: String? = null,
    /**
     * The user's email from YOUR authenticated session (optional).
     *
     * When supplied, the Avafli capture screen shows it pre-filled and READ-ONLY —
     * the user cannot swap in a different address. Deliberate: Avafli links accounts
     * across devices by email, so a free-typed address lets a user attach
     * themselves to someone else's record, and a partner-authenticated address is
     * the most reliable destination for a winner notification.
     *
     * Supplying it never records any consent — the user still sees the address,
     * ticks the boxes, and submits inside the Avafli flow. A malformed value is
     * ignored and the field stays editable.
     */
    val email: String? = null
) {
    /** True when this value is the [guest] sentinel. */
    internal val isGuest: Boolean get() = id.isEmpty()

    companion object {
        /**
         * A guest session — the person is not signed in to YOUR app (or your
         * app has no accounts). The SDK mints a stable per-install guest id
         * (`avafli_guest_…`) and uses it for attribution, so there is always a
         * real identifier without your integration fabricating one. The Avafli
         * experience is fully functional for guests; when your user signs in,
         * call configure again with the real user and attribution upgrades in
         * place — the streak is device-anchored and unaffected.
         */
        @JvmField
        val GUEST = AvafliUser(id = "", firstName = "", lastName = "")
    }
}
