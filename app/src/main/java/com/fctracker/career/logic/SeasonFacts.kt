package com.fctracker.career.logic

import com.fctracker.career.MatchResult
import com.fctracker.career.Player
import com.fctracker.career.PlayerSeasonStat

/** Won, drawn, lost and goals for one set of matches (home, away, a whole season). */
data class Split(val won: Int = 0, val drawn: Int = 0, val lost: Int = 0, val goalsFor: Int = 0, val goalsAgainst: Int = 0) {
    val played get() = won + drawn + lost
    val points get() = won * 3 + drawn
    val goalDifference get() = goalsFor - goalsAgainst
}

/** A run of matches (unbeaten, winning) with the dates it started and ended. */
data class Run(val length: Int, val from: String, val to: String)

/** One player's season, from every source the app has, without counting a match twice. */
data class PlayerTotals(
    val player: Player,
    val appearances: Int,
    val starts: Int,
    val subApps: Int,
    val goals: Int,
    val assists: Int,
    val motm: Int,
    val cleanSheets: Int,
    val yellowCards: Int,
    val redCards: Int,
    val averageRating: Double,
    /** The last five ratings, oldest first. */
    val lastRatings: List<Double>
)

/**
 * Season figures worked out from the matches: home and away, records (best win, longest unbeaten run), the league
 * position matchday by matchday, and each player's totals.
 */
object SeasonFacts {

    fun split(matches: List<MatchResult>): Split {
        var w = 0; var d = 0; var l = 0; var gf = 0; var ga = 0
        for (m in matches) {
            when (m.resultBadge) { "W" -> w++; "L" -> l++; else -> d++ }
            gf += m.goalsFor; ga += m.goalsAgainst
        }
        return Split(w, d, l, gf, ga)
    }

    fun home(matches: List<MatchResult>) = split(matches.filter { it.isHome })
    fun away(matches: List<MatchResult>) = split(matches.filter { !it.isHome })

    private fun chronological(matches: List<MatchResult>) = matches.withIndex()
        .sortedWith(compareBy<IndexedValue<MatchResult>> { it.value.matchDate }.thenByDescending { it.index })
        .map { it.value }

    fun bestWin(matches: List<MatchResult>): MatchResult? = matches.filter { it.goalsFor > it.goalsAgainst }
        .maxWithOrNull(compareBy<MatchResult> { it.goalsFor - it.goalsAgainst }.thenBy { it.goalsFor })

    fun heaviestDefeat(matches: List<MatchResult>): MatchResult? = matches.filter { it.goalsFor < it.goalsAgainst }
        .maxWithOrNull(compareBy<MatchResult> { it.goalsAgainst - it.goalsFor }.thenBy { it.goalsAgainst })

    private fun longest(matches: List<MatchResult>, keep: (MatchResult) -> Boolean): Run? {
        var best: Run? = null
        var len = 0
        var start = ""
        for (m in chronological(matches)) {
            if (keep(m)) {
                if (len == 0) start = m.matchDate
                len++
                if (best == null || len > best.length) best = Run(len, start, m.matchDate)
            } else len = 0
        }
        return best
    }

    fun longestUnbeaten(matches: List<MatchResult>) = longest(matches) { it.resultBadge != "L" }
    fun longestWinning(matches: List<MatchResult>) = longest(matches) { it.resultBadge == "W" }
    fun longestHomeWinning(matches: List<MatchResult>) = longest(matches.filter { it.isHome }) { it.resultBadge == "W" }
    fun cleanSheets(matches: List<MatchResult>) = matches.count { it.goalsAgainst == 0 }

    /** The current streak, newest backwards: "3 wins in a row", "unbeaten in 5". */
    fun currentWins(matches: List<MatchResult>): Int = chronological(matches).asReversed().takeWhile { it.resultBadge == "W" }.size
    fun currentUnbeaten(matches: List<MatchResult>): Int = chronological(matches).asReversed().takeWhile { it.resultBadge != "L" }.size

    /** The league position after each league match, oldest first (0 positions, i.e. unknown, left out). */
    fun leaguePositions(matches: List<MatchResult>, isLeague: (String) -> Boolean): List<Int> =
        chronological(matches.filter { isLeague(it.competition) }).map { it.leaguePosition }.filter { it > 0 }

