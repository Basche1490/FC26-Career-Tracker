package com.fctracker.career.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.AppState
import com.fctracker.career.MainViewModel
import com.fctracker.career.MatchResult
import com.fctracker.career.Player
import com.fctracker.career.PlayerPerformance
import com.fctracker.career.isPlaceholderPlayerName
import com.fctracker.career.logic.CareerOps
import com.fctracker.career.logic.Dates
import com.fctracker.career.telemetry.TelemetryProcessor
import com.fctracker.career.ui.components.BookingCard
import com.fctracker.career.ui.components.ButtonKind
import com.fctracker.career.ui.components.ClubCrest
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.FcCard
import com.fctracker.career.ui.components.FcCheckRow
import com.fctracker.career.ui.components.FcDateField
import com.fctracker.career.ui.components.FcDialog
import com.fctracker.career.ui.components.FcField
import com.fctracker.career.ui.components.FcFullScreen
import com.fctracker.career.ui.components.FcNumberField
import com.fctracker.career.ui.components.FcPage
import com.fctracker.career.ui.components.FcSelectField
import com.fctracker.career.ui.components.FcSwitchRow
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.FcTopBar
import com.fctracker.career.ui.components.FieldPair
import com.fctracker.career.ui.components.HRule
import com.fctracker.career.ui.components.Headshot
import com.fctracker.career.ui.components.Mono
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.NavIcon
import com.fctracker.career.ui.components.RatingChip
import com.fctracker.career.ui.components.RatingEditor
import com.fctracker.career.ui.components.SectionHeader
import com.fctracker.career.ui.components.TeamCrest
import com.fctracker.career.ui.theme.FcTheme

/** Players who can be in a match line-up: the first team, not sold, retired or away on loan. */
internal fun matchEligible(players: List<Player>): List<Player> = players.filter {
    !it.isAcademy && !it.status.equals("Academy", true) && !it.isLoanedOut && it.loanDirection != "out" &&
        !it.status.equals("Sold", true) && !it.status.equals("Retired", true) && !it.status.equals("Loaned Out", true)
}

/**
 * The starting eleven from the formation in Squad, each with the position of his place on the pitch; when fewer
 * than eleven are picked there, the best players left fill the gaps.
 */
internal fun formationEleven(players: List<Player>, formation: String): List<Pair<Player, String>> {
    val eligible = matchEligible(players)
    val slots = formationsMap[formation]
    val starters = eligible.filter { it.isStartingXI }.take(11)
    fun slotRole(p: Player): String {
        if (slots == null) return p.primaryPosition
        val nearest = slots.minByOrNull { (it.x - p.pitchX) * (it.x - p.pitchX) + (it.y - p.pitchY) * (it.y - p.pitchY) } ?: return p.primaryPosition
        // the player's own position when it fits the place (a left-back stays LB, not LWB)
        return listOfNotNull(p.primaryPosition, p.secondaryPosition, p.tertiaryPosition).firstOrNull { it in nearest.roles } ?: nearest.roles.first()
    }
    val picked = starters.map { it to slotRole(it) }
    val rest = eligible.filter { e -> picked.none { it.first.id == e.id } }.sortedByDescending { it.overall }.take(11 - picked.size).map { it to it.primaryPosition }
    return picked + rest
}

