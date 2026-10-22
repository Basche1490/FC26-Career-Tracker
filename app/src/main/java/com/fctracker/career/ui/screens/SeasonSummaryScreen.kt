package com.fctracker.career.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.AppState
import com.fctracker.career.AwardEntry
import com.fctracker.career.ClubCoefficient
import com.fctracker.career.CompetitionLeaders
import com.fctracker.career.LeagueTableData
import com.fctracker.career.MainViewModel
import com.fctracker.career.Platform
import com.fctracker.career.Source
import com.fctracker.career.StatLeader
import com.fctracker.career.TableRow
import com.fctracker.career.TeamTrophy
import com.fctracker.career.feeLabel
import com.fctracker.career.isIncomingDeal
import com.fctracker.career.logic.CareerOps
import com.fctracker.career.logic.Competitions
import com.fctracker.career.logic.CupRun
import com.fctracker.career.logic.CupRuns
import com.fctracker.career.logic.Dates
import com.fctracker.career.logic.LeagueTables
import com.fctracker.career.logic.Milestones
import com.fctracker.career.logic.RoundState
import com.fctracker.career.logic.SeasonFacts
import com.fctracker.career.logic.SeasonView
import com.fctracker.career.logic.ZoneKind
import com.fctracker.career.ui.components.ArchivedBanner
import com.fctracker.career.ui.components.ButtonKind
import com.fctracker.career.ui.components.CompLogo
import com.fctracker.career.ui.components.ConfirmDialog
import com.fctracker.career.ui.components.EmptyCard
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.FcCard
import com.fctracker.career.ui.components.FcDialog
import com.fctracker.career.ui.components.FcField
import com.fctracker.career.ui.components.FcIconButton
import com.fctracker.career.ui.components.FcLabel
import com.fctracker.career.ui.components.FcPage
import com.fctracker.career.ui.components.FcNumberField
import com.fctracker.career.ui.components.FcSelectField
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.FcTopBar
import com.fctracker.career.ui.components.FieldPair
import com.fctracker.career.ui.components.Figure
import com.fctracker.career.ui.components.FormDots
import com.fctracker.career.ui.components.FormStrip
import com.fctracker.career.ui.components.HRule
import com.fctracker.career.ui.components.Headshot
import com.fctracker.career.ui.components.IconTile
import com.fctracker.career.ui.components.KeyValue
import com.fctracker.career.ui.components.Mono
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.Notice
import com.fctracker.career.ui.components.ProgressBar
import com.fctracker.career.ui.components.SectionHeader
import com.fctracker.career.ui.components.SoftTabs
import com.fctracker.career.ui.components.StatTile
import com.fctracker.career.ui.components.Stepper
import com.fctracker.career.ui.components.Tag
import com.fctracker.career.ui.components.Tone
import com.fctracker.career.ui.components.toneColors
import com.fctracker.career.ui.theme.FcTheme

val SEASON_TABS = listOf("Overview", "Table", "Stats", "Awards", "Europe")

/** Season Summary: the season so far (or an archived one), the league table, statistics, awards and the European ranking. */
@Composable
fun SeasonSummaryScreen(viewModel: MainViewModel, onMenuClick: () -> Unit, nav: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val health by viewModel.health.collectAsState()
    val view = remember(state) { SeasonView.of(state) }
    val console = state.platform == Platform.CONSOLE
    var tab by remember { mutableStateOf(0) }
    var showClubs by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var afterEnd by remember { mutableStateOf<Int?>(null) }
    FcPage(header = {
        if (view.isArchived) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { ArchivedBanner("${view.label} · ${view.leagueName}", { viewModel.setViewingSeasonYear(null) }) }
        FcTopBar(
            "Season Summary",
            sub = when {
                showClubs && tab == 1 -> "${SeasonView.seasonLabel(state.startYear)} · clubs in the league"
                view.isArchived -> "${view.label} · final"
                else -> "${view.label} (current)"
            },
            onNav = onMenuClick,
            pill = { if (!view.isArchived && !console) StatusPill(health.level, health.pill, onClick = { nav("connection") }) }
        )
        SoftTabs(SEASON_TABS, tab, { tab = it; showClubs = false }, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
    }) {
        when (tab) {
            0 -> overviewTab(state, view, nav, onEnd = { confirmEnd = true })
            1 -> if (showClubs && !view.isArchived) clubsEditor(viewModel, state, onDone = { showClubs = false }) else tableTab(viewModel, state, view, console, onClubs = { showClubs = true })
            2 -> statsTab(viewModel, state, view, console, nav)
            3 -> awardsTab(viewModel, state, view)
            4 -> europeTab(viewModel, state, view, console)
        }
    }

    if (confirmEnd) {
        FcDialog("Start new season?", { confirmEnd = false }, actions = {
            FcButton("Cancel", { confirmEnd = false }, kind = ButtonKind.SECONDARY, small = true)
            FcButton("Yes, start new season", { val y = state.startYear; viewModel.endSeason(); confirmEnd = false; afterEnd = y }, small = true)
        }) { Muted("This will archive the current season and begin a fresh one. Make sure you have played all matches first. Are you sure?", 13.sp) }
    }
    afterEnd?.let { y ->
        FcDialog("${SeasonView.seasonLabel(y)} is archived", { afterEnd = null }, actions = {
            if (console) FcButton("Clubs for next season", { afterEnd = null; tab = 1; showClubs = true }, kind = ButtonKind.SECONDARY, small = true)
            FcButton("See the recap", { afterEnd = null; nav("recap/$y") }, small = true)
        }) {
            Muted("Every page of ${SeasonView.seasonLabel(y)} stays available: switch Home to Archived and pick it. Your season recap is ready to share.", 13.sp)
            if (console) Muted("On console, check who went up and down so next season's table has the right clubs.", 12.sp)
        }
    }
}

// =====================================================================================================================
// Overview
// =====================================================================================================================

private fun LazyListScope.overviewTab(state: AppState, view: SeasonView, nav: (String) -> Unit, onEnd: () -> Unit) {
    val isLeague = Competitions.isLeagueFn(view.leagueName)
    val league = view.matches.filter { isLeague(it.competition) }
    val table = view.ownTable?.let { LeagueTables.sorted(it.rows) }.orEmpty()
    val own = table.firstOrNull { it.isUser || it.club.equals(state.clubName, true) }
    item { LeagueCard(state, view, table, own, league) }
    val cups = CupRuns.all(view.matches, view.fixtures, isLeague)
    if (cups.isNotEmpty()) item {
        FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
            SectionHeader("Cup runs", icon = Icons.Default.EmojiEvents)
            cups.forEachIndexed { i, run -> if (i > 0) HRule(); CupRunView(run) }
            Muted("Rounds and draws come from the game${if (state.platform == Platform.CONSOLE) " (on console: from the fixtures and results you add)" else ""}.", 11.sp)
        }
    }
    item {
        val s = SeasonFacts.split(league)
        FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
            SectionHeader("Form & statistics", icon = Icons.Default.ShowChart)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Muted("Last 5", 13.sp, Modifier.weight(1f))
                FormStrip(SeasonFacts.form(view.matches, 5), 26.dp)
            }
            KeyValue("Wins", "${s.won}", FcTheme.colors.good)
            KeyValue("Draws", "${s.drawn}", FcTheme.colors.warn)
            KeyValue("Losses", "${s.lost}", FcTheme.colors.bad)
            KeyValue("Goals scored", "${s.goalsFor}")
            KeyValue("Goals conceded", "${s.goalsAgainst}")
            Muted("League matches. All competitions: ${SeasonFacts.split(view.matches).let { "${it.won}W ${it.drawn}D ${it.lost}L, ${it.goalsFor}–${it.goalsAgainst}" }}.", 11.sp)
        }
    }
    item {
        FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
            SectionHeader("Home vs away performance", icon = Icons.Default.ShowChart)
            HomeAwayTable(SeasonFacts.home(league), SeasonFacts.away(league))
        }
    }
    item {
        val ins = view.transfers.filter { it.isIncomingDeal() }
        val outs = view.transfers.filter { !it.isIncomingDeal() && it.type != "Retirement" }
        val cur = state.currencySymbol
        FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
            SectionHeader("Financial & transfer summary", icon = Icons.Default.SwapHoriz)
            KeyValue(if (view.isArchived) "Transfer budget at the end" else "Current transfer budget", "$cur${String.format(java.util.Locale.US, "%.1f", view.transferBudget)}M")
            KeyValue("Wage bill", "$cur${view.players.filter { isActivePlayer(it) }.sumOf { it.wageK }}K a week")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Signings", "${ins.size}", Modifier.weight(1f), sub = "Total spent $cur${String.format(java.util.Locale.US, "%.1f", ins.sumOf { it.feeM })}M", tone = Tone.BAD, size = 24.sp)
                StatTile("Sales", "${outs.size}", Modifier.weight(1f), sub = "Total received $cur${String.format(java.util.Locale.US, "%.1f", outs.sumOf { it.feeM })}M", tone = Tone.GOOD, size = 24.sp)
            }
        }
    }
    item {
        val top = view.transfers.sortedByDescending { it.feeM }.take(4)
        FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
            SectionHeader(if (view.isArchived) "Top transfers" else "Top transfers (current season)", icon = Icons.Default.SwapHoriz)
            if (top.isEmpty()) Muted("No transfers recorded for this season.", 12.sp)
            top.forEach { t -> TransferMiniRow(t, state, view) }
        }
    }
    item { FcButton("Season recap card", { nav(if (view.isArchived) "recap/${view.season}" else "recap") }, kind = ButtonKind.SECONDARY, icon = Icons.Default.Share, full = true) }
    if (!view.isArchived) item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FcButton("End season and start ${SeasonView.seasonLabel(state.startYear + 1)}", onEnd, kind = ButtonKind.SECONDARY, icon = Icons.Default.Refresh, full = true)
            Muted("Archives this season with every page (matches, squad, tables, stats, awards) and starts the next one.", 11.sp)
        }
    }
}

