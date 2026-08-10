package com.avafli.winrsdk.domain

/**
 * Client-side field validation rules for the V2 experience (Master Field List).
 * Plain Kotlin — no view machinery — so every rule is unit-testable.
 *
 * These are SHAPE checks that drive user-facing error messages; the server
 * always revalidates.
 */
internal object WINRFieldValidation {

    /** Names: non-empty trimmed; unicode letters, spaces, apostrophes
     *  (straight or curly), hyphens, and periods; max 50 characters. */
    private val NAME_CHARS = setOf(' ', '\'', '’', '-', '.')

    fun isValidName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > 50) return false
        return trimmed.all { it.isLetter() || it in NAME_CHARS }
    }

    /**
     * US mobile number normalization: strip non-digits; a leading country
     * code 1 on an 11-digit number is dropped. Returns the bare 10-digit
     * number, or null when the input doesn't reduce to exactly 10 digits.
     */
    fun normalizedPhoneOrNull(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        val ten = if (digits.length == 11 && digits.startsWith("1")) digits.drop(1) else digits
        return ten.takeIf { it.length == 10 }
    }

    /** Claim-form phone is OPTIONAL: blank passes, non-blank must normalize. */
    fun isValidOptionalPhone(raw: String): Boolean =
        raw.isBlank() || normalizedPhoneOrNull(raw) != null

    /**
     * Email shape check (the server revalidates): one @, a dotted domain,
     * no whitespace, total length 6–254.
     */
    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

    fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        return trimmed.length in 6..254 && EMAIL_REGEX.matches(trimmed)
    }
}
