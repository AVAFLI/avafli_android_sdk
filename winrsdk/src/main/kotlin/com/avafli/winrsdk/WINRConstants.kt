package com.avafli.winrsdk

/**
 * SDK-level constants, mirroring the iOS SDK's `WINRConstants`.
 */
internal object WINRConstants {

    /**
     * Fallback Privacy Policy URL (iOS `WINRConstants.privacyURL`).
     *
     * Every "Privacy Policy" affordance opens this; "Official Rules" opens the
     * configured `rulesUrl`. Before 2.9.2 the privacy links latently opened
     * `rulesUrl` — a cross-platform bug fixed on all SDKs.
     */
    const val PRIVACY_URL = "https://winrmedia.com/sdk/privacy"
}
