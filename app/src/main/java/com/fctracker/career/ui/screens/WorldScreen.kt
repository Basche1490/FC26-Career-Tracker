package com.fctracker.career.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.AppState
import com.fctracker.career.MainViewModel
import com.fctracker.career.Platform
import com.fctracker.career.Source
import com.fctracker.career.WorldTopScorer
import com.fctracker.career.WorldWinner
import com.fctracker.career.logic.CareerOps
import com.fctracker.career.logic.LeagueTables
import com.fctracker.career.logic.Milestones
import com.fctracker.career.logic.SeasonView
import com.fctracker.career.logic.WorldWinners
import com.fctracker.career.logic.ZoneKind
import com.fctracker.career.telemetry.ContractTime
import com.fctracker.career.ui.components.ArchivedBanner
import com.fctracker.career.ui.components.ButtonKind
import com.fctracker.career.ui.components.ChipRow
import com.fctracker.career.ui.components.CompLogo
import com.fctracker.career.ui.components.ConfirmDialog
import com.fctracker.career.ui.components.EmptyCard
import com.fctracker.career.ui.components.Fab
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.FcCard
import com.fctracker.career.ui.components.FcDialog
import com.fctracker.career.ui.components.FcField
import com.fctracker.career.ui.components.FcNumberField
import com.fctracker.career.ui.components.FcPage
import com.fctracker.career.ui.components.FcSelectField
import com.fctracker.career.ui.components.FcSwitchRow
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.FcTopBar
import com.fctracker.career.ui.components.FieldPair
import com.fctracker.career.ui.components.Figure
import com.fctracker.career.ui.components.Headshot
import com.fctracker.career.ui.components.KeyValue
import com.fctracker.career.ui.components.Mono
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.Notice
import com.fctracker.career.ui.components.SeasonChip
import com.fctracker.career.ui.components.SectionHeader
import com.fctracker.career.ui.components.Tag
import com.fctracker.career.ui.components.Tone
import com.fctracker.career.ui.theme.FcTheme

/** The competitions the page knows per region (a console player picks from these, or types another). */
val WORLD_COMPETITIONS = linkedMapOf(
    "Europe" to listOf("UEFA Champions League", "UEFA Europa League", "UEFA Conference League", "UEFA Super Cup"),
    "England" to listOf("Premier League", "FA Cup", "Carabao Cup", "Community Shield"),
    "Spain" to listOf("LALIGA EA SPORTS", "Copa del Rey", "Supercopa de España"),
    "Italy" to listOf("Serie A", "Coppa Italia", "Supercoppa Italiana"),
    "Germany" to listOf("Bundesliga", "DFB-Pokal", "DFL-Supercup"),
    "France" to listOf("Ligue 1", "Coupe de France", "Trophée des Champions")
)
private val LEAGUES = setOf("Premier League", "LALIGA EA SPORTS", "Serie A", "Bundesliga", "Ligue 1")

