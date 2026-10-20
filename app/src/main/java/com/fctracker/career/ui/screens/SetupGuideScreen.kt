package com.fctracker.career.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.MainViewModel
import com.fctracker.career.telemetry.NotificationHelper
import com.fctracker.career.telemetry.StepState
import com.fctracker.career.ui.theme.StatusLive

/** What the guide shows for each of its steps. */
private enum class StepMode { DONE, CURRENT, LATER }

@Composable
private fun GuideStep(
    number: Int,
    title: String,
    mode: StepMode,
    summary: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    val borderColor = when (mode) {
        StepMode.CURRENT -> primary
        StepMode.DONE -> StatusLive.copy(alpha = 0.5f)
        StepMode.LATER -> MaterialTheme.colorScheme.outline
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (mode == StepMode.LATER) 0.65f else 1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (mode == StepMode.CURRENT) 2.dp else 1.dp, borderColor, shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (mode == StepMode.DONE) {
                StateBadge(StatusLive, Icons.Default.Check, size = 30.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (mode == StepMode.CURRENT) primary else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        number.toString(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (mode == StepMode.CURRENT) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                if (mode == StepMode.DONE) {
                    Text(summary, fontSize = 12.sp, color = StatusLive)
                }
            }
        }
        if (mode == StepMode.CURRENT) {
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The first-run guide: four steps in the order things have to happen, each one ticking itself off from what the app
 * can actually see (the bridge answering, the script reporting, a match arriving). Nothing here is a promise: a step
 * that cannot be seen from the phone (starting the launcher) is ticked by the person.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupGuideScreen(viewModel: MainViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val health by viewModel.health.collectAsState()
    val contact by viewModel.contact.collectAsState()
    val context = LocalContext.current

    var launcherStarted by rememberSaveable { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    val status = contact.status
    val script = status?.script
    val pcDone = launcherStarted || contact.reachable
    val phoneDone = contact.reachable
    val scriptDone = script != null && script.found && script.alive && script.versionOk
    val matchDone = scriptDone && status != null && status.lastMatch > 0
    val done = listOf(pcDone, phoneDone, scriptDone, matchDone)
    val firstOpen = done.indexOfFirst { !it }
    val allDone = firstOpen == -1
    fun modeOf(index: Int): StepMode = when {
        done[index] -> StepMode.DONE
        index == firstOpen -> StepMode.CURRENT
        else -> StepMode.LATER
    }

    fun finish() {
        viewModel.setSetupGuideDone(true)
        onClose()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Set up live sync", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = { finish() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close the guide", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    StatusPill(viewModel)
                    Spacer(Modifier.width(12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Matches, transfers, injuries and contract dates can reach this app by themselves while you play. " +
                            "You set it up once; it takes about three minutes.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val count = done.count { it }
                    LinearProgressIndicator(
                        progress = { count / done.size.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = StatusLive,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text("$count of ${done.size} done", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                GuideStep(1, "Start the helper on your PC", modeOf(0), "The helper is running on your PC") {
                    Body(
                        "On the PC, download FC26-PC-Tools.zip from the same Latest build page you installed this app from, " +
                            "unzip it and double-click FC26_Launcher.bat. It finds FC Live Editor, installs the capture script for you " +
                            "and starts the bridge in the background."
                    )
                    Spacer(Modifier.height(6.dp))
                    Body("Windows may ask for one permission so your phone can connect: choose Yes. The bridge keeps running after the launcher window closes.")
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { launcherStarted = true }) { Text("I've started it") }
                }
            }

            item {
                GuideStep(2, "Connect this phone", modeOf(1), "Connected to ${contact.address}") {
                    Body("Put the phone on the same Wi-Fi as the PC, then tap Find my PC.")
                    Spacer(Modifier.height(10.dp))
                    BridgeAddressPanel(viewModel, state.telemetryServerUrl)
                    val step = health.steps.firstOrNull { it.id == "phone" }
                    if (step != null && step.state == StepState.FAIL && step.fix.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        FixBox(step.fix, stepColor(step.state))
                    }
                }
            }

            item {
                val club = script?.club.orEmpty()
                GuideStep(
                    3, "Start the capture script", modeOf(2),
                    if (club.isNotBlank()) "Running for $club" else "The script is running"
                ) {
                    Body(
                        "In FC Live Editor load your career, open the Lua Scripts tab, pick FC26_Auto_Capture.lua and press Execute. " +
                            "The launcher already put it there. Keep Live Editor open while you play."
                    )
                    val step = health.steps.firstOrNull { it.id == "script" }
                    if (step != null && step.state != StepState.OK && step.state != StepState.UNKNOWN) {
                        Spacer(Modifier.height(8.dp))
                        Text(step.detail, fontSize = 12.sp, color = stepColor(step.state), fontWeight = FontWeight.Medium)
                        if (step.fix.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            FixBox(step.fix, stepColor(step.state))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            checking = true
                            viewModel.recheckConnection { checking = false }
                        },
                        enabled = !checking
                    ) { Text(if (checking) "Checking…" else "Check again") }
                }
            }

            item {
                GuideStep(4, "Play a match", modeOf(3), "Your first match arrived") {
                    Body(
                        "Play or simulate a match, then press Advance to reach the Career Hub. The result appears in Matches " +
                            "by itself, with an alert on this phone."
                    )
                    Spacer(Modifier.height(6.dp))
                    Body("For real match statistics instead of estimates, also run the Project Atlas exporter script in Live Editor.")
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = {
                        val shown = NotificationHelper.showTest(context)
                        alertMessage = if (shown) "Test alert sent. If you cannot see it, allow notifications for this app in Android settings."
                        else "Android blocked the alert. Allow notifications for this app in Android settings."
                    }) { Text("Send a test alert") }
                    val shown = alertMessage
                    if (shown != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(shown, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                if (allDone) {
                    Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("All set: open the dashboard") }
                } else {
                    TextButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Skip for now (you can come back from PC Connection)")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
