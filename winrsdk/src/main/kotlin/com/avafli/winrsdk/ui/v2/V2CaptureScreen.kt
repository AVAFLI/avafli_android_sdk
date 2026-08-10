package com.avafli.winrsdk.ui.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.R
import com.avafli.winrsdk.domain.Giveaway

// New-user capture ("VISIT. EARN. WIN."), ported from iOS WINRV2CaptureView.

@Composable
internal fun WINRV2CaptureScreen(
    accent: Color,
    logoUrl: String?,
    rulesUrl: String?,
    giveaway: Giveaway?,
    isSubmitting: Boolean,
    emailConsentText: String? = null,
    /**
     * Partner-authenticated email (WINRUser.email). Well-formed → rendered
     * pre-filled and READ-ONLY; malformed or null → the editable field.
     */
    prefilledEmail: String? = null,
    onSubmit: (String, Boolean, Boolean) -> Unit,
    onInfo: () -> Unit,
    onClose: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    // Age gate requires an affirmative tick; marketing consent never gates the CTA.
    var isAdult by remember { mutableStateOf(false) }
    // Unchecked by default: consent must be an affirmative act (pre-ticked boxes
    // are invalid under GDPR and disfavored by US state regulators).
    var wantsMarketing by remember { mutableStateOf(false) }

    // Shape check only — the server revalidates. Its job is to pick pre-fill vs
    // editable, so a partner bug degrades to the normal typed flow instead of
    // locking a garbage value into a read-only field.
    val lockedEmail = prefilledEmail?.trim()?.lowercase()?.takeIf {
        it.contains("@") && it.contains(".") && it.length in 6..254
    }

    val day1Entries = giveaway?.streakLadder?.firstOrNull() ?: 10
    val canSubmit = isAdult && (lockedEmail != null || (email.contains("@") && email.contains(".")))

    Box(Modifier.fillMaxSize()) {
        WINRV2TopGlow(accent, Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            WINRV2Header(
                logoUrl = logoUrl,
                onInfo = onInfo,
                onClose = onClose,
                modifier = Modifier.padding(top = 18.dp),
            )

            Column(
                modifier = Modifier.padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                WINRAutoSizeText(
                    "VISIT. EARN. WIN.",
                    style = WINRV2Font.inter(40.sp, FontWeight.Black, tracking = (-1.2).sp, color = Color.White),
                    minScale = 0.7f,
                )
                Text(
                    "VISIT DAILY.  EARN ENTRIES.  WIN BIG!",
                    style = WINRV2Font.inter(15.sp, FontWeight.Bold, color = Color.White),
                )
            }

            PrizeStrip(giveaway)

            Column(
                modifier = Modifier.padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                EmailField(lockedEmail ?: email, onValueChange = { email = it }, locked = lockedEmail != null)
                ConsentCheckbox(
                    checked = isAdult,
                    text = "I confirm I am 18 years of age or older",
                ) { isAdult = !isAdult }
                ConsentCheckbox(
                    checked = wantsMarketing,
                    text = emailConsentText ?: DEFAULT_MARKETING_CONSENT_TEXT,
                ) { wantsMarketing = !wantsMarketing }
                WINRV2PillButton(
                    accent = accent,
                    title = "CLAIM MY $day1Entries ENTRIES",
                    isLoading = isSubmitting,
                    enabled = canSubmit && !isSubmitting,
                    modifier = Modifier.alpha(if (canSubmit) 1f else 0.5f),
                ) {
                    onSubmit(lockedEmail ?: email.trim(), isAdult, wantsMarketing)
                }
            }

            Column(
                modifier = Modifier.padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "Your email lets us contact you if you win. By entering you agree to the Official Rules & Privacy Policy",
                    style = WINRV2Font.inter(12.sp, color = WINRV2Color.textTertiary, textAlign = TextAlign.Center),
                    modifier = Modifier.padding(horizontal = 30.dp),
                )
                WINRV2LegalLinks(rulesUrl = rulesUrl, showPoweredBy = true)
            }
        }
    }
}

/**
 * PRIZE-derived white strip (Joe's Day-1 examples):
 * cash → "$1,000.00 CASH PRIZE"; other → "Win a $500 Amazon Gift Card" + value.
 */
@Composable
private fun PrizeStrip(giveaway: Giveaway?) {
    val description = giveaway?.prizeDescription ?: ""
    val value = giveaway?.prizeValue?.toDoubleOrNull()?.toInt() ?: 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val isCash = WINRV2PrizeText.isCash(description)
        WINRAutoSizeText(
            WINRV2PrizeText.stripHeadline(description, value),
            style = WINRV2Font.inter(
                if (isCash) 24.sp else 23.sp,
                FontWeight.Black,
                tracking = (-0.7).sp,
                color = WINRV2Color.gunmetal,
            ),
            minScale = 0.6f,
        )
        // The value subtitle is redundant when the prize name already
        // states the amount ("$500 Amazon Gift Card").
        if (!isCash && WINRV2PrizeText.showsValueLine(description, value)) {
            Text(
                "$${value.winrFormatted()}.00 Value!",
                style = WINRV2Font.inter(16.sp, color = WINRV2Color.gunmetal),
            )
        }
    }
}

