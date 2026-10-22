package com.fctracker.career.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.AppState
import com.fctracker.career.MainViewModel
import com.fctracker.career.ui.components.ButtonKind
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.FcCard
import com.fctracker.career.ui.components.FcDialog
import com.fctracker.career.ui.components.FcSelectField
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.Notice
import com.fctracker.career.ui.components.SectionHeader
import com.fctracker.career.ui.components.Tone

val TACTICAL_VISIONS = linkedMapOf(
    "Standard" to "A balanced approach: keep the shape and adapt to the match.",
    "Wing Play" to "Stretch the pitch and cross early.",
    "Tiki Taka" to "Short passing and keeping the ball.",
    "Gegenpressing" to "Win it back high, straight after losing it.",
    "Park the Bus" to "Sit deep in numbers and protect the lead.",
    "Counter Attack" to "Absorb pressure, hit quickly on the break.",
    "Kick and Rush" to "Long balls to strong forwards, and the second balls."
)
val COACH_DEPARTMENTS = listOf("Attack", "Midfield", "Defense", "Goalkeeping")
val COACH_LEVELS = listOf("Expert (5 stars)", "Senior (4 stars)", "Standard (3 stars)", "Novice (2 stars)")

/** Squad · Tactics: the tactical vision and the coaching staff, saved with the career (the assistant reads them too). */
@Composable
fun TacticalVisionsTab(state: AppState, viewModel: MainViewModel) {
    var hiring by remember { mutableStateOf<String?>(null) }
    val vision = state.tacticalVision.ifBlank { "Standard" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                SectionHeader("Tactical vision", icon = Icons.Default.Lightbulb)
                Muted("Pick the style you play. Coaches boost player OVR when their department fits this vision.", 12.sp)
                FcSelectField("Active tactical vision", vision, TACTICAL_VISIONS.keys.toList(), { viewModel.setTacticalVision(it) })
                Notice(Tone.INFO, vision, TACTICAL_VISIONS[vision] ?: "")
                Muted("Other options: " + TACTICAL_VISIONS.keys.filter { it != vision }.joinToString(" · "), 11.sp)
            }
        }
        item { SectionHeader("Coaching staff", icon = Icons.Default.Groups) }
        COACH_DEPARTMENTS.forEach { dept ->
            item(key = dept) {
                val coach = state.coaches[dept]
                FcCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            FcText("$dept department", 14.sp, FontWeight.Bold)
                            Muted(coach?.let { "Coach: $it" } ?: "No coach hired", 12.sp)
                        }
                        FcButton(if (coach != null) "Replace" else "Hire", { hiring = dept }, kind = ButtonKind.SECONDARY, small = true)
                    }
                }
            }
        }
        item { Muted("Saved with your career, and part of what the AI assistant knows.", 11.sp) }
    }
    hiring?.let { dept ->
        FcDialog("Hire $dept coach", { hiring = null }, actions = {
            if (state.coaches[dept] != null) FcButton("Release", { viewModel.setCoach(dept, null); hiring = null }, kind = ButtonKind.DANGER, small = true)
            FcButton("Cancel", { hiring = null }, kind = ButtonKind.SECONDARY, small = true)
        }) {
            COACH_LEVELS.forEach { level ->
                FcButton(level, { viewModel.setCoach(dept, level); hiring = null }, kind = if (state.coaches[dept] == level) ButtonKind.PRIMARY else ButtonKind.SECONDARY, full = true)
            }
        }
    }
}
