package com.avafli.avaflisdk

import com.avafli.avaflisdk.ui.v2.AvafliV2Strings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins user-facing copy in AvafliV2Strings — in particular the article fix
 * from the 3.1 keyboard/copy audit ("part of an Avafli streak", never
 * "a Avafli").
 */
class UserFacingCopyTest {

    @Test
    fun `adoption subtitle names the typed email`() {
        val subtitle = AvafliV2Strings.adoptionSubtitle("ada@example.com")
        assertTrue(subtitle.contains("ada@example.com"))
        assertTrue(subtitle.contains("6-digit"))
    }

    @Test
    fun `adoption subtitle uses 'an Avafli', not 'a Avafli'`() {
        val subtitle = AvafliV2Strings.adoptionSubtitle("ada@example.com")
        assertTrue(subtitle.contains("part of an Avafli streak"))
        assertFalse(subtitle.contains("a Avafli"))
    }

    @Test
    fun `no centralized user-facing string says 'a Avafli'`() {
        // Sweep every String constant on the strings object so a future
        // sentence can't reintroduce the bad article.
        val constants = AvafliV2Strings::class.java.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .map { it.get(AvafliV2Strings) as String }
        assertTrue(constants.isNotEmpty())
        constants.forEach { value ->
            assertFalse("Found 'a Avafli' in: $value", value.contains("a Avafli"))
        }
    }
}