/**
 * Log match / match details: the score, the match facts, extra time, the team figures and the man of the match, then
 * the line-up and every player's numbers on a page of their own. [onHeadToHead] (an existing match) adds the link to
 * every match against this opponent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatchFormModal(
    viewModel: MainViewModel,
    state: AppState,
    initialMatch: MatchResult?,
    onDismiss: () -> Unit,
    onSave: (MatchResult) -> Unit,
    onHeadToHead: ((String) -> Unit)? = null,
    initialPage: Int = 0
) {
    val c = FcTheme.colors
    // 0 = the match, 1 = the line-up and every player's numbers ("Lineup & stats" on a match opens that one)
    var page by remember { mutableStateOf(initialPage.coerceIn(0, 1)) }
    var matchDate by remember { mutableStateOf(initialMatch?.matchDate?.takeIf { Dates.isDate(it) } ?: CareerOps.today(state)) }
    var opponent by remember { mutableStateOf(initialMatch?.opponentName ?: "") }
    var opponentCrestUrl by remember { mutableStateOf(initialMatch?.opponentCrestUrl ?: "") }
    val competitions = remember(state.country, state.leagueName) { (listOf(state.leagueName) + getCompetitionsForCountry(state.country)).filter { it.isNotBlank() }.distinct() }
    var competition by remember { mutableStateOf(initialMatch?.competition ?: competitions.firstOrNull() ?: "League") }
    var stage by remember { mutableStateOf(initialMatch?.stage ?: "") }
    val isLeague = TelemetryProcessor.isLeagueCompetition(competition)
    LaunchedEffect(competition) {
        if (initialMatch == null && isLeague) {
            val last = state.recentMatches.filter { TelemetryProcessor.isLeagueCompetition(it.competition) }.maxByOrNull { it.matchDate }
            val n = last?.let { Regex("\\d+").find(it.stage)?.value?.toIntOrNull() }
            stage = "Matchday ${(n ?: 0) + 1}"
        }
    }
    var isHome by remember { mutableStateOf(initialMatch?.isHome ?: true) }
    var goalsFor by remember { mutableStateOf(initialMatch?.goalsFor ?: 0) }
    var goalsAgainst by remember { mutableStateOf(initialMatch?.goalsAgainst ?: 0) }
    var isExtraTime by remember { mutableStateOf(initialMatch?.isExtraTime ?: false) }
    var homePenalties by remember { mutableStateOf(initialMatch?.homePenalties?.takeIf { it > 0 }?.toString() ?: "") }
    var awayPenalties by remember { mutableStateOf(initialMatch?.awayPenalties?.takeIf { it > 0 }?.toString() ?: "") }
    var possession by remember { mutableStateOf(if (initialMatch != null && initialMatch.hasTrustedPossession) initialMatch.possession.toString() else "") }
    var passingAccuracy by remember { mutableStateOf(initialMatch?.passingAccuracy?.takeIf { it > 0 }?.toString() ?: "") }
    var shotsFor by remember { mutableStateOf(initialMatch?.shotsFor?.toString() ?: "") }
    var shotsAgainst by remember { mutableStateOf(initialMatch?.shotsAgainst?.toString() ?: "") }
    var xGFor by remember { mutableStateOf(initialMatch?.expectedGoals?.takeIf { it > 0 }?.toString() ?: "") }
    var xGAgainst by remember { mutableStateOf(initialMatch?.expectedGoalsAgainst?.takeIf { it > 0 }?.toString() ?: "") }
    var leaguePosition by remember { mutableStateOf(initialMatch?.leaguePosition?.takeIf { it > 0 }?.toString() ?: "") }
    val performances = remember { mutableStateMapOf<String, PlayerPerformance>().apply { initialMatch?.playerPerformances?.forEach { put(it.playerId, it) } } }
    var motm by remember {
        mutableStateOf(
            initialMatch?.motmName?.takeIf { it.isNotBlank() }
                ?: initialMatch?.playerPerformances?.find { it.isMotm }?.playerId?.let { pid -> state.players.find { it.id == pid }?.displayName } ?: ""
        )
    }
    var opponentPicked by remember { mutableStateOf(initialMatch != null) }
    var motmPicked by remember { mutableStateOf(true) }
    val clubs = remember(state) {
        (state.leagueTables.flatMap { t -> t.rows.map { it.club } } + state.recentMatches.map { it.opponentName } +
            state.historicalSeasons.flatMap { h -> h.matches.map { it.opponentName } } + clubDomains.keys)
            .filter { it.isNotBlank() && !it.equals(state.clubName, true) }.distinct().sorted()
    }

    fun build(): MatchResult {
        val final = performances.values.map { p ->
            val shown = lineupNameOf(p, state.players, initialMatch)
            p.copy(isMotm = motm.isNotBlank() && shown.equals(motm, ignoreCase = true))
        }
        val poss = possession.toIntOrNull()?.coerceIn(0, 100) ?: 0
        return MatchResult(
            id = initialMatch?.id ?: java.util.UUID.randomUUID().toString(),
            opponentName = opponent.trim(),
            opponentCrestUrl = opponentCrestUrl.ifBlank { getTeamCrestUrl(opponent.trim()) },
            isHome = isHome,
            goalsFor = goalsFor,
            goalsAgainst = goalsAgainst,
            matchDate = matchDate,
            competition = competition,
            stage = stage.trim(),
            isExtraTime = isExtraTime,
            homePenalties = homePenalties.toIntOrNull() ?: 0,
            awayPenalties = awayPenalties.toIntOrNull() ?: 0,
            possession = poss,
            passingAccuracy = passingAccuracy.toIntOrNull() ?: (initialMatch?.passingAccuracy ?: 80),
            shotsFor = shotsFor.toIntOrNull() ?: 0,
            shotsAgainst = shotsAgainst.toIntOrNull() ?: 0,
            expectedGoals = xGFor.toDoubleOrNull() ?: 0.0,
            expectedGoalsAgainst = xGAgainst.toDoubleOrNull() ?: 0.0,
            leaguePosition = if (isLeague) (leaguePosition.toIntOrNull() ?: 0) else 0,
            motmName = motm.trim(),
            motmIsOpponent = initialMatch != null && initialMatch.motmIsOpponent && motm == initialMatch.motmName,
            possessionEstimated = false,
            possessionConfirmed = poss > 0,
            playerPerformances = final,
            // editing must not throw away what the game recorded: scorers and substitutions
            goalDetails = initialMatch?.goalDetails ?: emptyList(),
            substitutions = initialMatch?.substitutions ?: emptyList()
            // statsEstimated stays false: saving through this form counts as the person's review
        )
    }
    val canSave = opponent.isNotBlank() && Dates.isDate(matchDate)

    FcFullScreen(onDismiss = { if (page == 1) page = 0 else onDismiss() }) {
        BackHandler(enabled = page == 1) { page = 0 }
        if (page == 1) {
            LineupPage(
                state = state, initialMatch = initialMatch, performances = performances, motm = motm, goalsAgainst = goalsAgainst,
                subtitle = "${opponent.ifBlank { "Opponent" }} (${if (isHome) "H" else "A"}) · $goalsFor–$goalsAgainst",
                onBack = { page = 0 }, onSave = { if (canSave) onSave(build()) else page = 0 }
            )
            return@FcFullScreen
        }
        FcPage(header = { FcTopBar(if (initialMatch != null) "Match details" else "Log match", nav = NavIcon.CLOSE, onNav = onDismiss) }) {
            // -- the score ---------------------------------------------------------------------------------------------
            item {
                FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 8.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ClubCrest(state.clubName, state.clubCrestFile, state.clubCrestUrl, state.isExistingTeam, 52.dp)
                            FcText(state.clubName, 12.sp, color = c.muted, maxLines = 1, align = TextAlign.Center)
                        }
                        ScoreStepper(goalsFor, { goalsFor = it }, "your goals")
                        FcText("–", 34.sp, FontWeight.SemiBold, c.muted, Modifier.padding(horizontal = 4.dp))
                        ScoreStepper(goalsAgainst, { goalsAgainst = it }, "their goals")
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            TeamCrest(opponent.ifBlank { "?" }, 52.dp)
                            FcText(opponent.ifBlank { "Opponent" }, 12.sp, color = c.muted, maxLines = 2, align = TextAlign.Center)
                        }
                    }
                    val hp = homePenalties.toIntOrNull() ?: 0
                    val ap = awayPenalties.toIntOrNull() ?: 0
                    if (hp > 0 || ap > 0) FcText("(P: $hp – $ap)", 13.sp, FontWeight.SemiBold, c.warn, Modifier.fillMaxWidth(), align = TextAlign.Center)
                    else Muted("Penalties (if any): shown as “(P: 4 – 3)” under the score.", 11.sp, Modifier.fillMaxWidth(), align = TextAlign.Center)
                }
            }
            // -- match info --------------------------------------------------------------------------------------------
            item {
                FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                    SectionHeader("Match info", icon = Icons.Default.Info)
                    FcField("Opponent", opponent, { opponent = it; opponentPicked = false; opponentCrestUrl = "" }, placeholder = "Search a club",
                        trailing = { Icon(Icons.Default.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp)) })
                    val suggestions = if (!opponentPicked && opponent.trim().length >= 2) clubs.filter { it.contains(opponent.trim(), true) }.take(5) else emptyList()
                    if (suggestions.isNotEmpty()) SuggestionList(suggestions.map { it to null }) { name, _ -> opponent = name; opponentCrestUrl = getTeamCrestUrl(name); opponentPicked = true }
                    FieldPair(
                        { m -> FcDateField("Date", matchDate, { matchDate = it }, m) },
                        { m -> FcSelectField("Location", if (isHome) "Home" else "Away", listOf("Home", "Away"), { isHome = it == "Home" }, m) }
                    )
                    FcSelectField("Competition", competition, (competitions + competition).distinct(), { competition = it },
                        hint = "The list follows your club’s country (${state.country}) and your league.")
                    FcField("Stage", stage, { stage = it }, placeholder = "Matchday 14", hint = "For example Matchday 14, Round 3 or Final.")
                }
            }
            // -- extra time and penalties ------------------------------------------------------------------------------
            item {
                FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                    SectionHeader("Extra time & penalties", icon = Icons.Default.Schedule)
                    FcSwitchRow("Extra time played", isExtraTime, { isExtraTime = it }, sub = "Adds “AET” to the result")
                    FieldPair(
                        { m -> FcNumberField("Home penalties", homePenalties, { homePenalties = it }, m) },
                        { m -> FcNumberField("Away penalties", awayPenalties, { awayPenalties = it }, m) }
                    )
                }
            }
            // -- team figures ------------------------------------------------------------------------------------------
            item {
                FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                    SectionHeader("Detailed statistics", sub = "Optional. Leave blank to keep estimated values.", icon = Icons.Default.BarChart)
                    FieldPair({ m -> FcNumberField("Possession %", possession, { possession = it }, m) }, { m -> FcNumberField("Pass accuracy %", passingAccuracy, { passingAccuracy = it }, m) })
                    FieldPair({ m -> FcNumberField("Shots for", shotsFor, { shotsFor = it }, m) }, { m -> FcNumberField("Shots against", shotsAgainst, { shotsAgainst = it }, m) })
                    FieldPair({ m -> FcNumberField("xG for", xGFor, { xGFor = it }, m, decimal = true) }, { m -> FcNumberField("xG against", xGAgainst, { xGAgainst = it }, m, decimal = true) })
                    if (isLeague) FcNumberField("League position", leaguePosition, { leaguePosition = it }, hint = "Only asked for league matches.")
                }
            }
            // -- man of the match --------------------------------------------------------------------------------------
            item {
                FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                    SectionHeader("Man of the match", icon = Icons.Default.Star)
                    FcField("Search player name", motm, { motm = it; motmPicked = false }, hint = "Searches your squad, the player database, or “Opponent Player”.",
                        trailing = { Icon(Icons.Default.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp)) })
                    val options = if (!motmPicked && motm.trim().length >= 2) {
                        val squad = state.players.map { it.displayName }
                        val db = viewModel.searchPlayersAndTeams(motm.trim()).map { it.commonName.ifBlank { it.name } }
                        (squad + db + listOf("Opponent Player")).filter { it.contains(motm.trim(), true) }.distinct().take(6)
                    } else emptyList()
                    if (options.isNotEmpty()) SuggestionList(options.map { it to null }) { name, _ -> motm = name; motmPicked = true }
                }
            }
            item { FcButton("Save match result", { onSave(build()) }, icon = Icons.Default.Save, full = true, enabled = canSave) }
            item {
                // a new match has no line-up yet: the button says so
                FcButton(if (performances.isEmpty()) "Add lineup and player stats" else "Edit lineup and player stats (${performances.size})", { page = 1 },
                    kind = ButtonKind.SECONDARY, icon = Icons.Default.Groups, full = true)
            }
            if (onHeadToHead != null && opponent.isNotBlank()) item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { onHeadToHead(opponent.trim()) }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                    FcText("  Head to head vs ${opponent.trim()}", 14.sp, FontWeight.SemiBold, c.accent)
                }
            }
        }
    }
}

/** A line-up row with something entered for it (a rating other than the default, goals, cards, a substitution...). */
internal fun hasNumbers(p: PlayerPerformance): Boolean =
    (p.rating > 0 && p.rating != PlayerPerformance(playerId = "").rating) || p.goals > 0 || p.assists > 0 || p.goalAssisters.any { it != null } ||
        p.subInMinute != null || p.subOutMinute != null || p.yellowCard || p.redCard || p.saves > 0 || p.passes > 0 || p.shots > 0 ||
        p.tackles > 0 || p.interceptions > 0 || p.isMotm

