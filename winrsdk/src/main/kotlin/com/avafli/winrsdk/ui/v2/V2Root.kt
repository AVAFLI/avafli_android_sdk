package com.avafli.winrsdk.ui.v2

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.ui.ExperienceScreen
import com.avafli.winrsdk.ui.OptOutPhase
import com.avafli.winrsdk.ui.WINRExperienceViewModel
import kotlinx.coroutines.delay

// Root router for the V2 experience, ported from iOS WINRV2ExperienceRoot:
// dimmed host app behind a dark drawer flush to the screen bottom + sides,
// TOP corners rounded 30dp, near-full height (small top reveal below the
// status bar, 2.9.6), slide-up spring-in.

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
    // In-experience legal webview (2.9.4): non-null while Official Rules or
    // the Privacy Policy is presented inside the drawer.
    var legalPage by remember { mutableStateOf<WinrLegalPage?>(null) }
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
        // 2.9.6 (Ryan's device review): near-full-height drawer — the sheet
        // rises to just below the status bar with a small reveal of the host
        // app, replacing the fixed 90% proportion. On tall gesture-nav phones
        // the old 90% (whose bottom slice the nav inset then ate) clipped the
        // capture footer and forced the dashboard to scroll; the goal is that
        // every screen fits WITHOUT scrolling on typical phones (scrolling
        // remains the fallback for very short ones).
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val drawerHeight = screenHeight - statusBarTop - 14.dp

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
                .height(drawerHeight)
                .offset(y = drawerOffset)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(WINRV2Color.gunmetal),
        ) {
            // The gunmetal sheet bleeds under the gesture-nav bar (flush to the
            // physical bottom), but CONTENT never renders beneath it (2.9.6):
            // everything inside — screens, the legal webview, the delete
            // confirmation — is inset above the navigation bars.
            Box(Modifier.fillMaxSize().navigationBarsPadding()) {
                CompositionLocalProvider(LocalWinrLegalOpener provides { legalPage = it }) {
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

                // Legal webview overlay, inside the drawer chrome. The privacy
                // page's winr://delete bridge closes the webview FIRST, then
                // raises the EXISTING opt-out confirmation over the SDK experience
                // (matching iOS/web, 2.9.5) — so Cancel returns the user to the
                // screen they came from, not the privacy page.
                legalPage?.let { page ->
                    WINRV2LegalWebViewScreen(
                        accent = accent,
                        page = page,
                        onDeleteBridge = {
                            legalPage = null
                            viewModel.showOptOutConfirmation()
                        },
                        onClose = { legalPage = null },
                    )
                }

                // Destructive delete confirmation (+ in-flight/failed/deleted
                // states) — root-level since 2.9.4 so the webview bridge can raise
                // it (after the webview closes) over any screen. Done holds the
                // success copy a beat, then dismisses the WHOLE experience.
                if (ui.optOutPhase != OptOutPhase.Idle) {
                    WINRV2OptOutConfirmDialog(
                        phase = ui.optOutPhase,
                        onConfirm = { viewModel.confirmOptOut() },
                        onCancel = { viewModel.cancelOptOut() },
                    )
                }
                LaunchedEffect(ui.optOutPhase) {
                    if (ui.optOutPhase == OptOutPhase.Done) {
                        delay(WINRExperienceViewModel.OPT_OUT_SUCCESS_HOLD_MS)
                        onDismiss()
                    }
                }
            }
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

        // Unrecognized errors keep the FRIENDLY empty state — raw backend/
        // WINRError text is never rendered. Geo-block and session expiry are
        // routed to their dedicated states below.
        is ExperienceScreen.NoActiveGiveaway,
        is ExperienceScreen.Error -> EmptyState(onDismiss)

        is ExperienceScreen.GeoBlocked -> GeoBlockedState(onDismiss)

        is ExperienceScreen.SessionExpired -> SessionExpiredState(
            onRetry = { viewModel.retryAfterSessionExpiry() },
            onDismiss = onDismiss,
        )

        is ExperienceScreen.CodeEntry -> WINRV2CodeEntryScreen(
            accent = accent,
            logoUrl = logoUrl,
            rulesUrl = rulesUrl,
            email = screen.email,
            isVerifying = ui.isVerifyingCode,
            errorText = ui.codeError,
            onSubmit = { viewModel.submitVerificationCode(it) },
            onResend = { viewModel.resendVerificationCode() },
            onInfo = { viewModel.showHowItWorks() },
            onClose = onDismiss,
            // Adoption re-entry (2.9): a parked adoption resumes here on a
            // later open — the raw email is unknown, so the default subtitle
            // (which names it) is replaced with the pick-up-where-you-left-off
            // copy.
            subtitle = if (screen.restaged) V2Strings.ADOPTION_RESTAGE_SUBTITLE else null,
        )

        // Soft email verification (2.7.0): the reused code screen, but dismissible
        // (onCancel) and with verify-specific copy. It gates nothing.
        is ExperienceScreen.EmailVerify -> WINRV2CodeEntryScreen(
            accent = accent,
            logoUrl = logoUrl,
            rulesUrl = rulesUrl,
            email = "",
            isVerifying = ui.isVerifyingCode,
            errorText = ui.codeError,
            onSubmit = { viewModel.submitEmailVerificationCode(it) },
            onResend = { viewModel.resendEmailVerificationCode() },
            onInfo = { viewModel.showHowItWorks() },
            onClose = onDismiss,
            title = V2Strings.VERIFY_EMAIL_TITLE,
            subtitle = V2Strings.VERIFY_EMAIL_SUBTITLE,
            onCancel = { viewModel.cancelEmailVerification() },
        )

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
            // Age-gate label: nested per-screen copy wins over the flat legacy
            // field; the screen builds the sentence from ageGateMinAge (default
            // 18) only when the server sent no verbatim text. Compliance copy —
            // must honor the publisher's configured minimum age.
            ageGateText = ui.sdkConfig?.copy?.emailCapture?.ageGateText
                ?: ui.sdkConfig?.copy?.ageGateText,
            ageGateMinAge = ui.sdkConfig?.ageGateMinAge ?: 18,
            prefilledEmail = viewModel.prefilledEmail(),
            submitError = ui.emailSubmitError,
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
                // Soft email verification (2.7.0): persistent, non-blocking chip.
                showVerifyChip = ui.unverified,
                onVerifyTap = { viewModel.showEmailVerification() },
                pendingClaimEntries = ui.pendingRevealGrant?.entries,
                revealed = ui.claimRevealed,
                notice = ui.dashboardNotice?.message,
                onNoticeRetry = if (ui.dashboardNotice?.retryable == true) {
                    { viewModel.retryDailyClaim() }
                } else null,
            )
        }

        is ExperienceScreen.WinnerClaim -> WINRV2WinnerClaimFlow(
            ui = ui,
            claim = screen.claim,
            accent = accent,
            logoUrl = logoUrl,
            appName = ui.sdkConfig?.appName,
            shareUrl = ui.sdkConfig?.shareUrl,
            placesApiKey = ui.sdkConfig?.placesApiKey,
            claimFormPrefill = viewModel.claimFormPrefill(),
            onContinue = { viewModel.winnerClaimContinue() },
            onSubmit = { form -> viewModel.submitPrizeClaim(form) },
            // Post-submit story attach (2.9): fire-and-forget on BOTH exits —
            // a typed story is never lost to a swipe-away.
            onShareDone = { story ->
                viewModel.attachClaimStory(story)
                viewModel.winnerShareDone()
            },
            onShareClosed = { story ->
                viewModel.attachClaimStory(story)
                onDismiss()
            },
            onClose = onDismiss,
        )

        // 2.9.5: no opt-out plumbing here — the "Privacy choices" fine print
        // is gone; delete is reached through the Privacy Policy webview
        // (legal-links rows / capture inline links) and its winr://delete
        // bridge, with the confirmation rendered at the root.
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

