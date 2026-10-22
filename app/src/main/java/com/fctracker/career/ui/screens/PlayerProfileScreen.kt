package com.fctracker.career.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import com.fctracker.career.MainViewModel
import com.fctracker.career.Platform
import com.fctracker.career.Player
import com.fctracker.career.TransferLog
import com.fctracker.career.logic.CareerOps
import com.fctracker.career.logic.Dates
import com.fctracker.career.logic.SeasonFacts
import com.fctracker.career.logic.SeasonView
import com.fctracker.career.telemetry.ContractTime
import com.fctracker.career.telemetry.Urgency
import com.fctracker.career.ui.components.ArchivedBanner
import com.fctracker.career.ui.components.ButtonKind
import com.fctracker.career.ui.components.ConfirmDialog
import com.fctracker.career.ui.components.EmptyCard
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.FcCard
import com.fctracker.career.ui.components.FcDialog
import com.fctracker.career.ui.components.FcField
import com.fctracker.career.ui.components.FcIconButton
import com.fctracker.career.ui.components.FcLabel
import com.fctracker.career.ui.components.FcPage
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.FcTopBar
import com.fctracker.career.ui.components.Figure
import com.fctracker.career.ui.components.KeyValue
import com.fctracker.career.ui.components.Mono
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.NavIcon
import com.fctracker.career.ui.components.NumberedHeadshot
import com.fctracker.career.ui.components.OvrBadge
import com.fctracker.career.ui.components.OvrChart
import com.fctracker.career.ui.components.Pill
import com.fctracker.career.ui.components.ProgressBar
import com.fctracker.career.ui.components.RatingChip
import com.fctracker.career.ui.components.SectionHeader
import com.fctracker.career.ui.components.StatTile
import com.fctracker.career.ui.components.Tone
import com.fctracker.career.ui.theme.FcTheme

