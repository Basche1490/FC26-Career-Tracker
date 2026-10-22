package com.fctracker.career.telemetry

import com.fctracker.career.AppState
import com.fctracker.career.AwardEntry
import com.fctracker.career.ClubCoefficient
import com.fctracker.career.CompetitionLeaders
import com.fctracker.career.Fixture
import com.fctracker.career.LeagueTableData
import com.fctracker.career.NationCoefficient
import com.fctracker.career.Player
import com.fctracker.career.PlayerSeasonStat
import com.fctracker.career.Source
import com.fctracker.career.StatLeader
import com.fctracker.career.TableRow
import com.fctracker.career.TeamSheet
import com.fctracker.career.WorldTopScorer
import com.fctracker.career.WorldWinner
import com.fctracker.career.logic.Dates
import com.fctracker.career.logic.GameProfile
import com.fctracker.career.logic.PlayerCards
import com.fctracker.career.logic.PlayerLookup
import com.fctracker.career.logic.WorldWinners
import com.fctracker.career.logic.LeagueTables
import com.fctracker.career.logic.Trophies
import org.json.JSONArray
import org.json.JSONObject

/**
 * The "career" report from the capture script on the PC: fixtures, every league table, the league leaders, the
 * squad's season statistics, injuries, suspensions, squad roles, shirt numbers, wages, form and attributes, awards, the
 * holders of every competition (Around the World), the European club ranking, the club's facts, the team sheet and the
 * trophies the club won.
 *
 * Any part may be missing (the script sends a part only when it changed); a missing part leaves the app's data as
 * it is. Values the person typed are never overwritten: an injury whose "Follow the game" switch is off keeps its
 * date, and fixtures, winners and awards added by hand stay.
 */
object CareerReport {

    data class Result(val state: AppState, val changed: Boolean, val newInjuries: List<String>, val newTrophies: List<String> = emptyList())

    private val FORMS = listOf("Excellent", "Good", "Okay", "Poor")

    /** The season a report's date belongs to for its tables and statistics: the game starts a new season in July. */
    fun reportSeason(careerDate: String?): Int? {
        val d = careerDate?.takeIf { Dates.isDate(it) } ?: return null
        val y = d.take(4).toIntOrNull() ?: return null
        val m = d.substring(5, 7).toIntOrNull() ?: return null
        return if (m >= 7) y else y - 1
    }

    private fun JSONObject.str(key: String): String = if (has(key) && !isNull(key)) optString(key).trim() else ""
    private fun JSONObject.int(key: String): Int = if (has(key) && !isNull(key)) optInt(key, 0) else 0
    private fun JSONObject.dbl(key: String): Double = if (has(key) && !isNull(key)) optDouble(key, 0.0).let { if (it.isNaN()) 0.0 else it } else 0.0
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun sameName(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        return SquadReport.normalise(a) == SquadReport.normalise(b)
    }

    private fun findPlayer(players: List<Player>, id: Int, name: String): Int {
        if (id > 0) players.indexOfFirst { it.gamePlayerId == id }.takeIf { it >= 0 }?.let { return it }
        return players.indexOfFirst { sameName(it.name, name) || sameName(it.displayName, name) }
    }

