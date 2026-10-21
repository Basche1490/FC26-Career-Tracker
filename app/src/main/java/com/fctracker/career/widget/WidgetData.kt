package com.fctracker.career.widget

import com.fctracker.career.AppState
import com.fctracker.career.logic.CareerOps
import com.fctracker.career.logic.Competitions
import com.fctracker.career.logic.Dates
import com.fctracker.career.logic.LeagueTables
import com.fctracker.career.logic.Milestones
import com.fctracker.career.logic.SeasonFacts

/** What the home-screen widgets show, worked out from the current season (never an archived one). */
data class WidgetData(
    val club: String,
    val league: String,
    val position: String,
    val positionLine: String,
    val points: Int,
    val played: Int,
    val nextTitle: String,
    val nextLine: String,
    val form: List<String>,
    val last: String
) {
    companion object {
        fun of(state: AppState): WidgetData {
            val isLeague = Competitions.isLeagueFn(state.leagueName)
            val league = state.recentMatches.filter { isLeague(it.competition) }
            val split = SeasonFacts.split(league)
            val table = state.leagueTables.firstOrNull { it.league.equals(state.leagueName, true) }?.let { LeagueTables.sorted(it.rows) }.orEmpty()
            val row = table.firstOrNull { it.isUser || it.club.equals(state.clubName, true) }
            val pos = table.indexOf(row).takeIf { it >= 0 }?.plus(1) ?: SeasonFacts.leaguePositions(state.recentMatches, isLeague).lastOrNull()
            val points = row?.points ?: split.points
            val played = row?.played ?: split.played
            val next = CareerOps.nextFixture(state)
            val last = state.recentMatches.maxByOrNull { it.matchDate }
            return WidgetData(
                club = state.clubName,
                league = state.leagueName.removePrefix("EFL "),
                position = pos?.let { Milestones.ordinal(it) } ?: "–",
                positionLine = "${pos?.let { Milestones.ordinal(it) } ?: "–"} · $points pts",
                points = points,
                played = played,
                nextTitle = next?.let { "Next: ${if (it.isHome) "vs" else "at"} ${it.opponent}" } ?: "No fixture yet",
                nextLine = next?.let { listOf(Dates.weekdayDayMonth(it.date), it.kickOff, it.round.ifBlank { it.competition }).filter { s -> s.isNotBlank() }.joinToString(" · ") } ?: "Add fixtures in Matches · Upcoming",
                form = SeasonFacts.form(state.recentMatches, 5),
                last = last?.let { "Last: ${it.resultBadge} ${it.goalsFor}–${it.goalsAgainst} ${it.opponentName}" } ?: "No match yet"
            )
        }
    }
}
