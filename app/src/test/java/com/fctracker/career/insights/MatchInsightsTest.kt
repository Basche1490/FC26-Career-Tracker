package com.fctracker.career.insights

import com.fctracker.career.MatchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchInsightsTest {

    private fun match(
        id: String, date: String, gf: Int, ga: Int,
        competition: String = "Premier League", estimated: Boolean = false
    ) = MatchResult(
        id = id, opponentName = "Opp $id", isHome = true, goalsFor = gf, goalsAgainst = ga,
        matchDate = date, competition = competition, statsEstimated = estimated
    )

    // newest first, the way the app keeps them
    private val matches = listOf(
        match("m6", "2026-10-18", 0, 2, "Champions League", estimated = true),
        match("m5", "2026-10-11", 1, 1),
        match("m4", "2026-10-04", 3, 0, estimated = true),
        match("m3", "2026-09-27", 2, 1),
        match("m2", "2026-09-20", 0, 1),
        match("m1", "2026-09-13", 4, 0, "League Cup")
    )

    @Test
    fun competitionsAreSortedAndStartWithAll() {
        assertEquals(listOf("All", "Champions League", "League Cup", "Premier League"), MatchInsights.competitions(matches))
        assertEquals(listOf("All"), MatchInsights.competitions(emptyList()))
    }

    @Test
    fun filtersCombine() {
        assertEquals(6, MatchInsights.filter(matches, "All", ResultFilter.ALL).size)
        assertEquals(listOf("m4", "m3", "m1"), MatchInsights.filter(matches, "All", ResultFilter.WINS).map { it.id })
        assertEquals(listOf("m5"), MatchInsights.filter(matches, "All", ResultFilter.DRAWS).map { it.id })
        assertEquals(listOf("m6", "m2"), MatchInsights.filter(matches, "All", ResultFilter.LOSSES).map { it.id })
        assertEquals(listOf("m6", "m4"), MatchInsights.filter(matches, "All", ResultFilter.ESTIMATED).map { it.id })
        assertEquals(listOf("m5", "m4", "m3", "m2"), MatchInsights.filter(matches, "Premier League", ResultFilter.ALL).map { it.id })
        assertEquals(listOf("m4", "m3"), MatchInsights.filter(matches, "Premier League", ResultFilter.WINS).map { it.id })
        assertTrue(MatchInsights.filter(matches, "League Cup", ResultFilter.LOSSES).isEmpty())
        assertEquals(2, MatchInsights.estimatedCount(matches))
    }

    @Test
    fun recordAddsUpTheMatches() {
        val r = MatchInsights.record(matches)
        assertEquals(6, r.played)
        assertEquals(3, r.won)
        assertEquals(1, r.drawn)
        assertEquals(2, r.lost)
        assertEquals(10, r.goalsFor)
        assertEquals(5, r.goalsAgainst)
        assertEquals(5, r.goalDifference)
        assertEquals(10, r.points)
        val none = MatchInsights.record(emptyList())
        assertEquals(0, none.played)
        assertEquals(0, none.points)
    }

    @Test
    fun formShowsTheNewestResultOnTheRight() {
        // last five by date: m6 L, m5 D, m4 W, m3 W, m2 L -> oldest first
        assertEquals(listOf("L", "W", "W", "D", "L"), MatchInsights.form(matches, 5))
        // the last three by date: m6 L, m5 D, m4 W -> oldest first
        assertEquals(listOf("W", "D", "L"), MatchInsights.form(matches, 3))
    }

    @Test
    fun formDoesNotDependOnTheListOrder() {
        val shuffled = listOf(matches[3], matches[0], matches[5], matches[1], matches[4], matches[2])
        assertEquals(MatchInsights.form(matches, 5), MatchInsights.form(shuffled, 5))
        assertEquals(MatchInsights.form(matches, 3), MatchInsights.form(shuffled, 3))
    }

    @Test
    fun formOfFewerMatchesThanAsked() {
        assertEquals(listOf("W"), MatchInsights.form(listOf(match("a", "2026-01-01", 1, 0)), 5))
        assertTrue(MatchInsights.form(emptyList(), 5).isEmpty())
    }

    @Test
    fun sameDayMatchesKeepTheListOrder() {
        // two matches the same day: the list holds the newest first, so the first one is the newer
        val day = listOf(match("late", "2026-05-01", 1, 0), match("early", "2026-05-01", 0, 1))
        assertEquals(listOf("L", "W"), MatchInsights.form(day, 2))
    }

    @Test
    fun seasonLabels() {
        assertEquals("2025/26", MatchInsights.seasonLabel(2025))
        assertEquals("1999/00", MatchInsights.seasonLabel(1999))
        assertEquals("2008/09", MatchInsights.seasonLabel(2008))
    }
}
