package com.avafli.winrsdk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.WINRBranding

/**
 * Top header with circular icon buttons — matches iOS WINRExperienceHeaderView.
 */
@Composable
internal fun ExperienceHeader(
    branding: WINRBranding,
    showsBack: Boolean,
    showsInfo: Boolean,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            showsBack -> CircularIconButton(
                branding = branding,
                icon = HeaderIcon.Back,
                onClick = onBack
            )
            showsInfo -> CircularIconButton(
                branding = branding,
                icon = HeaderIcon.QuestionMark,
                onClick = onInfo
            )
            else -> Spacer(modifier = Modifier.size(28.dp))
        }

        CircularIconButton(
            branding = branding,
            icon = HeaderIcon.Close,
            onClick = onClose
        )
    }
}

private enum class HeaderIcon { Back, QuestionMark, Close }

@Composable
private fun CircularIconButton(
    branding: WINRBranding,
    icon: HeaderIcon,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(branding.cardBackgroundColor.copy(alpha = 0.9f))
            .border(1.dp, branding.cardBorderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val tint = branding.primaryTextColor
        when (icon) {
            HeaderIcon.Back -> Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            HeaderIcon.QuestionMark -> androidx.compose.material3.Text(
                text = "?",
                color = tint,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            HeaderIcon.Close -> Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