@Composable
private fun LeagueCard(state: AppState, view: SeasonView, table: List<TableRow>, own: TableRow?, league: List<com.fctracker.career.MatchResult>) {
    val isLeague = Competitions.isLeagueFn(view.leagueName)
    val split = SeasonFacts.split(league)
    val pos = table.indexOf(own).takeIf { it >= 0 }?.plus(1) ?: SeasonFacts.leaguePositions(view.matches, isLeague).lastOrNull() ?: view.archived?.finalPosition
    val clubs = table.size
    val total = if (clubs > 1) (clubs - 1) * 2 else 0
    val played = own?.played ?: split.played
    val gd = (own?.goalsFor ?: split.goalsFor) - (own?.goalsAgainst ?: split.goalsAgainst)
    val zone = if (pos != null && clubs > 0) LeagueTables.zoneOf(view.leagueName, clubs, pos) else null
    FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CompLogo(view.leagueName, 44.dp)
            Column {
                FcText(view.leagueName, 16.sp, FontWeight.Bold)
                Muted("${if (view.isArchived) "Final" else "Current"} position: ${pos?.let { Milestones.ordinal(it) } ?: "–"}${zone?.let { " · ${it.label}" } ?: ""}${if (!view.isArchived && total > 0) " · after $played of $total" else ""}", 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("PTS", "${own?.points ?: split.points}", Modifier.weight(1f), size = 26.sp)
            StatTile("MP", "$played", Modifier.weight(1f), size = 26.sp)
            StatTile("GD", (if (gd > 0) "+" else "") + gd, Modifier.weight(1f), tone = if (gd > 0) Tone.GOOD else if (gd < 0) Tone.BAD else null, size = 26.sp)
        }
    }
}

