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
// Winner prize-claim flow (Joe's Light variant), ported from iOS
// WINRV2Claim.swift: winner splash → single-page claim form → confirmation
// with the OFFICIAL WINNER card. Shown when the giveaway payload carries
// prizeClaim.status == "pending".
//

@Composable
internal fun WINRV2WinnerClaimFlow(
    ui: ExperienceUiState,
    claim: PrizeClaimBlock,
    accent: Color,
    logoUrl: String?,
    claimFormPrefill: PrizeClaimForm,
    onContinue: () -> Unit,
    onSubmit: (PrizeClaimForm) -> Unit,
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

            is WinnerClaimStep.Form -> WINRV2ClaimFormScreen(
                accent = accent,
                logoUrl = logoUrl,
                prefill = claimFormPrefill,
                isSubmitting = ui.isSubmittingClaim,
                submitError = ui.claimSubmitError,
                onSubmit = onSubmit,
                onClose = onClose,
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
