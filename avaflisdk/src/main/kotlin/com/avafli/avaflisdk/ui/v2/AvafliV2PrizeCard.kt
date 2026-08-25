package com.avafli.avaflisdk.ui.v2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.avaflisdk.R
import kotlin.math.roundToInt

// Day 2+ prize card (Joe's Aug-2026 dark full-bleed revision, ported from iOS
// AvafliV2PrizeCard): the prize art fills the WHOLE card, a solid black stats
// strip (streak + total entries) sits inside the top edge, and the
// prize-derived headline rides the bottom over a black→transparent scrim.
// The image is publisher-configurable (prizeImageUrl); default is the bundled
// cash pile.

@Composable
internal fun AvafliV2PrizeCard(
    accent: Color,
    streakDay: Int,
    totalEntries: Int,
    prizeImageUrl: String?,
    prizeValue: Int,
    prizeDescription: String,
    visitMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        Hero(prizeImageUrl)
        StatsStrip(
            accent = accent,
            streakDay = streakDay,
            totalEntries = totalEntries,
            visitMode = visitMode,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Headline(
            accent = accent,
            prizeValue = prizeValue,
            prizeDescription = prizeDescription,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun Hero(prizeImageUrl: String?) {
    if (prizeImageUrl.isNullOrBlank()) {
        BundledHero()
        return
    }
    val art = rememberAvafliRemoteImageState(prizeImageUrl)

    // Deep charcoal under everything: a cold URL never flashes blank/white
    // against the card, it darkens and then the art fades up over it.
    Box(Modifier.fillMaxSize().background(AvafliV2Color.deepCharcoal))

    // The warm path (AvafliV2ImageWarmer decoded this at registration/refresh)
    // resolves the bitmap during the FIRST composition, so this target is
    // already 1f and animateFloatAsState starts there — no fade, the art
    // paints with the rest of the card. A cold URL starts at 0f and fades in
    // over ~200ms when the bytes land.
    val alpha by animateFloatAsState(
        targetValue = if (art.bitmap != null) 1f else 0f,
        animationSpec = tween(Avafli_IMAGE_FADE_MS),
        label = "avafliHeroFade",
    )
    art.bitmap?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(alpha),
            contentScale = ContentScale.Crop,
        )
    }
    // Broken URL — fall back to the bundled cash hero rather than a dark hole.
    if (art.failed) BundledHero()
}

@Composable
private fun BundledHero() {
    Image(
        painter = painterResource(R.drawable.avafli_cash_hero_single),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}

/** Solid black strip inside the top of the card: accent title + white sub. */
@Composable
private fun StatsStrip(
    accent: Color,
    streakDay: Int,
    totalEntries: Int,
    visitMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(Color.Black),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.avafli_flame),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(width = 18.dp, height = 22.dp),
            )
            Column(Modifier.padding(start = 7.dp)) {
                Text(
                    "$streakDay ${if (visitMode) "VISIT" else "DAY"} STREAK",
                    style = AvafliV2Font.inter(15.sp, FontWeight.Black, tracking = (-0.3).sp, color = accent),
                )
                Text(
                    "Keep it going!",
                    style = AvafliV2Font.inter(12.sp, FontWeight.Medium, color = Color.White),
                )
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.avafli_ticket),
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .size(width = 22.dp, height = 15.dp)
                    .rotate(-25f),
            )
            Column(Modifier.padding(start = 10.dp)) {
                AvafliV2CountUpText(value = totalEntries, accent = accent)
                Text(
                    "Total Entries",
                    style = AvafliV2Font.inter(12.sp, FontWeight.Medium, color = Color.White),
                )
            }
        }
    }
}

/** Prize headline over the bottom scrim. */
@Composable
private fun Headline(
    accent: Color,
    prizeValue: Int,
    prizeDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0f),
                    0.45f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Black.copy(alpha = 0.9f),
                )
            )
            .padding(horizontal = 14.dp)
            .padding(top = 34.dp, bottom = 10.dp),
    ) {
        if (AvafliV2PrizeText.isCash(prizeDescription)) {
            // "WIN $1,000" (Black 44) over "CASH PRIZE" (Black 19), right-aligned.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy((-5).dp),
            ) {
                AvafliAutoSizeText(
                    "WIN $${prizeValue.avafliFormatted()}",
                    style = AvafliV2Font.inter(44.sp, FontWeight.Black, tracking = (-2.2).sp, color = Color.White),
                    minScale = 0.5f,
                )
                Text(
                    "CASH PRIZE",
                    style = AvafliV2Font.inter(19.sp, FontWeight.Black, tracking = (-0.57).sp, color = Color.White),
                )
            }
        } else {
            // Centered "Win a {Prize}" + accent "$X.00 VALUE!".
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AvafliAutoSizeText(
                    "Win ${AvafliV2PrizeText.article(prizeDescription)} $prizeDescription",
                    style = AvafliV2Font.inter(
                        28.sp, FontWeight.Black,
                        tracking = (-1.0).sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                    minScale = 0.55f,
                    textAlign = TextAlign.Center,
                )
                if (AvafliV2PrizeText.showsValueLine(prizeDescription, prizeValue)) {
                    Text(
                        "$${prizeValue.avafliFormatted()}.00 VALUE!",
                        style = AvafliV2Font.inter(15.sp, FontWeight.Black, color = accent),
                    )
                }
            }
        }
    }
}

// MARK: - Counting total readout

/**
 * The stats-strip total, ported from iOS AvafliV2CountUpText: counts up
 * smoothly (~0.7s ease-out) when the value increases and pops Joe's Figma
 * confetti-burst GIF as it lands on the new total (one-shot, removed when
 * the GIF finishes).
 */
@Composable
internal fun AvafliV2CountUpText(value: Int, accent: Color) {
    var shown by remember { mutableStateOf(value) }
    var burst by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        val from = shown
        if (value == from) return@LaunchedEffect
        val counter = Animatable(0f)
        counter.animateTo(
            targetValue = 1f,
            // Ease-out ramp over ~0.7s (mirrors iOS's 1-(1-t)^3).
            animationSpec = tween(700, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)),
        ) {
            shown = from + ((value - from) * this.value).roundToInt()
        }
        shown = value
        // The GIF's onFinished removes the burst overlay.
        burst = true
    }

    Box(contentAlignment = Alignment.Center) {
        Text(
            shown.avafliFormatted(),
            style = AvafliV2Font.inter(15.sp, FontWeight.Black, tracking = (-0.3).sp, color = accent),
        )
        if (burst) {
            // Joe's Figma confetti burst: one-shot GIF shown as the count
            // lands, removed once it finishes. Larger than the text but must
            // not affect layout (requiredSize draws past the bounds).
            AvafliV2GifBurst(
                resId = R.raw.avafli_confetti_burst,
                modifier = Modifier.requiredSize(width = 54.dp, height = 44.dp),
                onFinished = { burst = false },
            )
        }
    }
}
