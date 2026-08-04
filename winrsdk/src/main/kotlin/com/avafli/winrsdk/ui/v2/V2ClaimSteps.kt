package com.avafli.winrsdk.ui.v2

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.R
import com.avafli.winrsdk.domain.PrizeClaimBlock
import com.avafli.winrsdk.domain.PrizeClaimForm
import com.avafli.winrsdk.ui.v2.WINRClaimStepTheme.fieldBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//
// Root of the stepped prize-claim form (Joe's Figma design), ported from iOS
// WINRV2ClaimStepsFlow.swift: a persistent gold-sparkle backdrop + header +
// animated step indicator, with the four form steps and the review screen
// sliding horizontally beneath them (push left on advance, push right on back).
//

/**
 * The five screens of the stepped form. [indicatorStep] is the 1-based step
 * number (review has no "STEP N OF 4" row, matching the SUBMIT frame).
 */
internal enum class WINRClaimFlowStep(val indicatorStep: Int?) {
    One(1), Two(2), Three(3), Four(4), Review(null)
}

@Composable
internal fun WINRV2ClaimStepsFlow(
    accent: Color,
    logoUrl: String?,
    claim: PrizeClaimBlock,
    prefill: PrizeClaimForm,
    isSubmitting: Boolean,
    submitError: String?,
    onSubmit: (PrizeClaimForm) -> Unit,
    onClose: () -> Unit,
) {
    // Form + photo preview live at flow level so every step keeps its values
    // when the user navigates back and forth.
    var form by remember { mutableStateOf(prefill) }
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    var step by remember { mutableStateOf(WINRClaimFlowStep.One) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // System photo picker (no runtime permission needed) — the task's UPLOAD
    // PHOTO and TAKE PHOTO affordances both open it on Android; the SDK adds
    // no camera permission of its own.
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        photo = null
        form = form.copy(photoBase64 = null)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                WINRClaimPhoto.loadBitmap(context, uri)?.let { bmp ->
                    bmp to WINRClaimPhoto.base64Jpeg(bmp)
                }
            }
            if (result != null) {
                photo = result.first.asImageBitmap()
                form = form.copy(photoBase64 = result.second)
            }
        }
    }
    val pickPhoto: () -> Unit = {
        photoLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    /** "I just won {prize} in {app}!" — the step-4 social share line. */
    val shareLine = remember(claim) {
        val prize = WINRV2PrizeText.stripHeadline(
            description = claim.prizeDescription,
            value = claim.prizeValue.toInt(),
        )
        val app = try {
            context.applicationInfo.loadLabel(context.packageManager).toString()
        } catch (_: Exception) {
            ""
        }
        if (app.isNotBlank()) "I just won $prize in $app!" else "I just won $prize!"
    }

    Box(Modifier.fillMaxSize().background(WINRV2Color.deepCharcoal)) {
        // Gold-sparkle full-bleed backdrop fading into the dark body, per the
        // frames (406dp tall, transparent → deepCharcoal).
        Image(
            painter = painterResource(R.drawable.winr_winner_modal_bg),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(406.dp)
                .align(Alignment.TopCenter),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(406.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0.05f to WINRV2Color.deepCharcoal.copy(alpha = 0.1f),
                        0.6f to WINRV2Color.deepCharcoal.copy(alpha = 0.6f),
                        1f to WINRV2Color.deepCharcoal,
                    )
                )
        )

        Column(Modifier.fillMaxSize()) {
            WINRClaimHeader(
                logoUrl = logoUrl,
                onClose = onClose,
                modifier = Modifier.padding(top = 18.dp),
                showsBack = step != WINRClaimFlowStep.One,
                onBack = {
                    WINRClaimFlowStep.entries.getOrNull(step.ordinal - 1)?.let { step = it }
                },
            )

            AnimatedVisibility(
                visible = step.indicatorStep != null,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
            ) {
                WINRClaimStepIndicator(
                    accent = accent,
                    current = step.indicatorStep ?: 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            // Steps push left when advancing and right when going back.
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val advancing = targetState.ordinal > initialState.ordinal
                    if (advancing) {
                        slideInHorizontally(tween(300)) { it } togetherWith
                            slideOutHorizontally(tween(300)) { -it }
                    } else {
                        slideInHorizontally(tween(300)) { -it } togetherWith
                            slideOutHorizontally(tween(300)) { it }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label = "winrClaimStep",
            ) { current ->
                when (current) {
                    WINRClaimFlowStep.One -> WINRClaimStep1(
                        accent = accent,
                        form = form,
                        onForm = { form = it },
                        maskedEmail = claim.maskedEmail,
                        onContinue = { step = WINRClaimFlowStep.Two },
                    )

                    WINRClaimFlowStep.Two -> WINRClaimStep2(
                        accent = accent,
                        form = form,
                        onForm = { form = it },
                        onContinue = { step = WINRClaimFlowStep.Three },
                    )

                    WINRClaimFlowStep.Three -> WINRClaimStep3(
                        accent = accent,
                        photo = photo,
                        onPickPhoto = pickPhoto,
                        onContinue = { step = WINRClaimFlowStep.Four },
                    )

                    WINRClaimFlowStep.Four -> WINRClaimStep4(
                        accent = accent,
                        form = form,
                        onForm = { form = it },
                        shareLine = shareLine,
                        onContinue = { step = WINRClaimFlowStep.Review },
                    )

                    WINRClaimFlowStep.Review -> WINRClaimReview(
                        accent = accent,
                        form = form,
                        onForm = { form = it },
                        isSubmitting = isSubmitting,
                        submitError = submitError,
                        onSubmit = onSubmit,
                    )
                }
            }
        }
    }
}

// ── "STEP N OF 4" + progress dots ──

/**
 * "STEP N OF 4" + the row of 4 dots connected by accent lines: filled with
 * the accent up to the current step, outlined after it.
 */
@Composable
internal fun WINRClaimStepIndicator(
    accent: Color,
    current: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics { contentDescription = "Step $current of 4" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "STEP $current OF 4",
            style = WINRV2Font.inter(17.sp, FontWeight.SemiBold, tracking = (-0.85).sp, color = Color.White),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (index in 1..4) {
                StepDot(accent = accent, filled = index <= current)
                if (index < 4) {
                    Box(
                        Modifier
                            .width(29.dp)
                            .height(1.5.dp)
                            .background(accent)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepDot(accent: Color, filled: Boolean) {
    val fill by animateFloatAsState(if (filled) 1f else 0f, tween(300), label = "winrStepDot")
    Box(
        Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(WINRV2Color.deepCharcoal.copy(alpha = 0.6f))
            .background(accent.copy(alpha = fill))
            .border(1.5.dp, accent, CircleShape)
    )
}

// ── Page scaffold ──

/**
 * One step's scrollable page: Inter-Black 27 title, Inter-Medium 18 subtitle,
 * the step's content, and the accent CONTINUE/SUBMIT pill (disabled at 50%
 * opacity until the step validates). [footer] renders below the CTA (the
 * review screen's lock note).
 */
@Composable
private fun WINRClaimStepPage(
    accent: Color,
    title: String,
    subtitle: String? = null,
    ctaTitle: String = "CONTINUE",
    ctaEnabled: Boolean = true,
    ctaLoading: Boolean = false,
    onCTA: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = WINRV2Font.inter(
                27.sp, FontWeight.Black,
                tracking = (-0.81).sp,
                color = Color.White,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.padding(top = 24.dp),
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = WINRV2Font.inter(
                    18.sp, FontWeight.Medium,
                    tracking = (-0.54).sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.padding(top = 7.dp),
            )
        }

        content()

        WINRV2PillButton(
            accent = accent,
            title = ctaTitle,
            isLoading = ctaLoading,
            enabled = ctaEnabled && !ctaLoading,
            modifier = Modifier
                .padding(top = 21.dp)
                .padding(horizontal = 12.dp)
                .alpha(if (ctaEnabled) 1f else 0.5f),
        ) { onCTA() }

        footer?.invoke()

        Spacer(Modifier.height(34.dp))
    }
}

// ── Step 1: TELL US ABOUT YOURSELF ──

@Composable
private fun WINRClaimStep1(
    accent: Color,
    form: PrizeClaimForm,
    onForm: (PrizeClaimForm) -> Unit,
    maskedEmail: String?,
    onContinue: () -> Unit,
) {
    WINRClaimStepPage(
        accent = accent,
        title = "TELL US ABOUT YOURSELF",
        subtitle = "We'll use this information to verify your prize and personalize your winner announcement.",
        ctaEnabled = form.isStep1Valid,
        onCTA = onContinue,
    ) {
        Column(
            modifier = Modifier
                .padding(top = 34.dp)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(21.dp),
        ) {
            WINRClaimStepField("First Name", form.firstName) { onForm(form.copy(firstName = it)) }
            WINRClaimStepField("Last Name (we will only show your last initial)", form.lastName) {
                onForm(form.copy(lastName = it))
            }
            // The winning email lives server-side (the SDK never stores the
            // raw address) and the claim is keyed to the account — shown
            // locked, masked by the backend for recognition.
            WINRClaimStepLockedField(
                label = "Winning Email Address (cannot be changed)",
                value = maskedEmail ?: "On file with your winning entry",
            )
            WINRClaimStepField(
                "Phone Number (optional)", form.phone,
                keyboardType = KeyboardType.Phone,
            ) { onForm(form.copy(phone = it)) }
        }
    }
}

// ── Step 2: WHERE SHOULD WE SEND YOUR PRIZE? ──

@Composable
private fun WINRClaimStep2(
    accent: Color,
    form: PrizeClaimForm,
    onForm: (PrizeClaimForm) -> Unit,
    onContinue: () -> Unit,
) {
    WINRClaimStepPage(
        accent = accent,
        title = "WHERE SHOULD WE\nSEND YOUR PRIZE?",
        ctaEnabled = form.isStep2Valid,
        onCTA = onContinue,
    ) {
        Column(
            modifier = Modifier
                .padding(top = 28.dp)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(21.dp),
        ) {
            WINRClaimStepField("Street Address", form.street) { onForm(form.copy(street = it)) }
            WINRClaimStepField("Apartment, Suite, etc. (optional)", form.apt) {
                onForm(form.copy(apt = it))
            }
            WINRClaimStepField("City", form.city) { onForm(form.copy(city = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Box(Modifier.weight(1f)) {
                    WINRClaimStepMenuField(
                        label = "State",
                        options = PrizeClaimForm.usStates,
                        selection = form.state,
                    ) { onForm(form.copy(state = it)) }
                }
                Box(Modifier.width(101.dp)) {
                    WINRClaimStepField(
                        "Zip Code", form.zip,
                        keyboardType = KeyboardType.Number,
                    ) { onForm(form.copy(zip = it.filter { c -> c.isDigit() }.take(5))) }
                }
            }
            // US-only sweepstakes — the country row renders like the frame's
            // dropdown but is fixed.
            WINRClaimStepLockedField(
                label = "Country",
                value = form.country,
                dimmed = false,
                showsChevron = true,
            )
        }
    }
}

// ── Step 3: SHOW OFF YOUR WIN! ──

@Composable
private fun WINRClaimStep3(
    accent: Color,
    photo: ImageBitmap?,
    onPickPhoto: () -> Unit,
    onContinue: () -> Unit,
) {
    WINRClaimStepPage(
        accent = accent,
        title = "SHOW OFF YOUR WIN!",
        subtitle = "Upload a photo we'd be proud to\nfeature as one of our winners.",
        onCTA = onContinue,
    ) {
        Column(
            modifier = Modifier.padding(top = 26.dp, bottom = 17.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            ClaimAvatar(accent = accent, photo = photo, onTap = onPickPhoto)

            Column(
                modifier = Modifier.width(277.dp).padding(top = 7.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ClaimPhotoButton(title = "UPLOAD PHOTO", icon = { UploadGlyph() }, onClick = onPickPhoto)
                ClaimPhotoButton(title = "TAKE PHOTO", icon = { CameraGlyph(20.dp) }, onClick = onPickPhoto)
            }

            Text(
                "Your photo may appear in our Winner Gallery,\nsocial media, and promotional materials.",
                style = WINRV2Font.inter(12.sp, color = Color.White, textAlign = TextAlign.Center),
            )
        }
    }
}

/**
 * 242dp circular preview with the 2dp accent ring and the 80dp camera badge
 * breaking the bottom-right edge (tappable — same as the photo buttons).
 */
@Composable
private fun ClaimAvatar(accent: Color, photo: ImageBitmap?, onTap: () -> Unit) {
    Box(Modifier.size(248.dp)) {
        Box(
            modifier = Modifier
                .size(242.dp)
                .clip(CircleShape)
                .background(WINRV2Color.gunmetal)
                .border(2.dp, accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Image(
                    bitmap = photo,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                PersonGlyph(Modifier.size(120.dp))
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(80.dp)
                .clip(CircleShape)
                .background(WINRV2Color.deepCharcoal)
                .border(2.2.dp, accent, CircleShape)
                .clickable(onClick = onTap)
                .semantics { contentDescription = "Take photo" },
            contentAlignment = Alignment.Center,
        ) {
            CameraGlyph(32.dp)
        }
    }
}

@Composable
private fun ClaimPhotoButton(title: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(47.dp)
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(start = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        icon()
        Text(
            title,
            style = WINRV2Font.inter(22.sp, FontWeight.SemiBold, tracking = (-0.66).sp, color = Color.White),
        )
    }
}

/** SF "person.fill" equivalent: filled head + shoulders, white 18%. */
@Composable
private fun PersonGlyph(modifier: Modifier = Modifier) {
    val tint = Color.White.copy(alpha = 0.18f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(tint, radius = 0.22f * w, center = androidx.compose.ui.geometry.Offset(0.5f * w, 0.3f * h))
        drawPath(
            Path().apply {
                moveTo(0.12f * w, 0.92f * h)
                quadraticTo(0.5f * w, 0.44f * h, 0.88f * w, 0.92f * h)
                close()
            },
            tint,
        )
    }
}

/** SF "camera.fill" equivalent, drawn white at [size]. */
@Composable
private fun CameraGlyph(size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        // Body with the top bump.
        drawPath(
            Path().apply {
                moveTo(0.05f * w, 0.3f * h)
                lineTo(0.3f * w, 0.3f * h)
                lineTo(0.38f * w, 0.16f * h)
                lineTo(0.62f * w, 0.16f * h)
                lineTo(0.7f * w, 0.3f * h)
                lineTo(0.95f * w, 0.3f * h)
                lineTo(0.95f * w, 0.86f * h)
                lineTo(0.05f * w, 0.86f * h)
                close()
            },
            Color.White,
        )
        // Lens (punched in the badge's dark background color).
        drawCircle(
            WINRV2Color.deepCharcoal,
            radius = 0.17f * w,
            center = androidx.compose.ui.geometry.Offset(0.5f * w, 0.57f * h),
        )
    }
}

/** SF "square.and.arrow.up" equivalent (share/upload), stroked white. */
@Composable
private fun UploadGlyph() {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Open-top square.
        drawPath(
            Path().apply {
                moveTo(0.32f * w, 0.36f * h)
                lineTo(0.12f * w, 0.36f * h)
                lineTo(0.12f * w, 0.94f * h)
                lineTo(0.88f * w, 0.94f * h)
                lineTo(0.88f * w, 0.36f * h)
                lineTo(0.68f * w, 0.36f * h)
            },
            Color.White,
            style = stroke,
        )
        // Arrow up out of it.
        drawPath(
            Path().apply {
                moveTo(0.5f * w, 0.62f * h)
                lineTo(0.5f * w, 0.06f * h)
                moveTo(0.32f * w, 0.22f * h)
                lineTo(0.5f * w, 0.05f * h)
                lineTo(0.68f * w, 0.22f * h)
            },
            Color.White,
            style = stroke,
        )
    }
}

// ── Step 4: PLEASE SHARE A LITTLE ──

private const val STORY_PLACEHOLDER =
    "Please share anything. What you’re going to do with the prize, why you love our app, your favorite food, etc."

@Composable
private fun WINRClaimStep4(
    accent: Color,
    form: PrizeClaimForm,
    onForm: (PrizeClaimForm) -> Unit,
    shareLine: String,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    WINRClaimStepPage(
        accent = accent,
        title = "PLEASE SHARE A LITTLE",
        subtitle = "This helps us show real people like you win!",
        onCTA = onContinue,
    ) {
        // 199dp multiline text area in the Figma field styling, with the
        // frame's placeholder while empty.
        with(WINRClaimStepTheme) {
            Box(
                modifier = Modifier
                    .padding(top = 29.dp)
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .height(215.dp)
                    .fieldBackground(),
            ) {
                BasicTextField(
                    value = form.story,
                    onValueChange = { onForm(form.copy(story = it)) },
                    textStyle = WINRV2Font.inter(20.sp, color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
                if (form.story.isEmpty()) {
                    Text(
                        STORY_PLACEHOLDER,
                        style = WINRV2Font.inter(20.sp, color = Color.White.copy(alpha = 0.6f)),
                        modifier = Modifier.padding(horizontal = 25.dp, vertical = 16.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(top = 38.dp, bottom = 17.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Text(
                "Share on Social Media:",
                style = WINRV2Font.inter(18.sp, FontWeight.Medium, tracking = (-0.54).sp, color = Color.White),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                WINRSocialGlyphKind.entries.forEach { kind ->
                    WINRSocialGlyph(
                        kind = kind,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { WINRShareSheet.present(context, shareLine) }
                            .semantics { contentDescription = "Share on ${kind.displayName}" },
                    )
                }
            }
        }
    }
}

// ── Review: ALMOST DONE! ──

@Composable
private fun WINRClaimReview(
    accent: Color,
    form: PrizeClaimForm,
    onForm: (PrizeClaimForm) -> Unit,
    isSubmitting: Boolean,
    submitError: String?,
    onSubmit: (PrizeClaimForm) -> Unit,
) {
    WINRClaimStepPage(
        accent = accent,
        title = "ALMOST DONE!",
        subtitle = "Please review and agree to claim your prize.",
        ctaTitle = "SUBMIT PRIZE CLAIM",
        ctaEnabled = form.isValid,
        ctaLoading = isSubmitting,
        onCTA = { onSubmit(form) },
        footer = {
            // Gunmetal "secure and encrypted" lock note under the CTA.
            Row(
                modifier = Modifier
                    .padding(top = 30.dp)
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .background(WINRV2Color.gunmetal, RoundedCornerShape(10.dp))
                    .padding(horizontal = 25.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.winr_lock),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    "Your information is secure and encrypted.",
                    style = WINRV2Font.inter(14.sp, color = Color.White),
                )
            }
        },
    ) {
        Box(
            Modifier
                .padding(top = 44.dp, bottom = 12.dp)
                .padding(horizontal = 12.dp)
        ) {
            WINRClaimConsentSection(
                accent = accent,
                form = form,
                onChange = onForm,
            )
        }

        if (submitError != null) {
            Text(
                submitError,
                style = WINRV2Font.inter(
                    13.sp, FontWeight.SemiBold,
                    color = Color(1.0f, 0.45f, 0.4f),
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.padding(top = 8.dp).padding(horizontal = 12.dp),
            )
        }
    }
}
