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
    )

    // ── Validation ──

    @Test
    fun `valid form with only required fields passes`() {
        assertTrue(validForm.isValid)
    }

    @Test
    fun `likeness promo consent is optional and never gates submit`() {
        // 2.9 (14 Aug): the single remaining review checkbox is OPTIONAL —
        // submit is valid with it on OR off.
        assertTrue(validForm.copy(authorizesLikeness = false).isValid)
        assertTrue(validForm.copy(authorizesLikeness = true).isValid)
    }

    @Test
    fun `likeness promo consent defaults to unchecked`() {
        // Consent must be an affirmative act — no pre-ticked boxes (2.9).
        assertFalse(PrizeClaimForm().authorizesLikeness)
    }

    @Test
    fun `phone apartment photo and story are optional`() {
        assertTrue(validForm.copy(phone = "", apt = "", photoBase64 = null, story = "").isValid)
        assertTrue(
            validForm.copy(
                phone = "5551234567", apt = "4B", photoBase64 = "abc", story = "So excited!",
            ).isValid
        )
    }

    // ── Per-step validity (stepped flow) ──

    @Test
    fun `step 1 requires first and last name`() {
        assertTrue(PrizeClaimForm(firstName = "Ada", lastName = "Lovelace").isStep1Valid)
        assertFalse(PrizeClaimForm(firstName = "Ada").isStep1Valid)
        assertFalse(PrizeClaimForm(lastName = "Lovelace").isStep1Valid)
        assertFalse(PrizeClaimForm(firstName = "  ", lastName = "Lovelace").isStep1Valid)
    }

    @Test
    fun `step 1 names follow the Master Field List character rule`() {
        assertTrue(PrizeClaimForm(firstName = "Mary Jane", lastName = "O'Brien-St. Clair").isStep1Valid)
        assertTrue(PrizeClaimForm(firstName = "José", lastName = "Muñoz").isStep1Valid)
        assertFalse(PrizeClaimForm(firstName = "Ada123", lastName = "Lovelace").isStep1Valid)
        assertFalse(PrizeClaimForm(firstName = "Ada", lastName = "L0velace!").isStep1Valid)
        assertFalse(PrizeClaimForm(firstName = "a".repeat(51), lastName = "Lovelace").isStep1Valid)
    }

    @Test
    fun `step 1 phone stays optional but a non-empty invalid one blocks continue`() {
        val base = PrizeClaimForm(firstName = "Ada", lastName = "Lovelace")
        assertTrue(base.isStep1Valid) // blank phone: fine
        assertTrue(base.copy(phone = "(555) 123-4567").isStep1Valid)
        assertTrue(base.copy(phone = "+1 555 123 4567").isStep1Valid)
        assertFalse(base.copy(phone = "12345").isStep1Valid)
        assertFalse(base.copy(phone = "55512345678").isStep1Valid)
        // ...and an invalid phone blocks overall submit too.
        assertFalse(validForm.copy(phone = "12345").isValid)
    }

    @Test
    fun `step 2 requires the full US shipping address`() {
        assertTrue(validForm.isStep2Valid)
        assertFalse(validForm.copy(street = " ").isStep2Valid)
        assertFalse(validForm.copy(city = "").isStep2Valid)
        assertFalse(validForm.copy(state = "").isStep2Valid)
        assertFalse(validForm.copy(zip = "1120").isStep2Valid)
        // Apartment stays optional at the step level too.
        assertTrue(validForm.copy(apt = "").isStep2Valid)
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
    fun `us state list covers all fifty states plus DC`() {
        // The official rules promise "50 states and the District of Columbia".
        assertEquals(51, PrizeClaimForm.usStates.size)
        assertEquals("Alabama", PrizeClaimForm.usStates.first())
        assertEquals("Wyoming", PrizeClaimForm.usStates.last())
        assertTrue(PrizeClaimForm.usStates.contains("District of Columbia"))
        // Alphabetical placement: between Delaware and Florida.
        assertEquals(PrizeClaimForm.usStates.sorted(), PrizeClaimForm.usStates)
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