/** Up, the number, down: the design's score stepper. */
@Composable
private fun ScoreStepper(value: Int, onChange: (Int) -> Unit, what: String) {
    val c = FcTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        StepCircle(Icons.Default.KeyboardArrowUp, "More $what") { if (value < 30) onChange(value + 1) }
        FcText("$value", 44.sp, FontWeight.SemiBold, c.text, Modifier.width(56.dp), align = TextAlign.Center)
        StepCircle(Icons.Default.KeyboardArrowDown, "Fewer $what") { if (value > 0) onChange(value - 1) }
    }
}

@Composable
private fun StepCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = FcTheme.colors
    Box(
        Modifier.size(40.dp).clip(CircleShape).border(1.dp, c.border, CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = c.text, modifier = Modifier.size(20.dp)) }
}

/** The rows under a search field: tap one to pick it. */
@Composable
internal fun SuggestionList(items: List<Pair<String, String?>>, onPick: (String, String?) -> Unit) {
    val c = FcTheme.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, c.border, RoundedCornerShape(18.dp))) {
        items.forEachIndexed { i, (name, sub) ->
            if (i > 0) HRule()
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(name, sub) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    FcText(name, 14.sp, FontWeight.SemiBold, maxLines = 1)
                    if (!sub.isNullOrBlank()) Muted(sub, 11.sp, maxLines = 1)
                }
            }
        }
    }
}

