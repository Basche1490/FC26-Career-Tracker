package com.fctracker.career.insights

import com.fctracker.career.TransferLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferInsightsTest {

    private fun deal(name: String, type: String, fee: Double, date: String, unknown: Boolean = false) =
        TransferLog(playerName = name, type = type, feeM = fee, fromToClub = "Club", date = date, feeUnknown = unknown)

    private val logs = listOf(
        deal("A", "In (Permanent)", 25.0, "2026-07-10"),
        deal("B", "Out (Permanent)", 12.5, "2026-08-30"),
        deal("C", "In (Loan)", 0.0, "2026-09-01", unknown = true),
        deal("D", "Free Agent Sign", 0.0, "2026-06-20"),
        deal("E", "Out (Permanent)", 8.0, "2027-01-15"),
        deal("F", "In (Permanent)", 4.0, "2026-12-30"),
        deal("G", "Retirement", 0.0, "2026-11-05"),
        deal("H", "Sold", 3.0, "2027-04-02")
    )

    @Test
    fun summaryCountsMoneyAndDeals() {
        val s = TransferInsights.summarise(logs)
        assertEquals(29.0, s.spent, 0.0001)
        assertEquals(23.5, s.received, 0.0001)
        assertEquals(4, s.signings)          // A, C, D, F
        assertEquals(3, s.departures)        // B, E, H (the retirement is neither)
        assertEquals(1, s.unknownFees)       // C
        assertEquals(-5.5, s.net, 0.0001)
    }

    @Test
    fun retirementsAndEmptyListsAreHarmless() {
        val s = TransferInsights.summarise(listOf(deal("G", "Retirement", 0.0, "2026-11-05")))
        assertEquals(0, s.signings + s.departures)
        assertEquals(0.0, s.net, 0.0)
        assertEquals(0, TransferInsights.summarise(emptyList()).unknownFees)
    }

    @Test
    fun floatingPointDustIsRoundedAway() {
        val dust = listOf(deal("A", "In (Permanent)", 0.1, "2026-07-01"), deal("B", "In (Permanent)", 0.2, "2026-07-02"))
        assertEquals(0.3, TransferInsights.summarise(dust).spent, 0.0)
    }

    @Test
    fun windowsFollowTheMonths() {
        val windows = TransferInsights.windows(logs)
        // newest first: Spring 2027 in-season (H), then winter 2026/27 (E, F), then in season Oct-Nov 2026 (G), then summer 2026
        val labels = windows.map { it.label }
        assertEquals(listOf("In season 2026/27", "Winter 2026/27", "Summer 2026"), labels)
        val summer = windows.first { it.kind == WindowKind.SUMMER }
        assertEquals(listOf("A", "B", "C", "D"), summer.logs.map { it.playerName }.sorted())
        val winter = windows.first { it.kind == WindowKind.WINTER }
        assertEquals(listOf("E", "F"), winter.logs.map { it.playerName }.sorted())
        val inSeason = windows.first { it.kind == WindowKind.IN_SEASON }
        assertEquals(listOf("G", "H"), inSeason.logs.map { it.playerName }.sorted())
        assertEquals(25.0, summer.summary.spent, 0.0001)
        assertEquals(12.5, summer.summary.received, 0.0001)
    }

    @Test
    fun theWindowOfADealIsKeptAcrossTheNewYear() {
        val w = TransferInsights.windows(listOf(deal("X", "In (Permanent)", 1.0, "2026-12-31"), deal("Y", "In (Permanent)", 1.0, "2027-02-01")))
        assertEquals(1, w.size)
        assertEquals("Winter 2026/27", w[0].label)
    }

    @Test
    fun twoSummersAreTwoWindows() {
        val w = TransferInsights.windows(listOf(deal("X", "In (Permanent)", 1.0, "2026-07-01"), deal("Y", "In (Permanent)", 1.0, "2027-07-01")))
        assertEquals(listOf("Summer 2027", "Summer 2026"), w.map { it.label })
    }

    @Test
    fun dealsWithoutADateAreInNoWindow() {
        assertTrue(TransferInsights.windows(listOf(deal("X", "In (Permanent)", 1.0, ""), deal("Y", "In (Permanent)", 1.0, "soon"))).isEmpty())
    }

    @Test
    fun moneyText() {
        assertEquals("€12.5M", TransferInsights.money(12.5, "€"))
        assertEquals("+€12.5M", TransferInsights.money(12.5, "€", signed = true))
        assertEquals("-€3.0M", TransferInsights.money(-3.0, "€", signed = true))
        assertEquals("-€3.0M", TransferInsights.money(-3.0, "€"))
        assertEquals("€0.0M", TransferInsights.money(0.0, "€", signed = true))
        assertEquals("€0.0M", TransferInsights.money(-0.01, "€", signed = true))
    }
}
