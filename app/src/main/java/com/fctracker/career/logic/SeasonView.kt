package com.fctracker.career.logic

import com.fctracker.career.AppState
import com.fctracker.career.AwardEntry
import com.fctracker.career.ClubCoefficient
import com.fctracker.career.CompetitionLeaders
import com.fctracker.career.Fixture
import com.fctracker.career.HistoricalSeason
import com.fctracker.career.LeagueTableData
import com.fctracker.career.MatchResult
import com.fctracker.career.Milestone
import com.fctracker.career.NationCoefficient
import com.fctracker.career.Objective
import com.fctracker.career.Player
import com.fctracker.career.PlayerSeasonStat
import com.fctracker.career.TransferLog

/**
 * The season a page shows: the current one, or an archived one the person picked on Home. Every page reads its data
 * through this, so switching Home to Archived changes every page at once, and [isArchived] tells a page to hide its
 * Add, Edit and Delete buttons (an archived season is read-only).
 */
data class SeasonView(
    val isArchived: Boolean,
    /** Start year of the season shown (2025 for 2025/26). */
    val season: Int,
    val leagueName: String,
    val matches: List<MatchResult>,
    val players: List<Player>,
    val academy: List<Player>,
    val transfers: List<TransferLog>,
    val objectives: List<Objective>,
    val leagueTables: List<LeagueTableData>,
    val leaders: List<CompetitionLeaders>,
    val squadStats: List<PlayerSeasonStat>,
    val awards: List<AwardEntry>,
    val coefficients: List<ClubCoefficient>,
    val nationCoefficients: List<NationCoefficient>,
    val milestones: List<Milestone>,
    val fixtures: List<Fixture>,
    val transferBudget: Double,
    val wageBudgetK: Double,
    /** The archived season itself (null for the current one). */
    val archived: HistoricalSeason?
) {
    val label: String get() = seasonLabel(season)

    /** The career's own league table, when one is known. */
    val ownTable: LeagueTableData? get() = leagueTables.firstOrNull { it.league.equals(leagueName, ignoreCase = true) }
        ?: leagueTables.firstOrNull { t -> t.rows.any { it.isUser } }

    companion object {
        fun seasonLabel(startYear: Int): String = "$startYear/${((startYear + 1) % 100).toString().padStart(2, '0')}"

        fun of(state: AppState): SeasonView {
            val archived = state.viewingSeasonYear?.let { y -> state.historicalSeasons.firstOrNull { it.year == y } }
            if (archived == null) {
                return SeasonView(
                    isArchived = false,
                    season = state.startYear,
                    leagueName = state.leagueName,
                    matches = state.recentMatches,
                    players = state.players,
                    academy = state.academyPlayers,
                    transfers = state.transfers,
                    objectives = state.objectives,
                    leagueTables = state.leagueTables,
                    leaders = state.leaders,
                    squadStats = state.squadStats,
                    awards = state.awards.filter { it.season == 0 || it.season == state.startYear },
                    coefficients = state.coefficients,
                    nationCoefficients = state.nationCoefficients,
                    milestones = state.milestones.filter { it.season == 0 || it.season == state.startYear },
                    fixtures = state.fixtures,
                    transferBudget = state.transferBudget,
                    wageBudgetK = state.wageBudgetK,
                    archived = null
                )
            }
            return SeasonView(
                isArchived = true,
                season = archived.year,
                leagueName = archived.leagueName.ifBlank { state.leagueName },
                matches = archived.matches,
                players = archived.players,
                academy = archived.academyPlayers,
                transfers = archived.transfers,
                objectives = archived.objectives,
                leagueTables = archived.leagueTables,
                leaders = archived.leaders,
                squadStats = archived.squadStats,
                awards = archived.awards,
                coefficients = archived.coefficients,
                nationCoefficients = archived.nationCoefficients,
                milestones = archived.milestones,
                fixtures = emptyList(),
                transferBudget = archived.transferBudget,
                wageBudgetK = archived.wageBudgetK,
                archived = archived
            )
        }
    }
}
