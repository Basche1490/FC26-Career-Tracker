package com.fctracker.career.ui.screens

import com.fctracker.career.AppState
import com.fctracker.career.GoalDetail
import com.fctracker.career.MatchResult
import com.fctracker.career.Player
import com.fctracker.career.PlayerPerformance
import com.fctracker.career.Substitution
import com.fctracker.career.isPlaceholderPlayerName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchLabelsTest {

    private val match = MatchResult(
        opponentName = "Sunderland", isHome = true, goalsFor = 1, goalsAgainst = 3,
        matchDate = "2028-08-12", competition = "EFL Championship"
    )

    @Test
    fun placeholderNamesAreRecognised() {
        listOf("", "  ", "Player", "player 463320", "Unknown player", "Unknown", "Opponent", "463320", "Player #12")
            .forEach { assertTrue("'$it'", isPlaceholderPlayerName(it)) }
        listOf("Cole Palmer", "Player One Jones", "Marc Palomino", "Elivélton Jesus")
            .forEach { assertFalse("'$it'", isPlaceholderPlayerName(it)) }
    }

    @Test
    fun anOpposingGoalShowsTheScorersRealNameOrJustTheClub() {
        fun label(name: String?) = goalScorerLabel(GoalDetail(scorerId = null, scorerName = name, minute = 20), match, null)
        assertEquals("Cole Palmer (Sunderland)", label("Cole Palmer"))
        assertEquals("Sunderland", label("Player"))
        assertEquals("Sunderland", label("Sunderland Goal"))
        assertEquals("Sunderland", label(null))
    }

    @Test
    fun ourScorerIsShownByTheSquadsCurrentName() {
        val squad = listOf(Player(id = "p9", name = "Enrique Iglesias", nickname = "Kike"))
        val goal = GoalDetail(scorerId = "p9", scorerName = "Enrique Iglesias")
        assertEquals("Kike", goalScorerLabel(goal, match, AppState(players = squad)))
        // a scorer the squad no longer holds keeps the name the game gave; a bare id is never shown
        assertEquals("Enrique Iglesias", goalScorerLabel(goal, match, AppState()))
        assertEquals("Unknown scorer", goalScorerLabel(GoalDetail(scorerId = "463320"), match, AppState()))
    }

    @Test
    fun aLineupRowNeverShowsANumber() {
        val loanee = PlayerPerformance(playerId = "461607", playerName = null)
        // 1. the squad
        val squad = listOf(Player(id = "461607", name = "Fabián Bravo"))
        assertEquals("Fabián Bravo", lineupNameOf(loanee, squad, match))
        // 2. the name stored with the performance
        assertEquals("Fabián Bravo", lineupNameOf(loanee.copy(playerName = "Fabián Bravo"), emptyList(), match))
        // 3. old matches saved without it: the name the same match gives him as a scorer, assister or substitute
        val scorer = match.copy(goalDetails = listOf(GoalDetail(scorerId = "461607", scorerName = "Fabián Bravo")))
        assertEquals("Fabián Bravo", lineupNameOf(loanee, emptyList(), scorer))
        val assister = match.copy(goalDetails = listOf(GoalDetail(scorerId = "p1", assisterId = "461607", assisterName = "Fabián Bravo")))
        assertEquals("Fabián Bravo", lineupNameOf(loanee, emptyList(), assister))
        val sub = match.copy(substitutions = listOf(Substitution(minute = 60, playerOutId = "461607", playerInId = "p7", playerOutName = "Fabián Bravo")))
        assertEquals("Fabián Bravo", lineupNameOf(loanee, emptyList(), sub))
        // 4. nothing known at all: a plain label, never the number
        assertEquals("Unknown player", lineupNameOf(loanee, emptyList(), match))
        assertEquals("Unknown player", lineupNameOf(loanee.copy(playerName = "Player 461607"), emptyList(), match))
    }

    @Test
    fun aGuessedMinuteIsNotShown() {
        assertEquals(" 23'", goalMinuteLabel(GoalDetail(minute = 23, minuteEstimated = false)))
        assertEquals("", goalMinuteLabel(GoalDetail(minute = 23, minuteEstimated = true)))
        assertEquals("", goalMinuteLabel(GoalDetail(minute = null)))
    }

    @Test
    fun possessionIsOnlyShownWhenItIsATrustedFigure() {
        val live = match.copy(id = "live-1795000000", possession = 62)
        // an automatic match saved by an earlier version may hold a pass-count guess that is not flagged: hidden
        assertFalse(live.hasTrustedPossession)
        assertFalse(live.copy(possessionEstimated = true).hasTrustedPossession)
        // once a number is typed in (or the game gave one) it counts
        assertTrue(live.copy(possessionConfirmed = true).hasTrustedPossession)
        assertFalse(live.copy(possessionConfirmed = true, statsEstimated = true).hasTrustedPossession)
        // a match entered by hand has always been the person's own figure
        assertTrue(match.copy(id = "5f0c2c1e-6d0e-4a38-a0a0-1e2f3a4b5c6d", possession = 55).hasTrustedPossession)
        // no figure at all
        assertFalse(match.copy(id = "5f0c2c1e-6d0e-4a38-a0a0-1e2f3a4b5c6d", possession = 0).hasTrustedPossession)
    }
}
