package com.fctracker.career.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.AppState
import com.fctracker.career.Fixture
import com.fctracker.career.MainViewModel
import com.fctracker.career.MatchResult
import com.fctracker.career.Platform
import com.fctracker.career.Source
import com.fctracker.career.insights.MatchInsights
import com.fctracker.career.insights.ResultFilter
import com.fctracker.career.logic.CareerOps
import com.fctracker.career.logic.Dates
import com.fctracker.career.logic.LeagueTables
import com.fctracker.career.logic.Milestones
import com.fctracker.career.logic.SeasonView
import com.fctracker.career.ui.components.ArchivedBanner
import com.fctracker.career.ui.components.BookingCard
import com.fctracker.career.ui.components.ButtonKind
import com.fctracker.career.ui.components.ChipRow
import com.fctracker.career.ui.components.CompLogo
import com.fctracker.career.ui.components.ConfirmDialog
import com.fctracker.career.ui.components.Fab
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.FcCard
import com.fctracker.career.ui.components.FcDateField
import com.fctracker.career.ui.components.FcDialog
import com.fctracker.career.ui.components.FcField
import com.fctracker.career.ui.components.FcIconButton
import com.fctracker.career.ui.components.FcLabel
import com.fctracker.career.ui.components.FcPage
import com.fctracker.career.ui.components.FcSelectField
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.FcTopBar
import com.fctracker.career.ui.components.FieldPair
import com.fctracker.career.ui.components.Figure
import com.fctracker.career.ui.components.FormDots
import com.fctracker.career.ui.components.FormStrip
import com.fctracker.career.ui.components.HRule
import com.fctracker.career.ui.components.Headshot
import com.fctracker.career.ui.components.Mono
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.NavIcon
import com.fctracker.career.ui.components.Notice
import com.fctracker.career.ui.components.Pill
import com.fctracker.career.ui.components.RatingChip
import com.fctracker.career.ui.components.SectionHeader
import com.fctracker.career.ui.components.Segmented
import com.fctracker.career.ui.components.Tag
import com.fctracker.career.ui.components.Tone
import com.fctracker.career.ui.components.resultTone
import com.fctracker.career.ui.components.toneColors
import com.fctracker.career.ui.theme.FcTheme

