package com.fctracker.career.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.AppState
import com.fctracker.career.MainViewModel
import com.fctracker.career.Platform
import com.fctracker.career.Player
import com.fctracker.career.Source
import com.fctracker.career.logic.CareerOps
import com.fctracker.career.logic.Dates
import com.fctracker.career.logic.SeasonView
import com.fctracker.career.telemetry.ContractTime
import com.fctracker.career.telemetry.Urgency
import com.fctracker.career.ui.components.ArchivedBanner
import com.fctracker.career.ui.components.ButtonKind
import com.fctracker.career.ui.components.ChipRow
import com.fctracker.career.ui.components.ConfirmDialog
import com.fctracker.career.ui.components.EmptyCard
import com.fctracker.career.ui.components.Fab
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.Stepper
import com.fctracker.career.ui.components.FcCard
import com.fctracker.career.ui.components.FcDateField
import com.fctracker.career.ui.components.FcDialog
import com.fctracker.career.ui.components.FcField
import com.fctracker.career.ui.components.FcLabel
import com.fctracker.career.ui.components.FcNumberField
import com.fctracker.career.ui.components.FcSelectField
import com.fctracker.career.ui.components.FcSwitchRow
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.FcTopBar
import com.fctracker.career.ui.components.FcChip
import com.fctracker.career.ui.components.FieldPair
import com.fctracker.career.ui.components.Figure
import com.fctracker.career.ui.components.Headshot
import com.fctracker.career.ui.components.HRule
import com.fctracker.career.ui.components.Mono
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.NavIcon
import com.fctracker.career.ui.components.Notice
import com.fctracker.career.ui.components.NumberedHeadshot
import com.fctracker.career.ui.components.OvrBadge
import com.fctracker.career.ui.components.Pill
import com.fctracker.career.ui.components.ProgressBar
import com.fctracker.career.ui.components.SectionHeader
import com.fctracker.career.ui.components.SoftTabs
import com.fctracker.career.ui.components.Tag
import com.fctracker.career.ui.components.Tone
import com.fctracker.career.ui.theme.FcTheme

val SQUAD_TABS = listOf("Roster", "Formation", "Tactics", "Wages", "Injuries")

val ROLE_TONE = mapOf("Crucial" to Tone.GOOD, "Important" to Tone.INFO, "Rotation" to Tone.MUTED, "Sporadic" to Tone.MUTED, "Prospect" to Tone.ACCENT)

fun isActivePlayer(p: Player) = p.status.isBlank() || p.status == "Active" || p.status == "Loaned In" || p.status == "Injured"

/** Squad: the roster, the formation, tactics, wages and roles, and injuries. */
@Composable
fun SquadScreen(viewModel: MainViewModel, onMenuClick: () -> Unit, openPlayer: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val health by viewModel.health.collectAsState()
    val view = remember(state) { SeasonView.of(state) }
    val console = state.platform == Platform.CONSOLE
    var tab by remember { mutableStateOf(0) }
    var addPlayer by remember { mutableStateOf(false) }
    val eafc by viewModel.eafcPlayers.collectAsState()
    val allEafc = if (eafc.isNotEmpty()) eafc else viewModel.allEafcPlayers

    val c = FcTheme.colors
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.statusBarsPadding()) {
            if (view.isArchived) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { ArchivedBanner("${view.label} · ${view.leagueName}", { viewModel.setViewingSeasonYear(null) }) }
            FcTopBar(
                "Squad", sub = if (view.isArchived) "Roster · ${view.label}" else "Management · ${SQUAD_TABS[tab]}", nav = NavIcon.MENU, onNav = onMenuClick,
                pill = if (!view.isArchived && !console) ({ StatusPill(health.level, health.pill, onClick = { viewModel.navigateToScreen("connection") }) }) else null
            )
            SoftTabs(if (view.isArchived) listOf("Roster") else SQUAD_TABS, if (view.isArchived) 0 else tab, { tab = it }, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
        }
        Box(Modifier.weight(1f)) {
            when (if (view.isArchived) 0 else tab) {
                0 -> RosterList(state, view, console, openPlayer, onAdd = { addPlayer = true })
                1 -> FormationTab(
                    view.players.filter { isActivePlayer(it) && !it.isLoanedOut && !it.isAcademy },
                    Modifier.fillMaxSize(),
                    onPlayerUpdate = { viewModel.updatePlayer(it) },
                    onApplyFormation = { viewModel.updatePlayers(it) },
                    onSwapPlayers = { a, b -> viewModel.swapPlayers(a, b) },
                    onPlayerClick = { openPlayer(it.id) },
                    activeFormation = state.activeFormation,
                    onActiveFormationChange = { viewModel.updateActiveFormation(it) },
                    header = { FormationHeader(viewModel, state, console) }
                )
                2 -> TacticalVisionsTab(state, viewModel)
                3 -> WagesList(viewModel, state, view, openPlayer)
                4 -> InjuriesList(viewModel, state, view, console)
            }
            if (!view.isArchived && tab == 0) Box(Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp)) { Fab("Add player", { addPlayer = true }) }
        }
    }
    if (addPlayer) {
        EditPlayerDialog(startYear = state.startYear, player = null, allEafcPlayers = allEafc, onDismiss = { addPlayer = false }, onUpdate = { viewModel.addPlayer(it) }, console = console)
    }
}

