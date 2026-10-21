package com.fctracker.career.ui.screens
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.MainViewModel
import com.fctracker.career.AppState
import com.fctracker.career.Player
import com.fctracker.career.ui.theme.WinColor

import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sync

/** Youth Academy: the prospects (from the game on PC, added by hand on console) and the scouting network. */
@Composable
fun AcademyScreen(viewModel: MainViewModel, onMenuClick: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val view = remember(state) { com.fctracker.career.logic.SeasonView.of(state) }
    val console = state.platform == com.fctracker.career.Platform.CONSOLE
    var tab by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Player?>(null) }
    var releasing by remember { mutableStateOf<Player?>(null) }
    val prospects = (view.academy + view.players.filter { it.isAcademy || it.status.equals("Academy", true) }).distinctBy { it.id }.sortedByDescending { it.potential }
    val c = com.fctracker.career.ui.theme.FcTheme.colors
    com.fctracker.career.ui.components.FcPage(
        header = {
            if (view.isArchived) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { com.fctracker.career.ui.components.ArchivedBanner("${view.label} · ${view.leagueName}", { viewModel.setViewingSeasonYear(null) }) }
            com.fctracker.career.ui.components.FcTopBar("Youth Academy", sub = "${prospects.size} prospects${if (view.isArchived) " · ${view.label}" else ""}", onNav = onMenuClick)
        },
        fab = if (!view.isArchived && tab == 0) ({ com.fctracker.career.ui.components.Fab("Add prospect", { adding = true }) }) else null
    ) {
        item { com.fctracker.career.ui.components.SoftTabs(listOf("Prospects", "Scouting network"), tab, { tab = it }) }
        if (tab == 1) {
            item { ScoutingNetworkTab() }
            return@FcPage
        }
        if (!view.isArchived) item {
            if (console) com.fctracker.career.ui.components.Notice(com.fctracker.career.ui.components.Tone.INFO, "You add youth players yourself", "On console, add each prospect your scouts bring in: name, position, age, OVR and potential.")
            else com.fctracker.career.ui.components.Notice(com.fctracker.career.ui.components.Tone.GOOD, "Added from your game", "New academy players arrive by themselves: the capture script reads your youth academy in Live Editor and sends name, position, age, OVR and potential.", icon = Icons.Default.Sync)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.fctracker.career.ui.components.SectionHeader("Youth prospects", Modifier.weight(1f), sub = if (view.isArchived) "End of ${view.label}" else null, icon = Icons.Default.School)
                if (!view.isArchived) com.fctracker.career.ui.components.FcButton("Generate", { adding = true }, kind = com.fctracker.career.ui.components.ButtonKind.SECONDARY, icon = Icons.Default.Refresh, small = true)
            }
        }
        if (prospects.isEmpty()) item { com.fctracker.career.ui.components.EmptyCard("No players in Youth Academy.") }
        prospects.chunked(2).forEach { pair ->
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { p ->
                        AcademyPlayerCard(p, Modifier.weight(1f), archived = view.isArchived,
                            onClick = { if (!view.isArchived) editing = p },
                            onPromote = { viewModel.promoteAcademyPlayer(p) },
                            onRelease = { releasing = p })
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (view.isArchived) item { com.fctracker.career.ui.components.Muted("No Promote, Release or add buttons in an archived season.", 11.sp) }
    }
    if (adding) EditYouthPlayerDialog(startYear = state.startYear, onDismiss = { adding = false }, onUpdate = { viewModel.addPlayer(it) })
    editing?.let { p ->
        EditYouthPlayerDialog(startYear = state.startYear, player = p, onDismiss = { editing = null }, onUpdate = { viewModel.updatePlayer(it); editing = null }, onDelete = { viewModel.deletePlayer(p.id); editing = null })
    }
    releasing?.let { p ->
        com.fctracker.career.ui.components.ConfirmDialog("Release ${p.displayName}?", "He leaves the academy.", "Release", { viewModel.removeAcademyPlayer(p.id) }, { releasing = null })
    }
}

