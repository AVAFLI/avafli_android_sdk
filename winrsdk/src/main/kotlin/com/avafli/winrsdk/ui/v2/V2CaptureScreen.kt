package com.avafli.winrsdk.ui.v2

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.onFocusChanged
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import com.avafli.winrsdk.R
import com.avafli.winrsdk.WINRConstants
import com.avafli.winrsdk.domain.Giveaway
import com.avafli.winrsdk.domain.WINRFieldValidation

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
     * Publisher-configured age-gate label. Rendered verbatim when present and
     * non-blank; otherwise the sentence is BUILT from [ageGateMinAge]. A
     * compliance string — 18 is never hardcoded over publisher config.
     */
    ageGateText: String? = null,
    /** Minimum age for the fallback age-gate sentence (default 18). */
    ageGateMinAge: Int = 18,
    /**
     * Partner-authenticated email (WINRUser.email). Well-formed → rendered
     * pre-filled and READ-ONLY; malformed or null → the editable field.
     */
    prefilledEmail: String? = null,
    /** Transport failure of a previous submit — inline near the CTA; the user
     *  stays here and can retry. */
    submitError: String? = null,
    onSubmit: (String, Boolean, Boolean) -> Unit,
    onInfo: () -> Unit,
    onClose: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    // Inline-error gating: the email error shows only after the field was
    // touched (gained focus once, then lost it holding an invalid non-empty
    // value) or after a submit attempt — never while a first entry is still
    // being typed.
    var emailFieldHadFocus by remember { mutableStateOf(false) }
    var emailTouched by remember { mutableStateOf(false) }
    var submitAttempted by remember { mutableStateOf(false) }
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
    val emailValid = lockedEmail != null || WINRFieldValidation.isValidEmail(email)
    val canSubmit = isAdult && emailValid
    val showEmailError = lockedEmail == null && !emailValid && (emailTouched || submitAttempted)

    // 2.9: flat dark background — the same gunmetal the streak dashboard's
    // drawer uses — replacing the blue radial gradient (WINRV2TopGlow).
    Box(Modifier.fillMaxSize().background(WINRV2Color.gunmetal)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // IME never blocks fields (2.9): the keyboard becomes bottom
                // padding so the email field, checkboxes, and CTA all stay
                // reachable by scrolling while typing.
                .imePadding(),
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
                // 2.9.3: "EARN." (with its period) renders in the publisher's
                // primary brand color — the same accent the CTAs use; "VISIT."
                // and "WIN." stay white.
                WINRAutoSizeText(
                    buildAnnotatedString {
                        append("VISIT. ")
                        withStyle(SpanStyle(color = accent)) { append("EARN.") }
                        append(" WIN.")
                    },
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EmailField(
                        lockedEmail ?: email,
                        onValueChange = { email = it },
                        locked = lockedEmail != null,
                        onFocusChanged = { focused ->
                            if (focused) {
                                emailFieldHadFocus = true
                            } else if (emailFieldHadFocus && email.isNotEmpty() &&
                                !WINRFieldValidation.isValidEmail(email)
                            ) {
                                emailTouched = true
                            }
                        },
                    )
                    if (showEmailError) {
                        Text(
                            V2Strings.INVALID_EMAIL,
                            style = WINRV2Font.inter(13.sp, color = Color(0xFFFF6B63)),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                ConsentCheckbox(
                    accent = accent,
                    checked = isAdult,
                    // Server text wins verbatim; otherwise build the sentence
                    // from the publisher's configured minimum age. Never a
                    // hardcoded "18" over server config (compliance).
                    text = ageGateText?.takeIf { it.isNotBlank() }
                        ?: "I confirm I am $ageGateMinAge years of age or older",
                ) { isAdult = !isAdult }
                ConsentCheckbox(
                    accent = accent,
                    checked = wantsMarketing,
                    text = emailConsentText ?: DEFAULT_MARKETING_CONSENT_TEXT,
                ) { wantsMarketing = !wantsMarketing }
                WINRV2PillButton(
                    accent = accent,
                    title = "CLAIM MY $day1Entries ENTRIES",
                    isLoading = isSubmitting,
                    // Tappable even while invalid (only the spinner blocks it):
                    // a submit attempt with a bad email surfaces the inline
                    // error instead of a dead button. The alpha dim still
                    // communicates "not ready".
                    enabled = !isSubmitting,
                    modifier = Modifier.alpha(if (canSubmit) 1f else 0.5f),
                ) {
                    submitAttempted = true
                    if (canSubmit) {
                        onSubmit(lockedEmail ?: email.trim(), isAdult, wantsMarketing)
                    }
                }
                if (submitError != null) {
                    Text(
                        submitError,
                        style = WINRV2Font.inter(13.sp, color = Color(0xFFFF6B63), textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Anchor the legal block to the drawer's bottom (Ryan): the weighted
            // spacer absorbs any free height between the CTA and the footer, so
            // the legal text sits at the bottom padding instead of congested
            // under the button. On short screens or with the IME open the
            // content exceeds the viewport, the spacer collapses to zero, and
            // the column's 18dp spacing remains the minimum gap — everything
            // scrolls via the existing verticalScroll + imePadding behavior,
            // never overlapping the button. Same pattern as the code-entry
            // screen's footer.
            Spacer(Modifier.weight(1f, fill = true))

            Column(
                modifier = Modifier.padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // ONE instance of the legal text (Ryan's call): the sentence itself
                // carries underlined tappable "Official Rules" / "Privacy Policy"
                // spans, replacing the separate OFFICIAL RULES • PRIVACY POLICY
                // links row this screen used to stack beneath it. Official Rules
                // opens rulesUrl; Privacy Policy opens WINRConstants.PRIVACY_URL
                // (ACTION_VIEW → browser). Other screens keep their
                // WINRV2LegalLinks row.
                val context = LocalContext.current
                val openUrl = { url: String? ->
                    url?.let {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                        } catch (_: Exception) {
                            // No browser available — silently ignore, like iOS UIApplication.open.
                        }
                    }
                    Unit
                }
                val linkStyles = TextLinkStyles(
                    style = SpanStyle(
                        color = WINRV2Color.textTertiary,
                        textDecoration = TextDecoration.Underline,
                    ),
                )
                Text(
                    buildAnnotatedString {
                        append("Your email lets us contact you if you win. By entering you agree to the ")
                        withLink(LinkAnnotation.Clickable("rules", linkStyles) { openUrl(rulesUrl) }) {
                            append("Official Rules")
                        }
                        append(" & ")
                        withLink(
                            LinkAnnotation.Clickable("privacy", linkStyles) {
                                openUrl(WINRConstants.PRIVACY_URL)
                            },
                        ) {
                            append("Privacy Policy")
                        }
                    },
                    style = WINRV2Font.inter(12.sp, color = WINRV2Color.textTertiary, textAlign = TextAlign.Center),
                    modifier = Modifier.padding(horizontal = 30.dp),
                )
                Text(
                    "Powered by © WINR Media",
                    style = WINRV2Font.inter(12.sp, color = WINRV2Color.textTertiary),
                )
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    locked: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    // IME never blocks fields (2.9): scroll this row above the keyboard when
    // it gains focus (the short delay lets the IME inset land first).
    val bringIntoView = remember {
        androidx.compose.foundation.relocation.BringIntoViewRequester()
    }
    val focusScope = androidx.compose.runtime.rememberCoroutineScope()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .bringIntoViewRequester(bringIntoView)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            onFocusChanged(state.isFocused)
                            if (state.isFocused) {
                                focusScope.launch {
                                    kotlinx.coroutines.delay(WINR_IME_SETTLE_MS)
                                    bringIntoView.bringIntoView()
                                }
                            }
                        },
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
 * treatment. 2.9.3: tinted the publisher's primary [accent] — checked is an
 * accent fill with a contrasting check (white, or gunmetal over light
 * accents), unchecked an accent-tinted border.
 */
@Composable
private fun ConsentCheckbox(accent: Color, checked: Boolean, text: String, onToggle: () -> Unit) {
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
                drawRoundRect(accent, cornerRadius = corner)
                val check = Path().apply {
                    moveTo(size.width * 0.24f, size.height * 0.52f)
                    lineTo(size.width * 0.43f, size.height * 0.71f)
                    lineTo(size.width * 0.78f, size.height * 0.3f)
                }
                drawPath(
                    check,
                    // Contrast against the publisher's fill: dark check over
                    // light accents, white otherwise.
                    if (accent.luminance() > 0.55f) WINRV2Color.gunmetal else Color.White,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            } else {
                drawRoundRect(
                    accent,
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
 * Verification code entry — one numeric field, auto-submits at 6 digits.
 * Shared by two flows:
 *  - adoption OTP (typed email matches an EXISTING account); and
 *  - soft email verification (2.7.0), via the dashboard "Verify your email"
 *    chip, which overrides [title]/[subtitle] and supplies [onCancel] to make
 *    the screen dismissible (it gates nothing).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    /** Header. Defaults to the adoption copy; overridden for email verification. */
    title: String = "CHECK YOUR EMAIL",
    /** Subtitle. When null, the adoption sentence (referencing [email]) is used. */
    subtitle: String? = null,
    /** When set, renders a dismiss control (soft-verification is not a gate). */
    onCancel: (() -> Unit)? = null,
) {
    var code by remember { mutableStateOf("") }
    // IME never blocks fields (2.9): scroll the code box above the keyboard
    // when it gains focus.
    val bringIntoView = remember {
        androidx.compose.foundation.relocation.BringIntoViewRequester()
    }
    val focusScope = androidx.compose.runtime.rememberCoroutineScope()

    // Flat gunmetal drawer background (2.9.3) — the blue top glow is gone from
    // every screen, matching the capture screen's 2.9 treatment.
    Box(Modifier.fillMaxSize().background(WINRV2Color.gunmetal)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // IME never blocks fields (2.9).
                .imePadding(),
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
                title,
                style = WINRV2Font.inter(28.sp, FontWeight.Black, color = Color.White),
                textAlign = TextAlign.Center,
            )
            Text(
                subtitle ?: (
                    "This email is already part of a WINR streak. Enter the 6-digit " +
                        "code we sent to $email to pick it up on this device."
                ),
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
                        .bringIntoViewRequester(bringIntoView)
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        focusScope.launch {
                                            kotlinx.coroutines.delay(WINR_IME_SETTLE_MS)
                                            bringIntoView.bringIntoView()
                                        }
                                    }
                                },
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

                // Soft-verification only: a dismiss control back to the dashboard.
                // Adoption OTP omits this — that flow completes a required merge.
                if (onCancel != null) {
                    Text(
                        V2Strings.VERIFY_EMAIL_CANCEL,
                        style = WINRV2Font.inter(14.sp, FontWeight.Bold, color = WINRV2Color.textTertiary),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onCancel() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
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
