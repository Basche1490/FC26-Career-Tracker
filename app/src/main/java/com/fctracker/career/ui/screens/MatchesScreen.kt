package com.fctracker.career.ui.screens

import androidx.compose.foundation.background
import coil.compose.AsyncImage
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fctracker.career.AppState
import com.fctracker.career.MainViewModel
import com.fctracker.career.MatchResult
import com.fctracker.career.PlayerPerformance
import com.fctracker.career.ui.theme.DrawColor
import com.fctracker.career.ui.theme.LossColor
import com.fctracker.career.ui.theme.WinColor
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(viewModel: MainViewModel, onMenuClick: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var showMatchForm by remember { mutableStateOf(false) }
    var matchToEdit by remember { mutableStateOf<MatchResult?>(null) }
    var selectedCompetitionFilter by remember { mutableStateOf("All") }

    
    // Calendar Logic
    val startYear = state.startYear
    val startCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, startYear)
        set(Calendar.MONTH, Calendar.JULY)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val endCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, startYear + 1)
        set(Calendar.MONTH, Calendar.JUNE)
        set(Calendar.DAY_OF_MONTH, 30)
    }
    
    // Determine active month based on the latest match, falling back to start month
        val displayMatches = if (state.viewingSeasonYear != null) {
        state.historicalSeasons.find { it.year == state.viewingSeasonYear }?.matches ?: emptyList()
    } else {
        state.recentMatches
    }
    val isArchived = state.viewingSeasonYear != null

    val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val monthFmt = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    
    var currentMonthCal by remember(displayMatches.size) { 
        mutableStateOf(
            if (displayMatches.isNotEmpty()) {
                val latest = displayMatches.first().matchDate
                try {
                    val d = df.parse(latest)
                    if (d != null) {
                        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = d; set(Calendar.DAY_OF_MONTH, 1) }
                    } else {
                        startCal.clone() as Calendar
                    }
                } catch(e: Exception) {
                    startCal.clone() as Calendar
                }
            } else {
                startCal.clone() as Calendar
            }
        ) 
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, "Menu", tint = MaterialTheme.colorScheme.primary)
                }
                Text("Matches & Fixtures", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Box(modifier = Modifier.size(48.dp))
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("Fixture Calendar", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                
                item {
                    FixtureCalendar(
                        currentMonthCal = currentMonthCal,
                        onMonthChange = { newCal -> currentMonthCal = newCal },
                        startCal = startCal,
                        endCal = endCal,
                        matches = displayMatches,
                        onDateClick = { m -> matchToEdit = m; showMatchForm = true }
                    )
                }
                
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Match History", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        val allComps = listOf("All") + displayMatches.map { it.competition }.distinct().sorted()
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text(selectedCompetitionFilter, color = MaterialTheme.colorScheme.primary)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Filter", tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                allComps.forEach { comp ->
                                    DropdownMenuItem(
                                        text = { Text(comp) },
                                        onClick = { selectedCompetitionFilter = comp; expanded = false }
                                    )
                                }
                            }
                        }
                    }
                }
                
                items(displayMatches.filter { selectedCompetitionFilter == "All" || it.competition == selectedCompetitionFilter }) { match ->
                    MatchResultCard(
                        match = match, 
                        state = state,
                        onEdit = { if(!isArchived) { matchToEdit = match; showMatchForm = true } },
                        onDelete = { if(!isArchived) viewModel.deleteMatchResult(match.id) }
                    )
                }
                if (displayMatches.filter { selectedCompetitionFilter == "All" || it.competition == selectedCompetitionFilter }.isEmpty()) {
                    item { Text("No matches played yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
        
        if (!isArchived) {
            FloatingActionButton(
                onClick = { matchToEdit = null; showMatchForm = true },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, "Log Match", tint = MaterialTheme.colorScheme.onPrimary)
        }
        }
        
        if (showMatchForm) {
            MatchFormModal(
                viewModel = viewModel, 
                state = state, 
                initialMatch = matchToEdit,
                onDismiss = { showMatchForm = false }, 
                onSave = { match -> 
                    if (matchToEdit != null) viewModel.updateMatchResult(match) 
                    else viewModel.addMatchResult(match)
                    showMatchForm = false 
                }
            )
        }
    }
}