    /**
     * @param lookup the built-in player database: players are named by their short name ("Pedri") and a card's nation is
     * taken from the database card of the same game id
     */
    fun apply(state: AppState, data: JSONObject, lookup: PlayerLookup = PlayerLookup.NONE): Result {
        var s = state
        var changed = false
        val newInjuries = mutableListOf<String>()
        val careerDate = data.str("careerDate").takeIf { Dates.isDate(it) } ?: s.careerDate
        if (careerDate != null && careerDate != s.careerDate && Dates.isDate(careerDate)) {
            s = s.copy(careerDate = careerDate)
            changed = true
        }
        // A report from the game's previous season (the season was already ended in the app, the game has not moved on
        // yet): its tables and statistics belong to the archived season, so they do not replace the new season's.
        val lastSeasonsData = reportSeason(careerDate)?.let { it < s.startYear } == true

        // -- fixtures: the game's list replaces its earlier one; fixtures added by hand stay -------------------------
        data.optJSONArray("fixtures")?.let { arr ->
            val incoming = arr.objects().mapNotNull { o ->
                val date = o.str("date")
                val opp = o.str("opponent")
                if (!Dates.isDate(date) || opp.isEmpty()) null
                else Fixture(
                    id = "game-" + o.str("key").ifBlank { "$date-$opp" },
                    date = date, kickOff = o.str("time"), competition = o.str("competition"), round = o.str("round"),
                    opponent = opp, isHome = o.optBoolean("home", true), source = Source.GAME, gameKey = o.str("key")
                )
            }.distinctBy { it.id }
            val manual = s.fixtures.filter { it.source != Source.GAME }
            val merged = (manual + incoming).sortedBy { it.date }
            if (merged != s.fixtures) { s = s.copy(fixtures = merged); changed = true }
        }

        // -- league tables ---------------------------------------------------------------------------------------------
        data.optJSONArray("tables")?.takeUnless { lastSeasonsData }?.let { arr ->
            val incoming = arr.objects().mapNotNull { o ->
                val league = o.str("league")
                val rows = o.optJSONArray("rows").objects().mapNotNull { r ->
                    val club = r.str("club")
                    if (club.isEmpty()) null else TableRow(
                        club = club, played = r.int("p"), won = r.int("w"), drawn = r.int("d"), lost = r.int("l"),
                        goalsFor = r.int("gf"), goalsAgainst = r.int("ga"), points = r.int("pts"), form = r.str("form").uppercase().filter { it in "WDL" },
                        isUser = r.optBoolean("user", false) || club.equals(s.clubName, ignoreCase = true)
                    )
                }
                if (league.isEmpty() || rows.isEmpty()) null else LeagueTableData(league, o.str("country"), rows, Source.GAME, careerDate.orEmpty())
            }
            if (incoming.isNotEmpty()) {
                val names = incoming.map { it.league.lowercase() }.toSet()
                val kept = s.leagueTables.filter { it.league.lowercase() !in names }
                val merged = incoming + kept
                if (merged != s.leagueTables) { s = s.copy(leagueTables = merged); changed = true }
                // The club went up or down: the game's table with the club in it names the career's league now.
                val userInOld = incoming.any { t -> t.league.equals(s.leagueName, true) && t.rows.any { it.isUser } }
                val moved = incoming.firstOrNull { t -> LeagueTables.isDomesticLeague(t.league) && !t.league.equals(s.leagueName, true) && t.rows.any { it.isUser } }
                if (!userInOld && moved != null && s.leagueName.isNotBlank()) { s = s.copy(leagueName = moved.league); changed = true }
            }
        }

        // -- league leaders --------------------------------------------------------------------------------------------
        data.optJSONArray("leaders")?.takeUnless { lastSeasonsData }?.let { arr ->
            fun list(a: JSONArray?) = a.objects().mapNotNull { o ->
                val p = o.str("player").let { if (it.isEmpty()) it else lookup.shortName(it) }
                if (p.isEmpty()) null else StatLeader(p, o.str("club"), o.int("value"), o.str("club").equals(s.clubName, true) || o.optBoolean("user", false))
            }
            val incoming = arr.objects().map { o ->
                CompetitionLeaders(o.str("competition"), list(o.optJSONArray("goals")), list(o.optJSONArray("assists")), list(o.optJSONArray("cleanSheets")), Source.GAME, careerDate.orEmpty())
            }.filter { it.competition.isNotEmpty() }
            if (incoming != s.leaders) { s = s.copy(leaders = incoming); changed = true }
        }

        // -- the squad's season statistics (every match, logged or not) ------------------------------------------------
        data.optJSONArray("stats")?.takeUnless { lastSeasonsData }?.let { arr ->
            val incoming = arr.objects().map { o ->
                PlayerSeasonStat(
                    playerName = o.str("name").let { if (it.isEmpty()) it else lookup.shortName(it) }, gamePlayerId = o.int("playerId").takeIf { it > 0 }, competition = o.str("competition"),
                    appearances = o.int("apps"), goals = o.int("goals"), assists = o.int("assists"), cleanSheets = o.int("cleanSheets"),
                    yellowCards = o.int("yellow"), redCards = o.int("red"), averageRating = o.dbl("rating"), motm = o.int("motm")
                )
            }.filter { it.playerName.isNotEmpty() || it.gamePlayerId != null }
            if (incoming != s.squadStats) { s = s.copy(squadStats = incoming); changed = true }
        }

        // -- players: injuries, suspensions, roles, numbers, wages, attributes -----------------------------------------
        data.optJSONArray("players")?.let { arr ->
            // The capture script says whether it could read the game's injuries and suspensions: when it could not, a
            // missing injury or suspension means "not known", not "fit again".
            val injuriesKnown = data.optBoolean("injuriesKnown", true)
            val suspensionsKnown = data.optBoolean("suspensionsKnown", true)
            val players = s.players.toMutableList()
            val reported = mutableSetOf<Int>()
            // nation names by the game's id, learned from players the database knows (it names their nation)
            val nations = mutableMapOf<Int, String>()
            for (o in arr.objects()) {
                val id = o.int("playerId")
                val nation = o.optJSONObject("profile")?.let { GameProfile.parse(it) }?.nation ?: continue
                val known = if (id > 0) lookup.byGameId(id, s.startYear)?.nationality else null
                if (!known.isNullOrBlank() && !known.equals("Unknown", true)) nations.putIfAbsent(nation, known)
            }
            for (o in arr.objects()) {
                val idx = findPlayer(players, o.int("playerId"), o.str("name"))
                if (idx < 0) continue
                reported += idx
                var p = players[idx]
                val before = p
                if (o.int("playerId") > 0 && p.gamePlayerId == null) p = p.copy(gamePlayerId = o.int("playerId"))
                val dbCard = p.gamePlayerId?.let { lookup.byGameId(it, s.startYear) }
                // the short name the game shows, when nobody named him otherwise
                if (p.nickname.isNullOrBlank() && p.customDisplayName.isNullOrBlank()) {
                    val short = dbCard?.nickname?.takeIf { it.isNotBlank() } ?: lookup.shortName(p.name).takeIf { it != p.name }
                    if (short != null && !short.equals(p.name, true)) p = p.copy(nickname = short)
                }
                // a card made with guessed details (born 1 January 1999, playing CM) takes the game's own
                GameProfile.parse(o.optJSONObject("profile"))?.let { g ->
                    val nation = dbCard?.nationality?.takeIf { it.isNotBlank() && !it.equals("Unknown", true) } ?: GameProfile.nationName(g.nation, nations)
                    p = PlayerCards.withProfile(p, g, nation, s.startYear)
                }
                if (dbCard != null) p = PlayerCards.withDatabase(p, dbCard)
                if (o.int("number") > 0) p = p.copy(number = o.int("number"))
                o.str("role").takeIf { it.isNotEmpty() }?.let { p = p.copy(squadRole = it) }
                if (o.has("wage") && o.int("wage") > 0) p = p.copy(wageK = o.int("wage"))
                if (o.has("bonus")) p = p.copy(bonus = o.str("bonus").ifBlank { null })
                if (o.int("overall") in 1..99) p = p.copy(overall = o.int("overall"))
                if (o.int("potential") in 1..99) p = p.copy(potential = o.int("potential"))
                o.str("form").takeIf { it in FORMS }?.let { p = p.copy(form = it) }
                o.optJSONObject("attributes")?.let { a ->
                    val map = a.keys().asSequence().associateWith { a.optInt(it, 0) }.filterValues { it in 1..99 }
                    if (map.isNotEmpty()) {
                        p = p.copy(attributes = map, seasonStartAttributes = p.seasonStartAttributes.ifEmpty { map })
                    }
                }
                // Suspension: a missing object means not suspended.
                val susp = o.optJSONObject("suspension")
                if (suspensionsKnown || susp != null) {
                    p = if (susp != null && susp.int("games") > 0) p.copy(suspendedGames = susp.int("games"), suspensionEnd = susp.str("end").ifBlank { null })
                    else p.copy(suspendedGames = 0, suspensionEnd = null)
                }
                // Injury from the game.
                val inj = o.optJSONObject("injury")
                if (inj != null && Dates.isDate(inj.str("back"))) {
                    val back = inj.str("back")
                    val start = inj.str("start").takeIf { Dates.isDate(it) }
                    val name = inj.str("name").ifBlank { "Injury" }
                    val stillOut = careerDate == null || back > careerDate
                    if (stillOut) {
                        val wasFit = !p.isInjured
                        p = if (p.isInjured && !p.injuryFollowGame) {
                            p.copy(injuryGameReturnDate = back)          // the person's own date stays
                        } else {
                            p.copy(isInjured = true, injuryName = name, injuryStartDate = start ?: p.injuryStartDate ?: careerDate,
                                injuryReturnDate = back, injuryGameReturnDate = back, injurySource = Source.GAME, injuryFollowGame = true)
                        }
                        if (wasFit) newInjuries += p.displayName
                    } else if (p.isInjured && p.injurySource == Source.GAME && p.injuryFollowGame) {
                        p = clearedInjury(p)
                    }
                } else if (injuriesKnown && p.isInjured && p.injurySource == Source.GAME && p.injuryFollowGame) {
                    p = clearedInjury(p)      // the game no longer has him injured
                }
                if (p != before) { players[idx] = p; changed = true }
            }
            s = s.copy(players = players, activeInjuries = players.count { it.isInjured })
        }

        // -- awards from the game (added once, by key) ------------------------------------------------------------------
        data.optJSONArray("awards")?.let { arr ->
            val existingKeys = s.awards.map { it.gameKey }.filter { it.isNotEmpty() }.toSet()
            val add = arr.objects().mapNotNull { o ->
                val title = o.str("title")
                val key = o.str("key").ifBlank { "${o.str("title")}|${o.str("recipient")}|${o.str("period")}" }
                if (title.isEmpty() || key in existingKeys) null
                else AwardEntry(title = title, recipient = o.str("recipient"), period = o.str("period"), kind = o.str("kind").ifBlank { "player" },
                    season = s.startYear, source = Source.GAME, gameKey = key)
            }.distinctBy { it.gameKey }
            if (add.isNotEmpty()) { s = s.copy(awards = s.awards + add); changed = true }
        }

        // -- Around the World: only in June and July, for the season that just finished ---------------------------------
        val worldSeason = WorldWinners.seasonForUpdate(careerDate)
        if (worldSeason != null) {
            data.optJSONArray("holders")?.let { arr ->
                val incoming = arr.objects().map { o ->
                    WorldWinner(region = o.str("region"), competition = o.str("competition"), winner = o.str("winner"), runnerUp = o.str("runnerUp"),
                        score = o.str("score"), penalties = o.str("pens"), detail = o.str("detail"), isLeague = o.optBoolean("league", false))
                }
                val merged = WorldWinners.merge(s.worldWinners, incoming, worldSeason)
                if (merged != s.worldWinners) { s = s.copy(worldWinners = merged); changed = true }
            }
            data.optJSONArray("topScorers")?.let { arr ->
                val incoming = arr.objects().map { o -> WorldTopScorer(region = o.str("region"), competition = o.str("competition"), player = o.str("player").let { if (it.isEmpty()) it else lookup.shortName(it) }, club = o.str("club"), goals = o.int("goals")) }
                val merged = WorldWinners.mergeScorers(s.worldTopScorers, incoming, worldSeason)
                if (merged != s.worldTopScorers) { s = s.copy(worldTopScorers = merged); changed = true }
            }
        }

        // -- European club ranking ---------------------------------------------------------------------------------------
        data.optJSONArray("coefficients")?.let { arr ->
            val incoming = arr.objects().mapNotNull { o ->
                val club = o.str("club")
                if (club.isEmpty()) null else ClubCoefficient(o.int("rank"), club, o.str("country"), o.dbl("season"), o.dbl("total"),
                    o.optBoolean("user", false) || club.equals(s.clubName, true))
            }.sortedBy { it.rank }
            if (incoming.isNotEmpty() && incoming != s.coefficients) { s = s.copy(coefficients = incoming); changed = true }
        }
        data.optJSONArray("nations")?.let { arr ->
            val incoming = arr.objects().mapNotNull { o -> o.str("country").takeIf { it.isNotEmpty() }?.let { NationCoefficient(it, o.dbl("points")) } }
            if (incoming.isNotEmpty() && incoming != s.nationCoefficients) { s = s.copy(nationCoefficients = incoming); changed = true }
        }

        // -- the club's facts (never the history text) -------------------------------------------------------------------
        data.optJSONObject("club")?.let { c ->
            val ci = s.clubInfo
            val worth = c.dbl("clubWorth")
            val updated = ci.copy(
                founded = c.str("founded").ifBlank { ci.founded },
                stadium = c.str("stadium").ifBlank { ci.stadium },
                capacity = c.int("capacity").takeIf { it > 0 } ?: ci.capacity,
                rival = c.str("rival").ifBlank { ci.rival },
                leagueTitles = if (c.has("leagueTitles")) c.int("leagueTitles") else ci.leagueTitles,
                domesticCups = if (c.has("domesticCups")) c.int("domesticCups") else ci.domesticCups,
                europeanTrophies = if (c.has("europeanTrophies")) c.int("europeanTrophies") else ci.europeanTrophies,
                clubWorth = if (worth > 0) "${s.currencySymbol}${if (worth >= 1000) "%.1fB".format(java.util.Locale.US, worth / 1000) else "${worth.toInt()}M"}" else ci.clubWorth,
                prestigeHome = c.int("prestigeHome").takeIf { it > 0 } ?: ci.prestigeHome,
                prestigeAbroad = c.int("prestigeAbroad").takeIf { it > 0 } ?: ci.prestigeAbroad,
                source = Source.GAME
            )
            if (updated != ci) { s = s.copy(clubInfo = updated); changed = true }
            if (c.has("transferBudget")) {
                val b = c.dbl("transferBudget")
                if (b >= 0 && kotlin.math.abs(b - s.transferBudget) > 0.0001) { s = s.copy(transferBudget = b); changed = true }
            }
        }

        // -- the team sheet picked in the game --------------------------------------------------------------------------
        data.optJSONObject("teamsheet")?.let { t ->
            fun ids(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.optInt(it, 0) }.filter { it > 0 }
            val pos = t.optJSONObject("positions")
            val sheet = TeamSheet(
                formation = t.str("formation"), starting = ids(t.optJSONArray("xi")), bench = ids(t.optJSONArray("bench")),
                positions = pos?.keys()?.asSequence()?.associateWith { pos.optString(it) } ?: emptyMap(), updated = careerDate.orEmpty()
            )
            if (sheet.starting.isNotEmpty() && sheet != s.gameTeamSheet) { s = s.copy(gameTeamSheet = sheet); changed = true }
        }

        // -- trophies the club won (a league finished on top, a cup final won): added once ---------------------------------
        var newTrophies = emptyList<String>()
        data.optJSONArray("trophies")?.let { arr ->
            val won = arr.objects().mapNotNull { o ->
                val competition = o.str("competition")
                val year = o.int("startYear")
                if (competition.isEmpty() || year < 1900) null else competition to year
            }
            val added = Trophies.addFromGame(s, won)
            if (added.names.isNotEmpty()) { s = added.state; changed = true; newTrophies = added.names }
        }

        return Result(s, changed, newInjuries, newTrophies)
    }

    fun clearedInjury(p: Player): Player = p.copy(
        isInjured = false, injuryName = null, injuryStartDate = null, injuryReturnDate = null, injuryGameReturnDate = null,
        injurySource = null, injuryFollowGame = true, injuryNote = null
    )
}
