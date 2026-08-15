package com.avafli.winrsdk.ui.v2

import android.content.Intent
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.avafli.winrsdk.WINRConstants
import com.avafli.winrsdk.domain.PrizeClaimForm

// Review-screen consent (2.9, 14 Aug team decision): the "information is
// accurate" and "agree to Official Rules" checkboxes are GONE. Only the
// likeness/promo checkbox remains, OPTIONAL — submit is never gated on it —
// and its state rides the payload as `promoConsentGranted`. The Official
// Rules / Privacy Policy links stay tappable below it.

@Composable
internal fun WINRClaimConsentSection(
    accent: Color,
    form: PrizeClaimForm,
    rulesUrl: String?,
    onChange: (PrizeClaimForm) -> Unit,
) {
    val context = LocalContext.current
    // Official Rules → rulesUrl; Privacy Policy → WINRConstants.PRIVACY_URL.
    // (Pre-2.9.2 both opened rulesUrl — a latent bug fixed on all platforms.)
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
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        WINRClaimConsentRow(
            accent = accent,
            isOn = form.authorizesLikeness,
            text = AnnotatedString(
                "I authorize this app's publisher and its promotional partners to use my name, city, profile photo, and likeness for winner announcements and promotional purposes. (Optional)"
            ),
        ) { onChange(form.copy(authorizesLikeness = !form.authorizesLikeness)) }

        // Tappable rules/privacy line (no checkbox — informational, per 2.9).
        // Each phrase is its own link span so the two documents open their own
        // URLs (pre-2.9.2 the whole line opened rulesUrl).
        Text(
            buildAnnotatedString {
                val emphasis = TextLinkStyles(
                    style = SpanStyle(
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                    ),
                )
                append("By submitting you agree to the ")
                withLink(LinkAnnotation.Clickable("rules", emphasis) { openUrl(rulesUrl) }) {
                    append("Official Rules")
                }
                append(" and ")
                withLink(
                    LinkAnnotation.Clickable("privacy", emphasis) {
                        openUrl(WINRConstants.PRIVACY_URL)
                    },
                ) {
                    append("Privacy Policy")
                }
                append(".")
            },
            style = WINRV2Font.inter(14.sp, color = Color.White.copy(alpha = 0.8f)),
            modifier = Modifier.fillMaxWidth(),
        )
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
