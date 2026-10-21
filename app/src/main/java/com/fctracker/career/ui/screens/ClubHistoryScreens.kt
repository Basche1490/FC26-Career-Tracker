package com.fctracker.career.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.AppState
import com.fctracker.career.ClubInfo
import com.fctracker.career.MainViewModel
import com.fctracker.career.ai.ClubHistory
import com.fctracker.career.logic.Milestones
import com.fctracker.career.logic.SeasonView
import com.fctracker.career.ui.components.ButtonKind
import com.fctracker.career.ui.components.ClubCrest
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.FcCard
import com.fctracker.career.ui.components.FcChip
import com.fctracker.career.ui.components.FcDialog
import com.fctracker.career.ui.components.FcField
import com.fctracker.career.ui.components.FcLabel
import com.fctracker.career.ui.components.FcNumberField
import com.fctracker.career.ui.components.FcPage
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.FcTopBar
import com.fctracker.career.ui.components.FieldPair
import com.fctracker.career.ui.components.HRule
import com.fctracker.career.ui.components.KeyValue
import com.fctracker.career.ui.components.Mono
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.NavIcon
import com.fctracker.career.ui.components.SectionHeader
import com.fctracker.career.ui.components.Segmented
import com.fctracker.career.ui.theme.FcTheme

private fun yourTime(state: AppState): List<Pair<String, String>> {
    val first = state.historicalSeasons.minOfOrNull { it.year } ?: state.startYear
    val finished = state.historicalSeasons.size
    val trophies = state.teamTrophies.map { it.name }
    return listOf(
        "Manager since" to state.clubInfo.managerSince.ifBlank { "July $first" },
        "Seasons" to "${finished + 1} ($finished finished)",
        "Trophies with you" to (trophies.joinToString(", ").ifBlank { "None yet" })
    )
}

/**
 * The club history pop-up (tap the crest). A real club: the game's facts, the opening of its Wikipedia article and
 * the person's time there. The person's own club: the history they wrote (or the AI wrote from their answers).
 */
@Composable
fun ClubHistoryDialog(viewModel: MainViewModel, state: AppState, onDismiss: () -> Unit, onWrite: () -> Unit) {
    val c = FcTheme.colors
    val ci = state.clubInfo
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var editFacts by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val real = state.isExistingTeam
    LaunchedEffect(real) {
        if (real && (ci.history.isBlank() || ci.historySource != "wikipedia")) {
            loading = true
            viewModel.fetchWikipediaHistory { text -> loading = false; failed = text == null }
        }
    }
    FcDialog("Club history", onDismiss, actions = {
        if (!real && ci.history.isNotBlank()) {
            FcButton("Edit text", onWrite, kind = ButtonKind.SECONDARY, icon = Icons.Default.Edit, small = true)
            FcButton("Rewrite", onWrite, kind = ButtonKind.SECONDARY, icon = Icons.Default.Refresh, small = true)
        } else {
            FcButton("Edit facts", { editFacts = true }, kind = ButtonKind.SECONDARY, small = true)
        }
        FcButton("Close", onDismiss, small = true)
    }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(c.inset), contentAlignment = Alignment.Center) {
                ClubCrest(state.clubName, state.clubCrestFile, state.clubCrestUrl, real, 50.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FcText(state.clubName, 20.sp, FontWeight.SemiBold)
                val line = if (real) "${state.country} · ${state.leagueName}" else listOf(ci.nickname, ci.hometown).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Your own club" }
                Muted(line, 12.sp)
            }
        }
        FcLabel("From the game")
        val facts = buildList {
            if (ci.founded.isNotBlank()) add("Founded" to ci.founded)
            if (ci.stadium.isNotBlank()) add("Stadium" to (ci.stadium + if (ci.capacity > 0) " · %,d".format(ci.capacity) else ""))
            if (real) add("League" to state.leagueName)
            if (real && ci.clubWorth.isNotBlank()) add("Club worth" to ci.clubWorth)
            if (real || ci.leagueTitles > 0) add("League titles" to "${ci.leagueTitles}")
            if (real || ci.domesticCups > 0) add("Domestic cups" to "${ci.domesticCups}")
            if (real || ci.europeanTrophies > 0) add("European trophies" to "${ci.europeanTrophies}")
            if (ci.rival.isNotBlank()) add("Rival" to ci.rival)
        }
        if (facts.isEmpty()) Muted(if (state.platform == "console") "Add the club's facts with Edit facts." else "The club's facts arrive with the next report from your PC.", 12.sp)
        facts.forEach { (k, v) -> KeyValue(k, v) }
        if (real && (ci.prestigeHome > 0 || ci.prestigeAbroad > 0)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column { Muted("Prestige at home", 11.sp); Mono("${ci.prestigeHome}/10", 13.sp, weight = FontWeight.SemiBold) }
                Column { Muted("Prestige abroad", 11.sp); Mono("${ci.prestigeAbroad}/10", 13.sp, weight = FontWeight.SemiBold) }
            }
        }
        HRule()
        FcLabel("History")
        when {
            real && loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                Muted("Looking the club up on Wikipedia…", 13.sp)
            }
            ci.history.isNotBlank() -> {
                FcText(ci.history, 13.sp, lineHeight = 20.sp)
                ci.seasonLines.forEach { Muted(it, 12.sp) }
                if (ci.historySource == "wikipedia") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Muted("From Wikipedia", 11.sp, Modifier.weight(1f))
                        if (ci.historyUrl.isNotBlank()) Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).clickable { try { uri.openUri(ci.historyUrl) } catch (e: Exception) {} }.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FcText("Read more", 12.sp, FontWeight.SemiBold, c.accent)
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
                        }
                    }
                } else {
                    Muted(if (ci.historySource == "ai") "Written with AI from your answers. You can change any word; the app adds a line at the end of every season." else "Written by you. The app adds a line at the end of every season.", 11.sp)
                }
            }
            real -> Muted(if (failed) "The history needs an internet connection. Try again later." else "No history found for this club.", 13.sp)
            else -> {
                Muted("No history yet. Write it yourself, or answer a few questions and let the AI write it.", 13.sp)
                FcButton("Write the history", onWrite, icon = Icons.Default.SmartToy, small = true)
            }
        }
        HRule()
        FcLabel("Your time here")
        yourTime(state).forEach { (k, v) -> KeyValue(k, v) }
        if (real) Muted("Only for a real club. The history needs an internet connection; the rest comes from your game and the app.", 11.sp)
    }
    if (editFacts) ClubFactsDialog(ci, { viewModel.updateClubInfo(it); editFacts = false }, { editFacts = false })
}

