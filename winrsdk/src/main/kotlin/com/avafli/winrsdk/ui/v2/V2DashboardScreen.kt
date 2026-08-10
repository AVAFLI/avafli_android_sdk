package com.avafli.winrsdk.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avafli.winrsdk.domain.Giveaway

// Return-user dashboard (Day 2+ drawer), ported from iOS WINRV2DashboardView.

@Composable
internal fun WINRV2DashboardScreen(
    accent: Color,
    logoUrl: String?,
    rulesUrl: String?,
    giveaway: Giveaway?,
    streakDay: Int,
    totalEntries: Int,
    entriesToday: Int,
    ladder: List<Int>,
    claimedToday: Boolean,
    onInfo: () -> Unit,
    onClose: () -> Unit,
    onWinnerTap: (() -> Unit)? = null,
    /**
     * Reveal flow (Day 2+): the claim already succeeded server-side; the UI
     * mounts pinned to yesterday's numbers and the celebration (tile check +
     * confetti + totals update, bar → "N ENTRIES ADDED") fires on its own a
     * beat later — Joe's Slice prototype has no claim tap and no modal.
     */
    pendingClaimEntries: Int? = null,
    revealed: Boolean = true,
    /** Non-blocking banner text ("already entered today" / "couldn't record
     *  today's entry"); null hides the banner. */
    notice: String? = null,
    /** When set, the banner shows a TRY AGAIN affordance invoking this. */
    onNoticeRetry: (() -> Unit)? = null,
) {
    val visitMode = giveaway?.streakMode == "visit"
    // Pinned while today is unclaimed OR the reveal hasn't played — matching
    // the router (V2Root). Without the claimedToday clause the current tile
    // animates at claim-response time, ~1s BEFORE the count-up/toast beat.
    val preReveal = !revealed && (pendingClaimEntries != null || !claimedToday)

    fun ladderValue(day: Int): Int =
        WINRV2Ladder.entries(day = day, ladder = ladder, milestones = giveaway?.milestones)

    val nextEntries = ladderValue(streakDay + 1)
    val railEntries = buildRailEntries(giveaway, streakDay, visitMode, preReveal, ::ladderValue)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WINRV2Color.gunmetal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        WINRV2TabGrabber(Modifier.padding(top = 15.dp))
        WINRV2Header(logoUrl = logoUrl, onInfo = onInfo, onClose = onClose)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            if (notice != null) {
                DashboardNoticeBanner(
                    accent = accent,
                    message = notice,
                    onRetry = onNoticeRetry,
                )
            }
            if (giveaway?.latestWinner != null && onWinnerTap != null) {
                WINRV2WinnerBanner(onTap = onWinnerTap)
            }
            WINRV2PrizeCard(
                accent = accent,
                streakDay = if (preReveal) maxOf(streakDay - 1, 1) else streakDay,
                totalEntries = totalEntries,
                prizeImageUrl = giveaway?.prizeImageUrl,
                prizeValue = giveaway?.prizeValue?.toDoubleOrNull()?.toInt() ?: 0,
                prizeDescription = giveaway?.prizeDescription ?: "",
                visitMode = visitMode,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            WINRV2StreakRail(
                accent = accent,
                entries = railEntries,
                activeId = "day-$streakDay",
                visitMode = visitMode,
            )
            WINRV2ComeBackBar(
                accent = accent,
                nextEntries = nextEntries,
                visitMode = visitMode,
                claimed = claimedToday && !preReveal,
                claimedEntries = pendingClaimEntries ?: entriesToday,
                firstDay = streakDay <= 1,
                // A staged, not-yet-revealed grant at composition time means
                // this open IS the celebration: the bar starts ON the toast
                // (never the pitch first) and slides once to the pitch.
                celebrationOpen = pendingClaimEntries != null && preReveal,
                // A staged grant AT ALL means this open celebrates. Kept
                // separate from [celebrationOpen] because the cache-first
                // render mounts the dashboard before the grant exists — the
                // bar must accept the celebration when it arrives, not only
                // when it was there at mount.
                celebrating = pendingClaimEntries != null,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Always GOT IT (Slice prototype) — the celebration plays on
                // its own; the pill only ever closes.
                WINRV2PillButton(
                    accent = accent,
                    title = "GOT IT",
                    modifier = Modifier.padding(horizontal = 30.dp),
                ) { onClose() }
                WINRV2LegalLinks(rulesUrl = rulesUrl)
            }
        }
    }
}

/**
 * Non-blocking dashboard notice (duplicate same-day entry, claim transport
 * failure): claim-step field styling, white 13sp copy, optional accent
 * TRY AGAIN affordance. Sits above the winner banner; never covers content.
 */
@Composable
private fun DashboardNoticeBanner(
    accent: Color,
    message: String,
    onRetry: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .background(WINRClaimStepTheme.fieldFill, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            message,
            style = WINRV2Font.inter(13.sp, color = Color.White),
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            Text(
                V2Strings.TRY_AGAIN,
                style = WINRV2Font.inter(13.sp, FontWeight.Bold, color = accent),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onRetry() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * The rail: day tiles 1..max(31, streakDay+2) with accelerator power-up tiles
 * joined after each milestone day — identical construction to iOS.
 */
private fun buildRailEntries(
    giveaway: Giveaway?,
    streakDay: Int,
    visitMode: Boolean,
    preReveal: Boolean,
    ladderValue: (Int) -> Int,
): List<WINRV2RailEntry> {
    val entries = mutableListOf<WINRV2RailEntry>()
    val maxDay = maxOf(31, streakDay + 2)
    val milestoneDays: Map<Int, Int> =
        (giveaway?.milestones ?: emptyList()).associate { it.day to it.bonusEntries }
    for (day in 1..maxDay) {
        val state = when {
            day < streakDay -> WINRV2TileState.Completed
            day == streakDay -> if (preReveal) WINRV2TileState.Ready else WINRV2TileState.Active
            else -> WINRV2TileState.Locked
        }
        entries.add(
            WINRV2RailEntry.Day(id = "day-$day", day = day, entries = ladderValue(day), state = state)
        )
        milestoneDays[day]?.let { bonus ->
            val label = when (day) {
                7 -> "1 WEEK"
                14 -> "2 WEEK"
                21 -> "3 WEEK"
                29, 30 -> "1 MONTH"
                else -> "DAY $day"
            }
            val footnote = if (day == streakDay) {
                "STARTING TOMORROW"
            } else {
                "STARTING AT ${if (visitMode) "VISIT" else "DAY"} ${day + 1}"
            }
            entries.add(
                WINRV2RailEntry.PowerUp(id = "power-$day", label = label, bonus = bonus, footnote = footnote)
            )
        }
    }
    return entries
}
