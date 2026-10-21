package com.fctracker.career.logic

import com.fctracker.career.AppState
import com.fctracker.career.Player
import com.fctracker.career.feeLabel
import com.fctracker.career.isIncomingDeal

/** One block on a recap page. The renderer draws each kind the same way on every page. */
sealed class RecapBlock {
    /** A huge figure ("11th") with a line under it. */
    data class Big(val value: String, val caption: String) : RecapBlock()
    /** Two to four small figures side by side. */
    data class Figures(val items: List<Pair<String, String>>) : RecapBlock()
    /** "Top scorer ........ Marco Varela · 23" */
    data class Line(val key: String, val value: String) : RecapBlock()
    object Rule : RecapBlock()
    data class Label(val text: String) : RecapBlock()
    /** A player with a headshot, a line under the name and a figure on the right. */
    data class Person(val name: String, val sub: String, val value: String, val rank: Int? = null, val headshotUrl: String = "", val big: Boolean = false) : RecapBlock()
    /** W / D / L dots, oldest first. */
    data class Form(val results: List<String>, val note: String = "") : RecapBlock()
    /** The league position matchday by matchday (1 = top). */
    data class Chart(val positions: List<Int>, val clubs: Int) : RecapBlock()
}

data class RecapPage(val number: Int, val name: String, val title: String, val blocks: List<RecapBlock>)

/**
 * The season recap: ten pages, each an image of its own in the club's colours, made from the matches, the squad,
 * the transfers and the game's figures. A page with nothing to show (no cup run, no transfers) is left out.
 */
object Recap {

    val PAGE_NAMES = listOf("Cover", "The league", "Results", "Goals", "Player of the season", "Transfers", "Growth", "Cups & Europe", "Milestones & records", "Around the World")

    private fun ord(n: Int) = Milestones.ordinal(n)

    private fun runText(r: Run?): String {
        if (r == null || r.length < 2) return "–"
        val from = Dates.parse(r.from)
        val to = Dates.parse(r.to)
        val span = if (from != null && to != null && (from.month != to.month || from.year != to.year)) " · ${Dates.MONTHS_SHORT[from.month - 1]} to ${Dates.MONTHS_SHORT[to.month - 1]}" else ""
        return "${r.length} matches$span"
    }

    private fun posName(p: Player): String = when (p.primaryPosition.uppercase()) {
        "GK" -> "Goalkeeper"
        "CB" -> "Centre-back"
        "LB", "LWB" -> "Left back"
        "RB", "RWB" -> "Right back"
        "CDM" -> "Defensive midfield"
        "CM" -> "Central midfield"
        "CAM" -> "Attacking midfield"
        "LM" -> "Left midfield"
        "RM" -> "Right midfield"
        "LW" -> "Left wing"
        "RW" -> "Right wing"
        "ST" -> "Striker"
        "CF" -> "Centre-forward"
        else -> p.primaryPosition
    }

