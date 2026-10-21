package com.fctracker.career.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.fctracker.career.Player
import kotlin.math.hypot
import kotlin.math.roundToInt

data class FormationSlot(val x: Float, val y: Float, val roles: List<String>)

val formationsMap = mapOf(
    "4-3-3" to listOf(
        FormationSlot(0.5f, 0.85f, listOf("GK")),
        FormationSlot(0.15f, 0.65f, listOf("LB", "LWB")),
        FormationSlot(0.35f, 0.7f, listOf("CB")),
        FormationSlot(0.65f, 0.7f, listOf("CB")),
        FormationSlot(0.85f, 0.65f, listOf("RB", "RWB")),
        FormationSlot(0.25f, 0.45f, listOf("CM", "CDM", "CAM")),
        FormationSlot(0.5f, 0.5f, listOf("CM", "CDM", "CAM")),
        FormationSlot(0.75f, 0.45f, listOf("CM", "CDM", "CAM")),
        FormationSlot(0.2f, 0.2f, listOf("LW", "LM", "LF")),
        FormationSlot(0.5f, 0.15f, listOf("ST", "CF")),
        FormationSlot(0.8f, 0.2f, listOf("RW", "RM", "RF"))
    ),
    "4-2-3-1" to listOf(
        FormationSlot(0.5f, 0.85f, listOf("GK")),
        FormationSlot(0.15f, 0.65f, listOf("LB", "LWB")),
        FormationSlot(0.35f, 0.7f, listOf("CB")),
        FormationSlot(0.65f, 0.7f, listOf("CB")),
        FormationSlot(0.85f, 0.65f, listOf("RB", "RWB")),
        FormationSlot(0.35f, 0.55f, listOf("CDM", "CM")),
        FormationSlot(0.65f, 0.55f, listOf("CDM", "CM")),
        FormationSlot(0.2f, 0.35f, listOf("CAM", "LM", "LW")),
        FormationSlot(0.5f, 0.3f, listOf("CAM", "CM")),
        FormationSlot(0.8f, 0.35f, listOf("CAM", "RM", "RW")),
        FormationSlot(0.5f, 0.15f, listOf("ST", "CF"))
    ),
    "4-4-2" to listOf(
        FormationSlot(0.5f, 0.85f, listOf("GK")),
        FormationSlot(0.15f, 0.65f, listOf("LB", "LWB")),
        FormationSlot(0.35f, 0.7f, listOf("CB")),
        FormationSlot(0.65f, 0.7f, listOf("CB")),
        FormationSlot(0.85f, 0.65f, listOf("RB", "RWB")),
        FormationSlot(0.15f, 0.45f, listOf("LM", "LW")),
        FormationSlot(0.35f, 0.45f, listOf("CM", "CDM")),
        FormationSlot(0.65f, 0.45f, listOf("CM", "CDM")),
        FormationSlot(0.85f, 0.45f, listOf("RM", "RW")),
        FormationSlot(0.35f, 0.15f, listOf("ST", "CF")),
        FormationSlot(0.65f, 0.15f, listOf("ST", "CF"))
    ),
    "3-5-2" to listOf(
        FormationSlot(0.5f, 0.85f, listOf("GK")),
        FormationSlot(0.25f, 0.75f, listOf("CB")),
        FormationSlot(0.5f, 0.75f, listOf("CB")),
        FormationSlot(0.75f, 0.75f, listOf("CB")),
        FormationSlot(0.15f, 0.45f, listOf("LWB", "LM")),
        FormationSlot(0.35f, 0.55f, listOf("CDM", "CM")),
        FormationSlot(0.5f, 0.4f, listOf("CAM", "CM")),
        FormationSlot(0.65f, 0.55f, listOf("CDM", "CM")),
        FormationSlot(0.85f, 0.45f, listOf("RWB", "RM")),
        FormationSlot(0.35f, 0.15f, listOf("ST", "CF")),
        FormationSlot(0.65f, 0.15f, listOf("ST", "CF"))
    ),
    "5-3-2" to listOf(
        FormationSlot(0.5f, 0.85f, listOf("GK")),
        FormationSlot(0.1f, 0.65f, listOf("LWB", "LB")),
        FormationSlot(0.3f, 0.75f, listOf("CB")),
        FormationSlot(0.5f, 0.75f, listOf("CB")),
        FormationSlot(0.7f, 0.75f, listOf("CB")),
        FormationSlot(0.9f, 0.65f, listOf("RWB", "RB")),
        FormationSlot(0.25f, 0.45f, listOf("CM", "CDM")),
        FormationSlot(0.5f, 0.45f, listOf("CM", "CAM")),
        FormationSlot(0.75f, 0.45f, listOf("CM", "CDM")),
        FormationSlot(0.35f, 0.15f, listOf("ST", "CF")),
        FormationSlot(0.65f, 0.15f, listOf("ST", "CF"))
    )
)

