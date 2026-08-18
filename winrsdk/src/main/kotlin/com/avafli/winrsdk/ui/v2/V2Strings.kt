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

    /** OTP adoption code rejected — the code was wrong (default of the taxonomy). */
    const val CODE_MISMATCH = "That code didn't match. Check the email and try again."

    /** OTP adoption code rejected — the code had expired. */
    const val CODE_EXPIRED = "That code expired. Tap 'Send a new code' to get a fresh one."

    /** OTP adoption code rejected — too many wrong attempts. */
    const val CODE_TOO_MANY_ATTEMPTS = "Too many attempts. Request a new code."

    /** A "Send a new code" (resend) request failed in transport — shown in the
     *  code-error slot; the code screen stays up. */
    const val CODE_RESEND_FAILED = "Couldn't send a new code. Check your connection and try again."

    /** Adoption re-entry (2.9): subtitle on the code screen when a parked
     *  adoption resumes on a later open (the raw email is unknown here). */
    const val ADOPTION_RESTAGE_SUBTITLE =
        "Pick up where you left off! We just sent a fresh 6-digit code to your " +
            "email — enter it to finish connecting your streak."

    // ── Soft email verification (2.7.0) ──

    /** Persistent, non-blocking dashboard chip shown while the typed email is
     *  unverified. Tappable — opens the reused 6-digit code screen. */
    const val VERIFY_EMAIL_CHIP = "Verify your email"

    /** Header on the email-verification code screen (reuses the adoption screen). */
    const val VERIFY_EMAIL_TITLE = "VERIFY YOUR EMAIL"

    /** Subtitle on the email-verification code screen. */
    const val VERIFY_EMAIL_SUBTITLE =
        "Enter the 6-digit code we sent to your inbox so you're eligible to win."

    /** Back/dismiss control on the email-verification code screen — this flow is
     *  dismissible and gates nothing. */
    const val VERIFY_EMAIL_CANCEL = "Cancel"

    /** Transient dashboard confirmation after a successful verify — reuses the
     *  dashboard notice mechanism, then the chip disappears. */
    const val EMAIL_VERIFIED = "Email verified ✓"

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

    // ── Legal webview (2.9.4) ──

    /** Official Rules link label — also the in-app legal webview's title. */
    const val OFFICIAL_RULES_LINK = "Official Rules"

    /** Privacy Policy link label — also the in-app legal webview's title. */
    const val PRIVACY_POLICY_LINK = "Privacy Policy"

    /** The legal webview's main frame failed to load — shown with a RETRY pill. */
    const val LEGAL_LOAD_FAILED =
        "Couldn't load this page. Check your connection and try again."

    // ── RTD opt-out (reached via the privacy webview's delete section, whose
    //    winr://delete bridge raises this confirmation) ──

    const val OPT_OUT_TITLE = "Delete my data & stop participating"
    const val OPT_OUT_BODY =
        "This permanently deletes your WINR data, ends your giveaway participation, " +
            "and cannot be undone. You can also email info@avafli.com."
    const val OPT_OUT_CONFIRM = "DELETE MY DATA"
    const val OPT_OUT_CANCEL = "Cancel"

    /** Brief success state shown before the experience dismisses itself. */
    const val OPT_OUT_SUCCESS = "Your data has been deleted."

    /** The opt-out call failed — the confirmation stays up and can retry.
     *  We never pretend the deletion succeeded. */
    const val OPT_OUT_FAILED = "Something went wrong. Please check your connection and try again."

    // ── Quiet empty state (no giveaway / unrecognized errors) ──

    const val EMPTY_HEADLINE = "Nothing to see here yet"
    const val EMPTY_BODY = "Check back soon for your next chance to win!"
    const val CLOSE = "CLOSE"
}
