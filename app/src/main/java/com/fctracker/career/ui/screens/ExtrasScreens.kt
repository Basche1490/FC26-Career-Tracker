package com.fctracker.career.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.MainViewModel
import com.fctracker.career.Milestone
import com.fctracker.career.logic.Accent
import com.fctracker.career.logic.Dates
import com.fctracker.career.logic.SeasonView
import com.fctracker.career.ui.components.ArchivedBanner
import com.fctracker.career.ui.components.Bullet
import com.fctracker.career.ui.components.ButtonKind
import com.fctracker.career.ui.components.ClubCrest
import com.fctracker.career.ui.components.CompLogo
import com.fctracker.career.ui.components.EmptyCard
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.FcCard
import com.fctracker.career.ui.components.FcLabel
import com.fctracker.career.ui.components.FcPage
import com.fctracker.career.ui.components.FcSwitchRow
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.FcTopBar
import com.fctracker.career.ui.components.Headshot
import com.fctracker.career.ui.components.Mono
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.NavIcon
import com.fctracker.career.ui.components.SectionHeader
import com.fctracker.career.ui.components.Tag
import com.fctracker.career.ui.components.Tone
import com.fctracker.career.ui.theme.FcTheme
import com.fctracker.career.ui.theme.parseHex
import com.fctracker.career.widget.LargeWidget
import com.fctracker.career.widget.SmallWidget
import com.fctracker.career.widget.WidgetData
import com.fctracker.career.widget.WidgetUpdater

// =====================================================================================================================
// Milestones
// =====================================================================================================================

private fun kindTag(kind: String): Pair<String, Tone> = when (kind) {
    "apps" -> "Apps" to Tone.INFO
    "goals" -> "Goals" to Tone.GOOD
    "assists" -> "Assists" to Tone.GOOD
    "growth" -> "Growth" to Tone.ACCENT
    "record" -> "Record" to Tone.WARN
    else -> "Milestone" to Tone.MUTED
}

/** Milestone alerts: what the alerts look like, this season's milestones, and which kinds alert. */
@Composable
fun MilestonesScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val view = remember(state) { SeasonView.of(state) }
    val c = FcTheme.colors
    val list = view.milestones.sortedByDescending { it.date }
    val prefs = state.milestonePrefs
    val headshots = remember(state) { (state.players + state.academyPlayers + state.historicalSeasons.flatMap { it.players }).associate { it.id to it.headshotUrl } }
    FcPage(header = {
        if (view.isArchived) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { ArchivedBanner("${view.label} · ${view.leagueName}", { viewModel.setViewingSeasonYear(null) }) }
        FcTopBar("Milestones", sub = "Alerts and ${if (view.isArchived) view.label else "this season"}’s list", nav = NavIcon.BACK, onNav = onBack)
    }) {
        if (!view.isArchived) {
            item { FcLabel("ON YOUR PHONE") }
            if (list.isEmpty()) item {
                AlertPreview("Marco Varela: 150 matches", "150th appearance for ${state.clubName}, in the 3–1 win against Leicester City.", "example")
            } else list.take(3).forEachIndexed { i, m -> item(key = "alert-${m.key}-$i") { AlertPreview(m.title, m.detail, Dates.dayMonth(m.date)) } }
        }
        item { SectionHeader(if (view.isArchived) view.label else "This season", icon = Icons.Default.EmojiEvents) }
        if (list.isEmpty()) item { EmptyCard("No milestones ${if (view.isArchived) "that season" else "yet this season"}. They are noticed by themselves from your matches and the game’s numbers.") }
        else item {
            RowsCard {
                list.forEachIndexed { i, m ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (m.kind == "record" || m.playerId.isBlank()) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(c.warnBg), contentAlignment = Alignment.Center) { Icon(Icons.Default.EmojiEvents, null, tint = c.warn, modifier = Modifier.size(20.dp)) }
                        } else Headshot(headshots[m.playerId], m.playerName, 40.dp)
                        Column(Modifier.weight(1f)) {
                            FcText(m.title.substringAfter(": ").ifBlank { m.title }, 14.sp, FontWeight.SemiBold, maxLines = 2)
                            Muted("${m.playerName.ifBlank { state.clubName }} · ${Dates.full(m.date)}", 12.sp, maxLines = 1)
                        }
                        val (label, tone) = kindTag(m.kind)
                        Tag(label, tone)
                    }
                }
            }
        }
        if (!view.isArchived) item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                SectionHeader("Which milestones", icon = Icons.Default.NotificationsActive)
                FcSwitchRow("Appearances", prefs.appearances, { viewModel.setMilestonePrefs(prefs.copy(appearances = it)) }, sub = "50, 100, 150, 200 and so on")
                FcSwitchRow("Goals and assists", prefs.goals, { viewModel.setMilestonePrefs(prefs.copy(goals = it)) }, sub = "25, 50, 100 and so on")
                FcSwitchRow("Rating jumps", prefs.growth, { viewModel.setMilestonePrefs(prefs.copy(growth = it)) }, sub = "A player gains 3 OVR or more, or reaches his potential")
                FcSwitchRow("Club records", prefs.records, { viewModel.setMilestonePrefs(prefs.copy(records = it)) }, sub = "Wins in a row, unbeaten runs, biggest win, record signing")
                Muted("Worked out from your matches and from the game’s numbers. They also appear in the Live feed on Home. A switched-off kind is still listed here, just without an alert.", 11.sp)
            }
        }
    }
}