@Composable
fun CupRunView(run: CupRun) {
    val c = FcTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompLogo(run.competition, 30.dp)
            Column(Modifier.weight(1f)) {
                FcText(run.competition, 14.sp, FontWeight.SemiBold)
                FcText(run.status, 12.sp, FontWeight.Medium, when (run.tone) { "good" -> c.good; "bad" -> c.bad; "accent" -> c.accent; else -> c.muted })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            run.rounds.forEach { r ->
                val (fg, bg) = when (r.state) {
                    RoundState.WON -> toneColors(Tone.GOOD)
                    RoundState.LOST -> toneColors(Tone.BAD)
                    RoundState.NEXT -> c.accent to c.accentBg
                    RoundState.PLAYED -> toneColors(Tone.WARN)
                    RoundState.TODO -> c.muted to c.surface2
                }
                Box(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(15.dp)).background(bg), contentAlignment = Alignment.Center) {
                    Mono(r.code, 11.sp, fg, FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun HomeAwayTable(home: com.fctracker.career.logic.Split, away: com.fctracker.career.logic.Split) {
    val c = FcTheme.colors
    val widths = listOf(1f, 0.45f, 0.45f, 0.45f, 0.5f, 0.5f, 0.55f)
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            listOf("VENUE", "W", "D", "L", "GF", "GA", "PTS").forEachIndexed { i, h -> FcLabel(h, Modifier.weight(widths[i]), size = 11.sp) }
        }
        HRule()
        listOf("Home" to home, "Away" to away).forEach { (lab, s) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                FcText(lab, 13.sp, FontWeight.SemiBold, modifier = Modifier.weight(widths[0]))
                listOf(s.won, s.drawn, s.lost, s.goalsFor, s.goalsAgainst, s.points).forEachIndexed { i, v -> Mono("$v", 13.sp, modifier = Modifier.weight(widths[i + 1])) }
            }
            HRule()
        }
    }
}

@Composable
private fun TransferMiniRow(t: com.fctracker.career.TransferLog, state: AppState, view: SeasonView) {
    val c = FcTheme.colors
    val inc = t.isIncomingDeal()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Headshot(t.headshotUrl.ifBlank { view.players.firstOrNull { it.name.equals(t.playerName, true) || it.displayName.equals(t.playerName, true) }?.headshotUrl.orEmpty() }, t.playerName, 40.dp)
        Column(Modifier.weight(1f)) {
            FcText(t.playerName, 15.sp, FontWeight.SemiBold, maxLines = 1)
            Muted(if (t.type == "Retirement") "Retired from professional football" else "${t.type} ${if (inc) "from" else "to"} ${t.fromToClub}", 12.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            FcText(if (t.type == "Retirement") "Retired" else t.feeLabel(state.currencySymbol), 15.sp, FontWeight.Bold, if (t.type == "Retirement") c.muted else if (inc) c.bad else c.good)
            Muted(Dates.full(t.date), 11.sp)
        }
    }
}

// =====================================================================================================================
// Table
// =====================================================================================================================

private fun LazyListScope.tableTab(viewModel: MainViewModel, state: AppState, view: SeasonView, console: Boolean, onClubs: () -> Unit) {
    item { TableTabContent(viewModel, state, view, console, onClubs) }
}

@Composable
private fun TableTabContent(viewModel: MainViewModel, state: AppState, view: SeasonView, console: Boolean, onClubs: () -> Unit) {
    val own = view.ownTable ?: if (console && !view.isArchived) CareerOps.ownTable(state) else null
    val leagues = (listOfNotNull(own) + view.leagueTables.filter { it !== own }).distinctBy { it.league.lowercase() }
    var chosen by remember { mutableStateOf(own?.league ?: leagues.firstOrNull()?.league ?: "") }
    var editing by remember { mutableStateOf<TableRow?>(null) }
    var h2h by remember { mutableStateOf<String?>(null) }
    val table = leagues.firstOrNull { it.league == chosen } ?: own
    val editable = console && !view.isArchived
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (leagues.size > 1) FcSelectField("League", chosen, leagues.map { it.league }, { chosen = it }, hint = if (!console) "Any league in your game. The table comes from the game and updates after every round." else null)
        if (editable) {
            Notice(Tone.INFO, "On console you keep the table yourself", "Tap a club to change its numbers; just your own row and the clubs around you is enough.", icon = Icons.Default.Edit, actions = {
                FcButton("Change clubs", onClubs, kind = ButtonKind.SECONDARY, small = true)
                FcButton("Fill my row", { viewModel.fillOwnRowFromMatches() }, kind = ButtonKind.GHOST, small = true)
            })
        }
        if (table == null || table.rows.isEmpty()) {
            EmptyCard(if (console) "No table yet. Change clubs to add the clubs of your league." else "No table yet. It arrives from your game with the next report from the PC.")
        } else {
            LeagueTableView(table, state.clubName, onClub = { row -> if (editable && table.league == own?.league) editing = row else h2h = row.club })
            TableLegend(table)
            Muted(if (editable) "Played, points and goal difference are worked out from the other numbers." else "Tap a club to see its results against you. Form is the last 5 league matches, oldest first.", 11.sp)
        }
    }
    editing?.let { row -> RowEditDialog(row, onDismiss = { editing = null }, onSave = { viewModel.saveTableRow(table?.league ?: state.leagueName, row.club, it); editing = null }) }
    h2h?.let { club ->
        val results = LeagueTables.headToHead(view.matches, club)
        FcDialog(club, { h2h = null }, actions = { FcButton("Close", { h2h = null }, small = true) }) {
            if (results.isEmpty()) Muted("No matches against $club this season.", 13.sp)
            results.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.fctracker.career.ui.components.ResultBadge(m.resultBadge, 24.dp)
                    Column(Modifier.weight(1f)) {
                        FcText("${if (m.isHome) "vs" else "at"} ${m.opponentName}", 14.sp, FontWeight.SemiBold)
                        Muted("${m.competition} · ${Dates.full(m.matchDate)}", 12.sp)
                    }
                    Mono("${m.goalsFor}–${m.goalsAgainst}", 15.sp, weight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun LeagueTableView(table: LeagueTableData, clubName: String, onClub: ((TableRow) -> Unit)? = null) {
    val c = FcTheme.colors
    val rows = LeagueTables.sorted(table.rows)
    val w = listOf(0.09f, 0.45f, 0.08f, 0.12f, 0.12f, 0.2f)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface).padding(top = 12.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 13.dp, end = 10.dp, bottom = 8.dp)) {
            listOf("#", "CLUB", "P", "GD", "PTS", "FORM").forEachIndexed { i, h -> FcLabel(h, Modifier.weight(w[i]), size = 10.sp) }
        }
        HRule()
        rows.forEachIndexed { i, r ->
            val pos = i + 1
            val me = r.isUser || r.club.equals(clubName, true)
            val zone = LeagueTables.zoneOf(table.league, rows.size, pos)
            val band = when (zone) {
                ZoneKind.PROMOTION, ZoneKind.CHAMPIONS_LEAGUE, ZoneKind.CHAMPION -> c.good
                ZoneKind.PLAYOFF, ZoneKind.EUROPA_LEAGUE, ZoneKind.CONFERENCE_LEAGUE -> c.info
                ZoneKind.RELEGATION, ZoneKind.RELEGATION_PLAYOFF -> c.bad
                null -> Color.Transparent
            }
            Row(
                Modifier.fillMaxWidth().background(if (me) c.accentBg else Color.Transparent).let { m -> if (onClub != null) m.clickable { onClub(r) } else m }.height(38.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(band))
                Row(Modifier.weight(1f).padding(start = 10.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Mono("$pos", 13.sp, c.muted, FontWeight.Normal, Modifier.weight(w[0]))
                    Row(Modifier.weight(w[1]), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FcText(r.club, 13.sp, if (me) FontWeight.Bold else FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        if (r.movement == "promoted") Tag("Up", Tone.GOOD) else if (r.movement == "relegated") Tag("Down", Tone.BAD)
                    }
                    Mono("${r.played}", 13.sp, c.muted, FontWeight.Normal, Modifier.weight(w[2]), align = TextAlign.End)
                    Mono((if (r.goalDifference > 0) "+" else "") + r.goalDifference, 13.sp, modifier = Modifier.weight(w[3]), align = TextAlign.End)
                    Mono("${r.points}", 13.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(w[4]), align = TextAlign.End)
                    Box(Modifier.weight(w[5]), contentAlignment = Alignment.CenterEnd) { FormDots(r.form.takeLast(5)) }
                }
            }
            HRule()
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TableLegend(table: LeagueTableData) {
    val c = FcTheme.colors
    val kinds = LeagueTables.zonesFor(table.league, table.rows.size).map { it.kind }.distinct()
    if (kinds.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        kinds.forEach { k ->
            val col = when (k) {
                ZoneKind.PROMOTION, ZoneKind.CHAMPIONS_LEAGUE, ZoneKind.CHAMPION -> c.good
                ZoneKind.PLAYOFF, ZoneKind.EUROPA_LEAGUE, ZoneKind.CONFERENCE_LEAGUE -> c.info
                else -> c.bad
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(col))
                Muted(k.label, 11.sp)
            }
        }
    }
}

/** Console: one club's numbers. Played, points and goal difference follow from the rest. */
@Composable
private fun RowEditDialog(row: TableRow, onDismiss: () -> Unit, onSave: (TableRow) -> Unit) {
    var won by remember { mutableStateOf(row.won) }
    var drawn by remember { mutableStateOf(row.drawn) }
    var lost by remember { mutableStateOf(row.lost) }
    var gf by remember { mutableStateOf(row.goalsFor) }
    var ga by remember { mutableStateOf(row.goalsAgainst) }
    var form by remember { mutableStateOf(row.form) }
    var name by remember { mutableStateOf(row.club) }
    FcDialog("Edit ${row.club}", onDismiss, actions = {
        FcButton("Cancel", onDismiss, kind = ButtonKind.SECONDARY, small = true)
        FcButton("Save", { onSave(row.copy(club = name.trim().ifBlank { row.club }, won = won, drawn = drawn, lost = lost, goalsFor = gf, goalsAgainst = ga, form = form.uppercase().filter { it in "WDL" }.takeLast(5))) }, small = true)
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stepper("Won", won, { won = it }); Stepper("Drawn", drawn, { drawn = it }); Stepper("Lost", lost, { lost = it })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stepper("For", gf, { gf = it }); Stepper("Against", ga, { ga = it })
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Muted("Played", 11.sp); Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) { Mono("${won + drawn + lost}", 16.sp, weight = FontWeight.SemiBold) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Muted("Points", 13.sp); Spacer(Modifier.width(8.dp)); Mono("${won * 3 + drawn}", 20.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Muted("Goal difference", 13.sp); Spacer(Modifier.width(8.dp)); Mono((if (gf - ga > 0) "+" else "") + (gf - ga), 20.sp, weight = FontWeight.SemiBold)
        }
        FcField("Form (last 5, oldest first)", form, { form = it.uppercase().filter { ch -> ch in "WDL" }.take(5) }, placeholder = "WWDLW")
        FcField("Club name", name, { name = it }, hint = "Rename a club by changing its name here.")
    }
}

// -- console: the clubs in the league (promoted and relegated) ---------------------------------------------------------

private fun LazyListScope.clubsEditor(viewModel: MainViewModel, state: AppState, onDone: () -> Unit) {
    item { ClubsEditorContent(viewModel, state, onDone) }
}

@Composable
private fun ClubsEditorContent(viewModel: MainViewModel, state: AppState, onDone: () -> Unit) {
    val c = FcTheme.colors
    val own = CareerOps.ownTable(state)
    val lastSeason = state.historicalSeasons.maxByOrNull { it.year }
    val lastOwn = lastSeason?.leagueTables?.firstOrNull { it.league.equals(lastSeason.leagueName, true) }
    val lastBelow = lastSeason?.leagueTables?.firstOrNull { t -> LeagueTables.leagueBelow(lastSeason.leagueName)?.equals(t.league, true) == true }
    val lastAbove = lastSeason?.leagueTables?.firstOrNull { t -> LeagueTables.leagueAbove(lastSeason.leagueName)?.equals(t.league, true) == true }
    val proposal = lastOwn?.let { LeagueTables.proposeMovements(it, lastBelow, lastAbove) }
    val sameLeague = lastSeason != null && lastSeason.leagueName.equals(state.leagueName, true)
    fun inTable(name: String) = own.rows.any { it.club.equals(name, true) }
    val iWentUp = proposal != null && sameLeague && proposal.upFrom.any { it.equals(state.clubName, true) }
    val iWentDown = proposal != null && sameLeague && proposal.down.any { it.equals(state.clubName, true) }
    val applied = proposal != null && (proposal.down + proposal.upFrom).none { inTable(it) && !it.equals(state.clubName, true) } && (proposal.upInto + proposal.downInto).all { inTable(it) }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var changing by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<Boolean?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FcText("League: ${own.league}", 15.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f))
            FcButton("Done", onDone, small = true)
        }
        if (iWentUp || iWentDown) {
            val target = if (iWentUp) proposal?.leagueAbove else proposal?.leagueBelow
            Notice(if (iWentUp) Tone.GOOD else Tone.WARN, if (iWentUp) "You went up${target?.let { " to $it" } ?: ""}" else "You went down${target?.let { " to $it" } ?: ""}",
                "Move your career to the new league. The clubs that ${if (iWentUp) "went up" else "went down"} with you come along; add the rest of the league after.",
                icon = Icons.Default.SwapHoriz, actions = { FcButton("Move to the new league", { moving = iWentUp }, small = true) })
        } else if (proposal != null && sameLeague && !applied && (proposal.down.isNotEmpty() || proposal.upFrom.isNotEmpty() || proposal.upInto.isNotEmpty() || proposal.downInto.isNotEmpty())) {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                SectionHeader("New season: who went up and down?", icon = Icons.Default.Refresh)
                Muted("Taken from last season's final tables: the relegation places go down, the promotion places (and the first play-off place) go up. Change any of them before you use them.", 12.sp)
                if (proposal.down.isNotEmpty()) KeyValue("Down to ${proposal.leagueBelow ?: "the league below"}", proposal.down.joinToString(", "), mono = false)
                if (proposal.upFrom.isNotEmpty()) KeyValue("Up to ${proposal.leagueAbove ?: "the league above"}", proposal.upFrom.joinToString(", "), mono = false)
                if (proposal.upInto.isNotEmpty()) KeyValue("Up from ${proposal.leagueBelow ?: "the league below"}", proposal.upInto.joinToString(", "), mono = false)
                if (proposal.downInto.isNotEmpty()) KeyValue("Down from ${proposal.leagueAbove ?: "the league above"}", proposal.downInto.joinToString(", "), mono = false)
                if (proposal.upInto.isEmpty() && proposal.downInto.isEmpty()) Muted("The clubs coming in are not known (no table of the other leagues): add them with Add club or Change.", 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FcButton("Use these", { viewModel.applyNewSeasonClubs(proposal.down + proposal.upFrom, proposal.upInto, proposal.downInto) }, icon = Icons.Default.Sync, small = true)
                    FcButton("Change", { changing = true }, kind = ButtonKind.SECONDARY, icon = Icons.Default.Edit, small = true)
                }
            }
        } else if (proposal == null || !sameLeague) {
            FcButton("Who went up and down", { changing = true }, kind = ButtonKind.SECONDARY, icon = Icons.Default.Sync, full = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Clubs in this league (${own.rows.size})", Modifier.weight(1f), icon = Icons.Default.FormatListNumbered)
            FcButton("Add club", { adding = true }, kind = ButtonKind.SECONDARY, icon = Icons.Default.Add, small = true)
        }
        RowsCard {
            own.rows.sortedWith(compareBy({ if (it.movement == "promoted") 0 else if (it.movement == "relegated") 2 else 1 }, { it.club.lowercase() })).forEachIndexed { i, r ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().clickable { renaming = r.club }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FcText(r.club, 14.sp, FontWeight.Medium, c.text, Modifier.weight(1f), maxLines = 1)
                    if (r.movement == "promoted") Tag("Promoted", Tone.GOOD)
                    if (r.movement == "relegated") Tag("Relegated", Tone.BAD)
                    if (r.isUser) Tag("You", Tone.ACCENT)
                    else FcIconButton(Icons.Default.Close, "Remove ${r.club}", { viewModel.removeTableClub(own.league, r.club) }, size = 36.dp, border = false)
                }
            }
        }
        val left = if (proposal != null && sameLeague) (proposal.down + proposal.upFrom).filter { !inTable(it) && !it.equals(state.clubName, true) } else emptyList()
        if (left.isNotEmpty()) {
            FcLabel("LEFT THE LEAGUE")
            RowsCard {
                left.forEachIndexed { i, name ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Text(name, color = c.muted, fontSize = 14.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, maxLines = 1, modifier = Modifier.weight(1f))
                        Tag(if (name in proposal!!.down) "Relegated" else "Promoted", if (name in proposal.down) Tone.BAD else Tone.GOOD)
                        FcIconButton(Icons.Default.Refresh, "Put back $name", { viewModel.addTableClub(own.league, name) }, size = 36.dp, border = false)
                    }
                }
            }
        }
        Muted("Add a club from the club list or type a new name; tap a club to rename it. Removing a club keeps its results in earlier seasons.", 11.sp)
        Muted("On PC the game decides who goes up and down, and the clubs follow by themselves.", 11.sp)
    }
    if (adding) {
        var name by remember { mutableStateOf("") }
        val all = remember { viewModel.getUniqueClubs() }
        FcDialog("Add club", { adding = false }, actions = {
            FcButton("Cancel", { adding = false }, kind = ButtonKind.SECONDARY, small = true)
            FcButton("Add", { viewModel.addTableClub(own.league, name.trim()); adding = false }, small = true, enabled = name.isNotBlank() && !inTable(name.trim()))
        }) {
            FcField("Club name", name, { name = it }, placeholder = "Wigan Athletic", error = if (inTable(name.trim())) "Already in this league." else null)
            val suggestions = remember(name) { if (name.length < 2) emptyList() else all.filter { it.contains(name, true) && !it.equals(name, true) }.take(5) }
            suggestions.forEach { sug -> FcText(sug, 14.sp, color = c.accent, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { name = sug }.padding(8.dp)) }
        }
    }
    renaming?.let { old ->
        var name by remember(old) { mutableStateOf(old) }
        FcDialog("Rename club", { renaming = null }, actions = {
            FcButton("Cancel", { renaming = null }, kind = ButtonKind.SECONDARY, small = true)
            FcButton("Save", { viewModel.renameTableClub(own.league, old, name.trim()); renaming = null }, small = true, enabled = name.isNotBlank())
        }) { FcField("Club name", name, { name = it }) }
    }
    if (changing) {
        val p = proposal?.takeIf { sameLeague }
        var downText by remember { mutableStateOf(p?.down?.joinToString(", ").orEmpty()) }
        var upFromText by remember { mutableStateOf(p?.upFrom?.joinToString(", ").orEmpty()) }
        var upIntoText by remember { mutableStateOf(p?.upInto?.joinToString(", ").orEmpty()) }
        var downIntoText by remember { mutableStateOf(p?.downInto?.joinToString(", ").orEmpty()) }
        fun names(t: String) = t.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }
        FcDialog("Who went up and down", { changing = false }, actions = {
            FcButton("Cancel", { changing = false }, kind = ButtonKind.SECONDARY, small = true)
            FcButton("Use these", { viewModel.applyNewSeasonClubs(names(downText) + names(upFromText), names(upIntoText), names(downIntoText)); changing = false }, small = true)
        }) {
            Muted("Club names, separated by commas. Clubs leaving are taken out of ${own.league}; clubs coming in are added with a Promoted or Relegated tag for the season.", 12.sp)
            FcField("Went down (leave the league)", downText, { downText = it }, singleLine = false)
            FcField("Went up (leave the league)", upFromText, { upFromText = it }, singleLine = false)
            FcField("Came up into the league", upIntoText, { upIntoText = it }, singleLine = false)
            FcField("Came down into the league", downIntoText, { downIntoText = it }, singleLine = false)
        }
    }
    moving?.let { up ->
        val suggested = (if (up) proposal?.leagueAbove else proposal?.leagueBelow).orEmpty()
        var league by remember { mutableStateOf(suggested) }
        val companions = (if (up) proposal?.upFrom else proposal?.down).orEmpty().filterNot { it.equals(state.clubName, true) }
        var others by remember { mutableStateOf(companions.joinToString(", ")) }
        val leagues = remember(state.country) { getCompetitionsForCountry(state.country).filter { LeagueTables.isDomesticLeague(it) } }
        FcDialog(if (up) "Move up a league" else "Move down a league", { moving = null }, actions = {
            FcButton("Cancel", { moving = null }, kind = ButtonKind.SECONDARY, small = true)
            FcButton("Move", { viewModel.moveLeague(league, others.split(",").map { it.trim() }.filter { it.isNotEmpty() }, up); moving = null }, small = true, enabled = league.isNotBlank())
        }) {
            if (leagues.isNotEmpty()) FcSelectField("New league", league, leagues, { league = it }) else FcField("New league", league, { league = it })
            FcField(if (up) "Went up with you" else "Went down with you", others, { others = it }, singleLine = false, hint = "They join the new table with you. Add the other clubs after.")
            Muted("${lastSeason?.let { SeasonView.seasonLabel(it.year) } ?: "Last season"} stays in ${state.leagueName} in your archive.", 11.sp)
        }
    }
}

