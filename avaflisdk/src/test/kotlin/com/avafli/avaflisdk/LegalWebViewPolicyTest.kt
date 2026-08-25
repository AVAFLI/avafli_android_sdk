package com.avafli.avaflisdk

import com.avafli.avaflisdk.ui.v2.AvafliLegalWebPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The in-experience legal webview's pure URL policy (2.9.4):
 *
 *  - the privacy page loads with `?app=1` (the parallel in-app build that
 *    renders the delete-my-data section), built to tolerate existing queries;
 *  - an `avafli://delete` navigation — or the legacy `winr://delete`, which
 *    the hosted page may still emit — is the page's bridge back into the
 *    SDK's delete confirmation, and ONLY those navigations are intercepted.
 */
class LegalWebViewPolicyTest {

    // ── privacyUrlForApp: ?app=1 construction ──

    @Test
    fun `appends app=1 to the default privacy URL`() {
        assertEquals(
            "https://winrmedia.com/sdk/privacy?app=1",
            AvafliLegalWebPolicy.privacyUrlForApp(),
        )
    }

    @Test
    fun `appends app=1 to a URL with an existing query string`() {
        assertEquals(
            "https://winrmedia.com/sdk/privacy?lang=en&app=1",
            AvafliLegalWebPolicy.privacyUrlForApp("https://winrmedia.com/sdk/privacy?lang=en"),
        )
    }

    @Test
    fun `leaves a URL already carrying an app param untouched`() {
        val url = "https://winrmedia.com/sdk/privacy?app=1"
        assertEquals(url, AvafliLegalWebPolicy.privacyUrlForApp(url))
    }

    @Test
    fun `passes an unparseable URL through unchanged`() {
        assertEquals("not a url", AvafliLegalWebPolicy.privacyUrlForApp("not a url"))
    }

    // ── isDeleteBridge: the shouldOverrideUrlLoading decision ──

    @Test
    fun `avafli delete is the bridge`() {
        assertTrue(AvafliLegalWebPolicy.isDeleteBridge("avafli://delete"))
    }

    @Test
    fun `legacy winr delete is still the bridge`() {
        assertTrue(AvafliLegalWebPolicy.isDeleteBridge("winr://delete"))
    }

    @Test
    fun `tolerates trailing slash, case, and query strings`() {
        assertTrue(AvafliLegalWebPolicy.isDeleteBridge("avafli://delete/"))
        assertTrue(AvafliLegalWebPolicy.isDeleteBridge("winr://delete/"))
        assertTrue(AvafliLegalWebPolicy.isDeleteBridge("AVAFLI://DELETE"))
        assertTrue(AvafliLegalWebPolicy.isDeleteBridge("WINR://DELETE"))
        assertTrue(AvafliLegalWebPolicy.isDeleteBridge("avafli://delete?source=privacy"))
        assertTrue(AvafliLegalWebPolicy.isDeleteBridge("winr://delete?source=privacy"))
        assertTrue(AvafliLegalWebPolicy.isDeleteBridge("avafli:delete"))
        assertTrue(AvafliLegalWebPolicy.isDeleteBridge("winr:delete"))
    }

    @Test
    fun `ordinary web navigations are not intercepted`() {
        assertFalse(AvafliLegalWebPolicy.isDeleteBridge("https://winrmedia.com/sdk/privacy?app=1"))
        assertFalse(AvafliLegalWebPolicy.isDeleteBridge("https://winrmedia.com/delete"))
        assertFalse(AvafliLegalWebPolicy.isDeleteBridge("mailto:info@avafli.com"))
    }

    @Test
    fun `other avafli and winr paths are not the bridge`() {
        assertFalse(AvafliLegalWebPolicy.isDeleteBridge("avafli://open"))
        assertFalse(AvafliLegalWebPolicy.isDeleteBridge("winr://open"))
        assertFalse(AvafliLegalWebPolicy.isDeleteBridge("avafli://delete/everything"))
        assertFalse(AvafliLegalWebPolicy.isDeleteBridge("winr://delete/everything"))
        assertFalse(AvafliLegalWebPolicy.isDeleteBridge("avafli://"))
        assertFalse(AvafliLegalWebPolicy.isDeleteBridge("winr://"))
    }

    @Test
    fun `null and blank are not the bridge`() {
        assertFalse(AvafliLegalWebPolicy.isDeleteBridge(null))
        assertFalse(AvafliLegalWebPolicy.isDeleteBridge(""))
        assertFalse(AvafliLegalWebPolicy.isDeleteBridge("   "))
    }
}
