package com.avafli.winrsdk.ui.v2

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.ui.ExperienceScreen
import com.avafli.winrsdk.ui.WINRExperienceViewModel

// Root router for the V2 experience, ported from iOS WINRV2ExperienceRoot:
// dimmed host app behind a dark drawer flush to the screen bottom + sides,
// TOP corners rounded 30dp, ~90% screen height, slide-up spring-in.

@Composable
internal fun WINRV2ExperienceRoot(
    viewModel: WINRExperienceViewModel,
    onDismiss: () -> Unit,
) {
    val ui by viewModel.uiState.collectAsState()
    val accent = winrV2Accent(ui.sdkConfig?.branding?.primaryColor)
    val logoUrl = ui.sdkConfig?.branding?.logoUrl
    val rulesUrl = ui.giveaway?.rulesUrl ?: ui.sdkConfig?.rulesUrl
    val visitMode = ui.giveaway?.streakMode == "visit"

    var drawerAppeared by remember { mutableStateOf(false) }
    var showWinnerModal by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        drawerAppeared = true
        // Same idea for the publisher's remote art: normally already warm (the
        // SDK warms it at registration/refresh), but a drawer opened before
        // that landed gets one more chance to have it decoded before the prize
        // card paints.
        WINRV2ImageWarmer.prewarm(ui.giveaway?.prizeImageUrl)
        WINRV2ImageWarmer.prewarm(logoUrl)
        // Decode Joe's Figma confetti-burst GIF off-main NOW so mounting it at
        // the reveal beat is instant.
        WINRV2GifAssets.prewarm(context)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenHeight = maxHeight

        // Dimmed host app behind the drawer.
        val scrimAlpha by animateFloatAsState(
            if (drawerAppeared) 0.45f else 0f,
            tween(300),
            label = "winrScrim",
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
        )

        // Slide-up spring-in (iOS spring: response 0.45, damping 0.9).
        val drawerOffset by animateDpAsState(
            if (drawerAppeared) 0.dp else screenHeight,
            spring(dampingRatio = 0.9f, stiffness = 195f),
            label = "winrDrawer",
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(screenHeight * 0.90f)
                .offset(y = drawerOffset)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(WINRV2Color.gunmetal),
        ) {
            DrawerContent(
                ui = ui,
                accent = accent,
                logoUrl = logoUrl,
                rulesUrl = rulesUrl,
                visitMode = visitMode,
                viewModel = viewModel,
                onDismiss = onDismiss,
                onWinnerTap = { showWinnerModal = true },
            )
        }

        if (showWinnerModal) {
            ui.giveaway?.latestWinner?.let { winner ->
                WINRV2WinnerModal(accent = accent, winner = winner) {
                    showWinnerModal = false
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    ui: com.avafli.winrsdk.ui.ExperienceUiState,
    accent: Color,
    logoUrl: String?,
    rulesUrl: String?,
    visitMode: Boolean,
    viewModel: WINRExperienceViewModel,
    onDismiss: () -> Unit,
    onWinnerTap: () -> Unit,
) {
    when (val screen = ui.screen) {
        is ExperienceScreen.Loading -> WINRV2LoadingSkeleton()

        is ExperienceScreen.NoActiveGiveaway,
        is ExperienceScreen.Error -> EmptyState(onDismiss)

        is ExperienceScreen.EmailCapture -> WINRV2CaptureScreen(
            accent = accent,
            logoUrl = logoUrl,
            rulesUrl = rulesUrl,
            giveaway = ui.giveaway,
            isSubmitting = ui.isSubmittingEmail,
            // Nested per-screen copy wins over the flat legacy field; the
            // screen falls back to its own default when both are absent. The
            // config KEY stays `emailConsentText` for wire compatibility — it
            // now carries the publisher-named MARKETING consent string.
            emailConsentText = ui.sdkConfig?.copy?.emailCapture?.emailConsentText
                ?: ui.sdkConfig?.copy?.emailConsentText,
            prefilledEmail = viewModel.prefilledEmail(),
            onSubmit = { email, ageConfirmed, marketingConsent ->
                viewModel.submitEmail(
                    email = email,
                    marketingConsent = marketingConsent,
                    ageConfirmed = ageConfirmed,
                )
            },
            onInfo = { viewModel.showHowItWorks() },
            onClose = onDismiss,
        )

        is ExperienceScreen.Streak -> {
            // Predicted-reveal flow (Day 2+): the ViewModel stages a PREDICTED
            // grant BEFORE entering this state and flips claimRevealed one
            // composition beat (~0.15s) after mount, so the celebration is the
            // dashboard's first visible frame — the total springs from the
            // pre-claim baseline to the predicted value while the tile checks
            // off with confetti and the bar opens on the toast. The real claim
            // reconciles the numbers silently when it returns. No claim tap
            // (Joe's prototype).
            // The single pre-reveal baseline frame shows yesterday's numbers:
            // it is what the springs animate FROM.
            val preReveal = !ui.claimRevealed &&
                (ui.pendingRevealGrant != null || !ui.claimedToday)
            val targetTotal =
                if (preReveal) ui.preClaimTotalEntries ?: screen.streakState.totalEntries
                else screen.streakState.totalEntries
            val animatedTotal by animateIntAsState(
                targetTotal,
                spring(dampingRatio = 0.9f, stiffness = 195f),
                label = "winrRevealTotal",
            )
            WINRV2DashboardScreen(
                accent = accent,
                logoUrl = logoUrl,
                rulesUrl = rulesUrl,
                giveaway = ui.giveaway,
                streakDay = screen.streakState.currentDay.coerceAtLeast(1),
                totalEntries = animatedTotal,
                entriesToday = screen.entriesToday,
                ladder = screen.ladder,
                claimedToday = ui.claimedToday,
                onInfo = { viewModel.showHowItWorks() },
                onClose = onDismiss,
                onWinnerTap = onWinnerTap,
                pendingClaimEntries = ui.pendingRevealGrant?.entries,
                revealed = ui.claimRevealed,
            )
        }

        is ExperienceScreen.WinnerClaim -> WINRV2WinnerClaimFlow(
            ui = ui,
            claim = screen.claim,
            accent = accent,
            logoUrl = logoUrl,
            claimFormPrefill = viewModel.claimFormPrefill(),
            onContinue = { viewModel.winnerClaimContinue() },
            onSubmit = { form -> viewModel.submitPrizeClaim(form) },
            onClose = onDismiss,
        )

        is ExperienceScreen.HowItWorks -> WINRV2HowItWorksScreen(
            accent = accent,
            logoUrl = logoUrl,
            day1Entries = ui.giveaway?.streakLadder?.firstOrNull() ?: 10,
            visitMode = visitMode,
            onDone = { viewModel.hideHowItWorks() },
            onClose = onDismiss,
        )
    }
}

/** Nothing to pitch (or opted out / errored) — quiet empty state. */
@Composable
private fun EmptyState(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Nothing to see here yet",
            style = WINRV2Font.inter(20.sp, FontWeight.Bold, color = Color.White),
        )
        Text(
            "Check back soon for your next chance to win!",
            style = WINRV2Font.inter(14.sp, color = WINRV2Color.textTertiary),
            modifier = Modifier.padding(top = 12.dp),
        )
        WINRV2PillButton(
            accent = WINRV2Color.winrBlue,
            title = "CLOSE",
            modifier = Modifier.padding(top = 24.dp).width(220.dp),
        ) { onDismiss() }
    }
}