// =====================================================================================================================
// Stats
// =====================================================================================================================

private fun LazyListScope.statsTab(viewModel: MainViewModel, state: AppState, view: SeasonView, console: Boolean, nav: (String) -> Unit) {
    item { StatsTabContent(viewModel, state, view, console, nav) }
}

@Composable
private fun StatsTabContent(viewModel: MainViewModel, state: AppState, view: SeasonView, console: Boolean, nav: (String) -> Unit) {
    val comps = (view.leaders.map { it.competition } + view.leagueName).distinct()
    var chosen by remember { mutableStateOf(view.leagueName) }
    var editing by remember { mutableStateOf<String?>(null) }
    var sortCol by remember { mutableStateOf(4) }
    val leaders = view.leaders.firstOrNull { it.competition.equals(chosen, true) }
    val totals = remember(view) { SeasonFacts.allTotals(view.players.filter { it.status != "Sold" }, view.matches, view.squadStats).filter { it.appearances > 0 || it.goals > 0 } }
    val c = FcTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (comps.size > 1) FcSelectField("League", chosen, comps, { chosen = it })
        val ownScorers = totals.filter { it.goals > 0 }.sortedByDescending { it.goals }.take(4).map { StatLeader(it.player.displayName, state.clubName, it.goals, true) }
        val ownAssists = totals.filter { it.assists > 0 }.sortedByDescending { it.assists }.take(3).map { StatLeader(it.player.displayName, state.clubName, it.assists, true) }
        val ownCs = totals.filter { it.cleanSheets > 0 }.sortedByDescending { it.cleanSheets }.take(3).map { StatLeader(it.player.displayName, state.clubName, it.cleanSheets, true) }
        LeadersCard("Golden Boot · $chosen", "goals", leaders?.goals?.takeIf { it.isNotEmpty() } ?: ownScorers, state, view, own = leaders?.goals.isNullOrEmpty(), onEdit = if (console && !view.isArchived) ({ editing = "goals" }) else null)
        LeadersCard("Most assists", "assists", leaders?.assists?.takeIf { it.isNotEmpty() } ?: ownAssists, state, view, own = leaders?.assists.isNullOrEmpty(), onEdit = if (console && !view.isArchived) ({ editing = "assists" }) else null)
        LeadersCard("Most clean sheets", "clean", leaders?.cleanSheets?.takeIf { it.isNotEmpty() } ?: ownCs, state, view, own = leaders?.cleanSheets.isNullOrEmpty(), onEdit = if (console && !view.isArchived) ({ editing = "clean" }) else null)
        FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
            SectionHeader("Season player statistics", sub = "Tap a header to sort. Tap a row to open the player.", icon = Icons.Default.ShowChart)
            val headers = listOf("PLAYER", "APPS", "GLS", "AST", "AVG")
            val w = listOf(1f, 0.28f, 0.25f, 0.25f, 0.3f)
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                headers.forEachIndexed { i, h -> FcLabel(h, Modifier.weight(w[i]).clickable { sortCol = i }, color = if (sortCol == i) c.accent else c.muted) }
            }
            HRule()
            val sorted = when (sortCol) {
                0 -> totals.sortedBy { it.player.displayName }
                1 -> totals.sortedByDescending { it.appearances }
                2 -> totals.sortedByDescending { it.goals }
                3 -> totals.sortedByDescending { it.assists }
                else -> totals.sortedByDescending { it.averageRating }
            }
            if (sorted.isEmpty()) Muted("No appearances yet.", 12.sp)
            sorted.forEach { t ->
                Row(Modifier.fillMaxWidth().clickable { nav("player/${t.player.id}") }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    FcText(t.player.displayName, 13.sp, FontWeight.SemiBold, modifier = Modifier.weight(w[0]), maxLines = 1)
                    Mono("${t.appearances}", 13.sp, modifier = Modifier.weight(w[1]), align = TextAlign.End)
                    Mono("${t.goals}", 13.sp, modifier = Modifier.weight(w[2]), align = TextAlign.End)
                    Mono("${t.assists}", 13.sp, modifier = Modifier.weight(w[3]), align = TextAlign.End)
                    Mono(if (t.averageRating > 0) String.format(java.util.Locale.US, "%.1f", t.averageRating) else "–", 13.sp, modifier = Modifier.weight(w[4]), align = TextAlign.End)
                }
                HRule()
            }
            Muted(if (console) "From the matches you logged and the numbers on each player card." else "From the game for every match, including ones you did not log; a match you logged yourself keeps your numbers.", 11.sp)
        }
    }
    editing?.let { cat -> LeadersEditDialog(viewModel, state, chosen, cat, leaders, onDismiss = { editing = null }) }
}

