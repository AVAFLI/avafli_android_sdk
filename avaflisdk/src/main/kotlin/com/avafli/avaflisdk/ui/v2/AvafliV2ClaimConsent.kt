package com.avafli.avaflisdk.ui.v2

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.avaflisdk.domain.PrizeClaimForm

// Review-screen consent (2.9.3, Ryan's direction / Joe's updated Figma): the
// "By submitting you agree to the Official Rules / Privacy Policy" sentence is
// GONE from the review screen entirely — the legal linking lives on the
// capture screen. Only the likeness/promo checkbox remains, OPTIONAL — submit
// is never gated on it — and its state rides the payload as
// `promoConsentGranted`. The checkbox names the actual publisher:
// [appName] (server-fed sdkConfig.appName) when present, else the host app's
// launcher label (same source as the share line), else generic wording.

@Composable
internal fun AvafliClaimConsentSection(
    accent: Color,
    form: PrizeClaimForm,
    /** Publisher's app/brand name (sdkConfig.appName); null → host label. */
    appName: String?,
    onChange: (PrizeClaimForm) -> Unit,
) {
    val context = LocalContext.current
    val publisherName = remember(appName) {
        appName?.takeIf { it.isNotBlank() }
            ?: try {
                context.applicationInfo.loadLabel(context.packageManager)
                    .toString().takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
    }
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AvafliClaimConsentRow(
            accent = accent,
            isOn = form.authorizesLikeness,
            text = AnnotatedString(
                "I authorize ${publisherName ?: "this app's publisher"} and its promotional partners to use my name, city, profile photo, and likeness for winner announcements and promotional purposes. (Optional)"
            ),
        ) { onChange(form.copy(authorizesLikeness = !form.authorizesLikeness)) }
    }
}

/** A single consent checkbox row (accent square check + wrapping label). */
@Composable
private fun AvafliClaimConsentRow(
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
            style = AvafliV2Font.inter(16.sp, color = Color.White),
            modifier = Modifier.weight(1f),
        )
    }
}
