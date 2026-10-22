package com.fctracker.career.logic

import com.fctracker.career.AppState
import com.fctracker.career.Fixture
import com.fctracker.career.GoalDetail
import com.fctracker.career.HistoricalSeason
import com.fctracker.career.LeagueTableData
import com.fctracker.career.MatchResult
import com.fctracker.career.Player
import com.fctracker.career.PlayerPerformance
import com.fctracker.career.TableRow
import com.fctracker.career.TransferLog
import com.fctracker.career.WorldWinner
import com.fctracker.career.ai.AiContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatesTest {
    @Test fun roundTrip() {
        for (iso in listOf("1970-01-01", "1995-12-22", "2000-02-29", "2026-10-20", "2027-06-30")) {
            assertEquals(iso, Dates.fromEpochDay(Dates.toEpochDay(iso)!!))
        }
        assertEquals("2026-11-07", Dates.plusDays("2026-10-31", 7))
        assertEquals(7L, Dates.daysBetween("2026-10-31", "2026-11-07"))
    }

    @Test fun weekdaysAndLabels() {
        assertEquals(1, Dates.weekday("2026-10-20"))           // a Tuesday
        assertEquals("Tuesday 20 October", Dates.longWeekday("2026-10-20"))
        assertEquals("Sat 24 Oct", Dates.weekdayDayMonth("2026-10-24"))
        assertEquals(2026, Dates.seasonOf("2026-10-20"))
        assertEquals(2025, Dates.seasonOf("2026-05-20"))
    }

    @Test fun injuryDaysCountFrom22December1995() {
        // Gonzaga: the 1 January 1996 start said 19 September, the game said about 9 September.
        val day = (Dates.toEpochDay("2025-09-09")!! - Dates.toEpochDay("1995-12-22")!!).toInt()
        assertEquals("2025-09-09", Dates.injuryDayToIso(day))
        assertEquals("2025-09-19", Dates.plusDays(Dates.injuryDayToIso(day), 10))
        assertNull(Dates.injuryDayToIso(0))
    }
}

class LeagueTablesTest {
    private fun row(c: String, w: Int, d: Int, l: Int, gf: Int, ga: Int, me: Boolean = false) =
        LeagueTables.recalculated(TableRow(club = c, won = w, drawn = d, lost = l, goalsFor = gf, goalsAgainst = ga, isUser = me))

    @Test fun clubsComingDownFromTheLeagueAbove() {
        fun table(league: String, n: Int, prefix: String) = LeagueTableData(league = league, rows = (1..n).map { TableRow(club = "$prefix$it", points = 100 - it) })
        val own = table("EFL Championship", 24, "C")
        val m = LeagueTables.proposeMovements(own, table("EFL League One", 24, "L"), table("Premier League", 20, "P"))
        assertEquals(listOf("C22", "C23", "C24"), m.down)
        assertEquals(listOf("C1", "C2", "C3"), m.upFrom)
        assertEquals(listOf("L1", "L2", "L3"), m.upInto)
        assertEquals(listOf("P18", "P19", "P20"), m.downInto)
        assertEquals("Premier League", m.leagueAbove)
    }

    @Test fun domesticLeagues() {
        assertTrue(LeagueTables.isDomesticLeague("EFL Championship"))
        assertTrue(LeagueTables.isDomesticLeague("Süper Lig"))
        assertTrue(LeagueTables.isDomesticLeague("Scottish Premiership"))
        assertFalse(LeagueTables.isDomesticLeague("UEFA Champions League"))
        assertFalse(LeagueTables.isDomesticLeague("FA Cup"))
        assertFalse(LeagueTables.isDomesticLeague("DFL-Supercup"))
        assertFalse(LeagueTables.isDomesticLeague("Premier League U21"))
        assertFalse(LeagueTables.isDomesticLeague(""))
    }

