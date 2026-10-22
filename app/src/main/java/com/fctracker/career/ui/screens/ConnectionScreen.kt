package com.fctracker.career.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.MainViewModel
import com.fctracker.career.telemetry.BridgeAuth
import com.fctracker.career.telemetry.BridgeCheck
import com.fctracker.career.telemetry.BridgeClient
import com.fctracker.career.telemetry.HealthStep
import com.fctracker.career.telemetry.Level
import com.fctracker.career.telemetry.StepState
import com.fctracker.career.ui.theme.StatusDown
import com.fctracker.career.ui.theme.StatusIdle
import com.fctracker.career.ui.theme.StatusLive
import com.fctracker.career.ui.theme.StatusWarn

private fun iconFor(state: StepState): ImageVector? = when (state) {
    StepState.OK -> Icons.Default.Check
    StepState.WARN -> Icons.Default.Warning
    StepState.FAIL -> Icons.Default.Close
    StepState.UNKNOWN -> null
}

private fun iconFor(level: Level): ImageVector = when (level) {
    Level.LIVE -> Icons.Default.Check
    Level.ATTENTION -> Icons.Default.Warning
    Level.DOWN -> Icons.Default.Close
}

private fun colorForCheck(state: String): Color = when (state) {
    "ok" -> StatusLive
    "warn" -> StatusWarn
    "fail" -> StatusDown
    else -> StatusIdle
}

private fun stateOfCheck(state: String): StepState = when (state) {
    "ok" -> StepState.OK
    "warn" -> StepState.WARN
    "fail" -> StepState.FAIL
    else -> StepState.UNKNOWN
}

/** A round badge: a tick, a warning sign or a cross on a tinted disc (grey and empty while something is unknown). */
@Composable
fun StateBadge(color: Color, icon: ImageVector?, size: Dp = 28.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.58f))
        } else {
            Box(Modifier.size(size * 0.28f).clip(CircleShape).background(color))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = modifier)
}

/** One stop on the path Phone, Bridge, Capture script: a badge on a rail, what is going on, and what to do about it. */
@Composable
private fun PathRow(step: HealthStep, isLast: Boolean) {
    val color = stepColor(step.state)
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
            StateBadge(color, iconFor(step.state))
            if (!isLast) {
                Box(
                    Modifier
                        .padding(vertical = 2.dp)
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outline)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 14.dp)) {
            Text(step.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(step.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (step.fix.isNotBlank() && step.state != StepState.OK) {
                Spacer(Modifier.height(6.dp))
                FixBox(step.fix, color)
            }
        }
    }
}

