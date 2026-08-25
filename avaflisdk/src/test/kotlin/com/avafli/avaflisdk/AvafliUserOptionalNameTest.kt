package com.avafli.avaflisdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * firstName/lastName are optional on AvafliUser (2.6.3). A publisher can build a
 * user from whatever identity data they have — even just an id — and the SDK
 * captures the rest (email via its capture screen, name at prize-claim). These
 * pin that the id-only user constructs, is NOT a guest, and that the guest
 * sentinel semantics (guest is id-empty) are unaffected.
 */
class AvafliUserOptionalNameTest {

    @Test
    fun `id-only user constructs with empty names`() {
        val user = AvafliUser(id = "user_123")
        assertEquals("user_123", user.id)
        assertEquals("", user.firstName)
        assertEquals("", user.lastName)
        assertNull(user.phone)
        assertNull(user.email)
    }

    @Test
    fun `id-only user is not a guest`() {
        // A non-empty id but absent names must NOT be treated as a guest —
        // guest is defined solely by an empty id.
        assertFalse(AvafliUser(id = "user_123").isGuest)
    }

    @Test
    fun `guest sentinel is still a guest`() {
        assertTrue(AvafliUser.GUEST.isGuest)
        assertTrue(AvafliUser(id = "").isGuest)
    }

    @Test
    fun `id-only user carries the real id for the profile-submit path`() {
        // The registration/profile-submit path sends publisherUserId = user.id
        // for a signed-in (non-guest) user. An id-only user must still be
        // treated as signed in so its id is used, not a minted guest id.
        val user = AvafliUser(id = "user_123")
        val effectiveId = if (user.isGuest) "winr_guest_minted" else user.id
        assertEquals("user_123", effectiveId)
    }
}
