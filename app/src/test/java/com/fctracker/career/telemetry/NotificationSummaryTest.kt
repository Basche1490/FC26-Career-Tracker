package com.fctracker.career.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSummaryTest {

    private fun match(i: Int) = TelemetryNotice("Match $i", "Full Time $i", "matches", false)
    private fun deal(i: Int) = TelemetryNotice("Deal $i", "Signed $i", "transfers", true)

    @Test
    fun aFewUpdatesAreShownOneByOne() {
        val notices = listOf(match(1), deal(1), match(2))
        assertEquals(notices, NotificationHelper.summarise(notices))
    }

    @Test
    fun aBurstBecomesOneSummary() {
        val summary = NotificationHelper.summarise(listOf(match(1), match(2), match(3), deal(1), deal(2))).single()
        assertEquals("5 new updates", summary.title)
        assertEquals("3 matches, 2 transfers", summary.message)
        assertEquals("dashboard", summary.route)
        assertFalse(summary.isTransfer)
    }

    @Test
    fun aSummaryOpensTheScreenItIsAbout() {
        val matches = NotificationHelper.summarise((1..4).map { match(it) }).single()
        assertEquals("matches", matches.route)
        assertEquals("4 matches", matches.message)
        val deals = NotificationHelper.summarise((1..4).map { deal(it) }).single()
        assertEquals("transfers", deals.route)
        assertTrue(deals.isTransfer)
    }

    @Test
    fun aSummaryOnlyCountsWhatIsWanted() {
        val many = (1..5).map { match(it) } + (1..4).map { deal(it) }
        val wanted = NoticeFilter.filter(many, NotificationPrefs(matches = false))
        val summary = NotificationHelper.summarise(wanted).single()
        assertEquals("4 transfers", summary.message)
        assertEquals("transfers", summary.route)
    }
}
