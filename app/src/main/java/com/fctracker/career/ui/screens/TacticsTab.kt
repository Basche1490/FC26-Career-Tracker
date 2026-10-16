package com.fctracker.career.ui.screens

import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.AppState
import com.fctracker.career.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TacticalVisionsTab(state: AppState, viewModel: MainViewModel) {
    var selectedVision by remember { mutableStateOf("Standard") }
    var visionExpanded by remember { mutableStateOf(false) }
    val tacticalVisions = listOf("Standard", "Wing Play", "Tiki Taka", "Gegenpressing", "Park the Bus", "Counter Attack", "Kick and Rush")
    
    val visionDescriptions = mapOf(
        "Standard" to "A balanced approach to the game, focusing on maintaining shape and adapting to the situation.",
        "Wing Play" to "Utilizing the full width of the pitch to stretch the opponent's defense and create crossing opportunities.",
        "Tiki Taka" to "Possession-based football characterized by short passing and movement, maintaining control of the ball.",
        "Gegenpressing" to "Intense high pressing immediately after losing possession to win the ball back high up the pitch.",
        "Park the Bus" to "A highly defensive strategy focusing on defending deep in numbers and frustrating the opponent.",
        "Counter Attack" to "Sitting deep and absorbing pressure before rapidly transitioning into attack when the ball is won.",
        "Kick and Rush" to "Direct, physical football bypassing the midfield with long balls to strong forwards."
    )

    var hiredCoaches by remember { mutableStateOf(mapOf<String, String>()) }
    var showHireDialog by remember { mutableStateOf<String?>(null) }
    
    if (showHireDialog != null) {
        Dialog(onDismissRequest = { showHireDialog = null }) {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Hire ${showHireDialog} Coach", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    val availableCoaches = listOf("Expert (5 Stars)", "Senior (4 Stars)", "Standard (3 Stars)", "Novice (2 Stars)")
                    availableCoaches.forEach { coach ->
                        TextButton(onClick = { 
                            val mut = hiredCoaches.toMutableMap()
                            mut[showHireDialog!!] = coach
                            hiredCoaches = mut
                            showHireDialog = null
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(coach)
                        }
                    }
                    TextButton(onClick = { showHireDialog = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel", color = Color.White)
                    }
                }
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Tactical Vision", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Define your team's overarching identity. Coaches will boost player OVR based on this vision.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            ExposedDropdownMenuBox(
                expanded = visionExpanded,
                onExpandedChange = { visionExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedVision,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Active Tactical Vision") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = visionExpanded) },
                    modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = visionExpanded,
                    onDismissRequest = { visionExpanded = false }
                ) {
                    tacticalVisions.forEach { vision ->
                        DropdownMenuItem(
                            text = { Text(vision) },
                            onClick = {
                                selectedVision = vision
                                visionExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(visionDescriptions[selectedVision] ?: "", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Coaching Staff", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        val departments = listOf("Attack", "Midfield", "Defense", "Goalkeeping")
        items(departments.size) { index ->
            val dept = departments[index]
            val currentCoach = hiredCoaches[dept]
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("$dept Department", fontWeight = FontWeight.Bold, color = Color.White)
                        if (currentCoach != null) {
                            Text("Coach: $currentCoach", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        } else {
                            Text("No Coach Hired", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                    Button(onClick = { showHireDialog = dept }) {
                        Text(if (currentCoach != null) "Replace" else "Hire")
                    }
                }
            }
        }
    }
}
