package com.fctracker.career.ui.screens
import com.fctracker.career.feeLabel
import com.fctracker.career.isIncomingDeal
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.ExitToApp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fctracker.career.MainViewModel
import com.fctracker.career.AppState
import com.fctracker.career.insights.TransferInsights
import com.fctracker.career.insights.TransferSummary
import com.fctracker.career.insights.TransferWindow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(viewModel: MainViewModel, onMenuClick: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val view = remember(state) { com.fctracker.career.logic.SeasonView.of(state) }
    val health by viewModel.health.collectAsState()
    val console = state.platform == com.fctracker.career.Platform.CONSOLE
    val displayTransfers = view.transfers.sortedByDescending { it.date }
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showTransferForm by remember { mutableStateOf(false) }
    var formInitialType by remember { mutableStateOf("In (Permanent)") }
    var logToEdit by remember { mutableStateOf<com.fctracker.career.TransferLog?>(null) }
    var showDeployScout by remember { mutableStateOf(false) }
    var showScoutNetwork by remember { mutableStateOf(false) }
    val c = com.fctracker.career.ui.theme.FcTheme.colors

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.statusBarsPadding()) {
            if (view.isArchived) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { com.fctracker.career.ui.components.ArchivedBanner("${view.label} · ${view.leagueName}", { viewModel.setViewingSeasonYear(null) }) }
            com.fctracker.career.ui.components.FcTopBar(
                "Transfers", sub = "Market · ${view.label}", onNav = onMenuClick,
                actions = { if (!view.isArchived && !console) com.fctracker.career.ui.components.FcIconButton(Icons.Default.Refresh, "Sync from the PC", { viewModel.fetchLiveTelemetry() }) },
                pill = if (!view.isArchived && !console) ({ StatusPill(health.level, health.pill, onClick = { viewModel.navigateToScreen("connection") }) }) else null
            )
            if (!view.isArchived) com.fctracker.career.ui.components.SoftTabs(listOf("Transfer market", "Scout network"), selectedTabIndex, { selectedTabIndex = it }, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
        }
        Box(Modifier.weight(1f).imePadding()) {
            when (if (view.isArchived) 0 else selectedTabIndex) {
                0 -> TransferMarketTab(
                    state = state,
                    displayTransfers = displayTransfers,
                    onLogTransfer = { formInitialType = "In (Permanent)"; showTransferForm = true },
                    onRecordRetirement = { formInitialType = "Retirement"; showTransferForm = true },
                    viewModel = viewModel,
                    onEditTransfer = { if (!view.isArchived) logToEdit = it },
                    archived = view.isArchived,
                    budget = view.transferBudget
                )
                1 -> ScoutNetworkTab(viewModel, state, onDeployScout = { showDeployScout = true }, onManageNetwork = { showScoutNetwork = true })
            }
        }
    }

    if (showTransferForm) TransferFormModal(state = state, viewModel = viewModel, logToEdit = null, initialType = formInitialType) { showTransferForm = false }
    if (logToEdit != null) TransferFormModal(state = state, viewModel = viewModel, logToEdit = logToEdit) { logToEdit = null }
    if (showDeployScout) DeployScoutModal(allEafcPlayers = viewModel.allEafcPlayers, onDismiss = { showDeployScout = false }, onSave = { report -> viewModel.addScoutReport(report); showDeployScout = false })
    if (showScoutNetwork) ScoutNetworkModal(onDismiss = { showScoutNetwork = false })
}