@Composable
private fun LeadersCard(title: String, unit: String, rows: List<StatLeader>, state: AppState, view: SeasonView, own: Boolean, onEdit: (() -> Unit)?) {
    val c = FcTheme.colors
    FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(title, Modifier.weight(1f), sub = if (own && rows.isNotEmpty()) "Your players only (the league's list arrives from the game)" else null, icon = Icons.Default.EmojiEvents)
            if (onEdit != null) FcButton("Edit", onEdit, kind = ButtonKind.GHOST, icon = Icons.Default.Edit, small = true)
        }
        if (rows.isEmpty()) Muted("Nothing yet.", 12.sp)
        rows.take(5).forEachIndexed { i, l ->
            val me = l.isUser || l.club.equals(state.clubName, true)
            val headshot = if (me) view.players.firstOrNull { it.displayName.equals(l.player, true) || it.name.equals(l.player, true) }?.headshotUrl else null
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (me) c.accentBg else Color.Transparent).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Mono("${i + 1}", 12.sp, c.muted, FontWeight.Normal, Modifier.width(14.dp))
                Headshot(headshot, l.player, 34.dp)
                Column(Modifier.weight(1f)) {
                    FcText(l.player, 14.sp, if (me) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
                    Muted(l.club, 12.sp, maxLines = 1)
                }
                Figure("${l.value}", 18.sp, weight = FontWeight.SemiBold)
                Muted(unit, 11.sp, Modifier.width(38.dp))
            }
        }
    }
}

