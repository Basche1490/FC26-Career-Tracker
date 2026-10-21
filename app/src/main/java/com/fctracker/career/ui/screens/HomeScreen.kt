package com.fctracker.career.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.AppState
import com.fctracker.career.MainViewModel
import com.fctracker.career.Objective
import com.fctracker.career.Platform
import com.fctracker.career.feeLabel
import com.fctracker.career.isIncomingDeal
import com.fctracker.career.logic.CareerOps
import com.fctracker.career.logic.Competitions
import com.fctracker.career.logic.Dates
import com.fctracker.career.logic.HomeStory
import com.fctracker.career.logic.LeagueTables
import com.fctracker.career.logic.Milestones
import com.fctracker.career.logic.SeasonFacts
import com.fctracker.career.logic.SeasonView
import com.fctracker.career.logic.ZoneKind
import com.fctracker.career.telemetry.ContractTime
import com.fctracker.career.telemetry.Level
import com.fctracker.career.ui.components.ArchivedBanner
import com.fctracker.career.ui.components.ButtonKind
import com.fctracker.career.ui.components.ClubCrest
import com.fctracker.career.ui.components.CompLogo
import com.fctracker.career.ui.components.Dot
import com.fctracker.career.ui.components.Fab
import com.fctracker.career.ui.components.FcBottomSheet
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.FcCard
import com.fctracker.career.ui.components.FcIconButton
import com.fctracker.career.ui.components.FcPage
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.Figure
import com.fctracker.career.ui.components.FormStrip
import com.fctracker.career.ui.components.HRule
import com.fctracker.career.ui.components.Headshot
import com.fctracker.career.ui.components.KeyValue
import com.fctracker.career.ui.components.LeagueChart
import com.fctracker.career.ui.components.LocalOpenDrawer
import com.fctracker.career.ui.components.Mono
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.Notice
import com.fctracker.career.ui.components.Pill
import com.fctracker.career.ui.components.QuietLabel
import com.fctracker.career.ui.components.ResultBadge
import com.fctracker.career.ui.components.SeasonChip
import com.fctracker.career.ui.components.SeasonToggle
import com.fctracker.career.ui.components.SectionHeader
import com.fctracker.career.ui.components.StatCell
import com.fctracker.career.ui.components.StatTile
import com.fctracker.career.ui.components.Tag
import com.fctracker.career.ui.components.Tone
import com.fctracker.career.ui.components.toneColors
import com.fctracker.career.ui.theme.FcTheme