    @Test fun orderAndRecalculation() {
        val rows = listOf(row("B", 6, 3, 5, 22, 14, true), row("A", 10, 2, 2, 30, 13), row("C", 6, 3, 5, 20, 14))
        val s = LeagueTables.sorted(rows)
        assertEquals(listOf("A", "B", "C"), s.map { it.club })
        assertEquals(21, s[1].points)
        assertEquals(14, s[1].played)
        assertEquals(2, LeagueTables.position(rows, "b"))
    }

    @Test fun zones() {
        val ch = LeagueTables.zonesFor("EFL Championship", 24)
        assertEquals(ZoneKind.PROMOTION, LeagueTables.zoneOf("EFL Championship", 24, 2))
        assertEquals(ZoneKind.PLAYOFF, LeagueTables.zoneOf("EFL Championship", 24, 6))
        assertEquals(ZoneKind.RELEGATION, LeagueTables.zoneOf("EFL Championship", 24, 22))
        assertNull(LeagueTables.zoneOf("EFL Championship", 24, 10))
        assertEquals(3, ch.size)
        assertEquals(ZoneKind.CHAMPIONS_LEAGUE, LeagueTables.zoneOf("Premier League", 20, 4))
        assertEquals(ZoneKind.RELEGATION, LeagueTables.zoneOf("Premier League", 20, 18))
        assertEquals(ZoneKind.RELEGATION_PLAYOFF, LeagueTables.zoneOf("Bundesliga", 18, 16))
        assertEquals(ZoneKind.CHAMPION, LeagueTables.zoneOf("MLS", 29, 1))
    }

    @Test fun promotionAndRelegationForANewSeason() {
        val clubs = (1..24).map { row("Club $it", 24 - it, 0, it, 30, 20, it == 10) }
        val own = LeagueTableData(league = "EFL Championship", rows = clubs)
        val below = LeagueTableData(league = "EFL League One", rows = (1..24).map { row("L1 $it", 24 - it, 0, it, 30, 20) })
        val m = LeagueTables.proposeMovements(own, below)
        assertEquals(listOf("Club 22", "Club 23", "Club 24"), m.down)
        assertEquals(listOf("Club 1", "Club 2", "Club 3"), m.upFrom)
        assertEquals(listOf("L1 1", "L1 2", "L1 3"), m.upInto)
        val next = LeagueTables.newSeasonTable(own, m.down + m.upFrom, m.upInto, listOf("PL 18", "PL 19", "PL 20"), "Club 10")
        assertEquals(24, next.rows.size)
        assertTrue(next.rows.all { it.played == 0 && it.points == 0 })
        assertEquals("promoted", next.rows.first { it.club == "L1 1" }.movement)
        assertEquals("relegated", next.rows.first { it.club == "PL 20" }.movement)
        assertTrue(next.rows.first { it.club == "Club 10" }.isUser)
        assertFalse(next.rows.any { it.club == "Club 24" })
    }

    @Test fun consoleEditsKeepNamesUnique() {
        val t = LeagueTableData(league = "X", rows = listOf(row("A", 1, 0, 0, 1, 0), row("B", 0, 0, 1, 0, 1)))
        assertEquals(t.rows.size, LeagueTables.addClub(t, "a").rows.size)
        assertEquals(3, LeagueTables.addClub(t, "C").rows.size)
        assertEquals("B", LeagueTables.renameClub(t, "A", "B").rows.first().club.let { if (it == "A") "B" else it })
        assertEquals(listOf("Z", "B"), LeagueTables.renameClub(t, "A", "Z").rows.map { it.club })
        assertEquals(listOf("B"), LeagueTables.removeClub(t, "a").rows.map { it.club })
    }
}

class CupRunsTest {
    private fun m(opp: String, gf: Int, ga: Int, comp: String, stage: String, date: String, home: Boolean = true, hp: Int = 0, ap: Int = 0) =
        MatchResult(opponentName = opp, isHome = home, goalsFor = gf, goalsAgainst = ga, matchDate = date, competition = comp, stage = stage, homePenalties = hp, awayPenalties = ap)