@Composable
private fun LeadersEditDialog(viewModel: MainViewModel, state: AppState, competition: String, category: String, current: CompetitionLeaders?, onDismiss: () -> Unit) {
    val existing = when (category) { "goals" -> current?.goals; "assists" -> current?.assists; else -> current?.cleanSheets }.orEmpty()
    val rows = remember { mutableStateListOf(*existing.toTypedArray()) }
    var player by remember { mutableStateOf("") }
    var club by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    FcDialog("${when (category) { "goals" -> "Golden Boot"; "assists" -> "Most assists"; else -> "Most clean sheets" }} · $competition", onDismiss, actions = {
        FcButton("Cancel", onDismiss, kind = ButtonKind.SECONDARY, small = true)
        FcButton("Save", {
            val sorted = rows.sortedByDescending { it.value }
            val base = current ?: CompetitionLeaders(competition = competition, source = Source.MANUAL)
            val updated = when (category) { "goals" -> base.copy(goals = sorted); "assists" -> base.copy(assists = sorted); else -> base.copy(cleanSheets = sorted) }
            viewModel.saveLeaders(updated)
            onDismiss()
        }, small = true)
    }) {
        rows.forEachIndexed { i, l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FcText("${l.player} (${l.club})", 13.sp, modifier = Modifier.weight(1f))
                Mono("${l.value}", 13.sp)
                FcIconButton(Icons.Default.Close, "Remove", { rows.removeAt(i) }, size = 32.dp, border = false)
            }
        }
        FcField("Player", player, { player = it })
        FieldPair({ m -> FcField("Club", club, { club = it }, m, placeholder = state.clubName) }, { m -> FcNumberField(if (category == "clean") "Clean sheets" else category.replaceFirstChar { it.uppercase() }, value, { value = it }, m) })
        FcButton("Add to the list", {
            if (player.isNotBlank()) {
                rows.add(StatLeader(player.trim(), club.trim().ifBlank { state.clubName }, value.toIntOrNull() ?: 0, club.isBlank() || club.equals(state.clubName, true)))
                player = ""; club = ""; value = ""
            }
        }, kind = ButtonKind.SECONDARY, icon = Icons.Default.Add, small = true)
    }
}

// =====================================================================================================================
// Awards
// =====================================================================================================================

private fun LazyListScope.awardsTab(viewModel: MainViewModel, state: AppState, view: SeasonView) {
    item { AwardsTabContent(viewModel, state, view) }
}

