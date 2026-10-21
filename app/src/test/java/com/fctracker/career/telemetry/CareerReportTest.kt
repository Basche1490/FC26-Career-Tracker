package com.fctracker.career.telemetry

import com.fctracker.career.AppState
import com.fctracker.career.Fixture
import com.fctracker.career.LeagueTableData
import com.fctracker.career.MatchResult
import com.fctracker.career.Player
import com.fctracker.career.Source
import com.fctracker.career.TableRow
import com.fctracker.career.TeamTrophy
import com.fctracker.career.WorldWinner
import com.fctracker.career.logic.CareerOps
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CareerReportTest {

    private val pike = Player(id = "p2", name = "Jonah Pike", gamePlayerId = 222, overall = 76, wageK = 20)
    private val varela = Player(id = "p1", name = "Marco Varela", gamePlayerId = 111, overall = 78)
    private val base = AppState(clubName = "Your Club", leagueName = "EFL Championship", startYear = 2026, players = listOf(varela, pike), careerDate = "2026-10-10",
        fixtures = listOf(Fixture(id = "m1", date = "2026-11-20", opponent = "Typed FC", competition = "Friendly", source = Source.MANUAL)))

    private fun report(json: String) = CareerReport.apply(base, JSONObject(json))

    @Test fun fixturesReplaceTheGamesOwnButKeepTypedOnes() {
        val r = report("""{"careerDate":"2026-10-20","fixtures":[{"key":"9","date":"2026-10-24","time":"15:00","competition":"EFL Championship","round":"Matchday 15","opponent":"Hull City","home":true}]}""")
        assertTrue(r.changed)
        assertEquals(listOf("Hull City", "Typed FC"), r.state.fixtures.map { it.opponent })
        assertEquals("2026-10-20", r.state.careerDate)
        val again = CareerReport.apply(r.state, JSONObject("""{"fixtures":[{"key":"10","date":"2026-10-28","competition":"EFL Championship","opponent":"Coventry City","home":false}]}"""))
        assertEquals(listOf("Coventry City", "Typed FC"), again.state.fixtures.map { it.opponent })
    }

    @Test fun tablesLeadersAndStats() {
        val r = report("""{"tables":[{"league":"EFL Championship","country":"England","rows":[{"club":"Coventry City","p":14,"w":10,"d":2,"l":2,"gf":30,"ga":13,"pts":32,"form":"wwdww"},{"club":"Your Club","p":14,"w":6,"d":3,"l":5,"gf":22,"ga":14,"pts":21}]}],
            "leaders":[{"competition":"EFL Championship","goals":[{"player":"Ivan Brekke","club":"Coventry City","value":11},{"player":"Marco Varela","club":"Your Club","value":9}]}],
            "stats":[{"playerId":111,"name":"Marco Varela","competition":"EFL Championship","apps":14,"goals":9,"assists":3,"rating":7.6}]}""")
        val t = r.state.leagueTables.single()
        assertEquals("WWDWW", t.rows[0].form)
        assertTrue(t.rows[1].isUser)
        assertTrue(r.state.leaders.single().goals[1].isUser)
        assertEquals(9, r.state.squadStats.single().goals)
    }

    @Test fun injuriesFollowTheGameUnlessThePersonChangedTheDate() {
        val r = report("""{"careerDate":"2026-10-12","players":[{"playerId":222,"name":"Jonah Pike","number":8,"role":"Crucial","wage":28,"injury":{"name":"Hamstring strain","start":"2026-10-10","back":"2026-10-31"}}]}""")
        val p = r.state.players.first { it.id == "p2" }
        assertTrue(p.isInjured)
        assertEquals("2026-10-31", p.injuryReturnDate)
        assertEquals(Source.GAME, p.injurySource)
        assertEquals(8, p.number); assertEquals("Crucial", p.squadRole); assertEquals(28, p.wageK)
        assertEquals(listOf("Jonah Pike"), r.newInjuries)

        // The person says a week longer and turns "Follow the game" off: the game's later reports keep his date.
        val edited = CareerOps.setInjury(r.state, "p2", "Hamstring strain", "2026-10-10", "2026-11-07", followGame = false, note = "Setback")
        val later = CareerReport.apply(edited, JSONObject("""{"careerDate":"2026-10-20","players":[{"playerId":222,"name":"Jonah Pike","injury":{"name":"Hamstring strain","start":"2026-10-10","back":"2026-11-02"}}]}"""))
        val p2 = later.state.players.first { it.id == "p2" }
        assertEquals("2026-11-07", p2.injuryReturnDate)
        assertEquals("2026-11-02", p2.injuryGameReturnDate)
        assertFalse(p2.injuryFollowGame)
        assertTrue(later.newInjuries.isEmpty())

        // With "Follow the game" on, the game clears him once he is fit.
        val following = CareerReport.apply(r.state, JSONObject("""{"careerDate":"2026-11-01","players":[{"playerId":222,"name":"Jonah Pike"}]}"""))
        assertFalse(following.state.players.first { it.id == "p2" }.isInjured)
    }

    @Test fun aManualInjuryIsNeverClearedByTheGame() {
        val typed = CareerOps.setInjury(base, "p1", "Ankle knock", "2026-10-18", "2026-10-25", followGame = true, note = null)
        val r = CareerReport.apply(typed, JSONObject("""{"players":[{"playerId":111,"name":"Marco Varela"}]}"""))
        assertTrue(r.state.players.first { it.id == "p1" }.isInjured)
    }

    @Test fun suspensionsAndAttributes() {
        val r = report("""{"players":[{"playerId":111,"name":"Marco Varela","suspension":{"games":2,"end":"2026-10-30"},"attributes":{"Finishing":81,"Composure":77}}]}""")
        val p = r.state.players.first { it.id == "p1" }
        assertEquals(2, p.suspendedGames)
        assertEquals(81, p.attributes["Finishing"])
        assertEquals(p.attributes, p.seasonStartAttributes)
        val later = CareerReport.apply(r.state, JSONObject("""{"players":[{"playerId":111,"name":"Marco Varela","attributes":{"Finishing":83,"Composure":77}}]}"""))
        val q = later.state.players.first { it.id == "p1" }
        assertEquals(83, q.attributes["Finishing"]); assertEquals(81, q.seasonStartAttributes["Finishing"]); assertEquals(0, q.suspendedGames)
    }

    @Test fun aroundTheWorldOnlyChangesInJune() {
        val json = """{"holders":[{"region":"Europe","competition":"UEFA Champions League","winner":"Real Madrid","runnerUp":"Arsenal","score":"2–1"},{"region":"World","competition":"FIFA World Cup","winner":"Spain"}],
            "topScorers":[{"region":"England","competition":"Premier League","player":"Rafael Duarte","club":"Arsenal","goals":27}]}"""
        assertTrue(report(json).state.worldWinners.isEmpty())
        val june = CareerReport.apply(base.copy(careerDate = "2027-06-12"), JSONObject(json))
        assertEquals(1, june.state.worldWinners.size)
        assertEquals(2026, june.state.worldWinners.single().season)
        assertEquals(27, june.state.worldTopScorers.single().goals)
    }

    @Test fun awardsAreAddedOnce() {
        val json = """{"awards":[{"key":"potm-9","title":"Player of the Month","recipient":"Marco Varela","period":"September 2026"}]}"""
        val once = report(json)
        val twice = CareerReport.apply(once.state, JSONObject(json))
        assertEquals(1, twice.state.awards.size)
        assertFalse(twice.changed)
    }

    @Test fun coefficientsClubAndTeamSheet() {
        val r = report("""{"coefficients":[{"rank":2,"club":"Your Club","country":"England","season":1.5,"total":20.0},{"rank":1,"club":"Liverpool","country":"England","season":2.0,"total":102.7}],
            "nations":[{"country":"England","points":97.425}],
            "club":{"founded":"1898","stadium":"Riverside Park","capacity":32500,"rival":"Millwall","leagueTitles":1,"domesticCups":3,"clubWorth":312,"prestigeHome":6,"transferBudget":4.2},
            "teamsheet":{"formation":"4-2-3-1","xi":[111,222],"bench":[]}}""")
        assertEquals("Liverpool", r.state.coefficients.first().club)
        assertTrue(r.state.coefficients[1].isUser)
        assertEquals("Riverside Park", r.state.clubInfo.stadium)
        assertEquals("€312M", r.state.clubInfo.clubWorth)
        assertEquals(4.2, r.state.transferBudget, 1e-9)
        assertEquals(listOf(111, 222), r.state.gameTeamSheet?.starting)
    }

    @Test fun theProcessorPassesCareerReportsThroughAndAlertsNewInjuries() {
        val payload = JSONObject().put("time", 1000L).put("title", "Career").put("message",
            """{"dataType":"career","careerDate":"2026-10-12","players":[{"playerId":222,"name":"Jonah Pike","injury":{"name":"Hamstring strain","start":"2026-10-10","back":"2026-10-31"}}]}""")
        val r = TelemetryProcessor.apply(base, listOf(payload), notify = true, nowSeconds = 1000L, today = "2026-10-12")
        assertTrue(r.state.players.first { it.id == "p2" }.isInjured)
        assertEquals(1, r.notices.size)
        assertEquals("squad", r.notices.single().route)
        assertTrue(r.state.liveEvents.any { it.title == "Injury" })
    }
}