    @Test fun roundCodes() {
        assertEquals("R3", CupRuns.roundCode("Round 3"))
        assertEquals("R3", CupRuns.roundCode("Third Round"))
        assertEquals("QF", CupRuns.roundCode("Quarter-final"))
        assertEquals("SF", CupRuns.roundCode("Semi-Final 2nd Leg"))
        assertEquals("F", CupRuns.roundCode("Final"))
        assertEquals("R16", CupRuns.roundCode("Round of 16"))
        assertEquals("LP", CupRuns.roundCode("League Phase Matchday 3"))
    }

    @Test fun outInRoundThree() {
        val ms = listOf(m("Barnsley", 2, 0, "Carabao Cup", "Round 1", "2026-08-12"), m("Wigan", 1, 0, "Carabao Cup", "Round 2", "2026-08-26", home = false),
            m("Brighton", 1, 2, "Carabao Cup", "Round 3", "2026-10-05", home = false))
        val run = CupRuns.run("Carabao Cup", ms, emptyList())!!
        assertEquals(listOf(RoundState.WON, RoundState.WON, RoundState.LOST, RoundState.TODO), run.rounds.take(4).map { it.state })
        assertEquals("Out in Round 3: lost 1–2 at Brighton", run.status)
        assertTrue(run.out)
    }

    @Test fun nextRoundFromTheFixtureList() {
        val fx = listOf(Fixture(date = "2027-01-09", competition = "FA Cup", round = "Round 3", opponent = "Stoke City", isHome = true))
        val run = CupRuns.run("FA Cup", emptyList(), fx)!!
        assertEquals("R3", run.rounds.first().code)
        assertEquals(RoundState.NEXT, run.rounds.first().state)
        assertEquals("Round 3 next: Stoke City (H), Sat 9 Jan", run.status)
    }

    @Test fun penaltiesDecideACupTie() {
        val win = m("Leeds", 1, 1, "FA Cup", "Round 4", "2027-01-30", home = false, hp = 2, ap = 4)
        assertEquals("W", CupRuns.outcome(win))
        val final = m("Chelsea", 2, 1, "FA Cup", "Final", "2027-05-22")
        val run = CupRuns.run("FA Cup", listOf(win, final), emptyList())!!
        assertTrue(run.won)
        assertEquals("Winners: beat Chelsea 2–1 in the final", run.status)
    }
}

class AccentTest {
    @Test fun clubColoursBecomeAReadableAccent() {
        val blue = Accent.choose("#0b57d0", "#0e1a2b")
        assertEquals("#0B57D0", blue.fromHex)
        assertTrue(Accent.contrast(blue.hex, Accent.BACKGROUND) >= 4.5)
        assertEquals("lightened to stay readable", blue.why)
        val purple = Accent.choose("#d4a5ff", "#111111")
        assertEquals("kept as it is", purple.why)
        val mono = Accent.choose("#111111", "#ffffff")
        assertEquals(Accent.DEFAULT, mono.hex)
        assertNull(mono.fromHex)
        assertEquals("#05140D", Accent.onColour("#62E0B0"))
        assertEquals("#FFFFFF", Accent.onColour("#0B57D0"))
    }

    @Test fun theAccentNeverLooksLikeAResultColour() {
        // a red club whose other colour is white: red would read as a loss, so it is turned away from it
        val red = Accent.choose("#c8102e", "#ffffff")
        assertNull(Accent.clashesWith(red.hex))
        assertTrue(red.why.contains("loss"))
        assertTrue(Accent.contrast(red.hex, Accent.BACKGROUND) >= 4.5)
        // a yellow and green club: both near a result colour, the hue is turned just enough
        val canaries = Accent.choose("#fff200", "#00a650")
        assertNull(Accent.clashesWith(canaries.hex))
        // a green primary with a blue secondary: the blue is used instead
        val greenBlue = Accent.choose("#18a33a", "#1e4bd2")
        assertEquals("#1E4BD2", greenBlue.fromHex)
        assertNull(Accent.clashesWith(greenBlue.hex))
        // the second accent is the other club colour, also clear of the result colours, and different from the first
        val second = Accent.chooseSecondary("#0b57d0", "#f2b705", Accent.choose("#0b57d0", "#f2b705").hex)
        assertNull(Accent.clashesWith(second))
        assertTrue(Accent.contrast(second, Accent.BACKGROUND) >= 4.5)
        val tint = Accent.chooseSecondary("#0b57d0", "#ffffff", Accent.choose("#0b57d0", "#ffffff").hex)
        assertTrue(Accent.parse(tint) != null)
    }