/** The club's facts by hand (console, or to correct the game). */
@Composable
fun ClubFactsDialog(info: ClubInfo, onSave: (ClubInfo) -> Unit, onDismiss: () -> Unit) {
    var founded by remember { mutableStateOf(info.founded) }
    var stadium by remember { mutableStateOf(info.stadium) }
    var capacity by remember { mutableStateOf(if (info.capacity > 0) "${info.capacity}" else "") }
    var rival by remember { mutableStateOf(info.rival) }
    var titles by remember { mutableStateOf("${info.leagueTitles}") }
    var cups by remember { mutableStateOf("${info.domesticCups}") }
    var europe by remember { mutableStateOf("${info.europeanTrophies}") }
    var nickname by remember { mutableStateOf(info.nickname) }
    var hometown by remember { mutableStateOf(info.hometown) }
    var since by remember { mutableStateOf(info.managerSince) }
    FcDialog("Club facts", onDismiss, actions = {
        FcButton("Cancel", onDismiss, kind = ButtonKind.SECONDARY, small = true)
        FcButton("Save", {
            onSave(info.copy(founded = founded.trim(), stadium = stadium.trim(), capacity = capacity.toIntOrNull() ?: 0, rival = rival.trim(),
                leagueTitles = titles.toIntOrNull() ?: 0, domesticCups = cups.toIntOrNull() ?: 0, europeanTrophies = europe.toIntOrNull() ?: 0,
                nickname = nickname.trim(), hometown = hometown.trim(), managerSince = since.trim()))
        }, small = true)
    }) {
        FieldPair({ m -> FcField("Founded", founded, { founded = it }, m, placeholder = "1898") }, { m -> FcField("Nickname", nickname, { nickname = it }, m) })
        FcField("Stadium", stadium, { stadium = it })
        FieldPair({ m -> FcNumberField("Capacity", capacity, { capacity = it }, m) }, { m -> FcField("Rival", rival, { rival = it }, m) })
        FcField("Town or city", hometown, { hometown = it })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FcNumberField("League titles", titles, { titles = it }, Modifier.weight(1f))
            FcNumberField("Domestic cups", cups, { cups = it }, Modifier.weight(1f))
            FcNumberField("European", europe, { europe = it }, Modifier.weight(1f))
        }
        FcField("Manager since", since, { since = it }, placeholder = "July 2024")
    }
}

