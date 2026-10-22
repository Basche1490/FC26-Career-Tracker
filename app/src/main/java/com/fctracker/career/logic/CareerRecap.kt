package com.fctracker.career.logic

import com.fctracker.career.AppState
import com.fctracker.career.MatchResult
import com.fctracker.career.Player
import com.fctracker.career.feeLabel
import com.fctracker.career.isIncomingDeal

/**
 * The career recap: the season recap's pages for the whole career (every season, the trophies, the records, the best
 * players and the transfers), drawn by the same renderer. A page with nothing to show is left out.
 */
object CareerRecap {

    val PAGE_NAMES = listOf("Cover", "Season by season", "Trophies", "Records", "Hall of fame", "Best players", "Transfers")

    private fun ord(n: Int) = Milestones.ordinal(n)
    private fun f1(x: Double) = "%.1f".format(java.util.Locale.US, x)

    fun isContinental(t: String) = listOf("champions league", "europa", "conference", "uefa", "libertadores", "sudamericana", "club world").any { t.lowercase().contains(it) }
    fun isCup(t: String) = !isContinental(t) && listOf("cup", "copa", "coppa", "pokal", "coupe", "shield", "trophy", "trophée", "supercopa", "supercoppa", "supercup", "play-off", "playoff").any { t.lowercase().contains(it) }

    /** One season of the career, archived or the one being played. */
    private class Season(val year: Int, val league: String, val position: Int?, val points: Int?, val matches: List<MatchResult>, val players: List<Player>,
                         val stats: List<com.fctracker.career.PlayerSeasonStat>, val current: Boolean)

    private fun seasons(state: AppState): List<Season> {
        val past = state.historicalSeasons.sortedBy { it.year }.map {
            Season(it.year, it.leagueName, it.finalPosition.takeIf { p -> p > 0 }, it.points.takeIf { p -> p > 0 }, it.matches, it.players + it.academyPlayers, it.squadStats, false)
        }
        val view = SeasonView.of(state.copy(viewingSeasonYear = null))
        val ownTable = view.ownTable?.let { LeagueTables.sorted(it.rows) }.orEmpty()
        val pos = ownTable.indexOfFirst { it.isUser || it.club.equals(state.clubName, true) }.takeIf { it >= 0 }?.plus(1)
        val pts = ownTable.firstOrNull { it.isUser || it.club.equals(state.clubName, true) }?.points
        val current = Season(state.startYear, state.leagueName, pos, pts, state.recentMatches, state.players + state.academyPlayers, state.squadStats, true)
        return (past.filter { it.year != state.startYear } + current).filter { it.matches.isNotEmpty() || !it.current || it.players.isNotEmpty() }
    }

    /** Every player's totals over the career, the newest card of each player. */
    private fun careerTotals(all: List<Season>): List<PlayerTotals> {
        val acc = LinkedHashMap<String, PlayerTotals>()
        val latest = HashMap<String, Player>()
        fun key(p: Player) = p.gamePlayerId?.let { "g$it" } ?: p.name.lowercase().trim().ifBlank { p.id }
        for (s in all) {
            for (t in SeasonFacts.allTotals(s.players, s.matches, s.stats)) {
                val k = key(t.player)
                latest[k] = t.player
                val prev = acc[k]
                acc[k] = if (prev == null) t else prev.copy(
                    appearances = prev.appearances + t.appearances, goals = prev.goals + t.goals, assists = prev.assists + t.assists,
                    motm = prev.motm + t.motm, cleanSheets = prev.cleanSheets + t.cleanSheets,
                    averageRating = if (prev.appearances + t.appearances > 0)
                        (prev.averageRating * prev.appearances + t.averageRating * t.appearances) / (prev.appearances + t.appearances) else 0.0
                )
            }
        }
        return acc.map { (k, v) -> v.copy(player = latest[k] ?: v.player) }
    }

