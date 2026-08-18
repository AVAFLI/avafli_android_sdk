package com.avafli.winrsdk

import com.avafli.winrsdk.ui.v2.WINRLegalWebPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The in-experience legal webview's pure URL policy (2.9.4):
 *
 *  - the privacy page loads with `?app=1` (the parallel in-app build that
 *    renders the delete-my-data section), built to tolerate existing queries;
 *  - a `winr://delete` navigation is the page's bridge back into the SDK's
 *    delete confirmation — and ONLY that navigation is intercepted.
 */
class LegalWebViewPolicyTest {

    // ── privacyUrlForApp: ?app=1 construction ──

    @Test
    fun `appends app=1 to the default privacy URL`() {
        assertEquals(
            "https://winrmedia.com/sdk/privacy?app=1",
            WINRLegalWebPolicy.privacyUrlForApp(),
        )
    }

    @Test
    fun `appends app=1 to a URL with an existing query string`() {
        assertEquals(
            "https://winrmedia.com/sdk/privacy?lang=en&app=1",
            WINRLegalWebPolicy.privacyUrlForApp("https://winrmedia.com/sdk/privacy?lang=en"),
        )
    }

    @Test
    fun `leaves a URL already carrying an app param untouched`() {
        val url = "https://winrmedia.com/sdk/privacy?app=1"
        assertEquals(url, WINRLegalWebPolicy.privacyUrlForApp(url))
    }

    @Test
    fun `passes an unparseable URL through unchanged`() {
        assertEquals("not a url", WINRLegalWebPolicy.privacyUrlForApp("not a url"))
    }

    // ── isDeleteBridge: the shouldOverrideUrlLoading decision ──

    @Test
    fun `winr delete is the bridge`() {
        assertTrue(WINRLegalWebPolicy.isDeleteBridge("winr://delete"))
    }

    @Test
    fun `tolerates trailing slash, case, and query strings`() {
        assertTrue(WINRLegalWebPolicy.isDeleteBridge("winr://delete/"))
        assertTrue(WINRLegalWebPolicy.isDeleteBridge("WINR://DELETE"))
        assertTrue(WINRLegalWebPolicy.isDeleteBridge("winr://delete?source=privacy"))
        assertTrue(WINRLegalWebPolicy.isDeleteBridge("winr:delete"))
    }

    @Test
    fun `ordinary web navigations are not intercepted`() {
        assertFalse(WINRLegalWebPolicy.isDeleteBridge("https://winrmedia.com/sdk/privacy?app=1"))
        assertFalse(WINRLegalWebPolicy.isDeleteBridge("https://winrmedia.com/delete"))
        assertFalse(WINRLegalWebPolicy.isDeleteBridge("mailto:info@avafli.com"))
    }

    @Test
    fun `other winr paths are not the bridge`() {
        assertFalse(WINRLegalWebPolicy.isDeleteBridge("winr://open"))
        assertFalse(WINRLegalWebPolicy.isDeleteBridge("winr://delete/everything"))
        assertFalse(WINRLegalWebPolicy.isDeleteBridge("winr://"))
    }

    @Test
    fun `null and blank are not the bridge`() {
        assertFalse(WINRLegalWebPolicy.isDeleteBridge(null))
        assertFalse(WINRLegalWebPolicy.isDeleteBridge(""))
        assertFalse(WINRLegalWebPolicy.isDeleteBridge("   "))
    }
}
