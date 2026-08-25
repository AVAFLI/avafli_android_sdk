package com.avafli.avaflisdk

import com.avafli.avaflisdk.ui.v2.AvafliShareSheet
import com.avafli.avaflisdk.ui.v2.AvafliSocialGlyphKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Share-link UTM tagging: whenever the publisher's shareUrl rides along in a
 * share action it gains `utm_source={network}&utm_medium=avafli_share` — unless
 * the publisher already tagged it with their own utm_source.
 */
class ShareUtmTaggingTest {

    @Test
    fun `appends utm params to a plain URL`() {
        assertEquals(
            "https://example.com/app?utm_source=x&utm_medium=avafli_share",
            AvafliShareSheet.taggedShareUrl("https://example.com/app", "x"),
        )
    }

    @Test
    fun `appends utm params to a URL with an existing query string`() {
        assertEquals(
            "https://example.com/app?ref=abc&utm_source=facebook&utm_medium=avafli_share",
            AvafliShareSheet.taggedShareUrl("https://example.com/app?ref=abc", "facebook"),
        )
    }

    @Test
    fun `leaves a URL with an existing utm_source untouched`() {
        val url = "https://example.com/app?utm_source=publisher&utm_medium=email"
        assertEquals(url, AvafliShareSheet.taggedShareUrl(url, "x"))
    }

    @Test
    fun `passes through null, blank, and unparseable URLs`() {
        assertNull(AvafliShareSheet.taggedShareUrl(null, "x"))
        assertEquals("", AvafliShareSheet.taggedShareUrl("", "x"))
        assertEquals("not a url", AvafliShareSheet.taggedShareUrl("not a url", "x"))
    }

    @Test
    fun `each network carries its own utm_source value`() {
        assertEquals("x", AvafliSocialGlyphKind.X.utmSource)
        assertEquals("facebook", AvafliSocialGlyphKind.Facebook.utmSource)
        assertEquals("instagram", AvafliSocialGlyphKind.Instagram.utmSource)
        assertEquals("snapchat", AvafliSocialGlyphKind.Snapchat.utmSource)
        assertEquals("tiktok", AvafliSocialGlyphKind.TikTok.utmSource)
    }
}
