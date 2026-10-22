package com.fctracker.career.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.AppState
import com.fctracker.career.MainViewModel
import com.fctracker.career.MatchResult
import com.fctracker.career.Player
import com.fctracker.career.feeLabel
import com.fctracker.career.isIncomingDeal
import com.fctracker.career.logic.Dates
import com.fctracker.career.logic.Milestones
import com.fctracker.career.logic.RecapBlock
import com.fctracker.career.logic.RecapPage
import com.fctracker.career.logic.SeasonFacts
import com.fctracker.career.logic.SeasonView
import com.fctracker.career.ui.components.ButtonKind
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.FcCard
import com.fctracker.career.ui.components.FcDialog
import com.fctracker.career.ui.components.FcLabel
import com.fctracker.career.ui.components.FcPage
import com.fctracker.career.ui.components.FcSelectField
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.FcTopBar
import com.fctracker.career.ui.components.Figure
import com.fctracker.career.ui.components.HRule
import com.fctracker.career.ui.components.Headshot
import com.fctracker.career.ui.components.KeyValue
import com.fctracker.career.ui.components.Mono
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.OvrBadge
import com.fctracker.career.ui.components.SectionHeader
import com.fctracker.career.ui.components.StatTile
import com.fctracker.career.ui.components.Tag
import com.fctracker.career.ui.components.Tone
import com.fctracker.career.ui.recap.RecapRenderer
import com.fctracker.career.ui.recap.RecapShare
import com.fctracker.career.ui.theme.FcTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One player over the whole career (or one season): appearances, goals, assists and average rating. */
data class CareerTotals(val player: Player, val apps: Int, val goals: Int, val assists: Int, val average: Double)

object CareerFacts {
    /** Every season's totals added up by player; [season] = null for all-time, else one season (current or archived). */
    fun totals(state: AppState, season: Int? = null): List<CareerTotals> {
        val seasons = buildList {
            state.historicalSeasons.forEach { h -> if (season == null || season == h.year) add(Triple(h.players + h.academyPlayers, h.matches, h.squadStats)) }
            if (season == null || season == state.startYear) add(Triple(state.players + state.academyPlayers, state.recentMatches, state.squadStats))
        }
        val acc = linkedMapOf<String, CareerTotals>()
        val weighted = mutableMapOf<String, Pair<Double, Int>>()
        for ((players, matches, stats) in seasons) {
            for (t in SeasonFacts.allTotals(players, matches, stats)) {
                if (t.appearances == 0 && t.goals == 0 && t.assists == 0) continue
                val key = t.player.gamePlayerId?.let { "g$it" } ?: t.player.id
                val prev = acc[key]
                acc[key] = CareerTotals(t.player, (prev?.apps ?: 0) + t.appearances, (prev?.goals ?: 0) + t.goals, (prev?.assists ?: 0) + t.assists, 0.0)
                if (t.averageRating > 0 && t.appearances > 0) {
                    val w = weighted[key] ?: (0.0 to 0)
                    weighted[key] = (w.first + t.averageRating * t.appearances) to (w.second + t.appearances)
                }
            }
        }
        // The latest version of each player (name, rating, status) is the one shown.
        val latest = (state.historicalSeasons.sortedBy { it.year }.flatMap { it.players + it.academyPlayers } + state.players + state.academyPlayers)
            .associateBy { it.gamePlayerId?.let { g -> "g$g" } ?: it.id }
        return acc.map { (k, v) -> v.copy(player = latest[k] ?: v.player, average = weighted[k]?.let { it.first / it.second } ?: 0.0) }
    }

    fun allMatches(state: AppState): List<MatchResult> = state.historicalSeasons.flatMap { it.matches } + state.recentMatches

    /** The season label of a match ("2028/29"): the archived season that holds it, else the current one. */
    fun seasonOf(state: AppState, m: MatchResult): String =
        SeasonView.seasonLabel(state.historicalSeasons.firstOrNull { h -> h.matches.any { it.id == m.id } }?.year ?: state.startYear)