@Composable
private fun AwardsTabContent(viewModel: MainViewModel, state: AppState, view: SeasonView) {
    var assigning by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AwardEntry?>(null) }
    var removingTrophy by remember { mutableStateOf<String?>(null) }
    var removingAward by remember { mutableStateOf<com.fctracker.career.PlayerAward?>(null) }
    val game = view.awards.filter { it.source == Source.GAME }
    val own = view.awards.filter { it.source != Source.GAME }
    // the trophy list holds the whole career: this season's tab shows this season's (and any without a season in the name)
    val trophies = if (view.isArchived) view.archived?.trophies.orEmpty()
        else state.teamTrophies.map { it.name }.filter { com.fctracker.career.logic.Trophies.inSeason(it, state.startYear) }
    val totals = SeasonFacts.allTotals(view.players, view.matches, view.squadStats)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
            SectionHeader("From the game", icon = Icons.Default.EmojiEvents)
            if (game.isEmpty()) Muted(if (state.platform == Platform.CONSOLE) "On console, add the awards the game hands out with Assign awards." else "Nothing yet: monthly awards, team of the week and end-of-season awards are added by themselves as the game hands them out.", 12.sp)
            game.groupBy { it.title }.forEach { (title, list) ->
                AwardRow(title + if (list.size > 1 && list.first().kind == "totw") " × ${list.size}" else "", list.joinToString(", ") { a -> listOf(a.recipient, a.period).filter { it.isNotBlank() }.joinToString(" · ") }, "Game", Tone.INFO, if (list.first().kind == "totw") Tone.MUTED else Tone.WARN)
            }
        }
        FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
            SectionHeader("Your own awards & trophies", icon = Icons.Default.Star)
            if (trophies.isEmpty() && own.isEmpty() && (view.isArchived || state.playerAwards.isEmpty())) Muted("None yet.", 12.sp)
            trophies.forEach { t ->
                Box(Modifier.clickable(enabled = !view.isArchived) { removingTrophy = t }) { AwardRow(t, "Team trophy", "Trophy", Tone.WARN, Tone.WARN) }
            }
            own.forEach { a ->
                Box(Modifier.clickable(enabled = !view.isArchived) { deleting = a }) { AwardRow(a.title, listOf(a.recipient, a.period).filter { it.isNotBlank() }.joinToString(" · "), "Yours", Tone.MUTED, Tone.MUTED) }
            }
            if (!view.isArchived) state.playerAwards.forEach { a ->
                Box(Modifier.clickable { removingAward = a }) {
                    AwardRow(a.name, "${state.players.firstOrNull { it.id == a.playerId }?.displayName ?: ""}${if (a.monthYear.isNotBlank()) " · ${a.monthYear}" else ""}", "Yours", Tone.MUTED, Tone.MUTED)
                }
            }
            if (!view.isArchived && (trophies.isNotEmpty() || own.isNotEmpty())) Muted("Tap a trophy or award to remove it.", 11.sp)
            if (!view.isArchived) FcButton("Assign awards & honors", { assigning = true }, kind = ButtonKind.SECONDARY, icon = Icons.Default.EmojiEvents, full = true)
        }
        SectionHeader("Season milestones & record holders", icon = Icons.Default.TrackChanges)
        totals.maxByOrNull { it.appearances }?.takeIf { it.appearances > 0 }?.let { RecordHolder("Most appearances", it.player.displayName, it.player.headshotUrl, "${it.appearances}") }
        totals.maxByOrNull { it.goals }?.takeIf { it.goals > 0 }?.let { RecordHolder("Top scorer", it.player.displayName, it.player.headshotUrl, "${it.goals}") }
        totals.maxByOrNull { it.assists }?.takeIf { it.assists > 0 }?.let { RecordHolder("Top assists", it.player.displayName, it.player.headshotUrl, "${it.assists}") }
        view.milestones.takeLast(5).forEach { m -> AwardRow(m.title, Dates.full(m.date), "Milestone", Tone.ACCENT, Tone.ACCENT) }
    }
    if (assigning) AssignAwardsDialog(viewModel, state, onDismiss = { assigning = false })
    deleting?.let { a -> ConfirmDialog("Remove this award?", "${a.title} · ${a.recipient}", "Remove", { viewModel.deleteAward(a.id) }, { deleting = null }) }
    removingTrophy?.let { t ->
        ConfirmDialog("Remove this trophy?", "$t. If it came from the game, it is not added again.", "Remove", { viewModel.removeTeamTrophy(t) }, { removingTrophy = null })
    }
    removingAward?.let { a -> ConfirmDialog("Remove this award?", a.name, "Remove", { viewModel.removePlayerAward(a.id) }, { removingAward = null }) }
}

@Composable
private fun AwardRow(title: String, sub: String, tag: String, tagTone: Tone, iconTone: Tone) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(if (iconTone == Tone.MUTED) Icons.Default.Star else Icons.Default.EmojiEvents, iconTone, 40.dp)
        Column(Modifier.weight(1f)) {
            FcText(title, 15.sp, FontWeight.SemiBold)
            if (sub.isNotBlank()) Muted(sub, 12.sp)
        }
        Tag(tag, tagTone)
    }
}

@Composable
private fun RecordHolder(title: String, name: String, headshot: String, value: String) {
    FcCard(Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Headshot(headshot, name, 40.dp)
            Column(Modifier.weight(1f)) { FcLabel(title); FcText(name, 14.sp, FontWeight.Bold) }
            Figure(value, 28.sp, FcTheme.colors.accent)
        }
    }
}

@Composable
private fun AssignAwardsDialog(viewModel: MainViewModel, state: AppState, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(0) }
    var title by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf("") }
    var period by remember { mutableStateOf(SeasonView.seasonLabel(state.startYear)) }
    var fromGame by remember { mutableStateOf(false) }
    val names = state.players.filter { isActivePlayer(it) }.map { it.displayName }.sorted()
    FcDialog("Assign awards & honors", onDismiss, actions = {
        FcButton("Cancel", onDismiss, kind = ButtonKind.SECONDARY, small = true)
        FcButton("Add", {
            if (mode == 0) viewModel.addTeamTrophy(TeamTrophy(name = title.trim()))
            else viewModel.addAward(AwardEntry(title = title.trim(), recipient = recipient, period = period.trim(), kind = if (recipient == "You (manager)") "manager" else "player"))
            onDismiss()
        }, small = true, enabled = title.isNotBlank() && (mode == 0 || recipient.isNotBlank()))
    }) {
        com.fctracker.career.ui.components.Segmented(listOf("Team trophy", "Award"), mode, { mode = it })
        if (mode == 0) {
            FcField("Trophy", title, { title = it }, placeholder = "FA Cup ${SeasonView.seasonLabel(state.startYear)}", hint = "Include the season so the recap and the season list pick it up.")
        } else {
            FcField("Award", title, { title = it }, placeholder = "Player of the Month")
            FcSelectField("Given to", recipient, listOf("You (manager)") + names, { recipient = it })
            FcField("Month or season", period, { period = it }, placeholder = "September 2026")
            Muted("Use this for your own awards (Fans' Player of the Year) and, on console, for the ones the game hands out.", 11.sp)
        }
    }
}

// =====================================================================================================================
// Europe
// =====================================================================================================================

private fun LazyListScope.europeTab(viewModel: MainViewModel, state: AppState, view: SeasonView, console: Boolean) {
    item { EuropeTabContent(viewModel, state, view, console) }
}

