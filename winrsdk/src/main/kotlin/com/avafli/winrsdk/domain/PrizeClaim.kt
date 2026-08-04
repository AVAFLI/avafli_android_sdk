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
    /**
     * Explicit consents (Joe's "review and agree" checkboxes). All three are
     * REQUIRED — the likeness release in particular must be an affirmative
     * user action for the publicity authorization to hold up.
     */
    val confirmsAccuracy: Boolean = false,
    val authorizesLikeness: Boolean = false,
    val agreesToRules: Boolean = false,
) {
    /** Fixed — US-only sweepstakes. */
    val country: String get() = "United States"

    /**
     * SUBMIT enables when every required field is present, the zip is a
     * 5-digit US code, and all three consents are affirmed. Phone, apartment,
     * and photo are optional.
     */
    val isValid: Boolean
        get() = firstName.trim().isNotEmpty() &&
            lastName.trim().isNotEmpty() &&
            street.trim().isNotEmpty() &&
            city.trim().isNotEmpty() &&
            state.trim().isNotEmpty() &&
            isValidZip(zip) &&
            confirmsAccuracy &&
            authorizesLikeness &&
            agreesToRules

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
