package com.avafli.winrsdk.ui.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.domain.PrizeClaimForm

// Joe's "review and agree" consents (ported from iOS WINRClaimConsentRow) —
// all three required before SUBMIT PRIZE CLAIM. Shown on the review screen
// ("ALMOST DONE!") pre-checked; unticking any of them disables SUBMIT.

@Composable
internal fun WINRClaimConsentSection(
    accent: Color,
    form: PrizeClaimForm,
    onChange: (PrizeClaimForm) -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        WINRClaimConsentRow(
            accent = accent,
            isOn = form.confirmsAccuracy,
            text = AnnotatedString("I confirm my information is accurate."),
        ) { onChange(form.copy(confirmsAccuracy = !form.confirmsAccuracy)) }
        WINRClaimConsentRow(
            accent = accent,
            isOn = form.authorizesLikeness,
            text = AnnotatedString(
                "I authorize this app's publisher and its promotional partners to use my name, city, profile photo, and likeness for winner announcements and promotional purposes."
            ),
        ) { onChange(form.copy(authorizesLikeness = !form.authorizesLikeness)) }
        WINRClaimConsentRow(
            accent = accent,
            isOn = form.agreesToRules,
            text = buildAnnotatedString {
                val emphasis = SpanStyle(
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                )
                append("I agree to the ")
                withStyle(emphasis) { append("Official Rules") }
                append(" and ")
                withStyle(emphasis) { append("Privacy Policy") }
                append(".")
            },
        ) { onChange(form.copy(agreesToRules = !form.agreesToRules)) }
    }
}

/** A single consent checkbox row (accent square check + wrapping label). */
@Composable
private fun WINRClaimConsentRow(
    accent: Color,
    isOn: Boolean,
    text: AnnotatedString,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            ),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    if (isOn) accent else Color.White.copy(alpha = 0.07f),
                    RoundedCornerShape(5.dp),
                )
                .border(
                    1.5.dp,
                    if (isOn) accent else Color.White.copy(alpha = 0.4f),
                    RoundedCornerShape(5.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            // SF "checkmark" equivalent (bold 13pt), drawn white.
            Canvas(Modifier.size(13.dp).alpha(if (isOn) 1f else 0f)) {
                val check = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.55f)
                    lineTo(size.width * 0.38f, size.height * 0.85f)
                    lineTo(size.width * 0.92f, size.height * 0.18f)
                }
                drawPath(
                    check,
                    Color.White,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        Text(
            text,
            style = WINRV2Font.inter(16.sp, color = Color.White),
            modifier = Modifier.weight(1f),
        )
    }
}
