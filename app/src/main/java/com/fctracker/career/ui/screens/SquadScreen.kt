package com.fctracker.career.ui.screens
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fctracker.career.MainViewModel
import com.fctracker.career.Player
import com.fctracker.career.ui.theme.WinColor
import com.fctracker.career.ui.theme.DrawColor
import com.fctracker.career.ui.theme.LossColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadScreen(viewModel: MainViewModel, onMenuClick: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val viewingSeason = state.historicalSeasons.find { it.year == state.viewingSeasonYear }
    val displayPlayers = viewingSeason?.players?.takeIf { it.isNotEmpty() } ?: state.players
    val displayTransfers = viewingSeason?.transfers?.takeIf { it.isNotEmpty() } ?: state.transfers
    val isArchived = state.viewingSeasonYear != null
    
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Roster", "Formation", "Tactics", "Injuries")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Squad Management", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
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
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }
            
            var selectedPlayerForDetails by remember { mutableStateOf<Player?>(null) }
            val eafcPlayers by viewModel.eafcPlayers.collectAsState()
            val availableEafc = if (eafcPlayers.isNotEmpty()) eafcPlayers else viewModel.allEafcPlayers

            val activeSquad = displayPlayers.filter { it.status != "Sold" && it.status != "Retired" && it.status != "Loaned Out" && !it.isLoanedOut && !it.isAcademy }
            val rosterSquad = displayPlayers.filter { it.status != "Sold" && it.status != "Retired" }
            when (selectedTabIndex) {
                0 -> RosterTab(rosterSquad, startYear = state.startYear, Modifier.weight(1f), onPlayerUpdate = { viewModel.updatePlayer(it) }, onPlayerDelete = { viewModel.deletePlayer(it) }, allEafcPlayers = availableEafc)
                1 -> FormationTab(
                    activeSquad,
                    Modifier.weight(1f),
                    onPlayerUpdate = { viewModel.updatePlayer(it) },
                    onApplyFormation = { viewModel.updatePlayers(it) },
                    onSwapPlayers = { id1, id2 -> viewModel.swapPlayers(id1, id2) },
                    onPlayerClick = { selectedPlayerForDetails = it },
                    activeFormation = state.activeFormation,
                    onActiveFormationChange = { viewModel.updateActiveFormation(it) }
                )
                2 -> TacticalVisionsTab(state, viewModel)
                3 -> MedicalWingTab(activeSquad, state.recentMatches, Modifier.weight(1f), onPlayerUpdate = { viewModel.updatePlayer(it) }, onNavigateToFormation = { selectedTabIndex = 1 })
            }

            selectedPlayerForDetails?.let { p ->
                DetailedPlayerStatsModal(
                    state = state,
                    player = p,
                    onDismiss = { selectedPlayerForDetails = null },
                    onMoveToAcademy = { demoted ->
                        viewModel.updatePlayer(demoted.copy(isAcademy = true, isPromoted = false, status = "Academy", isStartingXI = false))
                        selectedPlayerForDetails = null
                    },
                    onRetirePlayer = { retiring ->
                        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                        cal.set(java.util.Calendar.YEAR, state.startYear)
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        val dateStr = sdf.format(cal.time)
                        viewModel.addTransferLog(
                            com.fctracker.career.TransferLog(
                                playerName = retiring.displayName,
                                type = "Retirement",
                                feeM = 0.0,
                                fromToClub = "Retired",
                                date = dateStr
                            )
                        )
                        selectedPlayerForDetails = null
                    }
                )
            }
        }
    }
}

