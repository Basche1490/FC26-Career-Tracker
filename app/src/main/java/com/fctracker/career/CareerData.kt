package com.fctracker.career

import java.util.UUID

/*
 * The data added for the final design: fixtures, every league table, season statistics, awards, the winners of the
 * season around the world, the European club ranking, the club's own history and the per-player extras (squad role,
 * notes, rating by season, injuries the person changed by hand).
 *
 * Every class here has a default for every field. Kotlin then generates a no-argument constructor, which Gson uses,
 * so a field missing from an older save simply takes its default instead of becoming null.
 */

/** Where a value came from. The app never overwrites something the person typed with a value from the game. */
object Source {
    const val GAME = "game"
    const val MANUAL = "manual"
}

/** Where the person plays: on PC the app fills itself through the PC helper; on console everything is typed in. */
object Platform {
    const val PC = "pc"
    const val CONSOLE = "console"
}

/** A match still to be played, from the game's fixture list or added by hand. */
data class Fixture(
    val id: String = UUID.randomUUID().toString(),
    /** yyyy-MM-dd */
    val date: String = "",
    /** HH:mm, blank when not known. */
    val kickOff: String = "",
    val competition: String = "",
    /** "Matchday 15", "Round 3", "Final"... */
    val round: String = "",
    val opponent: String = "",
    val isHome: Boolean = true,
    val source: String = Source.MANUAL,
    /** The game's own key for the fixture (blank for one added by hand); used to recognise it again. */
    val gameKey: String = ""
)

/** One club's line in a league table. */
data class TableRow(
    val club: String = "",
    val played: Int = 0,
    val won: Int = 0,
    val drawn: Int = 0,
    val lost: Int = 0,
    val goalsFor: Int = 0,
    val goalsAgainst: Int = 0,
    val points: Int = 0,
    /** The last league results, oldest first ("WWDLW"); blank when not known. */
    val form: String = "",
    val isUser: Boolean = false,
    /** "promoted" or "relegated" for a club that changed league this season; blank otherwise. */
    val movement: String = ""
) {
    val goalDifference: Int get() = goalsFor - goalsAgainst
}

/** A whole league table. The person's own league is the one whose [league] equals the career's league name. */
data class LeagueTableData(
    val league: String = "",
    val country: String = "",
    val rows: List<TableRow> = emptyList(),
    val source: String = Source.MANUAL,
    /** The game date (yyyy-MM-dd) the table is from; blank when typed in. */
    val updated: String = ""
)

/** A name in a "who leads the league" list (Golden Boot, assists, clean sheets). */
data class StatLeader(
    val player: String = "",
    val club: String = "",
    val value: Int = 0,
    val isUser: Boolean = false
)

data class CompetitionLeaders(
    val competition: String = "",
    val goals: List<StatLeader> = emptyList(),
    val assists: List<StatLeader> = emptyList(),
    val cleanSheets: List<StatLeader> = emptyList(),
    val source: String = Source.GAME,
    val updated: String = ""
)

/** One of the person's players in one competition this season, as the game counts it. */
data class PlayerSeasonStat(
    val playerName: String = "",
    val gamePlayerId: Int? = null,
    val competition: String = "",
    val appearances: Int = 0,
    val goals: Int = 0,
    val assists: Int = 0,
    val cleanSheets: Int = 0,
    val yellowCards: Int = 0,
    val redCards: Int = 0,
    val averageRating: Double = 0.0,
    val motm: Int = 0
)

/** An award: from the game (player of the month, team of the week...) or one the person gave. */
data class AwardEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    /** The player's name, or the manager's ("You"). */
    val recipient: String = "",
    /** "September 2026", "2026/27", "Week 12"... */
    val period: String = "",
    /** "player", "manager", "team" or "totw" (team of the week). */
    val kind: String = "player",
    val season: Int = 0,
    val source: String = Source.MANUAL,
    val gameKey: String = ""
)

