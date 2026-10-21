package com.fctracker.career.logic

import com.fctracker.career.AppState
import com.fctracker.career.AwardEntry
import com.fctracker.career.Fixture
import com.fctracker.career.HistoricalSeason
import com.fctracker.career.LeagueTableData
import com.fctracker.career.MatchResult
import com.fctracker.career.Player
import com.fctracker.career.PlayerNote
import com.fctracker.career.SeasonRating
import com.fctracker.career.Source
import com.fctracker.career.TableRow
import com.fctracker.career.WorldTopScorer
import com.fctracker.career.WorldWinner
import com.fctracker.career.telemetry.CareerReport
import com.fctracker.career.telemetry.ContractTime

/**
 * The changes the screens make to a career, as pure functions of the state (the view model runs them inside
 * StateFlow.update, so an edit never races a report arriving from the PC). Every one of them leaves an archived
 * season alone.
 */
object CareerOps {

    fun today(state: AppState): String = ContractTime.referenceDate(state) ?: Dates.phoneToday()

    // -- the end of a season ------------------------------------------------------------------------------------------

    /**
     * Archives the current season with everything its pages show, then starts the next one: ages and season figures
     * reset, each player's rating is saved for the season, the club history gets its line for the season.
     */
    fun endSeason(state: AppState): AppState {
        val isLeague = Competitions.isLeagueFn(state.leagueName)
        val own = state.leagueTables.firstOrNull { it.league.equals(state.leagueName, true) } ?: state.leagueTables.firstOrNull { t -> t.rows.any { it.isUser } }
        val tablePos = own?.let { t -> LeagueTables.sorted(t.rows).indexOfFirst { it.isUser || it.club.equals(state.clubName, true) }.takeIf { it >= 0 }?.plus(1) }
        val lastLeague = state.recentMatches.filter { isLeague(it.competition) && it.leaguePosition > 0 }.maxByOrNull { it.matchDate }?.leaguePosition
        val finalPosition = tablePos ?: lastLeague ?: 1
        val label = SeasonView.seasonLabel(state.startYear)
        val trophies = state.teamTrophies.map { it.name }.filter { it.contains(label) || it.contains("(${state.startYear})") || it.contains("${state.startYear}/") }
        val record = SeasonFacts.split(state.recentMatches.filter { isLeague(it.competition) })
        val completed = HistoricalSeason(
            year = state.startYear,
            leagueName = state.leagueName,
            finalPosition = finalPosition,
            matches = state.recentMatches,
            players = state.players,
            transfers = state.transfers,
            matchesPlayed = own?.rows?.firstOrNull { it.isUser }?.played ?: record.played,
            points = own?.rows?.firstOrNull { it.isUser }?.points ?: record.points,
            wins = record.won, draws = record.drawn, losses = record.lost, goalsFor = record.goalsFor, goalsAgainst = record.goalsAgainst,
            objectives = state.objectives,
            academyPlayers = state.academyPlayers,
            leagueTables = state.leagueTables,
            leaders = state.leaders,
            squadStats = state.squadStats,
            awards = state.awards.filter { it.season == 0 || it.season == state.startYear },
            coefficients = state.coefficients,
            nationCoefficients = state.nationCoefficients,
            milestones = state.milestones.filter { it.season == state.startYear },
            transferBudget = state.transferBudget,
            wageBudgetK = state.wageBudgetK,
            trophies = trophies
        )
        val next = state.startYear + 1
        val players = state.players.map { p ->
            val birthYear = p.birthDate.take(4).toIntOrNull() ?: (state.startYear - p.age)
            p.copy(
                matchesPlayed = 0, goals = 0, assists = 0, averageRating = 0.0,
                age = next - birthYear,
                ratingHistory = p.ratingHistory.filter { it.season != state.startYear } + SeasonRating(state.startYear, p.overall),
                startingOverall = p.overall,
                seasonStartAttributes = p.attributes.ifEmpty { p.seasonStartAttributes },
                suspendedGames = 0, suspensionEnd = null
            )
        }
        val academy = state.academyPlayers.map { p ->
            val birthYear = p.birthDate.take(4).toIntOrNull() ?: (state.startYear - p.age)
            p.copy(age = next - birthYear, startingOverall = p.overall, ratingHistory = p.ratingHistory + SeasonRating(state.startYear, p.overall))
        }
        val line = "$label: ${Milestones.ordinal(finalPosition)} in the ${state.leagueName}" + (if (trophies.isNotEmpty()) ", ${trophies.joinToString(", ")}" else "") + "."
        val info = state.clubInfo.copy(seasonLines = state.clubInfo.seasonLines.filterNot { it.startsWith("$label:") } + line)
        return state.copy(
            historicalSeasons = state.historicalSeasons.filter { it.year != state.startYear } + completed,
            recentMatches = emptyList(),
            transfers = emptyList(),
            objectives = state.objectives.filter { it.status == "In Progress" }.map { it },
            players = players,
            academyPlayers = academy,
            squadStats = emptyList(),
            leaders = emptyList(),
            awards = state.awards.filter { it.season != state.startYear && it.season != 0 },
            fixtures = state.fixtures.filter { it.date >= "$next-07-01" },
            leagueTables = state.leagueTables.map { t -> t.copy(rows = t.rows.map { TableRow(club = it.club, isUser = it.isUser) }, updated = "") },
            startYear = next,
            viewingSeasonYear = null,
            clubInfo = info
        )
    }

