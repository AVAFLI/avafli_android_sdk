package com.avafli.winrsdk.ui.v2

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
 *
 * Post-reveal / claimed-today variant (Joe's Aug-2026 frames): the bar
 * celebrates "{N} ENTRIES ADDED / You're on a roll!" instead of pitching
 * tomorrow. Pre-reveal and unclaimed states keep the come-back pitch.
 */
@Composable
internal fun WINRV2ComeBackBar(
    accent: Color,
    nextEntries: Int,
    visitMode: Boolean = false,
    claimed: Boolean = false,
    claimedEntries: Int = 0,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(71.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        // iOS: claimedContent enters with scale(0.8)+opacity on a spring
        // (response 0.5, damping 0.8); comeBackContent swaps with opacity.
        val swapSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 158f)
        AnimatedContent(
            targetState = claimed,
            transitionSpec = {
                val enter = if (targetState) {
                    scaleIn(animationSpec = swapSpring, initialScale = 0.8f) +
                        fadeIn(animationSpec = swapSpring)
                } else {
                    fadeIn()
                }
                val exit = if (targetState) {
                    fadeOut()
                } else {
                    scaleOut(targetScale = 0.8f) + fadeOut()
                }
                enter togetherWith exit
            },
            label = "winrComeBackSwap",
        ) { isClaimed ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isClaimed) {
                    ClaimedContent(accent, claimedEntries)
                } else {
                    ComeBackContent(accent, nextEntries, visitMode)
                }
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

@Composable
private fun ComeBackContent(accent: Color, nextEntries: Int, visitMode: Boolean) {
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
}

@Composable
private fun ClaimedContent(accent: Color, claimedEntries: Int) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WINRV2AnimatedCheckmark(
            modifier = Modifier.size(38.dp),
            lineWidth = 3.5.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WINRAutoSizeText(
                "${claimedEntries.winrFormatted()} ENTRIES ADDED",
                style = WINRV2Font.inter(20.sp, FontWeight.Black, tracking = (-0.6).sp, color = accent),
                minScale = 0.7f,
            )
            Text(
                "You’re on a roll!",
                style = WINRV2Font.inter(13.sp, FontWeight.Bold, color = Color.White),
            )
        }
    }
}