@Composable
fun FixtureCalendar(currentMonthCal: Calendar, onMonthChange: (Calendar) -> Unit, startCal: Calendar, endCal: Calendar, matches: List<MatchResult>,
    onDateClick: (MatchResult) -> Unit
) {
    val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    val matchesByDate = matches.groupBy { it.matchDate }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    val prev = currentMonthCal.clone() as Calendar
                    prev.add(Calendar.MONTH, -1)
                    if (prev.timeInMillis >= startCal.timeInMillis || (prev.get(Calendar.YEAR) == startCal.get(Calendar.YEAR) && prev.get(Calendar.MONTH) == startCal.get(Calendar.MONTH))) {
                        onMonthChange(prev)
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Prev Month", tint = Color.White)
                }
                Text(monthFmt.format(currentMonthCal.time), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                IconButton(onClick = {
                    val next = currentMonthCal.clone() as Calendar
                    next.add(Calendar.MONTH, 1)
                    if (next.timeInMillis <= endCal.timeInMillis || (next.get(Calendar.YEAR) == endCal.get(Calendar.YEAR) && next.get(Calendar.MONTH) == endCal.get(Calendar.MONTH))) {
                        onMonthChange(next)
                    }
                }) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Next Month", tint = Color.White)
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Days of week
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            val cal = currentMonthCal.clone() as Calendar
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 for Sun
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            
            val totalCells = firstDayOfWeek + daysInMonth
            val rows = Math.ceil(totalCells / 7.0).toInt()
            
            Column {
                for (r in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (c in 0 until 7) {
                            val cellIndex = r * 7 + c
                            val day = cellIndex - firstDayOfWeek + 1
                            if (day in 1..daysInMonth) {
                                val dateStr = String.format("%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, day)
                                val dayMatches = matchesByDate[dateStr] ?: emptyList()
                                
                                val matchOnDay = dayMatches.firstOrNull()
                                val dayColor = if (matchOnDay != null) {
                                    when (matchOnDay.resultBadge) {
                                        "W" -> com.fctracker.career.ui.theme.WinColor
                                        "D" -> com.fctracker.career.ui.theme.DrawColor
                                        else -> com.fctracker.career.ui.theme.LossColor
                                    }
                                } else { Color.Transparent }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(dayColor)
                                        .clickable(enabled = matchOnDay != null) {
                                            if (matchOnDay != null) onDateClick(matchOnDay)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = day.toString(), 
                                        color = if (dayMatches.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchResultCard(match: MatchResult, state: AppState?, onEdit: () -> Unit, onDelete: () -> Unit) {
    val badgeColor = when (match.resultBadge) {
        "W" -> com.fctracker.career.ui.theme.WinColor
        "D" -> com.fctracker.career.ui.theme.DrawColor
        else -> com.fctracker.career.ui.theme.LossColor
    }
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable { onEdit() }
            .padding(16.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    CompetitionLogoImage(
                        competition = match.competition, 
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White, CircleShape)
                            .padding(2.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    TeamCrestImage(teamName = match.opponentName, modifier = Modifier.size(36.dp).clip(CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        val homeAwayStr = if (match.isHome) "(H)" else "(A)"
                        Text("$homeAwayStr vs ${match.opponentName}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        val stagePart = if (match.stage.isNotBlank()) " • ${match.stage}" else ""
                        Text("${match.competition}$stagePart • ${match.matchDate}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${match.goalsFor} - ${match.goalsAgainst}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = badgeColor)
                            if (match.isExtraTime) {
                                Spacer(Modifier.width(4.dp))
                                Text("AET", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (match.homePenalties > 0 || match.awayPenalties > 0) {
                            Text("(${match.homePenalties} - ${match.awayPenalties} pens)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // MOTM & Match Key Stats Row
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (match.motmName.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF332B00))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = "MOTM", tint = Color(0xFFFFD700), modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (match.motmIsOpponent) "MOTM: ${match.motmName} (${match.opponentName})" else "MOTM: ${match.motmName}",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700)
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                // Match stats chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (match.possessionEstimated) "Poss: ~${match.possession}%" else "Poss: ${match.possession}%",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("•", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Shots: ${match.shotsFor}-${match.shotsAgainst}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (match.expectedGoals > 0.0 || match.expectedGoalsAgainst > 0.0) {
                        Text("•", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("xG: ${String.format(Locale.US, "%.1f", match.expectedGoals)}-${String.format(Locale.US, "%.1f", match.expectedGoalsAgainst)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (match.statsEstimated) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "ⓘ Estimated stats: the game did not send full match detail, so line-up, ratings and team numbers are typical values. Edit the match to correct them.",
                    fontSize = 10.sp,
                    color = Color(0xFFF59E0B)
                )
            }

            // Goal Details (Scorers)
            if (match.goalDetails.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    val ourGoals = match.goalDetails.filter { it.scorerId != null }
                    val oppGoals = match.goalDetails.filter { it.scorerId == null }

                    if (ourGoals.isNotEmpty()) {
                        val scorersSummary = ourGoals.joinToString(", ") { g ->
                            val pName = state?.players?.find { it.id == g.scorerId }?.displayName ?: g.scorerId ?: "Player"
                            val minStr = if (g.minute != null) " ${g.minute}'" else ""
                            val astStr = if (!g.assisterId.isNullOrBlank()) {
                                val aName = state?.players?.find { it.id == g.assisterId }?.displayName ?: g.assisterId
                                " (ast. $aName)"
                            } else ""
                            "$pName$minStr$astStr"
                        }
                        Text("⚽ $scorersSummary", fontSize = 11.sp, color = Color.White)
                    }

                    if (oppGoals.isNotEmpty()) {
                        val oppScorers = oppGoals.joinToString(", ") { g ->
                            val minStr = if (g.minute != null) " ${g.minute}'" else ""
                            "${match.opponentName}$minStr"
                        }
                        Text("⚽ (Opponent) $oppScorers", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Expandable Lineup & Player Ratings Toggle
            if (match.playerPerformances.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (expanded) "Hide Match Lineup & Stats" else "View Lineup & Player Stats (${match.playerPerformances.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (expanded) {
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        match.playerPerformances.forEach { perf ->
                            val player = state?.players?.find { it.id == perf.playerId || it.displayName == perf.playerId || it.name == perf.playerId }
                            val pName = player?.displayName ?: perf.playerName?.takeIf { it.isNotBlank() } ?: perf.playerId
                            val pPos = player?.primaryPosition ?: ""
                            val ratingColor = when {
                                perf.rating >= 8.0 -> Color(0xFF4CAF50)
                                perf.rating >= 7.0 -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    // Rating Badge
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(ratingColor.copy(alpha = 0.2f))
                                            .border(1.dp, ratingColor, RoundedCornerShape(6.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (perf.rating > 0) String.format(Locale.US, "%.1f", perf.rating) else "–",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ratingColor
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(pName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            if (perf.isMotm) {
                                                Spacer(Modifier.width(4.dp))
                                                Icon(Icons.Default.Star, "MOTM", tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                                            }
                                            if (pPos.isNotBlank()) {
                                                Spacer(Modifier.width(4.dp))
                                                Text(pPos, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }

                                        // Detailed Stats Row
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (perf.goals > 0) Text("⚽ ${perf.goals}", fontSize = 10.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                            if (perf.assists > 0) Text("🅰️ ${perf.assists}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            if (perf.passes > 0) Text("${perf.passes} pass (${perf.passAccuracy}%)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (perf.shots > 0) Text("${perf.shots} sh", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (perf.tackles > 0) Text("${perf.tackles} tkl", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (perf.saves > 0) Text("${perf.saves} saves", fontSize = 10.sp, color = Color(0xFF00E676))
                                            if (perf.cleanSheet) Text("🧤 CS", fontSize = 10.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                                            if (perf.yellowCard) Text("🟨", fontSize = 10.sp)
                                            if (perf.redCard) Text("🟥", fontSize = 10.sp)
                                        }
                                    }
                                }

                                if (perf.subOutMinute != null) {
                                    Text("🔻 ${perf.subOutMinute}'", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else if (perf.subInMinute != null) {
                                    Text("🔺 ${perf.subInMinute}'", fontSize = 10.sp, color = Color(0xFF4CAF50))
                                }
                            }
                        }

                        // Substitutions if any
                        if (match.substitutions.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), thickness = 0.5.dp)
                            match.substitutions.forEach { sub ->
                                val pOut = state?.players?.find { it.id == sub.playerOutId }?.displayName
                                    ?: sub.playerOutName?.takeIf { it.isNotBlank() } ?: sub.playerOutId
                                val pIn = state?.players?.find { it.id == sub.playerInId }?.displayName
                                    ?: sub.playerInName?.takeIf { it.isNotBlank() } ?: sub.playerInId
                                Text(
                                    "🔄 ${sub.minute}': 🔺 $pIn for 🔻 $pOut",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getCompetitionsForCountry(country: String): List<String> {
    return when(country) {
        "England" -> listOf("Premier League", "Championship", "FA Cup", "Carabao Cup", "Champions League", "Europa League", "Conference League", "Community Shield", "Friendly")
        "Spain" -> listOf("La Liga", "Segunda División", "Copa del Rey", "Supercopa de España", "Champions League", "Europa League", "Conference League", "Friendly")
        "Italy" -> listOf("Serie A", "Serie B", "Coppa Italia", "Supercoppa Italiana", "Champions League", "Europa League", "Conference League", "Friendly")
        "Germany" -> listOf("Bundesliga", "2. Bundesliga", "DFB-Pokal", "DFL-Supercup", "Champions League", "Europa League", "Conference League", "Friendly")
        "France" -> listOf("Ligue 1", "Ligue 2", "Coupe de France", "Trophée des Champions", "Champions League", "Europa League", "Conference League", "Friendly")
        else -> listOf("League", "Domestic Cup", "Continental", "Friendly")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchFormModal(viewModel: MainViewModel, state: AppState, initialMatch: MatchResult?, onDismiss: () -> Unit, onSave: (MatchResult) -> Unit) {
    var matchDate by remember { mutableStateOf(initialMatch?.matchDate ?: "") }

    var opponent by remember { mutableStateOf(initialMatch?.opponentName ?: "") }
    var opponentCrestUrl by remember { mutableStateOf(initialMatch?.opponentCrestUrl ?: "") }
    var competition by remember { mutableStateOf(initialMatch?.competition ?: "League") }
    var stage by remember { mutableStateOf(initialMatch?.stage ?: "") }
    
    // Auto-increment stage
    val isLeague = competition.contains("League", ignoreCase = true) || competition.contains("Division", ignoreCase = true) || competition.contains("Championship", ignoreCase = true) || competition == "Serie A" || competition == "La Liga" || competition == "Bundesliga" || competition == "Ligue 1"
    LaunchedEffect(competition) {
        if (initialMatch == null && isLeague) {
            val lastLeagueMatch = state.recentMatches.reversed().find { 
                val c = it.competition
                c.contains("League", true) || c.contains("Division", true) || c.contains("Championship", true) || c == "Serie A" || c == "La Liga" || c == "Bundesliga" || c == "Ligue 1"
            }
            if (lastLeagueMatch != null) {
                val lastStage = lastLeagueMatch.stage
                val number = Regex("\\d+").find(lastStage)?.value?.toIntOrNull()
                if (number != null) {
                    stage = "Matchday ${number + 1}"
                } else {
                    stage = "Matchday 1"
                }
            } else {
                stage = "Matchday 1"
            }
        }
    }

    var isHome by remember { mutableStateOf(initialMatch?.isHome ?: true) }
    
    var goalsFor by remember { mutableStateOf(initialMatch?.goalsFor?.toString() ?: "0") }
    var goalsAgainst by remember { mutableStateOf(initialMatch?.goalsAgainst?.toString() ?: "0") }
    var isExtraTime by remember { mutableStateOf(initialMatch?.isExtraTime ?: false) }
    var homePenalties by remember { mutableStateOf(initialMatch?.homePenalties?.toString() ?: "0") }
    var awayPenalties by remember { mutableStateOf(initialMatch?.awayPenalties?.toString() ?: "0") }

    var possession by remember { mutableStateOf(initialMatch?.possession?.toString() ?: "50") }
    var passingAccuracy by remember { mutableStateOf(initialMatch?.passingAccuracy?.toString() ?: "80") }
    var shotsFor by remember { mutableStateOf(initialMatch?.shotsFor?.toString() ?: "0") }
    var shotsAgainst by remember { mutableStateOf(initialMatch?.shotsAgainst?.toString() ?: "0") }
    var xGFor by remember { mutableStateOf(initialMatch?.expectedGoals?.toString() ?: "0.0") }
    var xGAgainst by remember { mutableStateOf(initialMatch?.expectedGoalsAgainst?.toString() ?: "0.0") }
    var leaguePosition by remember { mutableStateOf(initialMatch?.leaguePosition?.toString() ?: "1") }

    val performances = remember { 
        androidx.compose.runtime.mutableStateMapOf<String, PlayerPerformance>().apply {
            initialMatch?.playerPerformances?.forEach { put(it.playerId, it) }
        }
    }
    var motmSearchQuery by remember { 
        mutableStateOf(
            initialMatch?.motmName?.takeIf { it.isNotBlank() } 
            ?: initialMatch?.playerPerformances?.find { it.isMotm }?.playerId?.let { pid -> state.players.find { it.id == pid }?.displayName }
            ?: ""
        ) 
    }

    var showPlayerSelectDialog by remember { mutableStateOf(false) }
    var showOpponentDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val eligibleSquadPlayers = remember(state.players) {
        state.players.filter { 
            !it.isAcademy && !it.status.equals("Academy", ignoreCase = true) && !it.isLoanedOut && !it.status.equals("Sold", ignoreCase = true) 
        }
    }
    val initialMillis = remember(state.startYear) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, state.startYear)
        cal.set(Calendar.MONTH, Calendar.AUGUST)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.timeInMillis
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = if (matchDate.isEmpty()) initialMillis else null, initialDisplayedMonthMillis = if (matchDate.isEmpty()) initialMillis else null)

    val availableCompetitions = getCompetitionsForCountry(state.country)
    if (competition !in availableCompetitions && competition == "League" && availableCompetitions.isNotEmpty()) {
        competition = availableCompetitions[0]
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        matchDate = sdf.format(Date(millis))
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

    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.95f)
                .systemBarsPadding()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (initialMatch != null) "Edit Match" else "Log Match", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                }
                
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    
                    if (showOpponentDialog) {
                        item {
                            Dialog(onDismissRequest = { showOpponentDialog = false }) {
                                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                                    Column {
                                        Text("Search Opponent", fontWeight = FontWeight.Bold, color = Color.White)
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedTextField(value = opponent, onValueChange = { opponent = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Team Name") })
                                        Spacer(Modifier.height(16.dp))
                                        
                                        // Quick suggestions
                                        val suggestions = (clubDomains.keys.toList() + listOf("Barrow", "Wrexham", "Torquay AFC", "Torquay United")).distinct().sorted()
                                        val filtered = suggestions.filter { it.contains(opponent, ignoreCase = true) }
                                        
                                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                            items(filtered) { team ->
                                                Row(modifier = Modifier.fillMaxWidth().clickable { 
                                                    opponent = team
                                                    opponentCrestUrl = getTeamCrestUrl(team)
                                                    showOpponentDialog = false 
                                                }.padding(vertical = 8.dp)) {
                                                    Text(team, color = Color.White)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        
                                        Button(onClick = { 
                                            opponentCrestUrl = "https://ui-avatars.com/api/?name=${opponent.replace(" ", "+")}&background=random&color=fff&size=512"
                                            showOpponentDialog = false 
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Set Custom Team")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        // Scoreboard Header
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                if (state.clubCrestUrl.isNotBlank()) {
                                    coil.compose.AsyncImage(model = state.clubCrestUrl, contentDescription = "Our Crest", modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.Gray.copy(0.2f)))
                                } else {
                                    Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                        Text(state.clubName.take(1).uppercase(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(state.clubName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                            }
                        
                            
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.foundation.text.BasicTextField(
                                            value = goalsFor,
                                            onValueChange = { goalsFor = it },
                                            textStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center),
                                            modifier = Modifier.width(48.dp),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                        )
                                        Text(" - ", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White)
                                        androidx.compose.foundation.text.BasicTextField(
                                            value = goalsAgainst,
                                            onValueChange = { goalsAgainst = it },
                                            textStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center),
                                            modifier = Modifier.width(48.dp),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                        )
                                    }
                                    if (isExtraTime && ((homePenalties.toIntOrNull() ?: 0) > 0 || (awayPenalties.toIntOrNull() ?: 0) > 0)) {
                                        Text("(P: $homePenalties - $awayPenalties)", color = Color(0xFFF9A825), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clickable { showOpponentDialog = true }) {

                                TeamCrestImage(teamName = opponent, modifier = Modifier.size(64.dp).clip(CircleShape))
                                Spacer(Modifier.height(8.dp))
                                Text(if (opponent.isBlank()) "Select Opponent" else opponent, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    
                    item {
                        Text("Match Info", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = matchDate.ifEmpty { "YYYY-MM-DD" },
                                onValueChange = {},
                                label = { Text("Date") },
                                                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { showDatePicker = true }) {
                                        Icon(Icons.Default.DateRange, "Select Date")
                                    }
                                },
                                modifier = Modifier.weight(1f).clickable { showDatePicker = true }
                            )
                            
                            var compExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = compExpanded,
                                onExpandedChange = { compExpanded = it },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = competition,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Competition") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = compExpanded) },
                                    modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = compExpanded,
                                    onDismissRequest = { compExpanded = false }
                                ) {
                                    availableCompetitions.forEach { selectionOption ->
                                        DropdownMenuItem(
                                            text = { Text(selectionOption) },
                                            onClick = {
                                                competition = selectionOption
                                                compExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = stage, onValueChange = { stage = it }, label = { Text("Stage (e.g., Matchday 14, Final)") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Location: ", color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            RadioButton(selected = isHome, onClick = { isHome = true })
                            Text("Home", color = Color.White)
                            Spacer(Modifier.width(16.dp))
                            RadioButton(selected = !isHome, onClick = { isHome = false })
                            Text("Away", color = Color.White)
                        }
                    }
                    
                    item {
                        Text("Extra Time & Penalties", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isExtraTime, onCheckedChange = { isExtraTime = it })
                            Text("Extra Time Played", color = Color.White)
                        }
                        if (isExtraTime) {
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = homePenalties, onValueChange = { homePenalties = it }, label = { Text("Home Penalties") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                OutlinedTextField(value = awayPenalties, onValueChange = { awayPenalties = it }, label = { Text("Away Penalties") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            }
                        }
                    }
                    
                    item {
                        Text("Detailed Statistics (Optional)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = possession, onValueChange = { possession = it }, label = { Text("Possession %") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(value = passingAccuracy, onValueChange = { passingAccuracy = it }, label = { Text("Pass Acc %") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = shotsFor, onValueChange = { shotsFor = it }, label = { Text("Shots For") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(value = shotsAgainst, onValueChange = { shotsAgainst = it }, label = { Text("Shots Against") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = xGFor, onValueChange = { xGFor = it }, label = { Text("xG For") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(value = xGAgainst, onValueChange = { xGAgainst = it }, label = { Text("xG Against") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = leaguePosition, onValueChange = { leaguePosition = it }, label = { Text("League Position") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Man of the Match", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        var motmExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedTextField(
                                value = motmSearchQuery, 
                                onValueChange = { 
                                    motmSearchQuery = it
                                    motmExpanded = true 
                                }, 
                                label = { Text("Search Player Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            if (motmExpanded && motmSearchQuery.isNotBlank()) {
                                val searchEafc = viewModel.searchPlayersAndTeams(motmSearchQuery).map { if (it.commonName.isNotBlank()) it.commonName else it.name }
                                val allOptions = state.players.map { it.displayName } + searchEafc + listOf("Opponent Player")
                                val filtered = allOptions.filter { it.contains(motmSearchQuery, ignoreCase = true) }.distinct().take(10)
                                if (filtered.isNotEmpty()) {
                                    DropdownMenu(expanded = motmExpanded, onDismissRequest = { motmExpanded = false }, properties = androidx.compose.ui.window.PopupProperties(focusable = false)) {
                                        filtered.forEach { pName ->
                                            DropdownMenuItem(text = { Text(pName) }, onClick = { 
                                                motmSearchQuery = pName
                                                motmExpanded = false 
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Match Lineup (${performances.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { 
                                    eligibleSquadPlayers.sortedByDescending { it.overall }.take(11).forEach {
                                        if (!performances.containsKey(it.id)) {
                                            performances[it.id] = PlayerPerformance(playerId = it.id)
                                        }
                                    }
                                }) {
                                    Text("Auto-Pick XI")
                                }
                                Button(onClick = { showPlayerSelectDialog = true }) {
                                    Text("Select")
                                }
                            }
                        }
                    }
                    
                    item {
                        val validPerformances = performances.values.filter { perf -> state.players.any { it.id == perf.playerId } }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            validPerformances.forEach { perf ->
                                val player = state.players.firstOrNull { it.id == perf.playerId } ?: return@forEach
                                var expanded by remember { mutableStateOf(false) }
                                val isMotm = player.displayName == motmSearchQuery
                                val calculatedAssists = performances.values.flatMap { it.goalAssisters }.count { it == perf.playerId }
                                val totalAssists = perf.assists + calculatedAssists
                                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                                            if (perf.subInMinute != null) {
                                                Icon(Icons.Default.ArrowDropUp, "Subbed In", tint = Color.Green, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                            } else if (perf.subOutMinute != null) {
                                                Icon(Icons.Default.ArrowDropDown, "Subbed Out", tint = Color.Red, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(player.displayName, color = Color.White, modifier = Modifier.weight(1f))
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (isMotm) Icon(Icons.Default.Star, "MOTM", tint = Color.Yellow, modifier = Modifier.size(16.dp))
                                                repeat(perf.goals) { Icon(Icons.Default.SportsSoccer, "Goal", tint = Color.White, modifier = Modifier.size(16.dp)) }
                                                repeat(totalAssists) { Icon(Icons.Default.Moving, "Assist", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                                                if (perf.yellowCard) Box(modifier = Modifier.size(12.dp, 16.dp).background(Color.Yellow))
                                                if (perf.redCard) Box(modifier = Modifier.size(12.dp, 16.dp).background(Color.Red))
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Text(String.format("%.1f", perf.rating), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                        if (expanded) {
                                            PlayerPerformanceEditor(
                                                perf = perf,
                                                roster = state.players, // Pass full roster for sub dropdowns
                                                onUpdate = { newPerf -> performances[perf.playerId] = newPerf },
                                                onSubstitute = { teammateId, minute, isOut ->
                                                    if (isOut) {
                                                        performances[perf.playerId] = perf.copy(subOutMinute = minute)
                                                        val matePerf = performances[teammateId] ?: PlayerPerformance(playerId = teammateId)
                                                        performances[teammateId] = matePerf.copy(subInMinute = minute)
                                                    } else {
                                                        performances[perf.playerId] = perf.copy(subInMinute = minute)
                                                        val matePerf = performances[teammateId] ?: PlayerPerformance(playerId = teammateId)
                                                        performances[teammateId] = matePerf.copy(subOutMinute = minute)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    item { Spacer(Modifier.height(32.dp)) }
                }
                
                Button(
                    onClick = {
                        val finalPerformances = performances.values.map { 
                            val isThisPlayerMotm = state.players.find { p -> p.id == it.playerId }?.displayName == motmSearchQuery
                            it.copy(isMotm = isThisPlayerMotm) 
                        }
                        
                        val match = MatchResult(
                            id = initialMatch?.id ?: java.util.UUID.randomUUID().toString(),
                            opponentName = opponent,
                            opponentCrestUrl = opponentCrestUrl,
                            isHome = isHome,
                            goalsFor = goalsFor.toIntOrNull() ?: 0,
                            goalsAgainst = goalsAgainst.toIntOrNull() ?: 0,
                            matchDate = matchDate,
                            competition = competition,
                            stage = stage,
                            isExtraTime = isExtraTime,
                            homePenalties = homePenalties.toIntOrNull() ?: 0,
                            awayPenalties = awayPenalties.toIntOrNull() ?: 0,
                            possession = possession.toIntOrNull() ?: 50,
                            passingAccuracy = passingAccuracy.toIntOrNull() ?: 80,
                            shotsFor = shotsFor.toIntOrNull() ?: 0,
                            shotsAgainst = shotsAgainst.toIntOrNull() ?: 0,
                            expectedGoals = xGFor.toDoubleOrNull() ?: 0.0,
                            expectedGoalsAgainst = xGAgainst.toDoubleOrNull() ?: 0.0,
                            leaguePosition = leaguePosition.toIntOrNull() ?: 1,
                            motmName = motmSearchQuery,
                            motmIsOpponent = initialMatch != null && initialMatch.motmIsOpponent &&
                                motmSearchQuery == initialMatch.motmName,
                            possessionEstimated = initialMatch != null && initialMatch.possessionEstimated &&
                                possession == initialMatch.possession.toString(),
                            playerPerformances = finalPerformances,
                            // Editing a match must not throw away what the game recorded: scorers and substitutions
                            goalDetails = initialMatch?.goalDetails ?: emptyList(),
                            substitutions = initialMatch?.substitutions ?: emptyList()
                            // statsEstimated stays false: saving through this form counts as the user's review
                        )
                        onSave(match)
                    },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp)
                ) {
                    Text("Save Match Result", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        if (showPlayerSelectDialog) {
            Dialog(onDismissRequest = { showPlayerSelectDialog = false }) {
                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Select Match Lineup", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("${performances.size} selected", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(eligibleSquadPlayers, key = { it.id }) { p ->
                                val isSelected = performances.containsKey(p.id)
                                val rowAlpha = if (isSelected) 0.42f else 1.0f
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                                        .clickable {
                                            if (isSelected) {
                                                performances.remove(p.id)
                                            } else {
                                                performances[p.id] = PlayerPerformance(playerId = p.id)
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left: Player headshot image
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface)
                                            .alpha(rowAlpha),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isValidPlayerHeadshot(p.headshotUrl)) {
                                            coil.compose.SubcomposeAsyncImage(
                                                model = p.headshotUrl,
                                                contentDescription = "${p.displayName} Headshot",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                error = {
                                                    Icon(Icons.Default.Person, contentDescription = null, tint = if (isSelected) Color.Gray else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                }
                                            )
                                        } else {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = if (isSelected) Color.Gray else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    // Middle: Player name
                                    Text(
                                        text = p.displayName,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp,
                                        color = if (isSelected) Color.Gray else Color.White,
                                        modifier = Modifier.weight(1f).alpha(rowAlpha)
                                    )
                                    // Right side of player row: Position
                                    Text(
                                        text = p.primaryPosition.ifBlank { "SUB" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) Color.Gray else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.alpha(rowAlpha)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { showPlayerSelectDialog = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPerformanceEditor(
    perf: PlayerPerformance, 
    roster: List<com.fctracker.career.Player>, 
    onUpdate: (PlayerPerformance) -> Unit,
    onSubstitute: (teammateId: String, minute: Int, isOut: Boolean) -> Unit
) {
    val player = roster.find { it.id == perf.playerId }
    val positions = listOfNotNull(player?.primaryPosition, player?.secondaryPosition, player?.tertiaryPosition).filter { it.isNotBlank() }.map { it.uppercase() }
    val isGk = positions.contains("GK")
    val isDef = positions.any { it in listOf("CB", "LB", "RB", "LWB", "RWB", "CDM") }
    val isMidFwd = positions.any { it in listOf("CM", "CAM", "LM", "RM", "LW", "RW", "ST", "CF") } || positions.isEmpty()

    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Rating: ${String.format("%.1f", perf.rating)}", color = Color.White, modifier = Modifier.width(100.dp))
            Slider(
                value = perf.rating.toFloat(),
                onValueChange = { onUpdate(perf.copy(rating = (it * 10).toInt() / 10.0)) },
                valueRange = 1f..10f,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = perf.goals.toString(), 
                onValueChange = { 
                    val newGoals = it.toIntOrNull() ?: 0
                    val newAssisters = perf.goalAssisters.toMutableList()
                    while(newAssisters.size < newGoals) newAssisters.add(null)
                    while(newAssisters.size > newGoals) newAssisters.removeAt(newAssisters.lastIndex)
                    onUpdate(perf.copy(goals = newGoals, goalAssisters = newAssisters))
                },
                label = { Text("Goals") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = perf.assists.toString(),
                onValueChange = { onUpdate(perf.copy(assists = it.toIntOrNull() ?: 0)) },
                label = { Text("Assists") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        
        if (perf.goals > 0) {
            perf.goalAssisters.forEachIndexed { index, assisterId ->
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    val assisterName = roster.find { it.id == assisterId }?.displayName ?: "Unassisted"
                    OutlinedTextField(
                        value = assisterName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Goal ${index + 1} Assisted By") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Unassisted") },
                            onClick = { 
                                val newAssisters = perf.goalAssisters.toMutableList()
                                newAssisters[index] = null
                                onUpdate(perf.copy(goalAssisters = newAssisters))
                                expanded = false 
                            }
                        )
                        roster.forEach { teammate ->
                            if (teammate.id != perf.playerId) {
                                DropdownMenuItem(
                                    text = { Text(teammate.displayName) },
                                    onClick = { 
                                        val newAssisters = perf.goalAssisters.toMutableList()
                                        newAssisters[index] = teammate.id
                                        onUpdate(perf.copy(goalAssisters = newAssisters))
                                        expanded = false 
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Passing & Shooting
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = perf.passes.toString(),
                onValueChange = { onUpdate(perf.copy(passes = it.toIntOrNull() ?: 0)) },
                label = { Text("Passes") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = perf.passAccuracy.toString(),
                onValueChange = { onUpdate(perf.copy(passAccuracy = it.toIntOrNull() ?: 0)) },
                label = { Text("Pass Acc. (%)") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        if (!isGk) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = perf.shots.toString(),
                    onValueChange = { onUpdate(perf.copy(shots = it.toIntOrNull() ?: 0)) },
                    label = { Text("Shots") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = perf.shotAccuracy.toString(),
                    onValueChange = { onUpdate(perf.copy(shotAccuracy = it.toIntOrNull() ?: 0)) },
                    label = { Text("Shot Acc. (%)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        // Defensive Stats
        if (isDef || isMidFwd) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = perf.tackles.toString(),
                    onValueChange = { onUpdate(perf.copy(tackles = it.toIntOrNull() ?: 0)) },
                    label = { Text("Tackles") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = perf.tacklePercentage.toString(),
                    onValueChange = { onUpdate(perf.copy(tacklePercentage = it.toIntOrNull() ?: 0)) },
                    label = { Text("Tackle %") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = perf.interceptions.toString(),
                    onValueChange = { onUpdate(perf.copy(interceptions = it.toIntOrNull() ?: 0)) },
                    label = { Text("Interceptions") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        // Goalie Stats
        if (isGk) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = perf.saves.toString(),
                    onValueChange = { onUpdate(perf.copy(saves = it.toIntOrNull() ?: 0)) },
                    label = { Text("Saves") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = perf.penaltySaves.toString(),
                    onValueChange = { onUpdate(perf.copy(penaltySaves = it.toIntOrNull() ?: 0)) },
                    label = { Text("Penalty Saves") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
        
        Row(horizontalArrangement = if (isGk || isDef) Arrangement.SpaceBetween else Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = perf.yellowCard, onCheckedChange = { onUpdate(perf.copy(yellowCard = it)) })
                Text("Yellow", fontSize = 12.sp, color = Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = perf.redCard, onCheckedChange = { onUpdate(perf.copy(redCard = it)) })
                Text("Red", fontSize = 12.sp, color = Color.White)
            }
            if (isGk || isDef) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = perf.cleanSheet, onCheckedChange = { onUpdate(perf.copy(cleanSheet = it)) })
                    Text("Clean Sheet", fontSize = 12.sp, color = Color.White)
                }
            }
        }
        
        var isSubbed by remember { mutableStateOf(perf.subInMinute != null || perf.subOutMinute != null) }
        var subIsOut by remember { mutableStateOf(perf.subOutMinute != null) }
        var subMinute by remember { mutableStateOf((perf.subOutMinute ?: perf.subInMinute)?.toString() ?: "") }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isSubbed, onCheckedChange = { isSubbed = it })
            Text("Involved in Substitution?", fontSize = 14.sp, color = Color.White)
        }
        
        if (isSubbed) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = subIsOut, onClick = { subIsOut = true })
                    Text("Subbed Out", fontSize = 14.sp, color = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !subIsOut, onClick = { subIsOut = false })
                    Text("Subbed In", fontSize = 14.sp, color = Color.White)
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                
                
                var teammateExpanded by remember { mutableStateOf(false) }
                var selectedTeammateName by remember { mutableStateOf("Select Teammate") }
                ExposedDropdownMenuBox(
                    expanded = teammateExpanded,
                    onExpandedChange = { teammateExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedTeammateName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (subIsOut) "Replaced By" else "Replaced") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = teammateExpanded) },
                        modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = teammateExpanded,
                        onDismissRequest = { teammateExpanded = false }
                    ) {
                        roster.forEach { teammate ->
                            if (teammate.id != perf.playerId) {
                                DropdownMenuItem(
                                    text = { Text(teammate.displayName) },
                                    onClick = { 
                                        val min = subMinute.toIntOrNull() ?: 0
                                        onSubstitute(teammate.id, min, subIsOut)
                                        selectedTeammateName = teammate.displayName
                                        teammateExpanded = false 
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}


val clubDomains = mapOf(
        // ENGLAND - PREMIER LEAGUE & CHAMPIONSHIP
        "Arsenal" to "arsenal.com",
        "Aston Villa" to "avfc.co.uk",
        "Bournemouth" to "afcb.co.uk",
        "AFC Bournemouth" to "afcb.co.uk",
        "Brentford" to "brentfordfc.com",
        "Brighton" to "brightonandhovealbion.com",
        "Brighton & Hove Albion" to "brightonandhovealbion.com",
        "Burnley" to "burnleyfootballclub.com",
        "Chelsea" to "chelseafc.com",
        "Crystal Palace" to "cpfc.co.uk",
        "Everton" to "evertonfc.com",
        "Fulham" to "fulhamfc.com",
        "Ipswich Town" to "itfc.co.uk",
        "Liverpool" to "liverpoolfc.com",
        "Luton Town" to "lutontown.co.uk",
        "Manchester City" to "mancity.com",
        "Manchester United" to "manutd.com",
        "Newcastle United" to "nufc.co.uk",
        "Nottingham Forest" to "nottinghamforest.co.uk",
        "Sheffield United" to "sufc.co.uk",
        "Tottenham Hotspur" to "tottenhamhotspur.com",
        "West Ham" to "whufc.com",
        "West Ham United" to "whufc.com",
        "Wolverhampton" to "wolves.co.uk",
        "Wolverhampton Wanderers" to "wolves.co.uk",
        "Leeds United" to "leedsunited.com",
        "Leicester City" to "lcfc.com",
        "Southampton" to "southamptonfc.com",
        "Blackburn Rovers" to "rovers.co.uk",
        "Bristol City" to "bcfc.co.uk",
        "Cardiff City" to "cardiffcityfc.co.uk",
        "Coventry City" to "ccfc.co.uk",
        "Derby County" to "dcfc.co.uk",
        "Hull City" to "wearehullcity.co.uk",
        "Middlesbrough" to "mfc.co.uk",
        "Millwall" to "millwallfc.co.uk",
        "Norwich City" to "canaries.co.uk",
        "Oxford United" to "oufc.co.uk",
        "Plymouth Argyle" to "pafc.co.uk",
        "Portsmouth" to "portsmouthfc.co.uk",
        "Preston North End" to "pnefc.net",
        "Queens Park Rangers" to "qpr.co.uk",
        "QPR" to "qpr.co.uk",
        "Sheffield Wednesday" to "swfc.co.uk",
        "Stoke City" to "stokecityfc.com",
        "Sunderland" to "safc.com",
        "Swansea City" to "swanseacity.com",
        "Watford" to "watfordfc.com",
        "West Bromwich Albion" to "wba.co.uk",
        // ENGLAND - EFL LEAGUE ONE
        "Barnsley" to "barnsleyfc.co.uk",
        "Birmingham City" to "bcfc.com",
        "Blackpool" to "blackpoolfc.co.uk",
        "Bolton Wanderers" to "bwfc.co.uk",
        "Bristol Rovers" to "bristolrovers.co.uk",
        "Burton Albion" to "burtonalbionfc.co.uk",
        "Cambridge United" to "cambridgeunited.com",
        "Charlton Athletic" to "charltonafc.com",
        "Crawley Town" to "crawleytownfc.com",
        "Exeter City" to "exetercityfc.co.uk",
        "Huddersfield Town" to "htafc.com",
        "Leyton Orient" to "leytonorient.com",
        "Lincoln City" to "weareimps.com",
        "Mansfield Town" to "mansfieldtown.net",
        "Northampton Town" to "ntfc.co.uk",
        "Peterborough United" to "theposh.com",
        "Reading" to "readingfc.co.uk",
        "Rotherham United" to "themillers.co.uk",
        "Shrewsbury Town" to "shrewsburytown.com",
        "Stevenage" to "stevenagefc.com",
        "Stockport County" to "stockportcounty.com",
        "Wigan Athletic" to "wiganathletic.com",
        "Wrexham" to "wrexhamafc.co.uk",
        "Wrexham AFC" to "wrexhamafc.co.uk",
        "Wycombe Wanderers" to "wwfc.com",
        // ENGLAND - EFL LEAGUE TWO & NATIONAL LEAGUE
        "Accrington Stanley" to "accringtonstanley.co.uk",
        "AFC Wimbledon" to "afcwimbledon.co.uk",
        "Barrow" to "barrowafc.com",
        "Barrow AFC" to "barrowafc.com",
        "Bradford City" to "bradfordcityafc.com",
        "Bromley" to "bromleyfc.co.uk",
        "Carlisle United" to "carlisleunited.co.uk",
        "Cheltenham Town" to "ctfc.com",
        "Chesterfield" to "chesterfield-fc.co.uk",
        "Colchester United" to "cu-fc.com",
        "Crewe Alexandra" to "crewealex.net",
        "Doncaster Rovers" to "doncasterroversfc.co.uk",
        "Fleetwood Town" to "fleetwoodtownfc.com",
        "Gillingham" to "gillinghamfootballclub.com",
        "Grimsby Town" to "grimsby-townfc.co.uk",
        "Harrogate Town" to "harrogatetownafc.com",
        "Milton Keynes Dons" to "mkdons.com",
        "MK Dons" to "mkdons.com",
        "Morecambe" to "morecambefc.com",
        "Newport County" to "newport-county.co.uk",
        "Notts County" to "nottscountyfc.co.uk",
        "Port Vale" to "port-vale.co.uk",
        "Salford City" to "salfordcityfc.co.uk",
        "Swindon Town" to "swindontownfc.co.uk",
        "Tranmere Rovers" to "tranmererovers.co.uk",
        "Walsall" to "saddlers.co.uk",
        "Torquay United" to "torquayunited.com",
        "Torquay AFC" to "torquayunited.com",

        // SPAIN
        "Real Madrid" to "realmadrid.com",
        "Barcelona" to "fcbarcelona.com",
        "Atletico Madrid" to "atleticodemadrid.com",
        "Sevilla" to "sevillafc.es",
        "Real Sociedad" to "realsociedad.eus",
        "Athletic Club" to "athletic-club.eus",
        "Real Betis" to "realbetisbalompie.es",
        "Valencia" to "valenciacf.com",
        "Villarreal" to "villarrealcf.es",
        "Girona" to "gironafc.cat",

        // GERMANY
        "Bayern Munich" to "fcbayern.com",
        "Borussia Dortmund" to "bvb.de",
        "Bayer Leverkusen" to "bayer04.de",
        "RB Leipzig" to "rbleipzig.com",
        "Eintracht Frankfurt" to "eintracht.de",
        "Stuttgart" to "vfb.de",
        "Monchengladbach" to "borussia.de",
        "Wolfsburg" to "vfl-wolfsburg.de",

        // ITALY
        "Juventus" to "juventus.com",
        "AC Milan" to "acmilan.com",
        "Inter Milan" to "inter.it",
        "Napoli" to "sscnapoli.it",
        "Roma" to "asroma.com",
        "Lazio" to "sslazio.it",
        "Atalanta" to "atalanta.it",
        "Fiorentina" to "acffiorentina.com",
        "Bologna" to "bolognafc.it",

        // FRANCE
        "PSG" to "psg.fr",
        "Monaco" to "asmonaco.com",
        "Marseille" to "om.fr",
        "Lyon" to "ol.fr",
        "Lille" to "losc.fr",
        "Lens" to "rclens.fr",
        "Nice" to "ognice.com",
        "Rennes" to "staderennais.com",

        // SAUDI
        "Al Hilal" to "alhilal.com",
        "Al Nassr" to "alnassr.sa",
        "Al Ittihad" to "ittihadclub.sa",
        "Al Ahli" to "alahlifc.sa",
        "Al Ettifaq" to "ettifaq.com",

        // AMERICA
        "Inter Miami" to "intermiamicf.com",
        "LA Galaxy" to "lagalaxy.com",
        "LAFC" to "lafc.com",
        "Seattle Sounders" to "soundersfc.com",
        "Atlanta United" to "atlutd.com",
        "New York City FC" to "nycfc.com",
        "Philadelphia Union" to "philadelphiaunion.com",
        
        // PORTUGAL
        "Benfica" to "slbenfica.pt",
        "Porto" to "fcporto.pt",
        "Sporting CP" to "sporting.pt",
        "Braga" to "scbraga.pt",
        
        // NETHERLANDS
        "Ajax" to "ajax.nl",
        "PSV" to "psv.nl",
        "Feyenoord" to "feyenoord.nl",
        "AZ Alkmaar" to "az.nl"
    )

val highResClubCrests = mapOf(
    "Arsenal" to "https://cdn.futbin.com/content/fifa24/img/clubs/1.png",
    "Aston Villa" to "https://cdn.futbin.com/content/fifa24/img/clubs/2.png",
    "Bournemouth" to "https://cdn.futbin.com/content/fifa24/img/clubs/1943.png",
    "AFC Bournemouth" to "https://cdn.futbin.com/content/fifa24/img/clubs/1943.png",
    "Brentford" to "https://cdn.futbin.com/content/fifa24/img/clubs/1925.png",
    "Brighton" to "https://cdn.futbin.com/content/fifa24/img/clubs/1808.png",
    "Brighton & Hove Albion" to "https://cdn.futbin.com/content/fifa24/img/clubs/1808.png",
    "Burnley" to "https://cdn.futbin.com/content/fifa24/img/clubs/1796.png",
    "Chelsea" to "https://cdn.futbin.com/content/fifa24/img/clubs/5.png",
    "Crystal Palace" to "https://cdn.futbin.com/content/fifa24/img/clubs/1799.png",
    "Everton" to "https://cdn.futbin.com/content/fifa24/img/clubs/7.png",
    "Fulham" to "https://cdn.futbin.com/content/fifa24/img/clubs/144.png",
    "Ipswich Town" to "https://cdn.futbin.com/content/fifa24/img/clubs/94.png",
    "Ipswich" to "https://cdn.futbin.com/content/fifa24/img/clubs/94.png",
    "Liverpool" to "https://cdn.futbin.com/content/fifa24/img/clubs/9.png",
    "Luton Town" to "https://cdn.futbin.com/content/fifa24/img/clubs/1923.png",
    "Luton" to "https://cdn.futbin.com/content/fifa24/img/clubs/1923.png",
    "Manchester City" to "https://cdn.futbin.com/content/fifa24/img/clubs/10.png",
    "Man City" to "https://cdn.futbin.com/content/fifa24/img/clubs/10.png",
    "Manchester United" to "https://cdn.futbin.com/content/fifa24/img/clubs/11.png",
    "Man United" to "https://cdn.futbin.com/content/fifa24/img/clubs/11.png",
    "Man Utd" to "https://cdn.futbin.com/content/fifa24/img/clubs/11.png",
    "Newcastle United" to "https://cdn.futbin.com/content/fifa24/img/clubs/13.png",
    "Newcastle" to "https://cdn.futbin.com/content/fifa24/img/clubs/13.png",
    "Nottingham Forest" to "https://cdn.futbin.com/content/fifa24/img/clubs/14.png",
    "Sheffield United" to "https://cdn.futbin.com/content/fifa24/img/clubs/1794.png",
    "Tottenham Hotspur" to "https://cdn.futbin.com/content/fifa24/img/clubs/18.png",
    "Tottenham" to "https://cdn.futbin.com/content/fifa24/img/clubs/18.png",
    "Spurs" to "https://cdn.futbin.com/content/fifa24/img/clubs/18.png",
    "West Ham" to "https://cdn.futbin.com/content/fifa24/img/clubs/19.png",
    "West Ham United" to "https://cdn.futbin.com/content/fifa24/img/clubs/19.png",
    "Wolverhampton" to "https://cdn.futbin.com/content/fifa24/img/clubs/110.png",
    "Wolverhampton Wanderers" to "https://cdn.futbin.com/content/fifa24/img/clubs/110.png",
    "Wolves" to "https://cdn.futbin.com/content/fifa24/img/clubs/110.png",
    "Leeds United" to "https://cdn.futbin.com/content/fifa24/img/clubs/8.png",
    "Leeds" to "https://cdn.futbin.com/content/fifa24/img/clubs/8.png",
    "Leicester City" to "https://cdn.futbin.com/content/fifa24/img/clubs/95.png",
    "Leicester" to "https://cdn.futbin.com/content/fifa24/img/clubs/95.png",
    "Southampton" to "https://cdn.futbin.com/content/fifa24/img/clubs/17.png",
    "Blackburn Rovers" to "https://cdn.futbin.com/content/fifa24/img/clubs/3.png",
    "Bristol City" to "https://cdn.futbin.com/content/fifa24/img/clubs/1919.png",
    "Cardiff City" to "https://cdn.futbin.com/content/fifa24/img/clubs/1961.png",
    "Coventry City" to "https://cdn.futbin.com/content/fifa24/img/clubs/1800.png",
    "Derby County" to "https://cdn.futbin.com/content/fifa24/img/clubs/91.png",
    "Hull City" to "https://cdn.futbin.com/content/fifa24/img/clubs/1952.png",
    "Middlesbrough" to "https://cdn.futbin.com/content/fifa24/img/clubs/12.png",
    "Millwall" to "https://cdn.futbin.com/content/fifa24/img/clubs/97.png",
    "Norwich City" to "https://cdn.futbin.com/content/fifa24/img/clubs/1792.png",
    "Oxford United" to "https://cdn.futbin.com/content/fifa24/img/clubs/1951.png",
    "Plymouth Argyle" to "https://cdn.futbin.com/content/fifa24/img/clubs/1929.png",
    "Portsmouth" to "https://cdn.futbin.com/content/fifa24/img/clubs/1790.png",
    "Preston North End" to "https://cdn.futbin.com/content/fifa24/img/clubs/1801.png",
    "Queens Park Rangers" to "https://cdn.futbin.com/content/fifa24/img/clubs/15.png",
    "QPR" to "https://cdn.futbin.com/content/fifa24/img/clubs/15.png",
    "Sheffield Wednesday" to "https://cdn.futbin.com/content/fifa24/img/clubs/1807.png",
    "Stoke City" to "https://cdn.futbin.com/content/fifa24/img/clubs/1806.png",
    "Sunderland" to "https://cdn.futbin.com/content/fifa24/img/clubs/106.png",
    "Swansea City" to "https://cdn.futbin.com/content/fifa24/img/clubs/1960.png",
    "Watford" to "https://cdn.futbin.com/content/fifa24/img/clubs/1795.png",
    "West Bromwich Albion" to "https://cdn.futbin.com/content/fifa24/img/clubs/109.png",
    "West Brom" to "https://cdn.futbin.com/content/fifa24/img/clubs/109.png",
    "Birmingham City" to "https://cdn.futbin.com/content/fifa24/img/clubs/88.png",
    "Blackpool" to "https://cdn.futbin.com/content/fifa24/img/clubs/1926.png",
    "Bolton Wanderers" to "https://cdn.futbin.com/content/fifa24/img/clubs/4.png",
    "Charlton Athletic" to "https://cdn.futbin.com/content/fifa24/img/clubs/89.png",
    "Reading" to "https://cdn.futbin.com/content/fifa24/img/clubs/1793.png",
    "Wigan Athletic" to "https://cdn.futbin.com/content/fifa24/img/clubs/1917.png",
    "Huddersfield Town" to "https://cdn.futbin.com/content/fifa24/img/clubs/1939.png",
    "Wrexham" to "https://cdn.futbin.com/content/fifa24/img/clubs/114251.png",
    "Real Madrid" to "https://cdn.futbin.com/content/fifa24/img/clubs/243.png",
    "Barcelona" to "https://cdn.futbin.com/content/fifa24/img/clubs/241.png",
    "FC Barcelona" to "https://cdn.futbin.com/content/fifa24/img/clubs/241.png",
    "Atletico Madrid" to "https://cdn.futbin.com/content/fifa24/img/clubs/240.png",
    "Atletico" to "https://cdn.futbin.com/content/fifa24/img/clubs/240.png",
    "Bayern Munich" to "https://cdn.futbin.com/content/fifa24/img/clubs/21.png",
    "Bayern" to "https://cdn.futbin.com/content/fifa24/img/clubs/21.png",
    "Borussia Dortmund" to "https://cdn.futbin.com/content/fifa24/img/clubs/22.png",
    "Dortmund" to "https://cdn.futbin.com/content/fifa24/img/clubs/22.png",
    "Bayer Leverkusen" to "https://cdn.futbin.com/content/fifa24/img/clubs/32.png",
    "Leverkusen" to "https://cdn.futbin.com/content/fifa24/img/clubs/32.png",
    "Paris Saint-Germain" to "https://cdn.futbin.com/content/fifa24/img/clubs/73.png",
    "PSG" to "https://cdn.futbin.com/content/fifa24/img/clubs/73.png",
    "Juventus" to "https://cdn.futbin.com/content/fifa24/img/clubs/45.png",
    "Inter Milan" to "https://cdn.futbin.com/content/fifa24/img/clubs/44.png",
    "Inter" to "https://cdn.futbin.com/content/fifa24/img/clubs/44.png",
    "AC Milan" to "https://cdn.futbin.com/content/fifa24/img/clubs/47.png",
    "Milan" to "https://cdn.futbin.com/content/fifa24/img/clubs/47.png",
    "Napoli" to "https://cdn.futbin.com/content/fifa24/img/clubs/48.png",
    "Roma" to "https://cdn.futbin.com/content/fifa24/img/clubs/52.png",
    "Lazio" to "https://cdn.futbin.com/content/fifa24/img/clubs/116.png",
    "Ajax" to "https://cdn.futbin.com/content/fifa24/img/clubs/245.png",
    "PSV" to "https://cdn.futbin.com/content/fifa24/img/clubs/247.png",
    "Feyenoord" to "https://cdn.futbin.com/content/fifa24/img/clubs/246.png",
    "Benfica" to "https://cdn.futbin.com/content/fifa24/img/clubs/234.png",
    "Porto" to "https://cdn.futbin.com/content/fifa24/img/clubs/236.png",
    "Sporting CP" to "https://cdn.futbin.com/content/fifa24/img/clubs/237.png",
    "Sporting" to "https://cdn.futbin.com/content/fifa24/img/clubs/237.png",
    "Al Nassr" to "https://cdn.futbin.com/content/fifa24/img/clubs/112139.png",
    "Al Hilal" to "https://cdn.futbin.com/content/fifa24/img/clubs/605.png",
    "Al Ittihad" to "https://cdn.futbin.com/content/fifa24/img/clubs/607.png",
    "Al Ahli" to "https://cdn.futbin.com/content/fifa24/img/clubs/112391.png",
    "Inter Miami" to "https://cdn.futbin.com/content/fifa24/img/clubs/112885.png",
    "LA Galaxy" to "https://cdn.futbin.com/content/fifa24/img/clubs/697.png",
    "LAFC" to "https://cdn.futbin.com/content/fifa24/img/clubs/114162.png"
)

fun getTeamCrestUrl(teamName: String): String {
    if (teamName.isBlank()) return ""
    val cleanName = teamName.trim()
    
    // 1. Direct high-res match
    val directHighRes = highResClubCrests.entries.firstOrNull { it.key.equals(cleanName, ignoreCase = true) }
    if (directHighRes != null) return directHighRes.value
    
    // 2. Partial high-res match
    val lower = cleanName.lowercase()
    val partialHighRes = highResClubCrests.entries.firstOrNull { 
        val k = it.key.lowercase()
        lower == k || lower.contains(k) || k.contains(lower)
    }
    if (partialHighRes != null) return partialHighRes.value

    // 3. Domain match with high-resolution 256px Google Favicon service
    val exactDomain = clubDomains.entries.firstOrNull { it.key.equals(cleanName, ignoreCase = true) }?.value
    val domain = exactDomain ?: clubDomains.entries.firstOrNull { 
        val k = it.key.lowercase()
        lower.contains(k) || k.contains(lower)
    }?.value

    if (domain != null) {
        return "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://${domain}&size=256"
    }

    // 4. Fuzzy fallback
    val t = lower.replace(" ", "").replace("&", "").replace("-", "")
    val fallbackDomain = when {
        t.contains("barrow") -> "barrowafc.com"
        t.contains("torquay") -> "torquayunited.com"
        t.contains("wrexham") -> "wrexhamafc.co.uk"
        t.contains("arsenal") -> "arsenal.com"
        t.contains("astonvilla") -> "avfc.co.uk"
        t.contains("brentford") -> "brentfordfc.com"
        t.contains("brighton") -> "brightonandhovealbion.com"
        t.contains("chelsea") -> "chelseafc.com"
        t.contains("liverpool") -> "liverpoolfc.com"
        t.contains("manchestercity") -> "mancity.com"
        t.contains("manchesterunited") -> "manutd.com"
        t.contains("newcastle") -> "nufc.co.uk"
        t.contains("tottenham") -> "tottenhamhotspur.com"
        t.contains("realmadrid") -> "realmadrid.com"
        t.contains("barcelona") -> "fcbarcelona.com"
        t.contains("atletico") -> "atleticodemadrid.com"
        t.contains("bayern") -> "fcbayern.com"
        t.contains("dortmund") -> "bvb.de"
        t.contains("juventus") -> "juventus.com"
        t.contains("acmilan") -> "acmilan.com"
        t.contains("intermilan") || t.contains("inter") -> "inter.it"
        t.contains("psg") || t.contains("paris") -> "psg.fr"
        t.contains("ajax") -> "ajax.nl"
        else -> return ""
    }
    return "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://${fallbackDomain}&size=256"
}


@Composable
fun TeamCrestImage(teamName: String, modifier: Modifier = Modifier) {
    val url = getTeamCrestUrl(teamName)
    val fallbackBox = @Composable {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val initials = teamName.split(" ").take(2).joinToString("") { it.take(1) }.uppercase()
            Text(if (initials.isNotEmpty()) initials else "?", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
    
    if (url.isNotEmpty()) {
        coil.compose.SubcomposeAsyncImage(
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(url)
                
                .crossfade(true)
                .build(),
            contentDescription = "$teamName Crest",
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = modifier,
            loading = { fallbackBox() },
            error = { fallbackBox() }
        )
    } else {
        fallbackBox()
    }
}

@Composable
fun getCompetitionLogoUrl(competition: String): String {
    val c = competition.lowercase()
    
    if (c.contains("premier")) return "https://upload.wikimedia.org/wikipedia/en/thumb/f/f2/Premier_League_Logo.svg/512px-Premier_League_Logo.svg.png"
    if (c.contains("la liga") || c.contains("laliga")) return "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0f/LaLiga_logo_2023.svg/512px-LaLiga_logo_2023.svg.png"
    if (c.contains("segunda")) return "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0f/LaLiga_logo_2023.svg/512px-LaLiga_logo_2023.svg.png"
    if (c.contains("bundesliga")) return "https://upload.wikimedia.org/wikipedia/en/thumb/d/df/Bundesliga_logo_%282017%29.svg/512px-Bundesliga_logo_%282017%29.svg.png"
    if (c.contains("serie a")) return "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e9/Serie_A_logo_2019.svg/512px-Serie_A_logo_2019.svg.png"
    if (c.contains("serie b")) return "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e9/Serie_A_logo_2019.svg/512px-Serie_A_logo_2019.svg.png"
    if (c.contains("ligue 1")) return "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5e/Ligue1.svg/512px-Ligue1.svg.png"
    if (c.contains("ligue 2")) return "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5e/Ligue1.svg/512px-Ligue1.svg.png"
    if (c.contains("mls")) return "https://upload.wikimedia.org/wikipedia/commons/thumb/7/76/MLS_crest_logo_RGB_gradient.svg/512px-MLS_crest_logo_RGB_gradient.svg.png"
    if (c.contains("champions")) return "https://upload.wikimedia.org/wikipedia/en/thumb/b/bf/UEFA_Champions_League_logo_2.svg/512px-UEFA_Champions_League_logo_2.svg.png"
    if (c.contains("europa league") || c.contains("europa")) return "https://upload.wikimedia.org/wikipedia/en/thumb/0/03/UEFA_Europa_League_logo_%282021%29.svg/512px-UEFA_Europa_League_logo_%282021%29.svg.png"
    if (c.contains("conference")) return "https://upload.wikimedia.org/wikipedia/en/thumb/5/54/UEFA_Europa_Conference_League_logo.svg/512px-UEFA_Europa_Conference_League_logo.svg.png"
    if (c.contains("fa cup")) return "https://upload.wikimedia.org/wikipedia/en/thumb/b/be/Emirates_FA_Cup_logo.svg/512px-Emirates_FA_Cup_logo.svg.png"
    if (c.contains("carabao") || c.contains("efl") || c.contains("championship") || c.contains("league one") || c.contains("league two")) {
        return "https://upload.wikimedia.org/wikipedia/en/thumb/5/5e/EFL_logo.svg/512px-EFL_logo.svg.png"
    }
    if (c.contains("copa del rey")) return "https://upload.wikimedia.org/wikipedia/en/thumb/c/c9/Copa_del_Rey_2021_logo.svg/512px-Copa_del_Rey_2021_logo.svg.png"
    if (c.contains("dfb")) return "https://upload.wikimedia.org/wikipedia/en/thumb/0/07/DFB-Pokal_logo.svg/512px-DFB-Pokal_logo.svg.png"
    if (c.contains("coppa italia")) return "https://upload.wikimedia.org/wikipedia/en/thumb/2/23/Coppa_Italia_logo_%282021%29.svg/512px-Coppa_Italia_logo_%282021%29.svg.png"

    val domain = when {
        c.contains("saudi") || c.contains("roshn") -> "spl.com.sa"
        c.contains("eredivisie") -> "eredivisie.eu"
        c.contains("portugal") || c.contains("liga nos") -> "ligaportugal.pt"
        c.contains("world cup") -> "fifa.com"
        c.contains("usl") -> "uslchampionship.com"
        else -> return ""
    }
    return "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://${domain}&size=256"
}

@Composable
fun CompetitionLogoImage(competition: String, modifier: Modifier = Modifier) {
    val url = getCompetitionLogoUrl(competition)
    val fallbackBox = @Composable {
        val initials = competition.split(" ").take(2).joinToString("") { it.take(1) }.uppercase()
        val bgColor = when {
            competition.contains("Premier") -> Color(0xFF38003C)
            competition.contains("Champions") -> Color(0xFF001489)
            competition.contains("Europa") -> Color(0xFFF68712)
            competition.contains("La Liga") -> Color(0xFFEE1222)
            competition.contains("Serie A") -> Color(0xFF0058A8)
            competition.contains("Bundesliga") -> Color(0xFFD20515)
            else -> MaterialTheme.colorScheme.primary
        }
        Box(
            modifier = modifier.background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, color = if (bgColor == MaterialTheme.colorScheme.primary) MaterialTheme.colorScheme.onPrimary else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
    if (url.isNotEmpty()) {
        coil.compose.SubcomposeAsyncImage(
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(url)
                
                .crossfade(true)
                .build(),
            contentDescription = "$competition Logo",
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = modifier,
            loading = { fallbackBox() },
            error = { fallbackBox() }
        )
    } else {
        fallbackBox()
    }
}
