package com.fctracker.career.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fctracker.career.Player
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.automirrored.filled.ExitToApp

@Composable
fun DetailedPlayerStatsModal(
    state: com.fctracker.career.AppState,
    player: Player,
    showOvrGrowth: Boolean = false,
    onDismiss: () -> Unit,
    onMoveToAcademy: ((Player) -> Unit)? = null,
    onRetirePlayer: ((Player) -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        val allMatches = androidx.compose.runtime.remember(state.recentMatches, state.historicalSeasons) {
            (state.recentMatches + state.historicalSeasons.flatMap { it.matches }).sortedByDescending { it.matchDate }
        }
        val allPerformances: List<com.fctracker.career.PlayerPerformance> = androidx.compose.runtime.remember(allMatches) {
            allMatches.mapNotNull { m -> m.playerPerformances.find { p -> p.playerId == player.id } }
        }
        val last5Ratings = allPerformances.take(5).map { it.rating }.reversed()
        val calcAvg = if (allPerformances.isNotEmpty()) allPerformances.map { it.rating }.average() else 0.0
        val starts = allPerformances.count { it.subInMinute == null }
        val subs = allPerformances.count { it.subInMinute != null }
        val startPct = if (starts + subs > 0) starts.toFloat() / (starts + subs) else 0f
        val cleanSheets = allPerformances.count { it.cleanSheet }
        val yellowCards = allPerformances.count { it.yellowCard }
        val redCards = allPerformances.count { it.redCard }

        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 700.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                // 1. Header Section
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        val hasValidHeadshot = player.headshotUrl.isNotBlank() &&
                            !player.headshotUrl.contains("crest", ignoreCase = true) &&
                            !player.headshotUrl.contains("clubs", ignoreCase = true) &&
                            !player.headshotUrl.contains("logo", ignoreCase = true) &&
                            !player.headshotUrl.contains("icon.horse", ignoreCase = true)
                        if (hasValidHeadshot) {
                            coil.compose.AsyncImage(
                                model = player.headshotUrl,
                                contentDescription = "Player Face",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(player.displayName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        val posText = listOfNotNull(player.primaryPosition, player.secondaryPosition, player.tertiaryPosition).filter { it.isNotBlank() }.joinToString(", ")
                        Text("$posText • OVR ${player.overall}", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 2. Top Metrics (4-Column Highlights)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricBox("Apps", player.matchesPlayed.toString(), Modifier.weight(1f))
                    MetricBox("Goals", player.goals.toString(), Modifier.weight(1f), highlight = true)
                    MetricBox("Assists", player.assists.toString(), Modifier.weight(1f))
                    MetricBox("MOTMs", allPerformances.count { it.isMotm }.toString(), Modifier.weight(1f))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 3. Middle Grid (Form & Usage Analysis)
                Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Left Box: Form
                    Card(modifier = Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Form (Last 5)", fontSize = 12.sp, color = Color.LightGray)
                            Spacer(Modifier.height(8.dp))
                            if (last5Ratings.isEmpty()) {
                                Text("No Data", fontSize = 12.sp, color = Color.Gray)
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    last5Ratings.forEach { rating ->
                                        val color = when {
                                            rating >= 8.0 -> Color.Green
                                            rating >= 7.0 -> Color.Yellow
                                            else -> Color.Red
                                        }
                                        Box(modifier = Modifier.size(20.dp).background(color, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                                            Text(rating.toString(), fontSize = 8.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Avg: ${String.format("%.1f", calcAvg)}", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    // Right Box: Positions & Usage
                    Card(modifier = Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Usage", fontSize = 12.sp, color = Color.LightGray)
                            Text("Started: $starts", fontSize = 10.sp, color = Color.White)
                            LinearProgressIndicator(progress = { startPct }, modifier = Modifier.fillMaxWidth().height(4.dp).padding(vertical = 4.dp))
                            Text("Subbed: $subs", fontSize = 10.sp, color = Color.White)
                            LinearProgressIndicator(progress = { if (starts + subs > 0) 1f - startPct else 0f }, modifier = Modifier.fillMaxWidth().height(4.dp).padding(vertical = 4.dp), color = Color.Cyan)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 4. Bottom Grid (Detailed Stats & Growth)
                Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(modifier = Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Career Stats", fontSize = 12.sp, color = Color.LightGray, fontWeight = FontWeight.Bold)
                            StatRow("Apps", player.matchesPlayed.toString())
                            StatRow("Goals", player.goals.toString())
                            StatRow("Assists", player.assists.toString())
                            StatRow("Clean Sheets", cleanSheets.toString())
                            StatRow("Cards (Y/R)", "${yellowCards}Y / ${redCards}R")
                            StatRow("Avg Rating", String.format("%.1f", calcAvg))
                        }
                    }
                    if (showOvrGrowth) {
                        Card(modifier = Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("OVR vs Potential", fontSize = 12.sp, color = Color.LightGray, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                
                                val minOvr = 40f
                                val currentPct = ((player.overall - minOvr) / (99f - minOvr)).coerceIn(0f, 1f)
                                val potPct = ((player.potential - minOvr) / (99f - minOvr)).coerceIn(0f, 1f)
                                
                                Box(modifier = Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp)).background(Color.DarkGray)) {
                                    // Potential Background
                                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(potPct).background(Color(0xFF4CAF50).copy(alpha = 0.3f)))
                                    // Current OVR
                                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(currentPct).background(Color(0xFF4CAF50)))
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("OVR: ${player.overall}", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("POT: ${player.potential}", fontSize = 10.sp, color = Color(0xFF81C784), fontWeight = FontWeight.Bold)
                                }
                                
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = if (player.overall >= player.potential) "Reached Potential" else if (player.isAcademy) "Academy Prospect" else "Growing Player",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                }
                if (onMoveToAcademy != null && !player.isAcademy) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { onMoveToAcademy(player) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E676)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Move Back to Youth Academy", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (onRetirePlayer != null && !player.isAcademy && !player.status.equals("Retired", ignoreCase = true)) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onRetirePlayer(player) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFBBF24)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFFBBF24))
                        Spacer(Modifier.width(8.dp))
                        Text("Record Player Retirement", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MetricBox(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background).padding(8.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (highlight) MaterialTheme.colorScheme.onPrimary else Color.White)
            Text(label, fontSize = 10.sp, color = if (highlight) MaterialTheme.colorScheme.onPrimary.copy(alpha=0.8f) else Color.LightGray)
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 10.sp, color = Color.LightGray)
        Text(value, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
