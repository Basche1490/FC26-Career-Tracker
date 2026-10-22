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
        val fit = following.state.players.first { it.id == "p2" }
        assertFalse(fit.isInjured)
        // ...and the injury stays on the injury screen under "Back from injury", fit on his return day
        assertEquals(listOf(com.fctracker.career.InjuryRecord("Hamstring strain", "2026-10-10", "2026-10-31", Source.GAME)), fit.injuryHistory)
        assertEquals(listOf("p2"), CareerOps.recentRecoveries(following.state.players, "2026-11-01").map { it.first.id })
        assertTrue(CareerOps.recentRecoveries(following.state.players, "2027-01-30").isEmpty())

        // declared fit before his return day: fit on the day of the report
        val early = CareerReport.apply(r.state, JSONObject("""{"careerDate":"2026-10-20","players":[{"playerId":222,"name":"Jonah Pike"}]}"""))
        assertEquals("2026-10-20", early.state.players.first { it.id == "p2" }.injuryHistory.single().end)
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

    /** The exact text the capture script produces (written by tests/lua/test_transfer_logic.lua, scenario 7c F). */
    private fun scriptSample(): String {
        javaClass.classLoader?.getResource("career_report_from_script.json")?.let { return it.readText() }
        val candidates = listOf("src/test/resources", "test/resources", "app/src/test/resources")
        return candidates.map { java.io.File(it, "career_report_from_script.json") }.first { it.isFile }.readText()
    }

    @Test fun theCaptureScriptsReportIsUnderstood() {
        val club = AppState(clubName = "Torquay AFC", leagueName = "EFL Championship", startYear = 2028,
            players = listOf(Player(id = "a", name = "Enrique Iglesias", gamePlayerId = 461483), Player(id = "b", name = "Ignacio Zapata", gamePlayerId = 460053)))
        val r = CareerReport.apply(club, JSONObject(scriptSample()))
        assertTrue(r.changed)
        assertEquals("2028-08-12", r.state.careerDate)
        assertEquals(listOf("Sunderland", "Parma"), r.state.fixtures.map { it.opponent })
        assertEquals("15:00", r.state.fixtures.first().kickOff)
        val table = r.state.leagueTables.single()
        assertEquals(6, table.rows.size)
        assertTrue(table.rows.first { it.club == "Torquay AFC" }.isUser)
        val ig = r.state.players.first { it.id == "a" }
        assertEquals(9, ig.number); assertEquals("Crucial", ig.squadRole); assertEquals(15, ig.wageK); assertEquals(71, ig.overall)
        assertTrue(ig.isInjured); assertEquals(Source.GAME, ig.injurySource)
        assertEquals(2, r.state.players.first { it.id == "b" }.suspendedGames)
        assertEquals("Plainmoor", r.state.clubInfo.stadium); assertEquals("Sunderland", r.state.clubInfo.rival)
        assertEquals(4, r.state.leaders.single().goals.single().value)
        assertTrue(r.state.coefficients.last().isUser)
        assertEquals(listOf("Enrique Iglesias"), r.newInjuries)
        // the auto items added in script 2.10: attributes, form, the league statistics and the club's prestige
        assertEquals(83, ig.attributes["Sprint speed"]); assertEquals(76, ig.attributes["Finishing"])
        assertEquals(ig.attributes, ig.seasonStartAttributes)
        assertEquals("Good", ig.form)
        val stat = r.state.squadStats.single()
        assertEquals(461483, stat.gamePlayerId); assertEquals(5, stat.appearances); assertEquals(4, stat.goals); assertEquals(1, stat.yellowCards)
        assertEquals(4, r.state.clubInfo.prestigeHome); assertEquals(2, r.state.clubInfo.prestigeAbroad)
    }

    @Test fun trophiesAreAddedOnceWithTheirSeason() {
        val json = """{"careerDate":"2027-05-20","trophies":[{"competition":"FA Cup","season":"2026/27","startYear":2026,"league":false}]}"""
        val r = report(json)
        assertEquals(listOf("FA Cup 2026/27"), r.state.teamTrophies.map { it.name })
        assertEquals(listOf("FA Cup 2026/27"), r.newTrophies)
        val again = CareerReport.apply(r.state, JSONObject(json))
        assertEquals(1, again.state.teamTrophies.size)
        assertTrue(again.newTrophies.isEmpty())
        // the person typed it already, in their own words
        val typed = CareerReport.apply(base.copy(teamTrophies = listOf(TeamTrophy(name = "Emirates FA Cup winners (2026/27)"))), JSONObject(json))
        assertEquals(1, typed.state.teamTrophies.size)
        // the same cup another season is a new trophy
        val next = CareerReport.apply(r.state.copy(startYear = 2027), JSONObject("""{"trophies":[{"competition":"FA Cup","startYear":2027}]}"""))
        assertEquals(listOf("FA Cup 2026/27", "FA Cup 2027/28"), next.state.teamTrophies.map { it.name })
    }

    @Test fun aRemovedTrophyIsNotAddedAgain() {
        val json = """{"trophies":[{"competition":"FA Cup","startYear":2026}]}"""
        val r = report(json)
        val removed = com.fctracker.career.logic.Trophies.remove(r.state, "FA Cup 2026/27")
        assertTrue(removed.teamTrophies.isEmpty())
        val again = CareerReport.apply(removed, JSONObject(json))
        assertTrue(again.state.teamTrophies.isEmpty())
        assertFalse(again.changed)
        assertTrue(com.fctracker.career.logic.Trophies.inSeason("FA Cup 2026/27", 2026))
        assertFalse(com.fctracker.career.logic.Trophies.inSeason("FA Cup 2025/26", 2026))
        assertTrue(com.fctracker.career.logic.Trophies.inSeason("Fans' Cup", 2026))
    }

    @Test fun aTrophyForASeasonAlreadyEndedGoesToThatSeason() {
        val ended = CareerOps.endSeason(base.copy(careerDate = "2027-05-25"))
        assertEquals(2027, ended.startYear)
        val r = CareerReport.apply(ended, JSONObject("""{"careerDate":"2027-06-02","trophies":[{"competition":"EFL Championship","startYear":2026,"league":true}]}"""))
        assertEquals(listOf("EFL Championship 2026/27"), r.state.historicalSeasons.single { it.year == 2026 }.trophies)
        assertTrue(r.state.teamTrophies.none { it.name.contains("2026/27") })
        // a season the career never had is ignored
        val older = CareerReport.apply(ended, JSONObject("""{"trophies":[{"competition":"FA Cup","startYear":2019}]}"""))
        assertFalse(older.changed)
    }

    @Test fun lastSeasonsTablesDoNotReplaceTheNewSeason() {
        val ended = CareerOps.endSeason(base.copy(careerDate = "2027-05-25"))
        val json = """{"careerDate":"2027-06-03","tables":[{"league":"EFL Championship","rows":[{"club":"Your Club","p":46,"pts":90,"user":true},{"club":"Hull City","p":46,"pts":80}]}],
            "stats":[{"playerId":111,"name":"Marco Varela","apps":44,"goals":20}],
            "players":[{"playerId":111,"name":"Marco Varela","form":"Excellent"}]}"""
        val june = CareerReport.apply(ended, JSONObject(json))
        assertTrue(june.state.leagueTables.isEmpty() || june.state.leagueTables.none { t -> t.rows.any { it.played == 46 } })
        assertTrue(june.state.squadStats.isEmpty())
        assertEquals("Excellent", june.state.players.first { it.id == "p1" }.form)   // the squad's details still count
        // in July the game has started the new season: its tables count again
        val july = CareerReport.apply(ended, JSONObject(json.replace("2027-06-03", "2027-07-10")))
        assertTrue(july.state.leagueTables.isNotEmpty())
        assertEquals(2027, CareerReport.reportSeason("2027-07-01")); assertEquals(2026, CareerReport.reportSeason("2027-06-30"))
    }

    @Test fun theProcessorAnnouncesATrophy() {
        val payload = JSONObject().put("time", 1000L).put("title", "Career").put("message",
            """{"dataType":"career","careerDate":"2027-05-20","trophies":[{"competition":"FA Cup","startYear":2026}]}""")
        val r = TelemetryProcessor.apply(base, listOf(payload), notify = true, nowSeconds = 1000L, today = "2027-05-20")
        assertEquals(listOf("FA Cup 2026/27"), r.state.teamTrophies.map { it.name })
        assertTrue(r.state.liveEvents.any { it.title == "Trophy" })
        assertEquals("season_summary", r.notices.single().route)
    }

    @Test fun unknownInjuriesAndSuspensionsAreLeftAlone() {
        val r = report("""{"careerDate":"2026-10-12","players":[{"playerId":222,"name":"Jonah Pike","injury":{"name":"Knock","start":"2026-10-10","back":"2026-10-31"}},{"playerId":111,"name":"Marco Varela","suspension":{"games":1}}]}""")
        // The next report could not read either table: nothing is cleared.
        val later = CareerReport.apply(r.state, JSONObject("""{"careerDate":"2026-10-14","injuriesKnown":false,"suspensionsKnown":false,"players":[{"playerId":222,"name":"Jonah Pike"},{"playerId":111,"name":"Marco Varela"}]}"""))
        assertTrue(later.state.players.first { it.id == "p2" }.isInjured)
        assertEquals(1, later.state.players.first { it.id == "p1" }.suspendedGames)
        // When they are readable again, a missing entry means fit and available.
        val known = CareerReport.apply(later.state, JSONObject("""{"careerDate":"2026-10-15","injuriesKnown":true,"suspensionsKnown":true,"players":[{"playerId":222,"name":"Jonah Pike"},{"playerId":111,"name":"Marco Varela"}]}"""))
        assertFalse(known.state.players.first { it.id == "p2" }.isInjured)
        assertEquals(0, known.state.players.first { it.id == "p1" }.suspendedGames)
    }

    @Test fun promotionMovesTheCareerToTheNewLeague() {
        val r = report("""{"tables":[{"league":"Premier League","rows":[{"club":"Arsenal","p":0},{"club":"Your Club","p":0,"user":true}]},{"league":"EFL Championship","rows":[{"club":"Hull City","p":0}]}]}""")
        assertEquals("Premier League", r.state.leagueName)
        // A table without the club (another league) never moves it.
        val other = report("""{"tables":[{"league":"Serie A","rows":[{"club":"Inter","p":3}]}]}""")
        assertEquals("EFL Championship", other.state.leagueName)
        // A cup or European table with the club in it does not either.
        val cup = report("""{"tables":[{"league":"UEFA Champions League","rows":[{"club":"Your Club","p":3,"user":true}]}]}""")
        assertEquals("EFL Championship", cup.state.leagueName)
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
    @Test fun promotedOnConsoleTheCareerMovesLeague() {
        val s = AppState(clubName = "Your Club", leagueName = "EFL Championship", country = "England",
            leagueTables = listOf(LeagueTableData(league = "EFL Championship", rows = listOf(TableRow(club = "Your Club", isUser = true), TableRow(club = "Hull City")))))
        val moved = CareerOps.moveLeague(s, "Premier League", listOf("Burnley", "Your Club", ""), promoted = true)
        assertEquals("Premier League", moved.leagueName)
        val pl = moved.leagueTables.first { it.league == "Premier League" }
        assertEquals(listOf("Burnley", "Your Club"), pl.rows.map { it.club })
        assertTrue(pl.rows.all { it.movement == "promoted" })
        assertTrue(pl.rows.first { it.club == "Your Club" }.isUser)
        assertTrue(moved.leagueTables.first { it.league == "EFL Championship" }.rows.none { it.isUser })
        // Moving to the league it is already in changes nothing.
        assertEquals(s, CareerOps.moveLeague(s, "efl championship", emptyList(), promoted = true))
    }

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
