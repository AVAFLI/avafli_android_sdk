package com.avafli.winrsdk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.avafli.winrsdk.WINRBranding

/**
 * Reusable card wrapper — matches iOS WINRExperienceCard.
 * Used for milestone celebrations, completed state, and error screens.
 */
@Composable
internal fun WINRExperienceCard(
    branding: WINRBranding,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(branding.cornerRadius.dp))
            .background(branding.cardBackgroundColor.copy(alpha = 0.8f))
            .border(
                1.dp,
                branding.cardBorderColor,
                RoundedCornerShape(branding.cornerRadius.dp)
            )
            .padding(24.dp),
        content = content
    )
}