@Composable
fun TransferMarketTab(
    state: AppState, 
    displayTransfers: List<com.fctracker.career.TransferLog>, 
    onLogTransfer: () -> Unit, 
    onRecordRetirement: () -> Unit = {},
    viewModel: com.fctracker.career.MainViewModel, 
    onEditTransfer: (com.fctracker.career.TransferLog) -> Unit = {},
    archived: Boolean = false,
    budget: Double = state.transferBudget
) {
    var transferFilter by remember { mutableStateOf("All Types") }
    var filterExpanded by remember { mutableStateOf(false) }
    var windowKey by remember { mutableStateOf<String?>(null) }
    var profilePlayer by remember { mutableStateOf<com.fctracker.career.Player?>(null) }
    val windows = remember(displayTransfers) { TransferInsights.windows(displayTransfers) }
    val activeWindow = windows.firstOrNull { it.key == windowKey }
    val windowLogs = activeWindow?.logs ?: displayTransfers
    val filterOptions = listOf("All Types", "In (Permanent)", "Out (Permanent)", "In (Loan)", "Out (Loan)", "Free Agent Sign", "Retirement")
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            BudgetHeader(budget, onUpdateBudget = { viewModel.updateTransferBudget(it) }, editable = !archived, currency = state.currencySymbol)
            Spacer(modifier = Modifier.height(16.dp))
            
            val allTransfers = remember(state.transfers, state.historicalSeasons) {
                state.transfers + state.historicalSeasons.flatMap { it.transfers }
            }
            val recordBuys = allTransfers.filter { it.type.contains("In", ignoreCase = true) || it.type.equals("Bought", ignoreCase = true) }.sortedByDescending { it.feeM }.take(3)
            val recordSells = allTransfers.filter { (it.type.contains("Out", ignoreCase = true) || it.type.equals("Sold", ignoreCase = true)) && !it.type.equals("Retirement", ignoreCase = true) }.sortedByDescending { it.feeM }.take(3)
            
            if (recordBuys.isNotEmpty() || recordSells.isNotEmpty()) {
                Text("All-Time Record Deals", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recordBuys) { buy ->
                        RecordDealCard(title = "Record Signing", log = buy, state = state, isIncoming = true, viewModel = viewModel)
                    }
                    items(recordSells) { sell ->
                        RecordDealCard(title = "Record Departure", log = sell, state = state, isIncoming = false, viewModel = viewModel)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Transfer Logs", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!archived) OutlinedButton(
                        onClick = onRecordRetirement,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFBBF24)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.6f)),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFFBBF24))
                        Spacer(Modifier.width(4.dp))
                        Text("Retire Player", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    @OptIn(ExperimentalMaterial3Api::class)
                    ExposedDropdownMenuBox(
                        expanded = filterExpanded,
                        onExpandedChange = { filterExpanded = it },
                        modifier = Modifier.width(135.dp)
                    ) {
                        OutlinedTextField(
                            value = transferFilter,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterExpanded) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                        )
                        ExposedDropdownMenu(
                            expanded = filterExpanded,
                            onDismissRequest = { filterExpanded = false }
                        ) {
                            filterOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt, fontSize = 12.sp) },
                                    onClick = {
                                        transferFilter = opt
                                        filterExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val filteredTransfers = remember(windowLogs, transferFilter) {
                if (transferFilter == "All Types") windowLogs
                else windowLogs.filter { it.type.equals(transferFilter, ignoreCase = true) }
            }

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (displayTransfers.isNotEmpty()) {
                    item {
                        TransferSummaryCard(
                            summary = TransferInsights.summarise(windowLogs),
                            windows = windows,
                            selectedKey = activeWindow?.key,
                            currency = state.currencySymbol,
                            onSelect = { windowKey = it }
                        )
                    }
                }
                if (filteredTransfers.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (transferFilter == "All Types") "No recent transfers logged." else "No records found for $transferFilter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    val incomingDeals = filteredTransfers.filter { it.isIncomingDeal() }
                    val outgoingDeals = filteredTransfers.filterNot { it.isIncomingDeal() }
                    if (incomingDeals.isNotEmpty()) {
                        item { TransferSectionHeader("Transfers In", incomingDeals.size, Color(0xFF42A5F5)) }
                        items(incomingDeals) { log -> TransferLogRow(log, state, viewModel, onEditTransfer, onOpenPlayer = { profilePlayer = it }) }
                    }
                    if (outgoingDeals.isNotEmpty()) {
                        item {
                            if (incomingDeals.isNotEmpty()) Spacer(Modifier.height(8.dp))
                            TransferSectionHeader("Transfers Out", outgoingDeals.size, Color(0xFF4CAF50))
                        }
                        items(outgoingDeals) { log -> TransferLogRow(log, state, viewModel, onEditTransfer, onOpenPlayer = { profilePlayer = it }) }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 2.dp, bottom = 0.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!archived) com.fctracker.career.ui.components.Fab("Log deal", onLogTransfer, icon = Icons.Default.SwapHoriz)
        }

        profilePlayer?.let { p ->
            DetailedPlayerStatsModal(state = state, player = p, onDismiss = { profilePlayer = null })
        }
    }
}

