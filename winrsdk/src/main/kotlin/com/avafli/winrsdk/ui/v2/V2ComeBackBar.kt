package com.avafli.winrsdk.ui.v2

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.R
import kotlinx.coroutines.delay

/**
 * Confirmation ("come back tomorrow") bar, ported from iOS WINRV2ComeBackBar:
 * black strip, calendar icon, reward line, celebratory sprinkles drifting over it.
 *
 * Joe's Slice sequence: the bar RESTS on the come-back pitch. When the
 * auto-reveal fires (`claimed` flips true), "{N} ENTRIES ADDED / You're on a
 * roll!" SLIDES in horizontally, holds ~2.6s, then SLIDES back out to the
 * pitch — the pitch is the final state. A claimed-at-mount reopen (same-day
 * dashboard reopen) rests on the pitch directly, no toast replay.
 */
private enum class WINRV2BarPhase { Pitch, Added }

@Composable
internal fun WINRV2ComeBackBar(
    accent: Color,
    nextEntries: Int,
    visitMode: Boolean = false,
    claimed: Boolean = false,
    claimedEntries: Int = 0,
    modifier: Modifier = Modifier,
) {
    var phase by remember { mutableStateOf(WINRV2BarPhase.Pitch) }
    var lastClaimed by remember { mutableStateOf(claimed) }
    // LaunchedEffect scoping makes the hold timer teardown-safe: the delay is
    // cancelled automatically if the bar leaves composition mid-hold.
    LaunchedEffect(claimed) {
        if (claimed == lastClaimed) return@LaunchedEffect // mount: rest on pitch
        lastClaimed = claimed
        if (claimed) {
            // Fresh reveal: celebrate, hold, then slide back to the pitch.
            phase = WINRV2BarPhase.Added
            delay(2600)
            phase = WINRV2BarPhase.Pitch
        } else {
            phase = WINRV2BarPhase.Pitch
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(71.dp)
            .background(Color.Black)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        // iOS: pitch enters/exits at the leading edge, the ADDED toast at the
        // trailing edge — a horizontal slide (not a crossfade), on a spring
        // (response 0.5, damping 0.85).
        val swapSpring = spring<androidx.compose.ui.unit.IntOffset>(
            dampingRatio = 0.85f,
            stiffness = 158f,
        )
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                if (targetState == WINRV2BarPhase.Added) {
                    // Toast slides in from the trailing edge; pitch slides out leading.
                    (slideInHorizontally(swapSpring) { it } + fadeIn()) togetherWith
                        (slideOutHorizontally(swapSpring) { -it } + fadeOut())
                } else {
                    // Pitch slides back in from the leading edge; toast exits trailing.
                    (slideInHorizontally(swapSpring) { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally(swapSpring) { it } + fadeOut())
                }
            },
            label = "winrComeBackSwap",
        ) { barPhase ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (barPhase) {
                    WINRV2BarPhase.Added -> ClaimedContent(accent, claimedEntries)
                    WINRV2BarPhase.Pitch -> ComeBackContent(accent, nextEntries, visitMode)
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
            // "Come back tomorrow"/"Come back again" is BOLD, the rest
            // regular — per Joe's banner lockup.
            Text(
                buildAnnotatedString {
                    if (visitMode) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Come back again")
                        }
                        append(" to receive:")
                    } else {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Come back tomorrow")
                        }
                        append(" to\nkeep your streak alive and receive:")
                    }
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