/** Lineup & stats: who played, their ratings and numbers, the substitutions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LineupPage(
    state: AppState,
    initialMatch: MatchResult?,
    performances: MutableMap<String, PlayerPerformance>,
    motm: String,
    goalsAgainst: Int,
    subtitle: String,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val c = FcTheme.colors
    var selecting by remember { mutableStateOf(false) }
    val eligible = remember(state.players) { matchEligible(state.players).sortedWith(compareBy({ LineupOrder.rank(it.primaryPosition) }, { LineupOrder.side(it.primaryPosition) }, { it.displayName })) }
    fun nameOf(p: PlayerPerformance) = lineupNameOf(p, state.players, initialMatch)
    fun positionOf(p: PlayerPerformance) = p.position?.takeIf { it.isNotBlank() } ?: state.players.firstOrNull { it.id == p.playerId }?.primaryPosition
    FcPage(header = { FcTopBar("Lineup & stats", sub = subtitle, nav = NavIcon.BACK, onNav = onBack) }) {
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FcText("Match lineup (${performances.size})", 15.sp, FontWeight.Bold, modifier = Modifier.weight(1f))
                    FcButton("Auto-pick XI", {
                        // the eleven on the pitch in Squad, in their places; players who already have numbers stay
                        val eleven = formationEleven(state.players, state.activeFormation)
                        val keep = performances.values.filter { hasNumbers(it) }.map { it.playerId }.toSet()
                        performances.keys.toList().filter { it !in keep && eleven.none { e -> e.first.id == it } }.forEach { performances.remove(it) }
                        eleven.forEach { (p, pos) -> performances[p.id] = (performances[p.id] ?: PlayerPerformance(playerId = p.id)).copy(position = pos) }
                    }, kind = ButtonKind.SECONDARY, small = true)
                    FcButton("Select", { selecting = true }, kind = ButtonKind.SECONDARY, small = true)
                }
            }
        }
        val ordered = LineupOrder.order(performances.values.toList(), initialMatch?.substitutions ?: emptyList(), { positionOf(it) }, { nameOf(it) })
        if (ordered.isEmpty()) item { Muted("No line-up yet. Auto-pick XI takes the eleven on the pitch in Squad (Formation); Select picks players one by one.", 12.sp) }
        else item {
            FcCard(Modifier.fillMaxWidth(), padding = 0.dp, gap = 0.dp) {
                ordered.forEachIndexed { i, perf ->
                    key(perf.playerId) {
                        if (i > 0) HRule()
                        var open by remember { mutableStateOf(false) }
                        val shown = nameOf(perf)
                        val pos = positionOf(perf)
                        val isMotm = motm.isNotBlank() && shown.equals(motm, ignoreCase = true)
                        val assists = perf.assists + performances.values.flatMap { it.goalAssisters }.count { it == perf.playerId }
                        val keeper = pos?.equals("GK", true) == true
                        Column(Modifier.fillMaxWidth().padding(start = if (perf.subInMinute != null) 18.dp else 0.dp)) {
                            Row(
                                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FcText(pos?.uppercase() ?: "", 11.sp, FontWeight.Bold, c.muted, Modifier.width(30.dp))
                                Headshot(state.players.firstOrNull { it.id == perf.playerId }?.headshotUrl, shown, 34.dp)
                                Column(Modifier.weight(1f)) {
                                    FcText(shown, 14.sp, FontWeight.SemiBold, maxLines = 1)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (isMotm) LineupSymbol("MOTM", c.warn) { Icon(Icons.Default.Star, null, tint = c.warn, modifier = Modifier.size(14.dp)) }
                                        if (perf.goals > 0) LineupSymbol(countText(perf.goals), c.muted, if (perf.goals == 1) "1 goal" else "${perf.goals} goals") { Icon(Icons.Default.SportsSoccer, null, tint = c.muted, modifier = Modifier.size(14.dp)) }
                                        if (assists > 0) LineupSymbol(countText(assists), c.muted, if (assists == 1) "1 assist" else "$assists assists") { AssistIcon(c.muted) }
                                        if (keeper && perf.saves > 0) LineupSymbol("${perf.saves} saves", c.muted) { Icon(Icons.Default.Shield, null, tint = c.muted, modifier = Modifier.size(14.dp)) }
                                        if (keeper && goalsAgainst == 0) LineupSymbol("Clean sheet", c.good) { Icon(Icons.Default.Check, null, tint = c.good, modifier = Modifier.size(14.dp)) }
                                        if (perf.yellowCard) LineupSymbol("", c.warn, "Yellow card") { BookingCard(false, 14.dp) }
                                        if (perf.redCard) LineupSymbol("", c.bad, "Red card") { BookingCard(true, 14.dp) }
                                        perf.subInMinute?.let { m -> LineupSymbol("On $m'", c.good) { Icon(Icons.Default.SwapVert, null, tint = c.good, modifier = Modifier.size(14.dp)) } }
                                        perf.subOutMinute?.let { m -> LineupSymbol("Off $m'", c.muted) { Icon(Icons.Default.SwapVert, null, tint = c.muted, modifier = Modifier.size(14.dp)) } }
                                    }
                                }
                                if (perf.rating > 0) RatingChip(perf.rating) else Mono("–", 13.sp, c.muted)
                                Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = c.muted, modifier = Modifier.size(18.dp))
                            }
                            if (open) {
                                Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                                    PerformanceEditor(perf, state.players, performances,
                                        onUpdate = { performances[perf.playerId] = it },
                                        onRemove = { performances.remove(perf.playerId) })
                                }
                            }
                        }
                    }
                }
            }
        }
        item { FcButton("Save match result", onSave, icon = Icons.Default.Save, full = true) }
    }
    if (selecting) {
        FcDialog("Select match lineup", { selecting = false }, actions = { FcButton("Done", { selecting = false }, small = true) }) {
            Muted("${performances.size} selected", 12.sp)
            eligible.forEach { p ->
                val on = performances.containsKey(p.id)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) c.accentBg else c.surface2)
                        .clickable { if (on) performances.remove(p.id) else performances[p.id] = PlayerPerformance(playerId = p.id) }.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Headshot(p.headshotUrl, p.displayName, 34.dp)
                    Column(Modifier.weight(1f)) {
                        FcText(p.displayName, 14.sp, FontWeight.SemiBold, maxLines = 1)
                        Muted(listOfNotNull(p.primaryPosition, if (p.isStartingXI) "In the XI" else null).joinToString(" · "), 11.sp)
                    }
                    if (on) Icon(Icons.Default.Check, contentDescription = "Selected", tint = c.accent, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** One player's match numbers, in the design's fields. */
