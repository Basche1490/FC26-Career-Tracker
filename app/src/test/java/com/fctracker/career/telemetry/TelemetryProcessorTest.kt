package com.fctracker.career.telemetry

import com.fctracker.career.AppState
import com.fctracker.career.LiveEvent
import com.fctracker.career.MatchResult
import com.fctracker.career.Player
import com.fctracker.career.feeLabel
import com.fctracker.career.isIncomingDeal
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

    private val matchWithOpponentMotm = """
        {"dataType":"match","detailSource":"atlas","opponentName":"Chelsea","isHome":true,"goalsFor":1,"goalsAgainst":3,
         "date":"2026-02-08","competition":"Premier League","stage":"Matchday 7",
         "shotsFor":7,"shotsAgainst":14,"expectedGoals":0.8,"expectedGoalsAgainst":2.6,"possession":38,
         "possessionEstimated":true,"passingAccuracy":78,"motmName":"Cole Palmer","motmIsOpponent":true,
         "playerPerformances":[
           {"playerId":"p1","playerName":"Player 1","rating":6.2,"goals":0,"assists":0,"passes":20,"passAccuracy":80,"shots":0,
            "tackles":1,"tacklePercentage":50,"interceptions":0,"saves":4,"penaltySaves":1,"isMotm":false},
           {"playerId":"p6","playerName":"Player 6","rating":7.4,"goals":1,"assists":0,"passes":30,"passAccuracy":85,"shots":3,
            "shotAccuracy":66,"tackles":0,"tacklePercentage":0,"interceptions":0,"isMotm":false,"subOutMinute":45},
           {"playerId":"p7","playerName":"Player 7","rating":6.8,"goals":0,"assists":0,"passes":25,"passAccuracy":80,"shots":0,
            "tackles":2,"tacklePercentage":50,"interceptions":1,"isMotm":false,"subInMinute":45},
           {"playerId":"461607","playerName":"Fabian Bravo","rating":7.0,"goals":0,"assists":0,"passes":0,"passAccuracy":0,"shots":0,
            "tackles":0,"tacklePercentage":0,"interceptions":0,"isMotm":false}],
         "substitutions":[{"minute":45,"playerOutName":"Player 6","playerInName":"Player 7","playerOutId":"p6","playerInId":"p7"},
                          {"minute":70,"playerOutName":"Fabian Bravo","playerInName":"Someone New","playerOutId":"461607","playerInId":"555"}]}
    """

    @Test
    fun opposingManOfTheMatchIsNamedAndNoneOfOurPlayersIsStarred() {
        val state = AppState(leagueName = "Premier League", players = squad(14))
        val match = run(state, event(matchWithOpponentMotm)).state.recentMatches.single()

        assertEquals("Cole Palmer", match.motmName)
        assertTrue(match.motmIsOpponent)
        assertTrue(match.playerPerformances.none { it.isMotm }) // our best player used to get the star anyway
    }

    @Test
    fun opposingManOfTheMatchIsRecognisedEvenWhenThePayloadDoesNotSayIt() {
        // An older script sent the name only: nobody in the export carries it, so he played for the other side.
        val payload = matchWithOpponentMotm.replace(""""motmIsOpponent":true,""", "")
        val match = run(AppState(leagueName = "Premier League", players = squad(14)), event(payload)).state.recentMatches.single()

        assertTrue(match.motmIsOpponent)
        assertTrue(match.playerPerformances.none { it.isMotm })
    }

    @Test
    fun ourOwnManOfTheMatchIsStarredAndNotCalledAnOpponent() {
        val payload = matchWithOpponentMotm
            .replace(""""motmName":"Cole Palmer","motmIsOpponent":true""", """"motmName":"Player 6"""")
        val match = run(AppState(leagueName = "Premier League", players = squad(14)), event(payload)).state.recentMatches.single()

        assertFalse(match.motmIsOpponent)
        assertEquals(listOf("p6"), match.playerPerformances.filter { it.isMotm }.map { it.playerId })
    }

    @Test
    fun substitutionsSavesAndAPlayerWhoIsNotInTheSquadSurviveIntoTheMatch() {
        val match = run(AppState(leagueName = "Premier League", players = squad(14)), event(matchWithOpponentMotm))
            .state.recentMatches.single()

        assertEquals(2, match.substitutions.size)
        val first = match.substitutions[0]
        assertEquals(45, first.minute)
        assertEquals("p6", first.playerOutId)
        assertEquals("p7", first.playerInId)
        // a player the squad does not know (a loan signing) is shown by the name the game gave
        val second = match.substitutions[1]
        assertEquals("Fabian Bravo", second.playerOutName)
        assertEquals("Someone New", second.playerInName)

        val keeper = match.playerPerformances.first { it.playerId == "p1" }
        assertEquals(4, keeper.saves)
        assertEquals(1, keeper.penaltySaves)
        assertEquals(45, match.playerPerformances.first { it.playerId == "p6" }.subOutMinute)
        assertEquals(45, match.playerPerformances.first { it.playerId == "p7" }.subInMinute)

        val loanee = match.playerPerformances.first { it.playerId == "461607" }
        assertEquals("Fabian Bravo", loanee.playerName)
        assertEquals(7.0, loanee.rating, 0.001)
        assertEquals(0, loanee.passes) // nothing is invented for a player the export had no numbers for
        assertEquals(0, loanee.passAccuracy)
        assertEquals(4, match.playerPerformances.size) // nobody is invented around the real line-up

        assertTrue(match.possessionEstimated)
        assertEquals(38, match.possession)
    }

    @Test
    fun goalsKeepTheScorersNamesAndGuessedMinutesAreMarked() {
        val payload = """
            {"dataType":"match","detailSource":"atlas","opponentName":"Chelsea","isHome":true,"goalsFor":2,"goalsAgainst":2,
             "date":"2026-02-08","competition":"Premier League","stage":"Matchday 7","possession":50,
             "playerPerformances":[
               {"playerId":"p1","playerName":"Player 1","position":"GK","rating":6.2},
               {"playerId":"p6","playerName":"Player 6","position":"ST","rating":7.4,"goals":1},
               {"playerId":"461607","playerName":"Fabian Bravo","position":"CB","rating":7.0,"goals":1}],
             "goalDetails":[
               {"playerName":"Player 6","minute":30,"isOpponent":false,"minuteEstimated":true},
               {"playerName":"Fabian Bravo","minute":60,"isOpponent":false,"minuteEstimated":true},
               {"playerName":"Cole Palmer","minute":20,"isOpponent":true,"minuteEstimated":true},
               {"playerName":"Chelsea Goal","minute":70,"isOpponent":true,"minuteEstimated":true}]}
        """
        val match = run(AppState(leagueName = "Premier League", players = squad(14)), event(payload)).state.recentMatches.single()

        val ours = match.goalDetails.filter { it.scorerId != null }
        assertEquals(listOf("p6", "Fabian Bravo"), ours.map { it.scorerId })        // the second one is not in the squad
        assertEquals(listOf("Player 6", "Fabian Bravo"), ours.map { it.scorerName })
        assertTrue(match.goalDetails.all { it.minuteEstimated })

        val theirs = match.goalDetails.filter { it.scorerId == null }
        assertEquals(listOf("Cole Palmer", null), theirs.map { it.scorerName })      // "<club> Goal" is a stand-in, not a name

        assertEquals(listOf("GK", "ST", "CB"), match.playerPerformances.map { it.position })
    }

    @Test
    fun incomingAndOutgoingDealsAreTold() {
        fun t(type: String) = com.fctracker.career.TransferLog(type = type).isIncomingDeal()
        assertTrue(listOf("In (Permanent)", "In (Loan)", "Promoted In", "Free Agent Sign", "Bought").all { t(it) })
        assertFalse(listOf("Out (Permanent)", "Out (Loan)", "Retirement", "Sold", "Loaned Out").any { t(it) })
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

    private fun deal(
        name: String, type: String, fee: Double, club: String, date: String = "2028-07-09", feeUnknown: Boolean = false
    ): String {
        val unknownField = if (feeUnknown) ",\"feeUnknown\":true" else ""
        return """{"dataType":"transfer","title":"Transfer","message":"$name $type","playerName":"$name","type":"$type",
           "feeM":$fee,"fromToClub":"$club","date":"$date"$unknownField}"""
    }

    @Test
    fun aFeeTheGameDoesNotStoreIsKeptAsUnknownNotAsFree() {
        val result = run(AppState(), event(deal("Oliver Ebner", "In (Permanent)", 0.0, "Unknown club", feeUnknown = true)))
        val log = result.state.transfers.single()
        assertTrue(log.feeUnknown)
        assertEquals("Fee ?", log.feeLabel("€"))
        assertEquals("Free", log.copy(feeUnknown = false).feeLabel("€"))
        assertEquals("€26.0M", log.copy(feeM = 26.0).feeLabel("€"))
    }

    @Test
    fun aKnownFeeIsNeverMarkedUnknown() {
        val result = run(AppState(), event(deal("Leo Guerra", "In (Permanent)", 26.0, "Unknown club", feeUnknown = true)))
        assertFalse(result.state.transfers.single().feeUnknown)
    }

    @Test
    fun aRepeatedLoanFillsInTheClubTheFirstCopyDidNotHave() {
        val first = run(AppState(players = squad(3)), event(deal("Player 2", "Out (Loan)", 0.0, ""), time = 1))
        val second = run(first.state, event(deal("Player 2", "Out (Loan)", 0.0, "FC Porto"), time = 2))
        assertEquals(1, second.state.transfers.size)
        assertEquals("FC Porto", second.state.transfers.single().fromToClub)
    }

    @Test
    fun aCorrectedSigningReplacesTheEarlierWrongPromotion() {
        val first = run(AppState(), event(deal("Leo Guerra", "Promoted In", 0.0, "Youth Academy"), time = 1))
        val second = run(first.state, event(deal("Leo Guerra", "In (Permanent)", 26.0, "Roma"), time = 2))
        val log = second.state.transfers.single()
        assertEquals("In (Permanent)", log.type)
        assertEquals(26.0, log.feeM, 0.001)
        assertEquals("Roma", log.fromToClub)
        assertFalse(log.feeUnknown)
    }

    @Test
    fun aCorrectedSigningWithAnUnknownFeeKeepsThatHonest() {
        val first = run(AppState(), event(deal("Oliver Ebner", "Promoted In", 0.0, "Youth Academy", "2028-07-14"), time = 1))
        val second = run(
            first.state,
            event(deal("Oliver Ebner", "In (Permanent)", 0.0, "Unknown club", "2028-07-14", feeUnknown = true), time = 2)
        )
        val log = second.state.transfers.single()
        assertEquals("In (Permanent)", log.type)
        assertTrue(log.feeUnknown)
        assertEquals("Unknown club", log.fromToClub)
    }

    @Test
    fun aRepeatedTransferNeverOverwritesWhatWasEditedByHand() {
        val edited = AppState(
            transfers = listOf(
                com.fctracker.career.TransferLog(
                    playerName = "Oliver Ebner", type = "In (Permanent)", feeM = 11.7, fromToClub = "Athletic Club", date = "2028-07-14"
                )
            )
        )
        val result = run(edited, event(deal("Oliver Ebner", "In (Permanent)", 5.0, "Elsewhere", "2028-07-14")))
        assertEquals(edited, result.state)
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
