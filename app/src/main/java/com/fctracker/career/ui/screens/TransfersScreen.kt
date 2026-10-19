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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(viewModel: MainViewModel, onMenuClick: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val viewingSeason = state.historicalSeasons.find { it.year == state.viewingSeasonYear }
    val displayPlayers = viewingSeason?.players?.takeIf { it.isNotEmpty() } ?: state.players
    val displayTransfers = (viewingSeason?.transfers?.takeIf { it.isNotEmpty() } ?: state.transfers).sortedByDescending { it.date }
    val isArchived = state.viewingSeasonYear != null
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Transfer Market", "Scout Network")
    
    var showTransferForm by remember { mutableStateOf(false) }
    var formInitialType by remember { mutableStateOf("In (Permanent)") }
    var logToEdit by remember { mutableStateOf<com.fctracker.career.TransferLog?>(null) }
    var showDeployScout by remember { mutableStateOf(false) }
    var showScoutNetwork by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Transfers & Scouting", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.fetchLiveTelemetry(silent = false)
                        }
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.Refresh,
                            contentDescription = "Sync from Bridge",
                            tint = MaterialTheme.colorScheme.primary
                        )
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
            
            when (selectedTabIndex) {
                0 -> TransferMarketTab(
                    state = state,
                    displayTransfers = displayTransfers,
                    onLogTransfer = { 
                        formInitialType = "In (Permanent)"
                        showTransferForm = true 
                    },
                    onRecordRetirement = {
                        formInitialType = "Retirement"
                        showTransferForm = true
                    },
                    viewModel = viewModel,
                    onEditTransfer = { logToEdit = it }
                )
                1 -> ScoutNetworkTab(viewModel, state, onDeployScout = { showDeployScout = true }, onManageNetwork = { showScoutNetwork = true })
            }
        }
    }
    
    if (showTransferForm) TransferFormModal(state = viewModel.state.collectAsState().value, viewModel = viewModel, logToEdit = null, initialType = formInitialType) { showTransferForm = false }
    if (logToEdit != null) TransferFormModal(state = viewModel.state.collectAsState().value, viewModel = viewModel, logToEdit = logToEdit) { logToEdit = null }
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
    onEditTransfer: (com.fctracker.career.TransferLog) -> Unit = {}
) {
    var transferFilter by remember { mutableStateOf("All Types") }
    var filterExpanded by remember { mutableStateOf(false) }
    val filterOptions = listOf("All Types", "In (Permanent)", "Out (Permanent)", "In (Loan)", "Out (Loan)", "Free Agent Sign", "Retirement")
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            BudgetHeader(state.transferBudget, onUpdateBudget = { /* Need to update app state, handle it below */ viewModel.updateTransferBudget(it) })
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
                    OutlinedButton(
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
            
            val filteredTransfers = remember(displayTransfers, transferFilter) {
                if (transferFilter == "All Types") displayTransfers
                else displayTransfers.filter { it.type.equals(transferFilter, ignoreCase = true) }
            }

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        items(incomingDeals) { log -> TransferLogRow(log, state, viewModel, onEditTransfer) }
                    }
                    if (outgoingDeals.isNotEmpty()) {
                        item {
                            if (incomingDeals.isNotEmpty()) Spacer(Modifier.height(8.dp))
                            TransferSectionHeader("Transfers Out", outgoingDeals.size, Color(0xFF4CAF50))
                        }
                        items(outgoingDeals) { log -> TransferLogRow(log, state, viewModel, onEditTransfer) }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = onLogTransfer,
                icon = { Icon(Icons.Default.Add, contentDescription = "Log Transfer Deal") },
                text = { Text("Log Deal", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary
            )
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
    onEditTransfer: (com.fctracker.career.TransferLog) -> Unit
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
        TransferPlayerHeadshot(
            playerName = log.playerName,
            headshotUrl = resolvedHeadshot,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        val isIncoming = log.isIncomingDeal()
        val isRetirement = log.type.equals("Retirement", ignoreCase = true) || log.type.equals("Retired", ignoreCase = true)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(log.playerName + if (log.isRegen) " (Regen)" else "", fontWeight = FontWeight.Bold, color = Color.White)
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
                "${log.type} ${if (isIncoming) "from" else "to"} ${log.fromToClub}"
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
                    items(state.scoutReports, key = { it.id }) { report ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferFormModal(
    state: com.fctracker.career.AppState, 
    viewModel: com.fctracker.career.MainViewModel, 
    logToEdit: com.fctracker.career.TransferLog? = null, 
    initialType: String = "In (Permanent)",
    onDismiss: () -> Unit
) {
    var typeExpanded by remember { mutableStateOf(false) }
    var transferType by remember(logToEdit, initialType) { mutableStateOf(logToEdit?.type ?: initialType) }
    val transferTypes = listOf("In (Permanent)", "Out (Permanent)", "In (Loan)", "Out (Loan)", "Free Agent Sign", "Retirement")
    
    var playerQuery by remember(logToEdit) { mutableStateOf(logToEdit?.playerName ?: "") }
    var playerFilterText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(emptyList<com.fctracker.career.EafcPlayer>()) }
    var searchExpanded by remember { mutableStateOf(false) }
    
    var includeSwap by remember(logToEdit) { mutableStateOf(logToEdit?.includeSwap ?: false) }
    var isRegen by remember(logToEdit) { mutableStateOf(logToEdit?.isRegen ?: false) }
    var swapPlayerQuery by remember { mutableStateOf("") }
    var swapSearchResults by remember { mutableStateOf(emptyList<com.fctracker.career.EafcPlayer>()) }
    var swapSearchExpanded by remember { mutableStateOf(false) }
    
    var teamQuery by remember(logToEdit) { 
        mutableStateOf(
            logToEdit?.fromToClub ?: if (transferType == "Retirement") "Retired" else ""
        ) 
    }
    var teamResults by remember { mutableStateOf(emptyList<String>()) }
    var teamExpanded by remember { mutableStateOf(false) }
    
    var feeM by remember(logToEdit) { 
        mutableStateOf(
            logToEdit?.feeM?.toString() ?: if (transferType == "Retirement") "0" else ""
        ) 
    }
    var transferDate by remember(logToEdit) { 
        mutableStateOf(
            if (logToEdit?.date?.isNotEmpty() == true) logToEdit.date 
            else {
                val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                cal.set(java.util.Calendar.YEAR, state.startYear)
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                sdf.format(cal.time)
            }
        ) 
    }
    var showDatePicker by remember { mutableStateOf(false) }

    val academyIds = remember(state.academyPlayers) {
        state.academyPlayers.mapNotNull { it.id.takeIf { id -> id.isNotBlank() } }.toSet()
    }
    val academyNames = remember(state.academyPlayers) {
        state.academyPlayers.flatMap { listOf(it.displayName.lowercase().trim(), it.name.lowercase().trim()) }.filter { it.isNotBlank() }.toSet()
    }
    val seniorSquadPlayers = remember(state.players, academyIds, academyNames) {
        state.players.filter { p ->
            !p.isAcademy &&
            !p.status.equals("Academy", ignoreCase = true) &&
            !p.status.equals("Youth", ignoreCase = true) &&
            !p.status.equals("Sold", ignoreCase = true) &&
            !p.status.equals("Retired", ignoreCase = true) &&
            !academyIds.contains(p.id) &&
            !academyNames.contains(p.displayName.lowercase().trim()) &&
            !academyNames.contains(p.name.lowercase().trim())
        }.sortedByDescending { it.overall }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (transferType == "Retirement") {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Record Player Retirement", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
                        } else {
                            Text("Log Transfer Deal", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                if (transferType == "Retirement") {
                    item {
                        Surface(
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Career Retirement", fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24), fontSize = 12.sp)
                                    Text("Retire a senior squad player. Their status changes to Retired and they are permanently logged in transfer records.", color = Color.LightGray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                        OutlinedTextField(
                            value = transferType, 
                            onValueChange = {}, 
                            readOnly = true, 
                            label = { Text("Transfer Type") }, 
                            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(), 
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) }
                        )
                        ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                            transferTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = { 
                                        transferType = type
                                        typeExpanded = false 
                                        if (type == "Retirement") {
                                            if (teamQuery.isBlank() || teamQuery != "Retired") teamQuery = "Retired"
                                            if (feeM.isBlank()) feeM = "0"
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    val isOutTransfer = transferType == "Out (Permanent)" || transferType == "Out (Loan)" || transferType == "Retirement"
                    if (isOutTransfer) {
                        val displaySeniorSquad = remember(seniorSquadPlayers, playerFilterText) {
                            if (playerFilterText.isBlank()) seniorSquadPlayers
                            else seniorSquadPlayers.filter {
                                it.displayName.contains(playerFilterText, ignoreCase = true) ||
                                it.name.contains(playerFilterText, ignoreCase = true) ||
                                it.primaryPosition.contains(playerFilterText, ignoreCase = true)
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = if (playerQuery.isNotBlank()) playerQuery else playerFilterText,
                                onValueChange = { 
                                    playerFilterText = it
                                    playerQuery = ""
                                    searchExpanded = true
                                },
                                label = { 
                                    Text(
                                        if (transferType == "Retirement") "Select Retiring Player (Senior Squad)" 
                                        else "Select Player to Transfer Out (Senior Squad)"
                                    ) 
                                },
                                placeholder = { Text("Tap to select senior player...") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = searchExpanded) },
                                modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable, true).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = searchExpanded,
                                onDismissRequest = { searchExpanded = false }
                            ) {
                                if (displaySeniorSquad.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { 
                                            Column {
                                                Text("No eligible senior squad players found", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                                Text("Academy players cannot be transferred out directly", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                            }
                                        },
                                        onClick = { searchExpanded = false }
                                    )
                                } else {
                                    displaySeniorSquad.forEach { p ->
                                        DropdownMenuItem(
                                            text = { 
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    TransferPlayerHeadshot(
                                                        playerName = p.displayName,
                                                        headshotUrl = p.headshotUrl,
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                    Spacer(Modifier.width(10.dp))
                                                    Column {
                                                        Text(p.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                        Text(
                                                            "${p.primaryPosition} • OVR ${p.overall} • Age ${p.age}",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                playerQuery = p.displayName
                                                playerFilterText = ""
                                                searchExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = playerQuery,
                                onValueChange = { 
                                    playerQuery = it
                                    searchResults = viewModel.searchPlayersAndTeams(it)
                                    searchExpanded = searchResults.isNotEmpty()
                                },
                                label = { Text("Player Name Search") },
                                modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = searchExpanded,
                                onDismissRequest = { searchExpanded = false }
                            ) {
                                searchResults.forEach { p ->
                                    val dispName = if (p.commonName.isNotEmpty()) p.commonName else p.name
                                    DropdownMenuItem(
                                        text = { 
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                TransferPlayerHeadshot(
                                                    playerName = dispName,
                                                    headshotUrl = "https://cdn.futbin.com/content/fifa25/img/players/${p.id}.png",
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(dispName)
                                            }
                                        },
                                        onClick = {
                                            playerQuery = dispName
                                            searchExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (transferType != "Retirement") {
                    item { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includeSwap, onCheckedChange = { includeSwap = it })
                            Text("Include Player Swap", color = Color.White)
                        }
                    }
                    
                    if (includeSwap) {
                        item {
                            val isOutTransfer = transferType == "Out (Permanent)" || transferType == "Out (Loan)"
                            if (isOutTransfer) {
                                ExposedDropdownMenuBox(
                                    expanded = swapSearchExpanded,
                                    onExpandedChange = { swapSearchExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = swapPlayerQuery,
                                        onValueChange = { 
                                            swapPlayerQuery = it
                                            swapSearchResults = viewModel.searchPlayersAndTeams(it)
                                            swapSearchExpanded = swapSearchResults.isNotEmpty()
                                        },
                                        label = { Text("Search Incoming Player") },
                                        modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = swapSearchExpanded,
                                        onDismissRequest = { swapSearchExpanded = false }
                                    ) {
                                        swapSearchResults.forEach { p ->
                                            val dispName = if (p.commonName.isNotEmpty()) p.commonName else p.name
                                            DropdownMenuItem(
                                                text = { Text(dispName) },
                                                onClick = {
                                                    swapPlayerQuery = dispName
                                                    swapSearchExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                ExposedDropdownMenuBox(
                                    expanded = swapSearchExpanded,
                                    onExpandedChange = { swapSearchExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = swapPlayerQuery,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Select Senior Player to Swap Out") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = swapSearchExpanded) },
                                        modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = swapSearchExpanded,
                                        onDismissRequest = { swapSearchExpanded = false }
                                    ) {
                                        seniorSquadPlayers.forEach { p ->
                                            DropdownMenuItem(
                                                text = { 
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        TransferPlayerHeadshot(
                                                            playerName = p.displayName,
                                                            headshotUrl = p.headshotUrl,
                                                            modifier = Modifier.size(28.dp)
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Text("${p.displayName} (${p.primaryPosition} • OVR ${p.overall})")
                                                    }
                                                },
                                                onClick = {
                                                    swapPlayerQuery = p.displayName
                                                    swapSearchExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isRegen, onCheckedChange = { isRegen = it })
                            Text("Is Regen", color = Color.White)
                        }
                    }
                }
                
                item {
                    if (transferType == "Retirement") {
                        OutlinedTextField(
                            value = "Retired from Football",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Destination / Status") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = teamExpanded,
                            onExpandedChange = { teamExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = teamQuery,
                                onValueChange = { 
                                    teamQuery = it
                                    teamResults = viewModel.searchPlayersAndTeams(it).map { it.team }.distinct()
                                    teamExpanded = teamResults.isNotEmpty()
                                },
                                label = { Text("Opponent Team Search") },
                                modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = teamExpanded,
                                onDismissRequest = { teamExpanded = false }
                            ) {
                                teamResults.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t) },
                                        onClick = {
                                            teamQuery = t
                                            teamExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (transferType == "Retirement") {
                            OutlinedTextField(
                                value = "€0.0M (Career Ended)", 
                                onValueChange = {}, 
                                readOnly = true,
                                label = { Text("Transfer Fee") }, 
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            OutlinedTextField(value = feeM, onValueChange = { feeM = it }, label = { Text("Fee (€M)") }, modifier = Modifier.weight(1f))
                        }
                        
                        if (showDatePicker) {
                            val initialMillis = remember(transferDate) {
                                try {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                                    sdf.parse(transferDate)?.time
                                } catch (e: Exception) { null }
                            }
                            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis, initialDisplayedMonthMillis = initialMillis)
                            DatePickerDialog(
                                onDismissRequest = { showDatePicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        datePickerState.selectedDateMillis?.let { millis ->
                                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                                            transferDate = sdf.format(java.util.Date(millis))
                                        }
                                        showDatePicker = false
                                    }) { Text("OK") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                                }
                            ) {
                                DatePicker(state = datePickerState)
                            }
                        }
                        
                        Box(modifier = Modifier.weight(1f).clickable { showDatePicker = true }) {
                            OutlinedTextField(
                                value = transferDate,
                                onValueChange = {},
                                label = { Text("Date") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        if (logToEdit != null) {
                            Button(onClick = {
                                viewModel.deleteTransferLog(logToEdit.id)
                                onDismiss()
                            }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
                        } else {
                            Spacer(Modifier.width(8.dp))
                        }
                        
                        Row {
                            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) }
                            Button(
                                onClick = {
                                    val savedLog = com.fctracker.career.TransferLog(
                                        id = logToEdit?.id ?: java.util.UUID.randomUUID().toString(),
                                        playerName = playerQuery,
                                        type = transferType,
                                        feeM = if (transferType == "Retirement") 0.0 else (feeM.toDoubleOrNull() ?: 0.0),
                                        fromToClub = if (transferType == "Retirement") "Retired" else teamQuery,
                                        date = transferDate,
                                        includeSwap = if (transferType == "Retirement") false else includeSwap,
                                        isRegen = if (transferType == "Retirement") false else isRegen,
                                        headshotUrl = logToEdit?.headshotUrl ?: "",
                                        position = logToEdit?.position ?: "CM"
                                    )
                                    if (logToEdit != null) {
                                        viewModel.updateTransferLog(logToEdit, savedLog)
                                    } else {
                                        viewModel.addTransferLog(savedLog)
                                    }
                                    onDismiss()
                                },
                                colors = if (transferType == "Retirement") ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B), contentColor = Color.Black) else ButtonDefaults.buttonColors()
                            ) { 
                                Text(if (logToEdit != null) "Update" else if (transferType == "Retirement") "Confirm Retirement" else "Submit", fontWeight = FontWeight.Bold) 
                            }
                        }
                    }
                }
            }
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
fun BudgetHeader(budget: Double, onUpdateBudget: (Double) -> Unit = {}) {
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Remaining Budget", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
                Text("€${budget}M", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { isEditing = true }) {
                Icon(Icons.Default.Edit, "Edit Budget", tint = MaterialTheme.colorScheme.primary)
            }
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
                    Text(log.playerName, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(if (isIncoming) "From: ${log.fromToClub}" else "To: ${log.fromToClub}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}
