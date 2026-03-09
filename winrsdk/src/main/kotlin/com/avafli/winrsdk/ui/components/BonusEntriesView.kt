package com.avafli.winrsdk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.WINRBranding
import com.avafli.winrsdk.domain.SdkCopy

/**
 * Bonus entries screen — matches iOS BonusEntriesView.swift.
 * Offers the user a chance to watch a rewarded video to double entries.
 */
@Composable
internal fun BonusEntriesView(
    branding: WINRBranding,
    entries: Int,
    sdkCopy: SdkCopy? = null,
    onClaim: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 50.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = sdkCopy?.bonusEntries?.title 
                ?: sdkCopy?.bonusTitle
                ?: "BONUS ENTRIES",
            color = branding.primaryTextColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = (sdkCopy?.bonusEntries?.subtitle 
                ?: sdkCopy?.bonusSubtitle
                ?: "Watch a short video to double today's {entries} entries.")
                .replace("{entries}", entries.toString()),
            color = branding.primaryTextColor.copy(alpha = 0.9f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Watch & Claim button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(branding.cornerRadius.dp))
                .background(branding.primaryTextColor)
                .clickable { onClaim() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (sdkCopy?.bonusEntries?.watchButton ?: "WATCH & CLAIM {total} ENTRIES")
                    .replace("{total}", (entries * 2).toString())
                    .replace("{entries}", entries.toString()),
                color = branding.backgroundColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Skip button
        Text(
            text = (sdkCopy?.bonusEntries?.skipText ?: "No thanks, continue with {entries} entries")
                .replace("{entries}", entries.toString()),
            color = branding.primaryTextColor.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.clickable { onSkip() }
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}
