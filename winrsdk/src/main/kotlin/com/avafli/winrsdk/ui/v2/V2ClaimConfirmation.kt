package com.avafli.winrsdk.ui.v2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.R
import com.avafli.winrsdk.domain.PrizeClaimForm
import com.avafli.winrsdk.domain.WINRClaimDates

// Confirmation ("YOUR PRIZE CLAIM HAS BEEN SUBMITTED"), ported from iOS
// WINRV2ClaimConfirmationView, with the gold OFFICIAL WINNER keepsake card.
// 2.9.3 (Joe's frame 5386:5807): confetti celebration on appearance (the same
// gold drift + one-shot burst the winner splash got), the business-days card
// as a solid gunmetal card with an accent-stroked envelope circle, and the
// OFFICIAL/WINNER labels in the publisher's primary accent.

private object ClaimGold {
    val text = Color(0.72f, 0.55f, 0.16f)
    val border = Color(0.83f, 0.68f, 0.28f)
    val creamTop = Color(1.0f, 0.98f, 0.92f)
    val creamBottom = Color(0.95f, 0.88f, 0.68f)
    val ink = Color(0.1f, 0.09f, 0.07f)
}

@Composable
internal fun WINRV2ClaimConfirmationScreen(
    accent: Color,
    logoUrl: String?,
    form: PrizeClaimForm?,
    claimNumber: String,
    submittedAt: String,
    onDone: () -> Unit,
) {
    // One-shot celebratory burst on appearance — same beat as the splash.
    var burstFinished by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(WINRV2Color.deepCharcoal)) {
        // Confetti layer (same machinery/parameters as the winner splash):
        // gold drift behind the content. Canvas-drawn — never consumes touch.
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
                onClose = onDone,
                modifier = Modifier.padding(top = 18.dp),
            )

            Text(
                "YOUR PRIZE CLAIM HAS BEEN SUBMITTED",
                style = WINRV2Font.inter(
                    26.sp, FontWeight.Black,
                    tracking = (-0.7).sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.padding(top = 20.dp).padding(horizontal = 30.dp),
            )
            Text(
                "Our team is reviewing your information. You’ll receive a confirmation email shortly.",
                style = WINRV2Font.inter(
                    15.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.padding(top = 8.dp).padding(horizontal = 34.dp),
            )

            WINRClaimInfoCard(
                modifier = Modifier.padding(top = 22.dp),
                // Joe's frame: solid gunmetal-family card with a subtle border
                // (not the translucent splash treatment).
                background = WINRV2Color.gunmetal,
                borderColor = Color.White.copy(alpha = 0.12f),
                icon = {
                    // Envelope in a circle with the publisher-accent stroke.
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(WINRV2Color.deepCharcoal)
                            .border(2.dp, accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.winr_mail),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(width = 24.dp, height = 19.dp),
                        )
                    }
                },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "Expect to receive your prize within",
                        style = WINRV2Font.inter(14.sp, color = Color.White),
                    )
                    Text(
                        "3-5 Business Days",
                        style = WINRV2Font.inter(18.sp, FontWeight.Black, color = accent),
                    )
                }
            }

            WinnerCard(
                accent = accent,
                form = form,
                claimNumber = claimNumber,
                submittedAt = submittedAt,
                modifier = Modifier.padding(top = 40.dp).padding(horizontal = 34.dp),
            )

            WINRV2PillButton(
                accent = accent,
                title = "RETURN TO APP",
                modifier = Modifier
                    .padding(top = 30.dp, bottom = 34.dp)
                    .padding(horizontal = 30.dp),
            ) { onDone() }
        }

        // One-shot burst (Joe's Figma GIF) centered on appearance, removed
        // when finished — non-blocking, no layout impact.
        if (!burstFinished) {
            WINRV2GifBurst(
                resId = R.raw.winr_confetti_burst,
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredSize(320.dp),
                onFinished = { burstFinished = true },
            )
        }
    }
}

/**
 * The gold OFFICIAL WINNER keepsake card: cream/gold gradient, small trophy
 * breaking the top border, serif name, award month + claim number.
 */
@Composable
private fun WinnerCard(
    accent: Color,
    form: PrizeClaimForm?,
    claimNumber: String,
    submittedAt: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        0f to ClaimGold.creamTop,
                        1f to ClaimGold.creamBottom,
                    )
                )
                .border(2.dp, ClaimGold.border, RoundedCornerShape(14.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .padding(horizontal = 26.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 2.9.3: the OFFICIAL / WINNER labels take the publisher's
                // primary accent (the card body/typography stays gold-family).
                Text(
                    "OFFICIAL",
                    style = WINRV2Font.inter(16.sp, FontWeight.Black, tracking = 0.5.sp, color = accent),
                )
                Text(
                    "WINNER",
                    style = WINRV2Font.inter(16.sp, FontWeight.Black, tracking = 0.5.sp, color = accent),
                )
            }

            WINRAutoSizeText(
                form?.displayName ?: "Our Winner",
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = ClaimGold.ink,
                ),
                minScale = 0.6f,
                modifier = Modifier.padding(top = 6.dp).padding(horizontal = 20.dp),
            )

            if (form != null && form.city.trim().isNotEmpty()) {
                Text(
                    "${form.city.trim()}, ${form.state.trim()}",
                    style = WINRV2Font.inter(14.sp, color = Color(0.45f, 0.45f, 0.45f)),
                )
            }

            Text(
                "${WINRClaimDates.monthYearDisplay(submittedAt)} • $claimNumber",
                style = WINRV2Font.inter(11.sp, FontWeight.Bold, tracking = 1.1.sp, color = ClaimGold.text),
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )
        }
        // The little trophy sits centered ON the border, breaking it.
        Image(
            painter = painterResource(R.drawable.winr_trophy),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .height(54.dp)
                .offset(y = (-27).dp),
            contentScale = ContentScale.Fit,
        )
    }
}