    @Test fun hlsRoundTrip() {
        val (h, l, s) = Accent.rgbToHls(0.2, 0.4, 0.8)
        val (r, g, b) = Accent.hlsToRgb(h, l, s)
        assertEquals(0.2, r, 1e-9); assertEquals(0.4, g, 1e-9); assertEquals(0.8, b, 1e-9)
    }
}

class WorldWinnersTest {
    @Test fun internationalAndWomensCompetitionsAreLeftOut() {
        assertTrue(WorldWinners.isShown("UEFA Champions League"))
        assertTrue(WorldWinners.isShown("Coupe de France"))
        assertFalse(WorldWinners.isShown("FIFA World Cup"))
        assertFalse(WorldWinners.isShown("UEFA Nations League"))
        assertFalse(WorldWinners.isShown("European International Cup"))
        assertFalse(WorldWinners.isShown("Barclays Women's Super League"))
        assertFalse(WorldWinners.isShown("UEFA Euro 2028"))
    }

    @Test fun updatesOnlyInJune() {
        assertEquals(2026, WorldWinners.seasonForUpdate("2027-06-10"))
        assertEquals(2026, WorldWinners.seasonForUpdate("2027-07-01"))
        assertNull(WorldWinners.seasonForUpdate("2027-05-30"))
        assertNull(WorldWinners.seasonForUpdate("2026-10-20"))
    }

    @Test fun mergeKeepsWhatThePersonTypedUnlessTheGameKnowsIt() {
        val typed = listOf(WorldWinner(season = 2026, region = "Europe", competition = "UEFA Champions League", winner = "Typed", source = "manual"),
            WorldWinner(season = 2026, region = "England", competition = "FA Cup", winner = "Typed FA", source = "manual"))
        val game = listOf(WorldWinner(region = "Europe", competition = "UEFA Champions League", winner = "Real Madrid", score = "2–1", runnerUp = "Arsenal"),
            WorldWinner(region = "World", competition = "FIFA World Cup", winner = "Spain"))
        val merged = WorldWinners.merge(typed, game, 2026)
        assertEquals("Real Madrid", merged.first { it.competition == "UEFA Champions League" }.winner)
        assertEquals("Typed FA", merged.first { it.competition == "FA Cup" }.winner)
        assertFalse(merged.any { it.competition.contains("World Cup") })
        assertEquals("2–1 v Arsenal", WorldWinners.detail(merged.first { it.competition == "UEFA Champions League" }))
        val regions = WorldWinners.byRegion(merged, 2026).map { it.first }
        assertEquals(listOf("Europe", "England"), regions)
    }
}

class StoryAndRecapTest {
    private val varela = Player(id = "p1", name = "Marco Varela", primaryPosition = "ST", overall = 78, startingOverall = 75, potential = 80, age = 27, headshotUrl = "")
    private val pike = Player(id = "p2", name = "Jonah Pike", primaryPosition = "CM", overall = 76, startingOverall = 76, potential = 79, age = 19)

    private fun m(opp: String, gf: Int, ga: Int, date: String, pos: Int, scorer: Boolean, comp: String = "EFL Championship") = MatchResult(
        opponentName = opp, isHome = true, goalsFor = gf, goalsAgainst = ga, matchDate = date, competition = comp, leaguePosition = pos,
        playerPerformances = listOf(PlayerPerformance(playerId = "p1", rating = 7.5, goals = if (scorer) 1 else 0), PlayerPerformance(playerId = "p2", rating = 7.0, assists = 1)),
        goalDetails = if (scorer) listOf(GoalDetail(scorerId = "p1")) else emptyList()
    )