@Composable
private fun AlertPreview(title: String, body: String, whenText: String) {
    val c = FcTheme.colors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface).padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(c.accentBg), contentAlignment = Alignment.Center) { Icon(Icons.Default.EmojiEvents, null, tint = c.accent, modifier = Modifier.size(17.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row { Muted("FC Tracker · Milestone", 12.sp, Modifier.weight(1f)); Muted(whenText, 11.sp) }
            FcText(title, 14.sp, FontWeight.SemiBold)
            Muted(body, 12.sp)
        }
    }
}

// =====================================================================================================================
// Widgets
// =====================================================================================================================

/** The home-screen widgets: a preview with the career's own data, and how to add them. */
@Composable
fun WidgetsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val data = remember(state) { WidgetData.of(state) }
    val density = LocalDensity.current.density
    val formBig = remember(data.form, density) { WidgetUpdater.formStrip(data.form, (20 * density).toInt(), (4 * density).toInt()).asImageBitmap() }
    val formSmall = remember(data.form, density) { WidgetUpdater.formStrip(data.form, (16 * density).toInt(), (3 * density).toInt()).asImageBitmap() }
    val c = FcTheme.colors
    val widgetBg = Color(0xEB131A17)
    val canPin = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && runCatching { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }.getOrDefault(false)
    FcPage(header = { FcTopBar("Widgets", sub = "On your phone’s home screen", nav = NavIcon.BACK, onNav = onBack) }) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1D3B5C), Color(0xFF163A2C), Color(0xFF0D1813))))
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(widgetBg).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ClubCrest(state.clubName, state.clubCrestFile, state.clubCrestUrl, state.isExistingTeam, 26.dp)
                        FcText(data.club, 14.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                        Mono(data.positionLine, 12.sp, c.muted, FontWeight.Normal)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CompLogo(state.leagueName, 30.dp)
                        Column(Modifier.weight(1f)) {
                            FcText(data.nextTitle, 15.sp, FontWeight.SemiBold, maxLines = 1)
                            Muted(data.nextLine, 12.sp, maxLines = 1)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(formBig, contentDescription = "Form ${data.form.joinToString(" ")}")
                        Muted(data.last, 12.sp, Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, align = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.width(150.dp).clip(RoundedCornerShape(26.dp)).background(widgetBg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ClubCrest(state.clubName, state.clubCrestFile, state.clubCrestUrl, state.isExistingTeam, 18.dp)
                            Muted(data.league, 11.sp, maxLines = 1)
                        }
                        FcText(data.position, 40.sp, FontWeight.SemiBold, lineHeight = 40.sp)
                        Muted("${data.points} pts · ${data.played} played", 11.sp)
                        Image(formSmall, contentDescription = null)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(2) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                repeat(2) { j ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.10f + 0.03f * j)))
                                        Box(Modifier.width(34.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.18f)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (canPin) item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FcButton("Add large widget", { runCatching { AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, LargeWidget::class.java), null, null) } }, Modifier.weight(1f), icon = Icons.Default.Add)
                FcButton("Add small widget", { runCatching { AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, SmallWidget::class.java), null, null) } }, Modifier.weight(1f), kind = ButtonKind.SECONDARY, icon = Icons.Default.Add)
            }
        }
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                SectionHeader("Widgets", icon = Icons.Default.PhoneAndroid)
                Bullet("Large: next match, league position, form and the last result.")
                Bullet("Small: league position and form.")
                Bullet("They refresh when you log a match or one arrives from your PC, and always show the current season.")
                Muted("To add one: touch and hold an empty spot on your home screen, then Widgets, then FC Tracker.", 11.sp)
            }
        }
    }
}

