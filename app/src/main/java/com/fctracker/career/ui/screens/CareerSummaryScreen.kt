package com.fctracker.career.ui.screens
import com.fctracker.career.feeLabel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.fctracker.career.Player
import com.fctracker.career.ui.theme.WinColor

@Composable
fun CareerSummaryScreen(viewModel: MainViewModel, onMenuClick: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var showGraphicCard by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    // Calculate aggregated all-time stats
    var filterSeasonYearHoF by remember { mutableStateOf<Int?>(null) }
    var filterSeasonYearMilestones by remember { mutableStateOf<Int?>(null) }
    var filterExpandedHoF by remember { mutableStateOf(false) }
    var filterExpandedMilestones by remember { mutableStateOf(false) }
    
    var sortMode by remember { mutableStateOf("Rating") } // "Name", "Apps", "Goals", "Assists", "Rating"

    val displayStatsHoF = remember(state.players, state.historicalSeasons, filterSeasonYearHoF) {
        val nonAcademyCurrent = state.players.filter { !it.isAcademy && !it.status.equals("Academy", ignoreCase = true) }
        val rawList = if (filterSeasonYearHoF == null) {
            val map = mutableMapOf<String, com.fctracker.career.Player>()
            nonAcademyCurrent.forEach { map[it.id] = it.copy() }
            state.historicalSeasons.forEach { season ->
                season.players.filter { !it.isAcademy && !it.status.equals("Academy", ignoreCase = true) }.forEach { hp ->
                    val current = map[hp.id]
                    if (current != null) {
                        map[hp.id] = current.copy(
                            matchesPlayed = current.matchesPlayed + hp.matchesPlayed,
                            goals = current.goals + hp.goals,
                            assists = current.assists + hp.assists,
                            averageRating = if (current.averageRating == 0.0) hp.averageRating else if (hp.averageRating == 0.0) current.averageRating else (current.averageRating + hp.averageRating) / 2
                        )
                    } else {
                        map[hp.id] = hp.copy()
                    }
                }
            }
            map.values.toList()
        } else if (filterSeasonYearHoF == state.startYear) {
            nonAcademyCurrent
        } else {
            state.historicalSeasons.find { it.year == filterSeasonYearHoF }?.players?.filter { !it.isAcademy && !it.status.equals("Academy", ignoreCase = true) } ?: emptyList()
        }
        rawList.filter { !it.isAcademy && !it.status.equals("Academy", ignoreCase = true) }
    }
    
    val displayStatsMilestones = remember(state.players, state.historicalSeasons, filterSeasonYearMilestones) {
        val nonAcademyCurrent = state.players.filter { !it.isAcademy && !it.status.equals("Academy", ignoreCase = true) }
        val rawList = if (filterSeasonYearMilestones == null) {
            val map = mutableMapOf<String, com.fctracker.career.Player>()
            nonAcademyCurrent.forEach { map[it.id] = it.copy() }
            state.historicalSeasons.forEach { season ->
                season.players.filter { !it.isAcademy && !it.status.equals("Academy", ignoreCase = true) }.forEach { hp ->
                    val current = map[hp.id]
                    if (current != null) {
                        map[hp.id] = current.copy(
                            matchesPlayed = current.matchesPlayed + hp.matchesPlayed,
                            goals = current.goals + hp.goals,
                            assists = current.assists + hp.assists,
                            averageRating = if (current.averageRating == 0.0) hp.averageRating else if (hp.averageRating == 0.0) current.averageRating else (current.averageRating + hp.averageRating) / 2
                        )
                    } else {
                        map[hp.id] = hp.copy()
                    }
                }
            }
            map.values.toList()
        } else if (filterSeasonYearMilestones == state.startYear) {
            nonAcademyCurrent
        } else {
            state.historicalSeasons.find { it.year == filterSeasonYearMilestones }?.players?.filter { !it.isAcademy && !it.status.equals("Academy", ignoreCase = true) } ?: emptyList()
        }
        rawList.filter { !it.isAcademy && !it.status.equals("Academy", ignoreCase = true) }
    }
    val allTimeStats = remember(state.players, state.historicalSeasons) {
        val map = mutableMapOf<String, com.fctracker.career.Player>()
        // Initialize with current stats (excluding academy)
        state.players.filter { !it.isAcademy && !it.status.equals("Academy", ignoreCase = true) }.forEach { p ->
            map[p.id] = p.copy() // Start with current baseline
        }
        
        // Add historical stats (excluding academy)
        state.historicalSeasons.forEach { season ->
            season.players.filter { !it.isAcademy && !it.status.equals("Academy", ignoreCase = true) }.forEach { hp ->
                val current = map[hp.id]
                if (current != null) {
                    map[hp.id] = current.copy(
                        matchesPlayed = current.matchesPlayed + hp.matchesPlayed,
                        goals = current.goals + hp.goals,
                        assists = current.assists + hp.assists,
                    )
                } else {
                    map[hp.id] = hp.copy()
                }
            }
        }
        map.values.filter { !it.isAcademy && !it.status.equals("Academy", ignoreCase = true) }
    }



    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Career Summary", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
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
        var selectedPlayer by remember { mutableStateOf<Player?>(null) }
        selectedPlayer?.let {
            DetailedPlayerStatsModal(state = state, player = it, showOvrGrowth = true, onDismiss = { selectedPlayer = null })
        }

        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    DarkCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Season-by-Season Career Timeline", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))

                        if (state.historicalSeasons.isEmpty()) {
                            Text("No completed seasons yet. Completed seasons and their top transfers will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            state.historicalSeasons.sortedBy { it.year }.forEach { season ->
                                DarkCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "${season.year}/${season.year + 1} • ${season.leagueName}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = Color.White
                                            )
                                            val positionSuffix = when (season.finalPosition) {
                                                1 -> "1st (Champions 🏆)"
                                                2 -> "2nd (Promoted 🥈)"
                                                3 -> "3rd (Promoted 🥉)"
                                                else -> "${season.finalPosition}th"
                                            }
                                            Text(
                                                text = "Finished $positionSuffix",
                                                fontSize = 12.sp,
                                                color = if (season.finalPosition == 1) Color(0xFFFFD700) else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    // Top Transfers for this specific past season
                                    if (season.transfers.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            "Top Transfers & Key Deals",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))

                                        season.transfers.forEach { tr ->
                                            val isIncoming = tr.type.contains("In", ignoreCase = true) || tr.type.equals("Bought", ignoreCase = true)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 3.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        tr.playerName,
                                                        fontSize = 13.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    if (tr.fromToClub.isNotBlank() && tr.fromToClub != "Transfer Market") {
                                                        Text(
                                                            text = " (${tr.fromToClub})",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = tr.feeLabel(state.currencySymbol),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (!isIncoming) Color(0xFF81C784) else Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
                item {
                    DarkCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Overall Manager Statistics", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))
                        

                        val totalMatches = state.recentMatches.size
                        val wins = state.recentMatches.count { it.resultBadge == "W" }
                        val draws = state.recentMatches.count { it.resultBadge == "D" }
                        val losses = state.recentMatches.count { it.resultBadge == "L" }
                        val leagueTitles = state.teamTrophies.count { it.name.contains("League", ignoreCase = true) }
                        val domesticCups = state.teamTrophies.count { it.name.contains("Cup", ignoreCase = true) && !it.name.contains("Champions", ignoreCase = true) }
                        val continental = state.teamTrophies.count { it.name.contains("Champions", ignoreCase = true) || it.name.contains("Europa", ignoreCase = true) }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = leagueTitles.toString(), onValueChange = {}, label = { Text("League Titles") }, modifier = Modifier.weight(1f), readOnly = true)
                            OutlinedTextField(value = domesticCups.toString(), onValueChange = {}, label = { Text("Domestic Cups") }, modifier = Modifier.weight(1f), readOnly = true)
                            OutlinedTextField(value = continental.toString(), onValueChange = {}, label = { Text("Continental") }, modifier = Modifier.weight(1f), readOnly = true)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = wins.toString(), onValueChange = {}, label = { Text("Wins") }, modifier = Modifier.weight(1f), readOnly = true)
                            OutlinedTextField(value = draws.toString(), onValueChange = {}, label = { Text("Draws") }, modifier = Modifier.weight(1f), readOnly = true)
                            OutlinedTextField(value = losses.toString(), onValueChange = {}, label = { Text("Losses") }, modifier = Modifier.weight(1f), readOnly = true)
                        }
                    }
                }
                

                item {
                    val allMatches = state.historicalSeasons.flatMap { it.matches } + state.recentMatches
                    val homeMatches = allMatches.filter { it.isHome }
                    val awayMatches = allMatches.filter { !it.isHome }
                    
                    val homeWins = homeMatches.count { it.goalsFor > it.goalsAgainst }
                    val homeDraws = homeMatches.count { it.goalsFor == it.goalsAgainst }
                    val homeLosses = homeMatches.count { it.goalsFor < it.goalsAgainst }
                    val awayWins = awayMatches.count { it.goalsFor > it.goalsAgainst }
                    val awayDraws = awayMatches.count { it.goalsFor == it.goalsAgainst }
                    val awayLosses = awayMatches.count { it.goalsFor < it.goalsAgainst }

                    DarkCard(modifier = Modifier.fillMaxWidth()) {
                        Text("All-Time Home vs Away Performance", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                item {
                    DarkCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Hall of Fame (Best Players)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Box {
                                androidx.compose.material3.TextButton(onClick = { filterExpandedHoF = true }) {
                                    Text(if (filterSeasonYearHoF == null) "All-Time" else if (filterSeasonYearHoF == state.startYear) "${state.startYear}/${state.startYear+1} (Current)" else "${filterSeasonYearHoF}/${filterSeasonYearHoF!!+1}")
                                    Text("▼", modifier = Modifier.padding(start=4.dp))
                                }
                                DropdownMenu(expanded = filterExpandedHoF, onDismissRequest = { filterExpandedHoF = false }) {
                                    DropdownMenuItem(text = { Text("All-Time") }, onClick = { filterSeasonYearHoF = null; filterExpandedHoF = false })
                                    DropdownMenuItem(text = { Text("${state.startYear}/${state.startYear+1} (Current)") }, onClick = { filterSeasonYearHoF = state.startYear; filterExpandedHoF = false })
                                    state.historicalSeasons.forEach { s ->
                                        DropdownMenuItem(text = { Text("${s.year}/${s.year+1}") }, onClick = { filterSeasonYearHoF = s.year; filterExpandedHoF = false })
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Name" + if(sortMode=="Name") "↓" else "", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f).clickable { sortMode = "Name" })
                            Text("Apps" + if(sortMode=="Apps") "↓" else "", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable { sortMode = "Apps" })
                            Text("Gls" + if(sortMode=="Goals") "↓" else "", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable { sortMode = "Goals" })
                            Text("Ast" + if(sortMode=="Assists") "↓" else "", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable { sortMode = "Assists" })
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val topPlayers = displayStatsHoF.sortedWith(compareByDescending<com.fctracker.career.Player> { when(sortMode) { "Apps" -> it.matchesPlayed.toDouble(); "Goals" -> it.goals.toDouble(); "Assists" -> it.assists.toDouble(); "Name" -> -it.displayName.hashCode().toDouble(); else -> it.averageRating } }).take(5)
                        topPlayers.forEach { player ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(2f)) {
                                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                        if (isValidPlayerHeadshot(player.headshotUrl)) {
                                            coil.compose.SubcomposeAsyncImage(
                                                model = player.headshotUrl,
                                                contentDescription = player.displayName,
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
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${player.displayName}", color = Color.White, fontSize = 12.sp)
                                }
                                Text("${player.matchesPlayed}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Text("${player.goals}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Text("${player.assists}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
                item {
                    DarkCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Club Milestones & Record Holders", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Box {
                                androidx.compose.material3.TextButton(onClick = { filterExpandedMilestones = true }) {
                                    Text(if (filterSeasonYearMilestones == null) "All-Time" else if (filterSeasonYearMilestones == state.startYear) "${state.startYear}/${state.startYear+1} (Current)" else "${filterSeasonYearMilestones}/${filterSeasonYearMilestones!!+1}")
                                    Text("▼", modifier = Modifier.padding(start=4.dp))
                                }
                                DropdownMenu(expanded = filterExpandedMilestones, onDismissRequest = { filterExpandedMilestones = false }) {
                                    DropdownMenuItem(text = { Text("All-Time") }, onClick = { filterSeasonYearMilestones = null; filterExpandedMilestones = false })
                                    DropdownMenuItem(text = { Text("${state.startYear}/${state.startYear+1} (Current)") }, onClick = { filterSeasonYearMilestones = state.startYear; filterExpandedMilestones = false })
                                    state.historicalSeasons.forEach { s ->
                                        DropdownMenuItem(text = { Text("${s.year}/${s.year+1}") }, onClick = { filterSeasonYearMilestones = s.year; filterExpandedMilestones = false })
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val mostApps = displayStatsMilestones.maxByOrNull { it.matchesPlayed }
                        val topScorer = displayStatsMilestones.maxByOrNull { it.goals }
                        val topAssists = displayStatsMilestones.maxByOrNull { it.assists }
                        val highestAvg = displayStatsMilestones.filter { it.matchesPlayed > 0 }.maxByOrNull { it.averageRating }
                        
                        val allTransfersPool = remember(state.transfers, state.historicalSeasons) {
                            state.transfers + state.historicalSeasons.flatMap { it.transfers }
                        }
                        val recordSigning = allTransfersPool.filter { it.type.contains("In", ignoreCase = true) || it.type.equals("Bought", ignoreCase = true) }.maxByOrNull { it.feeM }
                        val recordSale = allTransfersPool.filter { it.type.contains("Out", ignoreCase = true) || it.type.equals("Sold", ignoreCase = true) }.maxByOrNull { it.feeM }
                        
                        com.fctracker.career.ui.screens.RecordCardWithImage("Most Appearances", mostApps?.displayName ?: "-", mostApps?.matchesPlayed?.toString() ?: "0", mostApps?.headshotUrl ?: "")
                        Spacer(Modifier.height(8.dp))
                        com.fctracker.career.ui.screens.RecordCardWithImage("All-Time Top Scorer", topScorer?.displayName ?: "-", topScorer?.goals?.toString() ?: "0", topScorer?.headshotUrl ?: "")
                        Spacer(Modifier.height(8.dp))
                        com.fctracker.career.ui.screens.RecordCardWithImage("All-Time Top Assists", topAssists?.displayName ?: "-", topAssists?.assists?.toString() ?: "0", topAssists?.headshotUrl ?: "")
                        Spacer(Modifier.height(8.dp))
                        RecordCard("Record Signing", recordSigning?.playerName ?: "-", state.currencySymbol + (recordSigning?.feeM?.toString() ?: "0") + "M")
                        Spacer(Modifier.height(8.dp))
                        RecordCard("Record Sale", recordSale?.playerName ?: "-", state.currencySymbol + (recordSale?.feeM?.toString() ?: "0") + "M")
                    }
                }
                item {
                    DarkCard(modifier = Modifier.fillMaxWidth()) {
                        Text("All-Time Squad List", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))
                        allTimeStats.forEach { player ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedPlayer = player },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(32.dp).clip(CircleShape).background(if (player.status == "Sold" || player.status == "Loaned Out") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isValidPlayerHeadshot(player.headshotUrl)) {
                                            coil.compose.SubcomposeAsyncImage(
                                                model = player.headshotUrl,
                                                contentDescription = player.displayName,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                error = {
                                                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                }
                                            )
                                        } else {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        val isPromotedActive = player.isPromoted && (player.promotedSeasonYear == null || (state.startYear - (player.promotedSeasonYear ?: state.startYear)) < 3)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(player.displayName, color = Color.White, fontSize = 14.sp)
                                            if (isPromotedActive) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFF047857), RoundedCornerShape(4.dp))
                                                        .border(0.5.dp, Color(0xFF6EE7B7), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text("PROMOTED", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                        Text(player.status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                    }
                                }
                                Text("OVR ${player.overall}", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
                item {
                    Button(onClick = { /* Export */ }, modifier = Modifier.fillMaxWidth()) {
                        Text("Export Career Summary Text")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showGraphicCard = true }, modifier = Modifier.fillMaxWidth()) { Text("Generate Career Graphic Card") }
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }

    if (showGraphicCard) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showGraphicCard = false }) {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    .padding(2.dp)
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                        .padding(24.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.Text("CAREER SUMMARY", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.Text(state.managerName.uppercase(), fontSize = 24.sp, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                    androidx.compose.material3.Text("${state.clubName} Manager", fontSize = 16.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.seasonRecord, fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("All-Time Record", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${state.currencySymbol}${state.squadValue}M", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Squad Value", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { showGraphicCard = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Close")
                    }
                }
            }
        }
    }

}

@Composable
fun RecordCard(title: String, holder: String, value: String) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(holder, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}


