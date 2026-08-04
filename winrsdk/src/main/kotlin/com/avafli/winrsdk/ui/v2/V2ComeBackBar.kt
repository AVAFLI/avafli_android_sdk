package com.avafli.winrsdk.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.R

/**
 * Confirmation ("come back tomorrow") bar, ported from iOS WINRV2ComeBackBar:
 * black strip, calendar icon, reward line, celebratory sprinkles drifting over it.
 */
@Composable
internal fun WINRV2ComeBackBar(
    accent: Color,
    nextEntries: Int,
    visitMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(71.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.winr_calendar),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(width = 26.dp, height = 28.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (visitMode) {
                        "Come back again to receive:"
                    } else {
                        "Come back tomorrow to\nkeep your streak alive and receive:"
                    },
                    style = WINRV2Font.inter(
                        12.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp,
                    ),
                )
                Text(
                    "${nextEntries.winrFormatted()} ENTRIES",
                    style = WINRV2Font.inter(16.sp, FontWeight.Black, color = accent),
                )
            }
        }
        // Joe's toast has celebratory sprinkles drifting over the reward line.
        WINRV2ConfettiField(
            modifier = Modifier.matchParentSize().clipToBounds(),
            count = 10,
            speed = 0.55,
        )
    }
}
