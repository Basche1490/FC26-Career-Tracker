package com.fctracker.career.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.AppState
import com.fctracker.career.MainViewModel
import com.fctracker.career.Platform
import com.fctracker.career.telemetry.Level
import com.fctracker.career.telemetry.NotificationHelper
import com.fctracker.career.ui.components.ButtonKind
import com.fctracker.career.ui.components.ConfirmDialog
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.FcCard
import com.fctracker.career.ui.components.FcField
import com.fctracker.career.ui.components.FcPage
import com.fctracker.career.ui.components.FcSelectField
import com.fctracker.career.ui.components.FcSwitchRow
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.FcTopBar
import com.fctracker.career.ui.components.FieldPair
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.Notice
import com.fctracker.career.ui.components.SectionHeader
import com.fctracker.career.ui.components.Segmented
import com.fctracker.career.ui.components.SoftTabs
import com.fctracker.career.ui.components.Tone
import com.fctracker.career.ui.theme.FcTheme

@Composable
fun SettingsScreen(viewModel: MainViewModel, onMenuClick: () -> Unit, nav: (String) -> Unit) {
    val state by viewModel.state.collectAsState()
    val console = state.platform == Platform.CONSOLE
    var tab by remember { mutableStateOf(0) }
    FcPage(header = {
        FcTopBar("Settings", sub = if (tab == 0) "Club & AI" else if (console) "Alerts & data" else "Sync, alerts & data", onNav = onMenuClick)
        SoftTabs(listOf("Club & AI", if (console) "Alerts & data" else "Sync, alerts & data"), tab, { tab = it }, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
    }) {
        if (tab == 0) {
            item { PlatformCard(viewModel, state) }
            item { ProfileAndAiCards(viewModel, state) }
            item { MoreCard(nav) }
        } else {
            if (console) {
                item { Notice(Tone.INFO, "You play on console", "The PC parts of the app are hidden, and everything the PC would fill in by itself has an Add or Edit button instead. Switch to PC under Club & AI if that changes.", icon = Icons.Default.Info) }
                item { HiddenOnConsoleCard() }
                item { SectionHeader("You fill these in yourself", sub = "Every page that fills itself on PC can be filled in by hand", icon = Icons.Default.Check) }
                item { FillYourselfRows(nav) }
                item { Muted("The same buttons are there on PC too, for example to correct an injury that lasts longer than the game said.", 11.sp) }
            } else {
                item { LiveSyncCard(viewModel, nav) }
            }
            item { NotificationsCard(viewModel, console, nav) }
            item { SaveSlotsCard(viewModel) }
            item { DataCard(viewModel) }
        }
    }
}

@Composable
private fun PlatformCard(viewModel: MainViewModel, state: AppState) {
    FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
        SectionHeader("Where you play", icon = Icons.Default.SportsSoccer)
        Segmented(listOf("Console", "PC"), if (state.platform == Platform.CONSOLE) 0 else 1, { viewModel.setPlatform(if (it == 0) Platform.CONSOLE else Platform.PC) })
        Muted("Console hides the PC helper, live sync, PC Connection and the Live pill. Nothing you logged is lost when you switch.", 11.sp)
    }
}

