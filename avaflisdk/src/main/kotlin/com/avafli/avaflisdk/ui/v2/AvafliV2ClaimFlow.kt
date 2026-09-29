package com.avafli.avaflisdk.ui.v2

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.avafli.avaflisdk.domain.PrizeClaimBlock
import com.avafli.avaflisdk.domain.PrizeClaimForm
import com.avafli.avaflisdk.ui.ClaimCodeSend
import com.avafli.avaflisdk.ui.ExperienceUiState
import com.avafli.avaflisdk.ui.WinnerClaimStep

//
// Winner prize-claim flow (Joe's stepped Figma design), ported from iOS
// AvafliV2Claim.swift: winner splash → 3 form steps + review (AvafliV2ClaimSteps.kt)
// → post-submit share screen (2.9) → confirmation with the OFFICIAL WINNER
// card. Shown when the giveaway payload carries prizeClaim.status == "pending".
//
// 3.2.0: while prizeClaim.verification.required is true, the six-digit
// email-ownership code sits between the splash and the form — the same
// code-entry screen the cross-device link uses.
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
    rulesUrl: String?,
    claimFormPrefill: PrizeClaimForm,
    onContinue: () -> Unit,
    // Email-ownership code step (3.2.0).
    onCodeSubmit: (String) -> Unit,
    onCodeResend: () -> Unit,
    onCodeSendRetry: () -> Unit,
    onCodeBack: () -> Unit,
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

            // Paints complete on its first frame (the masked address comes
            // with the block); the send call's progress is the inline status.
            is WinnerClaimStep.Code -> {
                val sendFailure = ui.claimCodeSend as? ClaimCodeSend.Failed
                AvafliV2CodeEntryScreen(
                    accent = accent,
                    logoUrl = logoUrl,
                    rulesUrl = rulesUrl,
                    email = "",
                    isVerifying = ui.isVerifyingCode,
                    errorText = ui.codeError,
                    onSubmit = onCodeSubmit,
                    onResend = onCodeResend,
                    // Never shown: the header carries the back chevron here.
                    onInfo = {},
                    onClose = onClose,
                    subtitle = AvafliV2Strings.claimCodeSubtitle(claim.maskedEmail),
                    onBack = onCodeBack,
                    statusText = when {
                        ui.claimCodeVerified -> AvafliV2Strings.EMAIL_VERIFIED
                        ui.claimCodeSend is ClaimCodeSend.Sending -> AvafliV2Strings.CLAIM_CODE_SENDING
                        ui.claimCodeInfo != null -> ui.claimCodeInfo
                        ui.claimCodeSend is ClaimCodeSend.Sent -> AvafliV2Strings.CLAIM_CODE_SENT
                        else -> null
                    },
                    sendErrorText = sendFailure?.message,
                    onRetry = if (sendFailure?.retryable == true) onCodeSendRetry else null,
                    resendAvailableAtMs = ui.claimCodeResendAtMs,
                    fieldResetSignal = ui.claimCodeFieldReset,
                    showContactHelp = true,
                    oneTimeCode = true,
                )
            }

            is WinnerClaimStep.Form -> AvafliV2ClaimStepsFlow(
                accent = accent,
                logoUrl = logoUrl,
                appName = appName,
                placesApiKey = placesApiKey,
                claim = claim,
                // Back from a detour through the code screen: everything the
                // person had typed, reopened where they left it (review).
                prefill = ui.claimFormDraft ?: claimFormPrefill,
                resumeAtReview = ui.claimFormDraft != null,
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
