package com.fctracker.career.logic

import com.fctracker.career.AppState
import com.fctracker.career.MatchResult
import com.fctracker.career.Milestone
import com.fctracker.career.Player
import com.fctracker.career.isIncomingDeal

/**
 * Notices milestones: 50, 100, 150... appearances for the club, 25, 50, 100... goals or assists, a jump of three or
 * more in a player's rating (or reaching his potential), and club records (wins in a row, unbeaten runs, the biggest
 * win, the record signing). Each milestone has a stable key, so it is announced once.
 */
object Milestones {

    fun appearanceSteps(n: Int): List<Int> = (50..n step 50).toList()
    fun goalSteps(n: Int): List<Int> = listOf(25, 50).filter { it <= n } + (100..n step 50).toList()

    data class CareerCount(val apps: Int, val goals: Int, val assists: Int)

    /** Appearances, goals and assists for the club over every season, including the current one. */
    fun careerCounts(state: AppState, p: Player): CareerCount {
        var apps = 0; var goals = 0; var assists = 0
        for (s in state.historicalSeasons) {
            val past = s.players.firstOrNull { it.id == p.id } ?: continue
            val t = SeasonFacts.totals(past, s.matches, s.squadStats)
            apps += t.appearances; goals += t.goals; assists += t.assists
        }
        val now = SeasonFacts.totals(p, state.recentMatches, state.squadStats)
        return CareerCount(apps + now.appearances, goals + now.goals, assists + now.assists)
    }

    private fun lastMatchWith(p: Player, matches: List<MatchResult>): MatchResult? =
        matches.filter { m -> m.playerPerformances.any { it.playerId == p.id || it.playerName.equals(p.name, true) } }.maxByOrNull { it.matchDate }

    /** New milestones (not yet in [AppState.milestones]); [today] is the game's date. */
    fun detect(state: AppState, today: String): List<Milestone> {
        if (state.viewingSeasonYear != null) return emptyList()
        val known = state.milestones.map { it.key }.toSet()
        val out = mutableListOf<Milestone>()
        val club = state.clubName
        val season = state.startYear
        fun add(m: Milestone) { if (m.key !in known && out.none { it.key == m.key }) out += m }

        for (p in state.players.filter { it.status.equals("Active", true) || it.status.equals("Loaned In", true) || it.status.isBlank() }) {
            val c = careerCounts(state, p)
            val last = lastMatchWith(p, state.recentMatches)
            val where = last?.let { " in the ${it.goalsFor}–${it.goalsAgainst} ${if (it.resultBadge == "W") "win" else if (it.resultBadge == "L") "defeat" else "draw"} ${if (it.isHome) "against" else "at"} ${it.opponentName}" } ?: ""
            appearanceSteps(c.apps).lastOrNull()?.let { n ->
                add(Milestone("apps:$n:${p.id}", "${p.displayName}: $n matches", "${ordinal(n)} appearance for $club$where.", "apps", p.id, p.displayName, last?.matchDate ?: today, season))
            }
            goalSteps(c.goals).lastOrNull()?.let { n ->
                add(Milestone("goals:$n:${p.id}", "${p.displayName}: $n goals", "His ${ordinal(n)} goal for the club.", "goals", p.id, p.displayName, last?.matchDate ?: today, season))
            }
            goalSteps(c.assists).lastOrNull()?.let { n ->
                add(Milestone("assists:$n:${p.id}", "${p.displayName}: $n assists", "His ${ordinal(n)} assist for the club.", "assists", p.id, p.displayName, last?.matchDate ?: today, season))
            }
            if (p.startingOverall > 0 && p.overall - p.startingOverall >= 3) {
                val gain = p.overall - p.startingOverall
                add(Milestone("growth:${p.overall}:${p.id}:$season", "${p.displayName} reached ${p.overall} OVR", "+$gain this season. Potential ${p.potential}.", "growth", p.id, p.displayName, today, season))
            }
            if (p.potential > 0 && p.overall >= p.potential && p.startingOverall in 1 until p.potential) {
                add(Milestone("potential:${p.id}:$season", "${p.displayName} reached his potential", "OVR ${p.overall}, his potential.", "growth", p.id, p.displayName, today, season))
            }
        }

        // Club records: compared with every earlier season of the career.
        val past = state.historicalSeasons.flatMap { it.matches }
        val now = state.recentMatches
        val bestPastWins = SeasonFacts.longestWinning(past)?.length ?: 0
        val currentWins = SeasonFacts.currentWins(now)
        if (currentWins >= 5 && currentWins > bestPastWins) {
            add(Milestone("record:wins:$season:$currentWins", "Club record: $currentWins wins in a row", "$club's longest winning run in your career.", "record", "", club, now.maxOfOrNull { it.matchDate } ?: today, season))
        }
        val bestPastUnbeaten = SeasonFacts.longestUnbeaten(past)?.length ?: 0
        val currentUnbeaten = SeasonFacts.currentUnbeaten(now)
        if (currentUnbeaten >= 10 && currentUnbeaten > bestPastUnbeaten && currentUnbeaten % 5 == 0) {
            add(Milestone("record:unbeaten:$season:$currentUnbeaten", "Club record: unbeaten in $currentUnbeaten", "$club's longest unbeaten run in your career.", "record", "", club, now.maxOfOrNull { it.matchDate } ?: today, season))
        }
        val pastBest = SeasonFacts.bestWin(past)
        val nowBest = SeasonFacts.bestWin(now)
        if (nowBest != null && nowBest.goalsFor - nowBest.goalsAgainst >= 4 &&
            (pastBest == null || nowBest.goalsFor - nowBest.goalsAgainst > pastBest.goalsFor - pastBest.goalsAgainst)
        ) {
            add(Milestone("record:win:${nowBest.id}", "Club record: biggest win", "${nowBest.goalsFor}–${nowBest.goalsAgainst} ${if (nowBest.isHome) "against" else "at"} ${nowBest.opponentName}.", "record", "", club, nowBest.matchDate, season))
        }
        val pastFee = state.historicalSeasons.flatMap { it.transfers }.filter { it.isIncomingDeal() }.maxOfOrNull { it.feeM } ?: 0.0
        val bigNow = state.transfers.filter { it.isIncomingDeal() && it.feeM > 0 }.maxByOrNull { it.feeM }
        if (bigNow != null && bigNow.feeM > pastFee && state.historicalSeasons.isNotEmpty()) {
            add(Milestone("record:signing:${bigNow.id}", "Club record signing", "${bigNow.playerName} for ${state.currencySymbol}${bigNow.feeM}M.", "record", "", bigNow.playerName, bigNow.date.ifBlank { today }, season))
        }
        return out
    }

    /** Whether a milestone of this kind raises an alert (the switches on the Milestones page). */
    fun alerts(state: AppState, m: Milestone): Boolean = when (m.kind) {
        "apps" -> state.milestonePrefs.appearances
        "goals", "assists" -> state.milestonePrefs.goals
        "growth" -> state.milestonePrefs.growth
        "record" -> state.milestonePrefs.records
        else -> true
    }

    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }
}
