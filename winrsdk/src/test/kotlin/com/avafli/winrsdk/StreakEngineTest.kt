package com.avafli.winrsdk

import com.avafli.winrsdk.domain.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class StreakEngineTest {

    private lateinit var campaign: Campaign
    private lateinit var engine: StreakEngine

    @Before
    fun setup() {
        campaign = Campaign(
            id = "test-campaign",
            title = "Test Sweepstakes",
            prizeDescription = "Win a Prize!",
            streakLadder = listOf(1, 2, 3, 5, 8, 13, 21),
            doublingEnabled = true,
            maxDailyBaseEntries = 1,
            streakConfig = StreakConfig(
                weeklyBonusThreshold = 5,
                weeklyBonusEntries = 10,
                monthlyBonusThreshold = 20,
                monthlyBonusEntries = 50
            ),
            milestones = listOf(
                Milestone(day = 3, bonusEntries = 5, badge = "streak_3"),
                Milestone(day = 7, bonusEntries = 25, badge = "streak_7")
            )
        )
        engine = StreakEngine(campaign)
    }

    @Test
    fun `entry multiplier returns correct values from ladder`() {
        assertEquals(1, engine.getEntryMultiplier(1))
        assertEquals(2, engine.getEntryMultiplier(2))
        assertEquals(3, engine.getEntryMultiplier(3))
        assertEquals(5, engine.getEntryMultiplier(4))
        assertEquals(8, engine.getEntryMultiplier(5))
        assertEquals(13, engine.getEntryMultiplier(6))
        assertEquals(21, engine.getEntryMultiplier(7))
    }

    @Test
    fun `entry multiplier clamps to last value for days beyond ladder`() {
        assertEquals(21, engine.getEntryMultiplier(8))
        assertEquals(21, engine.getEntryMultiplier(100))
    }

    @Test
    fun `entry multiplier returns 1 for empty ladder`() {
        val emptyCampaign = campaign.copy(streakLadder = emptyList())
        val emptyEngine = StreakEngine(emptyCampaign)
        assertEquals(1, emptyEngine.getEntryMultiplier(1))
    }

    @Test
    fun `process daily claim updates state correctly`() {
        val grant = engine.processDailyClaim(
            entries = 1,
            streakDay = 1,
            totalEntries = 1
        )

        assertEquals(1, grant.entries)
        assertEquals(1, grant.streakDay)
        assertEquals(1, grant.totalEntries)
        assertTrue(engine.getState().claimedToday)
        assertEquals(1, engine.getState().currentDay)
        assertNotNull(engine.getState().lastClaimDate)
    }

    @Test
    fun `streak progression through multiple days`() {
        for (day in 1..7) {
            val entries = engine.getEntryMultiplier(day)
            engine.processDailyClaim(
                entries = entries,
                streakDay = day,
                totalEntries = entries * day
            )
            assertEquals(day, engine.getState().currentDay)
        }
        assertTrue(engine.getState().completedDays.all { it })
    }

    @Test
    fun `reset streak clears state`() {
        engine.processDailyClaim(entries = 1, streakDay = 3, totalEntries = 6)
        engine.resetStreak()

        assertEquals(0, engine.getState().currentDay)
        assertFalse(engine.getState().claimedToday)
        assertEquals(0, engine.getState().totalEntries)
    }

    @Test
    fun `check milestone returns correct milestone`() {
        assertNull(engine.checkMilestone(1))
        assertNull(engine.checkMilestone(2))

        val milestone3 = engine.checkMilestone(3)
        assertNotNull(milestone3)
        assertEquals(5, milestone3?.bonusEntries)
        assertEquals("streak_3", milestone3?.badge)

        val milestone7 = engine.checkMilestone(7)
        assertNotNull(milestone7)
        assertEquals(25, milestone7?.bonusEntries)
    }

    @Test
    fun `double entries doubles the grant entries`() {
        val grant = DailyEntryGrant(
            entries = 5,
            streakDay = 4,
            totalEntries = 11
        )

        val doubled = engine.doubleEntries(grant)
        assertEquals(10, doubled.entries)
        assertTrue(doubled.doubled)
    }

    @Test
    fun `double entries does nothing when doubling disabled`() {
        val noDblCampaign = campaign.copy(doublingEnabled = false)
        val noDblEngine = StreakEngine(noDblCampaign)

        val grant = DailyEntryGrant(entries = 5, streakDay = 4, totalEntries = 11)
        val result = noDblEngine.doubleEntries(grant)
        assertEquals(5, result.entries)
        assertFalse(result.doubled)
    }

    @Test
    fun `has claimed today returns false initially`() {
        assertFalse(engine.hasClaimedToday())
    }

    @Test
    fun `has claimed today returns true after claim`() {
        engine.processDailyClaim(entries = 1, streakDay = 1, totalEntries = 1)
        assertTrue(engine.hasClaimedToday())
    }

    @Test
    fun `weekly bonus progress tracks correctly`() {
        val (current, threshold) = engine.getWeeklyBonusProgress()
        assertEquals(0, current)
        assertEquals(5, threshold)

        engine.processDailyClaim(entries = 1, streakDay = 1, totalEntries = 1)
        val (afterClaim, _) = engine.getWeeklyBonusProgress()
        assertEquals(1, afterClaim)
    }

    @Test
    fun `monthly bonus progress tracks correctly`() {
        val (current, threshold) = engine.getMonthlyBonusProgress()
        assertEquals(0, current)
        assertEquals(20, threshold)
    }

    @Test
    fun `weekly bonus earned flag set when bonus received`() {
        engine.processDailyClaim(
            entries = 1,
            streakDay = 5,
            totalEntries = 19,
            weeklyBonusEntries = 10
        )
        assertTrue(engine.getState().weeklyBonusEarned)
    }

    @Test
    fun `monthly bonus earned flag set when bonus received`() {
        engine.processDailyClaim(
            entries = 1,
            streakDay = 1,
            totalEntries = 100,
            monthlyBonusEntries = 50
        )
        assertTrue(engine.getState().monthlyBonusEarned)
    }

    @Test
    fun `next streak day wraps from 7 to 1`() {
        engine.setState(engine.getState().copy(currentDay = 7))
        assertEquals(1, engine.getNextStreakDay())
    }

    @Test
    fun `next streak day increments normally`() {
        engine.setState(engine.getState().copy(currentDay = 3))
        assertEquals(4, engine.getNextStreakDay())
    }

    @Test
    fun `calculate entries uses multiplier and max daily base`() {
        val campaignWith3Base = campaign.copy(maxDailyBaseEntries = 3)
        val engine3 = StreakEngine(campaignWith3Base)
        engine3.setState(engine3.getState().copy(currentDay = 4))

        // Day 4 multiplier = 5, base = 3 → 15
        assertEquals(15, engine3.calculateEntries())
    }
}
