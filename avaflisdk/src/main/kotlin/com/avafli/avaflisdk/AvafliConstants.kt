package com.avafli.avaflisdk

/**
 * SDK-level constants, mirroring the iOS SDK's `AvafliConstants`.
 */
internal object AvafliConstants {

    /**
     * Fallback Privacy Policy URL (iOS `AvafliConstants.privacyURL`).
     *
     * Every "Privacy Policy" affordance opens this; "Official Rules" opens the
     * configured `rulesUrl`. Before 2.9.2 the privacy links latently opened
     * `rulesUrl` — a cross-platform bug fixed on all SDKs.
     */
    const val PRIVACY_URL = "https://sdk.avafli.com/sdk/privacy"
}
