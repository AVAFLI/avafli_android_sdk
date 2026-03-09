package com.avafli.winrsdk.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.WINRBranding

/**
 * Full-size streak day tile (130×160) — matches iOS StreakDayTile.swift.
 */
@Composable
internal fun StreakDayTile(
    dayNumber: Int,
    entries: Int,
    isClaimed: Boolean,
    isToday: Boolean,
    branding: WINRBranding,
    modifier: Modifier = Modifier
) {
    val cr = branding.cornerRadius + 4f

    // Pulse animation for today's pill border
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    // Tile background gradient
    val tileBg = when {
        isToday -> Brush.linearGradient(
            colors = listOf(branding.primaryButtonColor, branding.cardBackgroundColor.copy(alpha = 0.9f))
        )
        isClaimed -> Brush.linearGradient(
            colors = listOf(branding.primaryTextColor.copy(alpha = 0.25f), branding.cardBackgroundColor.copy(alpha = 0.95f))
        )
        else -> Brush.linearGradient(
            colors = listOf(branding.cardBackgroundColor.copy(alpha = 0.5f), branding.cardBackgroundColor.copy(alpha = 0.35f))
        )
    }

    val borderColor = when {
        isToday -> branding.accentGlowColor
        isClaimed -> branding.primaryTextColor.copy(alpha = 0.7f)
        else -> Color.White.copy(alpha = 0.18f)
    }

    val shadowColor = when {
        isToday -> branding.accentGlowColor.copy(alpha = 0.7f)
        isClaimed -> branding.primaryTextColor.copy(alpha = 0.4f)
        else -> Color.Black.copy(alpha = 0.35f)
    }

    val shadowRadius = when {
        isToday -> 8.dp
        isClaimed -> 4.dp
        else -> 0.dp
    }

    val tileScale = if (isToday) 1.08f else 0.96f

    Box(
        modifier = modifier
            .scale(tileScale)
            .width(130.dp)
            .height(160.dp)
            .shadow(shadowRadius, RoundedCornerShape(cr.dp), ambientColor = shadowColor, spotColor = shadowColor)
            .clip(RoundedCornerShape(cr.dp))
            .background(tileBg)
            .border(
                width = if (isToday) 2.5.dp else 1.3.dp,
                color = borderColor,
                shape = RoundedCornerShape(cr.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp, horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Day pill
            Box(contentAlignment = Alignment.Center) {
                // Pulse ring for today
                if (isToday) {
                    Box(
                        modifier = Modifier
                            .scale(pulseScale)
                            .matchParentSize()
                            .border(
                                2.dp,
                                branding.accentGlowColor.copy(alpha = pulseAlpha),
                                RoundedCornerShape(50)
                            )
                    )
                }
                val pillBg = when {
                    isToday -> branding.cardBackgroundColor.copy(alpha = 0.95f)
                    isClaimed -> branding.primaryTextColor.copy(alpha = 0.4f)
                    else -> branding.cardBackgroundColor.copy(alpha = 0.9f)
                }
                Text(
                    text = "DAY $dayNumber",
                    color = if (isToday) branding.primaryButtonTextColor else branding.secondaryTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(pillBg)
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }

            // Entries count
            Text(
                text = "$entries",
                color = when {
                    isToday -> Color.White
                    isClaimed -> branding.primaryTextColor
                    else -> branding.mutedTextColor.copy(alpha = 0.9f)
                },
                fontSize = 30.sp,
                fontWeight = FontWeight.Black
            )

            // "Entries" label
            Text(
                text = "Entries",
                color = when {
                    isToday -> Color.White.copy(alpha = 0.9f)
                    isClaimed -> branding.primaryTextColor.copy(alpha = 0.85f)
                    else -> branding.mutedTextColor.copy(alpha = 0.7f)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            // Status icon
            when {
                isToday -> Text(
                    text = "🔥",
                    fontSize = 22.sp
                )
                isClaimed -> Text(
                    text = "✅",
                    fontSize = 20.sp
                )
                else -> Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = branding.mutedTextColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Compact streak tile (90×115) — matches iOS CompactStreakTile (used in streak dashboard carousel).
 */
@Composable
internal fun CompactStreakTile(
    dayNumber: Int,
    entries: Int,
    isClaimed: Boolean,
    isToday: Boolean,
    branding: WINRBranding,
    modifier: Modifier = Modifier
) {
    val cr = branding.cornerRadius

    // Pulse animation for today
    val infiniteTransition = rememberInfiniteTransition(label = "compactPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "compactPulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "compactPulseAlpha"
    )

    val tileBg = when {
        isToday -> Brush.linearGradient(
            colors = listOf(branding.primaryButtonColor, branding.cardBackgroundColor.copy(alpha = 0.9f))
        )
        isClaimed -> Brush.linearGradient(
            colors = listOf(branding.primaryTextColor.copy(alpha = 0.2f), branding.cardBackgroundColor.copy(alpha = 0.9f))
        )
        else -> Brush.linearGradient(
            colors = listOf(branding.cardBackgroundColor.copy(alpha = 0.45f), branding.cardBackgroundColor.copy(alpha = 0.3f))
        )
    }

    val borderColor = when {
        isToday -> branding.accentGlowColor
        isClaimed -> branding.primaryTextColor.copy(alpha = 0.6f)
        else -> Color.White.copy(alpha = 0.15f)
    }

    val tileScale = if (isToday) 1.05f else 1.0f

    Box(
        modifier = modifier
            .scale(tileScale)
            .width(90.dp)
            .height(115.dp)
            .then(
                if (isToday) Modifier.shadow(
                    12.dp,
                    RoundedCornerShape(cr.dp),
                    ambientColor = branding.accentGlowColor.copy(alpha = 0.7f),
                    spotColor = branding.accentGlowColor.copy(alpha = 0.7f)
                ) else Modifier
            )
            .clip(RoundedCornerShape(cr.dp))
            .background(tileBg)
            .border(
                width = if (isToday) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(cr.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Day pill
            Box(contentAlignment = Alignment.Center) {
                if (isToday) {
                    Box(
                        modifier = Modifier
                            .scale(pulseScale)
                            .matchParentSize()
                            .border(
                                1.5.dp,
                                branding.accentGlowColor.copy(alpha = pulseAlpha),
                                RoundedCornerShape(50)
                            )
                    )
                }
                val pillBg = when {
                    isToday -> branding.cardBackgroundColor.copy(alpha = 0.95f)
                    isClaimed -> branding.primaryTextColor.copy(alpha = 0.35f)
                    else -> branding.cardBackgroundColor.copy(alpha = 0.85f)
                }
                Text(
                    text = "DAY $dayNumber",
                    color = if (isToday) branding.primaryButtonTextColor else branding.secondaryTextColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(pillBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            // Entries count
            Text(
                text = "$entries",
                color = when {
                    isToday -> Color.White
                    isClaimed -> branding.primaryTextColor
                    else -> branding.mutedTextColor.copy(alpha = 0.85f)
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )

            // "Entries" label
            Text(
                text = "Entries",
                color = when {
                    isToday -> Color.White.copy(alpha = 0.85f)
                    isClaimed -> branding.primaryTextColor.copy(alpha = 0.8f)
                    else -> branding.mutedTextColor.copy(alpha = 0.6f)
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )

            // Status icon
            val iconSize = 16.dp
            when {
                isToday -> Text(text = "🔥", fontSize = 16.sp)
                isClaimed -> Text(text = "✅", fontSize = 16.sp)
                else -> Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = branding.mutedTextColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
