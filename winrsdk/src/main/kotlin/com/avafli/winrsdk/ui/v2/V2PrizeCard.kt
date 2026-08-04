package com.avafli.winrsdk.ui.v2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import com.avafli.winrsdk.R
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// Day 2+ prize card (Joe's Aug-2026 dark full-bleed revision, ported from iOS
// WINRV2PrizeCard): the prize art fills the WHOLE card, a solid black stats
// strip (streak + total entries) sits inside the top edge, and the
// prize-derived headline rides the bottom over a black→transparent scrim.
// The image is publisher-configurable (prizeImageUrl); default is the bundled
// cash pile.

@Composable
internal fun WINRV2PrizeCard(
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
    if (prizeImageUrl != null) {
        val art = rememberWinrRemoteImage(prizeImageUrl)
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize().background(WINRV2Color.deepCharcoal))
        }
    } else {
        Image(
            painter = painterResource(R.drawable.winr_cash_hero_single),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
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
                painterResource(R.drawable.winr_flame),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(width = 18.dp, height = 22.dp),
            )
            Column(Modifier.padding(start = 7.dp)) {
                Text(
                    "$streakDay ${if (visitMode) "VISIT" else "DAY"} STREAK",
                    style = WINRV2Font.inter(15.sp, FontWeight.Black, tracking = (-0.3).sp, color = accent),
                )
                Text(
                    "Keep it going!",
                    style = WINRV2Font.inter(12.sp, FontWeight.Medium, color = Color.White),
                )
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.winr_ticket),
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .size(width = 22.dp, height = 15.dp)
                    .rotate(-25f),
            )
            Column(Modifier.padding(start = 10.dp)) {
                WINRV2CountUpText(value = totalEntries, accent = accent)
                Text(
                    "Total Entries",
                    style = WINRV2Font.inter(12.sp, FontWeight.Medium, color = Color.White),
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
        if (WINRV2PrizeText.isCash(prizeDescription)) {
            // "WIN $1,000" (Black 44) over "CASH PRIZE" (Black 19), right-aligned.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy((-5).dp),
            ) {
                WINRAutoSizeText(
                    "WIN $${prizeValue.winrFormatted()}",
                    style = WINRV2Font.inter(44.sp, FontWeight.Black, tracking = (-2.2).sp, color = Color.White),
                    minScale = 0.5f,
                )
                Text(
                    "CASH PRIZE",
                    style = WINRV2Font.inter(19.sp, FontWeight.Black, tracking = (-0.57).sp, color = Color.White),
                )
            }
        } else {
            // Centered "Win a {Prize}" + accent "$X.00 VALUE!".
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WINRAutoSizeText(
                    "Win ${WINRV2PrizeText.article(prizeDescription)} $prizeDescription",
                    style = WINRV2Font.inter(
                        28.sp, FontWeight.Black,
                        tracking = (-1.0).sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                    minScale = 0.55f,
                    textAlign = TextAlign.Center,
                )
                if (WINRV2PrizeText.showsValueLine(prizeDescription, prizeValue)) {
                    Text(
                        "$${prizeValue.winrFormatted()}.00 VALUE!",
                        style = WINRV2Font.inter(15.sp, FontWeight.Black, color = accent),
                    )
                }
            }
        }
    }
}

// MARK: - Counting total readout

/**
 * The stats-strip total, ported from iOS WINRV2CountUpText: counts up
 * smoothly (~0.7s ease-out) when the value increases and pops a brief star
 * burst as it lands on the new total.
 */
@Composable
internal fun WINRV2CountUpText(value: Int, accent: Color) {
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
        burst = true
        delay(900)
        burst = false
    }

    Box(contentAlignment = Alignment.Center) {
        Text(
            shown.winrFormatted(),
            style = WINRV2Font.inter(15.sp, FontWeight.Black, tracking = (-0.3).sp, color = accent),
        )
        if (burst) {
            WINRV2ConfettiField(
                modifier = Modifier.matchParentSize(),
                count = 8,
                speed = 1.4,
            )
        }
    }
}