    private val matches = listOf(
        m("Leicester City", 3, 1, "2026-10-12", 10, true), m("Leeds United", 1, 1, "2026-10-01", 11, true),
        m("Norwich City", 2, 0, "2026-09-27", 12, true), m("Sunderland", 2, 1, "2026-09-21", 12, true), m("Middlesbrough", 0, 2, "2026-09-14", 13, false)
    )

    @Test fun theHomeStory() {
        val s = HomeStory.story("Manager name", matches, listOf(varela, pike), Competitions.isLeagueFn("EFL Championship"))
        assertEquals("A strong week, Manager name.", s.headline)
        assertEquals("Two wins in three, up to 10th, and Marco Varela has scored in four straight.", s.summary)
        assertEquals("Welcome, boss.", HomeStory.story("", emptyList(), emptyList()) { true }.headline)
    }

    @Test fun recordsAndTotals() {
        assertEquals("Leicester City", SeasonFacts.bestWin(matches)?.opponentName)
        assertEquals(4, SeasonFacts.longestUnbeaten(matches)?.length)
        assertEquals(listOf("L", "W", "W", "D", "W"), SeasonFacts.form(matches, 5))
        val t = SeasonFacts.totals(varela, matches)
        assertEquals(5, t.appearances); assertEquals(4, t.goals); assertEquals(7.5, t.averageRating, 1e-9)
        val p2 = SeasonFacts.totals(pike.copy(goals = 9), matches)
        assertEquals("the player card's own count wins when it is higher", 9, p2.goals)
    }

    @Test fun theCareerRecapCoversEverySeason() {
        val past = HistoricalSeason(year = 2025, leagueName = "League One", finalPosition = 1, points = 92, matches = matches.map { it.copy(id = it.id + "-old", matchDate = it.matchDate.replace("2026", "2025")) },
            players = listOf(varela, pike), trophies = listOf("League One 2025/26"))
        val state = AppState(clubName = "Your Club", managerName = "Manager name", leagueName = "EFL Championship", startYear = 2026, players = listOf(varela, pike), recentMatches = matches,
            historicalSeasons = listOf(past), teamTrophies = listOf(com.fctracker.career.TeamTrophy(name = "FA Cup 2026/27")),
            transfers = listOf(TransferLog(playerName = "Dario Kane", type = "In (Permanent)", feeM = 2.4, fromToClub = "Sheffield United", date = "2026-07-14")))
        val pages = CareerRecap.build(state)
        assertEquals(listOf("Cover", "Season by season", "Trophies", "Records", "Hall of fame", "Best players", "Transfers"), pages.map { it.name })
        val cover = pages.first()
        assertEquals("2", (cover.blocks.first() as RecapBlock.Big).value)          // two trophies
        val played = (cover.blocks.filterIsInstance<RecapBlock.Figures>().first()).items.first { it.first == "Played" }.second
        assertEquals("10", played)
        val seasons = pages[1].blocks.filterIsInstance<RecapBlock.Line>()
        assertEquals("1st · 92 pts", seasons.first().value)
        val scorer = pages[4].blocks.filterIsInstance<RecapBlock.Person>().first()
        assertEquals("Marco Varela", scorer.name); assertEquals("8", scorer.value)   // four goals in each season
    }

    @Test fun recapPagesAndAutomaticLeaveOuts() {
        val table = LeagueTableData(league = "EFL Championship", rows = listOf(TableRow("Coventry City", 14, 10, 2, 2, 30, 13, 32), TableRow("Your Club", 14, 6, 3, 5, 22, 14, 21, isUser = true)))
        val state = AppState(clubName = "Your Club", leagueName = "EFL Championship", startYear = 2026, players = listOf(varela, pike), recentMatches = matches,
            leagueTables = listOf(table), transfers = listOf(TransferLog(playerName = "Dario Kane", type = "In (Permanent)", feeM = 2.4, fromToClub = "Sheffield United", date = "2026-07-14")))
        val pages = Recap.build(state, SeasonView.of(state))
        val names = pages.map { it.name }
        assertTrue(names.containsAll(listOf("Cover", "The league", "Results", "Goals", "Player of the season", "Transfers", "Growth")))
        assertFalse("no cup matches and no winners yet", names.contains("Around the World"))
        val cover = pages.first()
        assertEquals("2nd", (cover.blocks.first() as RecapBlock.Big).value)
    }