/** Matches: the results played (with the calendar and filters) and the fixtures still to come. */
@Composable
fun MatchesScreen(viewModel: MainViewModel, onMenuClick: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val health by viewModel.health.collectAsState()
    val view = remember(state) { SeasonView.of(state) }
    val console = state.platform == Platform.CONSOLE
    var tab by remember { mutableStateOf(0) }
    var competition by remember { mutableStateOf(MatchInsights.ALL_COMPETITIONS) }
    var result by remember { mutableStateOf(ResultFilter.ALL) }
    var showForm by remember { mutableStateOf(false) }
    var editMatch by remember { mutableStateOf<MatchResult?>(null) }
    var deleteMatch by remember { mutableStateOf<MatchResult?>(null) }
    var detailMatch by remember { mutableStateOf<MatchResult?>(null) }
    var fixtureDialog by remember { mutableStateOf<Fixture?>(null) }
    var newFixture by remember { mutableStateOf(false) }
    var deleteFixture by remember { mutableStateOf<Fixture?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var headToHead by remember { mutableStateOf<String?>(null) }

    val matches = view.matches
    val upcoming = if (view.isArchived) emptyList() else CareerOps.upcoming(state)
    val showUpcoming = tab == 1 && !view.isArchived
    val table = remember(view.ownTable) { view.ownTable?.let { LeagueTables.sorted(it.rows) }.orEmpty() }

    val comps = remember(matches, upcoming, showUpcoming) {
        if (showUpcoming) listOf(MatchInsights.ALL_COMPETITIONS) + upcoming.map { it.competition }.filter { it.isNotBlank() }.distinct().sorted()
        else MatchInsights.competitions(matches)
    }
    val activeComp = if (competition in comps) competition else MatchInsights.ALL_COMPETITIONS
    val compMatches = MatchInsights.byCompetition(matches, activeComp)
    val visible = compMatches.filter { MatchInsights.fits(it, result) }.sortedByDescending { it.matchDate }
    val estimated = MatchInsights.estimatedCount(compMatches)

    FcPage(
        header = {
            if (view.isArchived) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { ArchivedBanner("${view.label} · ${view.leagueName}", { viewModel.setViewingSeasonYear(null) }) }
            FcTopBar(
                "Matches", sub = "${view.leagueName} · ${view.label}", nav = NavIcon.MENU, onNav = onMenuClick,
                pill = if (!view.isArchived && !console) ({ StatusPill(health.level, health.pill, onClick = { viewModel.navigateToScreen("connection") }) }) else null
            )
        },
        fab = when {
            view.isArchived -> null
            showUpcoming && console -> ({ Fab("Add fixture", { newFixture = true }) })
            else -> ({ Fab("Log match", { editMatch = null; showForm = true }) })
        }
    ) {
        if (!view.isArchived) item { Segmented(listOf("Played (${matches.size})", "Upcoming (${upcoming.size})"), tab, { tab = it; competition = MatchInsights.ALL_COMPETITIONS }) }

        if (showUpcoming) {
            item {
                if (console) Notice(Tone.INFO, "On console you add fixtures yourself", "On PC they arrive from the game by themselves.", icon = Icons.Default.Info)
                else Notice(Tone.GOOD, "Added from your game", "The capture script reads your fixture list: dates, kick-off times and cup draws appear by themselves, and a rescheduled match moves by itself.", icon = Icons.Default.Sync)
            }
            if (comps.size > 2) item { FcSelectField("Competition", if (activeComp == MatchInsights.ALL_COMPETITIONS) "All competitions" else activeComp, listOf("All competitions") + comps.drop(1), { competition = if (it == "All competitions") MatchInsights.ALL_COMPETITIONS else it }) }
            val list = upcoming.filter { activeComp == MatchInsights.ALL_COMPETITIONS || it.competition == activeComp }
            if (list.isEmpty()) {
                item { Muted(if (console) "No fixtures yet. Add a few at a time, or the whole list from the game's calendar screen." else "No fixtures yet. They arrive with the next report from your PC; on console this list stays empty unless you add fixtures yourself.", 12.sp) }
            }
            list.groupBy { Dates.monthYear(it.date) }.forEach { (month, fixtures) ->
                item { FcLabel(month.ifBlank { "No date" }) }
                itemsIndexed(fixtures, key = { i, f -> "$month|${f.id}#$i" }) { _, f ->
                    FixtureRow(f, table, onClick = { if (f.source != Source.GAME || console) fixtureDialog = f })
                }
            }
            if (!console) item { FcButton("Add a fixture yourself", { newFixture = true }, kind = ButtonKind.SECONDARY, icon = Icons.Default.Add, small = true) }
            return@FcPage
        }

        // -- played ----------------------------------------------------------------------------------------------
        if (compMatches.isNotEmpty()) {
            item {
                val r = MatchInsights.record(compMatches)
                val isLeague = com.fctracker.career.logic.Competitions.isLeagueFn(state.leagueName)
                val leagueGames = compMatches.count { isLeague(it.competition) }
                val pts = MatchInsights.leaguePoints(compMatches, isLeague)
                FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FcText("${r.played} played", 15.sp, FontWeight.Bold, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Tag("${r.won}W", Tone.GOOD); Tag("${r.drawn}D", Tone.WARN); Tag("${r.lost}L", Tone.BAD)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val gd = r.goalsFor - r.goalsAgainst
                        // points only come from league matches; a cup filter shows none
                        val ptsText = when {
                            leagueGames == 0 -> ""
                            leagueGames == compMatches.size -> " · $pts pts"
                            else -> " · $pts league pts"
                        }
                        Muted("Goals ${r.goalsFor}–${r.goalsAgainst} (${if (gd > 0) "+" else ""}$gd)$ptsText", 13.sp, Modifier.weight(1f))
                        Muted("Form", 12.sp)
                        Spacer(Modifier.width(8.dp))
                        FormStrip(MatchInsights.form(compMatches), 22.dp, 4.dp)
                    }
                }
            }
        }
        item {
            // a played day opens that match: its details (editable in the current season, read-only when archived)
            SeasonCalendar(view.season, matches, upcoming, onMatch = { m ->
                expandedId = m.id
                if (view.isArchived) detailMatch = m else { editMatch = m; showForm = true }
            })
        }
        if (comps.size > 2) {
            item { FcSelectField("Competition", if (activeComp == MatchInsights.ALL_COMPETITIONS) "All competitions" else activeComp, listOf("All competitions") + comps.drop(1), { competition = if (it == "All competitions") MatchInsights.ALL_COMPETITIONS else it }) }
        }
        if (compMatches.isNotEmpty()) {
            item {
                val filters = ResultFilter.values().filter { it != ResultFilter.ESTIMATED || estimated > 0 || result == it }
                ChipRow(filters.map { if (it == ResultFilter.ESTIMATED) "Estimated ($estimated)" else it.label }, filters.indexOf(result).coerceAtLeast(0), { result = filters[it] })
            }
        }
        item {
            Muted(if (view.isArchived) "Read-only: matches of an archived season cannot be edited or deleted." else "Showing ${view.label}. For an earlier season, switch Home to Archived and pick the season.", 12.sp)
        }
        if (visible.isEmpty()) {
            item {
                if (matches.isEmpty()) Muted("No matches played yet.", 13.sp)
                else FcButton("Clear filters", { result = ResultFilter.ALL; competition = MatchInsights.ALL_COMPETITIONS }, kind = ButtonKind.SECONDARY, small = true)
            }
        }
        itemsIndexed(visible, key = { i, m -> "${m.id}#$i" }) { _, m ->
            val open = expandedId == m.id || (expandedId == null && m.id == visible.firstOrNull()?.id)
            SwipeRow(enabled = !view.isArchived, onEdit = { editMatch = m; showForm = true }, onDelete = { deleteMatch = m }) {
                MatchTimelineRow(
                    m, state, open,
                    onToggle = { expandedId = if (open) "none" else m.id },
                    onLineup = { if (view.isArchived) detailMatch = m else { editMatch = m; showForm = true } },
                    onDelete = if (view.isArchived) null else ({ deleteMatch = m }),
                    archived = view.isArchived
                )
            }
        }
    }

    if (showForm) {
        MatchFormModal(viewModel = viewModel, state = state, initialMatch = editMatch, onDismiss = { showForm = false }, onSave = { m ->
            if (editMatch != null) viewModel.updateMatchResult(m) else viewModel.addMatchResult(m)
            showForm = false
        }, onHeadToHead = if (editMatch != null) ({ opp -> showForm = false; headToHead = opp }) else null)
    }
    headToHead?.let { opp ->
        HeadToHeadPage(state, opp, onClose = { headToHead = null }, onOpenMatch = { h ->
            if (h.archived) detailMatch = h.match else { editMatch = h.match; showForm = true }
        })
    }
    deleteMatch?.let { m ->
        ConfirmDialog("Delete this match?", "${m.goalsFor}–${m.goalsAgainst} ${if (m.isHome) "vs" else "at"} ${m.opponentName} on ${Dates.full(m.matchDate)} will be removed from your match history. This cannot be undone.", "Delete", { viewModel.deleteMatchResult(m.id) }, { deleteMatch = null })
    }
    detailMatch?.let { m -> MatchDetailDialog(m, state, onDismiss = { detailMatch = null }, onHeadToHead = { detailMatch = null; headToHead = m.opponentName }) }
    if (newFixture || fixtureDialog != null) {
        FixtureDialog(state, fixtureDialog, onDismiss = { newFixture = false; fixtureDialog = null },
            onSave = { f, again ->
                if (fixtureDialog != null) viewModel.updateFixture(f) else viewModel.addFixture(f)
                if (!again) { newFixture = false; fixtureDialog = null }
            },
            onDelete = fixtureDialog?.let { f -> { deleteFixture = f; fixtureDialog = null } })
    }
    deleteFixture?.let { f -> ConfirmDialog("Delete this fixture?", "${if (f.isHome) "vs" else "at"} ${f.opponent} on ${Dates.full(f.date)}.", "Delete", { viewModel.deleteFixture(f.id) }, { deleteFixture = null }) }
}

