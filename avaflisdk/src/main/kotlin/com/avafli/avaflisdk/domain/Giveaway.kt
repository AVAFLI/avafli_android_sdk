package com.avafli.avaflisdk.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class Giveaway(
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
    val adConfig: AdConfig? = null,
    /**
     * Publisher-configurable prize art for the V2 prize card. Absent → the SDK
     * renders the bundled default cash hero with the "WIN $X" overlay.
     */
    val prizeImageUrl: String? = null,
    /**
     * "daily" (default) = consecutive-day streak that resets on a miss.
     * "visit" = visit-count streak for low-frequency apps; never resets.
     */
    val streakMode: String? = null,
    /**
     * Most recent drawn winner for this giveaway chain — drives the
     * "WE HAVE A WINNER!" banner + winners dialog. Absent → no banner.
     */
    val latestWinner: GiveawayWinner? = null
)

@Serializable
data class GiveawayWinner(
    /** Display name, e.g. "Catherine C." */
    val name: String,
    /** e.g. "Brooklyn, New York" */
    val location: String? = null,
    val avatarUrl: String? = null,
    /** "yyyy-MM-dd" */
    val awardedAt: String? = null
) {
    /** "August 20, 2026" — falls back to the raw string if not yyyy-MM-dd. */
    val awardedAtDisplay: String?
        get() {
            val raw = awardedAt ?: return null
            return try {
                LocalDate.parse(raw)
                    .format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US))
            } catch (_: Exception) {
                raw
            }
        }
}

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

/**
 * Rewarded-ad configuration from the giveaway payload. The rewarded-video flow is
 * parked (Phase 1) — this stays only for wire-format tolerance with the backend.
 */
@Serializable
data class AdConfig(
    val provider: String,
    val appKey: String? = null,
    val rewardedAdUnitId: String? = null,
    val testMode: Boolean? = null
)