@Composable
private fun ProfileAndAiCards(viewModel: MainViewModel, state: AppState) {
    val context = LocalContext.current
    val c = FcTheme.colors
    val storedKey by viewModel.aiApiKey.collectAsState()
    var real by remember { mutableStateOf(state.isExistingTeam) }
    var search by remember { mutableStateOf("") }
    var clubName by remember { mutableStateOf(state.clubName) }
    var manager by remember { mutableStateOf(state.managerName) }
    var country by remember { mutableStateOf(state.country) }
    var league by remember { mutableStateOf(state.leagueName) }
    var currency by remember { mutableStateOf(ClubSetup.currencyLabel(state.currencySymbol)) }
    var crestUrl by remember { mutableStateOf(state.clubCrestUrl) }
    var ownCrestForReal by remember { mutableStateOf(state.clubCrestFile.isNotBlank() || state.clubCrestUrl.isNotBlank()) }
    var primary by remember { mutableStateOf(state.primaryColorHex) }
    var secondary by remember { mutableStateOf(state.secondaryColorHex) }
    var useClub by remember { mutableStateOf(state.useClubColours) }
    var agent by remember { mutableStateOf(ClubSetup.agentLabel(state.aiAgent)) }
    var apiUrl by remember { mutableStateOf(state.aiApiUrl.ifBlank { ClubSetup.defaultApiUrl(state.aiAgent) }) }
    var key by remember(storedKey) { mutableStateOf(storedKey) }
    var model by remember { mutableStateOf(state.aiModel) }
    val clubs = remember { viewModel.getUniqueClubs() }

    fun save() {
        viewModel.updateClubProfile(clubName, manager, league, country, ClubSetup.currencySymbol(currency), primary, secondary, if (real && !ownCrestForReal) "" else crestUrl, real, useClub)
        viewModel.updateAiSettings(agent, apiUrl, if (key != storedKey) key.trim() else null, model)
        Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
            SectionHeader("Club profile", icon = Icons.Default.Shield)
            FcSwitchRow("Manage an existing real club", real, { real = it }, sub = "Off: your own club. On: pick a real club and its crest, colours and history come with it.")
            if (real) {
                FcField("Search real club", search, { search = it }, placeholder = "Leicester City", leadingIcon = Icons.Default.Search)
                val hits = remember(search) { if (search.length < 2) emptyList() else clubs.filter { it.contains(search, true) }.take(6) }
                hits.forEach { h ->
                    FcText(h, 14.sp, color = c.accent, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                        clubName = h; search = ""
                        ClubSetup.coloursOf(h)?.let { (p, s) -> primary = p; secondary = s }
                    }.padding(8.dp))
                }
            }
            FcField("Club name", clubName, { clubName = it })
            FcField("Manager name", manager, { manager = it })
            FieldPair(
                { m -> FcSelectField("Country", country, ClubSetup.COUNTRIES.let { if (country in it) it else it + country }, { country = it; league = ClubSetup.leaguesFor(it).firstOrNull() ?: league }, m) },
                { m -> val options = ClubSetup.leaguesFor(country, league); if (options.isEmpty()) FcField("League", league, { league = it }, m) else FcSelectField("League", league, options, { league = it }, m) }
            )
            FcSelectField("Currency", currency, ClubSetup.CURRENCIES, { currency = it }, hint = "EUR, GBP or USD.")
            if (real && !ownCrestForReal) RealCrestRow(viewModel, state, clubName, onUseOwn = { ownCrestForReal = true })
            else CrestBlock(viewModel, state, clubName, crestUrl, { crestUrl = it })
            ColourRows(primary, secondary, { p, s -> primary = p; secondary = s }, useClub, { useClub = it }, if (real) "Taken from the club you picked. Tap a colour, or More colours, to change them." else null)
            FcButton("Save profile settings", { save() }, icon = Icons.Default.Save, full = true)
        }
        FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
            SectionHeader("AI agent settings", icon = Icons.Default.Star)
            FcSelectField("AI agent", agent, ClubSetup.AI_AGENTS, { agent = it; apiUrl = ClubSetup.defaultApiUrl(it).ifBlank { apiUrl } }, hint = "Google Gemini, Claude, ChatGPT, Grok or Other.")
            FcField("API URL", apiUrl, { apiUrl = it }, placeholder = "https://…")
            FcField(if (agent == "Other") "API key" else "$agent API key", key, { key = it }, password = true, leadingIcon = Icons.Default.Lock)
            Muted("Your key is kept in encrypted storage on this phone and is not included in backups. Tap “Save profile settings” above, or Save below, to apply.", 11.sp)
            FcField("Model", model, { model = it }, hint = "Leave blank for the default (${com.fctracker.career.ai.AiClient.defaultModel(agent).ifBlank { "enter one" }}).")
            FcButton("Save AI settings", { save() }, kind = ButtonKind.SECONDARY, icon = Icons.Default.Save, full = true)
        }
    }
}

