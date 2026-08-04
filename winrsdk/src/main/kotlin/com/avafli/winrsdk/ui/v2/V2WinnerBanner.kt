package com.avafli.winrsdk.ui.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.R

/**
 * "WE HAVE A WINNER!" banner (below the header, above the prize card), ported
 * from iOS WINRV2WinnerBanner. Shown when the giveaway carries a latestWinner.
 */
@Composable
internal fun WINRV2WinnerBanner(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(WINRV2Color.deepCharcoal)
            .clickable(onClick = onTap)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.winr_trophy),
            contentDescription = null,
            modifier = Modifier
                .size(width = 41.dp, height = 54.dp)
                .graphicsLayer(scaleX = -1f),   // design mirrors the trophy on the toast
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(8.dp).weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "WE HAVE A WINNER!",
                style = WINRV2Font.inter(17.sp, FontWeight.ExtraBold, tracking = (-0.85).sp, color = Color.White),
            )
            Text(
                "Tap to see latest winners.",
                style = WINRV2Font.inter(12.sp, tracking = (-0.6).sp, color = Color.White),
            )
        }
        Spacer(Modifier.width(8.dp).weight(1f))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(WINRV2Color.gunmetal),
            contentAlignment = Alignment.Center,
        ) {
            WINRV2PlusIcon()
        }
    }
}

/** Small white "+" (SF plus equivalent). */
@Composable
private fun WINRV2PlusIcon() {
    Canvas(Modifier.size(16.dp)) {
        val stroke = 2.dp.toPx()
        drawLine(
            Color.White,
            start = androidx.compose.ui.geometry.Offset(size.width / 2f, 0f),
            end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            Color.White,
            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
