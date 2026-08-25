package com.avafli.avaflisdk.ui.v2

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
import com.avafli.avaflisdk.R

// Drawer chrome + TOP UI header, ported from iOS AvafliV2Components.swift.

/** The little grab handle at the top of the drawer (Figma "TAB"). */
@Composable
internal fun AvafliV2TabGrabber(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 51.dp, height = 5.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.4f))
    )
}

// The radial primary-color top glow (AvafliV2TopGlow) is GONE (2.9.3): every
// screen now uses the flat gunmetal drawer background.

@Composable
internal fun AvafliV2CircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(AvafliV2Color.deepCharcoal)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Back chevron (SF "chevron.left" equivalent), drawn white. */
@Composable
internal fun AvafliV2BackChevron(modifier: Modifier = Modifier) {
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
internal fun AvafliV2Header(
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
        AvafliV2CircleButton(onClick = if (showsBack) onBack else onInfo) {
            if (showsBack) {
                AvafliV2BackChevron()
            } else {
                Text("?", style = AvafliV2Font.inter(16.sp, color = Color.White))
            }
        }
        Spacer(Modifier.weight(1f))
        val logo = rememberAvafliRemoteImage(logoUrl)
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
                "Avafli",
                style = AvafliV2Font.inter(28.sp, FontWeight.Black, color = Color.White),
            )
            else -> Box(Modifier.height(60.dp).width(1.dp))
        }
        Spacer(Modifier.weight(1f))
        AvafliV2CircleButton(onClick = onClose) {
            Icon(
                painter = painterResource(R.drawable.avafli_close_x),
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
