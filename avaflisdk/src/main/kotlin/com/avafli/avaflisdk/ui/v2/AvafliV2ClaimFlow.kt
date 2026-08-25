package com.avafli.avaflisdk.ui.v2

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.avafli.avaflisdk.domain.PrizeClaimBlock
import com.avafli.avaflisdk.domain.PrizeClaimForm
import com.avafli.avaflisdk.ui.ExperienceUiState
import com.avafli.avaflisdk.ui.WinnerClaimStep

//
// Winner prize-claim flow (Joe's stepped Figma design), ported from iOS
// AvafliV2Claim.swift: winner splash → 3 form steps + review (AvafliV2ClaimSteps.kt)
// → post-submit share screen (2.9) → confirmation with the OFFICIAL WINNER
// card. Shown when the giveaway payload carries prizeClaim.status == "pending".
//

@Composable
internal fun AvafliV2WinnerClaimFlow(
    ui: ExperienceUiState,
    claim: PrizeClaimBlock,
    accent: Color,
    logoUrl: String?,
    /** Publisher's app/brand name (sdkConfig.appName) — likeness consent copy. */
    appName: String?,
    shareUrl: String?,
    /** Google Places key (2.9) — street-field autocomplete; null → off. */
    placesApiKey: String?,
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
        label = "avafliClaimStep",
    ) { step ->
        when (step) {
            is WinnerClaimStep.Splash -> AvafliV2WinnerSplashScreen(
                accent = accent,
                logoUrl = logoUrl,
                prizeHeadline = AvafliV2PrizeText.stripHeadline(
                    description = claim.prizeDescription,
                    value = claim.prizeValue.toInt(),
                ),
                onContinue = onContinue,
                onClose = onClose,
            )

            is WinnerClaimStep.Form -> AvafliV2ClaimStepsFlow(
                accent = accent,
                logoUrl = logoUrl,
                appName = appName,
                placesApiKey = placesApiKey,
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
            is WinnerClaimStep.Share -> AvafliV2ClaimShareScreen(
                accent = accent,
                logoUrl = logoUrl,
                claim = claim,
                shareUrl = shareUrl,
                onDone = onShareDone,
                onClose = onShareClosed,
            )

            is WinnerClaimStep.Confirmation -> AvafliV2ClaimConfirmationScreen(
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