    /**
     * Builds the pages for the season [view] shows. [state] supplies the rest of the career (earlier seasons for the
     * records, the winners around the world, the club's name and currency).
     */
    fun build(state: AppState, view: SeasonView): List<RecapPage> {
        val cur = state.currencySymbol.ifBlank { "€" }
        val isLeague = Competitions.isLeagueFn(view.leagueName)
        val matches = view.matches
        val league = matches.filter { isLeague(it.competition) }
        val all = SeasonFacts.split(matches)
        val lg = SeasonFacts.split(league)
        val table = view.ownTable
        val sortedTable = table?.let { LeagueTables.sorted(it.rows) }.orEmpty()
        val clubs = sortedTable.size.takeIf { it > 0 } ?: 24
        val positions = SeasonFacts.leaguePositions(matches, isLeague)
        val finalPos = sortedTable.indexOfFirst { it.isUser || it.club.equals(state.clubName, true) }.takeIf { it >= 0 }?.plus(1)
            ?: positions.lastOrNull() ?: view.archived?.finalPosition
        val totals = SeasonFacts.allTotals(view.players, matches, view.squadStats)
        val scorers = totals.filter { it.goals > 0 }.sortedByDescending { it.goals }
        val assisters = totals.filter { it.assists > 0 }.sortedByDescending { it.assists }
        val pos = SeasonFacts.playerOfSeason(totals, matches.size)
        val best = SeasonFacts.bestWin(matches)
        val worst = SeasonFacts.heaviestDefeat(matches)
        val pages = mutableListOf<RecapPage>()
        val seasonLabel = view.label
        fun vs(m: com.fctracker.career.MatchResult) = "${m.goalsFor}–${m.goalsAgainst} ${if (m.isHome) "vs" else "at"} ${m.opponentName}"
        val trophies = (view.archived?.trophies ?: emptyList()) + state.teamTrophies.map { it.name }.filter { it.contains(seasonLabel) || it.contains("${view.season}") }

        // 1 Cover
        val note = when {
            trophies.any { it.contains("Champions", true) || it.contains("Winners", true) } -> trophies.first()
            finalPos == 1 -> "Champions"
            view.isArchived && finalPos != null && LeagueTables.zoneOf(view.leagueName, clubs, finalPos) == ZoneKind.PROMOTION -> "Promoted"
            else -> ""
        }
        pages += RecapPage(1, PAGE_NAMES[0], "Season $seasonLabel", listOfNotNull(
            RecapBlock.Big(finalPos?.let { ord(it) } ?: "–", listOf(view.leagueName, note).filter { it.isNotBlank() }.joinToString(" · ")),
            RecapBlock.Figures(listOf("Played" to "${all.played}", "Won" to "${all.won}", "Drawn" to "${all.drawn}", "Lost" to "${all.lost}")),
            RecapBlock.Rule,
            scorers.firstOrNull()?.let { RecapBlock.Line("Top scorer", "${it.player.displayName} · ${it.goals}") },
            pos?.let { RecapBlock.Line("Player of the season", "${it.player.displayName} · ${"%.1f".format(java.util.Locale.US, it.averageRating)}") },
            best?.let { RecapBlock.Line("Best win", vs(it)) },
            RecapBlock.Form(SeasonFacts.form(matches, 10), "Swipe for more →").takeIf { matches.isNotEmpty() }
        ))

        // 2 The league
        if (league.isNotEmpty() || sortedTable.isNotEmpty()) {
            val ownRow = sortedTable.firstOrNull { it.isUser || it.club.equals(state.clubName, true) }
            val points = ownRow?.points ?: lg.points
            val gf = ownRow?.goalsFor ?: lg.goalsFor
            val ga = ownRow?.goalsAgainst ?: lg.goalsAgainst
            val record = ownRow?.let { "${it.won}-${it.drawn}-${it.lost}" } ?: "${lg.won}-${lg.drawn}-${lg.lost}"
            val home = SeasonFacts.home(league)
            val away = SeasonFacts.away(league)
            val hi = positions.withIndex().minByOrNull { it.value }
            val lo = positions.withIndex().maxByOrNull { it.value }
            pages += RecapPage(2, PAGE_NAMES[1], "The league", listOfNotNull(
                RecapBlock.Label("Position, matchday by matchday").takeIf { positions.size >= 2 },
                RecapBlock.Chart(positions, clubs).takeIf { positions.size >= 2 },
                RecapBlock.Figures(listOf("Final" to (finalPos?.let { ord(it) } ?: "–"), "Points" to "$points", "Record" to record, "Goals" to "$gf–$ga")),
                RecapBlock.Rule,
                RecapBlock.Line("At home", "${home.won} won · ${home.drawn} drawn · ${home.lost} lost").takeIf { league.isNotEmpty() },
                RecapBlock.Line("Away", "${away.won} won · ${away.drawn} drawn · ${away.lost} lost").takeIf { league.isNotEmpty() },
                hi?.let { RecapBlock.Line("Highest", "${ord(it.value)} on matchday ${it.index + 1}") },
                lo?.let { RecapBlock.Line("Lowest", "${ord(it.value)} on matchday ${it.index + 1}") }
            ))
        }

        // 3 Results
        if (matches.isNotEmpty()) {
            pages += RecapPage(3, PAGE_NAMES[2], "Results", listOfNotNull(
                best?.let { RecapBlock.Line("Best win", "${it.goalsFor}–${it.goalsAgainst} ${if (it.isHome) "vs" else "at"} ${it.opponentName} (${if (it.isHome) "H" else "A"})") },
                worst?.let { RecapBlock.Line("Heaviest defeat", vs(it)) },
                RecapBlock.Line("Longest unbeaten run", runText(SeasonFacts.longestUnbeaten(matches))),
                RecapBlock.Line("Longest winning run", runText(SeasonFacts.longestWinning(matches))),
                RecapBlock.Line("Clean sheets", "${SeasonFacts.cleanSheets(matches)}"),
                RecapBlock.Line("Goals per match", "%.2f".format(java.util.Locale.US, all.goalsFor.toDouble() / all.played.coerceAtLeast(1))),
                RecapBlock.Rule,
                RecapBlock.Label("Last ${minOf(20, matches.size)} matches, oldest first"),
                RecapBlock.Form(SeasonFacts.form(matches, 20))
            ))
        }

        // 4 Goals
        if (scorers.isNotEmpty() || assisters.isNotEmpty()) {
            val byComp = SeasonFacts.goalsByCompetition(matches).filter { it.second > 0 }
            pages += RecapPage(4, PAGE_NAMES[3], "Goals", buildList {
                if (scorers.isNotEmpty()) {
                    add(RecapBlock.Label("Top scorers"))
                    scorers.take(3).forEachIndexed { i, t -> add(RecapBlock.Person(t.player.displayName, posName(t.player), "${t.goals}", i + 1, t.player.headshotUrl)) }
                }
                if (assisters.isNotEmpty()) {
                    add(RecapBlock.Rule)
                    add(RecapBlock.Label("Most assists"))
                    assisters.take(2).forEachIndexed { i, t -> add(RecapBlock.Person(t.player.displayName, posName(t.player), "${t.assists}", i + 1, t.player.headshotUrl)) }
                }
                if (byComp.isNotEmpty()) {
                    add(RecapBlock.Rule)
                    add(RecapBlock.Line("Goals by competition", byComp.joinToString(" · ") { "${shortComp(it.first)} ${it.second}" }))
                }
            })
        }

        // 5 Player of the season
        if (pos != null) {
            val motm = totals.filter { it.motm > 0 }.maxByOrNull { it.motm }
            val apps = totals.maxByOrNull { it.appearances }
            val young = totals.filter { it.player.age <= 21 && it.averageRating > 0 && it.appearances >= 3 }.maxByOrNull { it.averageRating }
            val keeper = totals.filter { it.player.primaryPosition.equals("GK", true) }.maxByOrNull { it.cleanSheets }
            pages += RecapPage(5, PAGE_NAMES[4], "Player of the season", listOfNotNull(
                RecapBlock.Person(pos.player.displayName, "${"%.1f".format(java.util.Locale.US, pos.averageRating)} average · ${pos.motm} times man of the match", "", headshotUrl = pos.player.headshotUrl, big = true),
                RecapBlock.Rule,
                RecapBlock.Line("Best average rating", "${pos.player.displayName} · ${"%.1f".format(java.util.Locale.US, pos.averageRating)}"),
                motm?.let { RecapBlock.Line("Most man of the match", "${it.player.displayName} · ${it.motm}") },
                apps?.takeIf { it.appearances > 0 }?.let { RecapBlock.Line("Most appearances", "${it.player.displayName} · ${it.appearances}") },
                young?.let { RecapBlock.Line("Young player of the season", "${it.player.displayName} · ${it.player.age}") },
                keeper?.takeIf { it.cleanSheets > 0 }?.let { RecapBlock.Line("Best goalkeeper", "${it.player.displayName} · ${it.cleanSheets} clean sheets") }
            ))
        }

        // 6 Transfers
        if (view.transfers.isNotEmpty()) {
            val ins = view.transfers.filter { it.isIncomingDeal() }
            val outs = view.transfers.filter { !it.isIncomingDeal() && it.type != "Retirement" }
            val spent = ins.sumOf { it.feeM }
            val got = outs.sumOf { it.feeM }
            val net = got - spent
            val bigIn = ins.filter { it.feeM > 0 }.maxByOrNull { it.feeM }
            val bigOut = outs.filter { it.feeM > 0 }.maxByOrNull { it.feeM }
            val value = ins.filter { it.feeM > 0 }.mapNotNull { t -> totals.firstOrNull { it.player.name.equals(t.playerName, true) || it.player.displayName.equals(t.playerName, true) }?.let { t to it } }
                .filter { it.second.appearances > 0 }.minByOrNull { it.first.feeM / it.second.appearances }
            fun m(x: Double) = "$cur${"%.1f".format(java.util.Locale.US, x)}M"
            pages += RecapPage(6, PAGE_NAMES[5], "Transfers", listOfNotNull(
                RecapBlock.Figures(listOf("In" to "${ins.size}", "Out" to "${outs.size}", "Spent" to m(spent), "Received" to m(got))),
                RecapBlock.Line("Net spend", (if (net >= 0) "+" else "−") + m(kotlin.math.abs(net))),
                RecapBlock.Rule,
                bigIn?.let { RecapBlock.Label("Biggest signing") },
                bigIn?.let { t -> RecapBlock.Person(t.playerName, "from ${t.fromToClub}", t.feeLabel(cur), headshotUrl = t.headshotUrl.ifBlank { view.players.firstOrNull { p -> p.name.equals(t.playerName, true) }?.headshotUrl.orEmpty() }) },
                bigOut?.let { RecapBlock.Label("Biggest sale") },
                bigOut?.let { t -> RecapBlock.Person(t.playerName, "to ${t.fromToClub}", t.feeLabel(cur), headshotUrl = t.headshotUrl) },
                value?.let { (t, tot) -> RecapBlock.Line("Best value", "${t.playerName} · ${t.feeLabel(cur)} · ${tot.appearances} matches") }
            ))
        }

        // 7 Growth
        val jumps = view.players.filter { it.startingOverall > 0 && it.overall > it.startingOverall }.sortedByDescending { it.overall - it.startingOverall }
        val debuts = (view.players + view.academy).filter { it.isPromoted && (it.promotedSeasonYear == null || it.promotedSeasonYear == view.season) }
        if (jumps.isNotEmpty() || debuts.isNotEmpty()) {
            val active = view.players.filter { it.status.isBlank() || it.status.equals("Active", true) || it.status.equals("Loaned In", true) }
            val avgStart = active.filter { it.startingOverall > 0 }.map { it.startingOverall }.average()
            val avgNow = active.map { it.overall }.average()
            pages += RecapPage(7, PAGE_NAMES[6], "Growth", buildList {
                if (jumps.isNotEmpty()) {
                    add(RecapBlock.Label("Biggest rating jumps"))
                    jumps.take(3).forEachIndexed { i, p -> add(RecapBlock.Person(p.displayName, "${p.startingOverall} → ${p.overall}", "+${p.overall - p.startingOverall}", i + 1, p.headshotUrl)) }
                    add(RecapBlock.Rule)
                }
                if (debuts.isNotEmpty()) add(RecapBlock.Line("Academy debuts", debuts.take(3).joinToString(", ") { it.displayName }))
                if (!avgStart.isNaN() && !avgNow.isNaN()) add(RecapBlock.Line("Squad average rating", "${"%.1f".format(java.util.Locale.US, avgStart)} → ${"%.1f".format(java.util.Locale.US, avgNow)}"))
                val ages = active.map { it.age }.average()
                if (!ages.isNaN()) add(RecapBlock.Line("Average age", "%.1f".format(java.util.Locale.US, ages)))
            })
        }

        // 8 Cups & Europe
        val cups = CupRuns.all(matches, view.fixtures, isLeague)
        val europeWinners = state.worldWinners.filter { it.season == view.season && it.region.equals("Europe", true) }
        if (cups.isNotEmpty() || europeWinners.isNotEmpty()) {
            val bestCup = matches.filter { !isLeague(it.competition) && CupRuns.outcome(it) == "W" }.maxWithOrNull(compareBy({ it.goalsFor - it.goalsAgainst }, { it.goalsFor }))
            pages += RecapPage(8, PAGE_NAMES[7], "Cups & Europe", buildList {
                cups.forEach { add(RecapBlock.Line(it.competition, it.status)) }
                if (cups.none { it.isEuropean }) add(RecapBlock.Line("Champions League, Europa League, Conference League", "Not this season"))
                if (bestCup != null) {
                    add(RecapBlock.Rule)
                    add(RecapBlock.Label("Best cup night"))
                    add(RecapBlock.Line("${shortComp(bestCup.competition)} ${bestCup.stage}".trim(), vs(bestCup)))
                }
                if (europeWinners.isNotEmpty()) {
                    add(RecapBlock.Rule)
                    add(RecapBlock.Label("European winners"))
                    listOf("Champions League", "Europa League", "Conference League").forEach { key ->
                        europeWinners.firstOrNull { it.competition.contains(key, true) }?.let { add(RecapBlock.Line(key, it.winner)) }
                    }
                }
            })
        }

        // 9 Milestones & records
        val ms = view.milestones
        if (ms.isNotEmpty() || best != null) {
            val pastMatches = state.historicalSeasons.filter { it.year < view.season }.flatMap { it.matches }
            pages += RecapPage(9, PAGE_NAMES[8], "Milestones & records", buildList {
                ms.filter { it.kind != "record" }.take(4).forEach { add(RecapBlock.Line(it.playerName.ifBlank { state.clubName }, it.title.substringAfter(": ").ifBlank { it.title })) }
                val records = mutableListOf<RecapBlock.Line>()
                val wins = SeasonFacts.longestWinning(matches)
                val pastWins = SeasonFacts.longestWinning(pastMatches)?.length ?: 0
                if (wins != null && wins.length >= 3 && wins.length > pastWins) records += RecapBlock.Line("Wins in a row", "${wins.length}")
                val homeWins = SeasonFacts.longestHomeWinning(matches)
                val pastHome = SeasonFacts.longestHomeWinning(pastMatches)?.length ?: 0
                if (homeWins != null && homeWins.length >= 3 && homeWins.length > pastHome) records += RecapBlock.Line("Home wins in a row", "${homeWins.length}")
                val pastBest = SeasonFacts.bestWin(pastMatches)
                if (best != null && (pastBest == null || best.goalsFor - best.goalsAgainst > pastBest.goalsFor - pastBest.goalsAgainst)) records += RecapBlock.Line("Biggest win", vs(best))
                val pastFee = state.historicalSeasons.filter { it.year < view.season }.flatMap { it.transfers }.filter { it.isIncomingDeal() }.maxOfOrNull { it.feeM } ?: 0.0
                view.transfers.filter { it.isIncomingDeal() && it.feeM > pastFee && it.feeM > 0 }.maxByOrNull { it.feeM }?.let { records += RecapBlock.Line("Record signing", "${it.playerName} · ${it.feeLabel(cur)}") }
                ms.filter { it.kind == "record" }.forEach { m -> if (records.none { it.value == m.detail }) records += RecapBlock.Line(m.title.removePrefix("Club record: ").replaceFirstChar { c -> c.uppercase() }, m.detail.removeSuffix(".")) }
                if (records.isNotEmpty()) {
                    if (isNotEmpty()) add(RecapBlock.Rule)
                    add(RecapBlock.Label("Club records set"))
                    addAll(records.take(5))
                }
            }.ifEmpty { listOf(RecapBlock.Line("This season", "No milestones yet")) })
        }

        // 10 Around the World
        val world = WorldWinners.byRegion(state.worldWinners, view.season)
        if (world.isNotEmpty()) {
            pages += RecapPage(10, PAGE_NAMES[9], "Around the World", buildList {
                world.firstOrNull { it.first == "Europe" }?.let { (_, list) ->
                    add(RecapBlock.Label("Europe"))
                    list.forEach { w -> add(RecapBlock.Line(shortComp(w.competition), w.winner + (if (w.score.isNotBlank() && w.runnerUp.isNotBlank()) " · ${w.score} v ${w.runnerUp}" else if (w.penalties.isNotBlank()) " · on penalties" else ""))) }
                    add(RecapBlock.Rule)
                }
                val leagues = world.filter { it.first != "Europe" }.mapNotNull { (_, list) -> list.firstOrNull { it.isLeague } }
                if (leagues.isNotEmpty()) {
                    add(RecapBlock.Label("Top 5 leagues"))
                    leagues.forEach { add(RecapBlock.Line(shortComp(it.competition), it.winner)) }
                }
                val champion = sortedTable.firstOrNull()
                if (champion != null && view.isArchived) {
                    add(RecapBlock.Rule)
                    add(RecapBlock.Line(shortComp(view.leagueName), "${champion.club}${finalPos?.let { " (you: ${ord(it)})" } ?: ""}"))
                }
                state.worldTopScorers.filter { it.season == view.season }.maxByOrNull { it.goals }?.let { add(RecapBlock.Line("Top scorer in Europe", "${it.player} · ${it.goals}")) }
            })
        }
        return pages
    }

    fun shortComp(c: String): String = c.replace("UEFA ", "").replace("LALIGA EA SPORTS", "LALIGA").replace("EFL ", "")
}
