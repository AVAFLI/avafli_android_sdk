package com.avafli.winrsdk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.WINRBranding
import com.avafli.winrsdk.domain.SdkCopy
import com.avafli.winrsdk.domain.StreakState

/**
 * Full streak dashboard screen — matches iOS StreakDashboardView.swift.
 * Includes hero logo, prize banner, horizontal streak tile carousel,
 * bonus progress pills, and a sticky footer with claim/done button.
 */
@Composable
internal fun StreakDashboard(
    branding: WINRBranding,
    streakState: StreakState,
    entriesToday: Int,
    ladder: List<Int>,
    claimedToday: Boolean,
    campaign: com.avafli.winrsdk.domain.Campaign?,
    sdkCopy: SdkCopy? = null,
    onClaim: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentDay = streakState.currentDay.coerceAtLeast(1)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val scrollState = rememberScrollState()

        // Main scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp)
                .padding(bottom = 120.dp), // space for sticky footer
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // ── Hero logo ──
            WINRLogoView(
                branding = branding,
                useSecondary = true,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .heightIn(max = 140.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Prize banner ──
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = sdkCopy?.welcomeTitle
                        ?: campaign?.prizeValue?.let { "WIN $it!" }
                        ?: "WIN PRIZES!",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = sdkCopy?.welcomeSubtitle
                        ?: "Keep your daily streak alive to unlock more entries.",
                    color = branding.mutedTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Streak tiles carousel ──
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Upcoming rewards",
                    color = branding.secondaryTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ladder.forEachIndexed { index, entries ->
                        val day = index + 1
                        CompactStreakTile(
                            dayNumber = day,
                            entries = entries,
                            isClaimed = day < currentDay || (day == currentDay && claimedToday),
                            isToday = day == currentDay && !claimedToday,
                            branding = branding
                        )
                    }
                }
            }

            // ── Bonus Progress ──
            campaign?.streakConfig?.let { config ->
                Spacer(modifier = Modifier.height(14.dp))
                Column(modifier = Modifier.padding(horizontal = 6.dp)) {
                    Text(
                        text = "Bonus Progress",
                        color = branding.secondaryTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BonusProgressPill(
                            label = "Week",
                            current = streakState.weeklyDaysCompleted,
                            target = config.weeklyBonusThreshold,
                            bonus = config.weeklyBonusEntries,
                            branding = branding,
                            modifier = Modifier.weight(1f)
                        )
                        BonusProgressPill(
                            label = "Month",
                            current = streakState.monthlyDaysCompleted,
                            target = config.monthlyBonusThreshold,
                            bonus = config.monthlyBonusEntries,
                            branding = branding,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Sticky footer ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            branding.backgroundColor.copy(alpha = 0.0f),
                            branding.backgroundColor.copy(alpha = 0.96f)
                        )
                    )
                )
                .padding(horizontal = 14.dp)
                .padding(bottom = 16.dp, top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (claimedToday) {
                // ── Already claimed state ──
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Claimed",
                    tint = branding.accentGlowColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Today's entries claimed!",
                    color = branding.secondaryTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Come back tomorrow to continue your streak.",
                    color = branding.mutedTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(branding.cornerRadius.dp))
                        .background(branding.cardBackgroundColor.copy(alpha = 0.8f))
                        .border(
                            1.dp,
                            branding.accentGlowColor.copy(alpha = 0.4f),
                            RoundedCornerShape(branding.cornerRadius.dp)
                        )
                        .clickable { onDone() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Done",
                        color = branding.secondaryTextColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                // ── Unclaimed state ──
                Text(
                    text = "Day $currentDay reward",
                    color = branding.secondaryTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Claim $entriesToday entries for today's visit to keep your streak alive.",
                    color = branding.mutedTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .height(48.dp)
                        .shadow(
                            14.dp,
                            RoundedCornerShape(branding.cornerRadius.dp),
                            ambientColor = branding.accentGlowColor.copy(alpha = 0.6f),
                            spotColor = branding.accentGlowColor.copy(alpha = 0.6f)
                        )
                        .clip(RoundedCornerShape(branding.cornerRadius.dp))
                        .background(branding.primaryButtonColor)
                        .clickable { onClaim() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = sdkCopy?.claimButtonTitle ?: "Claim $entriesToday Entries",
                        color = branding.primaryButtonTextColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ── Bonus Progress Pill ──

@Composable
private fun BonusProgressPill(
    label: String,
    current: Int,
    target: Int,
    bonus: Int,
    branding: WINRBranding,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(branding.cornerRadius.dp))
            .background(branding.cardBackgroundColor.copy(alpha = 0.5f))
            .border(
                1.dp,
                branding.accentGlowColor.copy(alpha = 0.25f),
                RoundedCornerShape(branding.cornerRadius.dp)
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = branding.mutedTextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$current/$target days",
                color = branding.secondaryTextColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { (current.toFloat() / target.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = branding.accentGlowColor,
            trackColor = branding.cardBackgroundColor,
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (current >= target) {
            Text(
                text = "✓ Bonus earned!",
                color = branding.accentGlowColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        } else {
            Text(
                text = "+$bonus entries",
                color = branding.mutedTextColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