/** "Transfers In (3)" style heading above a group of deals. */
@Composable
private fun TransferSectionHeader(title: String, count: Int, accent: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(width = 4.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.width(6.dp))
        Text("($count)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TransferLogRow(
    log: com.fctracker.career.TransferLog,
    state: AppState,
    viewModel: com.fctracker.career.MainViewModel,
    onEditTransfer: (com.fctracker.career.TransferLog) -> Unit,
    onOpenPlayer: (com.fctracker.career.Player) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onEditTransfer(log) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val resolvedHeadshot = resolvePlayerHeadshotUrl(log.playerName, state, viewModel)
        // A player who is in the squad has a profile: his picture opens it (a thin ring shows that it can be tapped).
        val squadPlayer = (state.players + state.academyPlayers).firstOrNull {
            it.name.equals(log.playerName.trim(), true) || it.displayName.equals(log.playerName.trim(), true)
        }
        TransferPlayerHeadshot(
            playerName = log.playerName,
            headshotUrl = resolvedHeadshot,
            modifier = if (squadPlayer != null) {
                Modifier
                    .size(40.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape)
                    .clickable { onOpenPlayer(squadPlayer) }
            } else Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        val isIncoming = log.isIncomingDeal()
        val isRetirement = log.type.equals("Retirement", ignoreCase = true) || log.type.equals("Retired", ignoreCase = true)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(com.fctracker.career.logic.PlayerNames.shown(log.playerName) + if (log.isRegen) " (Regen)" else "", fontWeight = FontWeight.Bold, color = Color.White)
                if (isRetirement) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "RETIRED",
                            color = Color(0xFFFBBF24),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            val subtitle = if (isRetirement) {
                "Retired from professional football"
            } else {
                "${log.type} ${if (isIncoming) "from" else "to"} ${log.fromToClub}" +
                    (log.swapPlayerName?.takeIf { it.isNotBlank() }?.let { " · swap with $it" } ?: "")
            }
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (isRetirement) {
                Text("Retired", fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
            } else {
                Text(log.feeLabel(state.currencySymbol), fontWeight = FontWeight.Bold, color = if (!isIncoming) Color(0xFF4CAF50) else Color(0xFFF44336))
            }
            Text(log.date, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ScoutNetworkTab(viewModel: com.fctracker.career.MainViewModel, state: AppState, onDeployScout: () -> Unit, onManageNetwork: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Active Scout Network", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Button(onClick = onManageNetwork, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.primary)) {
                    Text("Hire Scout", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No scouts hired.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Scouted Prospects Board", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))
            
            if (state.scoutReports.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Awaiting scout reports...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    itemsIndexed(state.scoutReports, key = { i, r -> "${r.id}#$i" }) { _, report ->
                        DarkCard {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(report.playerName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(report.position, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Age: ${report.age}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Est. Rating: ${report.ratingRange} | Est. Potential: ${report.potentialRange}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Value: ${state.currencySymbol}${report.value}M", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Scout: ${report.scoutName}", fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Rec: ${report.recommendation}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (report.recommendation == "Sign") Color(0xFF4CAF50) else if (report.recommendation == "Ignore") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                }
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    TextButton(onClick = { viewModel.removeScoutReport(report.id) }, contentPadding = PaddingValues(0.dp)) {
                                        Text("Delete", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        FloatingActionButton(
            onClick = onDeployScout,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Deploy Scout", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
fun DeployScoutModal(allEafcPlayers: List<com.fctracker.career.EafcPlayer>, onDismiss: () -> Unit, onSave: (com.fctracker.career.ScoutReport) -> Unit) {
    var playerName by remember { mutableStateOf("") }
    var positionSelected by remember { mutableStateOf("ST") }
    var age by remember { mutableStateOf("") }
    var ratingRange by remember { mutableStateOf("") }
    var potentialRange by remember { mutableStateOf("") }
    var valueM by remember { mutableStateOf("") }
    var scoutName by remember { mutableStateOf("") }
    var recommendationSelected by remember { mutableStateOf("Sign") }

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Create Scout Report", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                item {
                    var nameExpanded by remember { mutableStateOf(false) }
                    @OptIn(ExperimentalMaterial3Api::class)
                    ExposedDropdownMenuBox(expanded = nameExpanded, onExpandedChange = { nameExpanded = !nameExpanded }) {
                        OutlinedTextField(
                            value = playerName,
                            onValueChange = { 
                                playerName = it
                                nameExpanded = true
                            },
                            label = { Text("Player Name") },
                            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                            singleLine = true
                        )
                        if (playerName.isNotEmpty() && playerName.length >= 2) {
                            val filtered = allEafcPlayers.filter { it.team != "Retired" && (it.name.contains(playerName, ignoreCase = true) || it.commonName.contains(playerName, ignoreCase = true)) }.take(5)
                            if (filtered.isNotEmpty()) {
                                ExposedDropdownMenu(expanded = nameExpanded, onDismissRequest = { nameExpanded = false }) {
                                    filtered.forEach { p ->
                                        val displayName = if (p.commonName.isNotBlank()) "${p.commonName} (${p.name})" else p.name
                                        DropdownMenuItem(
                                            text = { Text(displayName) },
                                            onClick = {
                                                playerName = p.name
                                                nameExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    var positionExpanded by remember { mutableStateOf(false) }
                    val positions = listOf("GK", "CB", "LB", "RB", "CDM", "CM", "CAM", "LM", "RM", "LW", "RW", "ST")
                    @OptIn(ExperimentalMaterial3Api::class)
                    ExposedDropdownMenuBox(expanded = positionExpanded, onExpandedChange = { positionExpanded = it }) {
                        OutlinedTextField(value = positionSelected, onValueChange = {}, readOnly = true, label = { Text("Position") }, modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = positionExpanded) })
                        ExposedDropdownMenu(expanded = positionExpanded, onDismissRequest = { positionExpanded = false }) {
                            positions.forEach { pos ->
                                DropdownMenuItem(text = { Text(pos) }, onClick = { positionSelected = pos; positionExpanded = false })
                            }
                        }
                    }
                }
                item { OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Age") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = ratingRange, onValueChange = { ratingRange = it }, label = { Text("Estimated Rating Range (78 - 82)") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = potentialRange, onValueChange = { potentialRange = it }, label = { Text("Estimated Potential Range (86 - 91)") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = valueM, onValueChange = { valueM = it }, label = { Text("Estimated Value (€M)") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = scoutName, onValueChange = { scoutName = it }, label = { Text("Assigned Scout Name") }, modifier = Modifier.fillMaxWidth()) }
                item {
                    var recommendationExpanded by remember { mutableStateOf(false) }
                    val recommendations = listOf("Sign", "Keep Scouting", "Ignore")
                    @OptIn(ExperimentalMaterial3Api::class)
                    ExposedDropdownMenuBox(expanded = recommendationExpanded, onExpandedChange = { recommendationExpanded = it }) {
                        OutlinedTextField(value = recommendationSelected, onValueChange = {}, readOnly = true, label = { Text("Recommendation") }, modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = recommendationExpanded) })
                        ExposedDropdownMenu(expanded = recommendationExpanded, onDismissRequest = { recommendationExpanded = false }) {
                            recommendations.forEach { rec ->
                                DropdownMenuItem(text = { Text(rec) }, onClick = { recommendationSelected = rec; recommendationExpanded = false })
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) }
                        Button(onClick = {
                            val report = com.fctracker.career.ScoutReport(
                                playerName = playerName,
                                position = positionSelected,
                                age = age,
                                ratingRange = ratingRange,
                                potentialRange = potentialRange,
                                value = valueM,
                                scoutName = scoutName,
                                recommendation = recommendationSelected
                            )
                            onSave(report)
                        }) { Text("Save Report") }
                    }
                }
            }
        }
    }
}
@Composable
fun ScoutNetworkModal(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Hire Scout", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                item { OutlinedTextField(value = "", onValueChange = {}, label = { Text("Scout Name") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = "", onValueChange = {}, label = { Text("Assigned Region/Country") }, modifier = Modifier.fillMaxWidth()) }
                item {
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) }
                        Button(onClick = onDismiss) { Text("Hire Scout") }
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetHeader(budget: Double, onUpdateBudget: (Double) -> Unit = {}, editable: Boolean = true, currency: String = "€") {
    var isEditing by remember { mutableStateOf(false) }
    var budgetInput by remember { mutableStateOf(budget.toString()) }

    if (isEditing) {
        Dialog(onDismissRequest = { isEditing = false }) {
            DarkCard(modifier = Modifier.fillMaxWidth()) {
                Text("Edit Transfer Budget (M)", color = Color.White)
                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = { budgetInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { isEditing = false }) { Text("Cancel") }
                    Button(onClick = {
                        val newBudget = budgetInput.toDoubleOrNull() ?: budget
                        onUpdateBudget(newBudget)
                        isEditing = false
                    }) { Text("Save") }
                }
            }
        }
    }

    com.fctracker.career.ui.components.FcCard(Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                com.fctracker.career.ui.components.FcLabel(if (editable) "Remaining budget" else "Budget at the end of the season")
                com.fctracker.career.ui.components.Figure("$currency${String.format(java.util.Locale.US, "%.1f", budget)}M", 40.sp)
            }
            if (editable) com.fctracker.career.ui.components.FcIconButton(Icons.Default.Edit, "Edit transfer budget", { budgetInput = budget.toString(); isEditing = true })
        }
    }
}


fun resolvePlayerHeadshotUrl(playerName: String, state: AppState, viewModel: MainViewModel): String? {
    val cleanName = playerName.trim()
    val squadPlayer = state.players.firstOrNull { it.name.equals(cleanName, true) || it.displayName.equals(cleanName, true) }
    if (!squadPlayer?.headshotUrl.isNullOrBlank()) return squadPlayer?.headshotUrl
    val historicalPlayer = state.historicalSeasons.flatMap { it.players }.firstOrNull { it.name.equals(cleanName, true) || it.displayName.equals(cleanName, true) }
    if (!historicalPlayer?.headshotUrl.isNullOrBlank()) return historicalPlayer?.headshotUrl
    val eafcPlayer = viewModel.allEafcPlayers.firstOrNull { (it.commonName.isNotBlank() && it.commonName.equals(cleanName, true)) || it.name.equals(cleanName, true) }
    if (eafcPlayer != null) return "https://cdn.futbin.com/content/fifa25/img/players/${eafcPlayer.id}.png"
    return null
}

@Composable
fun TransferPlayerHeadshot(
    playerName: String,
    headshotUrl: String?,
    modifier: Modifier = Modifier.size(40.dp)
) {
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (!headshotUrl.isNullOrBlank() && isValidPlayerHeadshot(headshotUrl)) {
            coil.compose.SubcomposeAsyncImage(
                model = headshotUrl,
                contentDescription = "$playerName Headshot",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                error = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Default Silhouette",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxSize(0.65f)
                    )
                }
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Default Silhouette",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxSize(0.65f)
            )
        }
    }
}

@Composable
fun RecordDealCard(title: String, log: com.fctracker.career.TransferLog, state: AppState, isIncoming: Boolean, viewModel: MainViewModel) {
    val resolvedHeadshot = resolvePlayerHeadshotUrl(log.playerName, state, viewModel)
    
    Box(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, if (isIncoming) Color(0xFF4CAF50).copy(alpha = 0.3f) else Color(0xFFF44336).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Text(log.feeLabel(state.currencySymbol), fontSize = 10.sp, color = if (isIncoming) Color(0xFF4CAF50) else Color(0xFFF44336), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TransferPlayerHeadshot(
                    playerName = log.playerName,
                    headshotUrl = resolvedHeadshot,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(com.fctracker.career.logic.PlayerNames.shown(log.playerName), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(if (isIncoming) "From: ${log.fromToClub}" else "To: ${log.fromToClub}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SummaryFigure(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/**
 * Money spent and received, and the number of deals, for one transfer window (or for everything). Fees the game does
 * not store are not in the totals: the card says how many there are, so the totals are never read as complete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferSummaryCard(
    summary: TransferSummary,
    windows: List<TransferWindow>,
    selectedKey: String?,
    currency: String,
    onSelect: (String?) -> Unit
) {
    DarkCard(modifier = Modifier.fillMaxWidth()) {
        if (windows.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = selectedKey == null, onClick = { onSelect(null) }, label = { Text("All", fontSize = 12.sp) })
                windows.forEach { w ->
                    FilterChip(selected = selectedKey == w.key, onClick = { onSelect(w.key) }, label = { Text(w.label, fontSize = 12.sp) })
                }
            }
            Spacer(Modifier.height(10.dp))
        } else if (windows.size == 1) {
            Text(windows[0].label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            SummaryFigure("Spent", TransferInsights.money(summary.spent, currency), Color(0xFFF44336))
            SummaryFigure("Received", TransferInsights.money(summary.received, currency), Color(0xFF4CAF50))
            SummaryFigure(
                "Net",
                TransferInsights.money(summary.net, currency, signed = true),
                when {
                    summary.net > 0.0 -> Color(0xFF4CAF50)
                    summary.net < 0.0 -> Color(0xFFF44336)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        val parts = mutableListOf("${summary.signings} in", "${summary.departures} out")
        if (summary.unknownFees > 0) {
            parts.add("${summary.unknownFees} ${if (summary.unknownFees == 1) "fee" else "fees"} unknown, not counted")
        }
        Text(parts.joinToString("  ·  "), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