    /** The new season's clubs in the career's league (console: promoted and relegated clubs). */
    fun applyNewSeasonClubs(state: AppState, leaving: List<String>, promotedIn: List<String>, relegatedIn: List<String>): AppState {
        val own = state.leagueTables.firstOrNull { it.league.equals(state.leagueName, true) } ?: LeagueTableData(league = state.leagueName, rows = listOf(TableRow(club = state.clubName, isUser = true)))
        val next = LeagueTables.newSeasonTable(own, leaving, promotedIn, relegatedIn, state.clubName)
        return state.copy(leagueTables = listOf(next) + state.leagueTables.filterNot { it.league.equals(own.league, true) })
    }

    // -- league tables (console) --------------------------------------------------------------------------------------

    fun ownTable(state: AppState): LeagueTableData =
        state.leagueTables.firstOrNull { it.league.equals(state.leagueName, true) }
            ?: LeagueTableData(league = state.leagueName, country = state.country, rows = listOf(TableRow(club = state.clubName, isUser = true)))

    fun updateTable(state: AppState, table: LeagueTableData): AppState =
        state.copy(leagueTables = listOf(table) + state.leagueTables.filterNot { it.league.equals(table.league, true) })

    fun editRow(state: AppState, league: String, oldClub: String, row: TableRow): AppState {
        val t = state.leagueTables.firstOrNull { it.league.equals(league, true) } ?: ownTable(state)
        return updateTable(state, LeagueTables.replaceRow(t, oldClub, row))
    }

    // -- fixtures -------------------------------------------------------------------------------------------------------

    fun addFixture(state: AppState, f: Fixture): AppState = state.copy(fixtures = (state.fixtures + f.copy(source = Source.MANUAL)).sortedBy { it.date })
    fun updateFixture(state: AppState, f: Fixture): AppState = state.copy(fixtures = state.fixtures.map { if (it.id == f.id) f else it }.sortedBy { it.date })
    fun deleteFixture(state: AppState, id: String): AppState = state.copy(fixtures = state.fixtures.filterNot { it.id == id })

    /** The fixtures still to play: from today on, and not one that already has a logged result. */
    fun upcoming(state: AppState): List<Fixture> {
        val today = ContractTime.referenceDate(state)
        return state.fixtures.filter { f ->
            (today == null || f.date >= today) && state.recentMatches.none { m -> m.opponentName.equals(f.opponent, true) && m.matchDate == f.date }
        }.sortedWith(compareBy({ it.date }, { it.kickOff }))
    }

    fun nextFixture(state: AppState): Fixture? = upcoming(state).firstOrNull()

    /** A logged match takes its fixture off the list. */
    fun dropPlayedFixture(state: AppState, m: MatchResult): AppState = state.copy(fixtures = state.fixtures.filterNot { f ->
        f.opponent.equals(m.opponentName, true) && (f.date == m.matchDate || (Dates.daysBetween(f.date, m.matchDate)?.let { kotlin.math.abs(it) <= 3 } == true && f.competition.equals(m.competition, true)))
    })

    // -- injuries -------------------------------------------------------------------------------------------------------

    /** Saves an injury from the edit dialog. Changing the game's date turns "Follow the game" off unless the person kept it on. */
    fun setInjury(state: AppState, playerId: String, name: String, start: String?, back: String?, followGame: Boolean, note: String?): AppState =
        state.copy(players = state.players.map { p ->
            if (p.id != playerId) p else p.copy(
                isInjured = true,
                injuryName = name.ifBlank { "Injury" },
                injuryStartDate = start,
                injuryReturnDate = if (followGame && p.injuryGameReturnDate != null) p.injuryGameReturnDate else back,
                injuryFollowGame = followGame,
                injurySource = p.injurySource ?: Source.MANUAL,
                injuryNote = note?.ifBlank { null }
            )
        }).let { it.copy(activeInjuries = it.players.count { p -> p.isInjured }) }

