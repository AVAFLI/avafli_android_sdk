package com.avafli.avaflisdk.ui.v2

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.avaflisdk.R
import com.avafli.avaflisdk.domain.GiveawayWinner

/**
 * Winners dialog, ported from iOS AvafliV2WinnerModal: rounded-20 card with a
 * 2dp accent border, gold-sparkle background art + dark gradient + drifting
 * gold confetti, trophy, "WE HAVE A WINNER!", winner pill, awarded date.
 */
@Composable
internal fun AvafliV2WinnerModal(
    accent: Color,
    winner: GiveawayWinner,
    onDismiss: () -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        if (appeared) 1f else 0.9f,
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "winnerScale",
    )
    val alpha by animateFloatAsState(if (appeared) 1f else 0f, label = "winnerAlpha")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 380.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .alpha(alpha)
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},   // swallow taps inside the card
                ),
        ) {
            ModalBackground()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HeaderRow(accent)
                Spacer(Modifier.height(7.dp))
                WinnerSection(accent, winner)
            }
            Box(
                Modifier
                    .matchParentSize()
                    .border(2.dp, accent, RoundedCornerShape(20.dp))
            )
            AvafliV2CircleButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.avafli_close_x),
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

/** Gold-sparkle art behind the content (Figma bg export + gold confetti drift). */
@Composable
private fun BoxScope.ModalBackground() {
    Box(Modifier.matchParentSize().background(AvafliV2Color.deepCharcoal))
    Image(
        painter = painterResource(R.drawable.avafli_winner_modal_bg),
        contentDescription = null,
        modifier = Modifier.matchParentSize().alpha(0.9f),
        contentScale = ContentScale.Crop,
    )
    Box(
        Modifier
            .matchParentSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 0.75f)),
                )
            )
    )
    AvafliV2ConfettiField(
        modifier = Modifier.matchParentSize(),
        style = AvafliConfettiStyle.Gold,
        count = 26,
        speed = 0.7,
    )
}

@Composable
private fun HeaderRow(accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(129.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.avafli_trophy),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 114.dp, height = 153.dp)
                    .rotate(-5.96f),
                contentScale = ContentScale.Fit,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "WE HAVE A",
                style = AvafliV2Font.inter(23.sp, FontWeight.Bold, tracking = (-1.15).sp, color = Color.White),
            )
            AvafliAutoSizeText(
                "WINNER!",
                style = AvafliV2Font.inter(44.sp, FontWeight.Black, tracking = (-2.2).sp, color = accent),
                minScale = 0.7f,
            )
            Text(
                "Congratulations to our latest big winner!",
                style = AvafliV2Font.inter(15.sp, tracking = (-0.75).sp, color = Color.White, textAlign = TextAlign.Center),
                modifier = Modifier.width(160.dp),
            )
        }
    }
}

@Composable
private fun WinnerSection(accent: Color, winner: GiveawayWinner) {
    Column(
        modifier = Modifier.padding(bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "LATEST WINNER:",
            style = AvafliV2Font.inter(19.sp, FontWeight.Bold, color = Color.White),
        )
        WinnerPill(accent, winner)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            winner.awardedAtDisplay?.let { awarded ->
                Text(
                    "This prize awarded on $awarded",
                    style = AvafliV2Font.inter(16.sp, color = Color.White, textAlign = TextAlign.Center),
                )
            }
            Text(
                "All new prize available now! Keep going!",
                style = AvafliV2Font.inter(16.sp, FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center),
            )
        }
    }
}

@Composable
private fun WinnerPill(accent: Color, winner: GiveawayWinner) {
    Row(
        modifier = Modifier
            .height(72.dp)
            .clip(CircleShape)
            .background(AvafliV2Color.gunmetal)
            .border(2.dp, accent, CircleShape)
            .padding(start = 13.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        WinnerAvatar(accent, winner)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                winner.name,
                style = AvafliV2Font.inter(20.sp, FontWeight.Bold, color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            winner.location?.let { location ->
                Text(
                    location,
                    style = AvafliV2Font.inter(20.sp, color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WinnerAvatar(accent: Color, winner: GiveawayWinner) {
    val avatar = rememberAvafliRemoteImage(winner.avatarUrl)
    Box(
        modifier = Modifier.size(51.dp).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar != null) {
            Image(
                bitmap = avatar,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            // Initials fallback.
            Box(
                Modifier.matchParentSize().background(accent.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    winner.name.take(1),
                    style = AvafliV2Font.inter(24.sp, FontWeight.Bold, color = Color.White),
                )
            }
        }
    }
}