    @Test fun archivedSeasonViewIsReadOnlyAndShowsThatSeason() {
        val past = HistoricalSeason(year = 2025, leagueName = "League One", finalPosition = 1, matches = matches.take(2), players = listOf(varela))
        val state = AppState(startYear = 2026, historicalSeasons = listOf(past), viewingSeasonYear = 2025, recentMatches = matches)
        val v = SeasonView.of(state)
        assertTrue(v.isArchived)
        assertEquals("2025/26", v.label)
        assertEquals(2, v.matches.size)
        assertEquals("League One", v.leagueName)
        assertFalse(SeasonView.of(state.copy(viewingSeasonYear = null)).isArchived)
    }

    @Test fun aiContextHoldsEveryPage() {
        val state = AppState(clubName = "Your Club", managerName = "Manager name", leagueName = "EFL Championship", startYear = 2026, players = listOf(varela, pike), recentMatches = matches,
            fixtures = listOf(Fixture(date = "2026-10-24", kickOff = "15:00", competition = "EFL Championship", round = "Matchday 15", opponent = "Hull City")),
            leagueTables = listOf(LeagueTableData(league = "EFL Championship", rows = listOf(TableRow("Hull City", 14, 5, 3, 6, 18, 20, 18, form = "WLDWL"), TableRow("Your Club", 14, 6, 3, 5, 22, 14, 21, isUser = true)))),
            careerDate = "2026-10-20")
        val text = AiContext.build(state)
        listOf("== CAREER ==", "== SQUAD", "Marco Varela", "== UPCOMING FIXTURES ==", "Hull City", "== MATCHES THIS SEASON", "== LEAGUE TABLE").forEach { assertTrue(it, text.contains(it)) }
        assertTrue(AiContext.systemPrompt(state, "chat").contains("OFF_TOPIC"))
        assertTrue(AiContext.names(state).contains("Hull City"))

        // Every other page too: tactics and coaches, scout reports, the game's team sheet, other leagues, past seasons and their matches.
        val past = HistoricalSeason(year = 2025, leagueName = "League One", finalPosition = 1, matches = matches.take(2), players = listOf(varela),
            objectives = listOf(com.fctracker.career.Objective(category = "League", priority = "High", description = "Win promotion", status = "Completed")))
        val full = state.copy(tacticalVision = "Gegenpressing", coaches = mapOf("Attack" to "Expert (5 stars)"),
            scoutReports = listOf(com.fctracker.career.ScoutReport(playerName = "Tom Scout", position = "ST", age = "19", ratingRange = "65-70", potentialRange = "80-85", value = "€2M", scoutName = "Ana", recommendation = "Sign him")),
            gameTeamSheet = com.fctracker.career.TeamSheet(formation = "4-3-3", starting = listOf(111), positions = mapOf("111" to "ST")),
            leagueTables = state.leagueTables + LeagueTableData(league = "EFL League One", rows = listOf(TableRow("Wigan Athletic", 14, 9, 3, 2, 25, 10, 30))),
            historicalSeasons = listOf(past),
            players = listOf(varela.copy(form = "Excellent", attributes = mapOf("Finishing" to 84, "Composure" to 80), seasonStartAttributes = mapOf("Finishing" to 81, "Composure" to 80)), pike),
            teamTrophies = listOf(com.fctracker.career.TeamTrophy(name = "FA Cup 2026/27")))
        val all = AiContext.build(full)
        listOf("form: Excellent", "best attributes: Finishing 84, Composure 80", "changed this season: Finishing +3", "FA Cup 2026/27").forEach { assertTrue(it, all.contains(it)) }
        listOf("Tactical vision: Gegenpressing", "Attack Expert", "== SCOUT REPORTS", "Tom Scout", "Team sheet picked in the game (4-3-3)", "Other league EFL League One: 1. Wigan Athletic",
            "== PAST SEASONS ==", "Win promotion (Completed)", "== MATCHES 2025/26 (League One) ==").forEach { assertTrue(it, all.contains(it)) }
    }

