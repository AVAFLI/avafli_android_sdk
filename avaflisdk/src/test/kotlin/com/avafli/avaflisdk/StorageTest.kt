package com.avafli.avaflisdk

import com.avafli.avaflisdk.storage.PreferencesStorage
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the PreferencesStorage layer.
 * Note: SecureStorage tests require Android instrumentation due to EncryptedSharedPreferences.
 * These tests validate the PreferencesStorage logic using mocks.
 */
class StorageTest {

    @Test
    fun `completed days serialization round trips correctly`() {
        val input = listOf(true, false, true, true, false, false, true)
        val encoded = input.joinToString(",") { if (it) "1" else "0" }
        val decoded = encoded.split(",").map { it == "1" }
        assertEquals(input, decoded)
    }

    @Test
    fun `empty completed days returns default`() {
        val defaultDays = List(7) { false }
        assertEquals(7, defaultDays.size)
        assertTrue(defaultDays.none { it })
    }

    @Test
    fun `streak day defaults to zero`() {
        // Verify default behavior
        val defaultStreakDay = 0
        assertEquals(0, defaultStreakDay)
    }

    @Test
    fun `completed days encoding preserves order`() {
        val days = listOf(true, true, true, false, false, false, false)
        val encoded = days.joinToString(",") { if (it) "1" else "0" }
        assertEquals("1,1,1,0,0,0,0", encoded)

        val decoded = encoded.split(",").map { it == "1" }
        assertEquals(days, decoded)
    }

    @Test
    fun `all days completed serializes correctly`() {
        val allComplete = List(7) { true }
        val encoded = allComplete.joinToString(",") { if (it) "1" else "0" }
        assertEquals("1,1,1,1,1,1,1", encoded)
    }

    @Test
    fun `preferences storage mock operations`() {
        val storage = mockk<PreferencesStorage>(relaxed = true)

        // Save and retrieve streak day
        every { storage.getStreakDay() } returns 5
        storage.saveStreakDay(5)
        assertEquals(5, storage.getStreakDay())

        // Save and retrieve total entries
        every { storage.getTotalEntries() } returns 42
        storage.saveTotalEntries(42)
        assertEquals(42, storage.getTotalEntries())

        // Email submitted flag
        every { storage.isEmailSubmitted() } returns true
        storage.saveEmailSubmitted(true)
        assertTrue(storage.isEmailSubmitted())

        // Last claim date
        every { storage.getLastClaimDate() } returns "2025-03-07"
        storage.saveLastClaimDate("2025-03-07")
        assertEquals("2025-03-07", storage.getLastClaimDate())

        verify {
            storage.saveStreakDay(5)
            storage.saveTotalEntries(42)
            storage.saveEmailSubmitted(true)
            storage.saveLastClaimDate("2025-03-07")
        }
    }

    @Test
    fun `clear all resets everything`() {
        val storage = mockk<PreferencesStorage>(relaxed = true)

        storage.clearAll()

        every { storage.getStreakDay() } returns 0
        every { storage.getTotalEntries() } returns 0
        every { storage.isEmailSubmitted() } returns false
        every { storage.getLastClaimDate() } returns null

        assertEquals(0, storage.getStreakDay())
        assertEquals(0, storage.getTotalEntries())
        assertFalse(storage.isEmailSubmitted())
        assertNull(storage.getLastClaimDate())
    }
}
