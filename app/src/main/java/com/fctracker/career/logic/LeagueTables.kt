package com.fctracker.career.logic

import com.fctracker.career.LeagueTableData
import com.fctracker.career.MatchResult
import com.fctracker.career.Source
import com.fctracker.career.TableRow

/** What a coloured band on the left of a table row means. */
enum class ZoneKind(val label: String) {
    CHAMPION("Champions"),
    CHAMPIONS_LEAGUE("Champions League"),
    EUROPA_LEAGUE("Europa League"),
    CONFERENCE_LEAGUE("Conference League"),
    PROMOTION("Promotion"),
    PLAYOFF("Play-offs"),
    RELEGATION_PLAYOFF("Relegation play-off"),
    RELEGATION("Relegation")
}

data class Zone(val from: Int, val to: Int, val kind: ZoneKind) {
    operator fun contains(position: Int) = position in from..to
}

/** Who goes down from a league, who comes up into it, and who goes up from it, at the start of a new season. */
data class Movements(
    val down: List<String>,
    val upInto: List<String>,
    val upFrom: List<String>,
    val leagueBelow: String?,
    val leagueAbove: String?
)

/**
 * Everything about league tables that is not drawing them: the order, the zones of each league, editing a club's
 * numbers on console, and the clubs that change at the start of a season (promoted and relegated).
 */
object LeagueTables {

    /** The table order: points, then goal difference, then goals scored, then name. */
    fun sorted(rows: List<TableRow>): List<TableRow> =
        rows.sortedWith(
            compareByDescending<TableRow> { it.points }
                .thenByDescending { it.goalDifference }
                .thenByDescending { it.goalsFor }
                .thenBy { it.club.lowercase() }
        )

    fun position(rows: List<TableRow>, club: String): Int? {
        val i = sorted(rows).indexOfFirst { it.club.equals(club, ignoreCase = true) }
        return if (i >= 0) i + 1 else null
    }

    /** Played and points are worked out from won, drawn and lost (the console edit dialog). */
    fun recalculated(row: TableRow): TableRow =
        row.copy(played = row.won + row.drawn + row.lost, points = row.won * 3 + row.drawn)

    private fun has(name: String, vararg parts: String) = parts.any { name.contains(it, ignoreCase = true) }

    /** The leagues the app knows the zones of; others get "the champion and the bottom three". */
    fun zonesFor(league: String, clubs: Int): List<Zone> {
        val n = clubs.coerceAtLeast(1)
        val l = league.trim()
        return when {
            has(l, "championship") && !has(l, "scottish") ->
                listOf(Zone(1, 2, ZoneKind.PROMOTION), Zone(3, 6, ZoneKind.PLAYOFF), Zone(n - 2, n, ZoneKind.RELEGATION))
            has(l, "league one") ->
                listOf(Zone(1, 2, ZoneKind.PROMOTION), Zone(3, 6, ZoneKind.PLAYOFF), Zone(n - 3, n, ZoneKind.RELEGATION))
            has(l, "league two") ->
                listOf(Zone(1, 3, ZoneKind.PROMOTION), Zone(4, 7, ZoneKind.PLAYOFF), Zone(n - 1, n, ZoneKind.RELEGATION))
            has(l, "premier league") && !has(l, "scottish", "russian", "ukrain", "egypt") ->
                listOf(Zone(1, 4, ZoneKind.CHAMPIONS_LEAGUE), Zone(5, 5, ZoneKind.EUROPA_LEAGUE), Zone(6, 6, ZoneKind.CONFERENCE_LEAGUE), Zone(n - 2, n, ZoneKind.RELEGATION))
            (has(l, "laliga", "la liga") && !has(l, "hypermotion", "2")) || (has(l, "serie a") && !has(l, "brasil", "brazil")) ->
                listOf(Zone(1, 4, ZoneKind.CHAMPIONS_LEAGUE), Zone(5, 5, ZoneKind.EUROPA_LEAGUE), Zone(6, 6, ZoneKind.CONFERENCE_LEAGUE), Zone(n - 2, n, ZoneKind.RELEGATION))
            has(l, "bundesliga") && !has(l, "2.", "2 ", "austria", "österreich") ->
                listOf(Zone(1, 4, ZoneKind.CHAMPIONS_LEAGUE), Zone(5, 5, ZoneKind.EUROPA_LEAGUE), Zone(6, 6, ZoneKind.CONFERENCE_LEAGUE), Zone(n - 2, n - 2, ZoneKind.RELEGATION_PLAYOFF), Zone(n - 1, n, ZoneKind.RELEGATION))
            has(l, "ligue 1") ->
                listOf(Zone(1, 4, ZoneKind.CHAMPIONS_LEAGUE), Zone(5, 5, ZoneKind.EUROPA_LEAGUE), Zone(6, 6, ZoneKind.CONFERENCE_LEAGUE), Zone(n - 2, n - 2, ZoneKind.RELEGATION_PLAYOFF), Zone(n - 1, n, ZoneKind.RELEGATION))
            has(l, "hypermotion", "serie b", "2. bundesliga", "ligue 2") ->
                listOf(Zone(1, 2, ZoneKind.PROMOTION), Zone(3, 6, ZoneKind.PLAYOFF), Zone(n - 3, n, ZoneKind.RELEGATION))
            n >= 10 -> listOf(Zone(1, 1, ZoneKind.CHAMPION), Zone(n - 2, n, ZoneKind.RELEGATION))
            else -> emptyList()
        }.filter { it.from in 1..n && it.to in 1..n && it.from <= it.to }
    }

