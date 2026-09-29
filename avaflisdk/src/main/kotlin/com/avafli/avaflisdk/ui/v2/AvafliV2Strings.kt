package com.avafli.avaflisdk.ui.v2

/**
 * Centralized user-facing copy for the V2 experience — Scott's "User Message
 * (UI)" column of the Master Field List.
 *
 * The module ships no strings.xml (inline Kotlin is the convention), so this
 * object is the single place every user-visible error/notice string lives.
 * UI and ViewModel code must reference these constants rather than repeating
 * literals; raw backend/AvafliError text is NEVER shown to users.
 */
internal object AvafliV2Strings {

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

    /** Adoption code screen subtitle — names the typed email. */
    fun adoptionSubtitle(email: String): String =
        "This email is already part of an Avafli streak. Enter the 6-digit " +
            "code we sent to $email to pick it up on this device."

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

    // ── Winner claim: email-ownership code (3.2.0) ──
    //
    // The six-digit code a winner enters before the claim form opens. Reuses
    // the code-entry screen; "Send a new code" and "Email verified ✓" are the
    // strings that screen and [EMAIL_VERIFIED] already carry.

    /** Code screen subtitle — names the MASKED address (the SDK never holds the raw one). */
    fun claimCodeSubtitle(maskedEmail: String?): String =
        maskedEmail?.takeIf { it.isNotBlank() }
            ?.let { "Enter the 6-digit code we sent to $it" }
            ?: "Enter the 6-digit code we sent to your email."

    /** Inline status while the send call is in flight. */
    const val CLAIM_CODE_SENDING = "Sending your code…"

    /** Inline status when this open (or "Send a new code") put a code in the inbox. */
    const val CLAIM_CODE_SENT = "Code sent"

    /** The resend action while it is cooling down — a live countdown. */
    fun claimCodeResendCountdown(secondsLeft: Int): String {
        val s = secondsLeft.coerceAtLeast(0)
        return "Send a new code in ${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** Where the help line's mailto link goes. */
    const val SUPPORT_EMAIL = "info@avafli.com"

    /** Help line under the code screen's actions; [SUPPORT_EMAIL] is the link. */
    const val CLAIM_CODE_HELP_PREFIX = "Can't get to this email? Contact "

    /** Wrong code. Falls back to [CODE_MISMATCH] when the backend sent no count. */
    fun claimCodeMismatch(attemptsRemaining: Int?): String = when {
        attemptsRemaining == null || attemptsRemaining < 0 -> CODE_MISMATCH
        attemptsRemaining == 1 -> "That code didn't match. 1 try left."
        else -> "That code didn't match. $attemptsRemaining tries left."
    }

    /** Transport failure on the code screen (send or check) — what they typed is kept. */
    const val CLAIM_CODE_NETWORK =
        "We couldn't reach the server. Check your connection and try again."

    /** The code could not be sent (fallback for the backend's own wording). */
    const val CLAIM_CODE_SEND_FAILED =
        "We couldn't send your code just now. Please try again in a minute."

    /** "Send a new code" tapped inside the 60-second cooldown. */
    const val CLAIM_CODE_COOLDOWN = "Please wait a moment before requesting another code."

    /** Hourly send limit reached (fallback for the backend's own wording). */
    const val CLAIM_CODE_SEND_LIMIT =
        "You've requested several codes. Please try again in a little while, " +
            "or contact info@avafli.com."

    /** The backend replaced a dead code (fallback for its own wording) — a notice, not an error. */
    const val CLAIM_CODE_FRESH_SENT = "We sent you a new code. Check your email."

    /** The account has no address to send to (fallback for the backend's own wording). */
    const val CLAIM_NO_EMAIL_ON_FILE =
        "We don't have an email on file for this account. Contact info@avafli.com " +
            "to claim your prize."

    /** The typed value was not a code the backend could check. */
    const val CLAIM_CODE_INVALID = "Enter the 6-digit code from the email we sent."

    /** Anything else went wrong checking the code — what they typed is kept. */
    const val CLAIM_CODE_FAILED = "Something went wrong. Please try again."

    /** Retry action beside an inline send failure. */
    const val CLAIM_CODE_RETRY = "Try again"

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

    // ── Offline (opened for a pending claim, status fetch failed) ──

    const val OFFLINE_HEADLINE = "We couldn't reach the server."
    const val OFFLINE_BODY = "Check your connection and try again."

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
        "This permanently erases your information and ends your participation. " +
            "Entries and streaks are forfeited and cannot be restored. " +
            "You can join again as a new participant after 24 hours."
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