/** Around the World: last season's winners of the top five leagues, their cups and the European cups. Changes once a year, in June. */
@Composable
fun WorldScreen(viewModel: MainViewModel, onMenuClick: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val view = remember(state) { SeasonView.of(state) }
    val console = state.platform == Platform.CONSOLE
    val seasons = state.worldWinners.filter { WorldWinners.isShown(it.competition) }.map { it.season }.distinct().sortedDescending()
    var picked by remember { mutableStateOf<Int?>(null) }
    val season = when {
        view.isArchived -> view.season
        picked != null && picked in seasons -> picked!!
        else -> seasons.firstOrNull() ?: CareerOps.worldSeasonToFill(state)
    }
    val regions = WorldWinners.byRegion(state.worldWinners, season)
    val scorers = state.worldTopScorers.filter { it.season == season }
    val careerDate = ContractTime.referenceDate(state)
    val next = WorldWinners.nextUpdate(careerDate, state.startYear)
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WorldWinner?>(null) }
    val editable = console && !view.isArchived

    FcPage(
        header = {
            if (view.isArchived) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { ArchivedBanner("${view.label} · ${view.leagueName}", { viewModel.setViewingSeasonYear(null) }) }
            FcTopBar("Around the World", sub = if (seasons.isEmpty()) "League and cup winners" else "League and cup winners · updated June ${(seasons.first()) + 1}", onNav = onMenuClick)
        },
        fab = if (editable) ({ Fab("Add winner", { adding = true }) }) else null
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SeasonChip(SeasonView.seasonLabel(season))
                Muted("Winners of the season", 12.sp)
            }
        }
        if (!view.isArchived && seasons.size > 1) item { ChipRow(seasons.map { SeasonView.seasonLabel(it) }, seasons.indexOf(season).coerceAtLeast(0), { picked = seasons[it] }) }
        if (!view.isArchived) item {
            Notice(Tone.INFO, "Next update: $next (in-game)",
                if (console) "This page changes once a year, in June, after the last finals. On console you add the winners yourself: a reminder appears on Home in June. World Cups, Euros and other international matches are left out."
                else "This page changes once a year, in June, after the last finals and before the new season starts. World Cups, Euros and other international matches are left out.",
                icon = Icons.Default.Schedule)
        }
        if (regions.isEmpty()) {
            item {
                if (view.isArchived) EmptyCard("No winners were recorded for ${SeasonView.seasonLabel(season)}.")
                else EmptyCard("The first winners arrive in $next (in-game). The page fills in once a year.${if (console) " Use Add winner to fill it in yourself." else ""}")
            }
        }
        regions.forEach { (region, list) ->
            item(key = "region-$region-$season") {
                val scorer = scorers.firstOrNull { it.region.equals(region, true) }
                FcCard(Modifier.fillMaxWidth(), padding = 16.dp, gap = 14.dp) {
                    SectionHeader(region, icon = if (region == "Europe") Icons.Default.Language else Icons.Default.Shield)
                    list.forEach { w -> WinnerRow(w, onClick = if (editable) ({ editing = w }) else null) }
                    if (region == "Europe" && !console) {
                        listOf("UEFA Champions League", "UEFA Europa League", "UEFA Conference League").filter { key -> list.none { it.competition.contains(key.removePrefix("UEFA "), true) } }.forEach { MissingRow(it) }
                    }
                    if (scorer != null) ScorerRow(scorer, if (region == "Europe") "Top scorer in Europe" else "Top scorer in the league")
                }
            }
        }
        if (regions.isNotEmpty() || view.isArchived) item { YourLeagueCard(state, season) }
        item {
            Muted(if (console) "On console you fill this page in yourself: tap a winner to change or remove it. Women’s competitions are left out."
                  else "League winners, cup finals and top scorers come from your game. Women’s competitions are left out.", 11.sp)
        }
    }

    if (adding) WinnerDialog(state, null, CareerOps.worldSeasonToFill(state).takeIf { seasons.isEmpty() || picked == null } ?: season, onDismiss = { adding = false }, onSave = { w, scorer ->
        viewModel.addWorldWinner(w); scorer?.let { viewModel.addWorldTopScorer(it) }; adding = false
    })
    editing?.let { e ->
        WinnerDialog(state, e, e.season, onDismiss = { editing = null }, onSave = { w, scorer ->
            if (!w.competition.equals(e.competition, true)) viewModel.deleteWorldWinner(e.season, e.competition)
            viewModel.addWorldWinner(w); scorer?.let { viewModel.addWorldTopScorer(it) }; editing = null
        }, onDelete = { viewModel.deleteWorldWinner(e.season, e.competition); editing = null })
    }
}

@Composable
private fun WinnerRow(w: WorldWinner, onClick: (() -> Unit)?) {
    val c = FcTheme.colors
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CompLogo(w.competition, 32.dp)
        Column(Modifier.weight(1f)) {
            Muted(w.competition, 12.sp, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.EmojiEvents, null, tint = c.warn, modifier = Modifier.size(15.dp))
                FcText(w.winner, 15.sp, FontWeight.SemiBold, maxLines = 1)
            }
            Mono(WorldWinners.detail(w), 12.sp, c.muted, FontWeight.Normal)
        }
        if (w.source == Source.MANUAL) Tag("Yours", Tone.MUTED)
    }
}

