package com.avafli.avaflisdk.ui.v2

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// CTA pill + legal links, ported from iOS AvafliV2PillButton / AvafliV2LegalLinks.

@Composable
internal fun AvafliV2PillButton(
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
            AvafliAutoSizeText(
                title,
                style = AvafliV2Font.inter(24.sp, FontWeight.ExtraBold, tracking = (-0.72).sp, color = Color.White),
                minScale = 0.6f,
            )
        }
    }
}

@Composable
internal fun AvafliV2LegalLinks(
    rulesUrl: String?,
    showPoweredBy: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // OFFICIAL RULES → rulesUrl; PRIVACY POLICY → AvafliConstants.PRIVACY_URL
    // with ?app=1 (the in-app build). Both open INSIDE the experience via the
    // legal webview (2.9.4) instead of bouncing out to a browser.
    val openLegal = rememberAvafliLegalOpener()
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
                style = AvafliV2Font.inter(12.sp, color = AvafliV2Color.textSecondary),
                modifier = Modifier.clickable(onClick = {
                    openLegal(AvafliV2Strings.OFFICIAL_RULES_LINK, rulesUrl)
                }),
            )
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(AvafliV2Color.textSecondary)
            )
            Text(
                "PRIVACY POLICY",
                style = AvafliV2Font.inter(12.sp, color = AvafliV2Color.textSecondary),
                modifier = Modifier.clickable(onClick = {
                    openLegal(AvafliV2Strings.PRIVACY_POLICY_LINK, AvafliLegalWebPolicy.privacyUrlForApp())
                }),
            )
        }
        if (showPoweredBy) {
            Text(
                "Powered by © Avafli",
                style = AvafliV2Font.inter(12.sp, color = AvafliV2Color.textTertiary),
            )
        }
    }
}
