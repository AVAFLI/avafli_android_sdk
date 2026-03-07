package com.avafli.winrsdk.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Engine that manages streak progression, bonuses, and milestones.
 * Port of the iOS StreakEngine.
 */
class StreakEngine(private val campaign: Campaign) {

    private var state: StreakState = StreakState()

    fun getState(): StreakState = state

    fun setState(newState: StreakState) {
        state = newState
    }

    /**
     * Get the entry multiplier for a given streak day (1-indexed).
     * Falls back to the last value in the ladder if day exceeds ladder size.
     */
    fun getEntryMultiplier(day: Int): Int {
        if (campaign.streakLadder.isEmpty()) return 1
        val index = (day - 1).coerceIn(0, campaign.streakLadder.size - 1)
        return campaign.streakLadder[index]
    }

    /**
     * Calculate entries for the current streak day.
     */
    fun calculateEntries(): Int {
        val day = state.currentDay.coerceAtLeast(1)
        return getEntryMultiplier(day) * campaign.maxDailyBaseEntries
    }

    /**
     * Process a daily claim. Returns the updated state and entries earned.
     */
    fun processDailyClaim(
        entries: Int,
        streakDay: Int,
        totalEntries: Int,
        weeklyBonusEntries: Int? = null,
        monthlyBonusEntries: Int? = null,
        milestone: Milestone? = null
    ): DailyEntryGrant {
        val today = LocalDate.now().toString()
        val completedDays = state.completedDays.toMutableList()
        val dayIndex = (streakDay - 1).coerceIn(0, 6)
        completedDays[dayIndex] = true

        state = state.copy(
            currentDay = streakDay,
            claimedToday = true,
            totalEntries = totalEntries,
            weeklyDaysCompleted = state.weeklyDaysCompleted + 1,
            monthlyDaysCompleted = state.monthlyDaysCompleted + 1,
            weeklyBonusEarned = weeklyBonusEntries != null && weeklyBonusEntries > 0,
            monthlyBonusEarned = monthlyBonusEntries != null && monthlyBonusEntries > 0,
            lastClaimDate = today,
            completedDays = completedDays
        )

        return DailyEntryGrant(
            entries = entries,
            streakDay = streakDay,
            totalEntries = totalEntries,
            weeklyBonusEntries = weeklyBonusEntries,
            monthlyBonusEntries = monthlyBonusEntries,
            milestone = milestone
        )
    }

    /**
     * Check if the streak should be reset based on the last claim date.
     * Streak resets if the user missed a day.
     */
    fun checkStreakReset(): Boolean {
        val lastClaim = state.lastClaimDate ?: return false
        val lastDate = try {
            LocalDate.parse(lastClaim)
        } catch (e: Exception) {
            return true
        }
        val today = LocalDate.now()
        val daysBetween = ChronoUnit.DAYS.between(lastDate, today)

        return if (daysBetween > 1) {
            resetStreak()
            true
        } else {
            false
        }
    }

    /**
     * Reset the streak to day 1.
     */
    fun resetStreak() {
        state = StreakState()
    }

    /**
     * Check if today's entries have already been claimed.
     */
    fun hasClaimedToday(): Boolean {
        val lastClaim = state.lastClaimDate ?: return false
        return try {
            LocalDate.parse(lastClaim) == LocalDate.now()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the weekly bonus progress (days completed / threshold).
     */
    fun getWeeklyBonusProgress(): Pair<Int, Int> {
        return Pair(
            state.weeklyDaysCompleted,
            campaign.streakConfig.weeklyBonusThreshold
        )
    }

    /**
     * Get the monthly bonus progress (days completed / threshold).
     */
    fun getMonthlyBonusProgress(): Pair<Int, Int> {
        return Pair(
            state.monthlyDaysCompleted,
            campaign.streakConfig.monthlyBonusThreshold
        )
    }

    /**
     * Check if a milestone is reached at the given streak day.
     */
    fun checkMilestone(day: Int): Milestone? {
        return campaign.milestones?.find { it.day == day }
    }

    /**
     * Double the entries for the current claim (after watching rewarded video).
     */
    fun doubleEntries(currentGrant: DailyEntryGrant): DailyEntryGrant {
        if (!campaign.doublingEnabled) return currentGrant
        return currentGrant.copy(
            entries = currentGrant.entries * 2,
            doubled = true
        )
    }

    /**
     * Get the next streak day (for display purposes).
     */
    fun getNextStreakDay(): Int {
        return if (state.currentDay >= 7) 1 else state.currentDay + 1
    }
}