fun applySmartFormation(players: List<Player>, formationName: String): List<Player>? {
    val layout = formationsMap[formationName] ?: return null
    val nonAcademy = players.filter { !it.isAcademy && !it.status.equals("Academy", ignoreCase = true) }
    val starting = nonAcademy.filter { it.isStartingXI }.take(11)
    
    val unassignedPlayers = starting.toMutableList()
    val assignments = mutableMapOf<FormationSlot, Player>()
    
    // Pass 1: Primary Position (Highest Overall first)
    for (slot in layout) {
        val match = unassignedPlayers.filter { it.primaryPosition in slot.roles }.maxByOrNull { it.overall }
        if (match != null) {
            assignments[slot] = match
            unassignedPlayers.remove(match)
        }
    }
    
    // Pass 2: Secondary Position
    for (slot in layout.filter { !assignments.containsKey(it) }) {
        val match = unassignedPlayers.filter { it.secondaryPosition in slot.roles }.maxByOrNull { it.overall }
        if (match != null) {
            assignments[slot] = match
            unassignedPlayers.remove(match)
        }
    }
    
    // Pass 3: Tertiary Position
    for (slot in layout.filter { !assignments.containsKey(it) }) {
        val match = unassignedPlayers.filter { it.tertiaryPosition in slot.roles }.maxByOrNull { it.overall }
        if (match != null) {
            assignments[slot] = match
            unassignedPlayers.remove(match)
        }
    }
    
    // Pass 4: Fallback (Fill remaining slots with highest overall players left)
    val remainingSlots = layout.filter { !assignments.containsKey(it) }
    remainingSlots.forEach { slot ->
        val match = unassignedPlayers.maxByOrNull { it.overall }
        if (match != null) {
            assignments[slot] = match
            unassignedPlayers.remove(match)
        }
    }
    
    return assignments.map { (slot, player) ->
        player.copy(pitchX = slot.x, pitchY = slot.y)
    }
}

