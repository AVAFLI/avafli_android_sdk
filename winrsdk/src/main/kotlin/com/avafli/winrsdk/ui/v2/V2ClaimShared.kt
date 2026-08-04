package com.avafli.winrsdk.ui.v2

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
import com.avafli.winrsdk.R

// Shared chrome for the winner claim flow (ported from iOS WINRV2Claim.swift):
// header with logo + X only, and the dark info card with a leading icon.

/** Claim-flow header: publisher logo centered, X close only (no "?"). */
@Composable
internal fun WINRClaimHeader(
    logoUrl: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        val logo = rememberWinrRemoteImage(logoUrl)
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
                    "WINR",
                    style = WINRV2Font.inter(28.sp, FontWeight.Black, color = Color.White),
                )
                else -> {}
            }
        }
        WINRV2CircleButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Icon(
                painter = painterResource(R.drawable.winr_close_x),
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** Dark info card with a leading icon (shield/mail) — splash + confirmation. */
@Composable
internal fun WINRClaimInfoCard(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 22.dp)
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Box(Modifier.padding(start = 14.dp)) { content() }
    }
}

/** The dark rounded field background shared by the claim form's inputs. */
internal fun Modifier.winrClaimFieldBackground(): Modifier = this
    .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(8.dp))
    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