/** Swipe right to edit, left to delete (asks first). The row springs back; the buttons inside do the same things. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeRow(enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val edit by rememberUpdatedState(onEdit)
    val del by rememberUpdatedState(onDelete)
    val c = FcTheme.colors
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.StartToEnd -> edit()
            SwipeToDismissBoxValue.EndToStart -> del()
            SwipeToDismissBoxValue.Settled -> Unit
        }
        false
    })
    SwipeToDismissBox(state = state, backgroundContent = {
        val dir = state.dismissDirection
        val deleting = dir == SwipeToDismissBoxValue.EndToStart
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(
                when (dir) { SwipeToDismissBoxValue.StartToEnd -> c.accentBg; SwipeToDismissBoxValue.EndToStart -> c.badBg; else -> Color.Transparent }
            ).padding(horizontal = 20.dp),
            contentAlignment = if (deleting) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            if (dir != SwipeToDismissBoxValue.Settled) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (deleting) Icons.Default.Delete else Icons.Default.Edit, contentDescription = null, tint = if (deleting) c.bad else c.accent)
                FcText(if (deleting) "Delete" else "Edit", 14.sp, FontWeight.Bold, if (deleting) c.bad else c.accent)
            }
        }
    }) { content() }
}

/** One match on the timeline: the dot in the result's colour, the opponent, the score, the four figures. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MatchTimelineRow(m: MatchResult, state: AppState, open: Boolean, onToggle: () -> Unit, onLineup: () -> Unit, onDelete: (() -> Unit)?, archived: Boolean) {
    val c = FcTheme.colors
    val (fg, bg) = toneColors(resultTone(m.resultBadge))
    Row(Modifier.fillMaxWidth().background(c.bg), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(12.dp).padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Box(Modifier.size(10.dp).clip(CircleShape).background(fg)) }
            Box(Modifier.width(1.dp).height(60.dp).background(c.border))
        }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(c.surface).clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CompLogo(m.competition, 38.dp)
                Column(Modifier.weight(1f)) {
                    FcText("${if (m.isHome) "vs" else "at"} ${m.opponentName}", 16.sp, FontWeight.SemiBold, maxLines = 1)
                    Muted(listOf(Dates.dayMonth(m.matchDate), m.competition, m.stage).filter { it.isNotBlank() }.joinToString(" · "), 12.sp, maxLines = 2)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Figure("${m.goalsFor}–${m.goalsAgainst}", 30.sp, fg, FontWeight.SemiBold)
                    if (m.homePenalties > 0 || m.awayPenalties > 0) {
                        val (p, q) = if (m.isHome) m.homePenalties to m.awayPenalties else m.awayPenalties to m.homePenalties
                        Muted("$p–$q pens", 11.sp)
                    } else if (m.isExtraTime) Muted("AET", 11.sp)
                }
            }
            MatchFiguresRow(m)
            if (open) {
                if (m.goalDetails.isNotEmpty()) { HRule(); GoalLines(m, state) }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (m.motmName.isNotBlank()) Pill("MOTM: ${m.motmName}${if (m.motmIsOpponent) " (opponent)" else ""}", Tone.WARN)
                    if (m.statsEstimated) Pill("Estimated stats", Tone.WARN, warn = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FcButton(if (archived) "Lineup & ratings" else "Lineup & stats", onLineup, kind = ButtonKind.SECONDARY, icon = Icons.Default.Groups, small = true)
                    if (onDelete != null) FcButton("Delete", onDelete, kind = ButtonKind.DANGER, icon = Icons.Default.Delete, small = true)
                }
            } else if (m.statsEstimated) {
                Pill("Estimated stats", Tone.WARN, warn = true)
            }
        }
    }
}

/** A month of the season: played days in the result's colour, fixtures ringed in the accent. */
@Composable
fun SeasonCalendar(season: Int, matches: List<MatchResult>, fixtures: List<Fixture>, onMatch: (MatchResult) -> Unit) {
    val c = FcTheme.colors
    val latest = (matches.map { it.matchDate } + fixtures.take(1).map { it.date }).filter { Dates.isDate(it) }.maxOrNull()
    val first = Dates.parse(latest) ?: Dates.Ymd(season, 8, 1)
    var ym by remember(season) { mutableStateOf(first.year * 12 + first.month - 1) }
    val year = ym / 12
    val month = ym % 12 + 1
    val byDate = matches.groupBy { it.matchDate }
    val fixtureDates = fixtures.map { it.date }.toSet()
    val firstIso = "%04d-%02d-01".format(year, month)
    val lead = ((Dates.weekday(firstIso) ?: 0) + 1) % 7      // Sunday first
    val days = Dates.daysInMonth(year, month)
    val minYm = season * 12 + 6
    val maxYm = (season + 1) * 12 + 5
    FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
        SectionHeader("Fixture Calendar", icon = Icons.Default.CalendarMonth)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FcIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", { if (ym > minYm) ym -= 1 }, size = 40.dp)
            FcText("${Dates.MONTHS[month - 1]} $year", 15.sp, FontWeight.Bold, modifier = Modifier.weight(1f), align = TextAlign.Center)
            FcIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", { if (ym < maxYm) ym += 1 }, size = 40.dp)
        }
        Row(Modifier.fillMaxWidth()) { listOf("S", "M", "T", "W", "T", "F", "S").forEach { Muted(it, 11.sp, Modifier.weight(1f), weight = FontWeight.SemiBold, align = TextAlign.Center) } }
        val cells = List(lead) { 0 } + (1..days).toList()
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { d ->
                    if (d == 0) Spacer(Modifier.weight(1f).height(38.dp))
                    else {
                        val iso = "%04d-%02d-%02d".format(year, month, d)
                        val m = byDate[iso]?.firstOrNull()
                        val isFixture = iso in fixtureDates
                        val (fg, bg) = if (m != null) toneColors(resultTone(m.resultBadge)) else c.muted to Color.Transparent
                        Box(
                            Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(19.dp)).background(bg)
                                .let { if (isFixture && m == null) it.border(1.5.dp, c.accent, RoundedCornerShape(19.dp)) else it }
                                .clickable(enabled = m != null) { m?.let(onMatch) },
                            contentAlignment = Alignment.Center
                        ) { FcText("$d", 13.sp, if (m != null || isFixture) FontWeight.Bold else FontWeight.Normal, if (m != null) fg else if (isFixture) c.accent else c.muted) }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A fixture from the game's list: the date box, the competition, the opponent with his position and form. */
@Composable
fun FixtureRow(f: Fixture, table: List<com.fctracker.career.TableRow>, onClick: () -> Unit) {
    val c = FcTheme.colors
    val pos = table.indexOfFirst { it.club.equals(f.opponent, true) }
    val form = table.getOrNull(pos)?.form.orEmpty()
    val d = Dates.parse(f.date)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.width(50.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Muted(Dates.weekday(f.date)?.let { Dates.WEEKDAYS_SHORT[it] } ?: "", 11.sp)
            FcText(d?.let { "${it.day} ${Dates.MONTHS_SHORT[it.month - 1]}" } ?: f.date, 15.sp, FontWeight.SemiBold, maxLines = 1)
            if (f.kickOff.isNotBlank()) Mono(f.kickOff, 11.sp, c.muted, FontWeight.Normal)
        }
        CompLogo(f.competition, 34.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FcText("${if (f.isHome) "vs" else "at"} ${f.opponent}", 15.sp, FontWeight.SemiBold, maxLines = 1)
            Muted(listOfNotNull(f.round.ifBlank { f.competition }, if (pos >= 0) "${f.opponent} ${Milestones.ordinal(pos + 1)}" else null).joinToString(" · "), 12.sp, maxLines = 1)
            if (form.isNotBlank()) FormDots(form.takeLast(5), Modifier.padding(top = 4.dp))
        }
        if (f.source != Source.GAME) Tag("Added by you", Tone.MUTED)
    }
}