@Composable
private fun EuropeTabContent(viewModel: MainViewModel, state: AppState, view: SeasonView, console: Boolean) {
    val c = FcTheme.colors
    var addPoints by remember { mutableStateOf(false) }
    var addClub by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<ClubCoefficient?>(null) }
    val coefficients = view.coefficients.sortedBy { if (it.rank > 0) it.rank else Int.MAX_VALUE }
    val mine = coefficients.firstOrNull { it.isUser || it.club.equals(state.clubName, true) }
    val ownTotal = CareerOps.ownCoefficientTotal(state)
    val top = coefficients.filter { it.rank in 1..10 }
    val countryClubs = coefficients.filter { it.rank > 10 && it.country.equals(state.country, true) && it !== mine }
    // clubs added by hand without a rank, or from another country further down: still shown
    val others = coefficients.filter { it !== mine && it !in top && it !in countryClubs }
    val editable = console && !view.isArchived
    val tap: (ClubCoefficient) -> (() -> Unit)? = { r -> if (editable) ({ removing = r }) else null }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Muted(if (console) "The European club ranking: points from European matches over the last five seasons. On console you add your club's points and the clubs you want to compare with." else "Club coefficients from your game: points from European matches over the last five seasons. It updates after every European round.", 12.sp)
        if (coefficients.isEmpty() && !console) EmptyCard("The ranking arrives from your game with the next report from the PC.")
        if (coefficients.isNotEmpty() || console) {
            val w = listOf(0.12f, 0.5f, 0.2f, 0.22f)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface).padding(top = 12.dp, bottom = 4.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
                    listOf("#", "CLUB", "SEASON", "5 YEARS").forEachIndexed { i, h -> FcLabel(h, Modifier.weight(w[i]), size = 10.sp) }
                }
                HRule()
                top.forEach { CoefRow(it, it === mine, w, tap(it)) }
                if (countryClubs.isNotEmpty()) {
                    Muted("Other clubs from ${state.country}", 11.sp, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    HRule()
                    countryClubs.forEach { CoefRow(it, false, w, tap(it)) }
                }
                if (others.isNotEmpty()) {
                    Muted(if (console) "Clubs you compare with" else "Other clubs", 11.sp, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    HRule()
                    others.forEach { CoefRow(it, false, w, tap(it)) }
                }
                if (mine != null && (mine.rank > 10 || mine.rank <= 0)) CoefRow(mine, true, w, tap(mine))
                if (mine == null) {
                    val ownSeason = state.ownCoefficientPoints[state.startYear.toString()] ?: 0.0
                    CoefRow(ClubCoefficient(0, "${state.clubName}${if (ownTotal <= 0) " · not ranked" else ""}", state.country, ownSeason, ownTotal, true), true, w)
                }
            }
        }
        FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 8.dp) {
            SectionHeader("Your club", icon = Icons.Default.Star)
            if (mine == null && ownTotal <= 0) {
                FcText("Not in the ranking yet", 15.sp, FontWeight.SemiBold)
                Muted("A club joins the ranking with its first match in the Champions League, Europa League or Conference League. From then on your row sits in its place in the table above, highlighted, and this card shows your points season by season.", 12.sp)
                Muted("Until then the table shows the top clubs and the clubs from your country, so you can see how far there is to go.", 11.sp)
            } else {
                mine?.let { KeyValue("Rank", if (it.rank > 0) Milestones.ordinal(it.rank) else "–") }
                KeyValue("Points over five seasons", String.format(java.util.Locale.US, "%.3f", mine?.total ?: ownTotal))
                state.ownCoefficientPoints.entries.sortedBy { it.key }.forEach { (y, p) -> KeyValue(SeasonView.seasonLabel(y.toIntOrNull() ?: 0), String.format(java.util.Locale.US, "%.3f", p)) }
            }
            if (editable) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FcButton("Add your points", { addPoints = true }, kind = ButtonKind.SECONDARY, icon = Icons.Default.Add, small = true)
                FcButton("Add a club", { addClub = true }, kind = ButtonKind.GHOST, icon = Icons.Default.Add, small = true)
            }
            if (editable && coefficients.isNotEmpty()) Muted("Tap a club in the table to remove it.", 11.sp)
        }
        if (view.nationCoefficients.isNotEmpty()) {
            val nations = view.nationCoefficients.sortedByDescending { it.points }.take(6)
            val max = nations.maxOf { it.points }.coerceAtLeast(1.0)
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                SectionHeader("Countries", icon = Icons.Default.Language)
                Muted("Decides how many clubs from each country play in Europe.", 11.sp)
                nations.forEach { n ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Muted(n.country, 12.sp, Modifier.width(72.dp), maxLines = 1)
                        Box(Modifier.weight(1f)) { ProgressBar(n.points.toFloat(), max.toFloat(), c.accent) }
                        Mono(String.format(java.util.Locale.US, "%.3f", n.points), 12.sp, modifier = Modifier.width(60.dp), align = TextAlign.End)
                    }
                }
            }
        }
    }
    if (addPoints) {
        var season by remember { mutableStateOf(state.startYear.toString()) }
        var pts by remember { mutableStateOf(state.ownCoefficientPoints[state.startYear.toString()]?.toString() ?: "") }
        FcDialog("Your European points", { addPoints = false }, actions = {
            FcButton("Cancel", { addPoints = false }, kind = ButtonKind.SECONDARY, small = true)
            FcButton("Save", { season.toIntOrNull()?.let { viewModel.setOwnCoefficient(it, pts.toDoubleOrNull() ?: 0.0) }; addPoints = false }, small = true)
        }) {
            FcSelectField("Season", SeasonView.seasonLabel(season.toIntOrNull() ?: state.startYear), (0..4).map { SeasonView.seasonLabel(state.startYear - it) }, { label -> season = label.take(4) })
            FcNumberField("Points that season", pts, { pts = it }, decimal = true, placeholder = "12.500")
            Muted("Find them in the game under Europe · Club coefficients.", 11.sp)
        }
    }
    if (addClub) {
        var club by remember { mutableStateOf("") }
        var country by remember { mutableStateOf("") }
        var rank by remember { mutableStateOf("") }
        var season by remember { mutableStateOf("") }
        var total by remember { mutableStateOf("") }
        FcDialog("Add a club to compare", { addClub = false }, actions = {
            FcButton("Cancel", { addClub = false }, kind = ButtonKind.SECONDARY, small = true)
            FcButton("Add", { viewModel.addCoefficientClub(ClubCoefficient(rank.toIntOrNull() ?: 0, club.trim(), country.trim(), season.toDoubleOrNull() ?: 0.0, total.toDoubleOrNull() ?: 0.0)); addClub = false }, small = true, enabled = club.isNotBlank())
        }) {
            FcField("Club", club, { club = it })
            FieldPair({ m -> FcField("Country", country, { country = it }, m) }, { m -> FcNumberField("Rank", rank, { rank = it }, m) })
            FieldPair({ m -> FcNumberField("This season", season, { season = it }, m, decimal = true) }, { m -> FcNumberField("5 years", total, { total = it }, m, decimal = true) })
        }
    }
    removing?.let { r -> ConfirmDialog("Remove ${r.club}?", "It leaves this ranking. You can add it again with Add a club.", "Remove", { viewModel.removeCoefficientClub(r.club) }, { removing = null }) }
}

@Composable
private fun CoefRow(r: ClubCoefficient, me: Boolean, w: List<Float>, onClick: (() -> Unit)? = null) {
    val c = FcTheme.colors
    Row(Modifier.fillMaxWidth().background(if (me) c.accentBg else Color.Transparent).let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Mono(if (r.rank > 0) "${r.rank}" else "–", 13.sp, c.muted, FontWeight.Normal, Modifier.weight(w[0]))
        Row(Modifier.weight(w[1]), verticalAlignment = Alignment.CenterVertically) {
            FcText(r.club, 13.sp, if (me) FontWeight.Bold else FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            if (r.country.isNotBlank() && !me) Muted("  ${r.country.take(3).uppercase()}", 10.sp)
        }
        Mono(String.format(java.util.Locale.US, "%.3f", r.season), 13.sp, c.muted, FontWeight.Normal, Modifier.weight(w[2]), align = TextAlign.End)
        Mono(String.format(java.util.Locale.US, "%.3f", r.total), 13.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(w[3]), align = TextAlign.End)
    }
    HRule()
}

/** A record holder card (also used on Career Summary). */
@Composable
fun RecordCardWithImage(title: String, playerName: String, recordValue: String, imageUrl: String) = RecordHolder(title, playerName, imageUrl, recordValue)
