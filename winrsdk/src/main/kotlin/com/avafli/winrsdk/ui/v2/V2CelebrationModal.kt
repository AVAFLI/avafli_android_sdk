package com.avafli.winrsdk.ui.v2

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.R

// Celebration modal (Day 1 "You're in!" / Day 2+ streak), ported from iOS
// WINRV2CelebrationModal. Dismissal requires an explicit tap (GOT IT or X) —
// there is no auto-fade and no scrim-tap dismiss.

@Composable
internal fun WINRV2CelebrationModal(
    accent: Color,
    streakDay: Int,
    earnedEntries: Int,
    nextEntries: Int,
    visitMode: Boolean = false,
    onDismiss: () -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val checkScale by animateFloatAsState(
        if (appeared) 1f else 0.4f,
        spring(dampingRatio = 0.6f, stiffness = 195f),
        label = "celebrationCheckScale",
    )
    val isFirstDay = streakDay <= 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(340.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(WINRV2Color.panel)
                    .border(1.dp, WINRV2Color.panelBorder, RoundedCornerShape(20.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Animated draw-on checkmark (the confetti flutter is an overlay
                // across the whole card, per Joe's modal GIF).
                Box(Modifier.height(110.dp), contentAlignment = Alignment.TopCenter) {
                    WINRV2AnimatedCheckmark(
                        modifier = Modifier
                            .padding(top = 17.dp)
                            .size(88.dp)
                            .graphicsLayer(scaleX = checkScale, scaleY = checkScale),
                        lineWidth = 7.dp,
                    )
                }

                if (isFirstDay) {
                    Text(
                        "You’re in!",
                        style = WINRV2Font.inter(14.sp, FontWeight.Bold, color = Color.White),
                    )
                    WINRAutoSizeText(
                        "$earnedEntries ENTRIES HAVE BEEN ADDED",
                        style = WINRV2Font.inter(23.sp, FontWeight.Black, color = accent),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        minScale = 0.7f,
                    )
                } else {
                    Text(
                        "YOU’RE ON A",
                        style = WINRV2Font.inter(20.sp, FontWeight.Bold, color = Color.White),
                    )
                    Text(
                        "$streakDay ${if (visitMode) "VISIT" else "DAY"} STREAK!",
                        style = WINRV2Font.inter(32.sp, FontWeight.Black, tracking = (-0.96).sp, color = accent),
                    )
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 14.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                if (isFirstDay) {
                    Text(
                        if (visitMode) "NEXT TIME YOU VISIT GET"
                        else "COME BACK TOMORROW TO KEEP\nYOUR STREAK GOING AND GET",
                        style = WINRV2Font.inter(
                            14.sp, FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                        ),
                    )
                    BigNumber(accent, nextEntries)
                } else {
                    Text(
                        "YOU EARNED",
                        style = WINRV2Font.inter(20.sp, FontWeight.Bold, color = Color.White),
                    )
                    BigNumber(accent, earnedEntries)
                    ComeBackCard(accent, nextEntries, visitMode)
                }

                WINRV2PillButton(
                    accent = accent,
                    title = "GOT IT",
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 0.dp)
                        .padding(top = 20.dp, bottom = 24.dp),
                ) { onDismiss() }
            }

            // Confetti flutters over the upper card, like Joe's GIF.
            WINRV2ConfettiField(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
                    .clip(RoundedCornerShape(20.dp)),
                count = 34,
            )

            WINRV2CircleButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.winr_close_x),
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

@Composable
private fun BigNumber(accent: Color, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WINRAutoSizeText(
            value.winrFormatted(),
            style = WINRV2Font.inter(96.sp, FontWeight.Black, tracking = (-4.8).sp, color = accent),
            modifier = Modifier.padding(horizontal = 40.dp),
            minScale = 0.5f,
        )
        Text(
            "ENTRIES",
            style = WINRV2Font.inter(32.sp, FontWeight.Black, tracking = (-0.96).sp, color = Color.White),
            modifier = Modifier.offset(y = (-14).dp),
        )
    }
}

@Composable
private fun ComeBackCard(accent: Color, nextEntries: Int, visitMode: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp)
            .height(71.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.winr_calendar),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(width = 24.dp, height = 26.dp),
        )
        Column(
            modifier = Modifier.padding(start = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                if (visitMode) "Come back again for" else "Come back tomorrow for",
                style = WINRV2Font.inter(15.sp, color = Color.White),
            )
            Text(
                "${nextEntries.winrFormatted()} ENTRIES",
                style = WINRV2Font.inter(20.sp, FontWeight.Black, color = accent),
            )
        }
    }
}
