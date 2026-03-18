package com.avafli.winrsdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.WINRBranding
import com.avafli.winrsdk.domain.SdkCopy
import com.avafli.winrsdk.ui.components.*
import com.avafli.winrsdk.ui.theme.WINRTheme

/**
 * Main Compose screen for the WINR experience — matches iOS WINRExperienceView.swift.
 * State-machine driven: Email → Streak → Bonus → Complete.
 * Presented as a ModalBottomSheet with gradient background and floating header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WINRExperienceScreen(
    viewModel: WINRExperienceViewModel,
    branding: WINRBranding,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    WINRTheme(branding = branding) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = Color.Transparent,
            dragHandle = null,
            shape = RoundedCornerShape(topStart = branding.cornerRadius.dp, topEnd = branding.cornerRadius.dp)
        ) {
            WINRExperienceBody(
                uiState = uiState,
                branding = branding,
                viewModel = viewModel,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun WINRExperienceBody(
    uiState: ExperienceUiState,
    branding: WINRBranding,
    viewModel: WINRExperienceViewModel,
    onDismiss: () -> Unit
) {
    val isHowItWorks = uiState.screen is ExperienceScreen.HowItWorks
    val showsInfo = uiState.screen is ExperienceScreen.Streak ||
            uiState.screen is ExperienceScreen.EmailCapture

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Background layer ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            branding.backgroundColor,
                            branding.backgroundColor.copy(alpha = 0.94f)
                        )
                    )
                )
        ) {
            // Radial accent glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                branding.accentGlowColor.copy(alpha = 0.35f),
                                Color.Transparent
                            ),
                            radius = 420f
                        )
                    )
            )
        }

        // ── Main content ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 50.dp) // space under header
        ) {
            when (val screen = uiState.screen) {
                is ExperienceScreen.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = branding.accentGlowColor
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = uiState.sdkCopy?.loading?.text ?: "Loading today's reward…",
                                color = branding.primaryTextColor
                            )
                        }
                    }
                }

                is ExperienceScreen.EmailCapture -> {
                    EmailCaptureView(
                        branding = branding,
                        rulesUrl = uiState.giveaway?.rulesUrl,
                        prizeValue = uiState.giveaway?.prizeValue,
                        sdkCopy = uiState.sdkCopy,
                        heroMedia = viewModel.getSdkMediaForScreen("emailCapture"),
                        onSubmit = { email, marketingConsent -> viewModel.submitEmail(email, marketingConsent) },
                        onSkip = { viewModel.skipEmailCapture() }
                    )
                }

                is ExperienceScreen.Streak -> {
                    StreakDashboard(
                        branding = branding,
                        streakState = screen.streakState,
                        entriesToday = screen.entriesToday,
                        ladder = screen.ladder,
                        claimedToday = uiState.claimedToday,
                        giveaway = uiState.giveaway,
                        sdkCopy = uiState.sdkCopy,
                        onClaim = { viewModel.claimDailyEntries() },
                        onDone = onDismiss
                    )
                }

                is ExperienceScreen.Bonus -> {
                    BonusEntriesView(
                        branding = branding,
                        entries = screen.grant.entries,
                        sdkCopy = uiState.sdkCopy,
                        onClaim = { viewModel.watchAdAndDouble() },
                        onSkip = { viewModel.skipBonus() }
                    )
                }

                is ExperienceScreen.MilestoneCelebration -> {
                    WINRExperienceCard(branding = branding) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            screen.milestone.badge?.let { badge ->
                                Text(text = badge, fontSize = 48.sp)
                            }
                            Text(
                                text = uiState.sdkCopy?.milestone?.title ?: "Milestone Reached!",
                                color = branding.primaryTextColor,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = (uiState.sdkCopy?.milestone?.subtitle ?: "Day {day} streak — +{bonus} bonus entries!")
                                    .replace("{day}", screen.milestone.day.toString())
                                    .replace("{bonus}", screen.milestone.bonusEntries.toString()),
                                color = branding.mutedTextColor,
                                textAlign = TextAlign.Center
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(branding.cornerRadius.dp))
                                    .background(branding.primaryButtonColor)
                                    .clickable { viewModel.dismissMilestoneCelebration() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.sdkCopy?.milestone?.continueButton ?: "Continue",
                                    color = branding.primaryButtonTextColor,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                is ExperienceScreen.Completed -> {
                    WINRExperienceCard(branding = branding) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = uiState.sdkCopy?.completed?.title 
                                    ?: uiState.sdkCopy?.completedTitle 
                                    ?: "Entries Claimed!",
                                color = branding.primaryTextColor,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = (uiState.sdkCopy?.completed?.subtitle 
                                    ?: uiState.sdkCopy?.completedSubtitle
                                    ?: "+{total} entries added to this month's drawing.")
                                    .replace("{total}", screen.grant.totalEntries.toString()),
                                color = branding.mutedTextColor,
                                textAlign = TextAlign.Center
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(branding.cornerRadius.dp))
                                    .background(branding.primaryButtonColor)
                                    .clickable { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.sdkCopy?.completed?.closeButton ?: "Close",
                                    color = branding.primaryButtonTextColor,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                is ExperienceScreen.HowItWorks -> {
                    HowItWorksView(
                        branding = branding,
                        sdkCopy = uiState.sdkCopy,
                        heroMedia = viewModel.getSdkMediaForScreen("howItWorks"),
                        onPrimary = { viewModel.primaryFromHowItWorks() }
                    )
                }

                is ExperienceScreen.Error -> {
                    WINRExperienceCard(branding = branding) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = uiState.sdkCopy?.error?.title ?: "Something went wrong.",
                                color = branding.primaryTextColor,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = uiState.sdkCopy?.error?.subtitle ?: "Please try again later.",
                                color = branding.mutedTextColor
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(branding.cornerRadius.dp))
                                    .background(branding.primaryButtonColor)
                                    .clickable { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.sdkCopy?.error?.closeButton ?: "Close",
                                    color = branding.primaryButtonTextColor,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Header (floating overlay at top) ──
        ExperienceHeader(
            branding = branding,
            showsBack = isHowItWorks,
            showsInfo = showsInfo && !isHowItWorks,
            onBack = { viewModel.hideHowItWorks() },
            onInfo = { viewModel.showHowItWorks() },
            onClose = onDismiss,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 15.dp, vertical = 15.dp)
        )
    }
}
