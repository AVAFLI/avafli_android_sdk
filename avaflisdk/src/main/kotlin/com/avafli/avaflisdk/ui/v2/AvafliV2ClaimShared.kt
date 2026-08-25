package com.avafli.avaflisdk.ui.v2

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.avaflisdk.R

// Shared chrome for the winner claim flow (ported from iOS AvafliV2Claim.swift):
// header with logo + X only, and the dark info card with a leading icon.

/**
 * Claim-flow header: publisher logo centered, X close only (no "?"), plus an
 * optional back chevron on the left (the stepped form, steps 2+ / review).
 */
@Composable
internal fun AvafliClaimHeader(
    logoUrl: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showsBack: Boolean = false,
    onBack: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        if (showsBack) {
            AvafliV2CircleButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                AvafliV2BackChevron()
            }
        }
        val logo = rememberAvafliRemoteImage(logoUrl)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .height(60.dp)
                .widthIn(max = 210.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                logo != null -> Image(
                    bitmap = logo,
                    contentDescription = null,
                    modifier = Modifier.height(60.dp),
                    contentScale = ContentScale.Fit,
                )
                logoUrl == null -> Text(
                    "Avafli",
                    style = AvafliV2Font.inter(28.sp, FontWeight.Black, color = Color.White),
                )
                else -> {}
            }
        }
        AvafliV2CircleButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Icon(
                painter = painterResource(R.drawable.avafli_close_x),
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** Dark info card with a leading icon (shield/mail) — splash + confirmation. */
@Composable
internal fun AvafliClaimInfoCard(
    modifier: Modifier = Modifier,
    /** Card fill. Default: the translucent treatment (splash secure-note). */
    background: Color = Color.White.copy(alpha = 0.08f),
    /** Optional subtle hairline border; null → borderless (default). */
    borderColor: Color? = null,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 22.dp)
            .fillMaxWidth()
            .background(background, RoundedCornerShape(12.dp))
            .then(
                borderColor?.let { Modifier.border(1.dp, it, RoundedCornerShape(12.dp)) }
                    ?: Modifier
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Box(Modifier.padding(start = 14.dp)) { content() }
    }
}