/** A winner of a league or cup in the season that just finished (Around the World). */
data class WorldWinner(
    /** The start year of the finished season (2025 for 2025/26). */
    val season: Int = 0,
    /** "Europe", "England", "Spain", "Italy", "Germany", "France", or the person's own country. */
    val region: String = "",
    val competition: String = "",
    val winner: String = "",
    val runnerUp: String = "",
    /** The final's score ("2–1"); blank when the game kept none. */
    val score: String = "",
    /** The shoot-out ("5–4") when the final went to penalties. */
    val penalties: String = "",
    /** For a league: "84 pts · Liverpool 2nd". */
    val detail: String = "",
    val isLeague: Boolean = false,
    val source: String = Source.GAME
)

data class WorldTopScorer(
    val season: Int = 0,
    val region: String = "",
    val competition: String = "",
    val player: String = "",
    val club: String = "",
    val goals: Int = 0,
    val source: String = Source.GAME
)

/** A club in the European club ranking (club coefficients: points over the last five seasons). */
data class ClubCoefficient(
    val rank: Int = 0,
    val club: String = "",
    val country: String = "",
    /** Points this season. */
    val season: Double = 0.0,
    /** Points over five seasons. */
    val total: Double = 0.0,
    val isUser: Boolean = false
)

data class NationCoefficient(
    val country: String = "",
    val points: Double = 0.0
)

/** What is known about the club: from the game for a real club, typed in (or written with AI) for the person's own. */
data class ClubInfo(
    val founded: String = "",
    val stadium: String = "",
    val capacity: Int = 0,
    val rival: String = "",
    val leagueTitles: Int = 0,
    val domesticCups: Int = 0,
    val europeanTrophies: Int = 0,
    /** Club worth as the game reports it, already formatted ("€312M"); blank when not known. */
    val clubWorth: String = "",
    /** 0 to 10, 0 = not known. */
    val prestigeHome: Int = 0,
    val prestigeAbroad: Int = 0,
    val colours: String = "",
    val nickname: String = "",
    val hometown: String = "",
    /** The history text. */
    val history: String = "",
    /** "wikipedia", "ai", "own" or blank. */
    val historySource: String = "",
    /** The Wikipedia page the history came from, for "Read more". */
    val historyUrl: String = "",
    /** The lines the app adds at the end of every season ("2026/27: 4th in the Championship."). */
    val seasonLines: List<String> = emptyList(),
    /** When the person took over (as typed or as the game says: "July 2024"). */
    val managerSince: String = "",
    /** The answers given when the history was written with AI, so "Rewrite" can use them again. */
    val aiAnswers: Map<String, String> = emptyMap(),
    val source: String = Source.MANUAL
)

/** A note the person wrote about a player. It belongs to the career, so it is part of the backup. */
data class PlayerNote(
    val id: String = UUID.randomUUID().toString(),
    val playerId: String = "",
    val text: String = "",
    /** The game date when written (yyyy-MM-dd), or the phone's date when the game date is not known. */
    val date: String = ""
)

/** A player's overall rating at the end of a season (or today, for the current one). */
data class SeasonRating(
    val season: Int = 0,
    val overall: Int = 0
)

/** A milestone the app noticed (150 appearances, 50 goals, a big rating jump, a club record). */
data class Milestone(
    /** A stable key ("apps:150:<player id>") so the same milestone is never announced twice. */
    val key: String = "",
    val title: String = "",
    val detail: String = "",
    /** "apps", "goals", "assists", "growth" or "record". */
    val kind: String = "",
    val playerId: String = "",
    val playerName: String = "",
    val date: String = "",
    val season: Int = 0
)

/** Which milestones raise an alert. */
data class MilestonePrefs(
    val appearances: Boolean = true,
    val goals: Boolean = true,
    val growth: Boolean = true,
    val records: Boolean = true
)

/** The team sheet picked in the game: the formation, the starting XI (game player ids, in slot order) and the bench. */
data class TeamSheet(
    val formation: String = "",
    val starting: List<Int> = emptyList(),
    val bench: List<Int> = emptyList(),
    /** The position each starter plays in the game's team sheet (player id -> "ST"). */
    val positions: Map<String, String> = emptyMap(),
    val updated: String = ""
)
