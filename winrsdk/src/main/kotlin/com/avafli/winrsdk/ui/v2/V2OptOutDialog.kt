package com.avafli.winrsdk.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.ui.OptOutPhase

/**
 * The destructive "Delete my data & stop participating" confirmation (and its
 * in-flight / failed / deleted states) — same scrim-plus-card treatment as the
 * winners dialog.
 *
 * 2.9.4: rendered by the V2 ROOT over whatever is showing, because delete is
 * now reached from the in-app privacy webview (the page's "Delete my data"
 * section navigates `winr://delete`, which the webview intercepts) rather than
 * from a native menu item. The confirmation + erasure flow itself is unchanged.
 */
@Composable
internal fun WINRV2OptOutConfirmDialog(
    phase: OptOutPhase,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val inFlight = phase == OptOutPhase.InFlight
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!inFlight) onCancel() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(WINRV2Color.deepCharcoal)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},   // swallow taps inside the card
                )
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (phase == OptOutPhase.Done) {
                Text(
                    V2Strings.OPT_OUT_SUCCESS,
                    style = WINRV2Font.inter(
                        18.sp, FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Text(
                    V2Strings.OPT_OUT_TITLE,
                    style = WINRV2Font.inter(
                        18.sp, FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    ),
                )
                Text(
                    V2Strings.OPT_OUT_BODY,
                    style = WINRV2Font.inter(
                        14.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                    ),
                )
                if (phase is OptOutPhase.Failed) {
                    Text(
                        phase.message,
                        style = WINRV2Font.inter(
                            13.sp,
                            color = WINRClaimStepTheme.errorRed,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
                WINRV2PillButton(
                    accent = WINRClaimStepTheme.errorRed,
                    title = V2Strings.OPT_OUT_CONFIRM,
                    isLoading = inFlight,
                    modifier = Modifier.padding(top = 4.dp),
                ) { onConfirm() }
                Text(
                    V2Strings.OPT_OUT_CANCEL,
                    style = WINRV2Font.inter(14.sp, color = WINRV2Color.textTertiary)
                        .copy(textDecoration = TextDecoration.Underline),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !inFlight, onClick = onCancel)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}
