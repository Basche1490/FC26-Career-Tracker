package com.fctracker.career.ui.screens
import com.fctracker.career.feeLabel
import com.fctracker.career.TeamTrophy
import com.fctracker.career.PlayerAward

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
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
import com.fctracker.career.AppState
import com.fctracker.career.MainViewModel
import com.fctracker.career.Objective

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonSummaryScreen(viewModel: MainViewModel, onMenuClick: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Squad & Awards")
    
    var showAwardsModal by remember { mutableStateOf(false) }
    var selectedPlayer by remember { mutableStateOf<com.fctracker.career.Player?>(null) }
    
    selectedPlayer?.let {
        DetailedPlayerStatsModal(state = state, player = it, onDismiss = { selectedPlayer = null })
    }
    
    if (showAwardsModal) {
        AwardsModal(state = state, viewModel = viewModel, onDismiss = { showAwardsModal = false })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Season Summary", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = MaterialTheme.colorScheme.primary
                    )
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
                0 -> OverviewTab(state, viewModel)
                1 -> SquadStatsAwardsTab(state, onPlayerClick = { selectedPlayer = it }) { showAwardsModal = true }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewTab(state: AppState, viewModel: MainViewModel) {

    val viewingSeason = state.historicalSeasons.find { it.year == state.viewingSeasonYear }
    val displayMatches = if (viewingSeason != null) viewingSeason.matches ?: emptyList() else state.recentMatches
    val displayPlayers = if (viewingSeason != null) viewingSeason.players ?: emptyList() else state.players
    var showGraphicCard by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { OverviewStatsCard(state) }

        item {
            DarkCard(modifier = Modifier.fillMaxWidth()) {
                val seasonTransfers = viewingSeason?.transfers ?: state.transfers
                val incomingCount = seasonTransfers.count { it.type.contains("In", ignoreCase = true) || it.type.equals("Bought", ignoreCase = true) }
                val outgoingCount = seasonTransfers.count { it.type.contains("Out", ignoreCase = true) || it.type.equals("Sold", ignoreCase = true) }
                val totalSpent = seasonTransfers.filter { it.type.contains("In", ignoreCase = true) || it.type.equals("Bought", ignoreCase = true) }.sumOf { it.feeM }
                val totalReceived = seasonTransfers.filter { it.type.contains("Out", ignoreCase = true) || it.type.equals("Sold", ignoreCase = true) }.sumOf { it.feeM }

                Text("Financial & Transfer Summary", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))

                if (viewingSeason == null) {
                    Text("Current Transfer Budget: ${state.currencySymbol}${state.transferBudget}M", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Signings: $incomingCount", color = Color.White)
                        Text("Total Spent: ${state.currencySymbol}${String.format(java.util.Locale.US, "%.2f", totalSpent)}M", color = com.fctracker.career.ui.theme.LossColor, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Sales: $outgoingCount", color = Color.White)
                        Text("Total Received: ${state.currencySymbol}${String.format(java.util.Locale.US, "%.2f", totalReceived)}M", color = com.fctracker.career.ui.theme.WinColor, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (viewingSeason != null) "Top Transfers (${viewingSeason.year}/${viewingSeason.year + 1})" else "Top Transfers (Current Season)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (seasonTransfers.isEmpty()) {
                    Text("No transfers recorded for this season.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    seasonTransfers.sortedByDescending { it.feeM }.take(10).forEach { tr ->
                        val isIncoming = tr.type.contains("In", ignoreCase = true) || tr.type.equals("Bought", ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isIncoming) Color(0xFF1E88E5).copy(alpha = 0.2f) else Color(0xFF4CAF50).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (isIncoming) "SIGNING" else "SALE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isIncoming) Color(0xFF42A5F5) else Color(0xFF81C784),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(tr.playerName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                    if (tr.fromToClub.isNotBlank()) {
                                        Text(
                                            text = "${if (isIncoming) "from" else "to"} ${tr.fromToClub}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = tr.feeLabel(state.currencySymbol),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isIncoming) Color(0xFF81C784) else Color.White
                                )
                                if (tr.date.isNotBlank()) {
                                    Text(tr.date, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        item { Spacer(Modifier.height(80.dp)) }
    }

}

data class PlayerSeasonStats(val player: com.fctracker.career.Player, var apps: Int = 0, var goals: Int = 0, var assists: Int = 0, var totalRating: Double = 0.0, var ratingCount: Int = 0) {
    val avgRating: Double get() = if (ratingCount > 0) totalRating / ratingCount else 0.0
}

@Composable
fun SquadStatsAwardsTab(state: AppState, onPlayerClick: (com.fctracker.career.Player) -> Unit = {}, onShowAwards: () -> Unit) {
    val viewingSeason = state.historicalSeasons.find { it.year == state.viewingSeasonYear }
    val displayMatches = if (viewingSeason != null) viewingSeason.matches ?: emptyList() else state.recentMatches
    val displayPlayers = (if (viewingSeason != null) viewingSeason.players ?: emptyList() else state.players).filter { !it.isAcademy && !it.status.equals("Academy", ignoreCase = true) }

    var sortMode by remember { mutableStateOf("Rating") } // "Name", "Apps", "Goals", "Assists", "Rating"
    val seasonStatsMap = remember(displayMatches, displayPlayers) {
        val map = mutableMapOf<String, PlayerSeasonStats>()
        displayPlayers.forEach { map[it.id] = PlayerSeasonStats(it) }
        displayMatches.forEach { match ->
            match.playerPerformances.forEach { perf ->
                map[perf.playerId]?.let { s ->
                    s.apps++
                    s.goals += perf.goals
                    s.assists += perf.assists
                    s.totalRating += perf.rating
                    s.ratingCount++
                }
            }
        }
        map.values.toList().sortedByDescending { it.apps }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { OverviewStatsCard(state) }
        item {
            Button(onClick = onShowAwards, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Text("Assign Awards & Honors")
            }
        }
        
        item {
            DarkCard(modifier = Modifier.fillMaxWidth()) {
                Text("Season Milestones & Record Holders", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
                
                val mostApps = seasonStatsMap.maxByOrNull { it.apps }
                val topScorer = seasonStatsMap.maxByOrNull { it.goals }
                val topAssists = seasonStatsMap.maxByOrNull { it.assists }
                val highestAvg = seasonStatsMap.filter { it.apps > 0 }.maxByOrNull { it.avgRating }
                
                RecordCardWithImage("Most Appearances", mostApps?.player?.displayName ?: "-", mostApps?.apps?.toString() ?: "0", mostApps?.player?.headshotUrl ?: "")
                Spacer(Modifier.height(8.dp))
                RecordCardWithImage("Top Scorer", topScorer?.player?.displayName ?: "-", topScorer?.goals?.toString() ?: "0", topScorer?.player?.headshotUrl ?: "")
                Spacer(Modifier.height(8.dp))
                RecordCardWithImage("Top Assists", topAssists?.player?.displayName ?: "-", topAssists?.assists?.toString() ?: "0", topAssists?.player?.headshotUrl ?: "")
            }
        }
        

        
        item {
            DarkCard(modifier = Modifier.fillMaxWidth()) {
                Text("Season Player Statistics", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Player", fontWeight = FontWeight.Bold, color = if (sortMode == "Name") MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.weight(1.5f).clickable { sortMode = "Name" })
                    Text("Apps", fontWeight = FontWeight.Bold, color = if (sortMode == "Apps") MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.weight(1f).clickable { sortMode = "Apps" })
                    Text("Gls", fontWeight = FontWeight.Bold, color = if (sortMode == "Goals") MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.weight(1f).clickable { sortMode = "Goals" })
                    Text("Ast", fontWeight = FontWeight.Bold, color = if (sortMode == "Assists") MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.weight(1f).clickable { sortMode = "Assists" })
                    Text("Avg", fontWeight = FontWeight.Bold, color = if (sortMode == "Rating") MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.weight(1f).clickable { sortMode = "Rating" })
                }
                
                val sortedStats = when(sortMode) {
                    "Name" -> seasonStatsMap.sortedBy { it.player.displayName }
                    "Apps" -> seasonStatsMap.sortedByDescending { it.apps }
                    "Goals" -> seasonStatsMap.sortedByDescending { it.goals }
                    "Assists" -> seasonStatsMap.sortedByDescending { it.assists }
                    else -> seasonStatsMap.sortedByDescending { it.avgRating }
                }
                
                seasonStatsMap.forEach { stat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayerClick(stat.player) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${stat.player.displayName}", color = Color.White, modifier = Modifier.weight(2f))
                        Text("${stat.apps}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("${stat.goals}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text("${stat.assists}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
fun RecordCardWithImage(title: String, playerName: String, recordValue: String, imageUrl: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    if (isValidPlayerHeadshot(imageUrl)) {
                        coil.compose.SubcomposeAsyncImage(
                            model = imageUrl,
                            contentDescription = playerName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            error = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        )
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(playerName, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Text(recordValue, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AwardsModal(state: AppState, viewModel: MainViewModel, onDismiss: () -> Unit) {

    val viewingSeason = state.historicalSeasons.find { it.year == state.viewingSeasonYear }
    val displayMatches = if (viewingSeason != null) viewingSeason.matches ?: emptyList() else state.recentMatches
    val displayPlayers = (if (viewingSeason != null) viewingSeason.players ?: emptyList() else state.players).filter { !it.isAcademy && !it.status.equals("Academy", ignoreCase = true) }
    var showAddTeamTrophy by remember { mutableStateOf(false) }
    var showAddPlayerAward by remember { mutableStateOf(false) }

    if (showAddTeamTrophy) {
        Dialog(onDismissRequest = { showAddTeamTrophy = false }) {
            DarkCard(modifier = Modifier.fillMaxWidth().heightIn(max=500.dp)) {
                Text("Add Team Trophy", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                var name by remember { mutableStateOf("") }
                var iconUrl by remember { mutableStateOf("") }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Trophy Name (e.g., Premier League)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = iconUrl, onValueChange = { iconUrl = it }, label = { Text("Icon URL (Optional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { showAddTeamTrophy = false }) { Text("Cancel") }
                    Button(onClick = {
                        viewModel.addTeamTrophy(com.fctracker.career.TeamTrophy(name = name, iconUrl = iconUrl))
                        showAddTeamTrophy = false
                    }) { Text("Save") }
                }
            }
        }
    }

    if (showAddPlayerAward) {
        Dialog(onDismissRequest = { showAddPlayerAward = false }) {
            DarkCard(modifier = Modifier.fillMaxWidth().heightIn(max=500.dp)) {
                Text("Add Player Award", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                var name by remember { mutableStateOf("") }
                var playerId by remember { mutableStateOf("") }
                var monthYear by remember { mutableStateOf("") }
                var iconUrl by remember { mutableStateOf("") }
                
                var playerExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = playerExpanded, onExpandedChange = { playerExpanded = !playerExpanded }) {
                    OutlinedTextField(
                        value = displayPlayers.find { it.id == playerId }?.displayName ?: "Select Player",
                        onValueChange = {}, readOnly = true, label = { Text("Player") },
                        modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = playerExpanded, onDismissRequest = { playerExpanded = false }) {
                        displayPlayers.forEach { p ->
                            DropdownMenuItem(text = { Text(p.displayName) }, onClick = { playerId = p.id; playerExpanded = false })
                        }
                    }
                }
                
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Award Name (e.g., Ballon d'Or)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = monthYear, onValueChange = { monthYear = it }, label = { Text("Month/Year") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = iconUrl, onValueChange = { iconUrl = it }, label = { Text("Icon URL (Optional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { showAddPlayerAward = false }) { Text("Cancel") }
                    Button(onClick = {
                        viewModel.addPlayerAward(com.fctracker.career.PlayerAward(name = name, playerId = playerId, monthYear = monthYear, iconUrl = iconUrl))
                        showAddPlayerAward = false
                    }) { Text("Save") }
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(max=600.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text("Team Trophies", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                items(state.teamTrophies) { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏆", fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(t.name, color = Color.White)
                    }
                }
                item { Button(onClick = { showAddTeamTrophy = true }, modifier = Modifier.fillMaxWidth()) { Text("Add Team Trophy") } }
                
                item { Spacer(Modifier.height(16.dp)) }
                item { Text("Player Trophies & Awards", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                items(state.playerAwards) { a ->
                    val playerName = displayPlayers.find { it.id == a.playerId }?.displayName ?: "Unknown Player"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏅", fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(a.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("$playerName • ${a.monthYear}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
                item { Button(onClick = { showAddPlayerAward = true }, modifier = Modifier.fillMaxWidth()) { Text("Add Player Award") } }
                item { Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { TextButton(onClick = onDismiss) { Text("Close") } } }
            }
        }
    }
}



@Composable
fun StatBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OverviewStatsCard(state: AppState) {
    val viewingSeason = state.historicalSeasons.find { it.year == state.viewingSeasonYear }
    val displayMatches = if (viewingSeason != null) viewingSeason.matches ?: emptyList() else state.recentMatches
    val activeLeagueName = viewingSeason?.leagueName?.takeIf { it.isNotBlank() } ?: state.leagueName
    val leagueMatches = displayMatches.filter { it.competition == activeLeagueName || it.competition == state.leagueName }
    val matchesPlayed = if (viewingSeason != null && viewingSeason.matchesPlayed > 0) viewingSeason.matchesPlayed else leagueMatches.size
    var wins = 0
    var draws = 0
    var losses = 0
    var goalsFor = 0
    var goalsAgainst = 0
    
    val homeMatches = leagueMatches.filter { it.isHome }
    val awayMatches = leagueMatches.filter { !it.isHome }
    val homeWins = homeMatches.count { it.goalsFor > it.goalsAgainst }
    val homeDraws = homeMatches.count { it.goalsFor == it.goalsAgainst }
    val homeLosses = homeMatches.count { it.goalsFor < it.goalsAgainst }
    val awayWins = awayMatches.count { it.goalsFor > it.goalsAgainst }
    val awayDraws = awayMatches.count { it.goalsFor == it.goalsAgainst }
    val awayLosses = awayMatches.count { it.goalsFor < it.goalsAgainst }
    
    leagueMatches.forEach { match ->
        goalsFor += match.goalsFor
        goalsAgainst += match.goalsAgainst
        when {
            match.goalsFor > match.goalsAgainst -> wins++
            match.goalsFor < match.goalsAgainst -> losses++
            else -> draws++
        }
    }

    val finalWins = if (viewingSeason != null && viewingSeason.wins > 0) viewingSeason.wins else wins
    val finalDraws = if (viewingSeason != null && viewingSeason.draws > 0) viewingSeason.draws else draws
    val finalLosses = if (viewingSeason != null && viewingSeason.losses > 0) viewingSeason.losses else losses
    val finalGf = if (viewingSeason != null && viewingSeason.goalsFor > 0) viewingSeason.goalsFor else goalsFor
    val finalGa = if (viewingSeason != null && viewingSeason.goalsAgainst > 0) viewingSeason.goalsAgainst else goalsAgainst
    val points = if (viewingSeason != null && viewingSeason.points > 0) viewingSeason.points else ((finalWins * 3) + finalDraws)
    val goalDiff = finalGf - finalGa
    val position = if (viewingSeason != null && viewingSeason.finalPosition > 0) viewingSeason.finalPosition else (leagueMatches.maxByOrNull { it.matchDate }?.leaguePosition ?: 1)
    
    val last5 = displayMatches.sortedByDescending { it.matchDate }.take(5).reversed()
    
    DarkCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.fctracker.career.ui.screens.CompetitionLogoImage(
                competition = activeLeagueName,
                height = 48.dp,
                maxWidth = 80.dp,
                tile = false
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(activeLeagueName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(if (viewingSeason != null) "Final Position: $position" else "Current Position: $position", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatBox("PTS", points.toString())
            StatBox("MP", matchesPlayed.toString())
            StatBox("GD", if (goalDiff > 0) "+$goalDiff" else goalDiff.toString())
        }
        
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(16.dp))
        
        Text("Form & Statistics", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Last 5: ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            last5.forEach { match ->
                val badgeColor = when {
                    match.goalsFor > match.goalsAgainst -> com.fctracker.career.ui.theme.WinColor
                    match.goalsFor < match.goalsAgainst -> com.fctracker.career.ui.theme.LossColor
                    else -> com.fctracker.career.ui.theme.DrawColor
                }
                val badge = when {
                    match.goalsFor > match.goalsAgainst -> "W"
                    match.goalsFor < match.goalsAgainst -> "L"
                    else -> "D"
                }
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(badgeColor), contentAlignment = Alignment.Center) {
                    Text(badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(4.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Wins: $wins | Draws: $draws | Losses: $losses", color = Color.White)
                Row {
                    Text("Goals Scored: $goalsFor", color = Color.White)
                    Spacer(Modifier.width(16.dp))
                    Text("Goals Conceded: $goalsAgainst", color = Color.White)
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        Text("Home vs Away Performance", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DarkCard(modifier = Modifier.weight(1f)) {
                Text("Home", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("W: $homeWins  D: $homeDraws  L: $homeLosses", color = Color.White, fontSize = 12.sp)
                Text("GF: ${homeMatches.sumOf { it.goalsFor }}  GA: ${homeMatches.sumOf { it.goalsAgainst }}", color = Color.White, fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            DarkCard(modifier = Modifier.weight(1f)) {
                Text("Away", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("W: $awayWins  D: $awayDraws  L: $awayLosses", color = Color.White, fontSize = 12.sp)
                Text("GF: ${awayMatches.sumOf { it.goalsFor }}  GA: ${awayMatches.sumOf { it.goalsAgainst }}", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}
