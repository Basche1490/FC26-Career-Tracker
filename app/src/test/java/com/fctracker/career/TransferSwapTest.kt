package com.fctracker.career

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferSwapTest {

    private fun deal(type: String, player: String = "Star Striker", swap: String? = "Squad Veteran", club: String = "Rival FC") =
        TransferLog(
            playerName = player, type = type, feeM = 5.0, fromToClub = club, date = "2026-08-01",
            includeSwap = swap != null, swapPlayerName = swap
        )

    @Test
    fun aSigningWithASwapSendsTheSwapPlayerTheOtherWay() {
        val other = deal("In (Permanent)").swapCounterpart(emptyList())
        assertNotNull(other)
        other!!
        assertEquals("Squad Veteran", other.playerName)
        assertEquals("Out (Permanent)", other.type)
        assertEquals("Rival FC", other.fromToClub)
        assertEquals("2026-08-01", other.date)
        assertEquals("Star Striker", other.swapPlayerName) // the two entries point at each other
        assertEquals(0.0, other.feeM, 0.0)
        assertFalse(other.includeSwap)
        assertFalse(other.isIncomingDeal())
    }

    @Test
    fun aSaleWithASwapBringsTheSwapPlayerIn() {
        val other = deal("Out (Permanent)", player = "Squad Veteran", swap = "Star Striker").swapCounterpart(emptyList())!!
        assertEquals("Star Striker", other.playerName)
        assertEquals("In (Permanent)", other.type)
        assertTrue(other.isIncomingDeal())
        assertEquals("", other.position) // the player database supplies the position
    }

    @Test
    fun loansAndFreeSigningsAreCountedByDirection() {
        assertEquals("Out (Permanent)", deal("In (Loan)").swapCounterpart(emptyList())!!.type)
        assertEquals("Out (Permanent)", deal("Free Agent Sign").swapCounterpart(emptyList())!!.type)
        assertEquals("In (Permanent)", deal("Out (Loan)").swapCounterpart(emptyList())!!.type)
    }

    @Test
    fun noSwapNoCounterpart() {
        assertNull(deal("In (Permanent)", swap = null).swapCounterpart(emptyList()))
        // the box is ticked but nobody was chosen
        assertNull(deal("In (Permanent)", swap = "  ").swapCounterpart(emptyList()))
        // a swap that is switched off again keeps no counterpart
        assertNull(deal("In (Permanent)").copy(includeSwap = false).swapCounterpart(emptyList()))
        // a player cannot be swapped for himself
        assertNull(deal("In (Permanent)", swap = "star striker").swapCounterpart(emptyList()))
        assertNull(deal("Retirement").swapCounterpart(emptyList()))
    }

    @Test
    fun savingTheSameDealAgainAddsNothing() {
        val main = deal("In (Permanent)")
        val first = main.swapCounterpart(listOf(main))!!
        assertNull(main.swapCounterpart(listOf(main, first)))
        // a deal on another day or with another club is a different deal
        assertNotNull(main.copy(date = "2026-09-01").swapCounterpart(listOf(main, first)))
        assertNotNull(main.copy(fromToClub = "Other FC").swapCounterpart(listOf(main, first)))
    }

    @Test
    fun theFeeShowsSwapForAPureSwapAndTheAmountWhenThereIsCash() {
        assertEquals("€5.0M", deal("In (Permanent)").feeLabel("€"))
        assertEquals("Swap", deal("In (Permanent)").copy(feeM = 0.0).feeLabel("€"))
        assertEquals("Swap", deal("In (Permanent)").swapCounterpart(emptyList())!!.feeLabel("€"))
        assertEquals("Free", TransferLog(playerName = "A", type = "In (Permanent)").feeLabel("€"))
    }

    @Test
    fun oldSavesWithoutTheFieldStillLoad() {
        val old = Gson().fromJson(
            """{"playerName":"Old Signing","type":"In (Permanent)","feeM":2.0,"includeSwap":true}""", TransferLog::class.java
        )
        assertNull(old.swapPlayerName)
        assertNull(old.swapCounterpart(emptyList()))

        val back = Gson().fromJson(Gson().toJson(deal("In (Permanent)")), TransferLog::class.java)
        assertEquals("Squad Veteran", back.swapPlayerName)
    }
}
