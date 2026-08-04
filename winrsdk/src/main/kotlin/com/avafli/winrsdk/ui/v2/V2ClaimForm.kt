package com.avafli.winrsdk.ui.v2

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.R
import com.avafli.winrsdk.domain.PrizeClaimForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Claim form ("TELL US ABOUT YOURSELF"), ported from iOS WINRV2ClaimFormView.

@Composable
internal fun WINRV2ClaimFormScreen(
    accent: Color,
    logoUrl: String?,
    prefill: PrizeClaimForm,
    isSubmitting: Boolean,
    submitError: String?,
    onSubmit: (PrizeClaimForm) -> Unit,
    onClose: () -> Unit,
) {
    var form by remember { mutableStateOf(prefill) }
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        photo = null
        form = form.copy(photoBase64 = null)
        if (uri == null) return@rememberLauncherForActivityResult
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

    Box(Modifier.fillMaxSize().background(WINRV2Color.deepCharcoal)) {
        // Gold-sparkle backdrop at the top, fading into the dark body.
        Image(
            painter = painterResource(R.drawable.winr_winner_modal_bg),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(430.dp)
                .align(Alignment.TopCenter),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(430.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to WINRV2Color.deepCharcoal.copy(alpha = 0.15f),
                        0.55f to WINRV2Color.deepCharcoal.copy(alpha = 0.55f),
                        1f to WINRV2Color.deepCharcoal,
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WINRClaimHeader(
                logoUrl = logoUrl,
                onClose = onClose,
                modifier = Modifier.padding(top = 18.dp),
            )

            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .height(40.dp)
                    .background(Color.White, CircleShape)
                    .padding(horizontal = 26.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "PRIZE CLAIM FORM",
                    style = WINRV2Font.inter(15.sp, FontWeight.Black, tracking = (-0.3).sp, color = WINRV2Color.gunmetal),
                )
            }

            WINRAutoSizeText(
                "TELL US ABOUT YOURSELF",
                style = WINRV2Font.inter(24.sp, FontWeight.Black, tracking = (-0.7).sp, color = Color.White),
                minScale = 0.7f,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                "We’ll use this information to verify your prize and personalize your winner announcement.",
                style = WINRV2Font.inter(15.sp, color = Color.White, textAlign = TextAlign.Center),
                modifier = Modifier.padding(top = 4.dp).padding(horizontal = 34.dp),
            )

            Column(
                modifier = Modifier
                    .padding(top = 22.dp)
                    .padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WINRClaimField("First Name", form.firstName, { form = form.copy(firstName = it) })
                WINRClaimField("Last Name (we will only show your last initial)", form.lastName, { form = form.copy(lastName = it) })
                // The winning email lives server-side (the SDK never stores raw
                // email) and the claim is keyed to the account — shown locked.
                WINRClaimField("Winning Email Address (cannot be changed)", "On file with your winning entry", {}, disabled = true)
                WINRClaimField("Phone Number (optional)", form.phone, { form = form.copy(phone = it) }, keyboardType = KeyboardType.Phone)
                WINRClaimField("Street Address", form.street, { form = form.copy(street = it) })
                WINRClaimField("Apartment, Suite, etc. (optional)", form.apt, { form = form.copy(apt = it) })
                WINRClaimField("City", form.city, { form = form.copy(city = it) })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        WINRClaimStatePicker(form.state) { form = form.copy(state = it) }
                    }
                    Box(Modifier.width(110.dp)) {
                        WINRClaimField(
                            "Zip Code", form.zip,
                            { form = form.copy(zip = it.filter { c -> c.isDigit() }.take(5)) },
                            keyboardType = KeyboardType.Number,
                        )
                    }
                }
                WINRClaimField("Country", form.country, {}, disabled = true)
                WINRClaimPhotoSection(
                    photo = photo,
                    onAttach = {
                        photoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemove = {
                        photo = null
                        form = form.copy(photoBase64 = null)
                    },
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
                    modifier = Modifier.padding(top = 14.dp).padding(horizontal = 30.dp),
                )
            }

            WINRV2PillButton(
                accent = accent,
                title = "SUBMIT",
                isLoading = isSubmitting,
                enabled = form.isValid && !isSubmitting,
                modifier = Modifier
                    .padding(top = 20.dp, bottom = 34.dp)
                    .padding(horizontal = 22.dp)
                    .alpha(if (form.isValid) 1f else 0.5f),
            ) { onSubmit(form) }
        }
    }
}