/** Home ("Story"): the club, a greeting about the week, the next match, the last result and the season so far. */
@Composable
fun HomeScreen(viewModel: MainViewModel, nav: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val health by viewModel.health.collectAsState()
    val view = remember(state) { SeasonView.of(state) }
    val console = state.platform == Platform.CONSOLE
    var showPicker by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showMatchForm by remember { mutableStateOf(false) }
    var noArchiveYet by remember { mutableStateOf(false) }

    LaunchedEffect(console) { if (!console) viewModel.fetchLiveTelemetry() }
    val setupPrompt by viewModel.setupPrompt.collectAsState()
    LaunchedEffect(setupPrompt) {
        if (setupPrompt) {
            viewModel.consumeSetupPrompt()
            if (!console) nav("setup")
        }
    }

    val isLeague = remember(view.leagueName) { Competitions.isLeagueFn(view.leagueName) }
    val matches = view.matches
    val chronological = remember(matches) { matches.withIndex().sortedWith(compareBy<IndexedValue<com.fctracker.career.MatchResult>> { it.value.matchDate }.thenByDescending { it.index }).map { it.value } }
    val last = chronological.lastOrNull()
    val table = view.ownTable
    val sorted = remember(table) { table?.let { LeagueTables.sorted(it.rows) }.orEmpty() }
    val clubs = sorted.size
    val positions = remember(matches) { SeasonFacts.leaguePositions(matches, isLeague) }

    FcPage(
        header = {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (view.isArchived) ArchivedBanner("${view.label} · ${view.leagueName}", { viewModel.setViewingSeasonYear(null) })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FcIconButton(Icons.Default.Menu, "Open menu", LocalOpenDrawer.current, border = false)
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (view.isArchived) SeasonChip(view.label)
                        else if (!console) StatusPill(health.level, health.pill, onClick = { nav("connection") })
                        SeasonToggle(
                            archived = view.isArchived,
                            onCurrent = { viewModel.setViewingSeasonYear(null) },
                            onArchived = { if (state.historicalSeasons.isEmpty()) noArchiveYet = true else showPicker = true }
                        )
                    }
                }
            }
        },
        fab = if (!view.isArchived) ({ Fab("Log match", { showMatchForm = true }) }) else null
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ClubCrest(state.clubName, state.clubCrestFile, state.clubCrestUrl, state.isExistingTeam, 40.dp, onClick = { showHistory = true })
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    FcText(state.clubName, 17.sp, FontWeight.SemiBold, maxLines = 1)
                    Muted("${view.leagueName} · ${state.country} · ${state.managerName}", 12.sp, maxLines = 1)
                }
            }
        }
        if (view.isArchived) {
            archivedHome(state, view, sorted, positions, nav)
            return@FcPage
        }

        // Greeting
        item {
            val story = HomeStory.story(state.managerName, matches, view.players, isLeague)
            val today = ContractTime.referenceDate(state)
            val md = matches.count { isLeague(it.competition) }
            val total = if (clubs > 1) (clubs - 1) * 2 else 0
            val dateLine = listOfNotNull(
                Dates.longWeekday(today).takeIf { it.isNotBlank() },
                if (md > 0) "Matchday $md${if (total > 0) " of $total" else ""}" else null
            ).joinToString(" · ")
            Column(Modifier.padding(start = 2.dp, end = 2.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dateLine.isNotBlank()) Muted(dateLine, 13.sp)
                FcText(story.headline, 32.sp, FontWeight.SemiBold, lineHeight = 35.sp)
                FcText(story.summary, 15.sp, color = FcTheme.colors.muted, lineHeight = 21.sp)
            }
        }
        if (CareerOps.worldReminderDue(state)) {
            item {
                Notice(Tone.INFO, "June: fill in Around the World", "The season's finals are over. Add the league and cup winners so the page (and next year's recap) has them.",
                    actions = {
                        FcButton("Add winners", { nav("world") }, small = true)
                        FcButton("Not now", { viewModel.dismissWorldReminder(CareerOps.worldSeasonToFill(state)) }, kind = ButtonKind.GHOST, small = true)
                    })
            }
        }
        item { NextMatchCard(state, sorted, console, onAdd = { nav("matches") }) }
        if (last != null) item { LastResultCard(last, state) }
        item { SeasonNumbersCard(state, view, sorted, isLeague) }
        if (chronological.isNotEmpty()) item { RecentFormCard(chronological.takeLast(5), onViewAll = { nav("matches") }) }
        item {
            val today = ContractTime.referenceDate(state)
            val attention = ContractTime.attentionList(view.players.filter { it.status == "Active" || it.status == "Loaned In" || it.status.isBlank() }, today)
            if (attention.isNotEmpty()) {
                val soonest = attention.mapNotNull { p -> listOfNotNull(ContractTime.contractChip(p, today)?.months, ContractTime.loanChip(p, today)?.months).minOrNull() }.minOrNull()
                val c = FcTheme.colors
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.warnBg).clickable { nav("squad") }.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = c.warn, modifier = Modifier.size(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        FcText("${attention.size} ${if (attention.size == 1) "contract needs" else "contracts need"} attention", 13.sp, FontWeight.Bold, c.warn)
                        FcText("The earliest ends in ${soonest?.let { if (it <= 0) "under a month" else "$it ${if (it == 1) "month" else "months"}" } ?: "a few months"}. Tap to see them in the squad.", 12.sp, color = c.text.copy(alpha = 0.9f))
                    }
                }
            }
        }
        if (positions.size >= 2) {
            item {
                FcCard(Modifier.fillMaxWidth(), padding = 20.dp, gap = 10.dp) {
                    SectionHeader("League position", sub = "Last ${positions.size} matchdays", icon = Icons.Default.ShowChart)
                    LeagueChart(positions, maxOf(clubs, 20))
                    Muted("Tap a dot to see the matchday and position, for example \"MD ${positions.size}: Position ${positions.last()}\".", 11.sp)
                }
            }
        }
        item { SquadSummaryCard(view) }
        item { ProspectsCard(state, view) }
        item { ObjectivesCard(viewModel, view.objectives, archived = false) }
        if (!console) item { LiveFeedCard(viewModel, state, onDetails = { nav("connection") }, onSetup = { nav("setup") }) }
        item { QuickNav(nav) }
    }

    if (showPicker) {
        SeasonPickSheet(state, onPick = { viewModel.setViewingSeasonYear(it); showPicker = false }, onDismiss = { showPicker = false })
    }
    if (noArchiveYet) {
        com.fctracker.career.ui.components.FcDialog("No archived seasons yet", { noArchiveYet = false }, actions = {
            FcButton("OK", { noArchiveYet = false }, small = true)
        }) { Muted("Only finished seasons are archived. When this season ends (Season Summary · End season), it appears here.", 14.sp) }
    }
    if (showHistory) {
        ClubHistoryDialog(viewModel, state, onDismiss = { showHistory = false }, onWrite = { showHistory = false; nav("club_history_edit") })
    }
    if (showMatchForm) {
        MatchFormModal(viewModel = viewModel, state = state, initialMatch = null, onDismiss = { showMatchForm = false }, onSave = { m ->
            viewModel.addMatchResult(m)
            showMatchForm = false
        })
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun NextMatchCard(state: AppState, table: List<com.fctracker.career.TableRow>, console: Boolean, onAdd: () -> Unit) {
    val next = CareerOps.nextFixture(state)
    FcCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            QuietLabel("Next match", Modifier.weight(1f))
            if (next != null) Muted(if (next.source == "game") "From your game" else "Added by you", 12.sp)
        }
        if (next == null) {
            Muted(if (console) "No fixtures yet. On console you add them yourself, a few at a time or the whole list from the game's calendar." else "No fixtures yet. They arrive from your game with the next report from the PC.", 13.sp)
            if (console) FcButton("Add fixture", onAdd, kind = ButtonKind.SECONDARY, small = true)
            return@FcCard
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CompLogo(next.competition, 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FcText("${if (next.isHome) "vs" else "at"} ${next.opponent}", 18.sp, FontWeight.SemiBold, maxLines = 1)
                Muted(listOf(Dates.weekdayDayMonth(next.date), next.kickOff, next.round).filter { it.isNotBlank() }.joinToString(" · "), 12.sp, maxLines = 1)
            }
        }
        val pos = table.indexOfFirst { it.club.equals(next.opponent, true) }
        val form = table.getOrNull(pos)?.form.orEmpty()
        if (pos >= 0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Muted("${next.opponent} are ${Milestones.ordinal(pos + 1)}${if (form.isNotBlank()) ", form oldest first" else ""}", 12.sp, Modifier.weight(1f))
                if (form.isNotBlank()) FormStrip(form.map { it.toString() }.takeLast(5), 20.dp, 4.dp)
            }
        }
    }
}

