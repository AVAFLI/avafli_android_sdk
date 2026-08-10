package com.avafli.winrsdk.ui.v2

/**
 * Centralized user-facing copy for the V2 experience — Scott's "User Message
 * (UI)" column of the Master Field List.
 *
 * The module ships no strings.xml (inline Kotlin is the convention), so this
 * object is the single place every user-visible error/notice string lives.
 * UI and ViewModel code must reference these constants rather than repeating
 * literals; raw backend/WINRError text is NEVER shown to users.
 */
internal object V2Strings {

    // ── Email capture ──

    /** Inline error under the email field (shown after touch or submit attempt). */
    const val INVALID_EMAIL = "Please enter a valid email address."

    /** The submit itself failed in transport — the user stays on capture and retries. */
    const val EMAIL_SUBMIT_FAILED = "Something went wrong sending your email. Please try again."

    /** OTP adoption code rejected. */
    const val CODE_MISMATCH = "That code didn't match. Check the email and try again."

    // ── Winner claim form ──

    const val INVALID_FIRST_NAME = "Please enter a valid first name."
    const val INVALID_LAST_NAME = "Please enter a valid last name."

    /** Claim-form phone stays OPTIONAL; shown only for a non-empty invalid value. */
    const val INVALID_PHONE = "Please enter a valid 10-digit mobile number."

    /** Transport-level prize-claim submit failure, inline on the review screen. */
    const val CLAIM_SUBMIT_FAILED = "Something went wrong. Please check your connection and try again."

    // ── Dashboard notices ──

    /** Transient notice when the backend rejects a claim as already-claimed
     *  and local state didn't already know (raced another device/open). */
    const val ALREADY_ENTERED_TODAY =
        "You've already entered today. Come back tomorrow to keep your streak going!"

    /** Auto-claim transport failure — dashboard rests UNCLAIMED, never fakes success. */
    const val ENTRY_NOT_RECORDED =
        "We couldn't record today's entry. Check your connection and try again."

    /** Retry affordance on the [ENTRY_NOT_RECORDED] notice. */
    const val TRY_AGAIN = "TRY AGAIN"

    // ── Geo-blocked (backend geo-fence: US-only promotion) ──

    const val GEO_BLOCKED_HEADLINE = "Not available in your location"
    const val GEO_BLOCKED_BODY =
        "This promotion is only available to users located in the United States. " +
            "Please check your location settings or try again from an eligible location."

    // ── Session expired ──

    const val SESSION_EXPIRED = "Your session has expired. Please try again."
    const val RETRY = "RETRY"

    // ── Quiet empty state (no giveaway / unrecognized errors) ──

    const val EMPTY_HEADLINE = "Nothing to see here yet"
    const val EMPTY_BODY = "Check back soon for your next chance to win!"
    const val CLOSE = "CLOSE"
}
