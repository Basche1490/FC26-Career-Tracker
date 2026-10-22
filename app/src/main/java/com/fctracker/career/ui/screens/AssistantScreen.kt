package com.fctracker.career.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.MainViewModel
import com.fctracker.career.Note
import com.fctracker.career.ai.Assistant
import com.fctracker.career.ai.ChatTurn
import com.fctracker.career.logic.CareerOps
import com.fctracker.career.ui.components.ButtonKind
import com.fctracker.career.ui.components.ConfirmDialog
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.FcCard
import com.fctracker.career.ui.components.FcChip
import com.fctracker.career.ui.components.FcDialog
import com.fctracker.career.ui.components.FcField
import com.fctracker.career.ui.components.FcIconButton
import com.fctracker.career.ui.components.FcLabel
import com.fctracker.career.ui.components.FcSelectField
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.FcTopBar
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.Notice
import com.fctracker.career.ui.components.Pill
import com.fctracker.career.ui.components.SectionHeader
import com.fctracker.career.ui.components.Tone
import com.fctracker.career.ui.theme.FcTheme

const val MAX_NOTES = 5

/** What the assistant reads: every page of the app (shown on the "Knows your whole career" card). */
val ASSISTANT_KNOWS = listOf(
    "Matches and lineups", "Fixtures", "League tables", "Squad, contracts, wages", "Injuries", "Formation and tactics", "Transfers and budget",
    "Scout reports", "Youth academy", "Stats and awards", "Cup runs", "Europe ranking", "Around the World", "Objectives", "Milestones",
    "Past seasons", "Club history", "Player notes", "Your notes"
)

private fun isProblem(text: String) = text.startsWith("API key needed") || text.startsWith("Error:") || text.startsWith("Error ")