@Composable
private fun LastResultCard(m: com.fctracker.career.MatchResult, state: AppState) {
    val (fg, _) = toneColors(com.fctracker.career.ui.components.resultTone(m.resultBadge))
    FcCard(Modifier.fillMaxWidth(), padding = 20.dp, gap = 14.dp, glow = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            QuietLabel("Last result", Modifier.weight(1f))
            if (m.statsEstimated) Pill("Estimated stats", Tone.WARN, warn = true)
        }
        FcText(matchHeadline(m), 21.sp, FontWeight.SemiBold, lineHeight = 26.sp)
        Muted(listOf(m.competition, m.stage, Dates.dayMonth(m.matchDate)).filter { it.isNotBlank() }.joinToString(" · "), 12.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Figure("${m.goalsFor} – ${m.goalsAgainst}", 54.sp, fg, FontWeight.SemiBold, Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Muted(if (m.isExtraTime) "After extra time" else "Full time", 12.sp)
                Muted(if (m.isHome) "Home" else "Away", 12.sp)
            }
        }
        MatchFiguresRow(m)
        if (m.goalDetails.isNotEmpty()) {
            HRule()
            GoalLines(m, state)
        }
    }
}

@Composable
private fun SeasonNumbersCard(state: AppState, view: SeasonView, table: List<com.fctracker.career.TableRow>, isLeague: (String) -> Boolean) {
    val league = view.matches.filter { isLeague(it.competition) }
    val own = table.firstOrNull { it.isUser || it.club.equals(state.clubName, true) }
    val pos = table.indexOf(own).takeIf { it >= 0 }?.plus(1) ?: SeasonFacts.leaguePositions(view.matches, isLeague).lastOrNull()
    val split = SeasonFacts.split(league)
    val played = own?.played ?: split.played
    val points = own?.points ?: split.points
    val gf = own?.goalsFor ?: split.goalsFor
    val ga = own?.goalsAgainst ?: split.goalsAgainst
    val w = own?.won ?: split.won
    val d = own?.drawn ?: split.drawn
    val l = own?.lost ?: split.lost
    val total = if (table.size > 1) (table.size - 1) * 2 else 0
    val gd = gf - ga
    FcCard(Modifier.fillMaxWidth(), padding = 20.dp, gap = 16.dp) {
        SectionHeader("Season in numbers", sub = if (total > 0) "After $played of $total matches" else "After $played league matches", icon = Icons.Default.TrackChanges)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCell("Position", pos?.let { Milestones.ordinal(it) } ?: "–", view.leagueName.replace("EFL ", ""), Modifier.weight(1f))
            StatCell("Points", "$points", "of ${played * 3} possible", Modifier.weight(1f))
            StatCell("Goal diff.", (if (gd > 0) "+" else "") + gd, "$gf for, $ga against", Modifier.weight(1f), tone = if (gd > 0) Tone.GOOD else if (gd < 0) Tone.BAD else null)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCell("Record", "$w-$d-$l", "won, drawn, lost", Modifier.weight(1f))
            StatCell("Per match", if (played > 0) String.format(java.util.Locale.US, "%.2f", points.toDouble() / played) else "–", "points", Modifier.weight(1f))
            StatCell("Matches", "$played", if (total > 0) "${(total - played).coerceAtLeast(0)} to play" else "league", Modifier.weight(1f))
        }
    }
}