/** A prospect: headshot, name, position and age, OVR and potential, Promote and Release. */
@Composable
fun AcademyPlayerCard(player: Player, modifier: Modifier = Modifier, archived: Boolean = false, onClick: () -> Unit = {}, onPromote: () -> Unit = {}, onRelease: () -> Unit = {}) {
    val c = com.fctracker.career.ui.theme.FcTheme.colors
    com.fctracker.career.ui.components.FcCard(modifier, padding = 12.dp, gap = 10.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.fctracker.career.ui.components.Headshot(player.headshotUrl, player.displayName, 40.dp, youth = true)
            Column(Modifier.weight(1f)) {
                com.fctracker.career.ui.components.FcText(player.displayName, 13.sp, FontWeight.Bold, maxLines = 1)
                com.fctracker.career.ui.components.Muted("${player.primaryPosition} · Age ${player.age}", 11.sp, maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { com.fctracker.career.ui.components.Muted("OVR", 10.sp); com.fctracker.career.ui.components.Figure("${player.overall}", 24.sp) }
            Column(horizontalAlignment = Alignment.End) { com.fctracker.career.ui.components.Muted("POT", 10.sp); com.fctracker.career.ui.components.Figure(player.potentialRange ?: "${player.potential}", 24.sp, c.good) }
        }
        if (!archived) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.fctracker.career.ui.components.FcButton("Promote", onPromote, Modifier.weight(1f), small = true)
            com.fctracker.career.ui.components.FcButton("Release", onRelease, Modifier.weight(1f), kind = com.fctracker.career.ui.components.ButtonKind.SECONDARY, small = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoutingNetworkTab() {
    var selectedType by remember { mutableStateOf("Technically Gifted") }
    var typeExpanded by remember { mutableStateOf(false) }
    val playerTypes = listOf("Technically Gifted", "Winger", "Physically Strong", "Playmaker", "Goalkeeper", "Attacker", "Defensive Minded", "Any")
    
    val selectedPositions = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var positionsExpanded by remember { mutableStateOf(false) }
    val allPositions = listOf("GK", "CB", "LB", "RB", "CDM", "CM", "CAM", "LM", "RM", "LW", "RW", "ST", "CF")
    
    val suggestions = mapOf(
        "Technically Gifted" to listOf("Spain", "Argentina", "Brazil", "Italy", "Portugal"),
        "Winger" to listOf("Brazil", "Spain", "Netherlands", "England", "France"),
        "Physically Strong" to listOf("Ivory Coast", "Ghana", "USA", "Norway", "Germany"),
        "Playmaker" to listOf("Spain", "Italy", "France", "Argentina", "Croatia"),
        "Goalkeeper" to listOf("Germany", "Italy", "Spain", "Brazil", "Slovenia"),
        "Attacker" to listOf("Brazil", "France", "Argentina", "Netherlands", "Portugal"),
        "Defensive Minded" to listOf("Italy", "Germany", "England", "Uruguay", "Senegal"),
        "Any" to listOf("Brazil", "France", "Spain", "Germany", "Argentina")
    )
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Scout Instructions", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Select the type of player you are looking for, and we will recommend the best countries to send your scouts to.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        ExposedDropdownMenuBox(
            expanded = typeExpanded,
            onExpandedChange = { typeExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedType,
                onValueChange = {},
                readOnly = true,
                label = { Text("Player Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = typeExpanded,
                onDismissRequest = { typeExpanded = false }
            ) {
                playerTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type) },
                        onClick = {
                            selectedType = type
                            typeExpanded = false
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        ExposedDropdownMenuBox(
            expanded = positionsExpanded,
            onExpandedChange = { positionsExpanded = it }
        ) {
            OutlinedTextField(
                value = if (selectedPositions.isEmpty()) "Any Position" else selectedPositions.joinToString(", "),
                onValueChange = {},
                readOnly = true,
                label = { Text("Positions (Max 3)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = positionsExpanded) },
                modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = positionsExpanded,
                onDismissRequest = { positionsExpanded = false }
            ) {
                allPositions.forEach { pos ->
                    val isSelected = selectedPositions.contains(pos)
                    DropdownMenuItem(
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Checkbox(checked = isSelected, onCheckedChange = null)
                                Text(pos)
                            }
                        },
                        onClick = {
                            if (isSelected) {
                                selectedPositions.remove(pos)
                            } else if (selectedPositions.size < 3) {
                                selectedPositions.add(pos)
                            }
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("Recommended Scouting Regions", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))
        
        val recommended = suggestions[selectedType] ?: emptyList()
        recommended.forEach { country ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(country, fontWeight = FontWeight.Bold, color = Color.White)
                Icon(Icons.Default.Add, contentDescription = "Deploy Scout", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

