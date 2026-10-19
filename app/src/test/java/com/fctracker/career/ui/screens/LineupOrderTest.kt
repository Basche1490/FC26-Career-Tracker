package com.fctracker.career.ui.screens

import com.fctracker.career.PlayerPerformance
import com.fctracker.career.Substitution
import org.junit.Assert.assertEquals
import org.junit.Test

class LineupOrderTest {

    private val positions = mapOf(
        "gk" to "GK", "rb" to "RB", "rcb" to "RCB", "lcb" to "LCB", "lb" to "LB",
        "rdm" to "RDM", "ldm" to "LDM", "cam" to "CAM", "rw" to "RW", "lw" to "LW", "st" to "ST",
        "sub_cdm" to "CDM", "sub_cam" to "CAM", "loanee" to null
    )

    private fun p(id: String, subIn: Int? = null, subOut: Int? = null) =
        PlayerPerformance(playerId = id, subInMinute = subIn, subOutMinute = subOut)

    private fun order(perfs: List<PlayerPerformance>, subs: List<Substitution> = emptyList()) =
        LineupOrder.order(perfs, subs, positionOf = { positions[it.playerId] }, nameOf = { it.playerId }).map { it.playerId }

    @Test
    fun strikerFirstGoalkeeperLastAndEachSubstituteUnderThePlayerHeReplaced() {
        val perfs = listOf(
            p("gk"), p("rb"), p("rcb"), p("lcb"), p("lb"),
            p("rdm", subOut = 45), p("ldm"), p("cam", subOut = 57),
            p("rw"), p("lw"), p("st"),
            p("sub_cdm", subIn = 45), p("sub_cam", subIn = 57)
        ).shuffled(java.util.Random(7))
        val subs = listOf(
            Substitution(minute = 45, playerOutId = "rdm", playerInId = "sub_cdm"),
            Substitution(minute = 57, playerOutId = "cam", playerInId = "sub_cam")
        )
        assertEquals(
            listOf("st", "lw", "rw", "cam", "sub_cam", "ldm", "rdm", "sub_cdm", "lb", "rb", "lcb", "rcb", "gk"),
            order(perfs, subs)
        )
    }

    @Test
    fun aMatchEditedByHandPairsSubstitutesByTheMinute() {
        val perfs = listOf(p("gk"), p("st", subOut = 70), p("cam", subOut = 70), p("sub_cam", subIn = 70), p("sub_cdm", subIn = 70))
        // the substitute who plays the same line as a player who went off goes under him; the other one takes the
        // remaining player who went off
        assertEquals(listOf("st", "sub_cdm", "cam", "sub_cam", "gk"), order(perfs))
    }

    @Test
    fun aPlayerWhosePositionIsNotKnownIsListedAfterTheKnownOnes() {
        val perfs = listOf(p("loanee"), p("gk"), p("st"), p("lcb"))
        assertEquals(listOf("st", "lcb", "gk", "loanee"), order(perfs))
    }

    @Test
    fun aSubstituteWhoseReplacedPlayerIsMissingStillAppears() {
        val perfs = listOf(p("st"), p("sub_cam", subIn = 60))
        assertEquals(listOf("st", "sub_cam"), order(perfs, listOf(Substitution(minute = 60, playerOutId = "nobody", playerInId = "sub_cam"))))
    }

    @Test
    fun aSecondSubstitutionChainsUnderTheFirstSubstitute() {
        val perfs = listOf(p("st", subOut = 30), p("sub_cam", subIn = 30, subOut = 80), p("sub_cdm", subIn = 80), p("gk"))
        val subs = listOf(
            Substitution(minute = 30, playerOutId = "st", playerInId = "sub_cam"),
            Substitution(minute = 80, playerOutId = "sub_cam", playerInId = "sub_cdm")
        )
        assertEquals(listOf("st", "sub_cam", "sub_cdm", "gk"), order(perfs, subs))
    }

    @Test
    fun positionLabelsRankFromStrikerToGoalkeeper() {
        val labels = listOf("ST", "RW", "CAM", "LM", "CM", "CDM", "LWB", "CB", "GK")
        assertEquals(labels.indices.toList(), labels.map { LineupOrder.rank(it) })
        assertEquals(LineupOrder.UNKNOWN_RANK, LineupOrder.rank(null))
        assertEquals(LineupOrder.UNKNOWN_RANK, LineupOrder.rank("??"))
        assertEquals(listOf(0, 1, 2), listOf("LB", "CB", "RB").map { LineupOrder.side(it) })
    }
}