@Composable
private fun RecentFormCard(last5: List<com.fctracker.career.MatchResult>, onViewAll: () -> Unit) {
    val c = FcTheme.colors
    FcCard(Modifier.fillMaxWidth(), padding = 20.dp, gap = 14.dp) {
        SectionHeader("Recent form", sub = "Last ${last5.size} matches, oldest first", icon = Icons.Default.ShowChart, action = "View all", onAction = onViewAll)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            last5.forEach { m ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.04f)).clickable(onClick = onViewAll).padding(vertical = 10.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ResultBadge(m.resultBadge, 28.dp)
                    Mono("${m.goalsFor}–${m.goalsAgainst}", 14.sp)
                    Muted("${if (m.isHome) "vs" else "at"} ${shortName(m.opponentName)}", 11.sp, maxLines = 1)
                    Muted(Dates.dayMonth(m.matchDate), 10.sp, maxLines = 1)
                }
            }
        }
    }
}

/** "LEI" for Leicester City: the first three letters of the main word. */
fun shortName(club: String): String {
    val words = club.split(" ").filter { it.isNotBlank() && it.lowercase() !in setOf("fc", "afc", "cf", "sc", "the", "de", "real", "club", "ac", "as", "ss", "ssc", "vfb", "vfl", "tsg", "rb", "1.") }
    val w = words.firstOrNull() ?: club
    return w.take(3).uppercase()
}

