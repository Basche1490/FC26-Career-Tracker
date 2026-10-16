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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademyScreen(viewModel: MainViewModel, onMenuClick: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val viewingSeason = state.historicalSeasons.find { it.year == state.viewingSeasonYear }
    val displayPlayers = viewingSeason?.players?.takeIf { it.isNotEmpty() } ?: state.players
    val displayTransfers = viewingSeason?.transfers?.takeIf { it.isNotEmpty() } ?: state.transfers
    val isArchived = state.viewingSeasonYear != null
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Prospects", "Scouting Network")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Youth Academy", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).imePadding().fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }
            
            when (selectedTabIndex) {
                0 -> ProspectsTab(state, displayPlayers, viewModel)
                1 -> ScoutingNetworkTab()
            }
        }
    }
}

@Composable
fun ProspectsTab(state: com.fctracker.career.AppState, displayPlayers: List<com.fctracker.career.Player>, viewModel: MainViewModel) {
    var showYouthForm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var playerToEdit by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.fctracker.career.Player?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Youth Prospects", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Button(onClick = { showYouthForm = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                    Text("Generate Prospect", fontSize = 12.sp)
                }
            }
            val academyPlayers = displayPlayers.filter { it.isAcademy }
            if (academyPlayers.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No players in Youth Academy.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(academyPlayers, key = { it.id }) { player ->
                        AcademyPlayerCard(
                            player,
                            onClick = { playerToEdit = player },
                            onPromote = { 
                                viewModel.updatePlayer(player.copy(isAcademy = false, isPromoted = true))
                                playerToEdit = null
                            },
                            onRelease = { 
                                viewModel.deletePlayer(player.id)
                                playerToEdit = null
                            }
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showYouthForm = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, "Add Youth Player", tint = MaterialTheme.colorScheme.onPrimary)
        }
        if (showYouthForm) {
            EditYouthPlayerDialog(startYear = state.startYear, onDismiss = { showYouthForm = false }, onUpdate = { viewModel.addPlayer(it) })
        }
        if (playerToEdit != null) {
            EditYouthPlayerDialog(
                startYear = state.startYear,
                player = playerToEdit,
                onDismiss = { playerToEdit = null },
                onUpdate = { updatedPlayer -> 
                    viewModel.updatePlayer(updatedPlayer)
                    playerToEdit = null
                },
                onDelete = {
                    viewModel.deletePlayer(playerToEdit!!.id)
                    playerToEdit = null
                }
            )
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
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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

@Composable
fun AcademyPlayerCard(player: Player, onClick: (() -> Unit)? = null, onPromote: (() -> Unit)? = null, onRelease: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${player.displayName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${player.primaryPosition} • Age ${player.age}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("OVR", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(player.overall.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("POT", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(player.potential.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WinColor)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onPromote?.invoke() },
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text("Promote", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onRelease?.invoke() },
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text("Release", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
