package com.fctracker.career.ui.screens

import androidx.compose.runtime.remember

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.setValue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.fctracker.career.Platform
import com.fctracker.career.logic.SeasonView
import com.fctracker.career.ui.components.ButtonKind
import com.fctracker.career.ui.components.ClubCrest
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.Tag
import com.fctracker.career.ui.components.Tone
import com.fctracker.career.ui.theme.FcTheme

data class DrawerItem(val route: String, val title: String, val icon: ImageVector, val everySeason: Boolean = false)

private val DRAWER_ITEMS = listOf(
    DrawerItem("dashboard", "Dashboard", Icons.Default.Home),
    DrawerItem("squad", "Squad & Roster", Icons.Default.Groups),
    DrawerItem("academy", "Youth Academy", Icons.Default.School),
    DrawerItem("transfers", "Transfers", Icons.Default.SwapHoriz),
    DrawerItem("matches", "Matches", Icons.AutoMirrored.Filled.List),
    DrawerItem("season_summary", "Season Summary", Icons.Default.CalendarMonth),
    DrawerItem("world", "Around the World", Icons.Default.Language),
    DrawerItem("career_summary", "Career Summary", Icons.Default.EmojiEvents, everySeason = true),
    DrawerItem("assistant", "AI Assistant", Icons.Default.SmartToy, everySeason = true),
    DrawerItem("connection", "PC Connection", Icons.Default.Wifi, everySeason = true),
    DrawerItem("settings", "Settings", Icons.Default.Settings, everySeason = true)
)

/** The side menu: the club, every page, and (in an archived season) the way back to the current one. */
@Composable
fun AppDrawer(viewModel: MainViewModel, state: AppState, currentRoute: String, navigateTo: (String) -> Unit) {
    val c = FcTheme.colors
    val view = SeasonView.of(state)
    val health by viewModel.health.collectAsState()
    val console = state.platform == Platform.CONSOLE
    val slot = viewModel.activeSaveSlot
    Column(
        Modifier.width(304.dp).fillMaxHeight().clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)).background(c.surface)
            .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        var showHistory by remember { mutableStateOf(false) }
        // the crest opens the club's history, the same as on Home
        Row(
            Modifier.padding(bottom = 16.dp).clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button) { showHistory = true }.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ClubCrest(state.clubName, state.clubCrestFile, state.clubCrestUrl, state.isExistingTeam, 44.dp, onClick = { showHistory = true })
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FcText(state.clubName, 19.sp, FontWeight.SemiBold, maxLines = 2)
                Muted("FC Tracker · ${view.label}", 12.sp)
            }
        }
        if (showHistory) {
            ClubHistoryDialog(viewModel, state, onDismiss = { showHistory = false }, onWrite = { showHistory = false; navigateTo("club_history_edit") })
        }
        if (view.isArchived) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(c.infoBg).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = c.info, modifier = Modifier.size(16.dp))
                FcText("Viewing ${view.label} · read-only", 12.sp, FontWeight.Bold, c.info)
            }
        }
        DRAWER_ITEMS.filter { !(console && it.route == "connection") }.forEach { item ->
            val on = currentRoute == item.route || (item.route == "squad" && currentRoute.startsWith("player"))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(50)).background(if (on) c.accent2Bg else Color.Transparent)
                    .clickable(role = Role.Button) { navigateTo(item.route) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(item.icon, contentDescription = null, tint = if (on) c.accent2 else c.text, modifier = Modifier.size(22.dp))
                FcText(item.title, 15.sp, if (on) FontWeight.SemiBold else FontWeight.Medium, if (on) c.accent2 else c.text, Modifier.weight(1f), maxLines = 1)
                when {
                    view.isArchived && item.everySeason -> Muted("every season", 11.sp)
                    item.route == "connection" -> StatusPill(health.level, health.pill, onClick = { navigateTo("connection") })
                    item.route == "world" && state.worldWinners.isEmpty() -> Tag("New", Tone.ACCENT)
                }
            }
        }
        Spacer(Modifier.heightIn(min = 12.dp))
        if (view.isArchived) {
            Column(Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FcButton("Back to current season", { viewModel.setViewingSeasonYear(null); navigateTo(currentRoute.takeIf { !it.startsWith("player") } ?: "dashboard") }, kind = ButtonKind.PRIMARY, icon = Icons.Default.History, full = true)
                Muted("Pages marked “every season” are the same whichever season you view.", 11.sp)
            }
        } else {
            Box(Modifier.padding(top = 14.dp, start = 6.dp)) {
                Muted("Slot $slot · ${state.clubName} · ${SeasonView.seasonLabel(state.startYear)}", 11.sp)
            }
        }
    }
}
