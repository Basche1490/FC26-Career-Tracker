package com.fctracker.career.ui.screens
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*

import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.AppState
import com.fctracker.career.MainViewModel
import com.fctracker.career.Player
import com.fctracker.career.Objective
import com.fctracker.career.ui.theme.WinColor
import com.fctracker.career.ui.theme.DrawColor
import com.fctracker.career.ui.theme.LossColor

@Composable
fun DashboardScreen(viewModel: MainViewModel, onNavigate: (String) -> Unit, onMenuClick: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val viewingSeason = state.historicalSeasons.find { it.year == state.viewingSeasonYear }
    val displayMatches = viewingSeason?.matches?.takeIf { it.isNotEmpty() } ?: state.recentMatches
    val displayPlayers = viewingSeason?.players?.takeIf { it.isNotEmpty() } ?: state.players
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.fetchLiveTelemetry()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding).imePadding()
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            HeroBanner(state, onMenuClick, { 
                if (it == -1) viewModel.endSeason() else viewModel.setViewingSeasonYear(it) 
            })
            
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (viewingSeason != null && viewingSeason.transfers.isNotEmpty()) {
                    item { SeasonTopTransfersWidget(viewingSeason, state.currencySymbol) }
                }
                item { FinancialOverviewCard(state) }
                item { FormAndStanding(state, onNavigate) }
                item { SquadSummaryStats(state) }
                item { TopProspectsWidget(state) }
                item { ObjectivesWidget(state, viewModel) }
                item { LiveTelemetryWidget(state, viewModel) }
                item { QuickNavigationGrid(onNavigate) }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroBanner(state: AppState, onMenuClick: () -> Unit, onSeasonSelected: (Int?) -> Unit) {
    var seasonExpanded by remember { mutableStateOf(false) }
    var showNewSeasonDialog by remember { mutableStateOf(false) }
    
    if (showNewSeasonDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showNewSeasonDialog = false },
            title = { Text("Start New Season?") },
            text = { Text("This will archive the current season and begin a fresh one. Ensure you have played all matches before proceeding. Are you sure?") },
            confirmButton = {
                Button(onClick = {
                    showNewSeasonDialog = false
                    onSeasonSelected(-1)
                }) {
                    Text("Yes, Start New Season")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showNewSeasonDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    val seasons = mutableListOf<String>()
    state.historicalSeasons.forEach { 
        seasons.add("${it.year}/${it.year + 1} (Archived)")
    }
    seasons.add("${state.startYear}/${state.startYear + 1} (Current)")

    var selectedSeason by remember { mutableStateOf(seasons.last()) }

    val clubDomains = mapOf(
        "Arsenal" to "arsenal.com",
        "Aston Villa" to "avfc.co.uk",
        "Chelsea" to "chelseafc.com",
        "Everton" to "evertonfc.com",
        "Liverpool" to "liverpoolfc.com",
        "Manchester City" to "mancity.com",
        "Manchester United" to "manutd.com",
        "Newcastle United" to "nufc.co.uk",
        "Tottenham Hotspur" to "tottenhamhotspur.com",
        "Real Madrid" to "realmadrid.com",
        "Barcelona" to "fcbarcelona.com",
        "Atletico Madrid" to "atleticodemadrid.com",
        "Bayern Munich" to "fcbayern.com",
        "Borussia Dortmund" to "bvb.de",
        "Bayer Leverkusen" to "bayer04.de",
        "Juventus" to "juventus.com",
        "AC Milan" to "acmilan.com",
        "Inter Milan" to "inter.it",
        "Napoli" to "sscnapoli.it",
        "Roma" to "asroma.com",
        "Paris Saint-Germain" to "psg.fr",
        "Marseille" to "om.fr",
        "Lyon" to "ol.fr",
        "Ajax" to "ajax.nl",
        "PSV" to "psv.nl",
        "Feyenoord" to "feyenoord.com",
        "Benfica" to "slbenfica.pt",
        "Porto" to "fcporto.pt",
        "Sporting CP" to "sporting.pt",
        "Al Nassr" to "alnassr.sa",
        "Al Hilal" to "alhilal.com",
        "Al Ittihad" to "ittihadclub.sa",
        "Inter Miami" to "intermiamicf.com",
        "LA Galaxy" to "lagalaxy.com"
    )
    val cleanClubName = state.clubName.replace(" ", "").lowercase()
    val domain = clubDomains[state.clubName] ?: "$cleanClubName.com"
    val fallbackCrestUrl = if (state.clubCrestUrl.isNotEmpty()) state.clubCrestUrl else "https://icon.horse/icon/$domain"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            )
            .padding(20.dp)
            .statusBarsPadding()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .size(40.dp)
                ) {
                    Icon(Icons.Default.Menu, "Menu", tint = MaterialTheme.colorScheme.primary)
                }

                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { seasonExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        val displayYear = state.viewingSeasonYear ?: state.startYear
                        val suffix = if (state.viewingSeasonYear == null) " (Current)" else " (Archived)"
                        Text("${displayYear}/${displayYear + 1}$suffix", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(androidx.compose.material.icons.Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(
                        expanded = seasonExpanded,
                        onDismissRequest = { seasonExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Start New Season") },
                            onClick = {
                                showNewSeasonDialog = true
                                seasonExpanded = false
                            }
                        )
                        state.historicalSeasons.forEach { season ->
                            DropdownMenuItem(
                                text = { Text("${season.year}/${season.year + 1} (Archived)") },
                                onClick = {
                                    onSeasonSelected(season.year)
                                    seasonExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("${state.startYear}/${state.startYear + 1} (Current)") },
                            onClick = {
                                onSeasonSelected(null)
                                seasonExpanded = false
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    if (fallbackCrestUrl.isNotEmpty()) {
                        coil.compose.SubcomposeAsyncImage(
                            model = fallbackCrestUrl,
                            contentDescription = "Club Crest",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            loading = { CircularProgressIndicator(modifier = Modifier.size(24.dp)) },
                            error = {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        state.clubName.split(" ").mapNotNull { it.firstOrNull() }.joinToString("").take(2).uppercase(),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        )
                    } else {
                        Text(
                            state.clubName.split(" ").mapNotNull { it.firstOrNull() }.joinToString("").take(2).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Column {
                    Text(
                        state.clubName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "${state.leagueName} • ${state.country} • ${state.managerName}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun FinancialOverviewCard(state: AppState) {
    val viewingSeason = state.historicalSeasons.find { it.year == state.viewingSeasonYear }
    val displayMatches = viewingSeason?.matches?.takeIf { it.isNotEmpty() } ?: state.recentMatches
    val leagueMatches = displayMatches.filter { it.competition == state.leagueName }.sortedBy { it.matchDate }

    DarkCard(modifier = Modifier.fillMaxWidth()) {
        Text("League Position Tracker", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (leagueMatches.isEmpty()) {
            Text("No league matches recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val positions = leagueMatches.map { it.leaguePosition }
            val maxPos = (positions.maxOrNull() ?: 1).coerceAtLeast(20)
            
            var selectedDot by remember { mutableStateOf<Int?>(null) }
            
            Box(modifier = Modifier.fillMaxWidth().height(160.dp).pointerInput(Unit) {
                detectTapGestures { offset ->
                    val stepX = if (positions.size > 1) size.width / (positions.size - 1).toFloat() else size.width.toFloat()
                    val clickedIndex = (offset.x / stepX).roundToInt()
                    if (clickedIndex in positions.indices) {
                        val y = if (maxPos > 1) ((positions[clickedIndex] - 1).toFloat() / (maxPos - 1).toFloat()) * size.height else 0f
                        if (kotlin.math.abs(offset.y - y) < 40f) {
                            selectedDot = clickedIndex
                        } else {
                            selectedDot = null
                        }
                    } else {
                        selectedDot = null
                    }
                }
            }) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val stepX = if (positions.size > 1) size.width / (positions.size - 1).toFloat() else size.width
                    val path = androidx.compose.ui.graphics.Path()
                    
                    positions.forEachIndexed { index, pos ->
                        val y = if (maxPos > 1) ((pos - 1).toFloat() / (maxPos - 1).toFloat()) * size.height else 0f
                        val x = index * stepX
                        
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    
                    drawPath(
                        path = path,
                        color = WinColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )
                    
                    positions.forEachIndexed { index, pos ->
                        val y = if (maxPos > 1) ((pos - 1).toFloat() / (maxPos - 1).toFloat()) * size.height else 0f
                        val x = index * stepX
                        drawCircle(
                            color = if (selectedDot == index) Color.White else WinColor,
                            radius = if (selectedDot == index) 6.dp.toPx() else 4.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(x, y)
                        )
                    }
                }
                
                selectedDot?.let { index ->
                    val pos = positions[index]
                    val stepX = if (positions.size > 1) 1f / (positions.size - 1).toFloat() else 1f
                    val xFraction = index * stepX
                    
                    Box(modifier = Modifier.align(Alignment.TopStart).padding(top = 16.dp)) {
                        Text("MD ${index + 1}: Position $pos", color = Color.White, fontSize = 12.sp, modifier = Modifier.background(Color.Black.copy(alpha=0.7f), RoundedCornerShape(4.dp)).padding(4.dp))
                    }
                }
            }
        }
    }
}
@Composable
fun SquadSummaryStats(state: AppState) {

    val viewingSeason = state.historicalSeasons.find { it.year == state.viewingSeasonYear }
    val displayMatches = viewingSeason?.matches?.takeIf { it.isNotEmpty() } ?: state.recentMatches
    val displayPlayers = viewingSeason?.players?.takeIf { it.isNotEmpty() } ?: state.players
    val totalPlayers = displayPlayers.size
    val avgAge = displayPlayers.map { it.age }.average().takeIf { !it.isNaN() } ?: 0.0
    val avgOvr = displayPlayers.map { it.overall }.average().takeIf { !it.isNaN() } ?: 0.0
    val topScorer = displayPlayers.maxByOrNull { it.goals }
    val topAssister = displayPlayers.maxByOrNull { it.assists }

    DarkCard(modifier = Modifier.fillMaxWidth()) {
        Text("Squad Summary", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Size", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(totalPlayers.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Avg Age", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(String.format("%.1f", avgAge), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Avg OVR", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(String.format("%.1f", avgOvr), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha=0.5f))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Top Scorer", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${topScorer?.displayName ?: "N/A"} (${topScorer?.goals ?: 0})", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = WinColor)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Top Assister", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${topAssister?.displayName ?: "N/A"} (${topAssister?.assists ?: 0})", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = WinColor)
            }
        }
    }
}

@Composable
fun FormAndStanding(state: AppState, onNavigate: (String) -> Unit) {

    val viewingSeason = state.historicalSeasons.find { it.year == state.viewingSeasonYear }
    val displayMatches = viewingSeason?.matches?.takeIf { it.isNotEmpty() } ?: state.recentMatches
    val displayPlayers = viewingSeason?.players?.takeIf { it.isNotEmpty() } ?: state.players
    DarkCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recent Form", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Button(
                onClick = { onNavigate("matches") },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("View All", fontSize = 10.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            displayMatches.takeLast(5).forEach { match ->
                val color = when (match.resultBadge) {
                    "W" -> WinColor
                    "D" -> DrawColor
                    else -> LossColor
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(match.resultBadge, color = Color(0xFF1A1C18), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


@Composable
fun TopProspectsWidget(state: AppState) {
    val viewingSeason = state.historicalSeasons.find { it.year == state.viewingSeasonYear }
    val displayPlayers = viewingSeason?.players?.takeIf { it.isNotEmpty() } ?: state.players
    
    val prospects = displayPlayers.filter { it.age <= 22 }.sortedByDescending { it.overall }.take(5)
    
    if (prospects.isNotEmpty()) {
        DarkCard(modifier = Modifier.fillMaxWidth()) {
            Text("Top Prospects (U22)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                prospects.forEach { player ->
                    val earliestPlayer = state.historicalSeasons.sortedBy { it.year }.firstNotNullOfOrNull { s -> s.players.find { p -> p.id == player.id } }
                    val initialOvr = earliestPlayer?.startingOverall ?: player.startingOverall
                    val currentStartOvr = player.startingOverall
                    
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(player.name, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(0.3f))
                        
                        Box(modifier = Modifier.weight(0.7f).height(16.dp)) {
                            // Max width is based on max potential or 100
                            val maxOvr = 100f
                            val startWidth = (initialOvr.toFloat() / maxOvr)
                            val currentWidth = (currentStartOvr.toFloat() / maxOvr)
                            
                            // Background bar (initial overall)
                            Box(modifier = Modifier.fillMaxWidth(startWidth).fillMaxHeight().background(Color.Gray.copy(alpha=0.5f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)))
                            
                            // Foreground bar (current starting overall growth)
                            if (currentWidth > startWidth) {
                                Box(modifier = Modifier.fillMaxWidth(currentWidth).fillMaxHeight().background(WinColor.copy(alpha=0.8f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)))
                            } else if (currentWidth < startWidth) {
                                Box(modifier = Modifier.fillMaxWidth(currentWidth).fillMaxHeight().background(LossColor.copy(alpha=0.8f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)))
                            } else {
                                Box(modifier = Modifier.fillMaxWidth(currentWidth).fillMaxHeight().background(DrawColor.copy(alpha=0.8f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp)))
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${initialOvr} → ${currentStartOvr}", color = Color.White, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ObjectivesWidget(state: AppState, viewModel: MainViewModel) {
    var showAddObjective by remember { androidx.compose.runtime.mutableStateOf(false) }

    DarkCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Season Objectives", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            androidx.compose.material3.IconButton(onClick = { showAddObjective = true }, modifier = Modifier.size(24.dp)) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = "Add Objective", tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.objectives.isEmpty()) {
                Text("No objectives set.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            } else {
                state.objectives.forEach { obj ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statusColor = when(obj.status) {
                            "Completed" -> WinColor
                            "Failed" -> LossColor
                            else -> DrawColor
                        }
                        val priorityColor = when(obj.priority) {
                            "Critical" -> Color.Red
                            "High" -> Color(0xFFFFA500)
                            "Medium" -> Color.Yellow
                            "Low" -> Color.Blue
                            else -> Color.Gray
                        }
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(obj.description, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(obj.category, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(modifier = Modifier.background(priorityColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(obj.priority, fontSize = 8.sp, color = priorityColor)
                        }
                    }
                }
            }
        }
    }

    if (showAddObjective) {
        var objTitle by remember { mutableStateOf("") }
        var objPriority by remember { mutableStateOf("Medium") }
        var objCategory by remember { mutableStateOf("Domestic Success") }
        var objStatus by remember { mutableStateOf("In Progress") }
        
        var priorityExpanded by remember { mutableStateOf(false) }
        val priorities = listOf("Critical", "High", "Medium", "Low", "Very Low")
        
        var categoryExpanded by remember { mutableStateOf(false) }
        val categories = listOf("Domestic Success", "Continental Success", "Brand Exposure", "Financial", "Youth Development")
        
        var statusExpanded by remember { mutableStateOf(false) }
        val statuses = listOf("In Progress", "Completed", "Failed")

        androidx.compose.ui.window.Dialog(onDismissRequest = { showAddObjective = false }) {
            DarkCard(modifier = Modifier.fillMaxWidth()) {
                Text("Add Objective", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                
                androidx.compose.material3.ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = objCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { selectionOption ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    objCategory = selectionOption
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                
                androidx.compose.material3.OutlinedTextField(
                    value = objTitle,
                    onValueChange = { objTitle = it },
                    label = { Text("Objective Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.ExposedDropdownMenuBox(
                        expanded = priorityExpanded,
                        onExpandedChange = { priorityExpanded = !priorityExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        androidx.compose.material3.OutlinedTextField(
                            value = objPriority,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Priority") },
                            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded) },
                            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = priorityExpanded,
                            onDismissRequest = { priorityExpanded = false }
                        ) {
                            priorities.forEach { selectionOption ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        objPriority = selectionOption
                                        priorityExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    androidx.compose.material3.ExposedDropdownMenuBox(
                        expanded = statusExpanded,
                        onExpandedChange = { statusExpanded = !statusExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        androidx.compose.material3.OutlinedTextField(
                            value = objStatus,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Status") },
                            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = statusExpanded,
                            onDismissRequest = { statusExpanded = false }
                        ) {
                            statuses.forEach { selectionOption ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        objStatus = selectionOption
                                        statusExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.TextButton(onClick = { showAddObjective = false }) { Text("Cancel") }
                    androidx.compose.material3.Button(onClick = {
                        viewModel.addObjective(Objective(category = objCategory, priority = objPriority, description = objTitle, status = objStatus))
                        showAddObjective = false
                    }) { Text("Add") }
                }
            }
        }
    }
}

@Composable
fun QuickNavigationGrid(onNavigate: (String) -> Unit) {
    val navItems = listOf(
        "Squad" to Icons.Default.Groups,
        "Transfers" to Icons.Default.Sync,
        "Academy" to Icons.Default.School,
        "Matches" to Icons.Default.SportsSoccer,
        "Season" to Icons.Default.EmojiEvents,
        "Career" to Icons.Default.Timeline,
        "AI Assist" to Icons.Default.SmartToy,
        "Settings" to Icons.Default.Settings
    )
    
    val chunkedNavs = navItems.chunked(4)
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Quick Navigation", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
        chunkedNavs.forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { (label, icon) ->
                    val route = when(label) {
                        "Squad" -> "squad"
                        "Transfers" -> "transfers"
                        "Academy" -> "academy"
                        "Matches" -> "matches"
                        "Season" -> "season_summary"
                        "Career" -> "career_summary"
                        "AI Assist" -> "assistant"
                        "Settings" -> "settings"
                        else -> "dashboard"
                    }
                    DarkCard(
                        modifier = Modifier.weight(1f).aspectRatio(1f).clickable { onNavigate(route) }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(label, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeasonTopTransfersWidget(season: com.fctracker.career.HistoricalSeason, currencySymbol: String) {
    DarkCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Top Transfers (${season.year}/${season.year + 1})",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(10.dp))
        season.transfers.forEach { tr ->
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
                        if (tr.fromToClub.isNotBlank() && tr.fromToClub != "Transfer Market") {
                            Text(
                                text = "${if (isIncoming) "from" else "to"} ${tr.fromToClub}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    text = if (tr.feeM > 0) "$currencySymbol${tr.feeM}M" else "Free",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (!isIncoming) Color(0xFF81C784) else Color.White
                )
            }
        }
    }
}

@Composable
fun DarkCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}


@Composable
fun LiveTelemetryWidget(state: AppState, viewModel: MainViewModel) {
    var showConfigDialog by remember { mutableStateOf(false) }
    var bridgeUrlInput by remember(state.telemetryServerUrl) { mutableStateOf(state.telemetryServerUrl) }

    var connectionTestMsg by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }

    DarkCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Live Telemetry Feed", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    val isOnline = state.bridgeConnectionStatus.startsWith("Online") || state.bridgeConnectionStatus.startsWith("Connected")
                    val statusDotColor = if (isOnline) Color(0xFF10B981) else if (state.bridgeConnectionStatus == "Not Checked") Color(0xFF94A3B8) else Color(0xFFEF4444)
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(statusDotColor))
                    Text(
                        state.bridgeConnectionStatus,
                        fontSize = 9.sp,
                        color = statusDotColor,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
                Text(
                    "PC Bridge: ${state.telemetryServerUrl}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.IconButton(
                    onClick = { showConfigDialog = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.Settings,
                        contentDescription = "Configure Bridge",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                androidx.compose.material3.IconButton(
                    onClick = { viewModel.fetchLiveTelemetry() },
                    modifier = Modifier.size(28.dp)
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.liveEvents.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "No live events yet. Start the bridge on your PC (Start_Bridge.bat) and play matches in EA FC!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Text(
                        "Tap the gear icon above to verify PC IP address or test transmission.",
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )
                }
            } else {
                state.liveEvents.take(3).forEach { ev ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ev.title, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(ev.message, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showConfigDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text("Telemetry Bridge Setup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Direct local connection to your PC bridge (Start_Bridge.bat). Ensure both your PC and phone are on the same Wi-Fi.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = bridgeUrlInput,
                        onValueChange = { bridgeUrlInput = it },
                        label = { Text("Bridge IP & Port") },
                        placeholder = { Text("http://192.168.2.24:8080") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                isTestingConnection = true
                                connectionTestMsg = "Pinging bridge..."
                                viewModel.updateTelemetryServerUrl(bridgeUrlInput.trim())
                                viewModel.testBridgeConnection { success, msg ->
                                    isTestingConnection = false
                                    connectionTestMsg = msg
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(if (isTestingConnection) "Testing..." else "Ping Bridge", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.injectTestMatchEvent()
                                connectionTestMsg = "✅ Test match injected! Check Recent Matches."
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Simulate Match", fontSize = 12.sp)
                        }
                    }

                    if (connectionTestMsg != null) {
                        Text(
                            connectionTestMsg!!,
                            fontSize = 11.sp,
                            color = if (connectionTestMsg!!.startsWith("✅")) Color(0xFF10B981) else Color(0xFFEF4444),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("💡 VPN Notice:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFFBBF24))
                            Text(
                                "If your phone is on a VPN, enable 'LAN / Local Network Bypass' in your VPN app so your phone can reach your computer's local Wi-Fi IP address.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateTelemetryServerUrl(bridgeUrlInput.trim())
                    viewModel.fetchLiveTelemetry()
                    showConfigDialog = false
                }) {
                    Text("Save & Connect")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showConfigDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
