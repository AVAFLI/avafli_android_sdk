package com.avafli.winrsdk.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avafli.winrsdk.WINRBranding
import com.avafli.winrsdk.ui.components.*
import com.avafli.winrsdk.ui.theme.WINRTheme

/**
 * Main Compose screen for the WINR experience.
 * Presented as a ModalBottomSheet.
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
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = {
                Surface(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Box(modifier = Modifier.size(width = 40.dp, height = 4.dp))
                }
            },
            shape = RoundedCornerShape(topStart = branding.cornerRadius.dp, topEnd = branding.cornerRadius.dp)
        ) {
            WINRExperienceContent(
                uiState = uiState,
                onClaim = { viewModel.claimDailyEntries() },
                onWatchAd = { viewModel.watchAdAndDouble() },
                onSubmitEmail = { viewModel.submitEmail(it) }
            )
        }
    }
}

@Composable
private fun WINRExperienceContent(
    uiState: ExperienceUiState,
    onClaim: () -> Unit,
    onWatchAd: () -> Unit,
    onSubmitEmail: (String) -> Unit
) {
    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val campaign = uiState.campaign
    if (campaign == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = uiState.error ?: "No active campaign",
                color = MaterialTheme.colorScheme.error
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        ExperienceHeader(
            campaign = campaign,
            totalEntries = uiState.totalEntries
        )

        // Streak Dashboard
        StreakDashboard(
            campaign = campaign,
            streakState = uiState.streakState,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Claim Card
        ExperienceCard(
            onClaim = onClaim,
            isClaiming = uiState.isClaiming,
            hasClaimed = uiState.hasClaimed,
            entriesEarned = uiState.entriesEarned,
            canDouble = uiState.canDouble && !uiState.isDoubled,
            onWatchAd = onWatchAd
        )

        // Error message
        uiState.error?.let { error ->
            AnimatedVisibility(visible = true, enter = fadeIn()) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Bonus section
        BonusEntriesView(
            streakConfig = campaign.streakConfig,
            weeklyDaysCompleted = uiState.streakState.weeklyDaysCompleted,
            monthlyDaysCompleted = uiState.streakState.monthlyDaysCompleted,
            weeklyBonusEarned = uiState.streakState.weeklyBonusEarned,
            monthlyBonusEarned = uiState.streakState.monthlyBonusEarned
        )

        // Email capture
        EmailCaptureView(
            onSubmitEmail = onSubmitEmail,
            isSubmitting = uiState.isSubmittingEmail,
            isSubmitted = uiState.emailSubmitted
        )

        // How it works
        HowItWorksView(rulesUrl = campaign.rulesUrl)

        // Bottom padding for navigation bar
        Spacer(modifier = Modifier.height(16.dp))
    }
}
