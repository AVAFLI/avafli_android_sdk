package com.avafli.winrsdk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.WINRBranding
import com.avafli.winrsdk.domain.ScreenMedia
import com.avafli.winrsdk.domain.SdkCopy

/**
 * "How It Works" screen — matches iOS WINRExperienceHowItWorksView.swift.
 * Full-screen view with numbered step rows, tip card, and sticky CTA.
 */
@Composable
internal fun HowItWorksView(
    branding: WINRBranding,
    sdkCopy: SdkCopy? = null,
    heroMedia: ScreenMedia? = null,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        Triple("📅", 
            sdkCopy?.howItWorks?.step1Title ?: "Visit Daily",
            sdkCopy?.howItWorks?.step1Desc ?: "Open the app each day to claim your daily entries."),
        Triple("🔥", 
            sdkCopy?.howItWorks?.step2Title ?: "Build Your Streak",
            sdkCopy?.howItWorks?.step2Desc ?: "Keep your streak alive — the longer it goes, the more entries you earn each day."),
        Triple("▶️", 
            sdkCopy?.howItWorks?.step3Title ?: "Watch & Double",
            sdkCopy?.howItWorks?.step3Desc ?: "Watch an optional short video to double your daily entries."),
        Triple("🎁", 
            sdkCopy?.howItWorks?.step4Title ?: "Win Prizes",
            sdkCopy?.howItWorks?.step4Desc ?: "Your entries go into the monthly prize drawing. More entries = better odds!"),
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 140.dp) // space for sticky CTA
        ) {
            // ── Hero ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                // Hero media from server config (image or lottie), fallback to emoji
                HeroMedia(
                    media = heroMedia,
                    defaultContent = {
                        Text(text = "🎰", fontSize = 48.sp)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = sdkCopy?.howItWorks?.title ?: "How It Works",
                    color = branding.primaryTextColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = sdkCopy?.howItWorks?.subtitle ?: "Earn entries every day for a chance to win big.",
                    color = branding.mutedTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Steps ──
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                steps.forEachIndexed { index, (icon, title, desc) ->
                    StepRow(
                        number = index + 1,
                        icon = icon,
                        title = title,
                        description = desc,
                        branding = branding
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Tip card ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(branding.cornerRadius.dp))
                    .background(branding.primaryButtonColor.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        branding.primaryButtonColor.copy(alpha = 0.2f),
                        RoundedCornerShape(branding.cornerRadius.dp)
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "💡", fontSize = 20.sp)
                Text(
                    text = sdkCopy?.howItWorks?.tipText ?: "Pro tip: A 5-day streak earns a weekly bonus of extra entries!",
                    color = branding.mutedTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── Sticky CTA ──
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp, top = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .shadow(
                        14.dp,
                        RoundedCornerShape(branding.cornerRadius.dp),
                        ambientColor = branding.accentGlowColor.copy(alpha = 0.5f),
                        spotColor = branding.accentGlowColor.copy(alpha = 0.5f)
                    )
                    .clip(RoundedCornerShape(branding.cornerRadius.dp))
                    .background(branding.primaryButtonColor)
                    .clickable { onPrimary() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = sdkCopy?.howItWorks?.gotItButton ?: "Got It!",
                    color = branding.primaryButtonTextColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Step Row ──

@Composable
private fun StepRow(
    number: Int,
    icon: String,
    title: String,
    description: String,
    branding: WINRBranding
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(branding.cornerRadius.dp))
            .background(branding.cardBackgroundColor.copy(alpha = 0.5f))
            .border(
                1.dp,
                branding.cardBorderColor.copy(alpha = 0.5f),
                RoundedCornerShape(branding.cornerRadius.dp)
            )
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Number badge
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            branding.primaryButtonColor,
                            branding.primaryButtonColor.copy(alpha = 0.7f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                color = branding.primaryButtonTextColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
        }

        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = icon, fontSize = 14.sp)
                Text(
                    text = title,
                    color = branding.primaryTextColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = branding.mutedTextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