@Composable
private fun PerformanceEditor(
    perf: PlayerPerformance,
    roster: List<Player>,
    performances: MutableMap<String, PlayerPerformance>,
    onUpdate: (PlayerPerformance) -> Unit,
    onRemove: () -> Unit
) {
    val player = roster.find { it.id == perf.playerId }
    val mates = roster.filter { it.id != perf.playerId }
    fun num(s: String) = s.toIntOrNull() ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (player == null) {
            FcField("Player name (not in the squad list)", perf.playerName?.takeIf { !isPlaceholderPlayerName(it) } ?: "", { onUpdate(perf.copy(playerName = it.ifBlank { null })) })
        }
        RatingEditor(if (perf.rating > 0) perf.rating else 6.5, { onUpdate(perf.copy(rating = it)) })
        FieldPair(
            { m ->
                FcNumberField("Goals", "${perf.goals}", { v ->
                    val g = num(v).coerceAtMost(15)
                    val list = perf.goalAssisters.toMutableList()
                    while (list.size < g) list.add(null)
                    while (list.size > g) list.removeAt(list.lastIndex)
                    onUpdate(perf.copy(goals = g, goalAssisters = list))
                }, m)
            },
            { m -> FcNumberField("Assists", "${perf.assists}", { onUpdate(perf.copy(assists = num(it))) }, m) }
        )
        perf.goalAssisters.forEachIndexed { index, assisterId ->
            val label = mates.find { it.id == assisterId }?.displayName ?: "Unassisted"
            FcSelectField("Goal ${index + 1} assisted by", label, listOf("Unassisted") + mates.map { it.displayName }, { pick ->
                val list = perf.goalAssisters.toMutableList()
                list[index] = mates.firstOrNull { it.displayName == pick }?.id
                onUpdate(perf.copy(goalAssisters = list))
            })
        }
        FieldPair({ m -> FcNumberField("Passes", "${perf.passes}", { onUpdate(perf.copy(passes = num(it))) }, m) }, { m -> FcNumberField("Pass accuracy %", "${perf.passAccuracy}", { onUpdate(perf.copy(passAccuracy = num(it).coerceAtMost(100))) }, m) })
        FieldPair({ m -> FcNumberField("Shots", "${perf.shots}", { onUpdate(perf.copy(shots = num(it))) }, m) }, { m -> FcNumberField("Shot accuracy %", "${perf.shotAccuracy}", { onUpdate(perf.copy(shotAccuracy = num(it).coerceAtMost(100))) }, m) })
        FieldPair({ m -> FcNumberField("Tackles", "${perf.tackles}", { onUpdate(perf.copy(tackles = num(it))) }, m) }, { m -> FcNumberField("Tackle %", "${perf.tacklePercentage}", { onUpdate(perf.copy(tacklePercentage = num(it).coerceAtMost(100))) }, m) })
        FieldPair({ m -> FcNumberField("Interceptions", "${perf.interceptions}", { onUpdate(perf.copy(interceptions = num(it))) }, m) }, { m -> FcNumberField("Saves", "${perf.saves}", { onUpdate(perf.copy(saves = num(it))) }, m) })
        FcNumberField("Penalty saves", "${perf.penaltySaves}", { onUpdate(perf.copy(penaltySaves = num(it))) })
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FcCheckRow("Yellow card", perf.yellowCard, { onUpdate(perf.copy(yellowCard = it)) })
            FcCheckRow("Red card", perf.redCard, { onUpdate(perf.copy(redCard = it)) })
        }
        FcCheckRow("Clean sheet", perf.cleanSheet, { onUpdate(perf.copy(cleanSheet = it)) })

        var subbed by remember { mutableStateOf(perf.subInMinute != null || perf.subOutMinute != null) }
        var subOut by remember { mutableStateOf(perf.subInMinute == null) }
        var minute by remember { mutableStateOf((perf.subOutMinute ?: perf.subInMinute)?.toString() ?: "") }
        FcCheckRow("Involved in a substitution", subbed, { on ->
            subbed = on
            if (!on) onUpdate(perf.copy(subInMinute = null, subOutMinute = null))
        })
        if (subbed) {
            FieldPair(
                { m -> FcSelectField("Subbed", if (subOut) "Subbed out" else "Subbed in", listOf("Subbed out", "Subbed in"), { subOut = it == "Subbed out" }, m) },
                { m -> FcNumberField("Minute", minute, { minute = it.take(3) }, m) }
            )
            FcSelectField(if (subOut) "Replaced by" else "Came on for", "Choose a player", mates.map { it.displayName }, { pick ->
                val mate = mates.firstOrNull { it.displayName == pick } ?: return@FcSelectField
                val min = minute.toIntOrNull() ?: 0
                if (subOut) {
                    onUpdate(perf.copy(subOutMinute = min, subInMinute = null))
                    performances[mate.id] = (performances[mate.id] ?: PlayerPerformance(playerId = mate.id)).copy(subInMinute = min, subOutMinute = null)
                } else {
                    onUpdate(perf.copy(subInMinute = min, subOutMinute = null))
                    performances[mate.id] = (performances[mate.id] ?: PlayerPerformance(playerId = mate.id)).copy(subOutMinute = min)
                }
            })
        }
        FcButton("Remove from the line-up", onRemove, kind = ButtonKind.DANGER, small = true)
    }
}
