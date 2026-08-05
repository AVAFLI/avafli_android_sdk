package com.avafli.winrsdk.ui.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.R
import com.avafli.winrsdk.domain.Milestone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

//
// Design system for the V2 experience — tokens, fonts, and ladder math, ported
// 1:1 from the iOS WINRV2Theme.swift (Joe's Figma WINR-High-V2). Everything is
// intentionally HARDCODED to the design except the publisher-configurable
// primary color: publishers can customize ONLY logo, prize image, primary color.
//

// MARK: - Colors (Figma design tokens)

internal object WINRV2Color {
    /** background/primary — the drawer + tile background ("gunmetal"). */
    val gunmetal = Color(0xFF1D2330)

    /** black (deep charcoal) — header circle buttons. */
    val deepCharcoal = Color(0xFF0B0D12)

    /** Modal panel background. */
    val panel = Color(0xFF141519)

    /** Modal hairline border. */
    val panelBorder = Color(0xFF515151)

    /** WINR brand blue — the DEFAULT primary when a publisher hasn't set one. */
    val winrBlue = Color(0xFF268FFF)

    val textPrimary = Color.White
    val textSecondary = Color.White.copy(alpha = 0.75f)
    val textTertiary = Color.White.copy(alpha = 0.6f)
    val foregroundSecondary = Color.White.copy(alpha = 0.5f)
}

/**
 * The publisher's primary color (branding.primaryColor) with the WINR-blue default.
 * Drives: CTA buttons, streak-tile outlines/glow, accent text, power-up tiles.
 */
internal fun winrV2Accent(hex: String?): Color {
    if (hex.isNullOrBlank()) return WINRV2Color.winrBlue
    var h = hex.trim()
    if (h.startsWith("#")) h = h.substring(1)
    if (h.length != 6) return WINRV2Color.winrBlue
    return try {
        Color(0xFF000000 or h.toLong(16))
    } catch (_: Exception) {
        WINRV2Color.winrBlue
    }
}

// MARK: - Fonts (Inter + Oswald, bundled in res/font)

internal object WINRV2Font {
    val interFamily = FontFamily(
        Font(R.font.winr_inter_regular, FontWeight.Normal),
        Font(R.font.winr_inter_medium, FontWeight.Medium),
        Font(R.font.winr_inter_semibold, FontWeight.SemiBold),
        Font(R.font.winr_inter_bold, FontWeight.Bold),
        Font(R.font.winr_inter_extrabold, FontWeight.ExtraBold),
        Font(R.font.winr_inter_black, FontWeight.Black),
    )

    val oswaldFamily = FontFamily(
        Font(R.font.winr_oswald_medium, FontWeight.Medium),
        Font(R.font.winr_oswald_bold, FontWeight.Bold),
    )

    fun inter(
        size: TextUnit,
        weight: FontWeight = FontWeight.Normal,
        tracking: TextUnit = TextUnit.Unspecified,
        color: Color = Color.Unspecified,
        textAlign: TextAlign = TextAlign.Unspecified,
        lineHeight: TextUnit = TextUnit.Unspecified,
    ): TextStyle = TextStyle(
        fontFamily = interFamily,
        fontSize = size,
        fontWeight = weight,
        letterSpacing = tracking,
        color = color,
        textAlign = textAlign,
        lineHeight = lineHeight,
    )

    fun oswald(
        size: TextUnit,
        bold: Boolean = false,
        color: Color = Color.Unspecified,
    ): TextStyle = TextStyle(
        fontFamily = oswaldFamily,
        fontSize = size,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        color = color,
    )
}

// MARK: - Number formatting ("1,000" grouping, like iOS .formatted())

internal fun Int.winrFormatted(): String =
    NumberFormat.getIntegerInstance(Locale.US).format(this.toLong())

// MARK: - Prize text derivation (capture strip / prize card / winner claim)

/**
 * Shared prize-derived copy rules (mirrors iOS WINRV2PrizeText): cash prizes
 * read "$1,000.00 CASH PRIZE"; other prizes read "Win a {Description}" with an
 * optional value line that is suppressed when the description already states
 * the amount.
 */
