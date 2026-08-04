package com.avafli.winrsdk.ui.v2

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
import com.avafli.winrsdk.R

// STREAK STEP tile, ported from iOS WINRV2StreakTile (106x134dp).

/**
 * `Ready` = today's tile before the auto-reveal fires (claim already granted
 * server-side, celebration pending): glows like `Active` but shows no checkmark.
 */
internal enum class WINRV2TileState { Completed, Active, Ready, Locked }

@Composable
internal fun WINRV2StreakTile(
    accent: Color,
    day: Int,
    entries: Int,
    state: WINRV2TileState,
    visitMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    when (state) {
        WINRV2TileState.Active -> {
            // Joe's ACTUAL Figma animation: the explosion GIF (check + confetti
            // burst together) mounts exactly when the tile flips to Active — the
            // reveal beat — plays ONCE at ~150% of the tile so it overflows the
            // bounds like an explosion, then is REMOVED; the tile's own small
            // check (icon slot) is the resting state, matching Joe's settled
            // frame and the completed tiles. The overlay is larger than the tile
            // but must not affect layout (requiredSize draws past the tile
            // bounds, like iOS .overlay).
            var burstFinished by remember { mutableStateOf(false) }
            Box(
                modifier.size(width = 106.dp, height = 134.dp),
                contentAlignment = Alignment.Center,
            ) {
                TileCard(
                    accent, day, entries, state, visitMode,
                    Modifier.winrPulseGlow(accent),
                    activeBurstFinished = burstFinished,
                )
                if (!burstFinished) {
                    WINRV2GifBurst(
                        resId = R.raw.winr_tile_burst,
                        modifier = Modifier.requiredSize(200.dp),
                        onFinished = { burstFinished = true },
                    )
                }
            }
        }

        WINRV2TileState.Ready ->
            // Pre-reveal the tile is CALM — a static glow only. Every moving
            // element (pulse, confetti, check draw) is saved for the single
            // celebration moment so nothing animates early.
            TileCard(accent, day, entries, state, visitMode, modifier.winrStaticGlow(accent))

        else -> TileCard(accent, day, entries, state, visitMode, modifier)
    }
}

@Composable
private fun TileCard(
    accent: Color,
    day: Int,
    entries: Int,
    state: WINRV2TileState,
    visitMode: Boolean,
    modifier: Modifier = Modifier,
    activeBurstFinished: Boolean = false,
) {
    val noun = if (visitMode) "VISIT" else "DAY"
    val numberColor = when (state) {
        WINRV2TileState.Completed -> accent
        WINRV2TileState.Active, WINRV2TileState.Ready -> Color.White
        WINRV2TileState.Locked -> WINRV2Color.foregroundSecondary
    }
    val labelColor = if (state == WINRV2TileState.Locked) WINRV2Color.foregroundSecondary else Color.White

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
            style = WINRV2Font.inter(12.sp, FontWeight.Bold, color = Color.White),
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WINRAutoSizeText(
                entries.winrFormatted(),
                style = WINRV2Font.inter(30.sp, FontWeight.Black, tracking = (-1.5).sp, color = numberColor),
                minScale = 0.6f,
            )
            Text(
                "ENTRIES",
                style = WINRV2Font.inter(15.sp, FontWeight.Bold, color = labelColor),
                modifier = Modifier.offset(y = (-2).dp),
            )
        }
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (state) {
                WINRV2TileState.Completed -> Image(
                    painter = painterResource(R.drawable.winr_check_tile_completed),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                // During the burst the GIF overlay paints the check (the slot
                // stays an empty spacer so the card layout matches the other
                // states); once it finishes the tile rests on the same small
                // static check as completed tiles.
                WINRV2TileState.Active -> if (activeBurstFinished) {
                    Image(
                        painter = painterResource(R.drawable.winr_check_tile_completed),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Unit
                }
                // Joe's frames: the current tile pre-check shows ONLY the
                // glowing number — no icon. The enclosing 24dp slot keeps its
                // size so the check can draw into place without the card
                // resizing.
                WINRV2TileState.Ready -> Unit
                WINRV2TileState.Locked -> Icon(
                    painter = painterResource(R.drawable.winr_lock),
                    contentDescription = null,
                    tint = labelColor,
                    modifier = Modifier.size(width = 16.dp, height = 20.dp),
                )
            }
        }
    }
}

private fun Modifier.tileBackground(accent: Color, state: WINRV2TileState): Modifier =
    if (state == WINRV2TileState.Active || state == WINRV2TileState.Ready) {
        drawBehind {
            drawRect(
                Brush.radialGradient(
                    0f to accent,
                    0.45f to accent.copy(alpha = 0.45f),
                    1f to WINRV2Color.gunmetal,
                    center = Offset(size.width / 2f, 0f),
                    radius = 150.dp.toPx(),
                )
            )
        }
    } else {
        background(WINRV2Color.gunmetal)
    }
