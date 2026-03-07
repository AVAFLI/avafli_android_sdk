package com.avafli.winrsdk.domain

/**
 * Represents the current streak state for the user.
 */
data class StreakState(
    /** Current streak day (1-7). */
    val currentDay: Int = 0,
    /** Whether today's entries have been claimed. */
    val claimedToday: Boolean = false,
    /** Total entries accumulated. */
    val totalEntries: Int = 0,
    /** Days completed this week (for weekly bonus). */
    val weeklyDaysCompleted: Int = 0,
    /** Days completed this month (for monthly bonus). */
    val monthlyDaysCompleted: Int = 0,
    /** Whether weekly bonus has been earned this period. */
    val weeklyBonusEarned: Boolean = false,
    /** Whether monthly bonus has been earned this period. */
    val monthlyBonusEarned: Boolean = false,
    /** Last claim date as ISO string (yyyy-MM-dd). */
    val lastClaimDate: String? = null,
    /** History of completed days in current 7-day cycle (indices 0-6). */
    val completedDays: List<Boolean> = List(7) { false }
)
