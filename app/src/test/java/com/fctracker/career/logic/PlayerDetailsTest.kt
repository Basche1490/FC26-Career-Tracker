package com.fctracker.career.logic

import com.fctracker.career.AppState
import com.fctracker.career.EafcPlayer
import com.fctracker.career.Player
import com.fctracker.career.TransferLog
import com.fctracker.career.telemetry.CareerReport
import com.fctracker.career.telemetry.TelemetryProcessor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Signings get the game's details, cards with guessed details are corrected, players are named the short way. */
class PlayerDetailsTest {

    private fun db(id: Int, name: String, common: String = "", pos: String = "CM", alt: String = "", born: String = "1/1/1995 12:00:00 AM", nation: String = "Spain") =
        EafcPlayer(id, name, common, pos, alt, 80, 0, "Club", 0.0, 0, born, 180, 75, "Right", 3, nation)

    private val index = PlayerIndex(listOf(
        db(189511, "Sergio Busquets Burgos", "Sergio Busquets", "CDM", "CM,CB", "7/16/1988 12:00:00 AM"),
        db(251854, "Pedro González López", "Pedri", "CM", "CDM,CAM", "11/25/2002 12:00:00 AM"),
        db(300001, "Leo Guerra", "", "ST", "LW", "3/3/2004 12:00:00 AM", "Argentina")
    ))

    private fun event(payload: String, time: Long = 1000): JSONObject =
        JSONObject().put("time", time).put("title", "Live Event").put("message", JSONObject(payload).toString())

    private fun run(state: AppState, vararg events: JSONObject) =
        TelemetryProcessor.apply(state, events.toList(), notify = false, nowSeconds = 5, today = "2028-08-10", lookup = index)

    // -- short names ---------------------------------------------------------------------------------------------------

    @Test fun shortNamesComeFromTheDatabase() {
        assertEquals("Sergio Busquets", index.shortName("Sergio Busquets Burgos"))
        assertEquals("Sergio Busquets", index.shortName("Sergio Busquets (Sergio Busquets Burgos)"))
        assertEquals("Pedri", index.shortName("Pedri (Pedro González López)"))
        assertEquals("Pedri", index.shortName("pedro gonzalez lopez"))
        assertEquals("Kenji Yamada", index.shortName("Kenji Yamada"))           // a youth player: nothing shorter
        // without the database, "Short (Full Name)" still loses the part in brackets when it spells out the same name
        assertEquals("Marco Varela", PlayerLookup.NONE.shortName("Marco Varela (Marco Antonio Varela)"))
        assertEquals("Jo (Captain)", PlayerLookup.NONE.shortName("Jo (Captain)"))
        assertEquals("Leo Guerra", PlayerNames.shown("Leo Guerra (Leonardo Guerra Diaz)"))
        assertEquals("Pedri", PlayerNames.shown("Pedri (Pedro González López)"))
    }

    @Test fun regensAndDealsSavedTheLongWayAreShortened() {
        val state = AppState(
            players = listOf(Player(id = "r", name = "Txus Mendia", isRegen = true, regenOriginalName = "Sergio Busquets (Sergio Busquets Burgos)")),
            transfers = listOf(TransferLog(playerName = "Pedri (Pedro González López)", type = "In (Permanent)"),
                TransferLog(playerName = "Txus Mendia", type = "In (Permanent)", isRegen = true, regenOriginalName = "Sergio Busquets Burgos"))
        )
        val s = RegenNames.shorten(state, index::commonNameOf)
        assertEquals("Sergio Busquets", s.players.single().regenOriginalName)
        assertEquals(listOf("Pedri", "Txus Mendia"), s.transfers.map { it.playerName })
        assertEquals("Sergio Busquets", s.transfers[1].regenOriginalName)
        assertTrue(RegenNames.shorten(s, index::commonNameOf) === s)      // nothing left to change
    }

    // -- a signing from the game ------------------------------------------------------------------------------------------