    /** Every trophy won, once each (the trophy list plus the trophies of archived seasons). */
    fun trophies(state: AppState): List<String> = (state.teamTrophies.map { it.name } + state.historicalSeasons.flatMap { it.trophies }).map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

    fun isContinental(t: String) = listOf("champions league", "europa", "conference", "uefa", "libertadores", "sudamericana", "club world").any { t.lowercase().contains(it) }
    fun isCup(t: String) = !isContinental(t) && listOf("cup", "copa", "coppa", "pokal", "coupe", "shield", "trophy", "trophée", "supercopa", "supercoppa", "supercup", "play-off", "playoff").any { t.lowercase().contains(it) }

    /** League titles: seasons finished first, or league trophies on the list, whichever is more. */
    fun leagueTitles(state: AppState): Int {
        val won = state.historicalSeasons.count { it.finalPosition == 1 }
        val listed = trophies(state).count { !isContinental(it) && !isCup(it) }
        return maxOf(won, listed)
    }

    /** The plain-text summary to share or keep. */
    fun exportText(state: AppState): String {
        val all = SeasonFacts.split(allMatches(state))
        val sb = StringBuilder()
        sb.appendLine("${state.managerName} · ${state.clubName}")
        sb.appendLine("Career summary (${SeasonView.seasonLabel(state.historicalSeasons.minOfOrNull { it.year } ?: state.startYear)} to ${SeasonView.seasonLabel(state.startYear)})")
        sb.appendLine()
        sb.appendLine("All-time record: ${all.won} won, ${all.drawn} drawn, ${all.lost} lost (${all.goalsFor}–${all.goalsAgainst})")
        sb.appendLine("League titles ${leagueTitles(state)} · Domestic cups ${trophies(state).count { isCup(it) }} · Continental ${trophies(state).count { isContinental(it) }}")
        val tr = trophies(state)
        if (tr.isNotEmpty()) sb.appendLine("Trophies: ${tr.joinToString(", ")}")
        sb.appendLine()
        sb.appendLine("Season by season")
        state.historicalSeasons.sortedBy { it.year }.forEach { h ->
            val deals = h.transfers.filter { it.feeM > 0 }.sortedByDescending { it.feeM }.take(2).joinToString("; ") { "${it.playerName} ${if (it.isIncomingDeal()) "from" else "to"} ${it.fromToClub} ${it.feeLabel(state.currencySymbol)}" }
            sb.appendLine("${SeasonView.seasonLabel(h.year)} · ${h.leagueName}: ${Milestones.ordinal(h.finalPosition)}${if (h.trophies.isNotEmpty()) " · ${h.trophies.joinToString(", ")}" else ""}${if (deals.isNotEmpty()) " · $deals" else ""}")
        }
        sb.appendLine("${SeasonView.seasonLabel(state.startYear)} · ${state.leagueName}: in progress")
        sb.appendLine()
        sb.appendLine("Hall of fame")
        totals(state).sortedByDescending { it.apps }.take(10).forEach { sb.appendLine("${it.player.displayName}: ${it.apps} apps, ${it.goals} goals, ${it.assists} assists") }
        sb.appendLine()
        sb.append("Made with FC Tracker")
        return sb.toString()
    }
}