@Composable
private fun SquadSummaryCard(view: SeasonView) {
    val players = view.players.filter { it.status.isBlank() || it.status == "Active" || it.status == "Loaned In" }
    val totals = SeasonFacts.allTotals(players, view.matches, view.squadStats)
    val scorer = totals.filter { it.goals > 0 }.maxByOrNull { it.goals }
    val assister = totals.filter { it.assists > 0 }.maxByOrNull { it.assists }
    FcCard(Modifier.fillMaxWidth(), padding = 20.dp, gap = 14.dp) {
        SectionHeader("Squad Summary", icon = Icons.Default.Groups)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Size", "${players.size}", Modifier.weight(1f))
            StatTile("Avg age", if (players.isEmpty()) "–" else String.format(java.util.Locale.US, "%.1f", players.map { it.age }.average()), Modifier.weight(1f))
            StatTile("Avg OVR", if (players.isEmpty()) "–" else String.format(java.util.Locale.US, "%.1f", players.map { it.overall }.average()), Modifier.weight(1f))
        }
        KeyValue("Top scorer", scorer?.let { "${it.player.displayName} · ${it.goals} goals" } ?: "–", mono = false)
        KeyValue("Top assister", assister?.let { "${it.player.displayName} · ${it.assists} assists" } ?: "–", mono = false)
    }
}

@Composable
private fun ProspectsCard(state: AppState, view: SeasonView) {
    val c = FcTheme.colors
    val prospects = view.players.filter { it.age <= 22 && (it.status.isBlank() || it.status == "Active" || it.status == "Loaned In") }.sortedByDescending { it.overall }.take(5)
    if (prospects.isEmpty()) return
    FcCard(Modifier.fillMaxWidth(), padding = 20.dp, gap = 12.dp) {
        SectionHeader("Top Prospects", sub = "Players aged 22 or under", icon = Icons.Default.Star)
        prospects.forEach { p ->
            val start = (p.startingOverall.takeIf { it > 0 } ?: p.overall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Headshot(p.headshotUrl, p.displayName, 32.dp, youth = p.isPromoted)
                FcText(p.displayName, 13.sp, FontWeight.SemiBold, modifier = Modifier.weight(0.42f), maxLines = 1)
                Box(Modifier.weight(0.38f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.surface2)) {
                    val lo = minOf(start, p.overall) / 100f
                    val hi = maxOf(start, p.overall) / 100f
                    Box(Modifier.fillMaxWidth(lo).fillMaxHeight().background(c.muted.copy(alpha = 0.5f)))
                    if (hi > lo) {
                        Row(Modifier.fillMaxWidth(hi).fillMaxHeight()) {
                            Spacer(Modifier.weight(lo.coerceAtLeast(0.001f)))
                            Box(Modifier.weight((hi - lo).coerceAtLeast(0.001f)).fillMaxHeight().background(if (p.overall >= start) c.good else c.bad))
                        }
                    } else {
                        Box(Modifier.fillMaxWidth(lo).fillMaxHeight().background(c.warn.copy(alpha = 0.8f)))
                    }
                }
                Mono("$start → ${p.overall}", 12.sp, weight = FontWeight.SemiBold)
            }
        }
        Muted("Bar: starting OVR in grey; growth in green, decline in red, no change in amber.", 11.sp)
    }
}

/** Objectives for the season. An archived season shows the ones it ended with, read-only. */
@Composable
fun ObjectivesCard(viewModel: MainViewModel, objectives: List<Objective>, archived: Boolean) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Objective?>(null) }
    val c = FcTheme.colors
    FcCard(Modifier.fillMaxWidth(), padding = 20.dp, gap = 12.dp) {
        SectionHeader("Season Objectives", sub = if (archived) "Read-only" else null, icon = Icons.Default.TrackChanges, action = if (archived) null else "Add", onAction = if (archived) null else ({ adding = true }))
        if (objectives.isEmpty()) Muted(if (archived) "No objectives were recorded for this season." else "No objectives set.", 12.sp)
        objectives.forEach { o ->
            val status = when (o.status) { "Completed" -> c.good; "Failed" -> c.bad; else -> c.warn }
            val tone = when (o.priority) { "Critical", "High" -> Tone.WARN; "Medium" -> Tone.WARN; else -> Tone.INFO }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).let { if (archived) it else it.clickable { editing = o } }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Dot(status, 8.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    FcText(o.description, 13.sp, FontWeight.SemiBold)
                    Muted("${o.category} · ${o.status}", 11.sp)
                }
                Tag(o.priority, tone)
            }
        }
    }
    if (adding) ObjectiveDialog(null, { adding = false }, { viewModel.addObjective(it); adding = false }, null)
    editing?.let { cur -> ObjectiveDialog(cur, { editing = null }, { viewModel.updateObjective(it); editing = null }, { viewModel.deleteObjective(cur.id); editing = null }) }
}