    @Test fun aSigningGetsTheGamesDetailsNotGuessedOnes() {
        val state = AppState(clubName = "Torquay AFC", startYear = 2028)
        val r = run(state, event("""{"dataType":"transfer","playerName":"Kai Moreno","type":"In (Permanent)","feeM":0,"fromToClub":"Leeds","date":"2028-08-01",
            "playerId":470001,"profile":{"positions":["LW","ST"],"birthDate":"2007-03-14","nation":14,"height":176,"weight":70,"foot":"Left","weakFoot":4,"overall":66,"potential":82}}"""))
        val p = r.state.players.single()
        assertEquals("LW", p.primaryPosition); assertEquals("ST", p.secondaryPosition)
        assertEquals("2007-03-14", p.birthDate); assertEquals(21, p.age)
        assertEquals("England", p.nationality); assertEquals(176, p.height); assertEquals("Left", p.preferredFoot); assertEquals(4, p.weakFoot)
        assertEquals(66, p.overall); assertEquals(82, p.potential); assertEquals(470001, p.gamePlayerId)
    }

    @Test fun aRealPlayerIsFoundByTheGamesIdAndNamedTheShortWay() {
        val state = AppState(clubName = "Torquay AFC", startYear = 2028)
        val r = run(state, event("""{"dataType":"transfer","playerName":"Pedro González López","type":"In (Loan)","feeM":0,"fromToClub":"FC Barcelona","date":"2028-08-02",
            "playerId":251854,"profile":{"positions":["CAM","CM"],"overall":88,"nation":45}}"""))
        val p = r.state.players.single()
        assertEquals("Pedri", p.displayName)
        assertEquals("2002-11-25", p.birthDate)                    // the database's birthday
        assertEquals("CAM", p.primaryPosition)                     // the game's position now
        assertEquals("Spain", p.nationality)
        assertTrue(p.isLoanedIn)
        assertEquals("Pedri", r.state.transfers.single().playerName)
        // the same deal again under the long name is not a second deal
        val again = run(r.state, event("""{"dataType":"transfer","playerName":"Pedro González López","type":"In (Loan)","feeM":0,"fromToClub":"FC Barcelona","date":"2028-08-02","playerId":251854}""", time = 2000))
        assertEquals(1, again.state.transfers.size)
    }

    @Test fun aDepartureIsMatchedByTheGamesIdWhateverTheName() {
        val state = AppState(clubName = "Torquay AFC", startYear = 2028, players = listOf(Player(id = "a", name = "K. Moreno", gamePlayerId = 470001)))
        val r = run(state, event("""{"dataType":"transfer","playerName":"Kai Moreno","type":"Out (Loan)","feeM":0,"fromToClub":"Exeter","date":"2028-08-20","playerId":470001}"""))
        val p = r.state.players.single()
        assertEquals("Loaned Out", p.status); assertTrue(p.isLoanedOut)
    }

    @Test fun boughtThenLoanedOutShowsBothDeals() {
        var state = AppState(clubName = "Torquay AFC", startYear = 2028)
        state = run(state, event("""{"dataType":"transfer","playerName":"Kai Moreno","type":"In (Permanent)","feeM":3.5,"fromToClub":"Leeds","date":"2028-08-01","playerId":470001}""")).state
        state = run(state, event("""{"dataType":"transfer","playerName":"Kai Moreno","type":"Out (Loan)","feeM":0,"fromToClub":"Exeter","date":"2028-08-08","playerId":470001}""", time = 2000)).state
        assertEquals(listOf("Out (Loan)", "In (Permanent)"), state.transfers.map { it.type })
        assertEquals("Loaned Out", state.players.single().status)
    }

