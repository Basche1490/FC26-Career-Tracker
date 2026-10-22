package com.fctracker.career.telemetry

import com.fctracker.career.AppState
import com.fctracker.career.GoalDetail
import com.fctracker.career.LiveEvent
import com.fctracker.career.MatchResult
import com.fctracker.career.Player
import com.fctracker.career.PlayerPerformance
import com.fctracker.career.Substitution
import com.fctracker.career.TransferLog
import com.fctracker.career.isPlaceholderPlayerName
import com.fctracker.career.logic.GameProfile
import com.fctracker.career.logic.PlayerCards
import com.fctracker.career.logic.PlayerLookup
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** One phone notification the processor wants shown. */
data class TelemetryNotice(
    val title: String,
    val message: String,
    val route: String,
    val isTransfer: Boolean
)

data class TelemetryResult(val state: AppState, val notices: List<TelemetryNotice>)

/**
 * Turns the events the PC bridge delivers (matches, transfers, injuries, plain messages) into changes to the
 * career state.
 *
 * It is a pure function of its inputs: it touches no Android classes, no clock and no storage. That keeps it
 * testable on a plain JVM and lets the caller run it inside StateFlow.update { } so that edits made on screen
 * while a poll is being processed are never overwritten.
 */
object TelemetryProcessor {

    /** Most live-event rows kept in a save. The bridge only ever holds the latest 100. */
    const val MAX_LIVE_EVENTS = 300

    /**
     * Only events younger than this raise a phone alert. The bridge keeps its latest 100 events for a while, so a
     * fresh install or a restored backup would otherwise light up with alerts about matches played days ago.
     */
    const val ALERT_WINDOW_SECONDS = 24L * 60 * 60

    private val LEAGUE_NAMES = setOf("Serie A", "La Liga", "Bundesliga", "Ligue 1")
    private val DEFENCE = listOf("CB", "LB", "RB", "LWB", "RWB")
    private val MIDFIELD = listOf("CM", "CDM", "CAM", "LM", "RM")