@Composable
private fun MoreCard(nav: (String) -> Unit) {
    FcCard(Modifier.fillMaxWidth(), padding = 6.dp, gap = 0.dp) {
        LinkRow(Icons.Default.NotificationsActive, "Milestone alerts", "100 games, 50 goals, debuts and club records") { nav("milestones") }
        LinkRow(Icons.Default.Widgets, "Home-screen widgets", "Next match, position and form on your home screen") { nav("widgets") }
        LinkRow(Icons.Default.Palette, "Club colours", "How the app turns them into its accent") { nav("theming") }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val c = FcTheme.colors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(c.accentBg), contentAlignment = Alignment.Center) { Icon(icon, null, tint = c.accent, modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f)) {
            FcText(title, 14.sp, FontWeight.SemiBold)
            Muted(sub, 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun HiddenOnConsoleCard() {
    val c = FcTheme.colors
    FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
        SectionHeader("Hidden on console", icon = Icons.Default.Lock)
        listOf(
            Icons.Default.Wifi to "PC Connection in the menu, and the Live pill on every screen",
            Icons.Default.Sync to "Live sync with your PC, background sync and Re-send last match",
            Icons.Default.NotificationsActive to "Alerts that need the PC (a match, transfer or injury arriving by itself)",
            Icons.Default.Check to "The live sync setup guide"
        ).forEach { (icon, text) ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = c.muted, modifier = Modifier.size(18.dp))
                FcText(text, 13.sp)
            }
        }
    }
}

@Composable
private fun FillYourselfRows(nav: (String) -> Unit) {
    RowsCard {
        listOf(
            Triple(Icons.Default.CalendarMonth, "Fixtures and the next match", "Matches · Upcoming · Add fixture") to "matches",
            Triple(Icons.AutoMirrored.Filled.List, "League table", "Season Summary · Table · tap a club to edit") to "season_summary",
            Triple(Icons.Default.ShowChart, "Stats, Golden Boot, cup runs", "Season Summary · Stats and Overview · Add / Edit") to "season_summary",
            Triple(Icons.Default.LocalHospital, "Injuries and suspensions", "Squad · Injuries · Add injury") to "squad",
            Triple(Icons.Default.Payments, "Wages, squad roles, shirt numbers, contracts", "Edit player") to "squad",
            Triple(Icons.Default.School, "Youth players", "Youth Academy · Add prospect") to "academy",
            Triple(Icons.Default.EmojiEvents, "Awards", "Season Summary · Awards · Assign awards") to "season_summary",
            Triple(Icons.Default.Language, "Around the World winners", "A reminder in June · Add winner") to "world",
            Triple(Icons.Default.Star, "European ranking", "Season Summary · Europe · Add your points") to "season_summary",
            Triple(Icons.Default.Shield, "Club honours (real club)", "Club history · Edit") to "dashboard"
        ).forEachIndexed { i, (row, route) ->
            if (i > 0) RowDivider()
            LinkRow(row.first, row.second, row.third) { nav(route) }
        }
    }
}

@Composable
private fun LiveSyncCard(viewModel: MainViewModel, nav: (String) -> Unit) {
    val health by viewModel.health.collectAsState()
    val background by viewModel.backgroundSync.collectAsState()
    FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
        SectionHeader("Live sync with your PC", icon = Icons.Default.Sync)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusPill(health.level, health.pill, onClick = { nav("connection") })
            FcText(health.headline, 14.sp, FontWeight.SemiBold, maxLines = 2, modifier = Modifier.weight(1f))
        }
        if (health.level == Level.LIVE) Muted(health.detail, 12.sp)
        FieldPair({ m -> FcButton("PC connection", { nav("connection") }, m, kind = ButtonKind.SECONDARY) }, { m -> FcButton("Setup guide", { nav("setup") }, m, kind = ButtonKind.SECONDARY) })
        FcSwitchRow("Keep syncing in the background", background, { viewModel.setBackgroundSync(it) },
            sub = "Shows a small “Live sync” notification so match and transfer alerts still arrive while the app is in the background. Swiping the app away stops it. Battery optimisation can also pause it.")
    }
}