@Composable
private fun MissingRow(competition: String) {
    Row(Modifier.fillMaxWidth().alpha(0.7f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CompLogo(competition, 32.dp)
        Column(Modifier.weight(1f)) {
            Muted(competition, 12.sp)
            Muted("Not in your game’s fixture list", 13.sp)
        }
    }
}

@Composable
private fun ScorerRow(s: WorldTopScorer, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Headshot(null, s.player, 32.dp)
        Column(Modifier.weight(1f)) {
            Muted(label, 12.sp)
            FcText(listOf(s.player, s.club).filter { it.isNotBlank() }.joinToString(" · "), 14.sp, FontWeight.SemiBold, maxLines = 1)
        }
        Figure("${s.goals}", 18.sp, weight = FontWeight.SemiBold)
        Muted("goals", 11.sp)
    }
}

/** The person's own league in that season, from its final table. */
@Composable
private fun YourLeagueCard(state: AppState, season: Int) {
    val archived = state.historicalSeasons.firstOrNull { it.year == season }
    val league = archived?.leagueName ?: state.leagueName
    val table = archived?.leagueTables?.firstOrNull { it.league.equals(league, true) }
    val rows = table?.let { LeagueTables.sorted(it.rows) }.orEmpty()
    FcCard(Modifier.fillMaxWidth(), padding = 16.dp, gap = 12.dp) {
        SectionHeader("Your league · $league", icon = Icons.Default.Shield)
        if (rows.isEmpty()) {
            if (archived != null) KeyValue(state.clubName, Milestones.ordinal(archived.finalPosition))
            Muted(if (archived == null) "Fills in when ${SeasonView.seasonLabel(season)} is finished." else "The final table of this season was not kept.", 12.sp)
            return@FcCard
        }
        val zones = LeagueTables.zonesFor(league, rows.size)
        fun clubsIn(vararg kinds: ZoneKind) = zones.filter { it.kind in kinds }.flatMap { z -> (z.from..z.to).mapNotNull { rows.getOrNull(it - 1)?.club } }
        val me = rows.indexOfFirst { it.isUser || it.club.equals(state.clubName, true) } + 1
        KeyValue("Champions", rows.first().club, mono = false)
        val promoted = clubsIn(ZoneKind.PROMOTION).drop(if (zones.any { it.kind == ZoneKind.PROMOTION && it.from == 1 }) 1 else 0)
        if (promoted.isNotEmpty()) KeyValue("Also promoted", promoted.joinToString(", "), mono = false)
        val playoff = clubsIn(ZoneKind.PLAYOFF)
        if (playoff.isNotEmpty()) KeyValue("Play-off places", playoff.joinToString(", "), mono = false)
        val europe = clubsIn(ZoneKind.CHAMPIONS_LEAGUE).filter { it != rows.first().club }
        if (europe.isNotEmpty()) KeyValue("Also in the Champions League", europe.joinToString(", "), mono = false)
        val down = clubsIn(ZoneKind.RELEGATION)
        if (down.isNotEmpty()) KeyValue("Relegated", down.joinToString(", "), mono = false)
        if (me > 0) KeyValue(state.clubName, Milestones.ordinal(me), FcTheme.colors.accent)
    }
}

