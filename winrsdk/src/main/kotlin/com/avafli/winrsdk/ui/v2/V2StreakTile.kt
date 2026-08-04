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
        WINRV2TileState.Active ->
            // Joe's active-tile motion: breathing glow + confetti specks scattered
            // around the tile. The confetti field is larger than the tile but must
            // not affect layout (it draws past the tile bounds, like iOS .background).
            Box(
                modifier.size(width = 106.dp, height = 134.dp),
                contentAlignment = Alignment.Center,
            ) {
                WINRV2ConfettiField(
                    modifier = Modifier.requiredSize(width = 152.dp, height = 176.dp),
                    count = 12,
                    speed = 0.7,
                )
                TileCard(accent, day, entries, state, visitMode, Modifier.winrPulseGlow(accent))
            }

        WINRV2TileState.Ready ->
            // Pre-reveal: glow marks today's tile, but the confetti and
            // checkmark are saved for the auto-reveal moment.
            TileCard(accent, day, entries, state, visitMode, modifier.winrPulseGlow(accent))

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
                WINRV2TileState.Active -> WINRV2AnimatedCheckmark(
                    modifier = Modifier.size(20.dp),
                    lineWidth = 2.5.dp,
                )
                WINRV2TileState.Ready -> Icon(
                    painter = painterResource(R.drawable.winr_flame),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(width = 16.dp, height = 20.dp),
                )
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
