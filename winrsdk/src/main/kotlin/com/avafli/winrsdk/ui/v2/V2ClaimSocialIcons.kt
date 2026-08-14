package com.avafli.winrsdk.ui.v2

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Simple white vector glyphs for the step-4 "Share on Social Media:" row
// (Instagram / Facebook / X / Snapchat / TikTok), drawn in-code so the SDK
// ships no third-party brand assets. Ported from iOS WINRV2ClaimSocialIcons.

internal enum class WINRSocialGlyphKind(val displayName: String) {
    Instagram("Instagram"),
    Facebook("Facebook"),
    X("X"),
    Snapchat("Snapchat"),
    TikTok("TikTok"),
}

@Composable
internal fun WINRSocialGlyph(kind: WINRSocialGlyphKind, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (kind) {
            WINRSocialGlyphKind.Instagram -> InstagramGlyph()
            WINRSocialGlyphKind.Facebook -> FacebookGlyph()
            WINRSocialGlyphKind.X -> XGlyph()
            WINRSocialGlyphKind.Snapchat -> SnapchatGlyph()
            WINRSocialGlyphKind.TikTok -> TikTokGlyph()
        }
    }
}

/** Rounded square + lens + dot. */
@Composable
private fun InstagramGlyph() {
    Canvas(Modifier.size(44.dp)) {
        val stroke = Stroke(width = 3.2.dp.toPx())
        val inset = 2.dp.toPx()
        drawRoundRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
            style = stroke,
        )
        drawCircle(
            color = Color.White,
            radius = 9.5.dp.toPx(),
            style = stroke,
        )
        drawCircle(
            color = Color.White,
            radius = 2.25.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(
                size.width / 2 + 11.5.dp.toPx(),
                size.height / 2 - 11.5.dp.toPx(),
            ),
        )
    }
}

/** White disc with the dark lowercase f. */
@Composable
private fun FacebookGlyph() {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "f",
            style = WINRV2Font.inter(30.sp, FontWeight.Bold, color = WINRV2Color.deepCharcoal),
            modifier = Modifier.offset(x = 2.dp, y = 1.dp),
        )
    }
}

/** The sharp X wordmark, approximated with the black weight. */
@Composable
private fun XGlyph() {
    Text(
        "X",
        style = androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
        ),
    )
}

/** Ghost outline (simplified Snapchat-style ghost, same path as iOS). */
@Composable
private fun SnapchatGlyph() {
    Canvas(Modifier.size(38.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            // Dome head.
            moveTo(0.18f * w, 0.42f * h)
            quadraticTo(0.5f * w, -0.28f * h, 0.82f * w, 0.42f * h)
            // Right side down, flaring out.
            lineTo(0.82f * w, 0.62f * h)
            quadraticTo(0.86f * w, 0.78f * h, 0.97f * w, 0.86f * h)
            // Scalloped bottom (three bumps).
            quadraticTo(0.82f * w, 0.98f * h, 0.66f * w, 0.88f * h)
            quadraticTo(0.5f * w, 1.02f * h, 0.34f * w, 0.88f * h)
            quadraticTo(0.18f * w, 0.98f * h, 0.03f * w, 0.86f * h)
            // Left flare back up.
            quadraticTo(0.14f * w, 0.78f * h, 0.18f * w, 0.62f * h)
            close()
        }
        drawPath(
            path,
            Color.White,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Musical note (TikTok stand-in, mirrors iOS "music.note"). */
@Composable
private fun TikTokGlyph() {
    Canvas(Modifier.size(38.dp).fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Stem with a flag to the right, note head at the bottom-left.
        drawPath(
            Path().apply {
                moveTo(0.55f * w, 0.82f * h)
                lineTo(0.55f * w, 0.12f * h)
                quadraticTo(0.72f * w, 0.16f * h, 0.78f * w, 0.34f * h)
            },
            Color.White,
            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawOval(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(0.26f * w, 0.7f * h),
            size = androidx.compose.ui.geometry.Size(0.32f * w, 0.24f * h),
        )
    }
}

// ── Share actions ──

/**
 * Per-platform share actions for the winner share row (2.9, 14 Aug decisions):
 *
 *  - X: web intent (`twitter.com/intent/tweet?text=`) via ACTION_VIEW,
 *    prefilled with the winner line + the publisher's optional `shareUrl` —
 *    the X app intercepts the URL when installed, the browser handles it
 *    otherwise.
 *  - Facebook: `sharer.php` with the shareUrl ONLY (the platform strips
 *    prefilled text — quotes/text params are ignored by policy). Without a
 *    shareUrl there is nothing sharer.php can carry → system share sheet.
 *  - Instagram / Snapchat / TikTok: no text-prefill APIs exist → the system
 *    share sheet (ACTION_SEND chooser) with text + link.
 *
 * All best-effort: a missing handler is silently ignored (mirrors iOS).
 */
internal object WINRShareSheet {

    /** System share sheet (ACTION_SEND chooser) with [text]. */
    fun present(context: Context, text: String) {
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, null))
        } catch (_: Exception) {
            // No activity to handle the share — silently ignore.
        }
    }

    /** Routes a tap on [kind] to the platform-appropriate share action. */
    fun share(context: Context, kind: WINRSocialGlyphKind, text: String, shareUrl: String?) {
        val textWithLink = listOfNotNull(
            text.takeIf { it.isNotBlank() },
            shareUrl?.takeIf { it.isNotBlank() },
        ).joinToString(" ")
        when (kind) {
            WINRSocialGlyphKind.X -> {
                val encoded = java.net.URLEncoder.encode(textWithLink, "UTF-8")
                openUrl(context, "https://twitter.com/intent/tweet?text=$encoded")
            }

            WINRSocialGlyphKind.Facebook -> {
                val url = shareUrl?.takeIf { it.isNotBlank() }
                if (url != null) {
                    val encoded = java.net.URLEncoder.encode(url, "UTF-8")
                    openUrl(context, "https://www.facebook.com/sharer/sharer.php?u=$encoded")
                } else {
                    // Nothing sharer.php can carry — degrade to the chooser.
                    present(context, textWithLink)
                }
            }

            WINRSocialGlyphKind.Instagram,
            WINRSocialGlyphKind.Snapchat,
            WINRSocialGlyphKind.TikTok -> present(context, textWithLink)
        }
    }

    private fun openUrl(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (_: Exception) {
            // No browser/app to handle it — silently ignore.
        }
    }
}
