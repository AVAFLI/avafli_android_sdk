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
 * Joe's sequence: on a CELEBRATION open ([celebrationOpen]) the bar's FIRST
 * visible state is the toast — "YOU'RE ON A ROLL! / Your {N} entries have been
 * added automatically." — never the pitch first; it holds ~2.5s then slides
 * ONCE to the come-back pitch, the final state. Non-celebration opens (same-day
 * reopens, Day 1 modal) rest on the pitch directly. If `claimed` flips true
 * mid-life without a celebration mount (late-arriving grant), the toast slides
 * in, holds, and slides back — same single forward carousel.
 */
private enum class WINRV2BarPhase { Pitch, Added }

/** How long the "entries added" toast holds before sliding to the pitch. */
private const val TOAST_HOLD_MS = 2500L

@Composable
internal fun WINRV2ComeBackBar(
    accent: Color,
    nextEntries: Int,
    visitMode: Boolean = false,
    claimed: Boolean = false,
    claimedEntries: Int = 0,
    celebrationOpen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Captured at mount: a celebration open starts ON the toast (no slide-in,
    // it IS the first frame) and later parameter flips must not re-trigger it.
    val celebrationAtMount = remember { celebrationOpen }
    var phase by remember {
        mutableStateOf(if (celebrationAtMount) WINRV2BarPhase.Added else WINRV2BarPhase.Pitch)
    }
    var lastClaimed by remember { mutableStateOf(claimed) }
    // LaunchedEffect scoping makes the hold timers teardown-safe: the delays
    // are cancelled automatically if the bar leaves composition mid-hold.
    LaunchedEffect(Unit) {
        if (celebrationAtMount) {
            // Celebration open: toast first, then ONE slide to the pitch.
            delay(TOAST_HOLD_MS)
            phase = WINRV2BarPhase.Pitch
        }
    }
    LaunchedEffect(claimed) {
        if (claimed == lastClaimed) return@LaunchedEffect // mount: keep initial phase
        lastClaimed = claimed
        if (claimed && !celebrationAtMount && phase == WINRV2BarPhase.Pitch) {
            // Late-arriving grant on a non-celebration mount: celebrate, hold,
            // then slide back to the pitch.
            phase = WINRV2BarPhase.Added
            delay(TOAST_HOLD_MS)
            phase = WINRV2BarPhase.Pitch
        } else if (!claimed) {
            // Claim retracted (predicted grant failed) — settle on the pitch.
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
        // Carousel: every state slides IN from the right and OUT to the
        // left — one continuous forward direction, never a rewind. Spring
        // mirrors iOS (response 0.5, damping 0.85).
        val swapSpring = spring<androidx.compose.ui.unit.IntOffset>(
            dampingRatio = 0.85f,
            stiffness = 158f,
        )
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (slideInHorizontally(swapSpring) { it } + fadeIn()) togetherWith
                    (slideOutHorizontally(swapSpring) { -it } + fadeOut())
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
                "YOU’RE ON A ROLL!",
                style = WINRV2Font.inter(20.sp, FontWeight.Black, tracking = (-0.6).sp, color = accent),
                minScale = 0.7f,
            )
            Text(
                "Your ${claimedEntries.winrFormatted()} entries have been added automatically.",
                style = WINRV2Font.inter(13.sp, FontWeight.Bold, color = Color.White),
            )
        }
    }
}
