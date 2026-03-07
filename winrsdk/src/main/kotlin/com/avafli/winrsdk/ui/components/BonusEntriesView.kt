package com.avafli.winrsdk.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avafli.winrsdk.domain.StreakConfig

/**
 * Displays weekly and monthly bonus progress bars.
 */
@Composable
internal fun BonusEntriesView(
    streakConfig: StreakConfig,
    weeklyDaysCompleted: Int,
    monthlyDaysCompleted: Int,
    weeklyBonusEarned: Boolean,
    monthlyBonusEarned: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Bonus Entries",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Weekly bonus
        BonusProgressRow(
            label = "Weekly Bonus",
            current = weeklyDaysCompleted,
            threshold = streakConfig.weeklyBonusThreshold,
            bonusEntries = streakConfig.weeklyBonusEntries,
            isEarned = weeklyBonusEarned
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Monthly bonus
        BonusProgressRow(
            label = "Monthly Bonus",
            current = monthlyDaysCompleted,
            threshold = streakConfig.monthlyBonusThreshold,
            bonusEntries = streakConfig.monthlyBonusEntries,
            isEarned = monthlyBonusEarned
        )
    }
}

@Composable
private fun BonusProgressRow(
    label: String,
    current: Int,
    threshold: Int,
    bonusEntries: Int,
    isEarned: Boolean
) {
    val progress by animateFloatAsState(
        targetValue = (current.toFloat() / threshold.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "bonusProgress"
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isEarned) "✅ +$bonusEntries earned!"
                else "$current / $threshold days",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isEarned) FontWeight.Bold else FontWeight.Normal,
                color = if (isEarned) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (isEarned) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
