package com.avafli.avaflisdk.ui.v2

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
import com.avafli.avaflisdk.R

// Winner splash ("CONGRATULATIONS!"), ported from iOS AvafliV2WinnerSplashView.
// 2.9.3 (Joe's updated frame): a confetti animation layer — the winner-modal
// gold drift plus a one-shot celebration burst on appearance.

@Composable
internal fun AvafliV2WinnerSplashScreen(
    accent: Color,
    logoUrl: String?,
    prizeHeadline: String,
    onContinue: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(AvafliV2Color.deepCharcoal)) {
        // Confetti layer per Joe's frame: the same gold drift as the winner
        // modal, behind the content. Canvas-drawn — never consumes touch.
        AvafliV2ConfettiField(
            modifier = Modifier.matchParentSize(),
            style = AvafliConfettiStyle.Gold,
            count = 26,
            speed = 0.7,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AvafliClaimHeader(
                logoUrl = logoUrl,
                onClose = onClose,
                modifier = Modifier.padding(top = 18.dp),
            )

            TrophyArt(Modifier.padding(top = 4.dp))

            AvafliAutoSizeText(
                "CONGRATULATIONS!",
                style = AvafliV2Font.inter(34.sp, FontWeight.Black, tracking = (-1.0).sp, color = Color.White),
                minScale = 0.7f,
                modifier = Modifier.padding(top = 2.dp).padding(horizontal = 20.dp),
            )
            Text(
                "YOU’RE OUR LATEST WINNER!",
                style = AvafliV2Font.inter(17.sp, FontWeight.Black, tracking = (-0.4).sp, color = accent),
            )

            Text(
                "You’ve won:",
                style = AvafliV2Font.inter(14.sp, color = Color.White),
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
                AvafliAutoSizeText(
                    prizeHeadline,
                    style = AvafliV2Font.inter(
                        28.sp, FontWeight.Black,
                        tracking = (-0.8).sp,
                        color = AvafliV2Color.gunmetal,
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
                style = AvafliV2Font.inter(15.sp, color = Color.White, textAlign = TextAlign.Center),
                modifier = Modifier.padding(top = 16.dp).padding(horizontal = 30.dp),
            )

            AvafliClaimInfoCard(
                modifier = Modifier.padding(top = 14.dp),
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.avafli_shield),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(26.dp),
                    )
                },
            ) {
                Text(
                    "Your information is securely collected and only used to verify your prize and announce you as our winner.",
                    style = AvafliV2Font.inter(13.sp, color = Color.White),
                )
            }

            AvafliV2PillButton(
                accent = accent,
                title = "CONTINUE",
                modifier = Modifier
                    .padding(top = 20.dp, bottom = 30.dp)
                    .padding(horizontal = 30.dp),
            ) { onContinue() }
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
            painter = painterResource(R.drawable.avafli_winner_modal_bg),
            contentDescription = null,
            modifier = Modifier
                .size(width = 300.dp, height = 260.dp)
                .clip(RectangleShape)
                .rotate(-2f),
            contentScale = ContentScale.Crop,
        )
        Image(
            painter = painterResource(R.drawable.avafli_trophy),
            contentDescription = null,
            modifier = Modifier.height(230.dp),
            contentScale = ContentScale.Fit,
        )
        if (!burstFinished) {
            AvafliV2GifBurst(
                resId = R.raw.avafli_confetti_burst,
                modifier = Modifier.requiredSize(320.dp),
                onFinished = { burstFinished = true },
            )
        }
    }
}
