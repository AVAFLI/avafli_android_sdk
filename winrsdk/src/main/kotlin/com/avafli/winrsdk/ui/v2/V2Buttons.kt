package com.avafli.winrsdk.ui.v2

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.avafli.winrsdk.WINRConstants

// CTA pill + legal links, ported from iOS WINRV2PillButton / WINRV2LegalLinks.

@Composable
internal fun WINRV2PillButton(
    accent: Color,
    title: String,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(CircleShape)
            .background(accent)
            .clickable(enabled = enabled && !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(26.dp),
            )
        } else {
            WINRAutoSizeText(
                title,
                style = WINRV2Font.inter(24.sp, FontWeight.ExtraBold, tracking = (-0.72).sp, color = Color.White),
                minScale = 0.6f,
            )
        }
    }
}

@Composable
internal fun WINRV2LegalLinks(
    rulesUrl: String?,
    showPoweredBy: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // OFFICIAL RULES → rulesUrl; PRIVACY POLICY → WINRConstants.PRIVACY_URL.
    // (Pre-2.9.2 both opened rulesUrl — a latent bug fixed on all platforms.)
    val open: (String?) -> Unit = { target ->
        target?.let { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            } catch (_: Exception) {
                // No browser available — silently ignore, like iOS UIApplication.open.
            }
        }
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "OFFICIAL RULES",
                style = WINRV2Font.inter(12.sp, color = WINRV2Color.textSecondary),
                modifier = Modifier.clickable(onClick = { open(rulesUrl) }),
            )
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(WINRV2Color.textSecondary)
            )
            Text(
                "PRIVACY POLICY",
                style = WINRV2Font.inter(12.sp, color = WINRV2Color.textSecondary),
                modifier = Modifier.clickable(onClick = { open(WINRConstants.PRIVACY_URL) }),
            )
        }
        if (showPoweredBy) {
            Text(
                "Powered by © WINR Media",
                style = WINRV2Font.inter(12.sp, color = WINRV2Color.textTertiary),
            )
        }
    }
}
