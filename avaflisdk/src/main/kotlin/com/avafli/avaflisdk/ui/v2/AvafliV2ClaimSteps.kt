package com.avafli.avaflisdk.ui.v2

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.focus.onFocusEvent
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
import com.avafli.avaflisdk.R
import com.avafli.avaflisdk.domain.PrizeClaimBlock
import com.avafli.avaflisdk.domain.PrizeClaimForm
import com.avafli.avaflisdk.domain.AvafliFieldValidation
import com.avafli.avaflisdk.network.AvafliPlacesClient
import com.avafli.avaflisdk.ui.v2.AvafliClaimStepTheme.fieldBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//
// Root of the stepped prize-claim form (Joe's Figma design), ported from iOS
// AvafliV2ClaimStepsFlow.swift: a persistent gold-sparkle backdrop + header +
// animated step indicator, with the form steps and the review screen sliding
// horizontally beneath them (push left on advance, push right on back).
//
// 2.9 (14 Aug team decision): the "PLEASE SHARE A LITTLE" screen moved OUT of
// the pre-submit steps — it now shows AFTER a successful submit (see
// AvafliV2ClaimShareScreen), so closing it loses nothing. The form is 3 steps +
// review.
//

/**
 * The screens of the stepped form. [indicatorStep] is the 1-based step
 * number (review has no "STEP N OF 3" row, matching the SUBMIT frame).
 */
internal enum class AvafliClaimFlowStep(val indicatorStep: Int?) {
    One(1), Two(2), Three(3), Review(null)
}