class CareerOpsTest {
    private val varela = Player(id = "p1", name = "Marco Varela", overall = 78, startingOverall = 75, birthDate = "1999-03-01", age = 27, matchesPlayed = 14, goals = 9, attributes = mapOf("Finishing" to 81))
    private val state = AppState(clubName = "Your Club", leagueName = "EFL Championship", startYear = 2026, players = listOf(varela),
        recentMatches = listOf(MatchResult(opponentName = "Hull City", isHome = true, goalsFor = 2, goalsAgainst = 0, matchDate = "2027-05-02", competition = "EFL Championship", leaguePosition = 4)),
        leagueTables = listOf(LeagueTableData(league = "EFL Championship", rows = listOf(TableRow("A", 46, 30, 6, 10, 80, 40, 96), TableRow("Your Club", 46, 25, 10, 11, 70, 45, 85, isUser = true)))),
        teamTrophies = listOf(TeamTrophy(name = "FA Cup 2026/27")),
        fixtures = listOf(Fixture(date = "2027-05-30", opponent = "Old"), Fixture(date = "2027-08-08", opponent = "New season")))

    @Test fun endOfSeasonArchivesEveryPageAndStartsTheNext() {
        val next = CareerOps.endSeason(state)
        val past = next.historicalSeasons.single()
        assertEquals(2026, past.year)
        assertEquals(2, past.finalPosition)
        assertEquals(1, past.leagueTables.size)
        assertEquals(listOf("FA Cup 2026/27"), past.trophies)
        assertEquals(2027, next.startYear)
        val p = next.players.single()
        assertEquals(0, p.matchesPlayed)
        assertEquals(78, p.startingOverall)
        assertEquals(78, p.ratingHistory.single().overall)
        assertEquals(28, p.age)
        assertEquals(listOf("New season"), next.fixtures.map { it.opponent })
        assertTrue(next.leagueTables.single().rows.all { it.points == 0 })
        assertTrue(next.clubInfo.seasonLines.single().startsWith("2026/27: 2nd in the EFL Championship"))
        assertNull(next.viewingSeasonYear)
    }