/** The AI assistant: answers questions about this career only, from everything the app holds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(viewModel: MainViewModel, onMenuClick: () -> Unit, nav: (String) -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val apiKey by viewModel.aiApiKey.collectAsState()
    val c = FcTheme.colors
    val chat = viewModel.assistantChat
    var mode by remember { mutableStateOf(Assistant.MODE_CHAT) }
    var talkTo by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var waiting by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf<String?>(null) }
    var showNotes by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val next = CareerOps.nextFixture(state)
    val squadNames = state.players.filter { isActivePlayer(it) }.sortedByDescending { it.overall }.map { it.displayName }
    val prompts = when (mode) {
        Assistant.MODE_PRESS -> listOf("Pre-match press conference${next?.let { " before ${it.opponent}" } ?: ""}", "Press conference after the last match", "Questions about the season so far")
        Assistant.MODE_PLAYER -> listOf("How do you feel about your playing time?", "Talk about your contract", "What do you think of the team's form?")
        else -> listOfNotNull(next?.let { "Analyse ${it.opponent}" } ?: "Analyse next opponent", "Lineup advice", "Contracts to renew", "Who should I sell?", "Academy players ready to step up", "Where should I strengthen?")
    }

    fun send(message: String) {
        val m = message.trim()
        if (m.isEmpty() || waiting) return
        val full = if (mode == Assistant.MODE_PLAYER && talkTo.isNotBlank()) "(Talking to $talkTo) $m" else m
        val history = chat.toList()
        chat.add(ChatTurn("user", full))
        text = ""
        waiting = true
        viewModel.askAssistant(full, mode, history) { reply, local ->
            val problem = isProblem(reply)
            // A question the app answered itself (or one that failed) is kept out of the agent's conversation.
            if (local || problem) {
                val i = chat.indexOfLast { it.role == "user" && it.text == full }
                if (i >= 0) chat[i] = chat[i].copy(local = true)
            }
            chat.add(ChatTurn("assistant", reply, local || problem))
            waiting = false
        }
    }

    LaunchedEffect(chat.size, waiting) { if (chat.isNotEmpty()) list.animateScrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        Column(Modifier.statusBarsPadding()) {
            FcTopBar(
                "AI Assistant",
                sub = "${state.aiAgent.substringBefore(" -").ifBlank { "AI" }} · knows your career",
                onNav = onMenuClick,
                actions = {
                    Box {
                        FcIconButton(Icons.Default.StickyNote2, "Saved notes (${viewModel.savedNotes.size})", { showNotes = true }, border = false)
                        if (viewModel.savedNotes.isNotEmpty()) Box(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp).clip(RoundedCornerShape(8.dp)).background(c.accent).padding(horizontal = 5.dp)) {
                            FcText("${viewModel.savedNotes.size}", 10.sp, FontWeight.Bold, c.onAccent)
                        }
                    }
                }
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FcChip("Press conference", mode == Assistant.MODE_PRESS, { mode = if (mode == Assistant.MODE_PRESS) Assistant.MODE_CHAT else Assistant.MODE_PRESS })
                    FcChip("Player chat", mode == Assistant.MODE_PLAYER, { mode = if (mode == Assistant.MODE_PLAYER) Assistant.MODE_CHAT else Assistant.MODE_PLAYER })
                }
            }
            if (mode == Assistant.MODE_PLAYER) item {
                FcSelectField("Talking to", talkTo.ifBlank { "Choose a player" }, squadNames, { talkTo = it }, hint = "The assistant answers as this player, from his numbers and contract.")
            }
            if (apiKey.isBlank()) item {
                Notice(Tone.WARN, "API key needed", "Add the key of your AI agent (${state.aiAgent}) in Settings. Questions about anything other than your career are still answered here, without the agent.",
                    icon = Icons.Default.Key, actions = { FcButton("Open Settings", { nav("settings") }, small = true) })
            }
            if (chat.isEmpty()) item {
                Muted("Ask anything about your career: the squad, matches, transfers, tactics or your next opponent.", 13.sp)
            }
            itemsIndexed(chat.toList()) { _, t ->
                Bubble(t.text, mine = t.role == "user", local = t.local, onSave = if (t.role == "assistant" && !t.local) ({ saving = t.text }) else null)
            }
            if (waiting) item { Bubble("Thinking…", mine = false, local = true) }
        }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(prompts) { _, p -> FcChip(p, false, { send(p) }) }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FcField("Message", text, { text = it.take(1500) }, Modifier.weight(1f), placeholder = "Ask about your career…", singleLine = false)
                FcIconButton(Icons.AutoMirrored.Filled.Send, "Send message", { send(text) }, size = 48.dp, background = c.accent, tint = c.onAccent, border = false)
            }
        }
    }

    saving?.let { content ->
        SaveNoteDialog(content, canSave = viewModel.savedNotes.size < MAX_NOTES, onSave = { title, body -> viewModel.addNote(title, body); saving = null }, onDismiss = { saving = null })
    }
    if (showNotes) NotesDialog(viewModel, onDismiss = { showNotes = false })
}

@Composable
private fun Bubble(text: String, mine: Boolean, local: Boolean = false, onSave: (() -> Unit)? = null) {
    val c = FcTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 6.dp, bottomEnd = if (mine) 6.dp else 18.dp))
                .background(if (mine) c.accent else c.surface2).let { if (!mine) it.border(1.dp, c.border, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp)) else it }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FcText(text, 14.sp, color = if (mine) c.onAccent else if (local) c.muted else c.text, lineHeight = 20.sp)
            if (onSave != null) {
                Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSave).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.material3.Icon(Icons.Default.BookmarkAdd, null, tint = c.accent, modifier = Modifier.size(16.dp))
                    FcText("Save note", 12.sp, FontWeight.SemiBold, c.accent)
                }
            }
        }
    }
}

@Composable
fun SaveNoteDialog(initialContent: String, canSave: Boolean, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf(initialContent) }
    FcDialog("Save note", onDismiss, actions = {
        FcButton("Cancel", onDismiss, kind = ButtonKind.SECONDARY, small = true)
        FcButton("Save", { onSave(title.trim(), content.trim()) }, small = true, enabled = canSave && title.isNotBlank())
    }) {
        if (!canSave) Notice(Tone.WARN, "Note limit reached ($MAX_NOTES/$MAX_NOTES)", "Delete a note first: open your notes from the top of the page.", icon = Icons.Default.Lock)
        FcField("Title", title, { title = it }, placeholder = "Plan against Hull City")
        FcField("Note", content, { content = it }, singleLine = false, minLines = 4)
        Muted("Your notes are part of what the assistant reads.", 11.sp)
    }
}

@Composable
private fun NotesDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    var open by remember { mutableStateOf<Note?>(null) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Note?>(null) }
    val note = open?.let { o -> viewModel.savedNotes.firstOrNull { it.id == o.id } }
    if (note == null) {
        FcDialog("Saved notes (${viewModel.savedNotes.size}/$MAX_NOTES)", onDismiss, actions = { FcButton("Close", onDismiss, small = true) }) {
            if (viewModel.savedNotes.isEmpty()) Muted("No notes saved. Tap Save note under an answer to keep it.", 13.sp)
            viewModel.savedNotes.forEach { n ->
                FcCard(Modifier.fillMaxWidth(), padding = 12.dp, gap = 4.dp, onClick = { open = n; editing = false }) {
                    FcText(n.title, 14.sp, FontWeight.SemiBold)
                    Muted(n.content, 12.sp, maxLines = 2)
                }
            }
        }
    } else if (editing) {
        var title by remember(note.id) { mutableStateOf(note.title) }
        var content by remember(note.id) { mutableStateOf(note.content) }
        FcDialog("Edit note", { editing = false }, actions = {
            FcButton("Cancel", { editing = false }, kind = ButtonKind.SECONDARY, small = true)
            FcButton("Save", { viewModel.updateNote(note.id, title.trim(), content.trim()); editing = false }, small = true, enabled = title.isNotBlank())
        }) {
            FcField("Title", title, { title = it })
            FcField("Note", content, { content = it }, singleLine = false, minLines = 5)
        }
    } else {
        FcDialog(note.title, { open = null }, actions = {
            FcButton("Delete", { deleting = note }, kind = ButtonKind.DANGER, icon = Icons.Default.Delete, small = true)
            FcButton("Edit", { editing = true }, kind = ButtonKind.SECONDARY, icon = Icons.Default.Edit, small = true)
            FcButton("Back", { open = null }, small = true)
        }) { FcText(note.content, 14.sp, lineHeight = 20.sp) }
    }
    deleting?.let { d -> ConfirmDialog("Delete this note?", d.title, "Delete", { viewModel.removeNote(d); open = null }, { deleting = null }) }
}
