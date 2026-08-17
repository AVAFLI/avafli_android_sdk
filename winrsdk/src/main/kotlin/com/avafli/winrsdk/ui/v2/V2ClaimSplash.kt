package com.avafli.winrsdk.ui.v2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.R

// Winner splash ("CONGRATULATIONS!"), ported from iOS WINRV2WinnerSplashView.
// 2.9.3 (Joe's updated frame): a confetti animation layer — the winner-modal
// gold drift plus a one-shot celebration burst on appearance — and the thin
// "WINR MEDIA PRIZE CLAIM" attribution strip pinned to the very bottom.

@Composable
internal fun WINRV2WinnerSplashScreen(
    accent: Color,
    logoUrl: String?,
    prizeHeadline: String,
    onContinue: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(WINRV2Color.deepCharcoal)) {
        // Confetti layer per Joe's frame: the same gold drift as the winner
        // modal, behind the content. Canvas-drawn — never consumes touch.
        WINRV2ConfettiField(
            modifier = Modifier.matchParentSize(),
            style = WINRConfettiStyle.Gold,
            count = 26,
            speed = 0.7,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WINRClaimHeader(
                logoUrl = logoUrl,
                onClose = onClose,
                modifier = Modifier.padding(top = 18.dp),
            )

            TrophyArt(Modifier.padding(top = 4.dp))

            WINRAutoSizeText(
                "CONGRATULATIONS!",
                style = WINRV2Font.inter(34.sp, FontWeight.Black, tracking = (-1.0).sp, color = Color.White),
                minScale = 0.7f,
                modifier = Modifier.padding(top = 2.dp).padding(horizontal = 20.dp),
            )
            Text(
                "YOU’RE OUR LATEST WINNER!",
                style = WINRV2Font.inter(17.sp, FontWeight.Black, tracking = (-0.4).sp, color = accent),
            )

            Text(
                "You’ve won:",
                style = WINRV2Font.inter(14.sp, color = Color.White),
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
            )

            // Full-width white strip with the prize-derived headline
            // (same derivation as the Day-1 capture strip).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                WINRAutoSizeText(
                    prizeHeadline,
                    style = WINRV2Font.inter(
                        28.sp, FontWeight.Black,
                        tracking = (-0.8).sp,
                        color = WINRV2Color.gunmetal,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 2,
                    minScale = 0.6f,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Text(
                "To process your prize, we just need a few details.",
                style = WINRV2Font.inter(15.sp, color = Color.White, textAlign = TextAlign.Center),
                modifier = Modifier.padding(top = 16.dp).padding(horizontal = 30.dp),
            )

            WINRClaimInfoCard(
                modifier = Modifier.padding(top = 14.dp),
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.winr_shield),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(26.dp),
                    )
                },
            ) {
                Text(
                    "Your information is securely collected and only used to verify your prize and announce you as our winner.",
                    style = WINRV2Font.inter(13.sp, color = Color.White),
                )
            }

            WINRV2PillButton(
                accent = accent,
                title = "CONTINUE",
                modifier = Modifier
                    // Extra bottom room so the pinned attribution strip never
                    // covers the CTA at full scroll.
                    .padding(top = 20.dp, bottom = 68.dp)
                    .padding(horizontal = 30.dp),
            ) { onContinue() }
        }

        // Thin bottom attribution strip, pinned to the very bottom (Joe's
        // frame: white strip, centered dark caption).
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "WINR MEDIA PRIZE CLAIM",
                style = WINRV2Font.inter(
                    12.sp, FontWeight.SemiBold,
                    tracking = 1.2.sp,
                    color = WINRV2Color.gunmetal,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    }
}

/** Trophy over the gold-sparkle art (bundled winner-modal-bg + trophy). */
@Composable
private fun TrophyArt(modifier: Modifier = Modifier) {
    // One-shot celebratory burst on appearance — Joe's actual Figma GIF, the
    // same beat as the Day 2+ tile reveal: mounts with the splash, plays ONCE
    // over the trophy, and is REMOVED when it finishes. Non-blocking (the
    // hosting view never consumes touch); larger than the art but must not
    // affect layout (requiredSize draws past the bounds).
    var burstFinished by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.height(280.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.winr_winner_modal_bg),
            contentDescription = null,
            modifier = Modifier
                .size(width = 300.dp, height = 260.dp)
                .clip(RectangleShape)
                .rotate(-2f),
            contentScale = ContentScale.Crop,
        )
        Image(
            painter = painterResource(R.drawable.winr_trophy),
            contentDescription = null,
            modifier = Modifier.height(230.dp),
            contentScale = ContentScale.Fit,
        )
        if (!burstFinished) {
            WINRV2GifBurst(
                resId = R.raw.winr_confetti_burst,
                modifier = Modifier.requiredSize(320.dp),
                onFinished = { burstFinished = true },
            )
        }
    }
}