@Composable
fun CareerSummaryScreen(viewModel: MainViewModel, onMenuClick: () -> Unit, openPlayer: (String) -> Unit, nav: (String) -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val c = FcTheme.colors
    var hofSeason by remember { mutableStateOf<Int?>(null) }
    var recordSeason by remember { mutableStateOf<Int?>(null) }
    var sort by remember { mutableStateOf(1) }
    var card by remember { mutableStateOf(false) }
    val allTime = remember(state) { CareerFacts.totals(state) }
    val hof = remember(state, hofSeason) { if (hofSeason == null) allTime else CareerFacts.totals(state, hofSeason) }
    val records = remember(state, recordSeason) { if (recordSeason == null) allTime else CareerFacts.totals(state, recordSeason) }
    val seasonOptions = listOf("All-time", "${SeasonView.seasonLabel(state.startYear)} (current)") + state.historicalSeasons.sortedByDescending { it.year }.map { SeasonView.seasonLabel(it.year) }
    fun seasonOf(label: String): Int? = when {
        label == "All-time" -> null
        label.endsWith("(current)") -> state.startYear
        else -> label.take(4).toIntOrNull()
    }
    fun labelOf(y: Int?): String = when (y) { null -> "All-time"; state.startYear -> "${SeasonView.seasonLabel(y)} (current)"; else -> SeasonView.seasonLabel(y) }
    val matches = CareerFacts.allMatches(state)
    val all = SeasonFacts.split(matches)
    val trophies = CareerFacts.trophies(state)

    FcPage(header = { FcTopBar("Career Summary", sub = "${state.clubName} · ${state.managerName}", onNav = onMenuClick) }) {
        item { SectionHeader("Season-by-season timeline", sub = if (state.historicalSeasons.isNotEmpty()) "Tap a season to open it (read-only)" else null, icon = Icons.Default.History) }
        if (state.historicalSeasons.isEmpty()) item { Muted("No completed seasons yet. Completed seasons and their top transfers will appear here.", 13.sp) }
        state.historicalSeasons.sortedBy { it.year }.forEachIndexed { i, h ->
            item(key = "season-${h.year}-$i") {
                val gold = h.finalPosition == 1 || h.trophies.isNotEmpty()
                FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp, onClick = { viewModel.setViewingSeasonYear(h.year); nav("dashboard") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            FcText("${SeasonView.seasonLabel(h.year)} · ${h.leagueName}", 15.sp, FontWeight.Bold)
                            val note = when {
                                h.finalPosition == 1 -> " (Champions)"
                                else -> ""
                            }
                            FcText("Finished ${Milestones.ordinal(h.finalPosition)}$note", 13.sp, FontWeight.SemiBold, if (gold) c.warn else c.text)
                            if (h.trophies.isNotEmpty()) Muted(h.trophies.joinToString(" · "), 12.sp)
                        }
                        if (gold) Icon(Icons.Default.EmojiEvents, null, tint = c.warn, modifier = Modifier.size(24.dp))
                    }
                    // one line per player and direction: a deal the game and the person both recorded is shown once
                    val deals = h.transfers.filter { it.type != "Retirement" }
                        .groupBy { com.fctracker.career.logic.PlayerCards.normalize(com.fctracker.career.logic.PlayerNames.shown(it.playerName)) + "|" + it.isIncomingDeal() }
                        .map { (_, same) -> same.maxByOrNull { it.feeM }!! }
                        .sortedByDescending { it.feeM }.take(3)
                    if (deals.isNotEmpty()) {
                        FcLabel("TOP TRANSFERS & KEY DEALS")
                        deals.forEach { t ->
                            val inc = t.isIncomingDeal()
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Tag(if (inc) "SIGNING" else "SALE", if (inc) Tone.INFO else Tone.GOOD)
                                FcText(com.fctracker.career.logic.PlayerNames.shown(t.playerName) + if (t.fromToClub.isNotBlank() && t.fromToClub != "Transfer Market") " (${t.fromToClub})" else "", 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                FcText(t.feeLabel(state.currencySymbol), 12.sp, FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                SectionHeader("Overall manager statistics", icon = Icons.Default.ShowChart)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("League titles", "${CareerFacts.leagueTitles(state)}", Modifier.weight(1f), size = 26.sp)
                    StatTile("Domestic cups", "${trophies.count { CareerFacts.isCup(it) }}", Modifier.weight(1f), size = 26.sp)
                    StatTile("Continental", "${trophies.count { CareerFacts.isContinental(it) }}", Modifier.weight(1f), size = 26.sp)
                }
                KeyValue("Matches", "${all.played}")
                KeyValue("Wins", "${all.won}", c.good)
                KeyValue("Draws", "${all.drawn}", c.warn)
                KeyValue("Losses", "${all.lost}", c.bad)
                KeyValue("Goals", "${all.goalsFor}–${all.goalsAgainst}")
                if (all.played > 0) KeyValue("Win rate", "${(all.won * 100 / all.played)}%")
                SeasonFacts.bestWin(matches)?.let { BiggestResult("Biggest win", it, state, c.good) }
                SeasonFacts.heaviestDefeat(matches)?.let { BiggestResult("Biggest loss", it, state, c.bad) }
                FcLabel("ALL-TIME HOME VS AWAY")
                HomeAwayTable(SeasonFacts.home(matches), SeasonFacts.away(matches))
            }
        }
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                SectionHeader("Hall of fame (best players)", icon = Icons.Default.Star)
                FcSelectField("Season", labelOf(hofSeason), seasonOptions, { hofSeason = seasonOf(it) })
                val w = listOf(1f, 0.26f, 0.24f, 0.24f, 0.28f)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
                    listOf("NAME", "APPS", "GLS", "AST", "AVG").forEachIndexed { i, h ->
                        // the number columns are right-aligned, as their figures are
                        Text(h, Modifier.weight(w[i]).clickable { sort = i }, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp,
                            color = if (sort == i) c.accent else c.muted, fontFamily = com.fctracker.career.ui.theme.PlexMono, maxLines = 1,
                            textAlign = if (i == 0) TextAlign.Start else TextAlign.End)
                    }
                }
                HRule()
                val rows = when (sort) {
                    0 -> hof.sortedBy { it.player.displayName }
                    2 -> hof.sortedByDescending { it.goals }
                    3 -> hof.sortedByDescending { it.assists }
                    4 -> hof.filter { it.apps >= 5 }.sortedByDescending { it.average }
                    else -> hof.sortedByDescending { it.apps }
                }.take(10)
                if (rows.isEmpty()) Muted("No appearances recorded yet.", 12.sp)
                rows.forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable { openPlayer(t.player.id) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        FcText(t.player.displayName, 13.sp, FontWeight.SemiBold, modifier = Modifier.weight(w[0]), maxLines = 1)
                        Mono("${t.apps}", 13.sp, modifier = Modifier.weight(w[1]), align = TextAlign.End)
                        Mono("${t.goals}", 13.sp, modifier = Modifier.weight(w[2]), align = TextAlign.End)
                        Mono("${t.assists}", 13.sp, modifier = Modifier.weight(w[3]), align = TextAlign.End)
                        Mono(if (t.average > 0) String.format(java.util.Locale.US, "%.1f", t.average) else "–", 13.sp, modifier = Modifier.weight(w[4]), align = TextAlign.End)
                    }
                    HRule()
                }
                Muted("Average rating counts players with 5 or more appearances.", 11.sp)
            }
        }
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                SectionHeader("Club milestones & record holders", icon = Icons.Default.TrackChanges)
                FcSelectField("Season", labelOf(recordSeason), seasonOptions, { recordSeason = seasonOf(it) })
                val pool = if (recordSeason == null) state.transfers + state.historicalSeasons.flatMap { it.transfers }
                    else if (recordSeason == state.startYear) state.transfers else state.historicalSeasons.firstOrNull { it.year == recordSeason }?.transfers.orEmpty()
                records.maxByOrNull { it.apps }?.takeIf { it.apps > 0 }?.let { KeyValue("Most appearances", "${it.player.displayName} · ${it.apps}", mono = false) }
                records.maxByOrNull { it.goals }?.takeIf { it.goals > 0 }?.let { KeyValue(if (recordSeason == null) "All-time top scorer" else "Top scorer", "${it.player.displayName} · ${it.goals}", mono = false) }
                records.maxByOrNull { it.assists }?.takeIf { it.assists > 0 }?.let { KeyValue(if (recordSeason == null) "All-time top assists" else "Top assists", "${it.player.displayName} · ${it.assists}", mono = false) }
                records.filter { it.apps >= 5 }.maxByOrNull { it.average }?.takeIf { it.average > 0 }?.let { KeyValue("Best average rating", "${it.player.displayName} · ${String.format(java.util.Locale.US, "%.1f", it.average)}", mono = false) }
                pool.filter { it.isIncomingDeal() && it.feeM > 0 }.maxByOrNull { it.feeM }?.let { KeyValue("Record signing", "${it.playerName} · ${it.feeLabel(state.currencySymbol)}", mono = false) }
                pool.filter { !it.isIncomingDeal() && it.type != "Retirement" && it.feeM > 0 }.maxByOrNull { it.feeM }?.let { KeyValue("Record sale", "${it.playerName} · ${it.feeLabel(state.currencySymbol)}", mono = false) }
                SeasonFacts.bestWin(if (recordSeason == null) matches else if (recordSeason == state.startYear) state.recentMatches else state.historicalSeasons.firstOrNull { it.year == recordSeason }?.matches.orEmpty())?.let {
                    KeyValue("Biggest win", "${it.goalsFor}–${it.goalsAgainst} ${if (it.isHome) "vs" else "at"} ${it.opponentName}", mono = false)
                }
                val ms = (state.historicalSeasons.filter { recordSeason == null || it.year == recordSeason }.flatMap { it.milestones } + if (recordSeason == null || recordSeason == state.startYear) state.milestones else emptyList()).takeLast(5)
                if (ms.isNotEmpty()) {
                    HRule()
                    ms.reversed().forEach { m -> KeyValue(m.playerName.ifBlank { state.clubName }, m.title.substringAfter(": "), mono = false) }
                }
            }
        }
        item { SectionHeader("All-time squad list", sub = "Tap a player to open his profile", icon = Icons.Default.Groups) }
        item {
            RowsCard {
                // strikers first, goalkeepers last; within a position the most appearances first
                val list = allTime.sortedWith(compareBy<CareerTotals>({ LineupOrder.rank(it.player.primaryPosition) }, { LineupOrder.side(it.player.primaryPosition) })
                    .thenByDescending { it.apps })
                val here = (state.players + state.academyPlayers).flatMap { listOfNotNull(it.id, it.gamePlayerId?.let { g -> "g$g" }) }.toSet()
                if (list.isEmpty()) Muted("Nobody has played yet.", 12.sp, Modifier.padding(14.dp))
                list.forEachIndexed { i, t ->
                    if (i > 0) RowDivider()
                    val p = t.player
                    val gone = p.id !in here && (p.gamePlayerId == null || "g${p.gamePlayerId}" !in here)
                    val leftTag = when {
                        p.status.equals("Sold", true) -> "Sold"
                        p.status.equals("Retired", true) -> "Retired"
                        p.status.equals("Released", true) -> "Released"
                        p.status.equals("Loaned Out", true) || p.isLoanedOut || p.loanDirection == "out" -> "On loan"
                        gone -> "Left the club"
                        else -> null
                    }
                    Row(Modifier.fillMaxWidth().clickable { openPlayer(p.id) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Headshot(p.headshotUrl, p.displayName, 42.dp, youth = p.isPromoted)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                FcText(p.displayName, 14.sp, FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                if (p.isPromoted) Tag("Academy", Tone.GOOD)
                                if (leftTag != null) Tag(leftTag, if (leftTag == "On loan") Tone.INFO else Tone.MUTED)
                            }
                            Muted("${p.primaryPosition} · ${t.apps} apps · ${t.goals} goals · ${t.assists} assists", 12.sp)
                        }
                        OvrBadge(p.overall, 36.dp)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FcButton("Export career summary text", {
                    val text = CareerFacts.exportText(state)
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "${state.clubName} career summary"); putExtra(Intent.EXTRA_TEXT, text) }
                    context.startActivity(Intent.createChooser(send, "Career summary"))
                }, kind = ButtonKind.SECONDARY, icon = Icons.Default.Download, full = true)
                FcButton("Career recap pages", { nav("career_recap") }, icon = Icons.Default.Share, full = true)
            }
        }
    }

    if (card) CareerCardDialog(state, all, onDismiss = { card = false })
}