    @Test fun milestonesAreAnnouncedOnce() {
        val many = (1..50).map { i -> m("Opp $i", 1, 0, "2026-%02d-%02d".format(8 + i / 28, 1 + i % 28), 5, true) }
        val state = AppState(clubName = "Your Club", startYear = 2026, players = listOf(varela), recentMatches = many)
        val found = Milestones.detect(state, "2026-12-01")
        assertNotNull(found.firstOrNull { it.key == "apps:50:p1" })
        assertNotNull(found.firstOrNull { it.key == "goals:50:p1" })
        val again = Milestones.detect(state.copy(milestones = found), "2026-12-02")
        assertTrue(again.isEmpty())
    }
}


class SquadRulesTest {
    @Test fun aPromotedProspectLeavesTheAcademy() {
        val prospect = Player(id = "y1", name = "Kit Navarro", isAcademy = true, status = "Academy", potential = 84, headshotUrl = "http://x/kit.png")
        val firstTeam = Player(id = "n1", name = "Kit Navarro", primaryPosition = "CDM", headshotUrl = "")
        val state = AppState(startYear = 2026, players = listOf(firstTeam), academyPlayers = listOf(prospect, Player(id = "y2", name = "Other Kid", isAcademy = true)))
        val fixed = Academy.firstTeamOnly(state)
        assertEquals(listOf("Other Kid"), fixed.academyPlayers.map { it.name })
        val p = fixed.players.single()
        assertTrue(p.isPromoted); assertEquals(2026, p.promotedSeasonYear); assertEquals("http://x/kit.png", p.headshotUrl); assertEquals(84, p.potential)
        // the game's "Promoted In" moves the academy card up instead of adding a second card
        val moved = Academy.promote(emptyList(), listOf(prospect), "Kit Navarro", 2026)!!
        assertEquals(1, moved.first.size); assertTrue(moved.first.single().isPromoted); assertTrue(moved.second.isEmpty())
        assertNull(Academy.promote(listOf(firstTeam), emptyList(), "Kit Navarro", 2026))
    }

    @Test fun regensAreNamedByTheShortName() {
        val lookup: (String) -> String? = { if (it == "Sergio Busquets Burgos") "Sergio Busquets" else null }
        val state = AppState(players = listOf(Player(name = "Lucas Mora", isRegen = true, regenOriginalName = "Sergio Busquets Burgos")),
            transfers = listOf(TransferLog(playerName = "Lucas Mora", isRegen = true, regenOriginalName = "Sergio Busquets Burgos")))
        val s = RegenNames.shorten(state, lookup)
        assertEquals("Sergio Busquets", s.players.single().regenOriginalName)
        assertEquals("Sergio Busquets", s.transfers.single().regenOriginalName)
        assertEquals("Kevin De Bruyne", RegenNames.short("Kevin De Bruyne", lookup))
        assertTrue(RegenNames.shorten(s, lookup) === s)
    }

    @Test fun pointsOnlyCountInLeagueMatches() {
        fun m(comp: String, gf: Int, ga: Int) = MatchResult(opponentName = "X", isHome = true, goalsFor = gf, goalsAgainst = ga, matchDate = "2026-10-01", competition = comp)
        val list = listOf(m("EFL Championship", 2, 0), m("EFL Championship", 1, 1), m("Carabao Cup", 3, 0), m("FA Cup", 0, 1))
        assertEquals(4, com.fctracker.career.insights.MatchInsights.leaguePoints(list, Competitions.isLeagueFn("EFL Championship")))
        assertEquals(7, com.fctracker.career.insights.MatchInsights.record(list).points)
    }
}
