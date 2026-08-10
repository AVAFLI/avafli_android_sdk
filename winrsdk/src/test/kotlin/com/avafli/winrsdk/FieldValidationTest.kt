package com.avafli.winrsdk

import com.avafli.winrsdk.domain.WINRFieldValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Master Field List validation rules behind the user-facing error
 * messages (V2Strings): email shape, claim-form names, and the optional
 * 10-digit US phone.
 */
class FieldValidationTest {

    // ── Email ──

    @Test
    fun `well-formed emails pass`() {
        assertTrue(WINRFieldValidation.isValidEmail("ada@example.com"))
        assertTrue(WINRFieldValidation.isValidEmail("a.b+tag@sub.domain.co"))
        assertTrue(WINRFieldValidation.isValidEmail("  ada@example.com  ")) // trimmed
        assertTrue(WINRFieldValidation.isValidEmail("a@b.co")) // minimum length 6
    }

    @Test
    fun `malformed emails fail`() {
        assertFalse(WINRFieldValidation.isValidEmail(""))
        assertFalse(WINRFieldValidation.isValidEmail("ada"))
        assertFalse(WINRFieldValidation.isValidEmail("ada@example")) // no dotted domain
        assertFalse(WINRFieldValidation.isValidEmail("ada@@example.com"))
        assertFalse(WINRFieldValidation.isValidEmail("ada example@x.com")) // whitespace
        assertFalse(WINRFieldValidation.isValidEmail("a@b.c")) // too short / 1-char TLD
        assertFalse(WINRFieldValidation.isValidEmail("a".repeat(250) + "@x.com")) // > 254
    }

    // ── Names (letters incl. unicode / spaces / apostrophes / hyphens / periods, max 50) ──

    @Test
    fun `typical names pass`() {
        assertTrue(WINRFieldValidation.isValidName("Ada"))
        assertTrue(WINRFieldValidation.isValidName("Mary Jane"))
        assertTrue(WINRFieldValidation.isValidName("O'Brien"))
        assertTrue(WINRFieldValidation.isValidName("O’Brien")) // curly apostrophe
        assertTrue(WINRFieldValidation.isValidName("Smith-Jones"))
        assertTrue(WINRFieldValidation.isValidName("St. Clair"))
        assertTrue(WINRFieldValidation.isValidName("José")) // unicode letters
        assertTrue(WINRFieldValidation.isValidName("Zoë"))
        assertTrue(WINRFieldValidation.isValidName("  Ada  ")) // trimmed
    }

    @Test
    fun `empty digits symbols and over-long names fail`() {
        assertFalse(WINRFieldValidation.isValidName(""))
        assertFalse(WINRFieldValidation.isValidName("   "))
        assertFalse(WINRFieldValidation.isValidName("Ada123"))
        assertFalse(WINRFieldValidation.isValidName("Ada_Lovelace"))
        assertFalse(WINRFieldValidation.isValidName("Ada!"))
        assertFalse(WINRFieldValidation.isValidName("a".repeat(51)))
        assertTrue(WINRFieldValidation.isValidName("a".repeat(50))) // boundary
    }

    // ── Phone (optional; non-empty must reduce to 10 digits, leading 1 allowed) ──

    @Test
    fun `phone normalization strips formatting and a leading country code`() {
        assertEquals("5551234567", WINRFieldValidation.normalizedPhoneOrNull("5551234567"))
        assertEquals("5551234567", WINRFieldValidation.normalizedPhoneOrNull("(555) 123-4567"))
        assertEquals("5551234567", WINRFieldValidation.normalizedPhoneOrNull("+1 555 123 4567"))
        assertEquals("5551234567", WINRFieldValidation.normalizedPhoneOrNull("15551234567"))
    }

    @Test
    fun `phones that do not reduce to 10 digits are rejected`() {
        assertNull(WINRFieldValidation.normalizedPhoneOrNull("555123456")) // 9 digits
        assertNull(WINRFieldValidation.normalizedPhoneOrNull("55512345678")) // 11, no leading 1
        assertNull(WINRFieldValidation.normalizedPhoneOrNull("155512345678")) // 12
        assertNull(WINRFieldValidation.normalizedPhoneOrNull("phone"))
        assertNull(WINRFieldValidation.normalizedPhoneOrNull(""))
    }

    @Test
    fun `blank phone passes the optional rule but invalid non-blank fails`() {
        assertTrue(WINRFieldValidation.isValidOptionalPhone(""))
        assertTrue(WINRFieldValidation.isValidOptionalPhone("   "))
        assertTrue(WINRFieldValidation.isValidOptionalPhone("(555) 123-4567"))
        assertFalse(WINRFieldValidation.isValidOptionalPhone("12345"))
    }
}
