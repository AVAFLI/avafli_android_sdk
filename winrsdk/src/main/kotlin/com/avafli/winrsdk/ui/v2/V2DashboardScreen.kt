package com.avafli.winrsdk.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
     * Reveal flow (Day 2+): the claim already succeeded server-side, but the UI
     * holds yesterday's numbers until the user taps "CLAIM N ENTRIES" — that tap
     * is the celebration (tile check + confetti + totals update), no modal.
     */
    pendingClaimEntries: Int? = null,
    revealed: Boolean = true,
    onClaim: (() -> Unit)? = null,
) {
    val visitMode = giveaway?.streakMode == "visit"
    val preReveal = pendingClaimEntries != null && !revealed

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
                // Delta A: once today's claim is revealed (or the user reopens in
                // a claimed state) the bar celebrates the entries that were just
                // added instead of pitching tomorrow. Pre-reveal keeps the
                // come-back copy.
                claimed = claimedToday && !preReveal,
                claimedEntries = pendingClaimEntries ?: entriesToday,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (preReveal && pendingClaimEntries != null && onClaim != null) {
                    WINRV2PillButton(
                        accent = accent,
                        title = "CLAIM ${pendingClaimEntries.winrFormatted()} ENTRIES",
                        modifier = Modifier.padding(horizontal = 30.dp),
                    ) { onClaim() }
                } else {
                    WINRV2PillButton(
                        accent = accent,
                        title = "GOT IT",
                        modifier = Modifier.padding(horizontal = 30.dp),
                    ) { onClose() }
                }
                WINRV2LegalLinks(rulesUrl = rulesUrl)
            }
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