@Composable
private fun LiveFeedCard(viewModel: MainViewModel, state: AppState, onDetails: () -> Unit, onSetup: () -> Unit) {
    val health by viewModel.health.collectAsState()
    val contact by viewModel.contact.collectAsState()
    val c = FcTheme.colors
    var refreshing by remember { mutableStateOf(false) }
    val neverSetUp = !contact.everConnected && !contact.reachable
    val feed = (state.liveEvents.take(4).map { it.title to it.message } +
        state.milestones.filter { it.season == state.startYear }.takeLast(1).map { "Milestone" to it.title }).take(4)
    FcCard(Modifier.fillMaxWidth(), padding = 20.dp, gap = 14.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Live feed", Modifier.weight(1f), icon = Icons.Default.Notifications)
            FcIconButton(Icons.Default.Refresh, "Check the connection now", { refreshing = true; viewModel.recheckConnection { refreshing = false } }, size = 40.dp)
        }
        Muted(if (health.level == Level.LIVE) "Receiving matches, fixtures, transfers and injuries from your PC. ${health.detail}" else listOf(health.headline, health.detail).filter { it.isNotBlank() }.joinToString(" "), 12.sp)
        feed.forEach { (t, m) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Dot(c.muted, 8.dp, Modifier.padding(top = 6.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    FcText(t, 13.sp, FontWeight.SemiBold)
                    Muted(m, 12.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (neverSetUp) {
                FcButton("Set up live sync", onSetup, small = true)
                FcButton("Enter address", onDetails, kind = ButtonKind.SECONDARY, small = true)
            } else {
                FcButton(if (health.level == Level.LIVE) "Details" else "Fix this", onDetails, kind = ButtonKind.SECONDARY, small = true)
            }
        }
    }
}

@Composable
private fun QuickNav(nav: (String) -> Unit) {
    val c = FcTheme.colors
    val items = listOf(
        Triple("Squad", Icons.Default.Groups, "squad"), Triple("Transfers", Icons.Default.SwapHoriz, "transfers"),
        Triple("Academy", Icons.Default.School, "academy"), Triple("Matches", Icons.AutoMirrored.Filled.List, "matches"),
        Triple("Season", Icons.Default.CalendarMonth, "season_summary"), Triple("Career", Icons.Default.EmojiEvents, "career_summary"),
        Triple("AI Assist", Icons.Default.SmartToy, "assistant"), Triple("Settings", Icons.Default.Settings, "settings")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FcText("Quick navigation", 17.sp, FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 2.dp))
        items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, icon, route) ->
                    Column(
                        Modifier.weight(1f).aspectRatio(1.05f).clip(RoundedCornerShape(18.dp)).background(c.surface).clickable(role = Role.Button) { nav(route) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = c.text, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(8.dp))
                        FcText(label, 12.sp, FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Archived Home
// ---------------------------------------------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.archivedHome(
    state: AppState, view: SeasonView, table: List<com.fctracker.career.TableRow>, positions: List<Int>, nav: (String) -> Unit
) {
    val s = view.archived ?: return
    val own = table.firstOrNull { it.isUser || it.club.equals(state.clubName, true) }
    val pos = table.indexOf(own).takeIf { it >= 0 }?.plus(1) ?: s.finalPosition
    val clubs = table.size.takeIf { it > 0 } ?: 24
    val zone = LeagueTables.zoneOf(view.leagueName, clubs, pos)
    val title = when {
        pos == 1 -> "Champions"
        zone == ZoneKind.PROMOTION -> "Promoted"
        else -> "Finished ${Milestones.ordinal(pos)}"
    }
    item {
        FcCard(Modifier.fillMaxWidth(), padding = 20.dp, gap = 10.dp, glow = true) {
            QuietLabel("${view.label} · ${view.leagueName}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FcText(title, 30.sp, FontWeight.SemiBold, lineHeight = 33.sp)
                    Muted("Finished ${Milestones.ordinal(pos)}${if (zone == ZoneKind.PROMOTION || pos == 1 && LeagueTables.isSecondTier(view.leagueName)) " · promoted" else ""}${s.trophies.takeIf { it.isNotEmpty() }?.let { " · " + it.joinToString(", ") } ?: ""}", 13.sp)
                }
                if (pos == 1 || s.trophies.isNotEmpty()) Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = FcTheme.colors.warn, modifier = Modifier.size(34.dp))
            }
        }
    }
    item {
        val isLeague = Competitions.isLeagueFn(view.leagueName)
        val split = SeasonFacts.split(view.matches.filter { isLeague(it.competition) })
        val played = own?.played ?: split.played
        val points = own?.points ?: split.points
        val gf = own?.goalsFor ?: split.goalsFor
        val ga = own?.goalsAgainst ?: split.goalsAgainst
        val top = SeasonFacts.allTotals(view.players, view.matches, view.squadStats).maxByOrNull { it.goals }
        FcCard(Modifier.fillMaxWidth(), padding = 20.dp, gap = 16.dp) {
            SectionHeader("Season in numbers", sub = "Final, after $played matches", icon = Icons.Default.TrackChanges)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCell("Position", Milestones.ordinal(pos), view.leagueName.replace("EFL ", ""), Modifier.weight(1f))
                StatCell("Points", "$points", "of ${played * 3}", Modifier.weight(1f))
                StatCell("Goal diff.", (if (gf - ga > 0) "+" else "") + (gf - ga), "$gf for, $ga against", Modifier.weight(1f), tone = if (gf > ga) Tone.GOOD else null)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCell("Record", "${own?.won ?: split.won}-${own?.drawn ?: split.drawn}-${own?.lost ?: split.lost}", "won, drawn, lost", Modifier.weight(2f))
                StatCell("Top scorer", "${top?.goals ?: 0}", top?.player?.displayName ?: "–", Modifier.weight(1f))
            }
        }
    }
    val deals = view.transfers.filter { it.feeM > 0 }.sortedByDescending { it.feeM }.take(4)
    if (deals.isNotEmpty()) {
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 20.dp, gap = 12.dp) {
                SectionHeader("Top transfers", sub = view.label, icon = Icons.Default.SwapHoriz)
                deals.forEach { t ->
                    val inc = t.isIncomingDeal()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Headshot(t.headshotUrl.ifBlank { view.players.firstOrNull { it.name.equals(t.playerName, true) }?.headshotUrl.orEmpty() }, t.playerName, 40.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                FcText(t.playerName, 15.sp, FontWeight.SemiBold, maxLines = 1)
                                Tag(if (inc) "Signing" else "Sale", if (inc) Tone.INFO else Tone.GOOD)
                            }
                            Muted("${if (inc) "from" else "to"} ${t.fromToClub}", 12.sp, maxLines = 1)
                        }
                        FcText(t.feeLabel(state.currencySymbol), 14.sp, FontWeight.SemiBold, if (inc) FcTheme.colors.text else FcTheme.colors.good)
                    }
                }
            }
        }
    }
    if (positions.size >= 2) {
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 20.dp, gap = 10.dp) {
                SectionHeader("League position", sub = "${positions.size} matchdays", icon = Icons.Default.ShowChart)
                LeagueChart(positions, maxOf(clubs, 20))
            }
        }
    }
    item {
        FcCard(Modifier.fillMaxWidth(), padding = 20.dp, gap = 12.dp) {
            SectionHeader("Season objectives", sub = "Read-only", icon = Icons.Default.TrackChanges)
            if (view.objectives.isEmpty()) Muted("No objectives were recorded for this season.", 12.sp)
            view.objectives.forEach { o ->
                KeyValue(o.description, o.status, when (o.status) { "Completed" -> FcTheme.colors.good; "Failed" -> FcTheme.colors.bad; else -> FcTheme.colors.warn }, mono = false)
            }
        }
    }
    item { Muted("Every page you open from the menu now shows ${view.label}, with the blue sign on top. Back to current, or the Current switch, returns to ${SeasonView.seasonLabel(state.startYear)}.", 11.sp) }
}