internal object WINRV2PrizeText {
    fun isCash(description: String): Boolean =
        description.isEmpty() || description.lowercase().contains("cash")

    /**
     * "a" vs "an", keyed off the LEADING token: "$500 Amazon…" reads
     * "a five-hundred…", so any non-letter start takes "a"; only a leading
     * vowel sound takes "an".
     */
    fun article(description: String): String {
        val first = description.trim().firstOrNull() ?: return "a"
        if (!first.isLetter()) return "a"
        return if ("AEIOU".contains(first.uppercaseChar())) "an" else "a"
    }

    /**
     * Whether the "$X.00 VALUE!" line should render (redundant when the prize
     * name already states the amount, e.g. "$500 Amazon Gift Card").
     */
    fun showsValueLine(description: String, value: Int): Boolean =
        value > 0 &&
            !description.contains("$${value.winrFormatted()}") &&
            !description.contains("$$value")

    /**
     * The white-strip headline (Day-1 capture + winner splash):
     * cash → "$1,000.00 CASH PRIZE"; other → "Win a $500 Amazon Gift Card".
     */
    fun stripHeadline(description: String, value: Int): String =
        if (isCash(description)) {
            "$${value.winrFormatted()}.00 CASH PRIZE"
        } else {
            "Win ${article(description)} $description"
        }
}

// MARK: - Ladder math (mirrors the backend exactly)

internal object WINRV2Ladder {
    /**
     * Explicit ladder values cover days 1..ladder.size; beyond that the daily
     * increment is the bonusEntries of the LATEST passed milestone (the
     * "+25 EVERY DAY!" accelerators). No milestones → flat at the ladder top.
     */
    fun entries(day: Int, ladder: List<Int>, milestones: List<Milestone>?): Int {
        if (ladder.isEmpty()) return 10
        if (day <= ladder.size) return ladder[(day - 1).coerceAtLeast(0)]
        val ms = (milestones ?: emptyList()).sortedBy { it.day }
        var value = ladder[ladder.size - 1]
        for (d in (ladder.size + 1)..day) {
            var rate = 0
            for (m in ms) {
                if (m.day < d) rate = m.bonusEntries
            }
            value += rate
        }
        return value
    }
}

// MARK: - Remote images (logo / prize art / avatars) — dependency-free loader

private val winrImageCache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
        size > 12
}

/** Already-decoded bitmap for [url], or null when it has never been loaded. */
internal fun winrCachedImage(url: String): ImageBitmap? =
    synchronized(winrImageCache) { winrImageCache[url] }

/**
 * Fetches + decodes [url] off the main thread into the shared LRU cache.
 * Returns null on any failure (callers render their fallback). A cache hit
 * returns without touching the network, so this is safe to call repeatedly.
 */
internal suspend fun winrLoadRemoteImage(url: String): ImageBitmap? {
    winrCachedImage(url)?.let { return it }
    return withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.inputStream.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }?.also { bitmap ->
                synchronized(winrImageCache) { winrImageCache[url] = bitmap }
            }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Load outcome for a remote image, so a display site can tell "still loading"
 * (dark placeholder) apart from "broken" (bundled fallback) — and whether the
 * bytes were already warm, which decides fade vs. no fade.
 */
internal data class WINRV2RemoteImageState(
    val bitmap: ImageBitmap? = null,
    val failed: Boolean = false,
    /**
     * True when [bitmap] was already decoded at the first composition (the
     * warm path — [WINRV2ImageWarmer] normally gets there first), so it paints
     * with the rest of its container instead of fading in.
     */
    val wasWarm: Boolean = false,
)

/**
 * Loads a remote bitmap off the main thread with a tiny in-memory LRU cache,
 * reporting loading / loaded / failed so callers can place a placeholder and a
 * fallback correctly.
 */
