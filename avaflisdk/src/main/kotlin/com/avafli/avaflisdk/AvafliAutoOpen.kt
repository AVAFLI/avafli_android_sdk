package com.avafli.avaflisdk

/**
 * Controls WHEN the SDK auto-opens the experience drawer after [Avafli.configure].
 *
 * Set on [AvafliConfiguration.autoOpen]. The default ([ALWAYS]) is today's
 * behavior — nothing changes for existing integrations. Device registration
 * (DAU/MAU tracking, sdk_version, platform) runs inside `configure()` in every
 * mode; only the presentation timing is affected.
 *
 * The server can further restrict the mode (`sdkConfig.experience.autoOpenMode`
 * and the `autoOpenEnabled` kill switch); the effective mode is the most
 * restrictive of the server and client settings ([NEVER] > [RETURNING_USERS_ONLY]
 * > [ALWAYS]).
 */
enum class AvafliAutoOpen {
    /** Auto-open once per calendar day when eligible (today's behavior). */
    ALWAYS,

    /**
     * Skip the auto-open for the session in which this device registered for the
     * first time (the backend reported `isNewUser`), so a first-run onboarding is
     * never interrupted. Every later launch auto-opens as normal. When the backend
     * does not report `isNewUser` (older backend) the device is treated as returning.
     */
    RETURNING_USERS_ONLY,

    /** The SDK never auto-opens; the publisher calls [Avafli.present]. */
    NEVER;

    internal companion object {
        /**
         * Parses the server's `experience.autoOpenMode` string. Unknown or absent
         * values → null, which the eligibility gate treats as [ALWAYS].
         */
        internal fun fromWire(raw: String?): AvafliAutoOpen? = when (raw) {
            "always" -> ALWAYS
            "returningUsersOnly" -> RETURNING_USERS_ONLY
            "never" -> NEVER
            else -> null
        }

        /**
         * Effective mode = most restrictive of (server kill switch, server mode,
         * client mode), where NEVER > RETURNING_USERS_ONLY > ALWAYS. The server
         * kill switch (`autoOpenEnabled == false`) is NEVER.
         */
        internal fun effective(
            serverAutoOpenEnabled: Boolean?,
            serverMode: AvafliAutoOpen?,
            clientMode: AvafliAutoOpen,
        ): AvafliAutoOpen {
            if (serverAutoOpenEnabled == false) return NEVER
            val server = serverMode ?: ALWAYS
            // Enum ordinal order IS the restrictiveness order.
            return if (server.ordinal >= clientMode.ordinal) server else clientMode
        }
    }
}