// ---------------------------------------------------------------------------------------------------------------------
// The archived season picker
// ---------------------------------------------------------------------------------------------------------------------

@Composable
fun SeasonPickSheet(state: AppState, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = FcTheme.colors
    val seasons = state.historicalSeasons.sortedByDescending { it.year }
    var chosen by remember { mutableStateOf(state.viewingSeasonYear ?: seasons.firstOrNull()?.year) }
    FcBottomSheet("Choose an archived season", onDismiss) {
        Muted("Only finished seasons are archived. Every page then shows that season, read-only.", 12.sp)
        seasons.forEach { s ->
            val on = s.year == chosen
            val won = s.finalPosition == 1 || s.trophies.isNotEmpty()
            val line = buildString {
                append(if (s.finalPosition == 1) "Champions" else "Finished ${Milestones.ordinal(s.finalPosition)}")
                val played = s.matches.size
                if (played > 0) append(" · $played matches")
                if (s.trophies.isNotEmpty()) append(" · ${s.trophies.first()}")
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (on) c.accentBg else c.surface2).clickable(role = Role.RadioButton) { chosen = s.year }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Color.Transparent).let { m -> m }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (on) c.accent else c.muted))
                    Box(Modifier.size(18.dp).clip(CircleShape).background(if (on) c.accentBg.copy(alpha = 1f).let { c.surface2 } else c.surface2))
                    if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FcText("${SeasonView.seasonLabel(s.year)} · ${s.leagueName}", 15.sp, FontWeight.SemiBold)
                    Muted(line, 12.sp)
                }
                if (won) Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = c.warn, modifier = Modifier.size(18.dp))
            }
        }
        FcButton(chosen?.let { "Show ${SeasonView.seasonLabel(it)}" } ?: "Show", { chosen?.let(onPick) }, full = true, enabled = chosen != null)
        FcButton("Cancel", onDismiss, kind = ButtonKind.SECONDARY, full = true)
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Objectives dialog
// ---------------------------------------------------------------------------------------------------------------------

