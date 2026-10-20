package com.fctracker.career.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

class NoticeFilterTest {
    private val match = TelemetryNotice("Full time", "2-0", "matches", false)
    private val transfer = TelemetryNotice("Signed", "X", "transfers", true)
    private val injury = TelemetryNotice("Injury", "Y", "squad", false)
    private val other = TelemetryNotice("Note", "Z", "dashboard", false)
    private val all = listOf(match, transfer, injury, other)

    @Test
    fun everythingIsOnByDefault() {
        assertEquals(all, NoticeFilter.filter(all, NotificationPrefs()))
    }

    @Test
    fun eachSwitchOnlyAffectsItsOwnKind() {
        assertEquals(listOf(transfer, injury, other), NoticeFilter.filter(all, NotificationPrefs(matches = false)))
        assertEquals(listOf(match, injury, other), NoticeFilter.filter(all, NotificationPrefs(transfers = false)))
        assertEquals(listOf(match, transfer, other), NoticeFilter.filter(all, NotificationPrefs(injuries = false)))
        assertEquals(listOf(other), NoticeFilter.filter(all, NotificationPrefs(false, false, false)))
    }

    @Test
    fun kinds() {
        assertEquals(NoticeKind.MATCH, NoticeFilter.kindOf(match))
        assertEquals(NoticeKind.TRANSFER, NoticeFilter.kindOf(transfer))
        assertEquals(NoticeKind.INJURY, NoticeFilter.kindOf(injury))
        assertEquals(NoticeKind.OTHER, NoticeFilter.kindOf(other))
    }
}
