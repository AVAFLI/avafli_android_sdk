package com.avafli.avaflisdk.domain

/**
 * Represents the result of claiming daily entries.
 */
data class DailyEntryGrant(
    val entries: Int,
    val streakDay: Int,
    val totalEntries: Int,
    val weeklyBonusEntries: Int? = null,
    val monthlyBonusEntries: Int? = null,
    val milestone: Milestone? = null,
    val doubled: Boolean = false
)
