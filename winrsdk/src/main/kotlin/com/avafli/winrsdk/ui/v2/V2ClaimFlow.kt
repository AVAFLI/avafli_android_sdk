package com.avafli.winrsdk.ui.v2

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.avafli.winrsdk.domain.PrizeClaimBlock
import com.avafli.winrsdk.domain.PrizeClaimForm
import com.avafli.winrsdk.ui.ExperienceUiState
import com.avafli.winrsdk.ui.WinnerClaimStep

//
// Winner prize-claim flow (Joe's stepped Figma design), ported from iOS
// WINRV2Claim.swift: winner splash → 3 form steps + review (V2ClaimSteps.kt)
// → post-submit share screen (2.9) → confirmation with the OFFICIAL WINNER
// card. Shown when the giveaway payload carries prizeClaim.status == "pending".
//

@Composable
internal fun WINRV2WinnerClaimFlow(
    ui: ExperienceUiState,
    claim: PrizeClaimBlock,
    accent: Color,
    logoUrl: String?,
    rulesUrl: String?,
    shareUrl: String?,
    claimFormPrefill: PrizeClaimForm,
    onContinue: () -> Unit,
    onSubmit: (PrizeClaimForm) -> Unit,
    /** DONE on the share screen — carries the typed story (attach + advance). */
    onShareDone: (String) -> Unit,
    /** Close/back FROM the share screen — carries the typed story so the
     *  attach still happens before the drawer dismisses. */
    onShareClosed: (String) -> Unit,
    onClose: () -> Unit,
) {
    Crossfade(
        targetState = ui.winnerClaimStep,
        animationSpec = tween(300),
        label = "winrClaimStep",
    ) { step ->
        when (step) {
            is WinnerClaimStep.Splash -> WINRV2WinnerSplashScreen(
                accent = accent,
                logoUrl = logoUrl,
                prizeHeadline = WINRV2PrizeText.stripHeadline(
                    description = claim.prizeDescription,
                    value = claim.prizeValue.toInt(),
                ),
                onContinue = onContinue,
                onClose = onClose,
            )

            is WinnerClaimStep.Form -> WINRV2ClaimStepsFlow(
                accent = accent,
                logoUrl = logoUrl,
                rulesUrl = rulesUrl,
                claim = claim,
                prefill = claimFormPrefill,
                isSubmitting = ui.isSubmittingClaim,
                submitError = ui.claimSubmitError,
                onSubmit = onSubmit,
                onClose = onClose,
            )

            // 2.9: the claim is already submitted — this screen is optional
            // flourish, and closing it loses nothing (a typed story is still
            // posted via attachClaimStory on BOTH exits).
            is WinnerClaimStep.Share -> WINRV2ClaimShareScreen(
                accent = accent,
                logoUrl = logoUrl,
                claim = claim,
                shareUrl = shareUrl,
                onDone = onShareDone,
                onClose = onShareClosed,
            )

            is WinnerClaimStep.Confirmation -> WINRV2ClaimConfirmationScreen(
                accent = accent,
                logoUrl = logoUrl,
                form = ui.submittedClaimForm,
                claimNumber = step.claimNumber,
                submittedAt = step.submittedAt,
                onDone = onClose,
            )
        }
    }
}