/** "Biggest win: 5–0 vs Leeds United (home) · 12 Oct 2028 · 2028/29 · EFL Championship". */
@Composable
private fun BiggestResult(label: String, m: MatchResult, state: AppState, tint: androidx.compose.ui.graphics.Color) {
    val c = FcTheme.colors
    val season = CareerFacts.seasonOf(state, m)
    val league = com.fctracker.career.logic.Competitions.isLeagueFn(state.leagueName)(m.competition)
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FcText(label, 13.sp, color = c.muted, modifier = Modifier.weight(1f))
            Figure("${m.goalsFor}–${m.goalsAgainst}", 18.sp, tint, FontWeight.SemiBold)
        }
        FcText("${if (m.isHome) "vs" else "at"} ${m.opponentName} (${if (m.isHome) "home" else "away"})", 14.sp, FontWeight.SemiBold, maxLines = 1)
        Muted(listOf(Dates.full(m.matchDate), season, m.competition + (if (league) " (league)" else if (m.stage.isNotBlank()) " · ${m.stage}" else "")).filter { it.isNotBlank() }.joinToString(" · "), 12.sp)
    }
}

/** The career card: an image in the club's colours with the record and the trophies, to share. */
@Composable
private fun CareerCardDialog(state: AppState, all: com.fctracker.career.logic.Split, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = FcTheme.colors
    val trophies = CareerFacts.trophies(state)
    val page = remember(state) {
        RecapPage(1, "Career", state.managerName, listOfNotNull(
            RecapBlock.Label("Career summary"),
            RecapBlock.Big("${all.won}W ${all.drawn}D ${all.lost}L", "${state.clubName} manager · ${state.historicalSeasons.size + 1} seasons"),
            RecapBlock.Figures(listOf("League titles" to "${CareerFacts.leagueTitles(state)}", "Cups" to "${trophies.count { CareerFacts.isCup(it) }}", "Europe" to "${trophies.count { CareerFacts.isContinental(it) }}")),
            RecapBlock.Rule,
            RecapBlock.Line("Squad value", "${state.currencySymbol}${String.format(java.util.Locale.US, "%.1f", state.squadValue)}M"),
            RecapBlock.Line("Goals", "${all.goalsFor}–${all.goalsAgainst}"),
            state.historicalSeasons.maxByOrNull { it.year }?.let { RecapBlock.Line("Last season", "${Milestones.ordinal(it.finalPosition)} in the ${it.leagueName}") },
            trophies.takeIf { it.isNotEmpty() }?.let { RecapBlock.Line("Trophies", it.take(4).joinToString(", ")) },
            CareerFacts.totals(state).maxByOrNull { it.goals }?.takeIf { it.goals > 0 }?.let { RecapBlock.Person(it.player.displayName, "Top scorer of the career", "${it.goals}", headshotUrl = it.player.headshotUrl) }
        ))
    }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page) {
        val style = RecapRenderer.style(context, state.primaryColorHex, state.secondaryColorHex)
        val assets = withContext(Dispatchers.IO) { RecapRenderer.loadAssets(context, state, listOf(page)) }
        image = withContext(Dispatchers.Default) { RecapRenderer.render(page, 1, "${state.clubName} · ${SeasonView.seasonLabel(state.startYear)}", state.clubName, style, assets) }
    }
    FcDialog("Career card", onDismiss, actions = {
        FcButton("Close", onDismiss, kind = ButtonKind.SECONDARY, small = true)
        FcButton("Share", {
            val bmp = image ?: return@FcButton
            scope.launch {
                val uri = withContext(Dispatchers.IO) { RecapShare.clear(context); RecapShare.savePng(context, bmp, "career-card.png") }
                RecapShare.shareImages(context, listOf(uri), "${state.clubName} career card")
            }
        }, icon = Icons.Default.Share, small = true, enabled = image != null)
    }) {
        val bmp = image
        if (bmp == null) {
            Row(Modifier.fillMaxWidth().aspectRatio(0.8f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        } else {
            Image(bmp.asImageBitmap(), contentDescription = "Career card: ${state.managerName}, ${all.won} wins, ${all.drawn} draws, ${all.lost} losses", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)))
        }
    }
}
