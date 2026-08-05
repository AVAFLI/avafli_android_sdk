package com.avafli.winrsdk.ui.v2

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cold start (nothing cached to paint from): a SKELETON of the dashboard
 * rather than a centered spinner.
 *
 * The drawer auto-opens ahead of its sequential network calls
 * (registerDevice → getActiveGiveaway → claim). A bare spinner made that wait
 * read as "nothing is here yet"; blocking out the real layout — grab handle,
 * header, prize card, three streak tiles, come-back bar, CTA pill — in the
 * drawer's own gunmetal reads as the content arriving, at identical latency.
 *
 * The warm path never gets here at all: a cached giveaway plus a persisted
 * streak paints the real dashboard immediately (see
 * `WINRExperienceViewModel.hydrateFromCache`).
 */
@Composable
internal fun WINRV2LoadingSkeleton() {
    // ONE shared pulse keeps every block in phase, so the drawer reads as a
    // single surface breathing rather than a field of blinking rectangles.
    val transition = rememberInfiniteTransition(label = "winrSkeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "winrSkeletonPulse",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(WINRV2Color.gunmetal)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(pulse),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WINRV2TabGrabber(Modifier.padding(top = 15.dp))
            Spacer(Modifier.height(15.dp))

            // Header row: "?" circle • logo • "X" circle.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(36.dp, 36.dp, radius = 18.dp)
                Spacer(Modifier.weight(1f))
                SkeletonBlock(140.dp, 34.dp)
                Spacer(Modifier.weight(1f))
                SkeletonBlock(36.dp, 36.dp, radius = 18.dp)
            }
            Spacer(Modifier.height(30.dp))

            // Prize card.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .height(200.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BLOCK_COLOR)
            )
            Spacer(Modifier.height(15.dp))

            // Streak rail: three tiles.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(3) { SkeletonBlock(106.dp, 134.dp, radius = 10.dp) }
            }
            Spacer(Modifier.height(15.dp))

            // Come-back bar — full bleed, like the real one.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(71.dp)
                    .background(BLOCK_COLOR)
            )
            Spacer(Modifier.height(15.dp))

            // CTA pill.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(BLOCK_COLOR)
            )
        }
    }
}

/** ~8% white on gunmetal — present, but never louder than the real content. */
private val BLOCK_COLOR = Color.White.copy(alpha = 0.08f)

private const val PULSE_MS = 900

@Composable
private fun SkeletonBlock(width: Dp, height: Dp, radius: Dp = 6.dp) {
    Box(
        Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(radius))
            .background(BLOCK_COLOR)
    )
}