    fun build(state: AppState): List<RecapPage> {
        val cur = state.currencySymbol.ifBlank { "€" }
        val all = seasons(state)
        val matches = all.flatMap { it.matches }
        val split = SeasonFacts.split(matches)
        val totals = careerTotals(all)
        val trophies = (state.teamTrophies.map { it.name } + state.historicalSeasons.flatMap { it.trophies }).map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        val leagueTitles = maxOf(state.historicalSeasons.count { it.finalPosition == 1 }, trophies.count { !isContinental(it) && !isCup(it) })
        val cups = trophies.count { isCup(it) }
        val continental = trophies.count { isContinental(it) }
        val first = all.minOfOrNull { it.year } ?: state.startYear
        val last = all.maxOfOrNull { it.year } ?: state.startYear
        val span = if (first == last) SeasonView.seasonLabel(first) else "${SeasonView.seasonLabel(first)} to ${SeasonView.seasonLabel(last)}"
        val scorers = totals.filter { it.goals > 0 }.sortedByDescending { it.goals }
        val apps = totals.filter { it.appearances > 0 }.sortedByDescending { it.appearances }
        val assisters = totals.filter { it.assists > 0 }.sortedByDescending { it.assists }
        val bestFinish = all.mapNotNull { s -> s.position?.let { s to it } }.minByOrNull { it.second }
        fun vs(m: MatchResult) = "${m.goalsFor}–${m.goalsAgainst} ${if (m.isHome) "vs" else "at"} ${m.opponentName}"
        val pages = mutableListOf<RecapPage>()

        // 1 Cover
        pages += RecapPage(1, PAGE_NAMES[0], "${state.managerName.ifBlank { "The manager" }}", listOfNotNull(
            if (trophies.isNotEmpty()) RecapBlock.Big("${trophies.size}", "${if (trophies.size == 1) "trophy" else "trophies"} · ${all.size} ${if (all.size == 1) "season" else "seasons"} · $span")
            else RecapBlock.Big("${all.size}", "${if (all.size == 1) "season" else "seasons"} in charge · $span"),
            RecapBlock.Figures(listOf("Played" to "${split.played}", "Won" to "${split.won}", "Drawn" to "${split.drawn}", "Lost" to "${split.lost}")),
            RecapBlock.Rule,
            RecapBlock.Line("Win rate", "${if (split.played > 0) Math.round(100.0 * split.won / split.played) else 0}%").takeIf { split.played > 0 },
            RecapBlock.Line("Goals", "${split.goalsFor}–${split.goalsAgainst}").takeIf { split.played > 0 },
            scorers.firstOrNull()?.let { RecapBlock.Line("All-time top scorer", "${it.player.displayName} · ${it.goals}") },
            bestFinish?.let { (s, p) -> RecapBlock.Line("Best finish", "${ord(p)} · ${s.league} ${SeasonView.seasonLabel(s.year)}") },
            RecapBlock.Form(SeasonFacts.form(matches, 10), "Swipe for more →").takeIf { matches.isNotEmpty() }
        ))

        // 2 Season by season
        if (all.isNotEmpty()) {
            pages += RecapPage(2, PAGE_NAMES[1], "Season by season", buildList {
                all.takeLast(11).forEach { s ->
                    val sp = SeasonFacts.split(s.matches)
                    val result = when {
                        s.current && s.position != null -> "${ord(s.position)} so far"
                        s.current -> "in progress"
                        s.position != null -> ord(s.position) + (s.points?.let { " · $it pts" } ?: "")
                        else -> "${sp.won}-${sp.drawn}-${sp.lost}"
                    }
                    add(RecapBlock.Line("${SeasonView.seasonLabel(s.year)} · ${s.league}", result))
                }
                add(RecapBlock.Rule)
                add(RecapBlock.Figures(listOf("Seasons" to "${all.size}", "League titles" to "$leagueTitles", "Best" to (bestFinish?.let { ord(it.second) } ?: "–"))))
            })
        }

        // 3 Trophies
        if (trophies.isNotEmpty()) {
            pages += RecapPage(3, PAGE_NAMES[2], "Trophies", buildList {
                add(RecapBlock.Big("${trophies.size}", if (trophies.size == 1) "trophy won" else "trophies won"))
                add(RecapBlock.Figures(listOf("League" to "$leagueTitles", "Cups" to "$cups", "Europe" to "$continental")))
                add(RecapBlock.Rule)
                trophies.takeLast(8).reversed().forEach { t ->
                    val season = Regex("""(19|20)\d{2}/\d{2}""").find(t)?.value.orEmpty()
                    add(RecapBlock.Line(t.replace(season, "").replace("()", "").trim().ifBlank { t }, season))
                }
            })
        }

        // 4 Records
        if (matches.isNotEmpty()) {
            val home = SeasonFacts.home(matches)
            val away = SeasonFacts.away(matches)
            fun run(r: Run?) = if (r == null || r.length < 2) "–" else "${r.length} matches"
            pages += RecapPage(4, PAGE_NAMES[3], "Records", listOfNotNull(
                SeasonFacts.bestWin(matches)?.let { RecapBlock.Line("Biggest win", vs(it)) },
                SeasonFacts.heaviestDefeat(matches)?.let { RecapBlock.Line("Heaviest defeat", vs(it)) },
                RecapBlock.Line("Longest unbeaten run", run(SeasonFacts.longestUnbeaten(matches))),
                RecapBlock.Line("Longest winning run", run(SeasonFacts.longestWinning(matches))),
                RecapBlock.Line("Clean sheets", "${SeasonFacts.cleanSheets(matches)}"),
                RecapBlock.Rule,
                RecapBlock.Line("At home", "${home.won} won · ${home.drawn} drawn · ${home.lost} lost"),
                RecapBlock.Line("Away", "${away.won} won · ${away.drawn} drawn · ${away.lost} lost"),
                RecapBlock.Line("Goals per match", "%.2f".format(java.util.Locale.US, split.goalsFor.toDouble() / split.played.coerceAtLeast(1)))
            ))
        }

        // 5 Hall of fame
        if (scorers.isNotEmpty() || apps.isNotEmpty()) {
            pages += RecapPage(5, PAGE_NAMES[4], "Hall of fame", buildList {
                if (scorers.isNotEmpty()) {
                    add(RecapBlock.Label("All-time top scorers"))
                    scorers.take(3).forEachIndexed { i, t -> add(RecapBlock.Person(t.player.displayName, "${t.appearances} apps · ${t.assists} assists", "${t.goals}", i + 1, t.player.headshotUrl)) }
                }
                if (apps.isNotEmpty()) {
                    add(RecapBlock.Rule)
                    add(RecapBlock.Label("Most appearances"))
                    apps.take(2).forEachIndexed { i, t -> add(RecapBlock.Person(t.player.displayName, "${t.goals} goals", "${t.appearances}", i + 1, t.player.headshotUrl)) }
                }
                assisters.firstOrNull()?.let { add(RecapBlock.Rule); add(RecapBlock.Line("Most assists", "${it.player.displayName} · ${it.assists}")) }
            })
        }

        // 6 Best players
        val rated = totals.filter { it.averageRating > 0 && it.appearances >= maxOf(10, matches.size / 10) }.maxByOrNull { it.averageRating }
            ?: totals.filter { it.averageRating > 0 }.maxByOrNull { it.averageRating }
        if (rated != null) {
            val motm = totals.filter { it.motm > 0 }.maxByOrNull { it.motm }
            val keeper = totals.filter { it.player.primaryPosition.equals("GK", true) && it.cleanSheets > 0 }.maxByOrNull { it.cleanSheets }
            val graduates = (state.players + state.historicalSeasons.flatMap { it.players }).filter { it.isPromoted }.distinctBy { it.gamePlayerId?.toString() ?: it.name.lowercase() }
            pages += RecapPage(6, PAGE_NAMES[5], "Best players", listOfNotNull(
                RecapBlock.Person(rated.player.displayName, "${f1(rated.averageRating)} average over ${rated.appearances} matches", "", headshotUrl = rated.player.headshotUrl, big = true),
                RecapBlock.Rule,
                RecapBlock.Line("Best average rating", "${rated.player.displayName} · ${f1(rated.averageRating)}"),
                motm?.let { RecapBlock.Line("Most man of the match", "${it.player.displayName} · ${it.motm}") },
                keeper?.let { RecapBlock.Line("Best goalkeeper", "${it.player.displayName} · ${it.cleanSheets} clean sheets") },
                RecapBlock.Line("Academy graduates", "${graduates.size}${if (graduates.isNotEmpty()) " · " + graduates.take(2).joinToString(", ") { it.displayName } else ""}").takeIf { graduates.isNotEmpty() }
            ))
        }

        // 7 Transfers
        val deals = (state.historicalSeasons.flatMap { it.transfers } + state.transfers).distinctBy { it.id }
        if (deals.isNotEmpty()) {
            val ins = deals.filter { it.isIncomingDeal() }
            val outs = deals.filter { !it.isIncomingDeal() && it.type != "Retirement" }
            val spent = ins.sumOf { it.feeM }
            val got = outs.sumOf { it.feeM }
            val net = got - spent
            fun m(x: Double) = "$cur${f1(x)}M"
            val bigIn = ins.filter { it.feeM > 0 }.maxByOrNull { it.feeM }
            val bigOut = outs.filter { it.feeM > 0 }.maxByOrNull { it.feeM }
            val photo: (String) -> String = { n -> (state.players + state.historicalSeasons.flatMap { it.players }).firstOrNull { it.name.equals(n, true) || it.displayName.equals(n, true) }?.headshotUrl.orEmpty() }
            pages += RecapPage(7, PAGE_NAMES[6], "Transfers", listOfNotNull(
                RecapBlock.Figures(listOf("In" to "${ins.size}", "Out" to "${outs.size}", "Spent" to m(spent), "Received" to m(got))),
                RecapBlock.Line("Net spend", (if (net >= 0) "+" else "−") + m(kotlin.math.abs(net))),
                RecapBlock.Rule,
                bigIn?.let { RecapBlock.Label("Record signing") },
                bigIn?.let { t -> RecapBlock.Person(t.playerName, "from ${t.fromToClub}", t.feeLabel(cur), headshotUrl = t.headshotUrl.ifBlank { photo(t.playerName) }) },
                bigOut?.let { RecapBlock.Label("Record sale") },
                bigOut?.let { t -> RecapBlock.Person(t.playerName, "to ${t.fromToClub}", t.feeLabel(cur), headshotUrl = t.headshotUrl.ifBlank { photo(t.playerName) }) }
            ))
        }
        return pages
    }
}