    fun zoneOf(league: String, clubs: Int, position: Int): ZoneKind? = zonesFor(league, clubs).firstOrNull { position in it }?.kind

    /** True for a league whose top places go up a division (not to Europe). */
    fun isSecondTier(league: String) = zonesFor(league, 24).any { it.kind == ZoneKind.PROMOTION }

    /** The league below and above, for the leagues the app knows. */
    fun leagueBelow(league: String): String? = LADDERS.firstNotNullOfOrNull { ladder ->
        val i = ladder.indexOfFirst { it.equals(league.trim(), ignoreCase = true) }
        if (i >= 0 && i + 1 < ladder.size) ladder[i + 1] else null
    }

    fun leagueAbove(league: String): String? = LADDERS.firstNotNullOfOrNull { ladder ->
        val i = ladder.indexOfFirst { it.equals(league.trim(), ignoreCase = true) }
        if (i > 0) ladder[i - 1] else null
    }

    private val LADDERS = listOf(
        listOf("Premier League", "EFL Championship", "EFL League One", "EFL League Two"),
        listOf("Premier League", "Championship", "League One", "League Two"),
        listOf("LALIGA EA SPORTS", "LALIGA HYPERMOTION"),
        listOf("La Liga", "La Liga 2"),
        listOf("Serie A", "Serie B"),
        listOf("Bundesliga", "2. Bundesliga", "3. Liga"),
        listOf("Ligue 1", "Ligue 2")
    )

    // -- editing on console ------------------------------------------------------------------------------------------

    fun replaceRow(table: LeagueTableData, oldClub: String, row: TableRow): LeagueTableData =
        table.copy(rows = table.rows.map { if (it.club.equals(oldClub, ignoreCase = true)) recalculated(row) else it }, source = Source.MANUAL)

    fun addClub(table: LeagueTableData, club: String, isUser: Boolean = false): LeagueTableData {
        val name = club.trim()
        if (name.isEmpty() || table.rows.any { it.club.equals(name, ignoreCase = true) }) return table
        return table.copy(rows = table.rows + TableRow(club = name, isUser = isUser), source = Source.MANUAL)
    }

    fun removeClub(table: LeagueTableData, club: String): LeagueTableData =
        table.copy(rows = table.rows.filterNot { it.club.equals(club, ignoreCase = true) }, source = Source.MANUAL)

    fun renameClub(table: LeagueTableData, oldClub: String, newClub: String): LeagueTableData {
        val name = newClub.trim()
        if (name.isEmpty()) return table
        if (!oldClub.equals(name, ignoreCase = true) && table.rows.any { it.club.equals(name, ignoreCase = true) }) return table
        return table.copy(rows = table.rows.map { if (it.club.equals(oldClub, ignoreCase = true)) it.copy(club = name) else it }, source = Source.MANUAL)
    }