@Composable
internal fun AvafliV2ClaimStepsFlow(
    accent: Color,
    logoUrl: String?,
    /** Publisher's app/brand name (sdkConfig.appName) — likeness consent copy. */
    appName: String?,
    /** Google Places key (sdkConfig.placesApiKey) — null → no autocomplete. */
    placesApiKey: String?,
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
    var step by remember { mutableStateOf(AvafliClaimFlowStep.One) }

    // Street-field address autocomplete (2.9): present only when the
    // publisher configured a Places key — otherwise the address step is
    // exactly the plain-typing form.
    val placesClient = remember(placesApiKey) {
        placesApiKey?.takeIf { it.isNotBlank() }?.let { AvafliPlacesClient(it) }
    }

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
                AvafliClaimPhoto.loadBitmap(context, uri)?.let { bmp ->
                    bmp to AvafliClaimPhoto.base64Jpeg(bmp)
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

    Box(Modifier.fillMaxSize().background(AvafliV2Color.deepCharcoal)) {
        // Gold-sparkle full-bleed backdrop fading into the dark body, per the
        // frames (406dp tall, transparent → deepCharcoal).
        Image(
            painter = painterResource(R.drawable.avafli_winner_modal_bg),
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
                        0.05f to AvafliV2Color.deepCharcoal.copy(alpha = 0.1f),
                        0.6f to AvafliV2Color.deepCharcoal.copy(alpha = 0.6f),
                        1f to AvafliV2Color.deepCharcoal,
                    )
                )
        )

        Column(Modifier.fillMaxSize()) {
            AvafliClaimHeader(
                logoUrl = logoUrl,
                onClose = onClose,
                modifier = Modifier.padding(top = 18.dp),
                showsBack = step != AvafliClaimFlowStep.One,
                onBack = {
                    AvafliClaimFlowStep.entries.getOrNull(step.ordinal - 1)?.let { step = it }
                },
            )

            AnimatedVisibility(
                visible = step.indicatorStep != null,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
            ) {
                AvafliClaimStepIndicator(
                    accent = accent,
                    current = step.indicatorStep ?: 3,
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
                label = "avafliClaimStep",
            ) { current ->
                when (current) {
                    AvafliClaimFlowStep.One -> AvafliClaimStep1(
                        accent = accent,
                        form = form,
                        onForm = { form = it },
                        maskedEmail = claim.maskedEmail,
                        onContinue = { step = AvafliClaimFlowStep.Two },
                    )

                    AvafliClaimFlowStep.Two -> AvafliClaimStep2(
                        accent = accent,
                        form = form,
                        onForm = { form = it },
                        placesClient = placesClient,
                        onContinue = { step = AvafliClaimFlowStep.Three },
                    )

                    AvafliClaimFlowStep.Three -> AvafliClaimStep3(
                        accent = accent,
                        photo = photo,
                        onPickPhoto = pickPhoto,
                        onContinue = { step = AvafliClaimFlowStep.Review },
                    )

                    AvafliClaimFlowStep.Review -> AvafliClaimReview(
                        accent = accent,
                        form = form,
                        appName = appName,
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
 * "STEP N OF 3" + the row of 3 dots connected by accent lines: filled with
 * the accent up to the current step, outlined after it. (The share step left
 * the pre-submit flow in 2.9, so the form is 3 steps.)
 */
@Composable
internal fun AvafliClaimStepIndicator(
    accent: Color,
    current: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics { contentDescription = "Step $current of 3" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "STEP $current OF 3",
            style = AvafliV2Font.inter(17.sp, FontWeight.SemiBold, tracking = (-0.85).sp, color = Color.White),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (index in 1..3) {
                StepDot(accent = accent, filled = index <= current)
                if (index < 3) {
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
    val fill by animateFloatAsState(if (filled) 1f else 0f, tween(300), label = "avafliStepDot")
    Box(
        Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(AvafliV2Color.deepCharcoal.copy(alpha = 0.6f))
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
private fun AvafliClaimStepPage(
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
            // IME never blocks fields (2.9): the open keyboard becomes bottom
            // padding, so every field and the CTA stay reachable by scrolling.
            .imePadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = AvafliV2Font.inter(
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
                style = AvafliV2Font.inter(
                    18.sp, FontWeight.Medium,
                    tracking = (-0.54).sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.padding(top = 7.dp),
            )
        }

        content()

        AvafliV2PillButton(
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
private fun AvafliClaimStep1(
    accent: Color,
    form: PrizeClaimForm,
    onForm: (PrizeClaimForm) -> Unit,
    maskedEmail: String?,
    onContinue: () -> Unit,
) {
    AvafliClaimStepPage(
        accent = accent,
        title = "TELL US ABOUT YOURSELF",
        subtitle = "We'll use this information to verify your prize and personalize your winner announcement.",
        ctaEnabled = form.isStep1Valid,
        onCTA = onContinue,
    ) {
        // Inline errors appear once a field holds a non-empty INVALID value —
        // an empty field is just "not filled yet" (the dimmed CTA covers it).
        // The same rules gate isStep1Valid, so an error always blocks CONTINUE.
        fun nameError(value: String, message: String): String? =
            message.takeIf { value.isNotBlank() && !AvafliFieldValidation.isValidName(value) }

        Column(
            modifier = Modifier
                .padding(top = 34.dp)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(21.dp),
        ) {
            AvafliClaimStepField(
                "First Name", form.firstName,
                errorText = nameError(form.firstName, AvafliV2Strings.INVALID_FIRST_NAME),
            ) { onForm(form.copy(firstName = it)) }
            AvafliClaimStepField(
                "Last Name (we will only show your last initial)", form.lastName,
                errorText = nameError(form.lastName, AvafliV2Strings.INVALID_LAST_NAME),
            ) { onForm(form.copy(lastName = it)) }
            // The winning email lives server-side (the SDK never stores the
            // raw address) and the claim is keyed to the account — shown
            // locked, masked by the backend for recognition.
            AvafliClaimStepLockedField(
                label = "Winning Email Address (cannot be changed)",
                value = maskedEmail ?: "On file with your winning entry",
            )
            // Phone stays OPTIONAL (blank is fine), but a non-empty value must
            // reduce to a valid 10-digit US number to CONTINUE.
            AvafliClaimStepField(
                "Phone Number (optional)", form.phone,
                keyboardType = KeyboardType.Phone,
                errorText = AvafliV2Strings.INVALID_PHONE.takeIf {
                    !AvafliFieldValidation.isValidOptionalPhone(form.phone)
                },
            ) { onForm(form.copy(phone = it)) }
        }
    }
}

// ── Step 2: WHERE SHOULD WE SEND YOUR PRIZE? ──

@Composable
private fun AvafliClaimStep2(
    accent: Color,
    form: PrizeClaimForm,
    onForm: (PrizeClaimForm) -> Unit,
    placesClient: AvafliPlacesClient?,
    onContinue: () -> Unit,
) {
    AvafliClaimStepPage(
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
            // With a Places key the street field suggests addresses and a tap
            // fills the whole step; without one it's the plain field. Every
            // filled value stays hand-editable.
            AvafliClaimStepStreetField(
                label = "Street Address",
                value = form.street,
                placesClient = placesClient,
                onValueChange = { onForm(form.copy(street = it)) },
                onAddressResolved = { address ->
                    onForm(
                        form.copy(
                            street = address.street,
                            // A missing component keeps whatever is already
                            // typed rather than blanking the field.
                            city = address.city.ifBlank { form.city },
                            state = if (address.state.isBlank()) {
                                form.state
                            } else {
                                AvafliPlacesClient.usStateFullName(address.state)
                            },
                            zip = address.zip.ifBlank { form.zip },
                        )
                    )
                },
            )
            AvafliClaimStepField("Apartment, Suite, etc. (optional)", form.apt) {
                onForm(form.copy(apt = it))
            }
            AvafliClaimStepField("City", form.city) { onForm(form.copy(city = it)) }
            // Weighted split (2.9 fix): the fixed-width zip box clipped on
            // narrow screens — both columns now share the row by weight.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(Modifier.weight(1.7f)) {
                    AvafliClaimStepMenuField(
                        label = "State",
                        options = PrizeClaimForm.usStates,
                        selection = form.state,
                    ) { onForm(form.copy(state = it)) }
                }
                Box(Modifier.weight(1f)) {
                    AvafliClaimStepField(
                        "Zip Code", form.zip,
                        keyboardType = KeyboardType.Number,
                    ) { onForm(form.copy(zip = it.filter { c -> c.isDigit() }.take(5))) }
                }
            }
            // US-only sweepstakes — the country row renders like the frame's
            // dropdown but is fixed.
            AvafliClaimStepLockedField(
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
private fun AvafliClaimStep3(
    accent: Color,
    photo: ImageBitmap?,
    onPickPhoto: () -> Unit,
    onContinue: () -> Unit,
) {
    AvafliClaimStepPage(
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
                style = AvafliV2Font.inter(12.sp, color = Color.White, textAlign = TextAlign.Center),
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
                .background(AvafliV2Color.gunmetal)
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
                .background(AvafliV2Color.deepCharcoal)
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
            style = AvafliV2Font.inter(22.sp, FontWeight.SemiBold, tracking = (-0.66).sp, color = Color.White),
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
            AvafliV2Color.deepCharcoal,
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

// ── Post-submit share screen: PLEASE SHARE A LITTLE (2.9) ──

private const val STORY_PLACEHOLDER =
    "Please share anything. What you’re going to do with the prize, why you love our app, your favorite food, etc."

/**
 * The "PLEASE SHARE A LITTLE" screen, shown AFTER a successful submit (14 Aug
 * team decision) — the claim is already safely in, so closing this screen (or
 * the drawer) loses nothing. Story + social actions are optional flourish.
 *
 * Both exits hand the CURRENT story text back ([onDone] advances to the
 * confirmation card; [onClose] dismisses) so a typed story is posted to the
 * claim via `attachClaimStory` no matter how the person leaves — including
 * the system back gesture, which is intercepted below.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun AvafliV2ClaimShareScreen(
    accent: Color,
    logoUrl: String?,
    claim: PrizeClaimBlock,
    shareUrl: String?,
    onDone: (String) -> Unit,
    onClose: (String) -> Unit,
) {
    val context = LocalContext.current
    var story by remember { mutableStateOf("") }

    // System back must not lose a typed story: route it through the same
    // story-carrying close as the X button.
    androidx.activity.compose.BackHandler { onClose(story) }

    /** "I just won {prize} in {app}!" — the social share line. */
    val shareLine = remember(claim) {
        val prize = AvafliV2PrizeText.stripHeadline(
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

    val storyBringIntoView = remember {
        androidx.compose.foundation.relocation.BringIntoViewRequester()
    }
    val storyFocusScope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(AvafliV2Color.deepCharcoal)) {
        // Same gold-sparkle backdrop as the stepped form.
        Image(
            painter = painterResource(R.drawable.avafli_winner_modal_bg),
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
                        0.05f to AvafliV2Color.deepCharcoal.copy(alpha = 0.1f),
                        0.6f to AvafliV2Color.deepCharcoal.copy(alpha = 0.6f),
                        1f to AvafliV2Color.deepCharcoal,
                    )
                )
        )

        Column(Modifier.fillMaxSize()) {
            AvafliClaimHeader(
                logoUrl = logoUrl,
                onClose = { onClose(story) },
                modifier = Modifier.padding(top = 18.dp),
            )

            AvafliClaimStepPage(
                accent = accent,
                title = "PLEASE SHARE A LITTLE",
                subtitle = "This helps us show real people like you win!",
                ctaTitle = "DONE",
                onCTA = { onDone(story) },
            ) {
                // Multiline text area in the Figma field styling, with the
                // frame's placeholder while empty.
                with(AvafliClaimStepTheme) {
                    Box(
                        modifier = Modifier
                            .padding(top = 29.dp)
                            .padding(horizontal = 12.dp)
                            .fillMaxWidth()
                            .height(215.dp)
                            .fieldBackground()
                            .bringIntoViewRequester(storyBringIntoView),
                    ) {
                        BasicTextField(
                            value = story,
                            onValueChange = { story = it },
                            textStyle = AvafliV2Font.inter(20.sp, color = Color.White),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                                .onFocusEvent { state ->
                                    if (state.isFocused) {
                                        storyFocusScope.launch {
                                            kotlinx.coroutines.delay(Avafli_IME_SETTLE_MS)
                                            storyBringIntoView.bringIntoView()
                                        }
                                    }
                                },
                        )
                        if (story.isEmpty()) {
                            Text(
                                STORY_PLACEHOLDER,
                                style = AvafliV2Font.inter(20.sp, color = Color.White.copy(alpha = 0.6f)),
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
                        style = AvafliV2Font.inter(18.sp, FontWeight.Medium, tracking = (-0.54).sp, color = Color.White),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        AvafliSocialGlyphKind.entries.forEach { kind ->
                            AvafliSocialGlyph(
                                kind = kind,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        AvafliShareSheet.share(context, kind, shareLine, shareUrl)
                                    }
                                    .semantics { contentDescription = "Share on ${kind.displayName}" },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Review: ALMOST DONE! ──

@Composable
private fun AvafliClaimReview(
    accent: Color,
    form: PrizeClaimForm,
    appName: String?,
    onForm: (PrizeClaimForm) -> Unit,
    isSubmitting: Boolean,
    submitError: String?,
    onSubmit: (PrizeClaimForm) -> Unit,
) {
    AvafliClaimStepPage(
        accent = accent,
        title = "ALMOST DONE!",
        subtitle = "Please review to claim your prize.",
        ctaTitle = "SUBMIT PRIZE CLAIM",
        // 2.9: the single likeness/promo checkbox is OPTIONAL — submit is
        // always enabled (field validity was enforced by the steps).
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
                    .background(AvafliV2Color.gunmetal, RoundedCornerShape(10.dp))
                    .padding(horizontal = 25.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.avafli_lock),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    "Your information is secure and encrypted.",
                    style = AvafliV2Font.inter(14.sp, color = Color.White),
                )
            }
        },
    ) {
        Box(
            Modifier
                .padding(top = 44.dp, bottom = 12.dp)
                .padding(horizontal = 12.dp)
        ) {
            AvafliClaimConsentSection(
                accent = accent,
                form = form,
                appName = appName,
                onChange = onForm,
            )
        }

        if (submitError != null) {
            Text(
                submitError,
                style = AvafliV2Font.inter(
                    13.sp, FontWeight.SemiBold,
                    color = Color(1.0f, 0.45f, 0.4f),
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.padding(top = 8.dp).padding(horizontal = 12.dp),
            )
        }
    }
}
