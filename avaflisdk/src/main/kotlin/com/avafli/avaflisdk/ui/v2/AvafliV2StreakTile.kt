package com.avafli.avaflisdk.ui.v2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.avaflisdk.R

// STREAK STEP tile, ported from iOS AvafliV2StreakTile (106x134dp).

/**
 * `Ready` = today's tile before the auto-reveal fires (claim already granted
 * server-side, celebration pending): glows like `Active` but shows no checkmark.
 */
internal enum class AvafliV2TileState { Completed, Active, Ready, Locked }

@Composable
internal fun AvafliV2StreakTile(
    accent: Color,
    day: Int,
    entries: Int,
    state: AvafliV2TileState,
    visitMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    when (state) {
        AvafliV2TileState.Active -> {
            // Joe's active-tile motion: breathing glow + confetti specks scattered
            // around the tile (drawn), PLUS a one-shot confetti-burst explosion —
            // Joe's actual Figma GIF — that mounts exactly when the tile flips to
            // Active (the reveal beat), plays ONCE at ~150% of the tile so it
            // overflows the bounds, and is REMOVED when it finishes. The drawn
            // check in the icon slot stays the resting state. Overlays are larger
            // than the tile but must not affect layout (requiredSize draws past
            // the tile bounds, like iOS .background/.overlay).
            var burstFinished by remember { mutableStateOf(false) }
            Box(
                modifier.size(width = 106.dp, height = 134.dp),
                contentAlignment = Alignment.Center,
            ) {
                AvafliV2ConfettiField(
                    modifier = Modifier.requiredSize(width = 152.dp, height = 176.dp),
                    count = 12,
                    speed = 0.7,
                )
                TileCard(accent, day, entries, state, visitMode, Modifier.avafliPulseGlow(accent))
                if (!burstFinished) {
                    AvafliV2GifBurst(
                        resId = R.raw.avafli_confetti_burst,
                        modifier = Modifier.requiredSize(200.dp),
                        onFinished = { burstFinished = true },
                    )
                }
            }
        }

        AvafliV2TileState.Ready ->
            // Pre-reveal the tile is CALM — a static glow only. Every moving
            // element (pulse, confetti, check draw) is saved for the single
            // celebration moment so nothing animates early.
            TileCard(accent, day, entries, state, visitMode, modifier.avafliStaticGlow(accent))

        else -> TileCard(accent, day, entries, state, visitMode, modifier)
    }
}

@Composable
private fun TileCard(
    accent: Color,
    day: Int,
    entries: Int,
    state: AvafliV2TileState,
    visitMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val noun = if (visitMode) "VISIT" else "DAY"
    val numberColor = when (state) {
        AvafliV2TileState.Completed -> accent
        AvafliV2TileState.Active, AvafliV2TileState.Ready -> Color.White
        AvafliV2TileState.Locked -> AvafliV2Color.foregroundSecondary
    }
    val labelColor = if (state == AvafliV2TileState.Locked) AvafliV2Color.foregroundSecondary else Color.White

    Column(
        modifier = modifier
            .size(width = 106.dp, height = 134.dp)
            .clip(RoundedCornerShape(10.dp))
            .tileBackground(accent, state)
            .border(2.dp, accent, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            if (day >= 31) "$noun 31 +" else "$noun $day",
            style = AvafliV2Font.inter(12.sp, FontWeight.Bold, color = Color.White),
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AvafliAutoSizeText(
                entries.avafliFormatted(),
                style = AvafliV2Font.inter(30.sp, FontWeight.Black, tracking = (-1.5).sp, color = numberColor),
                minScale = 0.6f,
            )
            Text(
                "ENTRIES",
                style = AvafliV2Font.inter(15.sp, FontWeight.Bold, color = labelColor),
                modifier = Modifier.offset(y = (-2).dp),
            )
        }
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (state) {
                AvafliV2TileState.Completed -> Image(
                    painter = painterResource(R.drawable.avafli_check_tile_completed),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                AvafliV2TileState.Active -> AvafliV2AnimatedCheckmark(
                    modifier = Modifier.size(20.dp),
                    lineWidth = 2.5.dp,
                )
                // Joe's frames: the current tile pre-check shows ONLY the
                // glowing number — no icon. The enclosing 24dp slot keeps its
                // size so the check can draw into place without the card
                // resizing.
                AvafliV2TileState.Ready -> Unit
                AvafliV2TileState.Locked -> Icon(
                    painter = painterResource(R.drawable.avafli_lock),
                    contentDescription = null,
                    tint = labelColor,
                    modifier = Modifier.size(width = 16.dp, height = 20.dp),
                )
            }
        }
    }
}

private fun Modifier.tileBackground(accent: Color, state: AvafliV2TileState): Modifier =
    if (state == AvafliV2TileState.Active || state == AvafliV2TileState.Ready) {
        drawBehind {
            drawRect(
                Brush.radialGradient(
                    0f to accent,
                    0.45f to accent.copy(alpha = 0.45f),
                    1f to AvafliV2Color.gunmetal,
                    center = Offset(size.width / 2f, 0f),
                    radius = 150.dp.toPx(),
                )
            )
        }
    } else {
        background(AvafliV2Color.gunmetal)
    }
