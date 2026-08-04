package com.avafli.winrsdk.ui.v2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

// Day 2+ prize card, ported from iOS WINRV2PrizeCard: white stats strip
// (streak + total entries) over the prize image. The image is publisher-
// configurable (prizeImageUrl); default is the bundled cash pile with
// the prize-derived headline overlaid.

internal fun winrIsCashPrize(prizeDescription: String): Boolean =
    prizeDescription.isEmpty() || prizeDescription.lowercase().contains("cash")

/**
 * Article keyed off the LEADING character: "$500 Amazon..." reads
 * "a five-hundred...", so any non-letter start takes "a"; only a leading
 * vowel takes "an".
 */
internal fun winrArticle(prizeDescription: String): String {
    val first = prizeDescription.trim().firstOrNull() ?: return "a"
    if (!first.isLetter()) return "a"
    return if ("AEIOU".contains(first.uppercaseChar())) "an" else "a"
}

/** True when the description already states the $amount ("$500 Amazon Gift Card"). */
internal fun winrDescriptionContainsValue(prizeDescription: String, value: Int): Boolean =
    prizeDescription.contains("$${value.winrFormatted()}") || prizeDescription.contains("$$value")

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
    Column(modifier.clip(RoundedCornerShape(10.dp))) {
        StatsStrip(accent, streakDay, totalEntries, visitMode)
        PromoArea(accent, prizeImageUrl, prizeValue, prizeDescription)
    }
}

@Composable
private fun StatsStrip(accent: Color, streakDay: Int, totalEntries: Int, visitMode: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(Color.White),
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
                    style = WINRV2Font.inter(12.sp, FontWeight.Medium, color = WINRV2Color.gunmetal),
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
                Text(
                    totalEntries.winrFormatted(),
                    style = WINRV2Font.inter(15.sp, FontWeight.Black, tracking = (-0.3).sp, color = accent),
                )
                Text(
                    "Total Entries",
                    style = WINRV2Font.inter(12.sp, FontWeight.Medium, color = WINRV2Color.gunmetal),
                )
            }
        }
    }
}

@Composable
private fun PromoArea(accent: Color, prizeImageUrl: String?, prizeValue: Int, prizeDescription: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
    ) {
        if (prizeImageUrl != null) {
            // Publisher-supplied prize art fills the card as-is.
            val art = rememberWinrRemoteImage(prizeImageUrl)
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize().background(WINRV2Color.gunmetal))
            }
        } else {
            // Default: bundled cash pile fading up into white, with the
            // prize-derived headline over the fade (Figma cash card).
            Image(
                painter = painterResource(R.drawable.winr_cash_hero_single),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = 14.dp),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White,
                            0.3f to Color.White.copy(alpha = 0.55f),
                            0.62f to Color.White.copy(alpha = 0f),
                        )
                    )
            )
            PromoHeadline(prizeValue, prizeDescription)
        }
    }
}

@Composable
private fun PromoHeadline(prizeValue: Int, prizeDescription: String) {
    if (winrIsCashPrize(prizeDescription)) {
        // Figma cash lockup: "WIN $1,000" (Black 54) over "CASH PRIZE" (Black 19),
        // right-aligned.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = 4.dp),
            horizontalAlignment = Alignment.End,
        ) {
            WINRAutoSizeText(
                "WIN $${prizeValue.winrFormatted()}",
                style = WINRV2Font.inter(54.sp, FontWeight.Black, tracking = (-2.7).sp, color = WINRV2Color.gunmetal),
                minScale = 0.5f,
            )
            Text(
                "CASH PRIZE",
                style = WINRV2Font.inter(19.sp, FontWeight.Black, tracking = (-0.57).sp, color = WINRV2Color.gunmetal),
                modifier = Modifier.offset(y = (-6).dp),
            )
        }
    } else {
        // "WIN A $500 AMAZON GIFT CARD" + "$500.00 Value!"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WINRAutoSizeText(
                "WIN ${winrArticle(prizeDescription).uppercase()} ${prizeDescription.uppercase()}",
                style = WINRV2Font.inter(
                    30.sp, FontWeight.Black,
                    tracking = (-1.1).sp,
                    color = WINRV2Color.gunmetal,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
                minScale = 0.55f,
                textAlign = TextAlign.Center,
            )
            if (prizeValue > 0 && !winrDescriptionContainsValue(prizeDescription, prizeValue)) {
                Text(
                    "$${prizeValue.winrFormatted()}.00 Value!",
                    style = WINRV2Font.inter(15.sp, FontWeight.Bold, color = WINRV2Color.gunmetal),
                )
            }
        }
    }
}