@Composable
fun RosterTab(players: List<Player>, startYear: Int = 2025, modifier: Modifier = Modifier, onPlayerUpdate: (Player) -> Unit = {}, onPlayerDelete: (String) -> Unit = {}, allEafcPlayers: List<com.fctracker.career.EafcPlayer> = emptyList()) {
    var searchQuery by remember { mutableStateOf("") }
    var editingPlayer by remember { mutableStateOf<Player?>(null) }
    var showAddPlayer by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search player name or position") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(onClick = { showAddPlayer = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Text("Add Player")
                }
            }
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filtered = players.filter {
                    it.displayName.contains(searchQuery, true) ||
                    it.name.contains(searchQuery, true) ||
                    it.primaryPosition.contains(searchQuery, true)
                }
                val firstTeamPlayers = filtered.filter { !it.isAcademy && !it.status.equals("Academy", ignoreCase = true) }
                    .sortedByDescending { it.overall }
                val academyPlayers = filtered.filter { it.isAcademy || it.status.equals("Academy", ignoreCase = true) }
                    .sortedByDescending { it.overall }

                if (firstTeamPlayers.isNotEmpty()) {
                    item {
                        Text(
                            text = "First Team (${firstTeamPlayers.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }
                    items(firstTeamPlayers, key = { it.id }) { player ->
                        PlayerRowDark(player, onClick = { editingPlayer = player })
                    }
                }
                if (academyPlayers.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Youth Academy (${academyPlayers.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }
                    items(academyPlayers, key = { it.id }) { player ->
                        PlayerRowDark(player, onClick = { editingPlayer = player })
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        editingPlayer?.let {
            EditPlayerDialog(startYear = startYear, player = it, allEafcPlayers = allEafcPlayers, onDismiss = { editingPlayer = null }, onUpdate = onPlayerUpdate, onDelete = { onPlayerDelete(it.id); editingPlayer = null })
        }

        if (showAddPlayer) {
            EditPlayerDialog(startYear = startYear, player = null, allEafcPlayers = allEafcPlayers, onDismiss = { showAddPlayer = false }, onUpdate = onPlayerUpdate)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MedicalWingTab(
    players: List<Player>, 
    recentMatches: List<com.fctracker.career.MatchResult>,
    modifier: Modifier = Modifier,
    onPlayerUpdate: (Player) -> Unit = {},
    onNavigateToFormation: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedPlayer by remember { mutableStateOf<Player?>(null) }
    var injuryName by remember { mutableStateOf("") }
    
    // Date Pickers state
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showReturnDatePicker by remember { mutableStateOf(false) }
    var startDateMillis by remember { mutableStateOf<Long?>(null) }
    var returnDateMillis by remember { mutableStateOf<Long?>(null) }
    
    val datePickerStateStart = androidx.compose.material3.rememberDatePickerState()
    val datePickerStateReturn = androidx.compose.material3.rememberDatePickerState()
    
    val dateFormatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    val startDateStr = startDateMillis?.let { dateFormatter.format(java.util.Date(it)) } ?: (selectedPlayer?.injuryStartDate ?: "")
    val returnDateStr = returnDateMillis?.let { dateFormatter.format(java.util.Date(it)) } ?: (selectedPlayer?.injuryReturnDate ?: "")

    var showFormationPopup by remember { mutableStateOf(false) }

    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            DarkCard(modifier = Modifier.fillMaxWidth()) {
                Text(if (selectedPlayer != null && selectedPlayer!!.isInjured) "Edit Injury" else "Add Injury", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(16.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedPlayer?.displayName ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Player") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true),
                            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            players.forEach { player ->
                                DropdownMenuItem(
                                    text = { Text(player.displayName) },
                                    onClick = {
                                        selectedPlayer = player
                                        injuryName = player.injuryName ?: ""
                                        startDateMillis = null
                                        returnDateMillis = null
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = injuryName,
                        onValueChange = { injuryName = it },
                        label = { Text("Injury Type (e.g. ACL Tear)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startDateStr, 
                            onValueChange = {}, 
                            readOnly = true,
                            label = { Text("Start Date") }, 
                            modifier = Modifier.weight(1f),
                            trailingIcon = {
                                androidx.compose.material3.IconButton(onClick = { showStartDatePicker = true }) {
                                    Icon(Icons.Default.DateRange, "Select Start Date")
                                }
                            }
                        )
                        OutlinedTextField(
                            value = returnDateStr, 
                            onValueChange = {}, 
                            readOnly = true,
                            label = { Text("Return Date") }, 
                            modifier = Modifier.weight(1f),
                            trailingIcon = {
                                androidx.compose.material3.IconButton(onClick = { showReturnDatePicker = true }) {
                                    Icon(Icons.Default.DateRange, "Select Return Date")
                                }
                            }
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        if (selectedPlayer?.isInjured == true) {
                            TextButton(onClick = {
                                selectedPlayer?.let { p ->
                                    onPlayerUpdate(p.copy(isInjured = false, injuryName = null, injuryStartDate = null, injuryReturnDate = null))
                                    selectedPlayer = null
                                    injuryName = ""
                                    startDateMillis = null
                                    returnDateMillis = null
                                }
                            }) { Text("Clear / Delete Injury", color = Color.Red) }
                        }
                        Button(onClick = { 
                            selectedPlayer?.let { p ->
                                val isStarter = p.isStartingXI && !p.isInjured
                                val updatedPlayer = p.copy(
                                    isInjured = true,
                                    injuryName = injuryName,
                                    injuryStartDate = startDateStr.takeIf { it.isNotBlank() },
                                    injuryReturnDate = returnDateStr.takeIf { it.isNotBlank() },
                                    isStartingXI = false
                                )
                                onPlayerUpdate(updatedPlayer)
                                if (isStarter) {
                                    showFormationPopup = true
                                } else {
                                    selectedPlayer = null
                                    injuryName = ""
                                    startDateMillis = null
                                    returnDateMillis = null
                                }
                            }
                        }) {
                            Text("Save Injury Record")
                        }
                    }
                }
            }
        }
        
        item {
            Text("Active Injuries", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        
        val injuredPlayers = players.filter { it.isInjured }
        if (injuredPlayers.isEmpty()) {
            item {
                Text("No current injuries.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(injuredPlayers) { p ->
                val missedMatches = recentMatches.count { match -> 
                    val d1 = p.injuryStartDate
                    val d2 = p.injuryReturnDate
                    if (d1 != null && d2 != null) match.matchDate in d1..d2 else false
                }
                
                DarkCard(modifier = Modifier.fillMaxWidth().clickable { 
                    selectedPlayer = p
                    injuryName = p.injuryName ?: ""
                    startDateMillis = null
                    returnDateMillis = null
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (p.headshotUrl.isNotEmpty()) {
                                coil.compose.SubcomposeAsyncImage(
                                    model = p.headshotUrl,
                                    contentDescription = "Player Headshot",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    error = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                )
                            } else {
                                Text(p.displayName.take(1), fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.displayName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                            Text("${p.injuryName ?: "Unknown Injury"}", color = Color.Red, fontSize = 14.sp)
                            Text("Out: ${p.injuryStartDate ?: "?"} to ${p.injuryReturnDate ?: "?"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$missedMatches", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                            Text("Matches Missed", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showStartDatePicker) {
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    startDateMillis = datePickerStateStart.selectedDateMillis
                    showStartDatePicker = false
                }) { Text("OK") }
            }
        ) {
            androidx.compose.material3.DatePicker(state = datePickerStateStart)
        }
    }

    if (showReturnDatePicker) {
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showReturnDatePicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    returnDateMillis = datePickerStateReturn.selectedDateMillis
                    showReturnDatePicker = false
                }) { Text("OK") }
            }
        ) {
            androidx.compose.material3.DatePicker(state = datePickerStateReturn)
        }
    }

    if (showFormationPopup) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { 
                showFormationPopup = false 
                selectedPlayer = null
                injuryName = ""
            },
            title = { Text("Adjust Starting 11") },
            text = { Text("The injured player was in your Starting 11 and has been moved to the bench. Please adjust your formation.") },
            confirmButton = {
                Button(onClick = {
                    showFormationPopup = false
                    selectedPlayer = null
                    injuryName = ""
                    onNavigateToFormation()
                }) {
                    Text("Go to Formation")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showFormationPopup = false
                    selectedPlayer = null
                    injuryName = ""
                }) {
                    Text("Dismiss")
                }
            }
        )
    }
}
@Composable
fun PlayerRowDark(player: Player, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .clickable { onClick() }
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (isValidPlayerHeadshot(player.headshotUrl)) {
                    coil.compose.SubcomposeAsyncImage(
                        model = player.headshotUrl,
                        contentDescription = "Player Headshot",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        error = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) }
                    )
                } else {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${player.displayName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    val tags = mutableListOf<String>()
                    if (player.isLoanedOut) tags.add("Out on Loan")
                    if (player.isLoanedIn) tags.add("Loaned In")
                    if (player.isRegen) tags.add("Regen")
                    if (player.isPromoted) tags.add("Youth")
                    else if (player.isAcademy) tags.add("Youth")
                    if (player.isInjured) tags.add("Injured")
                    if (tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            tags.forEach { tag ->
                                val (tagBgColor, tagTextColor, tagBorderColor, tagLabel) = when (tag) {
                                    "Regen" -> listOf(
                                        Color(0xFF4C1D95),
                                        Color(0xFFE9D5FF),
                                        Color(0xFFA855F7),
                                        "⚡ REGEN"
                                    )
                                    "Youth" -> listOf(
                                        Color(0xFF064E3B),
                                        Color(0xFF6EE7B7),
                                        Color(0xFF10B981),
                                        "🌱 YOUTH"
                                    )
                                    "Injured" -> listOf(
                                        Color.Red.copy(alpha = 0.25f),
                                        Color(0xFFFF8A80),
                                        Color.Red.copy(alpha = 0.6f),
                                        "INJURED"
                                    )
                                    "Out on Loan" -> listOf(
                                        Color(0xFFE65100).copy(alpha = 0.25f),
                                        Color(0xFFFFCC80),
                                        Color(0xFFFF9800).copy(alpha = 0.6f),
                                        "ON LOAN"
                                    )
                                    "Loaned In" -> listOf(
                                        Color(0xFF0D47A1).copy(alpha = 0.25f),
                                        Color(0xFF90CAF9),
                                        Color(0xFF2196F3).copy(alpha = 0.6f),
                                        "LOANED IN"
                                    )
                                    else -> listOf(
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                                        MaterialTheme.colorScheme.secondary,
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                        tag.uppercase()
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(tagBgColor as Color, RoundedCornerShape(6.dp))
                                        .border(1.dp, tagBorderColor as Color, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = tagLabel as String,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = tagTextColor as Color,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = "${player.primaryPosition} • Age ${player.age}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "€${player.valueM}M • €${player.wageK}K/wk",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (player.overall >= 80) WinColor
                        else if (player.overall >= 70) DrawColor
                        else LossColor
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = player.overall.toString(),
                    color = Color(0xFF1A1C18),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun PlayerFormModal(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        DarkCard(modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
            Text("Add Player Placeholder", color = Color.White)
        }
    }
}
