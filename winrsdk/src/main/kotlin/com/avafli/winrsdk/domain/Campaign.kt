package com.avafli.winrsdk.domain

import kotlinx.serialization.Serializable

@Serializable
data class Campaign(
    val id: String,
    val title: String,
    val prizeDescription: String,
    val prizeValue: String? = null,
    val streakLadder: List<Int> = listOf(1, 2, 3, 5, 8, 13, 21),
    val doublingEnabled: Boolean = false,
    val maxDailyBaseEntries: Int = 1,
    val rulesUrl: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val streakConfig: StreakConfig = StreakConfig(),
    val milestones: List<Milestone>? = null,
    val adConfig: AdConfig? = null
)

@Serializable
data class StreakConfig(
    val weeklyResetDay: Int = 1, // Monday
    val monthlyResetDay: Int = 1,
    val weeklyBonusThreshold: Int = 5,
    val weeklyBonusEntries: Int = 10,
    val monthlyBonusThreshold: Int = 20,
    val monthlyBonusEntries: Int = 50
)

@Serializable
data class Milestone(
    val day: Int,
    val bonusEntries: Int,
    val badge: String? = null
)

@Serializable
data class AdConfig(
    val provider: String,
    val appKey: String? = null,
    val rewardedAdUnitId: String? = null,
    val testMode: Boolean? = null
)