@Composable
internal fun rememberWinrRemoteImageState(url: String?): WINRV2RemoteImageState {
    val state by produceState(
        initialValue = url?.let { cached ->
            winrCachedImage(cached)?.let { WINRV2RemoteImageState(it, wasWarm = true) }
        } ?: WINRV2RemoteImageState(),
        key1 = url,
    ) {
        if (url.isNullOrBlank()) {
            value = WINRV2RemoteImageState()
            return@produceState
        }
        if (value.bitmap != null) return@produceState
        val bitmap = winrLoadRemoteImage(url)
        value = WINRV2RemoteImageState(bitmap = bitmap, failed = bitmap == null)
    }
    return state
}

/**
 * Loads a remote bitmap off the main thread with a tiny in-memory LRU cache.
 * Returns null while loading / on failure (callers render their fallback).
 */
@Composable
internal fun rememberWinrRemoteImage(url: String?): ImageBitmap? =
    rememberWinrRemoteImageState(url).bitmap

// MARK: - Remote-image prewarming (publisher prize art + logo)

/** Fade-in for a remote image whose bytes arrive after its container painted. */
internal const val WINR_IMAGE_FADE_MS = 200

/**
 * Decodes the publisher's remote art into the shared image cache ahead of the
 * drawer opening, so the prize card paints its art on its FIRST frame instead
 * of popping in a beat after everything else.
 *
 * This is the remote-image sibling of [WINRV2GifAssets.prewarm]: the SDK
 * learns `prizeImageUrl` / `branding.logoUrl` at registration and on every
 * giveaway refresh, long before the experience is presented, which is exactly
 * the moment to pay the download. Fire-and-forget — a failure just means the
 * display site loads it normally.
 */
internal object WINRV2ImageWarmer {

    /** URLs already warmed (or in flight) — a repeated refresh is a no-op. */
    private val warmed = mutableSetOf<String>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The decode step, injectable so tests can exercise the warm-once /
     * retry-after-failure bookkeeping without a network or an Android
     * `BitmapFactory`. Returns true when the URL is now cached.
     */
    internal var fetcher: suspend (String) -> Boolean = { winrLoadRemoteImage(it) != null }

    /**
     * Kicks off a decode for [url] unless it is already warm or in flight.
     * Returns the launched job (null when nothing was started) so callers —
     * tests, mostly — can await it; production call sites ignore it.
     */
    fun prewarm(url: String?): Job? {
        if (url.isNullOrBlank()) return null
        synchronized(warmed) { if (!warmed.add(url)) return null }
        return scope.launch {
            val ok = try {
                fetcher(url)
            } catch (_: Throwable) {
                false
            }
            // Drop it again on failure so the next refresh can retry.
            if (!ok) synchronized(warmed) { warmed.remove(url) }
        }
    }

    internal fun isWarmed(url: String): Boolean = synchronized(warmed) { warmed.contains(url) }

    internal fun resetForTesting() {
        synchronized(warmed) { warmed.clear() }
        fetcher = { winrLoadRemoteImage(it) != null }
    }
}

// MARK: - Auto-shrinking text (SwiftUI minimumScaleFactor equivalent)

/**
 * Single-style text that scales its font down (to [minScale]) until it fits,
 * mirroring SwiftUI's `lineLimit + minimumScaleFactor` behavior used across V2.
 */
@Composable
internal fun WINRAutoSizeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    minScale: Float = 0.5f,
    textAlign: TextAlign? = null,
) {
    var scale by remember(text, style) { mutableStateOf(1f) }
    var ready by remember(text, style) { mutableStateOf(false) }
    androidx.compose.material3.Text(
        text = text,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        style = style.copy(
            fontSize = style.fontSize * scale,
            letterSpacing = if (style.letterSpacing != TextUnit.Unspecified) {
                style.letterSpacing * scale
            } else {
                style.letterSpacing
            },
        ),
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Clip,
        textAlign = textAlign ?: style.textAlign,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && scale > minScale) {
                scale = (scale - 0.05f).coerceAtLeast(minScale)
            } else {
                ready = true
            }
        }
    )
}
