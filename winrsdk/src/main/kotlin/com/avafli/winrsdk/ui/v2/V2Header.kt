package com.avafli.winrsdk.ui.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.R

// Drawer chrome + TOP UI header, ported from iOS WINRV2Components.swift.

/** The little grab handle at the top of the drawer (Figma "TAB"). */
@Composable
internal fun WINRV2TabGrabber(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 51.dp, height = 5.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.4f))
    )
}

/** The radial primary-color glow that bleeds from the top of the drawer into gunmetal. */
@Composable
internal fun WINRV2TopGlow(accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawBehind {
            val brush = Brush.radialGradient(
                0f to accent,
                0.35f to accent.copy(alpha = 0.55f),
                0.8f to WINRV2Color.gunmetal.copy(alpha = 0.9f),
                1f to WINRV2Color.gunmetal,
                center = Offset(size.width / 2f, 0f),
                radius = 440.dp.toPx(),
            )
            drawRect(brush, alpha = 0.9f)
        }
    )
}

@Composable
internal fun WINRV2CircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(WINRV2Color.deepCharcoal)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Back chevron (SF "chevron.left" equivalent), drawn white. */
@Composable
internal fun WINRV2BackChevron(modifier: Modifier = Modifier) {
    Canvas(modifier.size(15.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.62f, size.height * 0.14f)
            lineTo(size.width * 0.32f, size.height * 0.5f)
            lineTo(size.width * 0.62f, size.height * 0.86f)
        }
        drawPath(
            path,
            Color.White,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * TOP UI: "?" circle • publisher logo • "X" circle.
 * The logo is one of the three publisher-configurable elements.
 * How-it-works swaps the "?" for a back ARROW that returns to the previous screen.
 */
@Composable
internal fun WINRV2Header(
    logoUrl: String?,
    showsBack: Boolean = false,
    onBack: () -> Unit = {},
    onInfo: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WINRV2CircleButton(onClick = if (showsBack) onBack else onInfo) {
            if (showsBack) {
                WINRV2BackChevron()
            } else {
                Text("?", style = WINRV2Font.inter(16.sp, color = Color.White))
            }
        }
        Spacer(Modifier.weight(1f))
        val logo = rememberWinrRemoteImage(logoUrl)
        when {
            logo != null -> Image(
                bitmap = logo,
                contentDescription = null,
                modifier = Modifier
                    .height(60.dp)
                    .widthIn(max = 210.dp),
                contentScale = ContentScale.Fit,
            )
            logoUrl == null -> Text(
                "WINR",
                style = WINRV2Font.inter(28.sp, FontWeight.Black, color = Color.White),
            )
            else -> Box(Modifier.height(60.dp).width(1.dp))
        }
        Spacer(Modifier.weight(1f))
        WINRV2CircleButton(onClick = onClose) {
            Icon(
                painter = painterResource(R.drawable.winr_close_x),
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
