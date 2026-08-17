package com.avafli.winrsdk

import com.avafli.winrsdk.ui.v2.WINRShareSheet
import com.avafli.winrsdk.ui.v2.WINRSocialGlyphKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Share-link UTM tagging: whenever the publisher's shareUrl rides along in a
 * share action it gains `utm_source={network}&utm_medium=winr_share` — unless
 * the publisher already tagged it with their own utm_source.
 */
class ShareUtmTaggingTest {

    @Test
    fun `appends utm params to a plain URL`() {
        assertEquals(
            "https://example.com/app?utm_source=x&utm_medium=winr_share",
            WINRShareSheet.taggedShareUrl("https://example.com/app", "x"),
        )
    }

    @Test
    fun `appends utm params to a URL with an existing query string`() {
        assertEquals(
            "https://example.com/app?ref=abc&utm_source=facebook&utm_medium=winr_share",
            WINRShareSheet.taggedShareUrl("https://example.com/app?ref=abc", "facebook"),
        )
    }

    @Test
    fun `leaves a URL with an existing utm_source untouched`() {
        val url = "https://example.com/app?utm_source=publisher&utm_medium=email"
        assertEquals(url, WINRShareSheet.taggedShareUrl(url, "x"))
    }

    @Test
    fun `passes through null, blank, and unparseable URLs`() {
        assertNull(WINRShareSheet.taggedShareUrl(null, "x"))
        assertEquals("", WINRShareSheet.taggedShareUrl("", "x"))
        assertEquals("not a url", WINRShareSheet.taggedShareUrl("not a url", "x"))
    }

    @Test
    fun `each network carries its own utm_source value`() {
        assertEquals("x", WINRSocialGlyphKind.X.utmSource)
        assertEquals("facebook", WINRSocialGlyphKind.Facebook.utmSource)
        assertEquals("instagram", WINRSocialGlyphKind.Instagram.utmSource)
        assertEquals("snapchat", WINRSocialGlyphKind.Snapchat.utmSource)
        assertEquals("tiktok", WINRSocialGlyphKind.TikTok.utmSource)
    }
}