@Composable
fun FormationTab(
    players: List<Player>,
    modifier: Modifier = Modifier,
    onPlayerUpdate: (Player) -> Unit = {},
    onApplyFormation: (List<Player>) -> Unit = {},
    onSwapPlayers: (String, String) -> Unit = { _, _ -> },
    onPlayerClick: ((Player) -> Unit)? = null,
    activeFormation: String = "4-3-3",
    onActiveFormationChange: (String) -> Unit = {},
    header: @Composable () -> Unit = {},
    readOnly: Boolean = false
) {
    val eligiblePlayers = remember(players) {
        players.filter { !it.isAcademy && !it.status.equals("Academy", ignoreCase = true) }
    }
    
    var draggedPlayer by remember { mutableStateOf<Player?>(null) }
    var dragCenterGlobal by remember { mutableStateOf(Offset.Zero) }
    val localDensity = androidx.compose.ui.platform.LocalDensity.current
    
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var pitchBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    
    val formations = listOf("4-3-3", "4-2-3-1", "4-4-2", "3-5-2", "5-3-2", "Custom")
    var formationExpanded by remember { mutableStateOf(false) }
    
    var selectedFormation by remember(activeFormation) { mutableStateOf(activeFormation) }
    val benchPositions = remember { androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Offset>() }
    
    var initialLoadDone by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        if (!initialLoadDone) {
            initialLoadDone = true
            val starting = eligiblePlayers.filter { it.isStartingXI }
            val hasPositions = starting.isNotEmpty() && starting.any { it.pitchX > 0f || it.pitchY > 0f }
            if (!hasPositions && activeFormation != "Custom") {
                val updated = applySmartFormation(eligiblePlayers, activeFormation)
                if (updated != null) {
                    onApplyFormation(updated)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().padding(16.dp).onGloballyPositioned { rootPos = it.positionInRoot() }) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            header()
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    @OptIn(ExperimentalMaterial3Api::class)
                    ExposedDropdownMenuBox(
                        expanded = formationExpanded,
                        onExpandedChange = { formationExpanded = !formationExpanded },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedFormation,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Formation Template") },
                            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formationExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = formationExpanded,
                            onDismissRequest = { formationExpanded = false }
                        ) {
                            formations.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        selectedFormation = selectionOption
                                        onActiveFormationChange(selectionOption)
                                        if (selectionOption != "Custom") {
                                            val updated = applySmartFormation(eligiblePlayers, selectionOption)
                                            if (updated != null) {
                                                onApplyFormation(updated)
                                            }
                                        }
                                        formationExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(490.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2E7D32)) 
                            .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .onGloballyPositioned { coordinates ->
                                pitchBounds = coordinates.boundsInRoot()
                            }
                    ) {
                    // Pitch Markings
                    Box(modifier = Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.3f)))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.3f)).align(Alignment.Center))
                    Box(modifier = Modifier.size(60.dp).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape).align(Alignment.Center))
                    Box(modifier = Modifier.size(100.dp, 60.dp).border(1.dp, Color.White.copy(alpha = 0.3f)).align(Alignment.TopCenter))
                    Box(modifier = Modifier.size(100.dp, 60.dp).border(1.dp, Color.White.copy(alpha = 0.3f)).align(Alignment.BottomCenter))
                    
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val width = maxWidth.value
                        val height = maxHeight.value
                        
                        eligiblePlayers.filter { it.isStartingXI }.forEach { player ->
                            val isDragged = draggedPlayer?.id == player.id
                            var myGlobalPos by remember { mutableStateOf(Offset.Zero) }
                            
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = (player.pitchX * width - 30).dp, // Adjust offset to center icon (approx 60dp width)
                                        y = (player.pitchY * height - 30).dp
                                    )
                                    .onGloballyPositioned { 
                                        myGlobalPos = it.positionInRoot() 
                                        benchPositions[player.id] = androidx.compose.ui.geometry.Offset(myGlobalPos.x + it.size.width / 2f, myGlobalPos.y + it.size.height / 2f)
                                    }
                                    .pointerInput(player) {
                                        detectTapGestures(
                                            onTap = {
                                                onPlayerClick?.invoke(player)
                                            }
                                        )
                                    }
                                    .pointerInput(player) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                draggedPlayer = player
                                                dragCenterGlobal = Offset(myGlobalPos.x + offset.x, myGlobalPos.y + offset.y)
                                                selectedFormation = "Custom"
                                                onActiveFormationChange("Custom")
                                            },
                                            onDragEnd = {
                                                val finalGlobalY = dragCenterGlobal.y
                                                val finalGlobalX = dragCenterGlobal.x
                                                
                                                if (finalGlobalY > pitchBounds.bottom || finalGlobalY < pitchBounds.top) {
                                                    onPlayerUpdate(player.copy(isStartingXI = false))
                                                } else {
                                                    val relativeX = (finalGlobalX - pitchBounds.left) / pitchBounds.width
                                                    val relativeY = (finalGlobalY - pitchBounds.top) / pitchBounds.height
                                                    onPlayerUpdate(player.copy(pitchX = relativeX.coerceIn(0f, 1f), pitchY = relativeY.coerceIn(0f, 1f)))
                                                }
                                                draggedPlayer = null
                                            },
                                            onDragCancel = { draggedPlayer = null }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            dragCenterGlobal += dragAmount
                                        }
                                    }
                                    .alpha(if (isDragged) 0f else 1f)
                            ) {
                                PlayerPitchIcon(player)
                            }
                        }
                    }
                }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val benchPlayers = eligiblePlayers.filter { !it.isStartingXI }
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Bench & Reserves (${benchPlayers.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "4 players / row",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).verticalScroll(rememberScrollState())
                ) {
                    benchPlayers.chunked(4).forEach { rowPlayers ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            rowPlayers.forEach { player ->
                                val isDragged = draggedPlayer?.id == player.id
                                var myGlobalPos by remember { mutableStateOf(Offset.Zero) }
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(vertical = 6.dp, horizontal = 4.dp)
                                        .onGloballyPositioned { 
                                            myGlobalPos = it.positionInRoot() 
                                            benchPositions[player.id] = androidx.compose.ui.geometry.Offset(myGlobalPos.x + it.size.width / 2f, myGlobalPos.y + it.size.height / 2f)
                                        }
                                        .pointerInput(player) {
                                            detectTapGestures(
                                                onTap = {
                                                    onPlayerClick?.invoke(player)
                                                }
                                            )
                                        }
                                        .pointerInput(player) {
                                            detectDragGestures(
                                                onDragStart = { offset -> 
                                                    draggedPlayer = player
                                                    dragCenterGlobal = Offset(myGlobalPos.x + offset.x, myGlobalPos.y + offset.y)
                                                    selectedFormation = "Custom"
                                                    onActiveFormationChange("Custom")
                                                },
                                                onDragEnd = {
                                                    val finalGlobalY = dragCenterGlobal.y
                                                    val finalGlobalX = dragCenterGlobal.x
                                                    
                                                    if (finalGlobalY < pitchBounds.bottom && finalGlobalY > pitchBounds.top &&
                                                        finalGlobalX > pitchBounds.left && finalGlobalX < pitchBounds.right) {
                                                        
                                                        val startingPlayers = eligiblePlayers.filter { it.isStartingXI }
                                                        var swapped = false
                                                        if (startingPlayers.isNotEmpty()) {
                                                            val closestPlayer = startingPlayers.minByOrNull {
                                                                val px = pitchBounds.left + it.pitchX * pitchBounds.width
                                                                val py = pitchBounds.top + it.pitchY * pitchBounds.height
                                                                hypot((px - finalGlobalX).toDouble(), (py - finalGlobalY).toDouble())
                                                            }
                                                            if (closestPlayer != null) {
                                                                val px = pitchBounds.left + closestPlayer.pitchX * pitchBounds.width
                                                                val py = pitchBounds.top + closestPlayer.pitchY * pitchBounds.height
                                                                if (hypot((px - finalGlobalX).toDouble(), (py - finalGlobalY).toDouble()) < 150.0) {
                                                                    onPlayerUpdate(closestPlayer.copy(isStartingXI = false))
                                                                    onPlayerUpdate(player.copy(isStartingXI = true, pitchX = closestPlayer.pitchX, pitchY = closestPlayer.pitchY))
                                                                    swapped = true
                                                                }
                                                            }
                                                        }
                                                        
                                                        if (!swapped) {
                                                            if (startingPlayers.size < 11) {
                                                                val relativeX = (finalGlobalX - pitchBounds.left) / pitchBounds.width
                                                                val relativeY = (finalGlobalY - pitchBounds.top) / pitchBounds.height
                                                                onPlayerUpdate(player.copy(isStartingXI = true, pitchX = relativeX.coerceIn(0f, 1f), pitchY = relativeY.coerceIn(0f, 1f)))
                                                            }
                                                        }
                                                    } else {
                                                        val closestBenchPlayer = benchPositions.entries.minByOrNull {
                                                            kotlin.math.hypot((it.value.x - finalGlobalX).toDouble(), (it.value.y - finalGlobalY).toDouble())
                                                        }
                                                        if (closestBenchPlayer != null && closestBenchPlayer.key != player.id && kotlin.math.hypot((closestBenchPlayer.value.x - finalGlobalX).toDouble(), (closestBenchPlayer.value.y - finalGlobalY).toDouble()) < 150.0) {
                                                            onSwapPlayers(player.id, closestBenchPlayer.key)
                                                        }
                                                    }
                                                    draggedPlayer = null
                                                },
                                                onDragCancel = { draggedPlayer = null }
                                            ) { change, dragAmount ->
                                                change.consume()
                                                dragCenterGlobal += dragAmount
                                            }
                                        }
                                        .alpha(if (isDragged) 0f else 1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BenchPlayerIcon(player)
                                }
                            }
                            for (i in 0 until (4 - rowPlayers.size)) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Overlay for Dragged Player (Snaps center perfectly to finger)
        if (draggedPlayer != null) {
            val iconCenterOffsetX = with(localDensity) { 30.dp.toPx() }
            val iconCenterOffsetY = with(localDensity) { 45.dp.toPx() } // Approx half of total icon height
            
            val localX = dragCenterGlobal.x - rootPos.x - iconCenterOffsetX
            val localY = dragCenterGlobal.y - rootPos.y - iconCenterOffsetY
            
            Box(
                modifier = Modifier
                    .offset { IntOffset(localX.roundToInt(), localY.roundToInt()) }
                    .zIndex(100f)
            ) {
                PlayerPitchIcon(draggedPlayer!!)
            }
        }
    }
}