    fun clearInjury(state: AppState, playerId: String): AppState =
        state.copy(players = state.players.map { if (it.id == playerId) CareerReport.clearedInjury(it) else it }).let { it.copy(activeInjuries = it.players.count { p -> p.isInjured }) }

    /** League and cup matches played while he was out (counted from the matches logged). */
    fun matchesMissed(p: Player, matches: List<MatchResult>, today: String?): Int {
        val start = p.injuryStartDate ?: return 0
        val end = listOfNotNull(p.injuryReturnDate, today).minOrNull() ?: today ?: return 0
        return matches.count { m -> m.matchDate >= start && m.matchDate <= end && m.playerPerformances.none { it.playerId == p.id || it.playerName.equals(p.name, true) } }
    }

    // -- notes, awards, winners, coefficients --------------------------------------------------------------------------

    fun addPlayerNote(state: AppState, playerId: String, text: String): AppState =
        if (text.isBlank()) state else state.copy(playerNotes = state.playerNotes + PlayerNote(playerId = playerId, text = text.trim(), date = today(state)))

    fun deletePlayerNote(state: AppState, id: String): AppState = state.copy(playerNotes = state.playerNotes.filterNot { it.id == id })

    fun addAward(state: AppState, a: AwardEntry): AppState = state.copy(awards = state.awards + a.copy(source = Source.MANUAL, season = if (a.season == 0) state.startYear else a.season))
    fun deleteAward(state: AppState, id: String): AppState = state.copy(awards = state.awards.filterNot { it.id == id })

    fun addWorldWinner(state: AppState, w: WorldWinner): AppState =
        state.copy(worldWinners = state.worldWinners.filterNot { it.season == w.season && it.competition.equals(w.competition, true) } + w.copy(source = Source.MANUAL))

    fun deleteWorldWinner(state: AppState, season: Int, competition: String): AppState =
        state.copy(worldWinners = state.worldWinners.filterNot { it.season == season && it.competition.equals(competition, true) })

    fun addWorldTopScorer(state: AppState, t: WorldTopScorer): AppState =
        state.copy(worldTopScorers = state.worldTopScorers.filterNot { it.season == t.season && it.region.equals(t.region, true) } + t.copy(source = Source.MANUAL))

    /** The season whose winners a console player fills in now: the one that just ended (or ends this June). */
    fun worldSeasonToFill(state: AppState): Int {
        val d = Dates.parse(today(state))
        return if (d != null && d.month in 6..7) d.year - 1 else state.startYear - 1
    }

    /** Console, June: the reminder to fill in Around the World is due and not dismissed. */
    fun worldReminderDue(state: AppState): Boolean {
        if (state.platform != com.fctracker.career.Platform.CONSOLE) return false
        val d = Dates.parse(ContractTime.referenceDate(state)) ?: return false
        val season = d.year - 1
        return d.month == 6 && state.worldReminderDismissed != season && state.worldWinners.none { it.season == season }
    }

    fun setOwnCoefficient(state: AppState, season: Int, points: Double): AppState =
        state.copy(ownCoefficientPoints = state.ownCoefficientPoints + (season.toString() to points))

    /** The person's own five-season total (console), from the points they typed. */
    fun ownCoefficientTotal(state: AppState): Double =
        state.ownCoefficientPoints.filterKeys { k -> k.toIntOrNull()?.let { it > state.startYear - 5 } == true }.values.sum()

    // -- the team sheet from the game -----------------------------------------------------------------------------------

    /**
     * "Use my team sheet from the game": the starting XI picked in FC 26 (and its formation). Players the game names
     * are matched by their game id; the view model then places them on the pitch by position. Null when the sheet
     * names fewer than seven players the app knows.
     */
    fun applyTeamSheet(state: AppState): AppState? {
        val sheet = state.gameTeamSheet ?: return null
        val byGameId = state.players.filter { it.gamePlayerId != null }.associateBy { it.gamePlayerId!! }
        val starters = sheet.starting.mapNotNull { byGameId[it]?.id }.toSet()
        if (starters.size < 7) return null
        return state.copy(
            activeFormation = sheet.formation.ifBlank { state.activeFormation },
            players = state.players.map { p -> p.copy(isStartingXI = p.id in starters) }
        )
    }
}