    /** Results oldest first. */
    fun form(matches: List<MatchResult>, count: Int): List<String> = chronological(matches).takeLast(count).map { it.resultBadge }

    fun goalsByCompetition(matches: List<MatchResult>): List<Pair<String, Int>> =
        matches.groupBy { it.competition }.map { (c, ms) -> c to ms.sumOf { it.goalsFor } }.sortedByDescending { it.second }

    // -- players --------------------------------------------------------------------------------------------------

    private fun matchesPlayer(perfId: String, perfName: String?, p: Player): Boolean =
        perfId == p.id || (!perfName.isNullOrBlank() && (perfName.equals(p.name, true) || perfName.equals(p.displayName, true)))

    /**
     * A player's totals. Three sources can know about the same match: the match logs (lineups), the counters on the
     * player card, and the game's own season statistics. The highest count wins for each figure, so nothing is
     * counted twice and a match the person did not log still counts when the game knows it.
     */
    fun totals(p: Player, matches: List<MatchResult>, gameStats: List<PlayerSeasonStat> = emptyList()): PlayerTotals {
        var apps = 0; var starts = 0; var subs = 0; var goals = 0; var assists = 0; var motm = 0; var cs = 0; var yc = 0; var rc = 0
        val ratings = mutableListOf<Double>()
        for (m in chronological(matches)) {
            val perf = m.playerPerformances.firstOrNull { matchesPlayer(it.playerId, it.playerName, p) } ?: continue
            apps++
            if (perf.subInMinute != null) subs++ else starts++
            goals += perf.goals
            assists += perf.assists
            if (perf.isMotm || (m.motmName.isNotBlank() && !m.motmIsOpponent && (m.motmName.equals(p.name, true) || m.motmName.equals(p.displayName, true)))) motm++
            if (perf.cleanSheet) cs++
            if (perf.yellowCard) yc++
            if (perf.redCard) rc++
            if (perf.rating > 0) ratings += perf.rating
        }
        val game = gameStats.filter { (it.gamePlayerId != null && it.gamePlayerId == p.gamePlayerId) || it.playerName.equals(p.name, true) || it.playerName.equals(p.displayName, true) }
        val gApps = game.sumOf { it.appearances }
        val gGoals = game.sumOf { it.goals }
        val gAssists = game.sumOf { it.assists }
        val gCs = game.sumOf { it.cleanSheets }
        val gYc = game.sumOf { it.yellowCards }
        val gRc = game.sumOf { it.redCards }
        val gMotm = game.sumOf { it.motm }
        val gRated = game.filter { it.averageRating > 0 && it.appearances > 0 }
        val gAvg = if (gRated.isNotEmpty()) gRated.sumOf { it.averageRating * it.appearances } / gRated.sumOf { it.appearances } else 0.0
        val avg = when {
            ratings.isNotEmpty() -> ratings.average()
            p.averageRating > 0 -> p.averageRating
            else -> gAvg
        }
        return PlayerTotals(
            player = p,
            appearances = maxOf(apps, p.matchesPlayed, gApps),
            starts = starts,
            subApps = subs,
            goals = maxOf(goals, p.goals, gGoals),
            assists = maxOf(assists, p.assists, gAssists),
            motm = maxOf(motm, gMotm),
            cleanSheets = maxOf(cs, gCs),
            yellowCards = maxOf(yc, gYc),
            redCards = maxOf(rc, gRc),
            averageRating = avg,
            lastRatings = ratings.takeLast(5)
        )
    }

    fun allTotals(players: List<Player>, matches: List<MatchResult>, gameStats: List<PlayerSeasonStat> = emptyList()): List<PlayerTotals> =
        players.map { totals(it, matches, gameStats) }

    /** The best average rating among players with at least a third of the matches (and at least 3). */
    fun playerOfSeason(totals: List<PlayerTotals>, matchesPlayed: Int): PlayerTotals? {
        val min = maxOf(3, matchesPlayed / 3)
        return totals.filter { it.appearances >= min && it.averageRating > 0 }.maxByOrNull { it.averageRating }
            ?: totals.filter { it.averageRating > 0 }.maxByOrNull { it.averageRating }
    }
}
