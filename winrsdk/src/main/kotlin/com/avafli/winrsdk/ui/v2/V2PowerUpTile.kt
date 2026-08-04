package com.avafli.winrsdk.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.R

/**
 * The joined "STREAK BONUS!" accelerator tile (Figma MILESTONE TILE right half),
 * ported from iOS WINRV2PowerUpTile. E.g. "1 WEEK STREAK BONUS! / +25 /
 * EVERY DAY! / STARTING AT DAY 8".
 */
@Composable
internal fun WINRV2PowerUpTile(
    accent: Color,
    label: String,       // e.g. "1 WEEK"
    bonus: Int,          // e.g. 25
    footnote: String,    // e.g. "STARTING TOMORROW"
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .size(width = 106.dp, height = 134.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accent)
            .padding(vertical = 10.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.winr_flame),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(width = 18.dp, height = 24.dp),
        )
        Spacer(Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                "$label\nSTREAK BONUS!",
                style = WINRV2Font.inter(
                    9.sp, FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 11.sp,
                ),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "+$bonus",
                    style = WINRV2Font.inter(26.sp, FontWeight.Black, tracking = (-0.8).sp, color = Color.White),
                )
                Text(
                    "EVERY DAY!",
                    style = WINRV2Font.inter(14.sp, FontWeight.Black, color = Color.White),
                    modifier = Modifier.offset(y = (-2).dp),
                )
            }
            Text(
                footnote,
                style = WINRV2Font.oswald(8.sp, bold = true, color = Color.White),
            )
        }
    }
}
