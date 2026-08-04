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
import androidx.compose.material3.CircularProgressIndicator
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
        // Decode Joe's Figma reveal GIFs off-main NOW so mounting them at the
        // reveal beat is instant (the 47-frame tile GIF costs ~0.7s to decode).
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
        is ExperienceScreen.Loading -> LoadingState()

        is ExperienceScreen.NoActiveGiveaway,
        is ExperienceScreen.Error -> EmptyState(onDismiss)

        is ExperienceScreen.EmailCapture -> WINRV2CaptureScreen(
            accent = accent,
            logoUrl = logoUrl,
            rulesUrl = rulesUrl,
            giveaway = ui.giveaway,
            isSubmitting = ui.isSubmittingEmail,
            onSubmit = { email -> viewModel.submitEmail(email, marketingConsent = true) },
            onInfo = { viewModel.showHowItWorks() },
            onClose = onDismiss,
        )

        is ExperienceScreen.Streak -> {
            // Auto-reveal flow (Day 2+): the dashboard mounts pinned to
            // yesterday's numbers (streak N-1, pre-claim total, READY tile),
            // then the ViewModel flips claimRevealed on its own a beat after
            // the claim response is staged — the total springs to the
            // post-claim value while the tile checks off with confetti.
            // No claim tap (Joe's Slice prototype).
            // Pinned from the FIRST frame: while today is unclaimed OR the
            // reveal hasn't played, show yesterday's numbers. Without the
            // claimedToday clause there's a flash of the raw post-claim
            // server state during the network round-trip, and elements flip
            // at different times.
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

        is ExperienceScreen.DailyConfirmed -> Box(Modifier.fillMaxSize()) {
            // Dashboard behind + celebration modal on top. Real blur on 31+,
            // desaturate + extra dim below (see winrCelebrationBackdrop).
            Box(Modifier.fillMaxSize().winrCelebrationBackdrop()) {
                WINRV2DashboardScreen(
                    accent = accent,
                    logoUrl = logoUrl,
                    rulesUrl = rulesUrl,
                    giveaway = ui.giveaway,
                    streakDay = ui.displayStreakDay,
                    totalEntries = screen.totalEntries,
                    entriesToday = screen.grant.entries,
                    ladder = ui.giveaway?.streakLadder?.takeIf { it.isNotEmpty() }
                        ?: listOf(1, 2, 3, 5, 8, 13, 21),
                    claimedToday = true,
                    onInfo = { viewModel.showHowItWorks() },
                    onClose = onDismiss,
                )
            }
            WINRV2CelebrationModal(
                accent = accent,
                streakDay = ui.displayStreakDay,
                earnedEntries = screen.grant.entries,
                nextEntries = WINRV2Ladder.entries(
                    day = ui.displayStreakDay + 1,
                    ladder = ui.giveaway?.streakLadder?.takeIf { it.isNotEmpty() }
                        ?: listOf(1, 2, 3, 5, 8, 13, 21),
                    milestones = ui.giveaway?.milestones,
                ),
                visitMode = visitMode,
                // Day-1 modal is the reveal for brand-new streaks; GOT IT closes
                // the whole experience until the next day's open.
                onDismiss = onDismiss,
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

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
        Text(
            "Loading…",
            style = WINRV2Font.inter(14.sp, color = WINRV2Color.textTertiary),
            modifier = Modifier.padding(top = 16.dp),
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