@Composable
private fun EmailField(value: String, onValueChange: (String) -> Unit, locked: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.winr_mail),
            contentDescription = null,
            tint = WINRV2Color.gunmetal.copy(alpha = 0.6f),
            modifier = Modifier.size(width = 22.dp, height = 18.dp),
        )
        Box(Modifier.weight(1f)) {
            if (locked) {
                // Read-only but VISIBLE: the user must see exactly which address
                // they are consenting for. Text, not a disabled field, so no
                // keyboard affordance appears.
                Text(
                    value,
                    style = WINRV2Font.inter(16.sp, color = WINRV2Color.gunmetal),
                    maxLines = 1,
                )
            } else {
                if (value.isEmpty()) {
                    Text(
                        "Enter your email address",
                        style = WINRV2Font.inter(16.sp, color = WINRV2Color.gunmetal.copy(alpha = 0.5f)),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = WINRV2Font.inter(16.sp, color = WINRV2Color.gunmetal),
                    singleLine = true,
                    cursorBrush = SolidColor(WINRV2Color.gunmetal),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (locked) {
            Icon(
                painter = painterResource(R.drawable.winr_lock),
                contentDescription = "Email provided by this app",
                tint = WINRV2Color.gunmetal.copy(alpha = 0.45f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * Offline/no-config fallback for the marketing-consent row. In practice the
 * backend populates the `emailConsentText` config key with a publisher-named
 * string ("I agree to receive marketing emails from {PublisherName}") — the
 * name is interpolated SERVER-side, never here.
 */
private const val DEFAULT_MARKETING_CONSENT_TEXT = "I agree to receive marketing emails from this app"

/**
 * The capture screen's checkbox row — shared verbatim by the 18+ age gate and
 * the marketing consent so both have identical box, check, spacing, and text
 * treatment.
 */
@Composable
private fun ConsentCheckbox(checked: Boolean, text: String, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onToggle,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // SF checkmark.square.fill / square equivalent, drawn at 20dp.
        Canvas(Modifier.size(20.dp)) {
            val corner = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
            if (checked) {
                drawRoundRect(Color.White, cornerRadius = corner)
                val check = Path().apply {
                    moveTo(size.width * 0.24f, size.height * 0.52f)
                    lineTo(size.width * 0.43f, size.height * 0.71f)
                    lineTo(size.width * 0.78f, size.height * 0.3f)
                }
                drawPath(
                    check,
                    WINRV2Color.gunmetal,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            } else {
                drawRoundRect(
                    Color.White,
                    cornerRadius = corner,
                    style = Stroke(1.5.dp.toPx()),
                )
            }
        }
        Text(
            text,
            style = WINRV2Font.inter(14.sp, color = Color.White),
        )
    }
}

/**
 * Verification code entry — shown when the typed email matches an EXISTING
 * account and the OTP gate is on. One numeric field, auto-submits at 6 digits.
 */
@Composable
internal fun WINRV2CodeEntryScreen(
    accent: Color,
    logoUrl: String?,
    rulesUrl: String?,
    email: String,
    isVerifying: Boolean,
    errorText: String?,
    onSubmit: (String) -> Unit,
    onResend: () -> Unit,
    onInfo: () -> Unit,
    onClose: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        WINRV2TopGlow(accent, Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            WINRV2Header(
                logoUrl = logoUrl,
                onInfo = onInfo,
                onClose = onClose,
                modifier = Modifier.padding(top = 18.dp),
            )

            Text(
                "CHECK YOUR EMAIL",
                style = WINRV2Font.inter(28.sp, FontWeight.Black, color = Color.White),
                textAlign = TextAlign.Center,
            )
            Text(
                "This email is already part of a WINR streak. Enter the 6-digit " +
                    "code we sent to $email to pick it up on this device.",
                style = WINRV2Font.inter(14.sp, color = Color.White.copy(alpha = 0.75f)),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 26.dp),
            )

            Column(
                modifier = Modifier.padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (code.isEmpty()) {
                            Text(
                                "••••••",
                                style = WINRV2Font.inter(22.sp, FontWeight.Bold, color = WINRV2Color.gunmetal.copy(alpha = 0.4f)),
                            )
                        }
                        BasicTextField(
                            value = code,
                            onValueChange = { new ->
                                val digits = new.filter { it.isDigit() }.take(6)
                                code = digits
                                // Auto-submit on the sixth digit.
                                if (digits.length == 6 && !isVerifying) onSubmit(digits)
                            },
                            textStyle = WINRV2Font.inter(22.sp, FontWeight.Bold, color = WINRV2Color.gunmetal)
                                .copy(textAlign = TextAlign.Center),
                            singleLine = true,
                            cursorBrush = SolidColor(WINRV2Color.gunmetal),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (errorText != null) {
                    Text(
                        errorText,
                        style = WINRV2Font.inter(13.sp, color = Color(0xFFFF6B63)),
                        textAlign = TextAlign.Center,
                    )
                }

                WINRV2PillButton(
                    accent = accent,
                    title = "VERIFY",
                    isLoading = isVerifying,
                    enabled = !isVerifying && code.length == 6,
                    onClick = { if (code.length == 6) onSubmit(code) },
                )

                // Two-tone: the question reads as copy, the underlined action reads
                // as a control.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onResend() }
                        .padding(6.dp),
                ) {
                    Text(
                        "Didn't get it? ",
                        style = WINRV2Font.inter(14.sp, color = Color.White.copy(alpha = 0.65f)),
                    )
                    Text(
                        "Send a new code",
                        style = WINRV2Font.inter(14.sp, FontWeight.Bold, color = Color(0xFF7FB0FF))
                            .copy(textDecoration = TextDecoration.Underline),
                    )
                }

                Spacer(Modifier.weight(1f, fill = true))

                // Same legal footer as the capture screen — one consent flow, one
                // footer; without it the sheet trails off into a void.
                WINRV2LegalLinks(rulesUrl = rulesUrl, showPoweredBy = true)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