/** Your own club's history: write it yourself, or answer a few questions and let the AI write it. */
@Composable
fun ClubHistoryEditScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val ci = state.clubInfo
    val c = FcTheme.colors
    var mode by remember { mutableStateOf(if (ci.historySource == "own") 0 else 1) }
    var own by remember { mutableStateOf(ci.history) }
    var hometown by remember { mutableStateOf(ci.aiAnswers["hometown"] ?: ci.hometown) }
    var nickname by remember { mutableStateOf(ci.aiAnswers["nickname"] ?: ci.nickname) }
    var origin by remember { mutableStateOf(ci.aiAnswers["origin"] ?: ClubHistory.ORIGINS.first()) }
    var must by remember { mutableStateOf(ci.aiAnswers["mustInclude"] ?: "") }
    var tone by remember { mutableStateOf(ci.aiAnswers["tone"] ?: ClubHistory.TONES.first()) }
    var length by remember { mutableStateOf(ci.aiAnswers["length"] ?: "Medium") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var editFacts by remember { mutableStateOf(false) }
    val seasonsLine = state.historicalSeasons.sortedBy { it.year }.joinToString(" · ") { "${SeasonView.seasonLabel(it.year)} ${Milestones.ordinal(it.finalPosition)} in the ${it.leagueName}" }

    FcPage(header = { FcTopBar("Club history", sub = "Your own club", nav = NavIcon.BACK, onNav = onBack) }) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ClubCrest(state.clubName, state.clubCrestFile, state.clubCrestUrl, false, 48.dp)
                Column {
                    FcText(state.clubName, 18.sp, FontWeight.SemiBold)
                    Muted(if (ci.history.isBlank()) "Your own club · no history yet" else "Your own club", 12.sp)
                }
            }
        }
        item { Segmented(listOf("Write it myself", "Write it with AI"), mode, { mode = it }) }
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FcLabel("Already known (from the game and the app)", Modifier.weight(1f))
                    FcText("Edit", 13.sp, FontWeight.Bold, c.accent, Modifier.clip(RoundedCornerShape(8.dp)).clickable { editFacts = true }.padding(6.dp))
                }
                KeyValue("Founded", ci.founded.ifBlank { "–" })
                KeyValue("Stadium", ci.stadium.ifBlank { "–" })
                KeyValue("Colours", ci.colours.ifBlank { "${state.primaryColorHex} · ${state.secondaryColorHex}" })
                KeyValue("Rival", ci.rival.ifBlank { "–" })
                KeyValue("Your seasons", seasonsLine.ifBlank { "First season" })
            }
        }
        if (mode == 0) {
            item {
                FcField("History", own, { own = it }, singleLine = false, minLines = 8, placeholder = "Founded in … by …",
                    hint = "Start from the facts above. The app adds a line at the end of every season.")
            }
            item { FcButton("Save history", { viewModel.saveOwnHistory(own); onBack() }, full = true, enabled = own.isNotBlank()) }
        } else {
            item {
                FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 14.dp) {
                    SectionHeader("A few questions", icon = Icons.Default.SmartToy)
                    FcField("Where is the club from?", hometown, { hometown = it }, hint = "Town, city or area.")
                    FcField("Nickname", nickname, { nickname = it }, hint = "Optional. Leave blank and the AI suggests one.")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FcText("How did the club start?", 14.sp, FontWeight.SemiBold)
                        ChipWrap(ClubHistory.ORIGINS, origin) { origin = it }
                    }
                    FcField("Anything that must be in it?", must, { must = it }, singleLine = false, minLines = 2, hint = "Optional: a famous fan, an old ground, a cup run, a legend.")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FcText("How should it sound?", 14.sp, FontWeight.SemiBold)
                        ChipWrap(ClubHistory.TONES, tone) { tone = it }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FcText("Length", 14.sp, FontWeight.SemiBold)
                        Segmented(ClubHistory.LENGTHS, ClubHistory.LENGTHS.indexOf(length).coerceAtLeast(0), { length = ClubHistory.LENGTHS[it] })
                    }
                }
            }
            item {
                FcButton(if (working) "Writing…" else if (result == null) "Write the history" else "Rewrite", {
                    working = true; error = null
                    viewModel.writeHistoryWithAi(mapOf("hometown" to hometown, "nickname" to nickname, "origin" to origin, "mustInclude" to must, "tone" to tone, "length" to length)) { text, err ->
                        working = false
                        if (text != null) { result = text; own = text } else error = err
                    }
                }, full = true, icon = Icons.Default.SmartToy, enabled = !working)
            }
            error?.let { item { com.fctracker.career.ui.components.Notice(com.fctracker.career.ui.components.Tone.BAD, "The AI could not write it", it) } }
            result?.let { text ->
                item {
                    FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                        FcLabel("The history")
                        FcField("", own, { own = it }, singleLine = false, minLines = 6)
                        Muted("Saved. Change any word and tap Save to keep your version.", 11.sp)
                        FcButton("Save", { viewModel.saveOwnHistory(own); onBack() }, small = true)
                    }
                }
            }
            item { Muted("Uses the AI agent from Settings (${state.aiAgent}). Nothing is sent until you tap the button; then only these answers and the facts above go, never your save.", 11.sp) }
        }
    }
    if (editFacts) ClubFactsDialog(ci, { viewModel.updateClubInfo(it); editFacts = false }, { editFacts = false })
}

/** Chips that wrap onto the next line, one of them chosen. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipWrap(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FcChip(o, o == selected, { onSelect(o) }) }
    }
}
