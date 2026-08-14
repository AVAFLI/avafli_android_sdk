package com.avafli.winrsdk.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.ui.OptOutPhase
import com.avafli.winrsdk.ui.WINRExperienceViewModel
import kotlinx.coroutines.delay

// How-it-works screen, ported from iOS WINRV2HowItWorksView. The header swaps
// the "?" for a back arrow that returns to the previous screen.

@Composable
internal fun WINRV2HowItWorksScreen(
    accent: Color,
    logoUrl: String?,
    rulesUrl: String? = null,
    day1Entries: Int,
    visitMode: Boolean = false,
    onDone: () -> Unit,
    onClose: () -> Unit,
    optOutPhase: OptOutPhase = OptOutPhase.Idle,
    onPrivacyChoices: () -> Unit = {},
    onDeleteRequested: () -> Unit = {},
    onOptOutConfirm: () -> Unit = {},
    onOptOutCancel: () -> Unit = {},
) {
    // Opt-out success: hold "Your data has been deleted." a beat, then dismiss
    // the WHOLE experience (not just this screen).
    LaunchedEffect(optOutPhase) {
        if (optOutPhase == OptOutPhase.Done) {
            delay(WINRExperienceViewModel.OPT_OUT_SUCCESS_HOLD_MS)
            onClose()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HowItWorksContent(
            accent = accent,
            logoUrl = logoUrl,
            day1Entries = day1Entries,
            visitMode = visitMode,
            onDone = onDone,
            onClose = onClose,
            onPrivacyChoices = onPrivacyChoices,
        )
        when (optOutPhase) {
            OptOutPhase.Idle -> Unit
            // 2.9: the "Privacy choices" surface — policy link + delete
            // action. Delete-my-data moved here from "How it works" proper.
            OptOutPhase.Choices -> PrivacyChoicesDialog(
                rulesUrl = rulesUrl,
                onDelete = onDeleteRequested,
                onCancel = onOptOutCancel,
            )
            else -> OptOutConfirmDialog(
                phase = optOutPhase,
                onConfirm = onOptOutConfirm,
                onCancel = onOptOutCancel,
            )
        }
    }
}

/**
 * The "Privacy choices" surface (2.9): a privacy-policy link plus the
 * "Delete my data & stop participating" action, in the same scrim-plus-card
 * treatment as the delete confirmation it leads to.
 */
@Composable
private fun PrivacyChoicesDialog(
    rulesUrl: String?,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val openPolicy: () -> Unit = {
        rulesUrl?.let { url ->
            try {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                )
            } catch (_: Exception) {
                // No browser available — silently ignore.
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(WINRV2Color.deepCharcoal)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},   // swallow taps inside the card
                )
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                V2Strings.PRIVACY_CHOICES_TITLE,
                style = WINRV2Font.inter(
                    18.sp, FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                ),
            )
            Text(
                V2Strings.PRIVACY_POLICY_LINK,
                style = WINRV2Font.inter(15.sp, FontWeight.Bold, color = Color(0xFF7FB0FF))
                    .copy(textDecoration = TextDecoration.Underline),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = openPolicy)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text(
                V2Strings.OPT_OUT_TITLE,
                style = WINRV2Font.inter(15.sp, FontWeight.Bold, color = WINRClaimStepTheme.errorRed)
                    .copy(textDecoration = TextDecoration.Underline),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text(
                V2Strings.OPT_OUT_CANCEL,
                style = WINRV2Font.inter(14.sp, color = WINRV2Color.textTertiary)
                    .copy(textDecoration = TextDecoration.Underline),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun HowItWorksContent(
    accent: Color,
    logoUrl: String?,
    day1Entries: Int,
    visitMode: Boolean,
    onDone: () -> Unit,
    onClose: () -> Unit,
    onPrivacyChoices: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WINRV2Color.panel),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WINRV2Header(
            logoUrl = logoUrl,
            showsBack = true,
            onBack = onDone,
            onInfo = {},
            onClose = onClose,
            modifier = Modifier.padding(top = 18.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(39.dp)
                .background(Color.White.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "HOW IT WORKS",
                style = WINRV2Font.inter(26.sp, FontWeight.Black, tracking = (-0.78).sp, color = WINRV2Color.gunmetal),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HowItWorksItem(
                    number = "1",
                    title = "ENTER ONCE",
                    body = "Submit your email to receive $day1Entries entries instantly and start your streak.",
                )
                HowItWorksItem(
                    number = "2",
                    title = if (visitMode) "KEEP VISITING" else "VISIT EVERY DAY",
                    body = if (visitMode) {
                        "Simply open the app whenever you like. Your entries are added automatically—no forms or extra steps."
                    } else {
                        "Simply open the app each day. Your entries are added automatically—no forms or extra steps."
                    },
                )
                HowItWorksItem(
                    number = "3",
                    title = "KEEP YOUR STREAK GROWING",
                    body = if (visitMode) {
                        "Earn more entries with every visit. The more you come back, the bigger your rewards!"
                    } else {
                        "Earn more entries with every consecutive visit. The longer your streak, the bigger your daily rewards!"
                    },
                )
            }

            Text(
                if (visitMode) {
                    "Every visit counts - your streak never resets."
                } else {
                    "Don’t miss a day - your streak resets if you do."
                },
                style = WINRV2Font.inter(
                    20.sp, FontWeight.Bold,
                    tracking = (-0.6).sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier
                    .padding(horizontal = 40.dp)
                    .padding(top = 22.dp),
            )

            WINRV2PillButton(
                accent = accent,
                title = "GOT IT - START MY STREAK",
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .padding(top = 20.dp),
            ) { onDone() }

            // Muted privacy opt-out entry point — deliberately quiet: present
            // for those who look for it, invisible to the pitch.
            Text(
                V2Strings.PRIVACY_CHOICES,
                style = WINRV2Font.inter(12.sp, color = WINRV2Color.textTertiary)
                    .copy(textDecoration = TextDecoration.Underline),
                modifier = Modifier
                    .padding(top = 18.dp, bottom = 30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onPrivacyChoices)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * The destructive "Delete my data & stop participating" confirmation (and its
 * in-flight / failed / deleted states) — same scrim-plus-card treatment as the
 * winners dialog.
 */
@Composable
private fun OptOutConfirmDialog(
    phase: OptOutPhase,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val inFlight = phase == OptOutPhase.InFlight
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!inFlight) onCancel() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(WINRV2Color.deepCharcoal)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},   // swallow taps inside the card
                )
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (phase == OptOutPhase.Done) {
                Text(
                    V2Strings.OPT_OUT_SUCCESS,
                    style = WINRV2Font.inter(
                        18.sp, FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Text(
                    V2Strings.OPT_OUT_TITLE,
                    style = WINRV2Font.inter(
                        18.sp, FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    ),
                )
                Text(
                    V2Strings.OPT_OUT_BODY,
                    style = WINRV2Font.inter(
                        14.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    ),
                )
                if (phase is OptOutPhase.Failed) {
                    Text(
                        phase.message,
                        style = WINRV2Font.inter(
                            13.sp,
                            color = WINRClaimStepTheme.errorRed,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
                WINRV2PillButton(
                    accent = WINRClaimStepTheme.errorRed,
                    title = V2Strings.OPT_OUT_CONFIRM,
                    isLoading = inFlight,
                    modifier = Modifier.padding(top = 4.dp),
                ) { onConfirm() }
                Text(
                    V2Strings.OPT_OUT_CANCEL,
                    style = WINRV2Font.inter(14.sp, color = WINRV2Color.textTertiary)
                        .copy(textDecoration = TextDecoration.Underline),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !inFlight, onClick = onCancel)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun HowItWorksItem(number: String, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            "$number.",
            style = WINRV2Font.inter(18.sp, FontWeight.Black, color = Color.White),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = WINRV2Font.inter(18.sp, FontWeight.Black, color = Color.White),
            )
            Text(
                body,
                style = WINRV2Font.inter(16.sp, color = Color.White),
            )
        }
    }
}