@Composable
private fun SquadList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

/** Rows inside one rounded card, separated by hairlines (the design's "soft list"). */
@Composable
fun RowsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = FcTheme.colors
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(22.dp)), content = content)
}

@Composable
fun RowDivider() = HRule()

fun contractPillTone(u: Urgency) = when (u) { Urgency.URGENT -> Tone.BAD; Urgency.SOON -> Tone.WARN; else -> Tone.MUTED }

/** A player row: headshot with the shirt number, name and tags, positions, value and wage, contract and loan pills, OVR. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerListRow(p: Player, today: String?, onClick: () -> Unit, showPills: Boolean = true, youth: Boolean = false, currency: String = "€") {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberedHeadshot(p.headshotUrl, p.displayName, p.number, 44.dp, youth = youth)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FcText(p.displayName, 15.sp, FontWeight.SemiBold, maxLines = 1)
                if (youth || p.isAcademy) Tag("Youth", Tone.GOOD)
                else if (p.isPromoted) Tag("Promoted", Tone.GOOD)
                if (p.loanDirection == "in" || p.isLoanedIn) Tag("Loaned in", Tone.INFO)
                if (p.loanDirection == "out" || p.isLoanedOut) Tag("On loan", Tone.INFO)
                if (p.isRegen) Tag("Regen", Tone.ACCENT)
                if (p.isInjured) Tag("Injured", Tone.BAD)
                if (p.suspendedGames > 0) Tag("Suspended", Tone.WARN)
            }
            val pos = listOfNotNull(p.primaryPosition, p.secondaryPosition?.takeIf { it.isNotBlank() }).joinToString("/")
            Muted("$pos · Age ${p.age}${if (p.isRegen && !p.regenOriginalName.isNullOrBlank()) " · Regen of ${com.fctracker.career.logic.PlayerNames.shown(p.regenOriginalName)}" else ""}${if (youth) p.potentialRange?.let { " · Potential $it" } ?: " · Potential ${p.potential}" else ""}", 12.sp)
            if (!youth) Mono("$currency${p.valueM}M · ${if (p.wageK > 0) "$currency${p.wageK}K/wk" else "on loan"}", 11.sp, FcTheme.colors.muted, FontWeight.Normal)
            if (showPills) {
                val cc = ContractTime.contractChip(p, today)
                val lc = ContractTime.loanChip(p, today)
                if (cc != null || lc != null) {
                    FlowRow(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        cc?.let { Pill("Contract ${it.text} · ${ContractTime.monthYear(p.contractEnd)}", contractPillTone(it.urgency), warn = it.urgency != Urgency.OK) }
                        lc?.let { Pill("Loan ${if (p.loanDirection == "in") "from" else "to"} ${p.loanClub.orEmpty().ifBlank { "another club" }} · ${it.text}", if (it.urgency == Urgency.OK) Tone.INFO else Tone.WARN) }
                        if (p.loanDirection != null && p.loanOptionToBuy) Pill("Option to buy", Tone.MUTED)
                    }
                }
            }
        }
        OvrBadge(p.overall)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = FcTheme.colors.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun RosterList(state: AppState, view: SeasonView, console: Boolean, openPlayer: (String) -> Unit, onAdd: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var attentionOnly by remember { mutableStateOf(false) }
    val today = if (view.isArchived) null else ContractTime.referenceDate(state)
    val roster = view.players.filter { it.status != "Sold" && it.status != "Retired" }
    val first = roster.filter { !it.isAcademy && !it.status.equals("Academy", true) }
    val attention = remember(first, today) { if (today == null) emptyList() else ContractTime.attentionList(first, today) }
    val match: (Player) -> Boolean = { p -> query.isBlank() || p.displayName.contains(query, true) || p.name.contains(query, true) || p.primaryPosition.contains(query, true) }
    // strikers first, the goalkeepers last; the best first within a position
    val byPosition = first.sortedWith(compareBy<Player>({ LineupOrder.rank(it.primaryPosition) }, { LineupOrder.side(it.primaryPosition) }).thenByDescending { it.overall })
    val shown = (if (attentionOnly && attention.isNotEmpty()) attention else byPosition).filter(match)
    val youth = (view.academy + roster.filter { it.isAcademy || it.status.equals("Academy", true) }).distinctBy { it.id }.filter(match).sortedByDescending { it.overall }
    SquadList {
        item {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FcField("Search", query, { query = it }, Modifier.weight(1f), placeholder = "Search player name or position", leadingIcon = Icons.Default.Search)
                if (!view.isArchived) FcButton("Add", onAdd, icon = Icons.Default.Add, small = true)
            }
        }
        if (attention.isNotEmpty()) {
            item { ChipRow(listOf("All (${first.size})", "Needs attention (${attention.size})"), if (attentionOnly) 1 else 0, { attentionOnly = it == 1 }) }
        }
        if (attentionOnly && attention.isNotEmpty()) {
            item { Notice(Tone.WARN, "${attention.size} ${if (attention.size == 1) "contract needs" else "contracts need"} attention", "Contracts ending within a year and loans ending within three months, the soonest first.", icon = Icons.Default.Warning) }
        }
        item { SectionHeader(if (view.isArchived) "First team (${first.size})" else "First team (${shown.size})", sub = if (view.isArchived) "As it was at the end of ${view.label}" else null, icon = Icons.Default.Groups) }
        if (shown.isEmpty()) item { EmptyCard(if (query.isNotBlank()) "No player fits \"$query\"." else "No players yet. Add your squad, or let the PC send it.") }
        else item {
            RowsCard {
                shown.forEachIndexed { i, p ->
                    if (i > 0) RowDivider()
                    PlayerListRow(p, today, { openPlayer(p.id) }, currency = state.currencySymbol)
                }
            }
        }
        if (!view.isArchived) item { Muted("Shirt numbers come from your game. Headshots come from the player database; a player without one (most youth and regens) gets the plain silhouette.", 12.sp) }
        else item { Muted("Ages and ratings are those of that season. No Add button, no formation changes: an archived squad is read-only.", 11.sp) }
        if (attentionOnly) {
            val loans = first.filter { it.loanDirection != null }
            if (loans.isNotEmpty()) item {
                FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                    SectionHeader("Loans", icon = Icons.Default.SwapHoriz)
                    loans.forEach { p -> PlayerListRow(p, today, { openPlayer(p.id) }, currency = state.currencySymbol) }
                    Muted("Loans appear here once they end within three months.", 11.sp)
                }
            }
            item { Muted("Contract and loan dates come from your career file via the capture script${if (console) " (on console: Edit player)" else ""}. When the script has not sent them, the pill is hidden instead of guessing.", 11.sp) }
        }
        if (youth.isNotEmpty()) {
            item { SectionHeader("Youth academy (${youth.size})", icon = Icons.Default.Groups) }
            item {
                RowsCard {
                    youth.forEachIndexed { i, p ->
                        if (i > 0) RowDivider()
                        PlayerListRow(p, today, { openPlayer(p.id) }, showPills = false, youth = true, currency = state.currencySymbol)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormationHeader(viewModel: MainViewModel, state: AppState, console: Boolean) {
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!console) {
            FcButton("Use my team sheet from the game", {
                message = if (viewModel.useGameTeamSheet()) "Your starting XI from FC 26 is on the pitch." else if (state.gameTeamSheet == null) "No team sheet from the game yet: it arrives with the next report from your PC." else "The team sheet names players the app does not know yet."
            }, kind = ButtonKind.SECONDARY, icon = Icons.Default.Sync, full = true)
            Muted(message ?: "Copies the starting XI, the bench and the shirt numbers you picked in FC 26 (PC only).", 11.sp)
        }
        FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 8.dp) {
            SectionHeader("Moving players", icon = Icons.Default.Groups)
            listOf(
                "Drag a player anywhere on the pitch. He stays exactly where you drop him.",
                "Drop a bench player on a pitch player and they swap: the pitch player goes to the bench.",
                "Drag a pitch player off the pitch to put him on the bench.",
                "A new formation template keeps everyone as close to his own position as it can: the keeper stays in goal, defenders at the back, forwards up front."
            ).forEach { com.fctracker.career.ui.components.Bullet(it) }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Wages and squad roles
// ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun WagesList(viewModel: MainViewModel, state: AppState, view: SeasonView, openPlayer: (String) -> Unit) {
    val c = FcTheme.colors
    val cur = state.currencySymbol
    var sort by remember { mutableStateOf(0) }
    var editBudget by remember { mutableStateOf(false) }
    val today = ContractTime.referenceDate(state)
    val players = view.players.filter { isActivePlayer(it) && !it.isAcademy }
    val bill = players.sumOf { it.wageK }
    val budget = view.wageBudgetK
    val roleOrder = listOf("Crucial", "Important", "Rotation", "Sporadic", "Prospect")
    val sorted = when (sort) {
        1 -> players.sortedWith(compareBy<Player> { roleOrder.indexOf(it.squadRole ?: "").let { i -> if (i < 0) 99 else i } }.thenByDescending { it.wageK })
        2 -> players.sortedBy { it.contractEnd ?: "9999" }
        else -> players.sortedByDescending { it.wageK }
    }
    SquadList {
        item {
            FcCard(Modifier.fillMaxWidth(), gap = 10.dp) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Muted("Wage bill", 12.sp)
                        Figure("$cur${bill}K", 34.sp, weight = FontWeight.SemiBold)
                        Muted("a week · $cur${String.format(java.util.Locale.US, "%.1f", bill * 52 / 1000.0)}M a year", 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.clickable(enabled = !view.isArchived) { editBudget = true }) {
                        Muted("Wage budget", 12.sp)
                        Figure(if (budget > 0) "$cur${budget.toInt()}K" else "Set", 22.sp, if (budget > 0) c.text else c.accent)
                        Muted(if (budget > 0) "set by you" else "tap to set", 11.sp)
                    }
                }
                if (budget > 0) {
                    ProgressBar(bill.toFloat(), budget.toFloat(), if (bill > budget) c.bad else c.accent, height = 10.dp)
                    Row(Modifier.fillMaxWidth()) {
                        Muted("${(100 * bill / budget).toInt()}% of the budget used", 12.sp, Modifier.weight(1f))
                        Muted(if (bill <= budget) "$cur${(budget - bill).toInt()}K left" else "$cur${(bill - budget).toInt()}K over", 12.sp)
                    }
                }
                Muted("Wages, squad roles and bonuses come from the game. The wage budget does not: the game keeps it out of reach, so you type it once.", 11.sp)
            }
        }
        item { ChipRow(listOf("Highest wage", "Squad role", "Contract end"), sort, { sort = it }) }
        item {
            RowsCard {
                sorted.forEachIndexed { i, p ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().clickable { openPlayer(p.id) }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberedHeadshot(p.headshotUrl, p.displayName, p.number, 42.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                FcText(p.displayName, 15.sp, FontWeight.SemiBold, maxLines = 1)
                                p.squadRole?.let { Tag(it, ROLE_TONE[it] ?: Tone.MUTED) }
                            }
                            val cc = ContractTime.contractChip(p, today)
                            Muted(if (p.contractEnd != null) "Contract to ${ContractTime.monthYear(p.contractEnd)}${cc?.let { " · ${it.text}" } ?: ""}" else "Contract not known", 12.sp)
                            p.bonus?.let { Mono(it, 11.sp, c.muted, FontWeight.Normal, maxLines = 2) }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Mono(if (p.wageK > 0) "$cur${p.wageK}K" else "–", 15.sp, weight = FontWeight.SemiBold)
                            Muted(if (p.wageK > 0) "a week" else "paid by parent club", 11.sp)
                        }
                    }
                }
            }
        }
        item { Muted("A loan player shows the share of his wage you pay.", 11.sp) }
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                SectionHeader("Squad roles", icon = Icons.Default.Groups)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ROLE_TONE.forEach { (k, v) -> Tag(k, v) } }
                Muted("The role you promised each player in the game. A player who plays much less than promised may ask to leave.", 11.sp)
            }
        }
    }
    if (editBudget) {
        var text by remember { mutableStateOf(if (budget > 0) budget.toInt().toString() else "") }
        FcDialog("Wage budget", { editBudget = false }, actions = {
            FcButton("Cancel", { editBudget = false }, kind = ButtonKind.SECONDARY, small = true)
            FcButton("Save", { viewModel.setWageBudget(text.toDoubleOrNull() ?: 0.0); editBudget = false }, small = true)
        }) {
            FcNumberField("Weekly wage budget ($cur thousands)", text, { text = it }, placeholder = "250")
            Muted("Find it in the game under Finances. Leave empty to hide the budget bar.", 11.sp)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Injuries and suspensions
// ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun InjuriesList(viewModel: MainViewModel, state: AppState, view: SeasonView, console: Boolean) {
    val c = FcTheme.colors
    var editing by remember { mutableStateOf<Player?>(null) }
    var adding by remember { mutableStateOf(false) }
    var banFor by remember { mutableStateOf<Player?>(null) }
    var addingBan by remember { mutableStateOf(false) }
    val today = ContractTime.referenceDate(state)
    val injured = view.players.filter { it.isInjured }.sortedBy { it.injuryReturnDate ?: "9999" }
    val suspended = view.players.filter { it.suspendedGames > 0 }
    SquadList {
        item {
            if (console) Notice(Tone.INFO, "Injuries you keep yourself", "On console add an injury when it happens, and change it when the player needs longer.", icon = Icons.Default.Healing)
            else Notice(Tone.INFO, "Injuries from your game", "The capture script sends who is injured, the injury and when he should be back, so the list keeps itself up to date. You can still add one yourself.", icon = Icons.Default.Sync)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("Active injuries (${injured.size})", Modifier.weight(1f), icon = Icons.Default.Healing)
                FcButton("Add injury", { adding = true }, kind = ButtonKind.SECONDARY, icon = Icons.Default.Add, small = true)
            }
        }
        if (injured.isEmpty()) item { EmptyCard("No current injuries.") }
        else item {
            RowsCard {
                injured.forEachIndexed { i, p ->
                    if (i > 0) RowDivider()
                    val missed = CareerOps.matchesMissed(p, view.matches, today)
                    Row(Modifier.fillMaxWidth().clickable { editing = p }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberedHeadshot(p.headshotUrl, p.displayName, p.number, 42.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                FcText(p.displayName, 15.sp, FontWeight.SemiBold, maxLines = 1)
                                Tag(if (p.injurySource == Source.GAME) "From the game" else "Added by you", if (p.injurySource == Source.GAME) Tone.INFO else Tone.MUTED)
                                if (!p.injuryFollowGame && p.injurySource == Source.GAME) Tag("Changed by you", Tone.WARN)
                            }
                            Muted("${p.injuryName ?: "Injury"}${p.injuryReturnDate?.let { " · back about ${Dates.dayMonth(it)}" } ?: ""}", 12.sp)
                            p.injuryStartDate?.let { Mono("Out since ${Dates.dayMonth(it)}", 11.sp, c.muted, FontWeight.Normal) }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Figure("$missed", 20.sp, c.bad, FontWeight.SemiBold)
                            Muted("missed", 11.sp)
                        }
                    }
                }
            }
        }
        item { Muted("Tap an injury to change it, for example when he needs longer than the game says. Matches missed counts the league and cup matches played while he was out.", 11.sp) }
        if (!console) item { Muted("The game keeps every player's latest injury, the day it happened and how many days it lasts; a player stays on this list until those days have run out.", 11.sp) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("Suspensions (${suspended.size})", Modifier.weight(1f), icon = Icons.Default.Warning)
                FcButton("Add suspension", { addingBan = true }, kind = ButtonKind.SECONDARY, icon = Icons.Default.Add, small = true)
            }
        }
        if (suspended.isEmpty()) item { Muted(if (console) "Nobody is suspended. Add a ban after a red card or too many bookings; it counts down with every match you log." else "Nobody is suspended. Bans arrive from the game; you can add or correct one here.", 12.sp) }
        if (suspended.isNotEmpty()) {
            item {
                RowsCard {
                    suspended.forEachIndexed { i, p ->
                        if (i > 0) RowDivider()
                        Row(Modifier.fillMaxWidth().clickable { banFor = p }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NumberedHeadshot(p.headshotUrl, p.displayName, p.number, 42.dp)
                            Column(Modifier.weight(1f)) {
                                FcText(p.displayName, 15.sp, FontWeight.SemiBold)
                                Muted("Out for ${p.suspendedGames} ${if (p.suspendedGames == 1) "match" else "matches"}${p.suspensionEnd?.let { " · until ${Dates.dayMonth(it)}" } ?: ""}", 12.sp)
                            }
                            Tag("Suspended", Tone.WARN)
                        }
                    }
                }
            }
        }
    }
    editing?.let { p -> InjuryEditDialog(viewModel, p, onDismiss = { editing = null }) }
    if (adding) AddInjuryDialog(viewModel, view.players.filter { isActivePlayer(it) && !it.isInjured }, CareerOps.today(state), onDismiss = { adding = false })
    if (addingBan || banFor != null) SuspensionDialog(viewModel, banFor, view.players.filter { isActivePlayer(it) && it.suspendedGames == 0 }, onDismiss = { addingBan = false; banFor = null })
}

/** Add or change a ban: the player, the matches left and (optionally) the last day of it. */
@Composable
private fun SuspensionDialog(viewModel: MainViewModel, existing: Player?, candidates: List<Player>, onDismiss: () -> Unit) {
    var player by remember { mutableStateOf(existing) }
    var games by remember { mutableStateOf(existing?.suspendedGames?.takeIf { it > 0 } ?: 1) }
    var end by remember { mutableStateOf(existing?.suspensionEnd ?: "") }
    FcDialog(if (existing == null) "Add suspension" else "Suspension · ${existing.displayName}", onDismiss, actions = {
        if (existing != null) FcButton("Lift ban", { viewModel.setSuspension(existing.id, 0, null); onDismiss() }, kind = ButtonKind.SECONDARY, small = true)
        FcButton("Cancel", onDismiss, kind = ButtonKind.SECONDARY, small = true)
        FcButton("Save", { player?.let { viewModel.setSuspension(it.id, games, end.ifBlank { null }) }; onDismiss() }, small = true, enabled = player != null)
    }) {
        if (existing == null) FcSelectField("Player", player?.displayName ?: "Choose a player", candidates.map { it.displayName }, { name -> player = candidates.firstOrNull { it.displayName == name } })
        Stepper("Matches left", games, { games = it }, min = 1, max = 20)
        FcDateField("Until (optional)", end.ifBlank { null }, { end = it }, hint = "Leave empty when the game counts matches, not days.")
        Muted("One match comes off with every competitive match you log that he does not play.", 11.sp)
    }
}