/** Console: add or change one winner (and, optionally, that league's top scorer). */
@Composable
private fun WinnerDialog(state: AppState, existing: WorldWinner?, season: Int, onDismiss: () -> Unit, onSave: (WorldWinner, WorldTopScorer?) -> Unit, onDelete: (() -> Unit)? = null) {
    val regions = (WORLD_COMPETITIONS.keys.toList() + state.country).distinct()
    var seasonPick by remember { mutableStateOf(existing?.season ?: season) }
    var region by remember { mutableStateOf(existing?.region ?: "Europe") }
    var competition by remember { mutableStateOf(existing?.competition ?: WORLD_COMPETITIONS["Europe"]!!.first()) }
    var winner by remember { mutableStateOf(existing?.winner.orEmpty()) }
    var isLeague by remember { mutableStateOf(existing?.isLeague ?: false) }
    var detail by remember { mutableStateOf(existing?.detail.orEmpty()) }
    var score by remember { mutableStateOf(existing?.score.orEmpty()) }
    var runner by remember { mutableStateOf(existing?.runnerUp.orEmpty()) }
    var pens by remember { mutableStateOf(existing?.penalties?.isNotBlank() == true) }
    var shootout by remember { mutableStateOf(existing?.penalties.orEmpty()) }
    val oldScorer = state.worldTopScorers.firstOrNull { it.season == seasonPick && it.region.equals(region, true) }
    var scorer by remember(region, seasonPick) { mutableStateOf(oldScorer?.player.orEmpty()) }
    var scorerClub by remember(region, seasonPick) { mutableStateOf(oldScorer?.club.orEmpty()) }
    var goals by remember(region, seasonPick) { mutableStateOf(oldScorer?.goals?.takeIf { it > 0 }?.toString().orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val options = WORLD_COMPETITIONS[region].orEmpty()
    FcDialog(if (existing == null) "Add winner" else "Change winner", onDismiss, actions = {
        if (onDelete != null) FcButton("Remove", { confirmDelete = true }, kind = ButtonKind.DANGER, small = true)
        FcButton("Cancel", onDismiss, kind = ButtonKind.SECONDARY, small = true)
        FcButton("Save", {
            val w = WorldWinner(
                season = seasonPick, region = region, competition = competition.trim(), winner = winner.trim(),
                runnerUp = if (isLeague) "" else runner.trim(), score = if (isLeague) "" else score.trim().replace("-", "–"),
                penalties = if (!isLeague && pens) shootout.trim().replace("-", "–") else "", detail = if (isLeague) detail.trim() else "", isLeague = isLeague, source = Source.MANUAL
            )
            val s = if (scorer.isNotBlank()) WorldTopScorer(seasonPick, region, competition.trim(), scorer.trim(), scorerClub.trim(), goals.toIntOrNull() ?: 0, Source.MANUAL) else null
            onSave(w, s)
        }, small = true, enabled = competition.isNotBlank() && winner.isNotBlank() && WorldWinners.isShown(competition))
    }) {
        FcSelectField("Season", SeasonView.seasonLabel(seasonPick), (0..3).map { SeasonView.seasonLabel(season - it) }, { label -> label.take(4).toIntOrNull()?.let { seasonPick = it } })
        FcSelectField("Where", region, regions, { r ->
            region = r
            WORLD_COMPETITIONS[r]?.firstOrNull()?.let { competition = it; isLeague = it in LEAGUES }
        })
        if (options.isNotEmpty()) FcSelectField("Competition", competition, options, { competition = it; isLeague = it in LEAGUES })
        else FcField("Competition", competition, { competition = it }, placeholder = "Championship")
        if (!WorldWinners.isShown(competition) && competition.isNotBlank()) Muted("International, women’s and youth competitions are left out of this page.", 12.sp)
        FcField("Winner", winner, { winner = it }, placeholder = "Real Madrid")
        FcSwitchRow("This is a league", isLeague, { isLeague = it }, sub = "A league has no final: add the points and the runner-up instead.")
        if (isLeague) {
            FcField("Points and runner-up", detail, { detail = it }, placeholder = "84 pts · Liverpool 2nd")
        } else {
            FieldPair({ m -> FcField("Score in the final", score, { score = it }, m, placeholder = "2–1") }, { m -> FcField("Runner-up", runner, { runner = it }, m, placeholder = "Arsenal") })
            FcSwitchRow("Decided on penalties", pens, { pens = it })
            if (pens) FcField("Shoot-out", shootout, { shootout = it }, placeholder = "5–4")
        }
        FcText(if (region == "Europe") "Top scorer in Europe (optional)" else "Top scorer in the league (optional)", 14.sp, FontWeight.SemiBold)
        FcField("Player", scorer, { scorer = it })
        FieldPair({ m -> FcField("Club", scorerClub, { scorerClub = it }, m) }, { m -> FcNumberField("Goals", goals, { goals = it }, m) })
    }
    if (confirmDelete && onDelete != null) ConfirmDialog("Remove this winner?", "${existing?.competition}: ${existing?.winner}", "Remove", onDelete, { confirmDelete = false })
}