    /**
     * The person's own row worked out from their logged league matches (console: "fill my row from my matches").
     */
    fun rowFromMatches(club: String, matches: List<MatchResult>, league: String): TableRow {
        val lm = matches.filter { it.competition.equals(league, ignoreCase = true) }
        var w = 0; var d = 0; var l = 0; var gf = 0; var ga = 0
        for (m in lm) {
            when (m.resultBadge) { "W" -> w++; "L" -> l++; else -> d++ }
            gf += m.goalsFor; ga += m.goalsAgainst
        }
        val form = lm.sortedBy { it.matchDate }.takeLast(5).joinToString("") { it.resultBadge }
        return recalculated(TableRow(club = club, won = w, drawn = d, lost = l, goalsFor = gf, goalsAgainst = ga, form = form, isUser = true))
    }

    // -- a new season: promoted and relegated clubs -------------------------------------------------------------------

    /**
     * Suggests who changes league, from the final tables: the relegation places of [own] go down; the promotion
     * places (and the first play-off place, standing in for the play-off winner) of the league below come up, and
     * the same places of [own] go up. Every name can be changed by the person before it is used.
     */
    fun proposeMovements(own: LeagueTableData, below: LeagueTableData?): Movements {
        val rows = sorted(own.rows)
        val zones = zonesFor(own.league, rows.size)
        val down = zones.filter { it.kind == ZoneKind.RELEGATION }
            .flatMap { z -> (z.from..z.to).mapNotNull { rows.getOrNull(it - 1)?.club } }
        val upFrom = upPlaces(own.league, rows)
        val upInto = if (below != null) upPlaces(below.league, sorted(below.rows)) else emptyList()
        return Movements(down, upInto, upFrom, leagueBelow(own.league) ?: below?.league, leagueAbove(own.league))
    }

    private fun upPlaces(league: String, rows: List<TableRow>): List<String> {
        val zones = zonesFor(league, rows.size)
        val promo = zones.filter { it.kind == ZoneKind.PROMOTION }.flatMap { z -> (z.from..z.to).toList() }
        if (promo.isEmpty()) return emptyList()
        val playoff = zones.firstOrNull { it.kind == ZoneKind.PLAYOFF }?.from
        return (promo + listOfNotNull(playoff)).mapNotNull { rows.getOrNull(it - 1)?.club }
    }

    /**
     * The table for the new season: the clubs that went down or up leave, the clubs that came in join, every number
     * goes back to zero, and the newcomers carry a "promoted" or "relegated" tag for the season.
     */
    fun newSeasonTable(
        own: LeagueTableData,
        leaving: List<String>,
        joiningPromoted: List<String>,
        joiningRelegated: List<String>,
        userClub: String
    ): LeagueTableData {
        val leave = leaving.map { it.trim().lowercase() }.toSet()
        val kept = own.rows.filter { it.club.trim().lowercase() !in leave }
            .map { TableRow(club = it.club, isUser = it.isUser || it.club.equals(userClub, ignoreCase = true)) }
        val names = kept.map { it.club.lowercase() }.toMutableSet()
        val added = mutableListOf<TableRow>()
        for (c in joiningPromoted) if (c.isNotBlank() && names.add(c.trim().lowercase())) added += TableRow(club = c.trim(), movement = "promoted", isUser = c.equals(userClub, true))
        for (c in joiningRelegated) if (c.isNotBlank() && names.add(c.trim().lowercase())) added += TableRow(club = c.trim(), movement = "relegated", isUser = c.equals(userClub, true))
        return own.copy(rows = (kept + added).sortedBy { it.club.lowercase() }, source = Source.MANUAL, updated = "")
    }

    /** The results against one club this season (tap a club in the table). */
    fun headToHead(matches: List<MatchResult>, club: String): List<MatchResult> =
        matches.filter { it.opponentName.equals(club, ignoreCase = true) }.sortedBy { it.matchDate }
}