@Composable
private fun NotificationsCard(viewModel: MainViewModel, console: Boolean, nav: (String) -> Unit) {
    val prefs by viewModel.notificationPrefs.collectAsState()
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(true) }
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { while (true) { allowed = NotificationHelper.alertsAllowed(context); kotlinx.coroutines.delay(1500) } }
    FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
        SectionHeader("Notifications", icon = Icons.Default.NotificationsActive)
        if (allowed) Notice(Tone.GOOD, "Android allows notifications", "Alerts can appear on this phone.")
        else Notice(Tone.BAD, "Android is hiding this app's alerts", "Open Android settings below and allow notifications for this app.")
        if (!console) {
            FcSwitchRow("Matches", prefs.matches, { viewModel.setNotificationPrefs(prefs.copy(matches = it)) }, sub = "The final score of each match, as soon as it is played.")
            FcSwitchRow("Transfers", prefs.transfers, { viewModel.setNotificationPrefs(prefs.copy(transfers = it)) }, sub = "Signings, sales and loans, when the deal is done.")
            FcSwitchRow("Injuries", prefs.injuries, { viewModel.setNotificationPrefs(prefs.copy(injuries = it)) }, sub = "A player of yours gets injured, and for how long.")
        }
        LinkRow(Icons.Default.Star, "Milestone alerts", "Choose which milestones alert you") { nav("milestones") }
        Muted("Tapping an alert opens the matching screen. These switches only affect this phone; the data is still saved when an alert is off.", 11.sp)
        FieldPair(
            { m -> FcButton("Send test alert", { msg = if (NotificationHelper.showTest(context)) "Test alert sent. If you cannot see it, check the notification settings." else "Android blocked the test alert. Allow notifications in Android settings." }, m, kind = ButtonKind.SECONDARY, small = true) },
            { m -> FcButton("Android settings", { runCatching { context.startActivity(NotificationHelper.settingsIntent(context)) }.onFailure { msg = "Open Android Settings › Apps › FC Tracker › Notifications." } }, m, kind = ButtonKind.SECONDARY, small = true) }
        )
        msg?.let { Muted(it, 12.sp) }
    }
}

@Composable
private fun SaveSlotsCard(viewModel: MainViewModel) {
    var prompt by remember { mutableStateOf<Int?>(null) }
    val active = viewModel.activeSaveSlot
    val names = viewModel.saveSlotNames
    var rename by remember(active, names[active]) { mutableStateOf(names[active] ?: "") }
    FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
        SectionHeader("Save slot management", icon = Icons.Default.Save)
        Segmented(listOf(1, 2).map { (names[it] ?: "Slot $it") + if (active == it) " (active)" else "" }, active - 1, { i ->
            val slot = i + 1
            if (slot != active) { if (viewModel.saveSlots.containsKey(slot)) viewModel.switchSaveSlot(slot) else prompt = slot }
        })
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FcField("Active save name", rename, { rename = it }, Modifier.weight(1f))
            FcButton("Rename", { viewModel.renameSaveSlot(active, rename.trim()) }, kind = ButtonKind.SECONDARY, small = true, enabled = rename.isNotBlank())
        }
    }
    prompt?.let { slot -> ConfirmDialog("Create a new save?", "Slot $slot is empty. Start a new career there? Your current save stays in its own slot.", "Create", { viewModel.switchSaveSlot(slot, createNew = true) }, { prompt = null }, danger = false) }
}

@Composable
private fun DataCard(viewModel: MainViewModel) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) runCatching {
            val enc = com.fctracker.career.utils.EncryptionUtils.encrypt(viewModel.exportStateToJson())
            context.contentResolver.openOutputStream(uri)?.use { it.write(enc.toByteArray()) } ?: throw java.io.IOException()
        }.onSuccess { Toast.makeText(context, "Backup exported", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, "Could not export the backup", Toast.LENGTH_SHORT).show() }
    }
    val import = rememberCareerImport(viewModel)
    FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
        SectionHeader("Data management", icon = Icons.Default.Download)
        FcButton("Export backup JSON", { export.launch("fc_tracker_backup.aes") }, kind = ButtonKind.SECONDARY, icon = Icons.Default.Download, full = true)
        Muted("Backups are encrypted (.aes). Your AI key and the PC pairing code are never part of a backup.", 11.sp)
        FcButton("Import JSON backup", import, kind = ButtonKind.SECONDARY, icon = Icons.Default.Upload, full = true)
        FcButton("Delete current save", { confirmDelete = true }, kind = ButtonKind.DANGER, icon = Icons.Default.Delete, full = true)
    }
    if (confirmDelete) ConfirmDialog("Delete this save?", "Everything in this save slot (matches, squad, seasons) is removed from this phone. Export a backup first if you might want it back.", "Delete", { viewModel.deleteCurrentSave() }, { confirmDelete = false })
}
