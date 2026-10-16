package com.fctracker.career.ui.screens
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fctracker.career.MainViewModel
import com.fctracker.career.ai.AiClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(viewModel: com.fctracker.career.MainViewModel, onMenuClick: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val aiApiKey by viewModel.aiApiKey.collectAsState()
    val viewingSeason = state.historicalSeasons.find { it.year == state.viewingSeasonYear }
    val displayPlayers = viewingSeason?.players?.takeIf { it.isNotEmpty() } ?: state.players
    val displayMatches = viewingSeason?.matches?.takeIf { it.isNotEmpty() } ?: state.recentMatches
    val displayTransfers = viewingSeason?.transfers?.takeIf { it.isNotEmpty() } ?: state.transfers
    val squadValue = displayPlayers.sumOf { it.valueM }
    val displayYear = state.viewingSeasonYear ?: state.startYear
    var chatText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var showNotesModal by remember { mutableStateOf(false) }
    
    var chatHistory by remember { mutableStateOf(listOf(
        com.fctracker.career.ui.screens.ChatMessage("assistant", "Hello boss! I've analyzed the squad and upcoming fixtures. Let me know if you need tactical advice or scouting recommendations.")
    )) }
    
    var showSaveNoteContent by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        chatHistory = listOf(com.fctracker.career.ui.screens.ChatMessage("assistant", "Hello boss! I've analyzed the squad and upcoming fixtures. Let me know if you need tactical advice or scouting recommendations."))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("AI Assistant", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Saved Tactical Notes (${viewModel.savedNotes.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    TextButton(onClick = { showNotesModal = true }) {
                        Text("View Notes")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (viewModel.savedNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No notes saved.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                chatHistory = listOf(
                                    com.fctracker.career.ui.screens.ChatMessage("user", "Start Press Conference"),
                                    com.fctracker.career.ui.screens.ChatMessage("assistant", "The journalists have arrived. Let's begin the press conference. We're ready for your questions, boss.")
                                )
                            },
                            label = { Text("Press Conference", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                chatHistory = listOf(
                                    com.fctracker.career.ui.screens.ChatMessage("user", "Start Player Chat"),
                                    com.fctracker.career.ui.screens.ChatMessage("assistant", "Boss, I need to talk to you about my playing time...")
                                )
                            },
                            label = { Text("Player Chat", fontSize = 12.sp) }
                        )
                    }
                }
                items(chatHistory) { msg ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (msg.role == "user") Alignment.End else Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (msg.role == "user") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp)
                        ) {
                            Text(msg.text, color = if (msg.role == "user") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (msg.role == "assistant") {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(onClick = { 
                                val fullChat = chatHistory.joinToString("\n\n") { if (it.role == "user") "You: ${it.text}" else "Assistant: ${it.text}" }
                                showSaveNoteContent = fullChat 
                            }, contentPadding = PaddingValues(0.dp)) {
                                Text("Save Note", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            if (aiApiKey.isBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "API key needed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suggestions = listOf("Analyze Next Opponent", "Lineup Advice", "Scouting Recs")
                items(suggestions) { text ->
                    AssistChip(
                        onClick = { chatText = text },
                        label = { Text(text) }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = chatText,
                    onValueChange = { chatText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask the AI Assistant...") },
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = {
                        if (chatText.isNotBlank()) {
                            val userText = chatText
                            chatHistory = chatHistory + com.fctracker.career.ui.screens.ChatMessage("user", userText)
                            chatText = ""
                            chatHistory = chatHistory + com.fctracker.career.ui.screens.ChatMessage("assistant", "Thinking...")
                            coroutineScope.launch {
                                val contextStr = """
System Context: You are an expert AI assistant for a football manager app. 
Only answer questions related to football, managing a club, tactics, players, and the current career mode save. If asked about unrelated topics, politely decline and remind the user that you are a football manager assistant.
If the user says 'Press Conference', act as a tough journalist asking post-match questions based on the recent matches and team performance.
If the user says 'Player Chat', act as an unhappy player asking about their playtime or contract.
Act accordingly based on the persona of ${state.aiAgent}. 

-- CLUB PROFILE --
Current Club: ${state.clubName}
Manager: ${state.managerName}
League: ${state.leagueName} (${state.country})
Year: ${displayYear}
Budget: ${state.currencySymbol}${state.transferBudget}M
Squad Value: ${state.currencySymbol}${squadValue}M

-- ROSTER & ACADEMY (${displayPlayers.size} players) --
${displayPlayers.joinToString("\n") { "- ${it.displayName} (OVR: ${it.overall}, POT: ${it.potential}, Pos: ${it.primaryPosition}, Age: ${it.age}, Status: ${it.status}, Academy: ${it.isAcademy}, Injured: ${it.isInjured}, Form: ${it.form}, Morale: ${it.morale}, Apps: ${it.matchesPlayed}, G: ${it.goals}, A: ${it.assists}, AvgR: ${it.averageRating})" }}

-- MATCH HISTORY --
${displayMatches.joinToString("\n") { "- vs ${it.opponentName} (${it.competition}, ${it.matchDate}): ${it.goalsFor}-${it.goalsAgainst} (${it.resultBadge}). MOTM: ${it.motmName}. xG: ${it.expectedGoals} - ${it.expectedGoalsAgainst}" }}

-- TRANSFERS --
${displayTransfers.joinToString("\n") { "- ${it.date}: ${it.type} - ${it.playerName} (Fee: ${state.currencySymbol}${it.feeM}M) with ${it.fromToClub}" }}

-- TROPHIES & AWARDS --
Trophies: ${state.teamTrophies.joinToString(", ") { it.name }}
Player Awards: ${state.playerAwards.joinToString(", ") { "${it.name} (${it.playerId}, ${it.monthYear})" }}
"""
                                val fullPrompt = "$contextStr\n\nUser Question: $userText"
                                val reply = AiClient.ask(fullPrompt, state.aiAgent, state.aiApiUrl, aiApiKey, state.aiModel)
                                chatHistory = chatHistory.dropLast(1) + com.fctracker.career.ui.screens.ChatMessage("assistant", reply)
                            }
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { 
                        if (chatText.isNotBlank()) {
                            val userText = chatText
                            chatHistory = chatHistory + com.fctracker.career.ui.screens.ChatMessage("user", userText)
                            chatText = ""
                            chatHistory = chatHistory + com.fctracker.career.ui.screens.ChatMessage("assistant", "Thinking...")
                            coroutineScope.launch {
                                val contextStr = """
System Context: You are an expert AI assistant for a football manager app. 
Only answer questions related to football, managing a club, tactics, players, and the current career mode save. If asked about unrelated topics, politely decline and remind the user that you are a football manager assistant.
If the user says 'Press Conference', act as a tough journalist asking post-match questions based on the recent matches and team performance.
If the user says 'Player Chat', act as an unhappy player asking about their playtime or contract.
Act accordingly based on the persona of ${state.aiAgent}. 

-- CLUB PROFILE --
Current Club: ${state.clubName}
Manager: ${state.managerName}
League: ${state.leagueName} (${state.country})
Year: ${displayYear}
Budget: ${state.currencySymbol}${state.transferBudget}M
Squad Value: ${state.currencySymbol}${squadValue}M

-- ROSTER & ACADEMY (${displayPlayers.size} players) --
${displayPlayers.joinToString("\n") { "- ${it.displayName} (OVR: ${it.overall}, POT: ${it.potential}, Pos: ${it.primaryPosition}, Age: ${it.age}, Status: ${it.status}, Academy: ${it.isAcademy}, Injured: ${it.isInjured}, Form: ${it.form}, Morale: ${it.morale}, Apps: ${it.matchesPlayed}, G: ${it.goals}, A: ${it.assists}, AvgR: ${it.averageRating})" }}

-- MATCH HISTORY --
${displayMatches.joinToString("\n") { "- vs ${it.opponentName} (${it.competition}, ${it.matchDate}): ${it.goalsFor}-${it.goalsAgainst} (${it.resultBadge}). MOTM: ${it.motmName}. xG: ${it.expectedGoals} - ${it.expectedGoalsAgainst}" }}

-- TRANSFERS --
${displayTransfers.joinToString("\n") { "- ${it.date}: ${it.type} - ${it.playerName} (Fee: ${state.currencySymbol}${it.feeM}M) with ${it.fromToClub}" }}

-- TROPHIES & AWARDS --
Trophies: ${state.teamTrophies.joinToString(", ") { it.name }}
Player Awards: ${state.playerAwards.joinToString(", ") { "${it.name} (${it.playerId}, ${it.monthYear})" }}
"""
                                val fullPrompt = "$contextStr\n\nUser Question: $userText"
                                val reply = AiClient.ask(fullPrompt, state.aiAgent, state.aiApiUrl, aiApiKey, state.aiModel)
                                chatHistory = chatHistory.dropLast(1) + com.fctracker.career.ui.screens.ChatMessage("assistant", reply)
                            }
                        }
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
    if (showNotesModal) {
        NotesModal(viewModel = viewModel, onDismiss = { showNotesModal = false })
    }
    
    showSaveNoteContent?.let { contentToSave ->
        SaveNoteDialog(
            initialContent = contentToSave,
            onSave = { title, newContent ->
                if (viewModel.savedNotes.size < 5) {
                    viewModel.addNote(title, newContent)
                }
                showSaveNoteContent = null
            },
            onDismiss = { showSaveNoteContent = null },
            canSave = viewModel.savedNotes.size < 5
        )
    }
}

@Composable
fun SaveNoteDialog(initialContent: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit, canSave: Boolean) {
    var title by remember { mutableStateOf("New Tactical Note") }
    var content by remember { mutableStateOf(initialContent) }

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Save Note", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (!canSave) {
                    Text("Note limit reached (5/5). Please delete an existing note first.", color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Content") }, modifier = Modifier.fillMaxWidth().height(150.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onSave(title, content) }, enabled = canSave && title.isNotBlank()) { Text("Save") }
                }
            }
        }
    }
}


@Composable
fun NotesModal(viewModel: com.fctracker.career.MainViewModel, onDismiss: () -> Unit) {
    var selectedNoteId by remember { mutableStateOf<String?>(null) }
    var editingNoteId by remember { mutableStateOf<String?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selectedNoteId == null) {
                    item { Text("Select a Note to View", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                    items(viewModel.savedNotes, key = { it.id }) { note ->
                        DarkCard(modifier = Modifier.fillMaxWidth().clickable { selectedNoteId = note.id }) {
                            Text(note.title, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(8.dp))
                        }
                    }
                    if (viewModel.savedNotes.isEmpty()) {
                        item { Text("No notes saved.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Button(onClick = onDismiss) { Text("Close") }
                        }
                    }
                } else {
                    val note = viewModel.savedNotes.find { it.id == selectedNoteId }
                    if (note == null) {
                        item { Text("Note not found.") }
                        item { Button(onClick = { selectedNoteId = null }) { Text("Back") } }
                    } else {
                        item {
                            if (editingNoteId == note.id) {
                                OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(value = editContent, onValueChange = { editContent = it }, label = { Text("Content") }, modifier = Modifier.fillMaxWidth().height(150.dp))
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    TextButton(onClick = { editingNoteId = null }) { Text("Cancel") }
                                    Button(onClick = { 
                                        viewModel.updateNote(note.id, editTitle, editContent)
                                        editingNoteId = null
                                    }) { Text("Save") }
                                }
                            } else {
                                Text(note.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(note.content, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                                    TextButton(onClick = { selectedNoteId = null }) { Text("Back", color = Color.White) }
                                    Row(horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { 
                                            editTitle = note.title
                                            editContent = note.content
                                            editingNoteId = note.id
                                        }) { Text("Edit") }
                                        TextButton(onClick = { 
                                            viewModel.removeNote(note)
                                            selectedNoteId = null 
                                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


data class ChatMessage(val role: String, val text: String)
