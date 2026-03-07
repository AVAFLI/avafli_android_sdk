package com.avafli.winrsdk.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avafli.winrsdk.domain.Campaign
import com.avafli.winrsdk.domain.StreakState

/**
 * 7-day streak dashboard showing current progress.
 */
@Composable
internal fun StreakDashboard(
    campaign: Campaign,
    streakState: StreakState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Your Streak",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (day in 1..7) {
                val index = day - 1
                val isCompleted = streakState.completedDays.getOrElse(index) { false }
                val isCurrentDay = day == (streakState.currentDay + 1).coerceAtMost(7) && !isCompleted
                val entries = if (campaign.streakLadder.isNotEmpty()) {
                    campaign.streakLadder.getOrElse(index) { campaign.streakLadder.last() }
                } else {
                    1
                } * campaign.maxDailyBaseEntries

                StreakDayTile(
                    dayNumber = day,
                    entries = entries,
                    isCompleted = isCompleted,
                    isCurrentDay = isCurrentDay
                )
            }
        }
    }
}
