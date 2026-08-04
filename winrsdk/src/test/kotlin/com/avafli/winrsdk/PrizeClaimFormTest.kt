package com.avafli.winrsdk

import com.avafli.winrsdk.domain.PrizeClaimForm
import com.avafli.winrsdk.domain.WINRClaimDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PrizeClaimFormTest {

    private val validForm = PrizeClaimForm(
        firstName = "Ada",
        lastName = "Lovelace",
        street = "1 Analytical Way",
        city = "Brooklyn",
        state = "New York",
        zip = "11201",
        confirmsAccuracy = true,
        authorizesLikeness = true,
        agreesToRules = true,
    )

    // ── Validation ──

    @Test
    fun `valid form with only required fields passes`() {
        assertTrue(validForm.isValid)
    }

    @Test
    fun `all three consents are required`() {
        assertFalse(validForm.copy(confirmsAccuracy = false).isValid)
        assertFalse(validForm.copy(authorizesLikeness = false).isValid)
        assertFalse(validForm.copy(agreesToRules = false).isValid)
        assertFalse(
            PrizeClaimForm(
                firstName = "Ada", lastName = "Lovelace", street = "1 Analytical Way",
                city = "Brooklyn", state = "New York", zip = "11201",
            ).isValid
        )
    }

    @Test
    fun `consents default to off`() {
        val fresh = PrizeClaimForm()
        assertFalse(fresh.confirmsAccuracy)
        assertFalse(fresh.authorizesLikeness)
        assertFalse(fresh.agreesToRules)
    }

    @Test
    fun `phone apartment and photo are optional`() {
        assertTrue(validForm.copy(phone = "", apt = "", photoBase64 = null).isValid)
        assertTrue(validForm.copy(phone = "5551234567", apt = "4B", photoBase64 = "abc").isValid)
    }

    @Test
    fun `each required field empty or blank fails`() {
        assertFalse(validForm.copy(firstName = "").isValid)
        assertFalse(validForm.copy(firstName = "   ").isValid)
        assertFalse(validForm.copy(lastName = "").isValid)
        assertFalse(validForm.copy(street = " ").isValid)
        assertFalse(validForm.copy(city = "").isValid)
        assertFalse(validForm.copy(state = "").isValid)
        assertFalse(validForm.copy(zip = "").isValid)
    }

    @Test
    fun `zip must be exactly five digits`() {
        assertTrue(PrizeClaimForm.isValidZip("11201"))
        assertTrue(PrizeClaimForm.isValidZip(" 11201 "))
        assertFalse(PrizeClaimForm.isValidZip("1120"))
        assertFalse(PrizeClaimForm.isValidZip("112011"))
        assertFalse(PrizeClaimForm.isValidZip("1120a"))
        assertFalse(PrizeClaimForm.isValidZip(""))
    }

    @Test
    fun `country is fixed to United States`() {
        assertEquals("United States", validForm.country)
    }

    // ── Display name ("First L.") ──

    @Test
    fun `display name is first name plus last initial`() {
        assertEquals("Ada L.", validForm.displayName)
    }

    @Test
    fun `display name trims whitespace`() {
        assertEquals("Ada L.", validForm.copy(firstName = " Ada ", lastName = " Lovelace ").displayName)
    }

    @Test
    fun `display name with empty last name omits initial`() {
        assertEquals("Ada", validForm.copy(lastName = "").displayName)
    }

    @Test
    fun `us state list covers all fifty states`() {
        assertEquals(50, PrizeClaimForm.usStates.size)
        assertEquals("Alabama", PrizeClaimForm.usStates.first())
        assertEquals("Wyoming", PrizeClaimForm.usStates.last())
    }

    // ── Winner-card award line dates ──

    @Test
    fun `month year display from ISO date-time with fractional seconds`() {
        assertEquals("AUGUST, 2026", WINRClaimDates.monthYearDisplay("2026-08-04T18:30:00.123Z"))
    }

    @Test
    fun `month year display from plain ISO date-time`() {
        assertEquals("AUGUST, 2026", WINRClaimDates.monthYearDisplay("2026-08-04T18:30:00Z"))
    }

    @Test
    fun `month year display from day-only date`() {
        assertEquals("JANUARY, 2027", WINRClaimDates.monthYearDisplay("2027-01-15"))
    }

    @Test
    fun `month year display falls back to now for garbage or null`() {
        val now = LocalDate.of(2026, 8, 4)
        assertEquals("AUGUST, 2026", WINRClaimDates.monthYearDisplay(null, now))
        assertEquals("AUGUST, 2026", WINRClaimDates.monthYearDisplay("not-a-date", now))
    }
}
