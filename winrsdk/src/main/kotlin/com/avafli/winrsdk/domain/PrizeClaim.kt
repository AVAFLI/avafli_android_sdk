package com.avafli.winrsdk.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * FIXED API contract, mirroring `PrizeClaimBlock` in the backend's types.ts
 * (and the iOS `PrizeClaimBlock` in WINRAPI.swift).
 *
 * Present on registerDevice / getActiveGiveaway responses only when this person
 * is the drawn winner of one of this publisher's giveaways.
 * `status == "pending"` drives the winner splash → claim form flow;
 * `"submitted"` means the form was already sent (normal dashboard shows).
 */
@Serializable
data class PrizeClaimBlock(
    /** "pending" | "submitted" */
    val status: String,
    val giveawayId: String,
    val prizeDescription: String = "",
    val prizeValue: Double = 0.0,
    /**
     * Display-only masked winning email ("d********r@winr.example.com") for
     * the claim form's locked field. Absent from older backends.
     */
    val maskedEmail: String? = null,
    /** Present when submitted. */
    val claimNumber: String? = null,
    /** ISO date, when submitted. */
    val submittedAt: String? = null,
) {
    val isPending: Boolean get() = status == "pending"
}

/**
 * The claim form's field values. Kept as a plain value type so validation is
 * unit-testable without any view machinery (mirrors iOS WINRPrizeClaimForm).
 */
internal data class PrizeClaimForm(
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val street: String = "",
    val apt: String = "",
    val city: String = "",
    val state: String = "",
    val zip: String = "",
    /** JPEG base64 of the optional attached photo (already downscaled/capped). */
    val photoBase64: String? = null,
    /** Optional "please share a little" story (step 4). Sent trimmed; omitted when empty. */
    val story: String = "",
    /**
     * Explicit consents (Joe's "review and agree" checkboxes). All three are
     * REQUIRED for submit, and PRE-CHECKED by default (CTO decision, Aug 2026)
     * — the user can still untick any of them on the review screen, which
     * disables SUBMIT until re-affirmed.
     */
    val confirmsAccuracy: Boolean = true,
    val authorizesLikeness: Boolean = true,
    val agreesToRules: Boolean = true,
) {
    /** Fixed — US-only sweepstakes. */
    val country: String get() = "United States"

    // ── Per-step validity (stepped flow) ──

    /**
     * Step 1 "TELL US ABOUT YOURSELF": first + last name (email is
     * server-side). Names follow the Master Field List rule (unicode letters/
     * spaces/apostrophes/hyphens/periods, max 50). Phone stays OPTIONAL, but a
     * non-empty value must reduce to a valid 10-digit US number — an invalid
     * one blocks CONTINUE (and submit) with an inline message.
     */
    val isStep1Valid: Boolean
        get() = WINRFieldValidation.isValidName(firstName) &&
            WINRFieldValidation.isValidName(lastName) &&
            WINRFieldValidation.isValidOptionalPhone(phone)

    /** Step 2 "WHERE SHOULD WE SEND YOUR PRIZE?": full US shipping address. */
    val isStep2Valid: Boolean
        get() = street.trim().isNotEmpty() &&
            city.trim().isNotEmpty() &&
            state.trim().isNotEmpty() &&
            isValidZip(zip)

    // Steps 3 (photo) and 4 (story) are fully optional — always advanceable.

    /** All three review-screen consents affirmed. */
    val hasAllConsents: Boolean
        get() = confirmsAccuracy && authorizesLikeness && agreesToRules

    /**
     * SUBMIT enables when every required field across the steps is present,
     * the zip is a 5-digit US code, and all three consents are affirmed.
     * Phone, apartment, photo, and story are optional.
     */
    val isValid: Boolean
        get() = isStep1Valid && isStep2Valid && hasAllConsents

    /** "First L." — the public display name on the winner card. */
    val displayName: String
        get() {
            val first = firstName.trim()
            val lastInitial = lastName.trim().firstOrNull()?.let { " $it." } ?: ""
            return "$first$lastInitial"
        }

    companion object {
        fun isValidZip(zip: String): Boolean {
            val z = zip.trim()
            return z.length == 5 && z.all { it.isDigit() }
        }

        val usStates = listOf(
            "Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado",
            "Connecticut", "Delaware", "Florida", "Georgia", "Hawaii", "Idaho",
            "Illinois", "Indiana", "Iowa", "Kansas", "Kentucky", "Louisiana",
            "Maine", "Maryland", "Massachusetts", "Michigan", "Minnesota",
            "Mississippi", "Missouri", "Montana", "Nebraska", "Nevada",
            "New Hampshire", "New Jersey", "New Mexico", "New York",
            "North Carolina", "North Dakota", "Ohio", "Oklahoma", "Oregon",
            "Pennsylvania", "Rhode Island", "South Carolina", "South Dakota",
            "Tennessee", "Texas", "Utah", "Vermont", "Virginia", "Washington",
            "West Virginia", "Wisconsin", "Wyoming",
        )
    }
}

/** Date display for the winner card (mirrors iOS WINRClaimDates). */
internal object WINRClaimDates {

    /**
     * "AUGUST, 2026" from an ISO date string (falls back to [now]) — the winner
     * card's award line.
     */
    fun monthYearDisplay(iso: String?, now: LocalDate = LocalDate.now()): String {
        val date = iso?.let { parseIso(it) } ?: now
        return date
            .format(DateTimeFormatter.ofPattern("MMMM, yyyy", Locale.US))
            .uppercase(Locale.US)
    }

    private fun parseIso(value: String): LocalDate? =
        // Full ISO date-time with offset (with or without fractional seconds)…
        runCatching { OffsetDateTime.parse(value).toLocalDate() }.getOrNull()
            // …or a plain "yyyy-MM-dd" day.
            ?: runCatching { LocalDate.parse(value) }.getOrNull()
}
