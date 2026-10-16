package com.fctracker.career

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Backups are plain Gson JSON of [AppState]; these tests protect what ends up in them and what can be read back. */
class ModelsJsonTest {
    private val gson = Gson()

    @Test
    fun aCareerSurvivesAJsonRoundTrip() {
        val state = AppState(
            clubName = "Wrexham",
            players = listOf(Player(id = "a", name = "Paul Mullin", goals = 12)),
            recentMatches = listOf(
                MatchResult(
                    opponentName = "Stockport", isHome = true, goalsFor = 2, goalsAgainst = 1, matchDate = "2026-01-01",
                    competition = "League One", statsEstimated = true,
                    goalDetails = listOf(GoalDetail(minute = 10, scorerId = "a"))
                )
            )
        )
        val back = gson.fromJson(gson.toJson(state), AppState::class.java)
        assertEquals(state, back)
        assertTrue(back.recentMatches.single().statsEstimated)
    }

    @Test
    fun anExportedBackupNeverContainsAnApiKey() {
        val json = gson.toJson(AppState(aiAgent = "Claude", aiModel = "some-model"))
        assertFalse(json.contains("aiApiKey"))
    }

    @Test
    fun anOldBackupWithAKeyAndWithoutNewFieldsStillImports() {
        val old = """{"clubName":"Old FC","aiApiKey":"sk-old","recentMatches":[
            {"opponentName":"X","isHome":true,"goalsFor":1,"goalsAgainst":0,"matchDate":"2025-09-01","competition":"League"}]}"""
        val state = gson.fromJson(old, AppState::class.java)
        assertEquals("Old FC", state.clubName)
        assertFalse(state.recentMatches.single().statsEstimated) // new field defaults to "not estimated"
        assertEquals("", state.aiModel)
    }

    @Test
    fun anArchivedSeasonKeepsItsObjectivesAndOldOnesLoadWithNone() {
        val season = HistoricalSeason(
            year = 2025, leagueName = "League One",
            objectives = listOf(Objective(id = "o1", category = "Financial", priority = "High", description = "Profit", status = "Completed"))
        )
        val back = gson.fromJson(gson.toJson(AppState(historicalSeasons = listOf(season))), AppState::class.java)
        assertEquals(season, back.historicalSeasons.single())

        // A season archived by the earlier app has no "objectives" field: it must load as an empty list, not null.
        val old = gson.fromJson("""{"historicalSeasons":[{"year":2024,"leagueName":"League Two"}]}""", AppState::class.java)
        assertTrue(old.historicalSeasons.single().objectives.isEmpty())
    }
}