// =====================================================================================================================
// Club colours as the accent
// =====================================================================================================================

/** How the app turns the club colours into its accent, with examples and the person's own club. */
@Composable
fun ThemingScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val c = FcTheme.colors
    val examples = listOf(
        Triple("Royal blue & white", "#1A3FAE", "#FFFFFF"), Triple("Gold & black", "#F2B705", "#111111"), Triple("Claret & sky blue", "#6D1A36", "#8FC3EA"),
        Triple("Black & white", "#111111", "#FFFFFF"), Triple("Red & white", "#D0101B", "#FFFFFF")
    )
    FcPage(header = { FcTopBar("Club colours", sub = "How the app uses them", nav = NavIcon.BACK, onNav = onBack) }) {
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                SectionHeader("How club colours become the accent", icon = Icons.Default.Settings)
                listOf(
                    "Start from the primary colour; if it is black, white or grey, try the secondary.",
                    "Too dark (or too light) to read on the dark background? Lighten it until it reads, keeping its hue.",
                    "If both colours are black, white or grey, keep the default mint.",
                    "The other club colour is the second accent: section icons, charts and the menu's selected place.",
                    "Win, draw and loss (and money in and out) keep their own green, amber and red. A club colour that looks like one of them gives way to the other club colour, or is turned a little round the colour wheel.",
                    "You can switch it off in Settings to go back to mint."
                ).forEachIndexed { i, t ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Mono("${i + 1}", 12.sp, c.accent, modifier = Modifier.width(16.dp))
                        FcText(t, 13.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
        item { ThemeExample("Your club · ${state.clubName}", state.primaryColorHex, state.secondaryColorHex, if (state.useClubColours) null else "Club colours are switched off: the app uses mint.") }
        item { FcSwitchRow("Use club colours in the app", state.useClubColours, { viewModel.setUseClubColours(it) }, sub = "Buttons, links and highlights take the club colour.") }
        examples.forEach { (name, p, s) -> item(key = name) { ThemeExample(name, p, s, null) } }
    }
}

@Composable
private fun ThemeExample(name: String, primary: String, secondary: String, override: String?) {
    val c = FcTheme.colors
    val choice = Accent.choose(primary, secondary)
    val acc = parseHex(choice.hex) ?: c.accent
    val acc2 = parseHex(Accent.chooseSecondary(primary, secondary, choice.hex)) ?: c.accent2
    val on = parseHex(Accent.onColour(choice.hex)) ?: Color.Black
    val why = when {
        override != null -> override
        choice.fromHex == null -> choice.why.replaceFirstChar { it.uppercase() }
        else -> "From the ${if (choice.fromHex.equals(primary, true)) "primary" else "secondary"} colour, ${choice.why}"
    }
    FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FcText(name, 14.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            listOf(primary, secondary).forEach { hex -> Box(Modifier.padding(start = 6.dp).size(22.dp).clip(CircleShape).background(parseHex(hex) ?: Color.Gray).border(1.dp, c.border, CircleShape)) }
        }
        Muted(why, 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(acc).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Add, null, tint = on, modifier = Modifier.size(16.dp))
                FcText("Log match", 13.sp, FontWeight.Bold, on)
            }
            FcText("View all", 13.sp, FontWeight.SemiBold, acc)
            Box(Modifier.size(32.dp).clip(CircleShape).background(acc2.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.EmojiEvents, null, tint = acc2, modifier = Modifier.size(17.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(Icons.Default.SportsSoccer, null, tint = acc, modifier = Modifier.size(18.dp))
                FcText("Story", 11.sp, color = acc)
                Box(Modifier.width(16.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(acc))
            }
        }
    }
}