/** Add or change a fixture (console, or a match the game's list does not have). */
@Composable
fun FixtureDialog(state: AppState, initial: Fixture?, onDismiss: () -> Unit, onSave: (Fixture, Boolean) -> Unit, onDelete: (() -> Unit)?) {
    var home by remember { mutableStateOf(initial?.isHome ?: true) }
    var opponent by remember { mutableStateOf(initial?.opponent ?: "") }
    var competition by remember { mutableStateOf(initial?.competition ?: state.leagueName) }
    var date by remember { mutableStateOf(initial?.date ?: CareerOps.today(state)) }
    var kickOff by remember { mutableStateOf(initial?.kickOff ?: "15:00") }
    var round by remember { mutableStateOf(initial?.round ?: "") }
    val clubs = remember(state) { (state.leagueTables.flatMap { t -> t.rows.map { it.club } } + state.recentMatches.map { it.opponentName }).distinct().filter { !it.equals(state.clubName, true) } }
    val competitions = remember(state.country, state.leagueName) { (listOf(state.leagueName) + getCompetitionsForCountry(state.country)).filter { it.isNotBlank() }.distinct() }
    fun build() = Fixture(id = initial?.id ?: java.util.UUID.randomUUID().toString(), date = date, kickOff = kickOff.trim(), competition = competition, round = round.trim(),
        opponent = opponent.trim(), isHome = home, source = initial?.source ?: Source.MANUAL, gameKey = initial?.gameKey ?: "")
    val ok = opponent.isNotBlank() && Dates.isDate(date)
    FcDialog(if (initial == null) "Add fixture" else "Edit fixture", onDismiss, actions = {
        if (onDelete != null) FcButton("Delete", onDelete, kind = ButtonKind.DANGER, small = true)
        Spacer(Modifier.weight(1f))
        FcButton("Cancel", onDismiss, kind = ButtonKind.SECONDARY, small = true)
        if (initial == null) FcButton("Add another", { onSave(build(), true); opponent = ""; round = "" }, kind = ButtonKind.SECONDARY, small = true, enabled = ok)
        FcButton(if (initial == null) "Add" else "Save", { onSave(build(), false) }, small = true, enabled = ok)
    }) {
        Segmented(listOf("Home", "Away"), if (home) 0 else 1, { home = it == 0 })
        FcField("Opponent", opponent, { opponent = it }, placeholder = "Hull City")
        val suggestions = if (opponent.length >= 2) clubs.filter { it.contains(opponent, true) && !it.equals(opponent, true) }.take(4) else emptyList()
        suggestions.forEach { s -> FcText(s, 14.sp, color = FcTheme.colors.accent, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { opponent = s }.padding(horizontal = 8.dp, vertical = 6.dp)) }
        FcSelectField("Competition", competition, competitions, { competition = it })
        FieldPair({ m -> FcDateField("Date", date, { date = it }, m) }, { m -> FcField("Kick-off", kickOff, { kickOff = it }, m, placeholder = "15:00") })
        FcField("Round or matchday", round, { round = it }, placeholder = "Matchday 15")
        Muted("The next fixture shows on Home. Add a few at a time, or the whole list from the game's calendar screen.", 11.sp)
    }
}