/** Change an injury: a later return date, the injury's name, a note; "Follow the game" decides whose date counts. */
@Composable
fun InjuryEditDialog(viewModel: MainViewModel, p: Player, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(p.injuryName ?: "") }
    var start by remember { mutableStateOf(p.injuryStartDate ?: "") }
    var back by remember { mutableStateOf(p.injuryReturnDate ?: "") }
    var follow by remember { mutableStateOf(p.injuryFollowGame && p.injurySource == Source.GAME) }
    var note by remember { mutableStateOf(p.injuryNote ?: "") }
    var confirmFit by remember { mutableStateOf(false) }
    val fromGame = p.injurySource == Source.GAME
    fun bump(days: Int) { Dates.plusDays(back.ifBlank { p.injuryGameReturnDate }, days)?.let { back = it; follow = false } }
    FcDialog("Edit injury", onDismiss, actions = {
        FcButton("He is fit again", { confirmFit = true }, kind = ButtonKind.SECONDARY, small = true)
        FcButton("Cancel", onDismiss, kind = ButtonKind.SECONDARY, small = true)
        FcButton("Save", { viewModel.setInjury(p.id, name, start.ifBlank { null }, back.ifBlank { null }, follow, note); onDismiss() }, small = true)
    }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberedHeadshot(p.headshotUrl, p.displayName, p.number, 44.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FcText(p.displayName, 15.sp, FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(if (fromGame) "From the game" else "Added by you", if (fromGame) Tone.INFO else Tone.MUTED)
                    if (fromGame && !follow) Tag("Changed by you", Tone.WARN)
                }
            }
        }
        FcField("Injury", name, { name = it }, placeholder = "Hamstring strain")
        FieldPair({ m -> FcDateField("Out since", start, { start = it }, m) }, { m -> FcDateField("Back about", back, { back = it; follow = false }, m) })
        if (fromGame && p.injuryGameReturnDate != null) {
            Muted("The game said about ${Dates.dayMonth(p.injuryGameReturnDate)}.${if (!follow) " Your date is kept." else ""}", 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FcChip("+3 days", false, { bump(3) })
            FcChip("+1 week", false, { bump(7) })
            FcChip("+2 weeks", false, { bump(14) })
        }
        if (fromGame) FcSwitchRow("Follow the game", follow, { follow = it; if (it) p.injuryGameReturnDate?.let { g -> back = g } }, sub = "On: the date changes whenever the game changes it. Off: your date stays until you turn this back on.")
        FcField("Note (optional)", note, { note = it }, placeholder = "Setback in training")
    }
    if (confirmFit) ConfirmDialog("${p.displayName} is fit again?", "He comes off the injury list now.", "He is fit", { viewModel.clearInjury(p.id); onDismiss() }, { confirmFit = false }, danger = false)
}

@Composable
private fun AddInjuryDialog(viewModel: MainViewModel, players: List<Player>, today: String, onDismiss: () -> Unit) {
    var who by remember { mutableStateOf(players.firstOrNull()) }
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(today) }
    var back by remember { mutableStateOf(Dates.plusDays(today, 14) ?: today) }
    var note by remember { mutableStateOf("") }
    FcDialog("Add injury", onDismiss, actions = {
        FcButton("Cancel", onDismiss, kind = ButtonKind.SECONDARY, small = true)
        FcButton("Add", { who?.let { viewModel.setInjury(it.id, name, start, back, false, note) }; onDismiss() }, small = true, enabled = who != null)
    }) {
        FcSelectField("Player", who?.displayName ?: "", players.map { it.displayName }, { n -> who = players.firstOrNull { it.displayName == n } })
        FcField("Injury", name, { name = it }, placeholder = "Ankle knock")
        FieldPair({ m -> FcDateField("Out since", start, { start = it }, m) }, { m -> FcDateField("Back about", back, { back = it }, m) })
        FcField("Note (optional)", note, { note = it })
    }
}
