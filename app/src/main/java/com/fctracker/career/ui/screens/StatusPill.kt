package com.fctracker.career.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.MainViewModel
import com.fctracker.career.telemetry.Level
import com.fctracker.career.telemetry.StepState
import com.fctracker.career.ui.theme.StatusDown
import com.fctracker.career.ui.theme.StatusIdle
import com.fctracker.career.ui.theme.StatusLive
import com.fctracker.career.ui.theme.StatusWarn

fun levelColor(level: Level): Color = when (level) {
    Level.LIVE -> StatusLive
    Level.ATTENTION -> StatusWarn
    Level.DOWN -> StatusDown
}

fun stepColor(state: StepState): Color = when (state) {
    StepState.OK -> StatusLive
    StepState.WARN -> StatusWarn
    StepState.FAIL -> StatusDown
    StepState.UNKNOWN -> StatusIdle
}

/** The same pill on every screen: Live, Check, Offline or Script off. A tap opens the Connection screen. */
@Composable
fun StatusPill(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val health by viewModel.health.collectAsState()
    StatusPill(
        level = health.level,
        label = health.pill,
        onClick = { viewModel.navigateToScreen("connection") },
        modifier = modifier
    )
}

@Composable
fun StatusPill(level: Level, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = levelColor(level)
    // A calm pulse while everything is live; the other states hold still so they read as "needs a look".
    val pulse = rememberInfiniteTransition(label = "pill")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot"
    )
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Connection status: $label. Tap for details." }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(8.dp)
                .alpha(if (level == Level.LIVE) dotAlpha else 1f)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}
