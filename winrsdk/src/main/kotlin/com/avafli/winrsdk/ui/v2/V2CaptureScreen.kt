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
    onSubmit: (String) -> Unit,
    onInfo: () -> Unit,
    onClose: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var isAdult by remember { mutableStateOf(false) }

    val day1Entries = giveaway?.streakLadder?.firstOrNull() ?: 10
    val canSubmit = isAdult && email.contains("@") && email.contains(".")

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
                EmailField(email, onValueChange = { email = it })
                AgeGateCheckbox(isAdult) { isAdult = !isAdult }
                WINRV2PillButton(
                    accent = accent,
                    title = "CLAIM MY $day1Entries ENTRIES",
                    isLoading = isSubmitting,
                    enabled = canSubmit && !isSubmitting,
                    modifier = Modifier.alpha(if (canSubmit) 1f else 0.5f),
                ) {
                    onSubmit(email.trim())
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
private fun EmailField(value: String, onValueChange: (String) -> Unit) {
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
}

@Composable
private fun AgeGateCheckbox(checked: Boolean, onToggle: () -> Unit) {
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
            "I confirm I am 18 years of age or older",
            style = WINRV2Font.inter(14.sp, color = Color.White),
        )
    }
}