    @Test fun aSigningTheGameListsAsALoanBecomesTheLoan() {
        val squad = listOf(Player(id = "a", name = "Kai Moreno", loanDirection = "in", loanClub = "Leeds"))
        val deals = listOf(TransferLog(playerName = "Kai Moreno", type = "In (Permanent)", fromToClub = "Unknown club", date = "2028-08-01", feeUnknown = true),
            TransferLog(playerName = "Old Deal", type = "In (Permanent)", date = "2027-08-01"))
        val fixed = TelemetryProcessor.loanCorrections(deals, squad, 2028)
        assertEquals("In (Loan)", fixed[0].type); assertEquals("Leeds", fixed[0].fromToClub)
        assertEquals(deals[1], fixed[1])
        // a paid signing is never turned into a loan
        val paid = listOf(deals[0].copy(feeM = 4.0))
        assertEquals(paid, TelemetryProcessor.loanCorrections(paid, squad, 2028))
    }

    // -- cards made with guessed details ----------------------------------------------------------------------------------

    @Test fun theCareerReportCorrectsAGuessedCardButNotARealOne() {
        val guessed = Player(id = "g", name = "Kai Moreno", overall = 72)                    // born 1 January 1999, CM, nation unknown
        val real = Player(id = "r", name = "Marco Varela", primaryPosition = "CB", birthDate = "2001-05-05", nationality = "Italy")
        val state = AppState(startYear = 2028, players = listOf(guessed, real))
        val r = CareerReport.apply(state, JSONObject("""{"players":[
            {"playerId":470001,"name":"Kai Moreno","profile":{"positions":["LW"],"birthDate":"2007-03-14","nation":14,"height":176}},
            {"playerId":470002,"name":"Marco Varela","profile":{"positions":["CDM"],"birthDate":"2001-05-05","nation":27}}]}"""), index)
        val g = r.state.players.first { it.id == "g" }
        assertEquals("LW", g.primaryPosition); assertEquals("2007-03-14", g.birthDate); assertEquals("England", g.nationality); assertEquals(470001, g.gamePlayerId)
        val k = r.state.players.first { it.id == "r" }
        assertEquals("CB", k.primaryPosition); assertEquals("Italy", k.nationality)   // what is already real stays
    }

    @Test fun startingUpRepairsGuessedCardsAndShortNames() {
        val state = AppState(startYear = 2028, players = listOf(
            Player(id = "1", name = "Leo Guerra"),                                          // guessed card of a real player
            Player(id = "2", name = "Sergio Busquets Burgos", primaryPosition = "CDM", birthDate = "1988-07-16", nationality = "Spain"),
            Player(id = "3", name = "Pedro González López", primaryPosition = "RW"),         // the person fixed his position
            Player(id = "4", name = "Kenji Yamada", nickname = "Kenji")
        ))
        val s = PlayerRepair.repair(state, index)
        val guerra = s.players[0]
        assertEquals("ST", guerra.primaryPosition); assertEquals("LW", guerra.secondaryPosition); assertEquals("2004-03-03", guerra.birthDate)
        assertEquals("Argentina", guerra.nationality); assertEquals(300001, guerra.gamePlayerId)
        assertEquals("Sergio Busquets", s.players[1].displayName)
        assertEquals("Pedri", s.players[2].displayName); assertEquals("RW", s.players[2].primaryPosition); assertEquals("2002-11-25", s.players[2].birthDate)
        assertEquals("Kenji", s.players[3].displayName)
        assertTrue(PlayerRepair.repair(s, index) === s)
    }

    @Test fun aLineupRowWithoutANameIsFoundByTheGamesId() {
        // the game left the regen's name out: only his id says who he is (the app showed "Unknown player")
        val state = AppState(clubName = "Torquay AFC", leagueName = "EFL Championship", startYear = 2028,
            players = listOf(Player(id = "a", name = "Txus Mendia", gamePlayerId = 460123, primaryPosition = "CDM"),
                Player(id = "b", name = "Kenji Yamada", gamePlayerId = 460040)))
        val r = run(state, event("""{"dataType":"match","detailSource":"atlas","opponentName":"Leeds","isHome":true,"goalsFor":1,"goalsAgainst":0,
            "date":"2028-08-12","competition":"EFL Championship","stage":"Matchday 2",
            "playerPerformances":[{"playerId":"460123","playerName":"Player","rating":7.1,"goals":1},{"playerId":"460040","playerName":"Kenji Yamada","rating":6.8}],
            "goalDetails":[{"playerName":"Player","playerId":"460123","minute":30,"isOpponent":false}],
            "substitutions":[{"minute":70,"playerOutName":"","playerInName":"","playerOutId":"460123","playerInId":"460040"}]}"""))
        val m = r.state.recentMatches.single()
        assertEquals(listOf("a", "b"), m.playerPerformances.map { it.playerId })
        assertEquals("a", m.goalDetails.single().scorerId)
        assertEquals("a", m.substitutions.single().playerOutId)
    }