fun isValidPlayerHeadshot(url: String): Boolean {
    if (url.isBlank()) return false
    val lower = url.lowercase()
    if (lower.contains("crest") || lower.contains("clubs") || lower.contains("logo") || lower.contains("icon.horse")) return false
    return true
}

@Composable
fun PlayerPitchIcon(player: Player) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        if (player.isInjured) {
            Box(modifier = Modifier.background(Color.Red.copy(alpha=0.85f), RoundedCornerShape(4.dp)).padding(2.dp)) {
                Text("⚠️ Inj", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isValidPlayerHeadshot(player.headshotUrl)) {
                coil.compose.SubcomposeAsyncImage(
                    model = player.headshotUrl,
                    contentDescription = "Player Face",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    error = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                )
            } else {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        
        // Name and Overall horizontally aligned
        Row(
            modifier = Modifier.background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                player.formationDisplayName,
                fontSize = 9.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                player.overall.toString(),
                fontSize = 9.sp,
                color = Color(0xFFFDD835), // Gold color for overall
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        
        // Positions
        val positions = listOfNotNull(player.primaryPosition, player.secondaryPosition).filter { it.isNotBlank() }.joinToString(",")
        Text(
            positions,
            fontSize = 7.sp,
            color = Color.LightGray,
            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun BenchPlayerIcon(player: Player) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (player.isInjured) {
            Box(
                modifier = Modifier
                    .background(Color.Red.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text("⚠️ Inj", fontSize = 7.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
        
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isValidPlayerHeadshot(player.headshotUrl)) {
                coil.compose.SubcomposeAsyncImage(
                    model = player.headshotUrl,
                    contentDescription = "Player Face",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    error = {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        
        Text(
            player.formationDisplayName,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(2.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                player.primaryPosition,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                player.overall.toString(),
                fontSize = 9.sp,
                color = Color(0xFFFDD835),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