/** "What to do" in a tinted box, in the colour of the problem. */
@Composable
fun FixBox(text: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun CheckRow(check: BridgeCheck) {
    val color = colorForCheck(check.state)
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        StateBadge(color, iconFor(stateOfCheck(check.state)), size = 22.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(check.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
            if (check.detail.isNotBlank()) {
                Text(check.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (check.fix.isNotBlank() && (check.state == "warn" || check.state == "fail")) {
                Spacer(Modifier.height(4.dp))
                FixBox(check.fix, color)
            }
        }
    }
}

private fun BridgeClient.Found.title(): String {
    val label = name.ifBlank { "Your PC" }
    return if (old) "$label (old bridge: run FC26_Launcher.bat to update it)" else label
}

/**
 * The bridge address, the pairing code, "Find my PC", "Save & connect" and "Forget this PC". Used by the Connection
 * screen and by the setup guide, so there is one place (and one set of words) for both.
 */
@Composable
fun BridgeAddressPanel(viewModel: MainViewModel, savedAddress: String) {
    val savedCode by viewModel.bridgeCode.collectAsState()
    var input by remember(savedAddress) { mutableStateOf(savedAddress) }
    var codeInput by remember(savedCode) { mutableStateOf(BridgeAuth.format(savedCode)) }
    val codeTyped = BridgeAuth.normalize(codeInput)
    val codeOk = BridgeAuth.isValid(codeInput)
    var finding by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var options by remember { mutableStateOf<List<BridgeClient.Found>>(emptyList()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Bridge address") },
            placeholder = { Text("http://192.168.1.20:5050") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = codeInput,
            onValueChange = { typed ->
                // capitals, digits, dashes and spaces only; the same length as typed, so the cursor stays where it is
                codeInput = typed.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == ' ' }.take(24)
            },
            label = { Text("Pairing code") },
            placeholder = { Text("XXXX-XXXX-XXXX-XXXX") },
            singleLine = true,
            isError = codeTyped.length >= BridgeAuth.LENGTH && !codeOk,
            supportingText = {
                Text(
                    when {
                        codeTyped.isEmpty() -> "Shown in the launcher window on the PC and on the bridge page (http://localhost:5050 on the PC)."
                        codeTyped.length < BridgeAuth.LENGTH -> "${codeTyped.length} of ${BridgeAuth.LENGTH} characters."
                        codeTyped.length > BridgeAuth.LENGTH -> "Too long: a pairing code has ${BridgeAuth.LENGTH} characters."
                        !codeOk -> "That is not a pairing code: it never contains I, O, 0 or 1."
                        else -> "Only a phone with this code can read from your PC. Keep it to yourself."
                    },
                    fontSize = 11.sp
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    finding = true
                    message = null
                    options = emptyList()
                    viewModel.findMyPc { result ->
                        finding = false
                        when {
                            result.found.isEmpty() -> message = result.message
                            result.found.size == 1 -> {
                                val only = result.found[0]
                                input = only.base
                                viewModel.saveBridgeAddress(only.base)
                                message = if (savedCode.isBlank()) "Found ${only.title()} at ${only.base}. Now type the pairing code shown on the PC and tap Save & connect."
                                else "Found ${only.title()} at ${only.base}."
                            }
                            else -> {
                                options = result.found
                                message = "Found ${result.found.size} bridges on this network. Pick yours:"
                            }
                        }
                    }
                },
                enabled = !finding,
                modifier = Modifier.weight(1f)
            ) {
                if (finding) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (finding) "Looking…" else "Find my PC", fontSize = 13.sp)
            }
            Button(
                onClick = {
                    saving = true
                    message = null
                    viewModel.saveBridgeConnection(input.trim(), if (codeOk) codeTyped else savedCode) { saving = false }
                    if (codeTyped.isNotEmpty() && !codeOk) message = "The pairing code is not complete, so the saved one is kept."
                },
                enabled = !saving && input.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalContentColor.current
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (saving) "Connecting…" else "Save & connect", fontSize = 13.sp)
            }
        }
        val shown = message
        if (shown != null) {
            Text(shown, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (savedCode.isNotBlank()) {
            TextButton(onClick = {
                viewModel.forgetPc()
                codeInput = ""
                message = "This phone forgot the pairing code. It cannot read from the PC until a code is typed again."
            }) { Text("Forget this PC", fontSize = 13.sp) }
        }
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(option.title(), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                    Text(option.base, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = {
                    input = option.base
                    options = emptyList()
                    message = "Using ${option.base}."
                    viewModel.saveBridgeAddress(option.base)
                }) { Text("Use this") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(viewModel: MainViewModel, onOpenSetup: () -> Unit, onMenuClick: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val health by viewModel.health.collectAsState()
    val contact by viewModel.contact.collectAsState()
    val context = LocalContext.current

    var checking by remember { mutableStateOf(false) }
    var resending by remember { mutableStateOf(false) }
    var resendOk by remember { mutableStateOf(false) }
    var resendMessage by remember { mutableStateOf<String?>(null) }
    var showTesting by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }

    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (e: Exception) {
            ""
        }
    }
    val status = contact.status
    val accent = levelColor(health.level)

    Scaffold(
        containerColor = com.fctracker.career.ui.theme.FcTheme.colors.bg,
        topBar = {
            androidx.compose.foundation.layout.Column(Modifier.statusBarsPadding()) {
                com.fctracker.career.ui.components.FcTopBar("PC Connection", sub = "Phone, bridge and capture script", onNav = onMenuClick,
                    pill = { StatusPill(health.level, health.pill, onClick = {}) })
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. The answer
            item {
                DarkCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StateBadge(accent, iconFor(health.level), size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(health.headline, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = accent)
                            if (health.detail.isNotBlank()) {
                                Text(health.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                checking = true
                                viewModel.recheckConnection { checking = false }
                            },
                            enabled = !checking
                        ) {
                            if (checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalContentColor.current
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (checking) "Checking…" else "Check again")
                        }
                        OutlinedButton(onClick = onOpenSetup) { Text("Setup guide") }
                    }
                }
            }

            // 2. Phone -> PC bridge -> capture script
            item {
                DarkCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle("How your data travels")
                    Text(
                        "Live Editor script  →  PC bridge  →  this phone",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    health.steps.forEachIndexed { index, step ->
                        PathRow(step, isLast = index == health.steps.lastIndex)
                    }
                }
            }

            // 3. The address
            item {
                DarkCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle("PC address")
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "The phone and the PC must be on the same Wi-Fi. Tap Find my PC, or type the address the bridge shows on the PC.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    BridgeAddressPanel(viewModel, state.telemetryServerUrl)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "On a VPN? Allow local network access in the VPN app, or the phone cannot see the PC.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. The bridge's own self check
            val checks = status?.checks.orEmpty()
            if (checks.isNotEmpty()) {
                item {
                    DarkCard(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle("Self check on the PC")
                        Text(
                            "The same list is shown on the PC at http://localhost:5050.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        checks.forEach { CheckRow(it) }
                    }
                }
            }

            // 5. Tools
            item {
                DarkCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle("Tools")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "A match did not show up? Ask the capture script to send its latest finished match again. A match the app already has is not added twice.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            resending = true
                            resendMessage = null
                            viewModel.resendLastMatch { ok, text ->
                                resending = false
                                resendOk = ok
                                resendMessage = text
                            }
                        },
                        enabled = contact.reachable && !resending,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (resending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalContentColor.current
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (resending) "Waiting for the script…" else "Re-send last match")
                    }
                    val resendText = resendMessage
                    if (resendText != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(resendText, fontSize = 12.sp, color = if (resendOk) StatusLive else StatusDown)
                    }
                    if (!contact.reachable) {
                        Spacer(Modifier.height(4.dp))
                        Text("Available once the phone is connected to the PC.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showTesting = !showTesting }) {
                        Text("Testing", fontSize = 12.sp)
                        Icon(
                            if (showTesting) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (showTesting) {
                        Text(
                            "Adds a made-up match or transfer so you can see what an alert looks like. Delete it afterwards from Matches or Transfers.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.injectTestMatchEvent()
                                    testMessage = "Test match added. Tap the alert to open Matches."
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Test match", fontSize = 12.sp) }
                            OutlinedButton(
                                onClick = {
                                    viewModel.injectTestTransferEvent()
                                    testMessage = "Test transfer added. Tap the alert to open Transfers."
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Test transfer", fontSize = 12.sp) }
                        }
                        val tested = testMessage
                        if (tested != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(tested, fontSize = 11.sp, color = StatusLive)
                        }
                    }
                }
            }

            // 6. Versions
            item {
                DarkCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle("Versions")
                    Spacer(Modifier.height(6.dp))
                    VersionLine("This app", if (appVersion.isBlank()) "unknown" else appVersion, null)
                    if (status != null) {
                        VersionLine(
                            "PC bridge", "v${status.bridgeVersion}",
                            status.protocol >= com.fctracker.career.telemetry.HealthModel.REQUIRED_BRIDGE_PROTOCOL
                        )
                        if (status.script.found) {
                            VersionLine(
                                "Capture script", "v${status.script.version.ifBlank { "?" }}",
                                status.script.versionOk
                            )
                        } else {
                            VersionLine("Capture script", "not running", false)
                        }
                    } else {
                        VersionLine("PC bridge", if (contact.reachable) "older (no version report)" else "not connected", false)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Out of date? Run FC26_Launcher.bat on the PC: it updates the bridge and the capture script in one go.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** "Capture script   v2.8.0   ✓". [ok] null = no verdict (the app itself). */
@Composable
private fun VersionLine(label: String, value: String, ok: Boolean?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
        if (ok != null) {
            Spacer(Modifier.width(8.dp))
            StateBadge(if (ok) StatusLive else StatusWarn, if (ok) Icons.Default.Check else Icons.Default.Warning, size = 20.dp)
        }
    }
}