/** Add (initial == null) or edit an objective. Delete is offered only when editing, and asks first. */
@Composable
fun ObjectiveDialog(initial: Objective?, onDismiss: () -> Unit, onSave: (Objective) -> Unit, onDelete: (() -> Unit)?) {
    var title by remember { mutableStateOf(initial?.description ?: "") }
    var priority by remember { mutableStateOf(initial?.priority ?: "Medium") }
    var category by remember { mutableStateOf(initial?.category ?: "Domestic Success") }
    var status by remember { mutableStateOf(initial?.status ?: "In Progress") }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete && onDelete != null) {
        com.fctracker.career.ui.components.ConfirmDialog("Delete objective?", "\"${initial?.description.orEmpty()}\" will be removed from this season.", "Delete", onDelete, { confirmDelete = false })
    }
    com.fctracker.career.ui.components.FcDialog(if (initial == null) "Add objective" else "Edit objective", onDismiss, actions = {
        if (onDelete != null) FcButton("Delete", { confirmDelete = true }, kind = ButtonKind.DANGER, small = true)
        Spacer(Modifier.weight(1f))
        FcButton("Cancel", onDismiss, kind = ButtonKind.SECONDARY, small = true)
        FcButton(if (initial == null) "Add" else "Save", {
            onSave(Objective(id = initial?.id ?: java.util.UUID.randomUUID().toString(), category = category, priority = priority, description = title.trim(), status = status))
        }, small = true, enabled = title.isNotBlank())
    }) {
        com.fctracker.career.ui.components.FcSelectField("Category", category, listOf("Domestic Success", "Continental Success", "Brand Exposure", "Financial", "Youth Development"), { category = it })
        com.fctracker.career.ui.components.FcField("Objective", title, { title = it }, placeholder = "Reach the play-offs")
        com.fctracker.career.ui.components.FieldPair(
            { m -> com.fctracker.career.ui.components.FcSelectField("Priority", priority, listOf("Critical", "High", "Medium", "Low", "Very Low"), { priority = it }, m) },
            { m -> com.fctracker.career.ui.components.FcSelectField("Status", status, listOf("In Progress", "Completed", "Failed"), { status = it }, m) }
        )
    }
}