/** Nothing to pitch (or opted out / unrecognized error) — quiet empty state. */
@Composable
private fun EmptyState(onDismiss: () -> Unit) {
    StatusState(
        headline = V2Strings.EMPTY_HEADLINE,
        body = V2Strings.EMPTY_BODY,
        primaryTitle = V2Strings.CLOSE,
        onPrimary = onDismiss,
    )
}

/** US-only geo-fence rejection — the person learns WHY nothing is available. */
@Composable
private fun GeoBlockedState(onDismiss: () -> Unit) {
    StatusState(
        headline = V2Strings.GEO_BLOCKED_HEADLINE,
        body = V2Strings.GEO_BLOCKED_BODY,
        primaryTitle = V2Strings.CLOSE,
        onPrimary = onDismiss,
    )
}

/** Token refresh failed — RETRY re-registers the device and reloads. */
@Composable
private fun SessionExpiredState(onRetry: () -> Unit, onDismiss: () -> Unit) {
    StatusState(
        headline = V2Strings.SESSION_EXPIRED,
        body = null,
        primaryTitle = V2Strings.RETRY,
        onPrimary = onRetry,
        secondaryTitle = V2Strings.CLOSE,
        onSecondary = onDismiss,
    )
}

/**
 * Shared full-drawer status layout: bold headline, optional body, primary
 * pill, optional secondary text action (the EmptyState look, generalized).
 */
@Composable
private fun StatusState(
    headline: String,
    body: String?,
    primaryTitle: String,
    onPrimary: () -> Unit,
    secondaryTitle: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            headline,
            style = WINRV2Font.inter(
                20.sp, FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            ),
        )
        if (body != null) {
            Text(
                body,
                style = WINRV2Font.inter(
                    14.sp,
                    color = WINRV2Color.textTertiary,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        WINRV2PillButton(
            accent = WINRV2Color.winrBlue,
            title = primaryTitle,
            modifier = Modifier.padding(top = 24.dp).width(220.dp),
        ) { onPrimary() }
        if (secondaryTitle != null && onSecondary != null) {
            Text(
                secondaryTitle,
                style = WINRV2Font.inter(14.sp, FontWeight.Bold, color = WINRV2Color.textTertiary),
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSecondary() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