/** An archived match's lineup and ratings, read-only. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MatchDetailDialog(m: MatchResult, state: AppState, onDismiss: () -> Unit, onHeadToHead: (() -> Unit)? = null) {
    val c = FcTheme.colors
    val players = state.players + state.historicalSeasons.flatMap { it.players }
    val ordered = LineupOrder.order(m.playerPerformances, m.substitutions, { p -> p.position ?: players.firstOrNull { it.id == p.playerId }?.primaryPosition }, { p -> lineupNameOf(p, players, m) })
    FcDialog("Lineup & stats", onDismiss, actions = {
        if (onHeadToHead != null) FcButton("Head to head", onHeadToHead, kind = ButtonKind.SECONDARY, small = true)
        FcButton("Close", onDismiss, small = true)
    }) {
        Muted("${m.opponentName} (${if (m.isHome) "H" else "A"}) · ${m.goalsFor}–${m.goalsAgainst} · ${Dates.full(m.matchDate)}", 12.sp)
        if (ordered.isEmpty()) Muted("No lineup was logged for this match.", 13.sp)
        ordered.forEach { p ->
            val name = lineupNameOf(p, players, m)
            val assists = p.assists + m.playerPerformances.flatMap { it.goalAssisters }.count { it == p.playerId }
            val isMotm = p.isMotm || (m.motmName.isNotBlank() && !m.motmIsOpponent && m.motmName.equals(name, true))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FcText((p.position ?: players.firstOrNull { it.id == p.playerId }?.primaryPosition ?: "").uppercase(), 11.sp, FontWeight.Bold, c.muted, Modifier.width(30.dp))
                Headshot(players.firstOrNull { it.id == p.playerId }?.headshotUrl, name, 34.dp)
                Column(Modifier.weight(1f)) {
                    FcText(name, 14.sp, FontWeight.SemiBold, maxLines = 1)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isMotm) LineupSymbol("MOTM", c.warn) { Icon(Icons.Default.Star, null, tint = c.warn, modifier = Modifier.size(14.dp)) }
                        if (p.goals > 0) LineupSymbol("${p.goals}", c.muted) { Icon(Icons.Default.SportsSoccer, null, tint = c.muted, modifier = Modifier.size(14.dp)) }
                        if (assists > 0) LineupSymbol(if (assists == 1) "1 assist" else "$assists assists", c.muted) { Icon(SoccerBootIcon, null, tint = c.muted, modifier = Modifier.size(14.dp)) }
                        if (p.saves > 0) LineupSymbol("${p.saves} saves", c.muted) { Icon(Icons.Default.Shield, null, tint = c.muted, modifier = Modifier.size(14.dp)) }
                        if (p.cleanSheet) LineupSymbol("Clean sheet", c.good) { Icon(Icons.Default.Check, null, tint = c.good, modifier = Modifier.size(14.dp)) }
                        if (p.yellowCard) LineupSymbol("Yellow card", c.warn) { BookingCard(false, 14.dp) }
                        if (p.redCard) LineupSymbol("Red card", c.bad) { BookingCard(true, 14.dp) }
                        p.subInMinute?.let { mm -> LineupSymbol("On $mm'", c.good) { Icon(Icons.Default.SwapVert, null, tint = c.good, modifier = Modifier.size(14.dp)) } }
                        p.subOutMinute?.let { mm -> LineupSymbol("Off $mm'", c.muted) { Icon(Icons.Default.SwapVert, null, tint = c.muted, modifier = Modifier.size(14.dp)) } }
                    }
                }
                if (p.rating > 0) RatingChip(p.rating)
            }
        }
        Muted("In an archived season the ratings, goals and cards are shown but cannot be changed.", 11.sp)
    }
}