    @Test fun upcomingFixturesSkipPlayedOnes() {
        val s = state.copy(careerDate = "2027-05-01", fixtures = listOf(Fixture(date = "2027-05-02", opponent = "Hull City"), Fixture(date = "2027-05-09", opponent = "Leeds United")))
        assertEquals(listOf("Leeds United"), CareerOps.upcoming(s).map { it.opponent })
        assertEquals(1, CareerOps.dropPlayedFixture(s, s.recentMatches.first()).fixtures.size)
    }

    @Test fun worldReminderIsForConsoleInJune() {
        val s = state.copy(platform = "console", careerDate = "2027-06-05")
        assertTrue(CareerOps.worldReminderDue(s))
        assertFalse(CareerOps.worldReminderDue(s.copy(worldReminderDismissed = 2026)))
        assertFalse(CareerOps.worldReminderDue(s.copy(platform = "pc")))
        assertFalse(CareerOps.worldReminderDue(s.copy(worldWinners = listOf(WorldWinner(season = 2026, competition = "X", winner = "Y")))))
        assertEquals(2026, CareerOps.worldSeasonToFill(s))
    }

    @Test fun teamSheetNeedsSevenKnownPlayers() {
        assertNull(CareerOps.applyTeamSheet(state))
        val many = (1..11).map { Player(id = "x$it", name = "P$it", gamePlayerId = it) }
        val s = state.copy(players = many, gameTeamSheet = com.fctracker.career.TeamSheet(formation = "4-4-2", starting = (1..11).toList()))
        val applied = CareerOps.applyTeamSheet(s)
        assertNotNull(applied)
        assertEquals("4-4-2", applied!!.activeFormation)
        assertTrue(applied.players.all { it.isStartingXI })
    }
}