    /**
     * @param notify whether notices should be produced for new items (false when replaying a manual test)
     * @param nowSeconds fallback timestamp for events that carry none
     * @param today yyyy-MM-dd, used when a payload has no date
     * @param crestUrlFor maps an opponent name to a crest image URL
     * @param signedPlayer looks a newly signed player up in the built-in player database
     * @param lookup the built-in player database: a signing by the game's id, and players' short names
     */
    fun apply(
        state: AppState,
        eventObjects: List<JSONObject>,
        notify: Boolean,
        nowSeconds: Long,
        today: String,
        crestUrlFor: (String) -> String = { "" },
        signedPlayer: (String) -> Player? = { null },
        lookup: PlayerLookup = PlayerLookup.NONE
    ): TelemetryResult {
        var base = state
        val events = state.liveEvents.toMutableList()
        val transfers = state.transfers.toMutableList()
        val matches = state.recentMatches.toMutableList()
        var squad = state.players.toMutableList()
        var academy = state.academyPlayers
        var careerDate = state.careerDate
        var injuries = state.activeInjuries
        val notices = mutableListOf<TelemetryNotice>()
        var hasNew = false

        for (json in eventObjects) {
            val timestamp = json.optLong("time", nowSeconds)
            val rawMessage = json.optString("message", "")
            val rawTitle = json.optString("title", "Career Update")
            val trimmedMessage = rawMessage.trim()
            // Alerts are for what just happened: an old event is stored quietly (see ALERT_WINDOW_SECONDS).
            val alert = notify && nowSeconds - timestamp <= ALERT_WINDOW_SECONDS

            if (trimmedMessage.startsWith("{") && trimmedMessage.endsWith("}")) {
                // A malformed event is skipped; it must never stop the events after it.
                val data = try { JSONObject(trimmedMessage) } catch (e: Exception) { null } ?: continue
                val dataType = data.optString("dataType")
                val evTitle = data.optString("title", rawTitle)
                val evMessage = data.optString("message", "")

                when (dataType) {
                    "transfer" -> {
                        val rawName = data.optString("playerName")
                        // the name the game shows ("Pedri", not "Pedro González López")
                        val name = lookup.shortName(rawName)
                        val type = data.optString("type")
                        val date = data.optString("date")
                        val feeM = data.optDouble("feeM", 0.0)
                        val fromToClub = data.optString("fromToClub")
                        val feeUnknown = data.optBoolean("feeUnknown", false)
                        val gameId = data.optInt("playerId", 0)
                        val profile = GameProfile.parse(data.optJSONObject("profile"))

                        val existingIdx = transfers.indexOfFirst { sameDeal(it, name, type, date, lookup) }
                        if (existingIdx >= 0) {
                            // The same deal again. Take over what the earlier copy was missing (a club, a fee, a
                            // corrected type) but never overwrite what is already there, so edits made by hand stay.
                            val merged = mergeRepeatedTransfer(transfers[existingIdx], type, feeM, fromToClub, feeUnknown)
                            if (merged != transfers[existingIdx]) {
                                transfers[existingIdx] = merged
                                hasNew = true
                            }
                        } else {
                            if (events.none { it.title == evTitle && it.timestamp == timestamp }) {
                                events.add(LiveEvent(evTitle, evMessage, timestamp))
                            }
                            hasNew = true
                            transfers.add(
                                0,
                                TransferLog(
                                    playerName = name, type = type, feeM = feeM, fromToClub = fromToClub,
                                    date = date, feeUnknown = feeUnknown && feeM <= 0.0
                                )
                            )
                            // a prospect the game promoted: his academy card moves up, no second card is added
                            val promoted = if (type.equals("Promoted In", true) || fromToClub.equals("Youth Academy", true))
                                com.fctracker.career.logic.Academy.promote(squad, academy, name, state.startYear) else null
                            if (promoted != null) {
                                squad = promoted.first.toMutableList()
                                academy = promoted.second
                            } else {
                                val signing = Signing(name, rawName, gameId, profile, state.startYear, nationNames(squad))
                                squad = applyTransferToSquad(squad, signing, type, feeM, signedPlayer, lookup)
                            }
                            if (alert) notices.add(TelemetryNotice(evTitle, evMessage, "transfers", true))
                        }
                    }

                    "match" -> {
                        val outcome = applyMatch(
                            state, data, timestamp, evTitle, events, matches, squad, today, crestUrlFor
                        )
                        if (outcome != null) {
                            squad = outcome.squad
                            hasNew = true
                            if (alert) notices.add(TelemetryNotice(evTitle, outcome.message, "matches", false))
                        }
                    }

                    "injury" -> {
                        val name = data.optString("playerName")
                        val idx = squad.indexOfFirst {
                            it.name.equals(name, ignoreCase = true) || it.displayName.equals(name, ignoreCase = true)
                        }
                        if (events.none { it.title == evTitle && it.timestamp == timestamp }) {
                            events.add(LiveEvent(evTitle, evMessage, timestamp))
                            hasNew = true
                            injuries += 1
                            if (idx >= 0) {
                                val days = data.optInt("duration", 0)
                                val injuryName = data.optString("injuryName", data.optString("injuryType", "Injury"))
                                    .ifBlank { "Injury" }
                                val returnDate = if (days > 0) plusDays(today, days) else null
                                squad[idx] = squad[idx].copy(
                                    isInjured = true,
                                    injuryName = injuryName,
                                    injuryStartDate = today,
                                    injuryReturnDate = returnDate
                                )
                            }
                            if (alert) notices.add(TelemetryNotice(evTitle, evMessage, "squad", false))
                        }
                    }
                    "squad" -> {
                        // Contract and loan end dates, straight from the game. Stored quietly: no live-event row, no alert.
                        val report = SquadReport.parse(data)
                        val applied = SquadReport.apply(squad, academy, report)
                        if (applied.changed) {
                            squad = applied.squad.toMutableList()
                            academy = applied.academy
                            hasNew = true
                        }
                        // a deal recorded as a signing (or a sale) that the game lists as a loan becomes the loan
                        val corrected = loanCorrections(transfers, squad, state.startYear)
                        if (corrected != transfers) {
                            transfers.clear(); transfers.addAll(corrected)
                            hasNew = true
                        }
                        if (report.careerDate != null && report.careerDate != careerDate) {
                            careerDate = report.careerDate   // the newest report wins: a reloaded older save moves it back
                            hasNew = true
                        }
                    }
                    "career" -> {
                        // Fixtures, tables, statistics, injuries, awards, winners, the European ranking...: stored
                        // quietly. A new injury is the one part that raises an alert.
                        val before = base.copy(players = squad, academyPlayers = academy, careerDate = careerDate, activeInjuries = injuries)
                        val r = CareerReport.apply(before, data, lookup)
                        if (r.changed) {
                            base = r.state
                            squad = r.state.players.toMutableList()
                            careerDate = r.state.careerDate
                            injuries = r.state.activeInjuries
                            hasNew = true
                            for (who in r.newInjuries) {
                                val p = squad.firstOrNull { it.displayName == who }
                                val msg = "$who is injured${p?.injuryName?.let { " ($it)" } ?: ""}${p?.injuryReturnDate?.let { ", back about ${ContractTime.fullDate(it)}" } ?: ""}"
                                if (events.none { it.title == "Injury" && it.message == msg }) events.add(LiveEvent("Injury", msg, timestamp))
                                if (alert) notices.add(TelemetryNotice("Injury", msg, "squad", false))
                            }
                            for (trophy in r.newTrophies) {
                                val msg = "Trophy won: $trophy"
                                if (events.none { it.title == "Trophy" && it.message == msg }) events.add(LiveEvent("Trophy", msg, timestamp))
                                if (alert) notices.add(TelemetryNotice("Trophy won", msg, "season_summary", false))
                            }
                        }
                    }
                    // Any other structured payload (for example a snapshot) is ignored, as before.
                }
            } else if (trimmedMessage.isNotEmpty()) {
                val sanitized = sanitizeEventMessage(rawMessage)
                if (events.none { it.title == rawTitle && it.timestamp == timestamp }) {
                    events.add(LiveEvent(rawTitle, sanitized, timestamp))
                    hasNew = true
                    if (alert) {
                        val isTransfer = rawTitle.contains("transfer", true) ||
                            sanitized.contains("transfer", true) ||
                            sanitized.contains("signed", true) ||
                            sanitized.contains("fee", true)
                        val isMatch = rawTitle.contains("match", true) ||
                            sanitized.contains(" vs ", true) ||
                            sanitized.contains("Full Time", true) ||
                            sanitized.contains("FT:", true) ||
                            sanitized.contains("Goal", true)
                        val route = if (isMatch) "matches" else if (isTransfer) "transfers" else "dashboard"
                        notices.add(TelemetryNotice(rawTitle, sanitized, route, isTransfer))
                    }
                }
            }
        }

        if (!hasNew) return TelemetryResult(state, notices)

        // a card that has just learned its game id (squad or career report) takes over the line-up rows stored under it
        val linkedMatches = com.fctracker.career.logic.PlayerRepair.linkMatches(matches, squad + academy)

        return TelemetryResult(
            com.fctracker.career.logic.Academy.firstTeamOnly(
                base.copy(
                    liveEvents = events.sortedByDescending { it.timestamp }.take(MAX_LIVE_EVENTS),
                    transfers = transfers,
                    recentMatches = linkedMatches,
                    activeInjuries = injuries,
                    players = squad,
                    academyPlayers = academy,
                    careerDate = careerDate
                )
            ),
            notices
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Transfers
    // ---------------------------------------------------------------------------------------------

    /** Same player, same day and same kind of deal. An earlier "Promoted In" is the same deal as a later signing. */
    private fun sameDeal(existing: TransferLog, name: String, type: String, date: String, lookup: PlayerLookup): Boolean {
        val samePlayer = existing.playerName.equals(name, ignoreCase = true) || lookup.shortName(existing.playerName).equals(name, ignoreCase = true)
        if (!samePlayer || existing.date != date) return false
        return existing.type.equals(type, ignoreCase = true) ||
            (existing.type.equals("Promoted In", ignoreCase = true) && type.equals("In (Permanent)", ignoreCase = true))
    }

    private fun isKnownClub(club: String): Boolean {
        val c = club.trim()
        return c.isNotEmpty() && !c.equals("Unknown club", true) && !c.equals("External Club", true) &&
            !c.equals("Youth Academy", true)
    }

    private fun mergeRepeatedTransfer(
        existing: TransferLog,
        type: String,
        feeM: Double,
        fromToClub: String,
        feeUnknown: Boolean
    ): TransferLog {
        var merged = existing
        if (existing.type.equals("Promoted In", true) && type.equals("In (Permanent)", true)) {
            // The first copy took the player for a promotion; the corrected copy knows he was signed.
            merged = merged.copy(type = "In (Permanent)")
            if (!isKnownClub(existing.fromToClub) || existing.fromToClub.equals("Youth Academy", true)) {
                merged = merged.copy(fromToClub = if (isKnownClub(fromToClub)) fromToClub else "Unknown club")
            }
        }
        if (!isKnownClub(merged.fromToClub) && isKnownClub(fromToClub)) merged = merged.copy(fromToClub = fromToClub)
        if (merged.feeM <= 0.0 && feeM > 0.0) merged = merged.copy(feeM = feeM, feeUnknown = false)
        if (merged.feeM <= 0.0 && feeUnknown && !merged.feeUnknown && existing.type != merged.type) {
            merged = merged.copy(feeUnknown = true) // a corrected signing whose fee the game does not store
        }
        return merged
    }

    /**
     * The game knows best whether a player is here on loan (or away on loan): a "signing" with no fee of a player the
     * squad report shows on loan from another club is turned into the loan (with that club), and a "sale" with no fee of
     * a player who is away on loan into the loan out. Only this season's deals are looked at.
     */
    internal fun loanCorrections(transfers: List<TransferLog>, squad: List<Player>, seasonYear: Int): List<TransferLog> {
        val seasonStart = "%04d-07-01".format(seasonYear)
        fun named(t: TransferLog, p: Player) = t.playerName.equals(p.displayName, true) || t.playerName.equals(p.name, true)
        return transfers.map { t ->
            if (t.date < seasonStart || t.feeM > 0.0) return@map t
            val p = squad.firstOrNull { named(t, it) && it.loanDirection != null } ?: return@map t
            when {
                p.loanDirection == "in" && t.type == "In (Permanent)" ->
                    t.copy(type = "In (Loan)", feeUnknown = false, fromToClub = p.loanClub?.takeIf { it.isNotBlank() && !isKnownClub(t.fromToClub) } ?: t.fromToClub)
                p.loanDirection == "out" && t.type == "Out (Permanent)" ->
                    t.copy(type = "Out (Loan)", feeUnknown = false, fromToClub = p.loanClub?.takeIf { it.isNotBlank() && !isKnownClub(t.fromToClub) } ?: t.fromToClub)
                else -> t
            }
        }
    }

    /** Who a transfer is about: his short and raw names, the game's id, his details from the game. */
    private class Signing(
        val name: String,
        val rawName: String,
        val gameId: Int,
        val profile: GameProfile?,
        val seasonYear: Int,
        val nations: Map<Int, String>
    )

    /**
     * Nation names by the game's id, as the career's own players show them (a squad report or a database card named
     * the nation of a player whose id is known): better than the app's built-in list for a regen's nation.
     */
    private fun nationNames(squad: List<Player>): Map<Int, String> =
        squad.filter { it.nationalityId != null && it.nationality.isNotBlank() && !it.nationality.equals("Unknown", true) }
            .groupBy { it.nationalityId!! }.mapValues { (_, list) -> list.groupingBy { it.nationality }.eachCount().maxByOrNull { it.value }!!.key }

    private fun applyTransferToSquad(
        squad: MutableList<Player>,
        who: Signing,
        type: String,
        feeM: Double,
        signedPlayer: (String) -> Player?,
        lookup: PlayerLookup
    ): MutableList<Player> {
        val name = who.name
        // the game's id finds him whatever he is called; otherwise any of his names
        val idx = squad.indexOfFirst { who.gameId > 0 && it.gamePlayerId == who.gameId }.takeIf { it >= 0 }
            ?: squad.indexOfFirst {
                it.name.equals(name, ignoreCase = true) || it.displayName.equals(name, ignoreCase = true) ||
                    it.name.equals(who.rawName, ignoreCase = true)
            }
        val isLoan = type.contains("Loan", ignoreCase = true)
        val nation = GameProfile.nationName(who.profile?.nation, who.nations)
        if (idx >= 0) {
            var existing = squad[idx]
            if (who.gameId > 0 && existing.gamePlayerId == null) existing = existing.copy(gamePlayerId = who.gameId)
            // a card that was made with guessed details takes the game's
            who.profile?.let { existing = PlayerCards.withProfile(existing, it, nation, who.seasonYear) }
            squad[idx] = when {
                type.contains("Out", true) && isLoan ->
                    existing.copy(status = "Loaned Out", isLoanedOut = true, isStartingXI = false)
                type.contains("Out", true) || type.equals("Sold", true) ->
                    existing.copy(status = "Sold", isLoanedOut = false, isStartingXI = false)
                type.contains("In", true) && isLoan ->
                    existing.copy(status = "Loaned In", isLoanedIn = true, isLoanedOut = false)
                type.equals("Retirement", true) || type.equals("Retired", true) ->
                    existing.copy(status = "Retired", isStartingXI = false)
                else ->
                    existing.copy(status = "Active", isLoanedOut = false, isAcademy = false)
            }
        } else if (type.contains("In", true) || type.contains("Sign", true)) {
            // The real card: by the game's id, then by name in the built-in player database; a regen or a youth
            // player is not in it, and his card is built from the game's details.
            val known = (if (who.gameId > 0) lookup.byGameId(who.gameId, who.seasonYear) else null)
                ?: lookup.byName(who.rawName, who.seasonYear) ?: signedPlayer(who.rawName)
            val base = known ?: Player(name = name, overall = who.profile?.overall ?: 72, valueM = if (feeM > 0) feeM else 2.0)
            var card = base.copy(
                status = if (isLoan) "Loaned In" else "Active",
                isLoanedIn = isLoan,
                valueM = if (feeM > 0) feeM else (known?.valueM?.takeIf { it > 0 } ?: 2.0),
                gamePlayerId = who.gameId.takeIf { it > 0 } ?: base.gamePlayerId
            )
            // a new card takes what the game says about him now (positions, ratings); a database card keeps its nation
            who.profile?.let { card = PlayerCards.withProfile(card, it, nation, who.seasonYear, force = true) }
            squad.add(card)
        }
        return squad
    }

    // ---------------------------------------------------------------------------------------------
    // Matches
    // ---------------------------------------------------------------------------------------------

    private class MatchOutcome(val squad: MutableList<Player>, val message: String)

    /** Returns null when the match is a duplicate (nothing changes). Adds to [events] and [matches] otherwise. */
    private fun applyMatch(
        state: AppState,
        data: JSONObject,
        timestamp: Long,
        evTitle: String,
        events: MutableList<LiveEvent>,
        matches: MutableList<MatchResult>,
        squadIn: MutableList<Player>,
        today: String,
        crestUrlFor: (String) -> String
    ): MatchOutcome? {
        val opponent = data.optString("opponentName", "Opponent")
        val isHome = data.optBoolean("isHome", true)
        val gf = data.optInt("goalsFor", 0)
        val ga = data.optInt("goalsAgainst", 0)
        val matchDate = data.optString("date", today)
        val rawComp = data.optString("competition", "League")
        val rawStage = data.optString("stage", "")
        val rawHp = data.optInt("homePenalties", 0)
        val rawAp = data.optInt("awayPenalties", 0)
        val fromAtlas = data.optString("detailSource") == "atlas"

        val isPlayoff = rawComp.contains("Play-off", true) || rawComp.contains("Playoff", true) ||
            rawComp.contains("Promotion", true) || rawStage.contains("Play-off", true) ||
            rawStage.contains("Playoff", true)

        // A promotion play-off still counts as the league the club is in.
        val comp = if (isPlayoff) state.leagueName.takeIf { it.isNotBlank() } ?: "EFL Championship" else rawComp

        val isLeagueComp = !isPlayoff && isLeagueCompetition(comp)

        val isPlayoffLeg1 = isPlayoff && (
            rawStage.contains("Leg 1", true) ||
                rawStage.contains("playoff", true) && !rawStage.contains("Leg 2", true) && !rawStage.contains("Final", true)
            )

        // EA FC memory uses 255 (0xFF) as the "no penalties taken" marker.
        val hp = if (isLeagueComp || isPlayoffLeg1 || rawHp >= 255 || rawHp < 0) 0 else rawHp
        val ap = if (isLeagueComp || isPlayoffLeg1 || rawAp >= 255 || rawAp < 0) 0 else rawAp
        val isET = if (isLeagueComp || isPlayoffLeg1) false else (data.optBoolean("isExtraTime", false) || hp > 0 || ap > 0)

        val stage = when {
            isPlayoff ->
                if (rawStage.contains("Play-off", true) || rawStage.contains("Playoff", true)) rawStage else "Play-off"
            isLeagueComp -> {
                // `matches` already holds the season's stored matches plus any added earlier in this batch.
                // (The old code also added state.recentMatches, which counted every league match twice.)
                val leagueSoFar = matches.count { isLeagueCompetition(it.competition) }
                val matchday = if (rawStage.contains("Matchday", true) && !rawStage.contains("Final", true)) {
                    Regex("\\d+").find(rawStage)?.value?.toIntOrNull() ?: (leagueSoFar + 1)
                } else leagueSoFar + 1
                "Matchday $matchday"
            }
            rawStage.isNotBlank() && !rawStage.equals("Matchday Final", true) -> rawStage
            isET || hp > 0 || ap > 0 -> "Knockout Stage"
            else -> "Round 1"
        }

        val etText = if (hp > 0 || ap > 0) " (Pens: $hp-$ap)" else if (isET) " (A.E.T.)" else ""
        val message = sanitizeEventMessage(
            "Full Time: ${state.clubName} ${if (isHome) "vs" else "@"} $gf - $ga $opponent ($comp - $stage)$etText"
        )

        // The same match can arrive from two sources (database + Atlas export): ignore the second one.
        val duplicate = matches.any { m ->
            m.opponentName.filter { it.isLetterOrDigit() }.equals(opponent.filter { it.isLetterOrDigit() }, ignoreCase = true) &&
                m.goalsFor == gf && m.goalsAgainst == ga && m.matchDate == matchDate && m.isHome == isHome
        }
        if (duplicate || events.any { it.title == evTitle && it.timestamp == timestamp }) return null

        events.add(LiveEvent(evTitle, message, timestamp))

        var estimated = false
        val parsed = parseMatchPerformances(data.optJSONArray("playerPerformances"), squadIn, exact = fromAtlas)
        estimated = estimated || parsed.usedDefaults
        val perfList = parsed.performances.toMutableList()

        // Fewer than 11 players in the line-up: top it up from the squad. Real exported line-ups
        // (detailSource = atlas) are complete, so nobody is invented for them.
        val present = perfList.map { it.playerId }.toSet()
        val bench = squadIn.filter {
            !it.isAcademy && !it.isLoanedOut && !it.status.equals("Sold", true) &&
                !present.contains(it.id) && !present.contains(it.displayName)
        }.sortedByDescending { it.overall }
        val needed = if (fromAtlas) 0 else (11 - perfList.size).coerceAtLeast(0)
        for (i in 0 until needed.coerceAtMost(bench.size)) {
            perfList.add(typicalPerformance(bench[i], i, gf, ga, ap, data.optInt("shotsAgainst", 5)))
            estimated = true
        }

        // Clean sheets for the goalkeeper and defenders.
        if (ga == 0) {
            for (idx in perfList.indices) {
                val perf = perfList[idx]
                val pos = squadIn.find { it.id == perf.playerId || it.displayName == perf.playerId }
                    ?.primaryPosition?.uppercase() ?: ""
                if (pos == "GK" || pos in DEFENCE) perfList[idx] = perf.copy(cleanSheet = true)
            }
        }

        // Man of the match: the payload's choice, otherwise the best-rated contribution.
        val best = perfList.maxByOrNull {
            (it.goals * 3) + (it.assists * 2) + (if (it.cleanSheet) 1.5 else 0.0) + it.rating
        }
        // The man of the match can play for the other team. When the game says he did but gave no name for him
        // ("Player"), nobody is named: our own best player must not be shown in his place.
        val declaredOpponent = data.optBoolean("motmIsOpponent", false)
        val rawMotm = data.optString("motmName")
            .takeIf { it.isNotBlank() && !(declaredOpponent && isPlaceholderPlayerName(it)) }
        val motm = rawMotm ?: if (declaredOpponent) "" else if (best != null) {
            squadIn.find { it.id == best.playerId }?.displayName ?: best.playerId
        } else ""

        // Our best player is only starred when the payload named nobody (or named one of ours that the squad list
        // spells differently); an opposing player's name must never star one of our players.
        val namedByPayload = rawMotm != null
        fun perfName(p: PlayerPerformance): String =
            squadIn.find { it.id == p.playerId }?.displayName ?: p.playerName ?: p.playerId
        val starredByPayload = perfList.any { it.isMotm }
        val anyNameMatch = perfList.any { perfName(it).equals(motm, ignoreCase = true) }
        val ourMotm = !declaredOpponent && (starredByPayload || anyNameMatch)
        val finalPerf = perfList.map { p ->
            val flagged = when {
                declaredOpponent -> false
                starredByPayload -> p.isMotm
                anyNameMatch -> perfName(p).equals(motm, ignoreCase = true)
                // an export lists everybody who played: a name that is not on the list belongs to the opponent
                namedByPayload && fromAtlas -> false
                else -> best?.playerId == p.playerId
            }
            p.copy(isMotm = flagged)
        }
        val motmIsOpponent = motm.isNotBlank() && (declaredOpponent || (fromAtlas && namedByPayload && !ourMotm))

        val goals = parseGoalDetails(data.optJSONArray("goalDetails"), squadIn)
        val subs = parseSubstitutions(data.optJSONArray("substitutions"), squadIn)

        // Team numbers the payload did not send are filled with typical values and tagged as estimated.
        fun has(key: String) = data.has(key) && !data.isNull(key)
        if (!(has("shotsFor") && has("shotsAgainst") && has("expectedGoals") && has("expectedGoalsAgainst") &&
                has("passingAccuracy"))
        ) estimated = true
        val shotsFor = data.optInt("shotsFor", if (gf > 0) gf * 3 + 2 else 6)
        val shotsAgainst = data.optInt("shotsAgainst", if (ga > 0) ga * 3 + 1 else 4)
        val xG = data.optDouble("expectedGoals", if (gf > 0) Math.round((gf * 0.82 + 0.35) * 100.0) / 100.0 else 0.45)
        val xGA = data.optDouble("expectedGoalsAgainst", if (ga > 0) Math.round((ga * 0.78 + 0.28) * 100.0) / 100.0 else 0.32)
        // Possession is only kept when the game gave a real figure. The export has none, and a share worked out
        // from the pass counts is not the number the game shows, so it is left out (0 = unknown) instead of guessed.
        val possession = if (has("possession") && !data.optBoolean("possessionEstimated", false)) {
            data.optInt("possession").coerceIn(0, 100)
        } else 0
        val passAcc = data.optInt("passingAccuracy", 86)
        // A league position belongs to league matches; a cup match has none (0 = blank). A promotion play-off still
        // counts as the club's own league, as before.
        val leaguePos = when {
            !isLeagueComp && !isPlayoff -> 0
            data.has("leaguePosition") && data.optInt("leaguePosition") > 0 -> data.optInt("leaguePosition")
            else -> state.leagueTable.finalRank.toIntOrNull() ?: 1
        }

        matches.add(
            0,
            MatchResult(
                id = "live-$timestamp",
                opponentName = opponent,
                isHome = isHome,
                goalsFor = gf,
                goalsAgainst = ga,
                matchDate = matchDate,
                competition = comp,
                stage = stage,
                isExtraTime = isET,
                homePenalties = hp,
                awayPenalties = ap,
                possession = possession,
                passingAccuracy = passAcc,
                shotsFor = shotsFor,
                shotsAgainst = shotsAgainst,
                expectedGoals = xG,
                expectedGoalsAgainst = xGA,
                motmName = motm,
                motmIsOpponent = motmIsOpponent,
                possessionEstimated = false,
                possessionConfirmed = possession > 0,
                leaguePosition = leaguePos,
                opponentCrestUrl = crestUrlFor(opponent),
                playerPerformances = finalPerf,
                goalDetails = goals,
                substitutions = subs,
                statsEstimated = estimated && !fromAtlas
            )
        )

        // Season totals for the players who played.
        val byKey = finalPerf.associateBy { it.playerId }
        val updated = squadIn.map { player ->
            val perf = byKey[player.id] ?: byKey[player.displayName] ?: byKey[player.name]
            if (perf == null) player else {
                val played = player.matchesPlayed + 1
                val avg = if (perf.rating > 0) {
                    if (player.matchesPlayed == 0) perf.rating
                    else Math.round(((player.averageRating * player.matchesPlayed + perf.rating) / played) * 10.0) / 10.0
                } else player.averageRating
                val base = player.copy(
                    matchesPlayed = played,
                    goals = player.goals + perf.goals,
                    assists = player.assists + perf.assists,
                    averageRating = avg
                )
                if (player.isInjured && player.injuryReturnDate != null && matchDate >= player.injuryReturnDate) {
                    base.copy(isInjured = false, injuryName = null, injuryStartDate = null, injuryReturnDate = null)
                } else base
            }
        }.toMutableList()

        return MatchOutcome(updated, message)
    }

    /** True for the club's own domestic league. Cups and the European competitions (the "Champions League" has "League" in its name) are not. */
    fun isLeagueCompetition(comp: String): Boolean {
        val c = comp.lowercase()
        if (NOT_A_LEAGUE.any { c.contains(it) }) return false
        return comp.contains("League", true) || comp.contains("Division", true) || comp.contains("Championship", true) ||
            comp.contains("Premier", true) || comp in LEAGUE_NAMES
    }

    private val NOT_A_LEAGUE = listOf(
        "cup", "champions league", "europa league", "conference league", "trophy", "shield",
        "supercup", "supercopa", "supercoppa", "friendl"
    )

    /** A believable line for a player the payload said nothing about. Always flagged as estimated by the caller. */
    private fun typicalPerformance(p: Player, i: Int, gf: Int, ga: Int, ap: Int, shotsAgainst: Int): PlayerPerformance {
        val pos = p.primaryPosition.uppercase()
        val isGk = pos == "GK"
        val isDef = pos in DEFENCE
        val isMid = pos in MIDFIELD
        val rating = when {
            gf > ga -> 7.2 + (i % 4) * 0.2
            gf < ga -> 6.4 + (i % 3) * 0.2
            else -> 6.8 + (i % 3) * 0.2
        }.coerceIn(5.5, 9.2)
        val passes = when {
            isMid -> 50 + (i * 3) % 25
            isDef -> 40 + (i * 2) % 20
            isGk -> 20 + (i * 2) % 10
            else -> 25 + (i * 3) % 15
        }
        val shots = if (!isGk && !isDef) 1 + (i % 3) else 0
        val tackles = if (!isGk) 2 + (i % 4) else 0
        return PlayerPerformance(
            playerId = p.id,
            rating = Math.round(rating * 10.0) / 10.0,
            passes = passes,
            passAccuracy = 82 + (i * 2) % 10,
            shots = shots,
            shotAccuracy = if (shots > 0) 65 else 0,
            saves = if (isGk) maxOf(0, shotsAgainst - ga) else 0,
            penaltySaves = if (isGk && ap > 0) maxOf(0, 5 - ap) else 0,
            tackles = tackles,
            tacklePercentage = if (tackles > 0) 75 else 0,
            interceptions = if (!isGk) 1 + (i % 3) else 0,
            cleanSheet = (isGk || isDef) && ga == 0
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Payload parsing
    // ---------------------------------------------------------------------------------------------

    class ParsedPerformances(val performances: List<PlayerPerformance>, val usedDefaults: Boolean)

    /**
     * [exact] = the numbers come from the game's own export: a missing or zero figure is a real zero (or unknown)
     * and is shown as it is. Otherwise gaps are filled with typical values and [ParsedPerformances.usedDefaults] is set.
     */
    fun parseMatchPerformances(array: JSONArray?, players: List<Player>, exact: Boolean = false): ParsedPerformances {
        if (array == null) return ParsedPerformances(emptyList(), false)
        val list = mutableListOf<PlayerPerformance>()
        var usedDefaults = false
        for (i in 0 until array.length()) {
            val p = array.optJSONObject(i) ?: continue
            val name = p.optString("playerName")
            val pid = p.optString("playerId")
            val matched = findSquadPlayer(players, pid, name)
            val mappedId = matched?.id ?: pid.ifBlank { name }
            val pos = matched?.primaryPosition?.uppercase() ?: "CM"
            val isGk = pos == "GK"
            val isDef = pos in DEFENCE
            val isMid = pos in MIDFIELD

            val rawPasses = p.optInt("passes", 0)
            val defaultPasses = when {
                isMid -> 48 + (i * 3) % 20
                isDef -> 38 + (i * 2) % 15
                isGk -> 22 + (i * 2) % 8
                else -> 26 + (i * 3) % 12
            }
            if (!exact && rawPasses <= 0) usedDefaults = true
            val passes = if (exact || rawPasses > 0) rawPasses else defaultPasses

            val passAccuracy = if (exact) p.optInt("passAccuracy", 0)
            else if (p.has("passAccuracy") && p.optInt("passAccuracy") > 0) p.optInt("passAccuracy") else {
                usedDefaults = true; 85
            }

            val defaultShots = if (!isGk && !isDef) 1 + (i % 3) else 0
            val shots = if (exact) p.optInt("shots", 0)
            else if (p.has("shots")) p.optInt("shots", 0) else { usedDefaults = true; defaultShots }
            val shotAccuracy = if (shots > 0) {
                if (exact) p.optInt("shotAccuracy", 0)
                else if (p.has("shotAccuracy") && p.optInt("shotAccuracy") > 0) p.optInt("shotAccuracy") else { usedDefaults = true; 65 }
            } else 0

            val defaultTackles = if (!isGk) 2 + (i % 3) else 0
            val tackles = if (exact) p.optInt("tackles", 0)
            else if (p.has("tackles")) p.optInt("tackles", 0) else { usedDefaults = true; defaultTackles }
            val tacklePct = if (tackles > 0) {
                if (exact) p.optInt("tacklePercentage", 0)
                else if (p.has("tacklePercentage") && p.optInt("tacklePercentage") > 0) p.optInt("tacklePercentage") else { usedDefaults = true; 75 }
            } else 0
            val interceptions = if (exact) p.optInt("interceptions", 0)
            else if (p.has("interceptions")) p.optInt("interceptions") else {
                usedDefaults = true; if (!isGk) 1 + (i % 3) else 0
            }
            val rating = if (p.has("rating")) p.optDouble("rating", 7.0)
            else if (exact) 0.0 else { usedDefaults = true; 7.0 }

            list.add(
                PlayerPerformance(
                    playerId = mappedId,
                    rating = rating,
                    goals = p.optInt("goals", 0),
                    assists = p.optInt("assists", 0),
                    passes = passes,
                    passAccuracy = passAccuracy,
                    shots = shots,
                    shotAccuracy = shotAccuracy,
                    saves = p.optInt("saves", 0),
                    penaltySaves = p.optInt("penaltySaves", 0),
                    tackles = tackles,
                    tacklePercentage = tacklePct,
                    interceptions = interceptions,
                    subInMinute = if (p.has("subInMinute")) p.optInt("subInMinute") else null,
                    subOutMinute = if (p.has("subOutMinute")) p.optInt("subOutMinute") else null,
                    yellowCard = p.optBoolean("yellowCard", false),
                    redCard = p.optBoolean("redCard", false),
                    cleanSheet = p.optBoolean("cleanSheet", false),
                    isMotm = p.optBoolean("isMotm", false),
                    playerName = name.takeIf { it.isNotBlank() },
                    position = if (p.has("position") && !p.isNull("position")) p.optString("position").trim().takeIf { it.isNotBlank() } else null
                )
            )
        }
        return ParsedPerformances(list, usedDefaults)
    }

    fun parseGoalDetails(array: JSONArray?, players: List<Player>): List<GoalDetail> {
        if (array == null) return emptyList()
        val list = mutableListOf<GoalDetail>()
        for (i in 0 until array.length()) {
            val p = array.optJSONObject(i) ?: continue
            val name = p.optString("playerName")
            val assister = p.optString("assisterName")
            val scorerId = findSquadPlayer(players, p.optString("playerId"), name)?.id ?: name
            val assisterId = if (assister.isNotBlank()) {
                findSquadPlayer(players, p.optString("assisterId"), assister)?.id ?: assister
            } else null
            val isOpponent = p.optBoolean("isOpponent")
            // The scripts write "<club> Goal" when they do not know who scored for the other side: that is no name.
            val unknownScorer = name.isBlank() ||
                (isOpponent && (name.endsWith(" Goal") || isPlaceholderPlayerName(name)))
            list.add(
                GoalDetail(
                    scorerId = if (isOpponent) null else scorerId,
                    assisterId = assisterId,
                    minute = if (p.has("minute")) p.optInt("minute") else null,
                    scorerName = if (unknownScorer) null else name,
                    assisterName = assister.takeIf { it.isNotBlank() },
                    minuteEstimated = p.optBoolean("minuteEstimated", false)
                )
            )
        }
        return list
    }

    fun parseSubstitutions(array: JSONArray?, players: List<Player>): List<Substitution> {
        if (array == null) return emptyList()
        val list = mutableListOf<Substitution>()
        for (i in 0 until array.length()) {
            val s = array.optJSONObject(i) ?: continue
            val outName = s.optString("playerOutName")
            val inName = s.optString("playerInName")
            val outId = findSquadPlayer(players, s.optString("playerOutId"), outName)?.id ?: s.optString("playerOutId", outName)
            val inId = findSquadPlayer(players, s.optString("playerInId"), inName)?.id ?: s.optString("playerInId", inName)
            list.add(
                Substitution(
                    minute = s.optInt("minute", 65),
                    playerOutId = outId,
                    playerInId = inId,
                    playerOutName = outName.takeIf { it.isNotBlank() },
                    playerInName = inName.takeIf { it.isNotBlank() }
                )
            )
        }
        return list
    }

    /**
     * The squad player a payload row is about: by the app's id, then by the game's id (the game can leave a regen's or an
     * academy graduate's name out, and then only the id says who he is), then by any of his names, accents and case
     * ignored.
     */
    fun findSquadPlayer(players: List<Player>, id: String?, name: String?): Player? {
        val raw = id?.trim().orEmpty()
        if (raw.isNotEmpty()) players.firstOrNull { it.id == raw }?.let { return it }
        val gameId = raw.toIntOrNull()
        if (gameId != null && gameId > 0) players.firstOrNull { it.gamePlayerId == gameId }?.let { return it }
        if (name.isNullOrBlank()) return null
        val n = SquadReport.normalise(name)
        if (n.isEmpty()) return null
        return players.firstOrNull { SquadReport.normalise(it.displayName) == n || SquadReport.normalise(it.name) == n }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** Removes the 255 "not set" markers EA FC uses (255 penalties, 255 shots...) from a message. */
    fun sanitizeEventMessage(text: String): String {
        if (text.isBlank()) return text
        return text
            .replace(Regex("\\s*\\(Pens:\\s*255-\\d+\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Pens:\\s*\\d+-255\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Pens:\\s*0-0\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b255\\s*penalties\\b", RegexOption.IGNORE_CASE), "0 penalties")
            .replace(Regex("\\b255\\b"), "0")
    }

    /** isoDate + days as yyyy-MM-dd, or null when isoDate cannot be read. (No java.time: minSdk 24.) */
    internal fun plusDays(isoDate: String, days: Int): String? = try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        format.isLenient = false
        val date = format.parse(isoDate)
        if (date == null) null else {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date; add(Calendar.DAY_OF_YEAR, days) }
            format.format(cal.time)
        }
    } catch (e: Exception) {
        null
    }
}