    @Test fun matchesStoredUnderTheGamesIdAreLinkedOnceTheCardKnowsIt() {
        val match = com.fctracker.career.MatchResult(opponentName = "Leeds", isHome = true, goalsFor = 1, goalsAgainst = 0, matchDate = "2028-08-12",
            competition = "EFL Championship", playerPerformances = listOf(com.fctracker.career.PlayerPerformance(playerId = "460123", goals = 1, goalAssisters = listOf("460040"))),
            goalDetails = listOf(com.fctracker.career.GoalDetail(scorerId = "460123", assisterId = "460040")))
        val players = listOf(Player(id = "a", name = "Txus Mendia", gamePlayerId = 460123), Player(id = "b", name = "Kenji Yamada", gamePlayerId = 460040))
        val linked = PlayerRepair.linkMatches(listOf(match), players).single()
        assertEquals("a", linked.playerPerformances.single().playerId)
        assertEquals(listOf<String?>("b"), linked.playerPerformances.single().goalAssisters)
        assertEquals("a", linked.goalDetails.single().scorerId); assertEquals("b", linked.goalDetails.single().assisterId)
        val again = listOf(linked)
        assertTrue(PlayerRepair.linkMatches(again, players) === again)
    }

    @Test fun seasonTotalsNeverBelieveACardBeyondWhatTheClubScored() {
        // a career imported with career totals on the cards: 150 goals in a season the club scored 40
        val matches = (1..10).map { i -> com.fctracker.career.MatchResult(opponentName = "Club $i", isHome = true, goalsFor = 4, goalsAgainst = 1,
            matchDate = "2027-09-%02d".format(i), competition = "EFL Championship",
            playerPerformances = listOf(com.fctracker.career.PlayerPerformance(playerId = "460123", goals = 1))) }
        val veteran = Player(id = "v", name = "Old Striker", gamePlayerId = 460123, goals = 150, matchesPlayed = 400)
        val t = SeasonFacts.totals(veteran, matches)
        assertEquals(10, t.appearances)            // found by the game's id in the line-ups; the card's 400 is not believed
        assertEquals(10, t.goals)                  // 150 in a season the club scored 40 is not believed either
        // a player listed in the squad and the academy is counted once
        val twice = SeasonFacts.allTotals(listOf(veteran, veteran.copy(isAcademy = true)), matches)
        assertEquals(1, twice.size)
        // with no matches logged for the season, the card is all there is, as long as one season could hold it
        assertEquals(25, SeasonFacts.totals(veteran.copy(goals = 25), emptyList()).goals)
        assertEquals(0, SeasonFacts.totals(veteran, emptyList()).goals)
        // a few more than the logged matches show is believed (a match that was not logged)
        assertEquals(45, SeasonFacts.totals(veteran.copy(goals = 45), matches).goals)
    }

    @Test fun profilesLeaveOutWhatIsImpossible() {
        val g = GameProfile.parse(JSONObject("""{"positions":["st","XX","ST"],"birthDate":"soon","height":300,"foot":"Both","weakFoot":4}"""))!!
        assertEquals(listOf("ST"), g.positions); assertNull(g.birthDate); assertNull(g.height); assertNull(g.foot); assertEquals(4, g.weakFoot)
        assertNull(GameProfile.parse(JSONObject("""{"height":0}""")))
    }
}
