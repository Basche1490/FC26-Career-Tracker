package com.fctracker.career.telemetry

import com.fctracker.career.AppState
import com.fctracker.career.MatchResult
import com.fctracker.career.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractTimeTest {

    @Test
    fun timeLeftMatchesTheGamesOwnScreen() {
        // Checked against the game: in August 2028 a contract running to 2031 shows "2y 10m".
        val months = ContractTime.monthsLeft("2031-06-30", "2028-08-15")
        assertEquals(34, months)
        assertEquals("2y 10m", ContractTime.label(months!!))
    }

    @Test
    fun monthsAreRoundedDown() {
        assertEquals(10, ContractTime.monthsLeft("2027-06-30", "2026-08-09"))
        assertEquals(10, ContractTime.monthsLeft("2027-06-30", "2026-08-30"))
        assertEquals(9, ContractTime.monthsLeft("2027-06-30", "2026-08-31"))   // 30 June is before the 31st: not yet a full month
        assertEquals(0, ContractTime.monthsLeft("2026-06-30", "2026-06-10"))
        assertEquals(-1, ContractTime.monthsLeft("2026-06-30", "2026-07-10"))
    }

    @Test
    fun labels() {
        assertEquals("Over", ContractTime.label(-3))
        assertEquals("<1m", ContractTime.label(0))
        assertEquals("1m", ContractTime.label(1))
        assertEquals("11m", ContractTime.label(11))
        assertEquals("1y", ContractTime.label(12))
        assertEquals("1y 1m", ContractTime.label(13))
        assertEquals("3y 11m", ContractTime.label(47))
        assertEquals("4y", ContractTime.label(48))
    }

    @Test
    fun urgencyThresholds() {
        assertEquals(Urgency.URGENT, ContractTime.contractUrgency(5))
        assertEquals(Urgency.SOON, ContractTime.contractUrgency(6))
        assertEquals(Urgency.SOON, ContractTime.contractUrgency(11))
        assertEquals(Urgency.OK, ContractTime.contractUrgency(12))
        assertEquals(Urgency.URGENT, ContractTime.loanUrgency(0))
        assertEquals(Urgency.SOON, ContractTime.loanUrgency(2))
        assertEquals(Urgency.OK, ContractTime.loanUrgency(3))
    }

    @Test
    fun datesThatAreNotDatesGiveNothing() {
        assertNull(ContractTime.monthsLeft(null, "2026-08-09"))
        assertNull(ContractTime.monthsLeft("2027-06-30", null))
        assertNull(ContractTime.monthsLeft("soon", "2026-08-09"))
        assertNull(ContractTime.monthsLeft("2027-13-01", "2026-08-09"))
        assertFalse(ContractTime.isDate("2026-8-9"))
        assertTrue(ContractTime.isDate("2026-08-09"))
        assertEquals("", ContractTime.monthYear("nope"))
        assertEquals("Jun 2029", ContractTime.monthYear("2029-06-30"))
        assertEquals("1 Jul 2029", ContractTime.fullDate("2029-07-01"))
    }

    @Test
    fun referenceDateIsTheGamesDateNeverThePhonesClock() {
        val match = MatchResult(opponentName = "A", isHome = true, goalsFor = 1, goalsAgainst = 0, matchDate = "2026-09-02", competition = "League")
        val older = match.copy(matchDate = "2026-08-20")
        assertNull(ContractTime.referenceDate(AppState()))
        assertEquals("2026-09-02", ContractTime.referenceDate(AppState(recentMatches = listOf(older, match))))
        assertEquals("2026-10-01", ContractTime.referenceDate(AppState(careerDate = "2026-10-01", recentMatches = listOf(match))))
        assertEquals("2026-09-02", ContractTime.referenceDate(AppState(careerDate = "garbage", recentMatches = listOf(match))))
    }

    @Test
    fun chipsAndAttentionList() {
        val now = "2026-08-09"
        val safe = Player(id = "a", name = "Safe", contractEnd = "2030-06-30")
        val soon = Player(id = "b", name = "Soon", contractEnd = "2027-06-30")        // 10 months
        val urgent = Player(id = "c", name = "Urgent", contractEnd = "2027-01-31")    // 5 months
        val unknown = Player(id = "d", name = "Unknown")                              // nothing reported: nothing shown
        val loanSoon = Player(id = "e", name = "Loan", contractEnd = "2030-06-30", loanDirection = "in", loanEnd = "2026-10-15")
        val loanNoDate = Player(id = "f", name = "NoDate", contractEnd = "2030-06-30", loanDirection = "out")

        assertEquals("3y 10m", ContractTime.contractChip(safe, now)!!.text)
        assertEquals(Urgency.SOON, ContractTime.contractChip(soon, now)!!.urgency)
        assertEquals(Urgency.URGENT, ContractTime.contractChip(urgent, now)!!.urgency)
        assertNull(ContractTime.contractChip(unknown, now))
        assertNull(ContractTime.contractChip(safe, null))
        assertNull(ContractTime.loanChip(safe, now))                        // not on loan
        assertNull(ContractTime.loanChip(loanNoDate, now))                  // on loan but the game gave no end date
        assertEquals(Urgency.SOON, ContractTime.loanChip(loanSoon, now)!!.urgency)

        val list = ContractTime.attentionList(listOf(safe, soon, urgent, unknown, loanSoon, loanNoDate), now)
        assertEquals(listOf("e", "c", "b"), list.map { it.id })             // soonest first: 2, 5 and 10 months
        assertTrue(ContractTime.attentionList(listOf(safe, soon), null).isEmpty())   // no game date: nothing is claimed
    }
}