/** A player's page: figures, rating by season, development, form, usage, career, the game's contract data, awards and notes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerProfileScreen(viewModel: MainViewModel, playerId: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val view = remember(state) { SeasonView.of(state) }
    val c = FcTheme.colors
    val p: Player? = view.players.firstOrNull { it.id == playerId } ?: view.academy.firstOrNull { it.id == playerId }
        ?: state.players.firstOrNull { it.id == playerId } ?: state.academyPlayers.firstOrNull { it.id == playerId }
        ?: state.historicalSeasons.flatMap { it.players }.lastOrNull { it.id == playerId }
    var editing by remember { mutableStateOf(false) }
    var addNote by remember { mutableStateOf(false) }
    var confirmAcademy by remember { mutableStateOf(false) }
    var confirmRetire by remember { mutableStateOf(false) }
    val console = state.platform == Platform.CONSOLE
    val eafc by viewModel.eafcPlayers.collectAsState()

    FcPage(header = {
        if (view.isArchived) androidx.compose.foundation.layout.Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            ArchivedBanner("${view.label} · ${view.leagueName}", { viewModel.setViewingSeasonYear(null) })
        }
        FcTopBar(p?.displayName ?: "Player", sub = if (view.isArchived) "Player profile · ${view.label}" else "Player profile", nav = NavIcon.BACK, onNav = onBack)
    }) {
        if (p == null) {
            item { EmptyCard("This player is no longer in the squad.") }
            return@FcPage
        }
        val today = if (view.isArchived) null else ContractTime.referenceDate(state)
        val t = SeasonFacts.totals(p, view.matches, view.squadStats)
        val positions = listOfNotNull(p.primaryPosition, p.secondaryPosition?.takeIf { it.isNotBlank() }).joinToString(" / ")
        item {
            FcCard(Modifier.fillMaxWidth(), gap = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    NumberedHeadshot(p.headshotUrl, p.displayName, p.number, 68.dp, youth = p.isAcademy)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FcText(p.displayName, 20.sp, FontWeight.Bold)
                        Muted(listOfNotNull(if (p.number > 0) "#${p.number}" else null, positions, "OVR ${p.overall}", "Age ${p.age}${if (view.isArchived) " (in ${view.label})" else ""}").joinToString(" · "), 13.sp)
                    }
                    OvrBadge(p.overall, 52.dp)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ContractTime.contractChip(p, today)?.let { Pill("Contract ${it.text} · ${ContractTime.monthYear(p.contractEnd)}", contractPillTone(it.urgency), warn = it.urgency != Urgency.OK) }
                    if (p.loanDirection != null) Pill("Loan ${if (p.loanDirection == "in") "from" else "to"} ${p.loanClub.orEmpty().ifBlank { "another club" }}${p.loanEnd?.let { " · until ${Dates.shortMonthYear(it)}" } ?: ""}", Tone.INFO)
                    if (p.isRegen && !p.regenOriginalName.isNullOrBlank()) Pill("Regen of ${p.regenOriginalName}", Tone.ACCENT)
                    if (p.isInjured) Pill("Injured${p.injuryReturnDate?.let { " · back ${Dates.dayMonth(it)}" } ?: ""}", Tone.BAD)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Apps", "${t.appearances}", Modifier.weight(1f))
                StatTile("Goals", "${t.goals}", Modifier.weight(1f), tone = Tone.ACCENT)
                StatTile("Assists", "${t.assists}", Modifier.weight(1f))
                StatTile("MOTMs", "${t.motm}", Modifier.weight(1f))
            }
        }
        // Rating by season
        item {
            val history = p.ratingHistory.filter { it.season < view.season }.sortedBy { it.season }
            val labels = history.map { SeasonView.seasonLabel(it.season).let { l -> l.substring(2) } } + SeasonView.seasonLabel(view.season).substring(2)
            val values = history.map { it.overall } + p.overall
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Overall rating by season", Modifier.weight(1f), icon = Icons.Default.ShowChart)
                    if (values.size >= 2) {
                        val d = values.last() - values.first()
                        Pill("${if (d >= 0) "+" else ""}$d since ${labels.first()}", if (d > 0) Tone.GOOD else if (d < 0) Tone.BAD else Tone.MUTED)
                    }
                }
                if (values.size >= 2) OvrChart(labels, values)
                else Mono("${p.overall} in ${view.label}", 14.sp)
                Muted(if (view.isArchived) "Up to the season you are viewing." else "Each season's rating is saved when the season ends; the current season shows today's rating.", 11.sp)
            }
        }
        // Development this season
        val start = p.seasonStartAttributes
        val now = p.attributes
        if (now.isNotEmpty() && start.isNotEmpty()) {
            item {
                val changes = now.mapNotNull { (k, v) -> start[k]?.let { s -> Triple(k, v, v - s) } }.filter { it.third != 0 }.sortedByDescending { kotlin.math.abs(it.third) }.take(6)
                val ovrDelta = p.overall - (p.startingOverall.takeIf { it > 0 } ?: p.overall)
                FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("Development this season", Modifier.weight(1f), icon = Icons.Default.ShowChart)
                        if (ovrDelta != 0) Pill("OVR ${if (ovrDelta > 0) "+" else ""}$ovrDelta", if (ovrDelta > 0) Tone.GOOD else Tone.BAD)
                    }
                    if (changes.isEmpty()) Muted("No attribute has changed since the start of the season.", 12.sp)
                    changes.forEach { (name, v, d) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Muted(name.replaceFirstChar { it.uppercase() }, 12.sp, Modifier.weight(1f))
                                Mono("$v", 14.sp, weight = FontWeight.SemiBold)
                                Mono("  ${if (d > 0) "+" else ""}$d", 13.sp, if (d > 0) c.good else c.bad, FontWeight.SemiBold)
                            }
                            ProgressBar(v.toFloat() - 40f, 59f, if (d > 0) c.good else c.bad, height = 6.dp)
                        }
                    }
                    Muted("From the game: today's attributes compared with the start of the season. The app keeps a copy at every season end.", 11.sp)
                }
            }
        }
        // Form
        if (t.lastRatings.isNotEmpty()) {
            item {
                FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                    SectionHeader(if (view.isArchived) "Form (last 5 of the season)" else "Form (last 5)", icon = Icons.Default.ShowChart)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        t.lastRatings.forEach { RatingChip(it) }
                        Muted("Avg ${String.format(java.util.Locale.US, "%.1f", t.lastRatings.average())}", 13.sp, weight = FontWeight.Bold)
                    }
                }
            }
        }
        // Usage
        if (t.appearances > 0 && (t.starts + t.subApps) > 0) {
            item {
                FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                    SectionHeader("Usage", icon = Icons.Default.ShowChart)
                    listOf(Triple("Started", t.starts, c.accent), Triple("Subbed", t.subApps, c.warn)).forEach { (k, n, col) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth()) { Muted(k, 12.sp, Modifier.weight(1f)); FcText("$n", 13.sp, FontWeight.Bold) }
                            ProgressBar(n.toFloat(), (t.starts + t.subApps).toFloat(), col, height = 8.dp)
                        }
                    }
                    p.squadRole?.let { Muted("Squad role promised in the game: $it.", 11.sp) }
                }
            }
        }
        // Career stats (every season up to the one shown)
        item {
            val seasons = state.historicalSeasons.filter { it.year < view.season }
            var apps = t.appearances; var goals = t.goals; var assists = t.assists; var cs = t.cleanSheets; var yc = t.yellowCards; var rc = t.redCards
            val ratings = mutableListOf<Pair<Double, Int>>()
            if (t.averageRating > 0) ratings += t.averageRating to t.appearances.coerceAtLeast(1)
            for (s in seasons) {
                val past = s.players.firstOrNull { it.id == p.id } ?: continue
                val pt = SeasonFacts.totals(past, s.matches, s.squadStats)
                apps += pt.appearances; goals += pt.goals; assists += pt.assists; cs += pt.cleanSheets; yc += pt.yellowCards; rc += pt.redCards
                if (pt.averageRating > 0) ratings += pt.averageRating to pt.appearances.coerceAtLeast(1)
            }
            val avg = if (ratings.isEmpty()) 0.0 else ratings.sumOf { it.first * it.second } / ratings.sumOf { it.second }
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                SectionHeader("Career stats", sub = "For ${state.clubName}", icon = Icons.Default.ShowChart)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Apps", "$apps", Modifier.weight(1f)); StatTile("Goals", "$goals", Modifier.weight(1f)); StatTile("Assists", "$assists", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Clean sheets", "$cs", Modifier.weight(1f)); StatTile("Cards Y / R", "$yc / $rc", Modifier.weight(1f)); StatTile("Avg rating", if (avg > 0) String.format(java.util.Locale.US, "%.1f", avg) else "–", Modifier.weight(1f))
                }
            }
        }
        // OVR vs potential
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 8.dp) {
                SectionHeader("OVR vs potential", icon = Icons.Default.Star)
                Row(Modifier.fillMaxWidth()) {
                    Muted("OVR", 12.sp); FcText(" ${p.overall}", 13.sp, FontWeight.Bold, modifier = Modifier.weight(1f))
                    Muted("POT", 12.sp); FcText(" ${p.potential}", 13.sp, FontWeight.Bold)
                }
                ProgressBar(p.overall.toFloat(), 99f, c.good, height = 10.dp)
                Muted(if (p.overall >= p.potential) "Reached potential" else "${p.potential - p.overall} to reach his potential${p.potentialRange?.let { " ($it)" } ?: ""}", 12.sp)
            }
        }
        // From the game
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                FcLabel("From the game")
                val cc = ContractTime.contractChip(p, today)
                KeyValue("Contract ends", p.contractEnd?.let { "${ContractTime.monthYear(it)}${cc?.let { ch -> " · ${ch.text} left" } ?: ""}" } ?: "Not known")
                KeyValue("Wage", if (p.wageK > 0) "${state.currencySymbol}${p.wageK}K a week" else "–")
                KeyValue("Squad role", p.squadRole ?: "Not known")
                p.bonus?.let { KeyValue("Bonus", it) }
                KeyValue("Loan", when (p.loanDirection) { "in" -> "From ${p.loanClub.orEmpty().ifBlank { "another club" }}"; "out" -> "To ${p.loanClub.orEmpty().ifBlank { "another club" }}"; else -> "Not on loan" })
                if (p.suspendedGames > 0) KeyValue("Suspended", "${p.suspendedGames} match(es)", c.warn)
                Muted(if (console) "On console you keep these up to date in Edit player." else "Read from your career by the capture script and kept up to date by it.", 11.sp)
            }
        }
        // Awards
        item {
            val names = setOf(p.name.lowercase(), p.displayName.lowercase())
            val awards = (if (view.isArchived) view.awards else state.awards).filter { it.recipient.lowercase() in names }
            val own = state.playerAwards.filter { it.playerId == p.id }
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                SectionHeader(if (view.isArchived) "Awards that season" else "Awards", icon = Icons.Default.EmojiEvents)
                if (awards.isEmpty() && own.isEmpty()) Muted("No awards yet.", 12.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    awards.forEach { a -> Pill("${a.title}${if (a.period.isNotBlank()) " · ${a.period}" else ""}", if (a.kind == "totw") Tone.MUTED else Tone.WARN) }
                    own.forEach { a -> Pill("${a.name}${if (a.monthYear.isNotBlank()) " · ${a.monthYear}" else ""}", Tone.MUTED) }
                }
                if (!view.isArchived) Muted("Awards the game gives are added by themselves; you can still add your own in Season Summary · Awards.", 11.sp)
            }
        }
        // Notes
        if (!view.isArchived) {
            item {
                val notes = state.playerNotes.filter { it.playerId == p.id }.reversed()
                FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("Notes", Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.StickyNote2)
                        FcButton("Add note", { addNote = true }, kind = ButtonKind.GHOST, icon = Icons.Default.Add, small = true)
                    }
                    if (notes.isEmpty()) Muted("Nothing yet: contract talks, how he fits, when to rest him.", 12.sp)
                    notes.forEach { n ->
                        FcCard(Modifier.fillMaxWidth(), padding = 12.dp, gap = 6.dp, background = c.surface2, border = false) {
                            FcText(n.text, 13.sp, lineHeight = 19.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Muted("${Dates.full(n.date)} (in-game)", 11.sp, Modifier.weight(1f))
                                FcIconButton(Icons.Default.Delete, "Delete note", { viewModel.deletePlayerNote(n.id) }, size = 32.dp, border = false, background = c.surface2, tint = c.muted)
                            }
                        }
                    }
                    Muted("Only on this phone. Notes are part of your backup.", 11.sp)
                }
            }
            item { FcButton("Edit player", { editing = true }, kind = ButtonKind.SECONDARY, icon = Icons.Default.Edit, full = true) }
            if (!p.isAcademy) item { FcButton("Move back to youth academy", { confirmAcademy = true }, kind = ButtonKind.SECONDARY, full = true) }
            item { FcButton("Record player retirement", { confirmRetire = true }, kind = ButtonKind.DANGER, full = true) }
        } else {
            item { Muted("No edit, academy or retirement buttons in an archived season.", 11.sp) }
        }
    }

    if (p != null && editing) {
        EditPlayerDialog(startYear = state.startYear, player = p, allEafcPlayers = if (eafc.isNotEmpty()) eafc else viewModel.allEafcPlayers, onDismiss = { editing = false },
            onUpdate = { viewModel.updatePlayer(it) }, onDelete = { viewModel.deletePlayer(p.id); editing = false; onBack() }, referenceDate = ContractTime.referenceDate(state), console = console)
    }
    if (p != null && addNote) {
        var text by remember { mutableStateOf("") }
        FcDialog("Note about ${p.displayName}", { addNote = false }, actions = {
            FcButton("Cancel", { addNote = false }, kind = ButtonKind.SECONDARY, small = true)
            FcButton("Save", { viewModel.addPlayerNote(p.id, text); addNote = false }, small = true, enabled = text.isNotBlank())
        }) { FcField("", text, { text = it }, singleLine = false, minLines = 4, placeholder = "Wants a new contract after Christmas…") }
    }
    if (p != null && confirmAcademy) {
        ConfirmDialog("Move ${p.displayName} to the academy?", "He leaves the first team list and the formation.", "Move", {
            viewModel.updatePlayer(p.copy(isAcademy = true, isPromoted = false, status = "Academy", isStartingXI = false)); onBack()
        }, { confirmAcademy = false }, danger = false)
    }
    if (p != null && confirmRetire) {
        ConfirmDialog("Record ${p.displayName}'s retirement?", "He is marked as retired and a retirement is added to the transfer log.", "Record retirement", {
            viewModel.addTransferLog(TransferLog(playerName = p.displayName, type = "Retirement", feeM = 0.0, fromToClub = "Retired", date = CareerOps.today(state)))
            onBack()
        }, { confirmRetire = false })
    }
}
