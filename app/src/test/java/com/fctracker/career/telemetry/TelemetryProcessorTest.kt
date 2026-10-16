package com.fctracker.career.telemetry

import com.fctracker.career.AppState
import com.fctracker.career.LiveEvent
import com.fctracker.career.MatchResult
import com.fctracker.career.Player
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryProcessorTest {

    private fun event(payload: String, time: Long = 1000, title: String = "Live Event"): JSONObject =
        JSONObject().put("time", time).put("title", title).put("message", JSONObject(payload).toString())

    private fun plainEvent(message: String, time: Long = 1000, title: String = "Note"): JSONObject =
        JSONObject().put("time", time).put("title", title).put("message", message)

    private fun squad(n: Int): List<Player> = (1..n).map {
        Player(
            id = "p$it",
            name = "Player $it",
            primaryPosition = if (it == 1) "GK" else if (it <= 5) "CB" else "CM",
            overall = 90 - it
        )
    }

    private fun run(
        state: AppState,
        vararg events: JSONObject,
        notify: Boolean = true,
        signed: (String) -> Player? = { null }
    ) = TelemetryProcessor.apply(
        state, events.toList(), notify, nowSeconds = 5, today = "2026-01-10", signedPlayer = signed
    )

    private val matchBase = """
        {"dataType":"match","opponentName":"Arsenal","isHome":true,"goalsFor":2,"goalsAgainst":0,
         "date":"2026-01-10","competition":"Premier League","stage":"Matchday 5"}
    """

    // ---- matches -----------------------------------------------------------------------------------

    @Test
    fun matchWithoutDetailIsStoredToppedUpAndFlaggedEstimated() {
        val state = AppState(clubName = "Test FC", leagueName = "Premier League", players = squad(14))
        val result = run(state, event(matchBase))

        val match = result.state.recentMatches.single()
        assertEquals("Matchday 5", match.stage)
        assertEquals(11, match.playerPerformances.size) // line-up topped up from the squad
        assertTrue(match.statsEstimated)
        assertEquals(1, result.notices.size)
        assertEquals("matches", result.notices[0].route)
        assertEquals("Full Time: Test FC vs 2 - 0 Arsenal (Premier League - Matchday 5)", result.notices[0].message)
    }

    @Test
    fun atlasMatchIsNeverPaddedOrFlagged() {
        val payload = """
            {"dataType":"match","detailSource":"atlas","opponentName":"Chelsea","isHome":false,"goalsFor":1,"goalsAgainst":1,
             "date":"2026-02-01","competition":"Premier League","stage":"Matchday 6",
             "shotsFor":9,"shotsAgainst":11,"expectedGoals":1.2,"expectedGoalsAgainst":1.4,"possession":47,"passingAccuracy":81,
             "motmName":"Player 6",
             "playerPerformances":[
               {"playerName":"Player 6","goals":1,"assists":0,"rating":8.1,"passes":40,"passAccuracy":88,"shots":3,"shotAccuracy":66,
                "tackles":2,"tacklePercentage":50,"interceptions":1},
               {"playerName":"Player 7","goals":0,"assists":1,"rating":7.0,"passes":30,"passAccuracy":80,"shots":1,"shotAccuracy":0,
                "tackles":0,"tacklePercentage":0,"interceptions":0}]}
        """
        val state = AppState(leagueName = "Premier League", players = squad(14))
        val match = run(state, event(payload)).state.recentMatches.single()

        assertEquals(2, match.playerPerformances.size)
        assertFalse(match.statsEstimated)
        assertEquals(47, match.possession)
        assertEquals("Player 6", match.motmName)
        assertTrue(match.playerPerformances.first { it.playerId == "p6" }.isMotm)
    }

    @Test
    fun fullPayloadFromAnotherSourceIsNotFlagged() {
        val payload = """
            {"dataType":"match","opponentName":"Everton","isHome":true,"goalsFor":3,"goalsAgainst":1,"date":"2026-03-01",
             "competition":"Premier League","stage":"Matchday 7","shotsFor":12,"shotsAgainst":6,"expectedGoals":2.1,
             "expectedGoalsAgainst":0.8,"possession":60,"passingAccuracy":88,
             "playerPerformances":[{"playerName":"Player 2","rating":7.5,"passes":50,"passAccuracy":90,"shots":0,"tackles":3,
               "tacklePercentage":66,"interceptions":2}]}
        """
        // The squad holds only the one player named in the payload, so there is nobody to top the line-up up with.
        val state = AppState(players = squad(2).drop(1))
        val match = run(state, event(payload)).state.recentMatches.single()
        assertEquals(1, match.playerPerformances.size)
        assertFalse(match.statsEstimated)
    }

    @Test
    fun theSameMatchFromTwoSourcesIsStoredOnce() {
        val state = AppState(players = squad(12))
        val first = run(state, event(matchBase, time = 1000))
        val second = run(first.state, event(matchBase, time = 2000, title = "Other title"))

        assertEquals(1, second.state.recentMatches.size)
        assertEquals(first.state, second.state)
        assertTrue(second.notices.isEmpty())
    }

    @Test
    fun missingStageNumbersTheLeagueMatchdayFromMatchesAlreadyPlayed() {
        val played = (1..3).map {
            MatchResult(opponentName = "Opp $it", isHome = true, goalsFor = 1, goalsAgainst = 0,
                matchDate = "2025-12-0$it", competition = "Premier League")
        }
        val payload = """{"dataType":"match","opponentName":"Spurs","isHome":true,"goalsFor":0,"goalsAgainst":0,
            "date":"2026-01-10","competition":"Premier League"}"""
        val match = run(AppState(recentMatches = played, players = squad(11)), event(payload)).state.recentMatches.first()
        // 3 league matches played -> this is matchday 4 (the old code counted them twice and said 7)
        assertEquals("Matchday 4", match.stage)
    }

    @Test
    fun the255PenaltyMarkerNeverReachesTheMatchOrTheMessage() {
        val payload = """{"dataType":"match","opponentName":"Leeds","isHome":true,"goalsFor":1,"goalsAgainst":0,
            "date":"2026-01-20","competition":"FA Cup","stage":"Round 3","homePenalties":255,"awayPenalties":255}"""
        val result = run(AppState(players = squad(11)), event(payload))
        val match = result.state.recentMatches.single()
        assertEquals(0, match.homePenalties)
        assertEquals(0, match.awayPenalties)
        assertFalse(match.isExtraTime)
        assertFalse(result.notices[0].message.contains("255"))
    }

    @Test
    fun penaltyShootoutIsKeptForCupTies() {
        val payload = """{"dataType":"match","opponentName":"Leeds","isHome":true,"goalsFor":1,"goalsAgainst":1,
            "date":"2026-01-20","competition":"FA Cup","stage":"Round 4","homePenalties":4,"awayPenalties":3}"""
        val result = run(AppState(players = squad(11)), event(payload))
        val match = result.state.recentMatches.single()
        assertEquals(4, match.homePenalties)
        assertTrue(match.isExtraTime)
        assertTrue(result.notices[0].message.endsWith("(Pens: 4-3)"))
    }

    @Test
    fun playoffMatchesCountAsTheClubsOwnLeague() {
        val payload = """{"dataType":"match","opponentName":"Leeds","isHome":true,"goalsFor":1,"goalsAgainst":0,
            "date":"2026-05-10","competition":"Championship Play-off","stage":"Play-off Semi Final Leg 1"}"""
        val match = run(AppState(leagueName = "EFL Championship", players = squad(11)), event(payload))
            .state.recentMatches.single()
        assertEquals("EFL Championship", match.competition)
        assertEquals("Play-off Semi Final Leg 1", match.stage)
    }

    @Test
    fun matchUpdatesSeasonTotalsAndAverageRating() {
        val payload = """{"dataType":"match","detailSource":"atlas","opponentName":"Fulham","isHome":true,"goalsFor":2,"goalsAgainst":0,
            "date":"2026-01-10","competition":"Premier League","stage":"Matchday 2",
            "shotsFor":1,"shotsAgainst":1,"expectedGoals":1,"expectedGoalsAgainst":1,"possession":50,"passingAccuracy":80,
            "playerPerformances":[{"playerName":"Player 6","goals":2,"assists":1,"rating":9.0,"passes":1,"passAccuracy":1,"shots":1,
              "tackles":0,"interceptions":0}]}"""
        val before = squad(8).map { if (it.id == "p6") it.copy(matchesPlayed = 1, goals = 1, averageRating = 7.0) else it }
        val after = run(AppState(players = before), event(payload)).state.players.first { it.id == "p6" }
        assertEquals(2, after.matchesPlayed)
        assertEquals(3, after.goals)
        assertEquals(1, after.assists)
        assertEquals(8.0, after.averageRating, 0.001)
    }

    @Test
    fun cleanSheetIsGivenToGoalkeeperAndDefendersOnly() {
        val payload = """{"dataType":"match","opponentName":"Fulham","isHome":true,"goalsFor":1,"goalsAgainst":0,
            "date":"2026-01-10","competition":"Premier League","stage":"Matchday 2"}"""
        val match = run(AppState(players = squad(11)), event(payload)).state.recentMatches.single()
        val byId = match.playerPerformances.associateBy { it.playerId }
        assertTrue(byId.getValue("p1").cleanSheet) // GK
        assertTrue(byId.getValue("p3").cleanSheet) // CB
        assertFalse(byId.getValue("p9").cleanSheet) // CM
    }

    // ---- transfers ---------------------------------------------------------------------------------

    private fun transfer(name: String, type: String, fee: Double = 10.0, date: String = "2026-01-15") =
        """{"dataType":"transfer","title":"Transfer","message":"$name $type","playerName":"$name","type":"$type",
           "feeM":$fee,"fromToClub":"Other FC","date":"$date"}"""

    @Test
    fun signingUsesTheDatabaseCardWhenThereIsOne() {
        val card = Player(name = "Star Striker", overall = 88, potential = 91, primaryPosition = "ST", valueM = 80.0)
        val result = run(AppState(players = squad(5)), event(transfer("Star Striker", "In (Permanent)", fee = 55.0)), signed = { card })

        val added = result.state.players.first { it.name == "Star Striker" }
        assertEquals(88, added.overall)
        assertEquals("ST", added.primaryPosition)
        assertEquals(55.0, added.valueM, 0.001) // the fee paid wins over the card's market value
        assertEquals("Active", added.status)
        assertEquals("transfers", result.notices.single().route)
        assertTrue(result.notices.single().isTransfer)
    }

    @Test
    fun signingWithoutADatabaseCardAddsAPlaceholder() {
        val result = run(AppState(players = squad(5)), event(transfer("Unknown Kid", "In (Permanent)", fee = 0.0)))
        val added = result.state.players.first { it.name == "Unknown Kid" }
        assertEquals(72, added.overall)
        assertEquals(2.0, added.valueM, 0.001)
    }

    @Test
    fun loanInIsMarkedAsLoanedIn() {
        val result = run(AppState(), event(transfer("Loan Guy", "In (Loan)", fee = 0.0)))
        val added = result.state.players.single()
        assertEquals("Loaned In", added.status)
        assertTrue(added.isLoanedIn)
    }

    @Test
    fun sellingAPlayerMarksThemSoldAndTakesThemOutOfTheStartingEleven() {
        val state = AppState(players = squad(3).map { if (it.id == "p2") it.copy(isStartingXI = true) else it })
        val result = run(state, event(transfer("Player 2", "Out (Permanent)", fee = 30.0)))
        val sold = result.state.players.first { it.id == "p2" }
        assertEquals("Sold", sold.status)
        assertFalse(sold.isStartingXI)
        assertEquals(1, result.state.transfers.size)
    }

    @Test
    fun loanOutIsRecognisedBeforePlainOut() {
        val result = run(AppState(players = squad(3)), event(transfer("Player 2", "Out (Loan)", fee = 0.0)))
        val loaned = result.state.players.first { it.id == "p2" }
        assertEquals("Loaned Out", loaned.status)
        assertTrue(loaned.isLoanedOut)
    }

    @Test
    fun theSameTransferTwiceIsOnlyRecordedOnce() {
        val first = run(AppState(players = squad(3)), event(transfer("Player 2", "Out (Permanent)"), time = 1))
        val second = run(first.state, event(transfer("player 2", "out (permanent)"), time = 2))
        assertEquals(1, second.state.transfers.size)
        assertEquals(first.state, second.state)
        assertTrue(second.notices.isEmpty())
    }

    @Test
    fun retirementMarksThePlayerRetired() {
        val result = run(AppState(players = squad(3)), event(transfer("Player 3", "Retirement", fee = 0.0)))
        assertEquals("Retired", result.state.players.first { it.id == "p3" }.status)
    }

    // ---- injuries ----------------------------------------------------------------------------------

    @Test
    fun injuryEventMarksThePlayerInjuredWithAReturnDate() {
        val payload = """{"dataType":"injury","title":"Medical Report","message":"Player 4 hurt","playerName":"Player 4",
            "duration":21,"injuryName":"Sprained Ankle"}"""
        val result = run(AppState(players = squad(5)), event(payload))
        val hurt = result.state.players.first { it.id == "p4" }
        assertTrue(hurt.isInjured)
        assertEquals("Sprained Ankle", hurt.injuryName)
        assertEquals("2026-01-10", hurt.injuryStartDate)
        assertEquals("2026-01-31", hurt.injuryReturnDate)
        assertEquals(1, result.state.activeInjuries)
        assertEquals("squad", result.notices.single().route)
    }

    // ---- robustness --------------------------------------------------------------------------------

    @Test
    fun aBrokenEventDoesNotStopTheOnesAfterIt() {
        val broken = JSONObject().put("time", 1).put("title", "Bad").put("message", "{ this is not json }")
        val good = event(transfer("Player 2", "Out (Permanent)"), time = 2)
        val result = run(AppState(players = squad(3)), broken, good)
        assertEquals(1, result.state.transfers.size)
    }

    @Test
    fun unknownStructuredPayloadsAreIgnored() {
        val result = run(AppState(), event("""{"dataType":"snapshot","title":"x","message":"y"}"""))
        assertTrue(result.state.liveEvents.isEmpty())
        assertTrue(result.notices.isEmpty())
    }

    @Test
    fun plainTextMessageBecomesALiveEventAndTheRightNotification() {
        val result = run(AppState(), plainEvent("Full Time: Us 2 - 1 Them", title = "Result"))
        assertEquals(1, result.state.liveEvents.size)
        assertEquals("matches", result.notices.single().route)

        val signing = run(AppState(), plainEvent("Club signed Somebody for a fee of 5M", title = "News"))
        assertEquals("transfers", signing.notices.single().route)
    }

    @Test
    fun nothingNewMeansTheStateIsReturnedUnchanged() {
        val state = AppState(liveEvents = listOf(LiveEvent("Note", "hello", 1000)))
        val result = run(state, plainEvent("hello", time = 1000, title = "Note"))
        assertTrue(result.state === state)
        assertTrue(result.notices.isEmpty())
    }

    @Test
    fun noNoticesWhenNotifyIsOff() {
        val result = run(AppState(players = squad(3)), event(transfer("Player 2", "Out (Permanent)")), notify = false)
        assertEquals(1, result.state.transfers.size)
        assertTrue(result.notices.isEmpty())
    }

    @Test
    fun liveEventListIsCappedAtTheNewestEntries() {
        val old = (1..TelemetryProcessor.MAX_LIVE_EVENTS).map { LiveEvent("Old $it", "m", it.toLong()) }
        val result = run(AppState(liveEvents = old), plainEvent("fresh", time = 100_000, title = "Fresh"))
        assertEquals(TelemetryProcessor.MAX_LIVE_EVENTS, result.state.liveEvents.size)
        assertEquals("Fresh", result.state.liveEvents.first().title)
        assertNull(result.state.liveEvents.firstOrNull { it.title == "Old 1" })
    }

    @Test
    fun sanitizeRemovesTheNotSetMarker() {
        assertEquals("Full Time 1 - 0", TelemetryProcessor.sanitizeEventMessage("Full Time 1 - 0 (Pens: 255-3)"))
        assertEquals("0 penalties", TelemetryProcessor.sanitizeEventMessage("255 penalties"))
        assertEquals("Score 2 - 255x", TelemetryProcessor.sanitizeEventMessage("Score 2 - 255x")) // not a standalone 255
        assertNotNull(TelemetryProcessor.sanitizeEventMessage(""))
    }

    @Test
    fun plusDaysCrossesMonthEndsAndRejectsGarbage() {
        assertEquals("2026-02-03", TelemetryProcessor.plusDays("2026-01-31", 3))
        assertNull(TelemetryProcessor.plusDays("not a date", 3))
    }
}
