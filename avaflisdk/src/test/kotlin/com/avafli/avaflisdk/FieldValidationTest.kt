package com.avafli.avaflisdk

import com.avafli.avaflisdk.domain.AvafliFieldValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Master Field List validation rules behind the user-facing error
 * messages (AvafliV2Strings): email shape, claim-form names, and the optional
 * 10-digit US phone.
 */
class FieldValidationTest {

    // ── Email ──

    @Test
    fun `well-formed emails pass`() {
        assertTrue(AvafliFieldValidation.isValidEmail("ada@example.com"))
        assertTrue(AvafliFieldValidation.isValidEmail("a.b+tag@sub.domain.co"))
        assertTrue(AvafliFieldValidation.isValidEmail("  ada@example.com  ")) // trimmed
        assertTrue(AvafliFieldValidation.isValidEmail("a@b.co")) // minimum length 6
    }

    @Test
    fun `malformed emails fail`() {
        assertFalse(AvafliFieldValidation.isValidEmail(""))
        assertFalse(AvafliFieldValidation.isValidEmail("ada"))
        assertFalse(AvafliFieldValidation.isValidEmail("ada@example")) // no dotted domain
        assertFalse(AvafliFieldValidation.isValidEmail("ada@@example.com"))
        assertFalse(AvafliFieldValidation.isValidEmail("ada example@x.com")) // whitespace
        assertFalse(AvafliFieldValidation.isValidEmail("a@b.c")) // too short / 1-char TLD
        assertFalse(AvafliFieldValidation.isValidEmail("a".repeat(250) + "@x.com")) // > 254
    }

    // ── Names (letters incl. unicode / spaces / apostrophes / hyphens / periods, max 50) ──

    @Test
    fun `typical names pass`() {
        assertTrue(AvafliFieldValidation.isValidName("Ada"))
        assertTrue(AvafliFieldValidation.isValidName("Mary Jane"))
        assertTrue(AvafliFieldValidation.isValidName("O'Brien"))
        assertTrue(AvafliFieldValidation.isValidName("O’Brien")) // curly apostrophe
        assertTrue(AvafliFieldValidation.isValidName("Smith-Jones"))
        assertTrue(AvafliFieldValidation.isValidName("St. Clair"))
        assertTrue(AvafliFieldValidation.isValidName("José")) // unicode letters
        assertTrue(AvafliFieldValidation.isValidName("Zoë"))
        assertTrue(AvafliFieldValidation.isValidName("  Ada  ")) // trimmed
    }

    @Test
    fun `empty digits symbols and over-long names fail`() {
        assertFalse(AvafliFieldValidation.isValidName(""))
        assertFalse(AvafliFieldValidation.isValidName("   "))
        assertFalse(AvafliFieldValidation.isValidName("Ada123"))
        assertFalse(AvafliFieldValidation.isValidName("Ada_Lovelace"))
        assertFalse(AvafliFieldValidation.isValidName("Ada!"))
        assertFalse(AvafliFieldValidation.isValidName("a".repeat(51)))
        assertTrue(AvafliFieldValidation.isValidName("a".repeat(50))) // boundary
    }

    // ── Phone (optional; non-empty must reduce to 10 digits, leading 1 allowed) ──

    @Test
    fun `phone normalization strips formatting and a leading country code`() {
        assertEquals("5551234567", AvafliFieldValidation.normalizedPhoneOrNull("5551234567"))
        assertEquals("5551234567", AvafliFieldValidation.normalizedPhoneOrNull("(555) 123-4567"))
        assertEquals("5551234567", AvafliFieldValidation.normalizedPhoneOrNull("+1 555 123 4567"))
        assertEquals("5551234567", AvafliFieldValidation.normalizedPhoneOrNull("15551234567"))
    }

    @Test
    fun `phones that do not reduce to 10 digits are rejected`() {
        assertNull(AvafliFieldValidation.normalizedPhoneOrNull("555123456")) // 9 digits
        assertNull(AvafliFieldValidation.normalizedPhoneOrNull("55512345678")) // 11, no leading 1
        assertNull(AvafliFieldValidation.normalizedPhoneOrNull("155512345678")) // 12
        assertNull(AvafliFieldValidation.normalizedPhoneOrNull("phone"))
        assertNull(AvafliFieldValidation.normalizedPhoneOrNull(""))
    }

    @Test
    fun `blank phone passes the optional rule but invalid non-blank fails`() {
        assertTrue(AvafliFieldValidation.isValidOptionalPhone(""))
        assertTrue(AvafliFieldValidation.isValidOptionalPhone("   "))
        assertTrue(AvafliFieldValidation.isValidOptionalPhone("(555) 123-4567"))
        assertFalse(AvafliFieldValidation.isValidOptionalPhone("12345"))
    }
}
