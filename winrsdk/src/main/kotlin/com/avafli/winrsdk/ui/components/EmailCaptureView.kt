package com.avafli.winrsdk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.WINRBranding
import com.avafli.winrsdk.domain.SdkCopy
import com.avafli.winrsdk.domain.ScreenMedia

/**
 * Email capture screen — matches iOS EmailCaptureView pixel-for-pixel.
 */
@Composable
internal fun EmailCaptureView(
    branding: WINRBranding,
    rulesUrl: String?,
    prizeValue: String?,
    sdkCopy: SdkCopy? = null,
    heroMedia: ScreenMedia? = null,
    onSubmit: (String, Boolean) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var isAgeConfirmed by remember { mutableStateOf(false) }
    var isMarketingConsented by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }

    val isEmailValid = remember(email) {
        val pattern = Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
        pattern.matches(email)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero media from server config (image or lottie)
        HeroMedia(
            media = heroMedia,
            defaultContent = {
                // Logo placeholder (fallback when no hero media)
                WINRLogoView(
                    branding = branding,
                    useSecondary = false,
                    modifier = Modifier.padding(bottom = 40.dp)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        // Title + subtitle
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = sdkCopy?.emailCapture?.prizeHeadline
                    ?: sdkCopy?.emailCapture?.title
                    ?: sdkCopy?.welcomeTitle
                    ?: prizeValue?.let { "WIN $it!" }
                    ?: "WIN PRIZES!",
                color = branding.secondaryTextColor,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = sdkCopy?.emailCapture?.subtitle
                    ?: sdkCopy?.welcomeSubtitle
                    ?: "Just submit this entry form for your FREE chance to win.",
                color = branding.mutedTextColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Email field
        Column(
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Text(
                text = sdkCopy?.emailCapture?.emailLabel ?: "Email",
                color = branding.secondaryTextColor.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = Color.Black.copy(alpha = 0.5f),
                        spotColor = Color.Black.copy(alpha = 0.5f)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(branding.inputFieldBackgroundColor)
                    .border(
                        1.dp,
                        if (emailError != null) Color.Red.copy(alpha = 0.6f)
                        else branding.inputFieldBorderColor,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = branding.inputFieldPlaceholderColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (email.isEmpty()) {
                            Text(
                                text = sdkCopy?.emailCapture?.emailPlaceholder ?: "Ex. johndoe@gmail.com",
                                color = branding.inputFieldPlaceholderColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        BasicTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                emailError = null
                            },
                            textStyle = TextStyle(
                                color = branding.primaryTextColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(branding.primaryButtonColor),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Inline error
            if (emailError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = emailError!!,
                    color = Color.Red.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 18+ Age gate checkbox
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable { isAgeConfirmed = !isAgeConfirmed },
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = if (isAgeConfirmed) "☑" else "☐",
                fontSize = 20.sp,
                color = if (isAgeConfirmed) branding.primaryButtonColor else branding.mutedTextColor
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = sdkCopy?.emailCapture?.ageGateText 
                    ?: sdkCopy?.ageGateText 
                    ?: "I confirm I am 18 years of age or older",
                color = branding.secondaryTextColor.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Marketing consent checkbox
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable { isMarketingConsented = !isMarketingConsented },
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = if (isMarketingConsented) "☑" else "☐",
                fontSize = 20.sp,
                color = if (isMarketingConsented) branding.primaryButtonColor else branding.mutedTextColor
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = sdkCopy?.emailCapture?.emailConsentText
                    ?: "I agree to receive marketing communications and prize notifications",
                color = branding.secondaryTextColor.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // CTA button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 6.dp)
                .height(48.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = branding.accentGlowColor.copy(
                        alpha = if (isAgeConfirmed) 0.5f else 0.1f
                    ),
                    spotColor = branding.accentGlowColor.copy(
                        alpha = if (isAgeConfirmed) 0.5f else 0.1f
                    )
                )
                .clip(RoundedCornerShape(22.dp))
                .background(
                    if (isAgeConfirmed && email.isNotEmpty())
                        branding.primaryButtonColor
                    else
                        branding.primaryButtonColor.copy(alpha = 0.4f)
                )
                .clickable {
                    val trimmed = email.trim()
                    when {
                        trimmed.isEmpty() -> emailError = "Email is required"
                        !isEmailValid -> emailError = "Please enter a valid email address"
                        !isAgeConfirmed -> emailError = "You must be 18+ to enter"
                        else -> {
                            emailError = null
                            onSubmit(trimmed, isMarketingConsented)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = sdkCopy?.emailCapture?.submitButton ?: "ENTER NOW",
                color = branding.primaryButtonTextColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Official Rules & Privacy Policy
        val uriHandler = LocalUriHandler.current
        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sdkCopy?.emailCapture?.rulesPrefix ?: "By entering, you agree to the ",
                color = branding.mutedTextColor.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
            Text(
                text = sdkCopy?.emailCapture?.rulesLinkText 
                    ?: sdkCopy?.rulesLinkText 
                    ?: "Official Rules",
                color = branding.primaryButtonColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    rulesUrl?.let { uriHandler.openUri(it) }
                }
            )
            Text(
                text = " & ",
                color = branding.mutedTextColor.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
            Text(
                text = "Privacy Policy",
                color = branding.primaryButtonColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://avafli-website.web.app/sdk/privacy")
                }
            )
        }
    }
}

/**
 * Logo view — renders logo from resource ID or URL.
 * Matches iOS WINRLogoView.
 */
@Composable
internal fun WINRLogoView(
    branding: WINRBranding,
    useSecondary: Boolean = false,
    modifier: Modifier = Modifier
) {
    val resId = if (useSecondary) branding.secondaryLogoResId ?: branding.logoResId else branding.logoResId
    val url = if (useSecondary) branding.secondaryLogoUrl ?: branding.logoUrl else branding.logoUrl

    // If no logo configured, show nothing (invisible spacer)
    if (resId == null && url == null) {
        Spacer(modifier = modifier.height(60.dp))
        return
    }

    // Render logo from resource
    resId?.let { id ->
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = id),
            contentDescription = "Logo",
            modifier = modifier
                .height(80.dp)
                .widthIn(max = 200.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
    }

    // TODO: Add URL-based logo loading via Coil/AsyncImage if needed
}

/**
 * Per-screen hero media display.
 * Provides structure for imageUrl/lottieUrl support.
 * Integrating apps can add Coil or other image loading libraries to implement full media loading.
 */
@Composable
internal fun HeroMedia(
    media: ScreenMedia?,
    defaultContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        media?.imageUrl?.isNotEmpty() == true -> {
            // TODO: Integrating apps can use Coil AsyncImage here:
            // AsyncImage(
            //     model = media.imageUrl,
            //     contentDescription = "Hero image",
            //     modifier = modifier,
            //     contentScale = ContentScale.Crop
            // )
            
            // For now, show placeholder or default content
            defaultContent()
        }
        media?.lottieUrl?.isNotEmpty() == true -> {
            // TODO: Integrating apps can use Lottie animation here:
            // LottieAnimation(
            //     composition = rememberLottieComposition(LottieCompositionSpec.Url(media.lottieUrl)),
            //     modifier = modifier
            // )
            
            // For now, show placeholder or default content
            defaultContent()
        }
        else -> defaultContent()
    }
}
