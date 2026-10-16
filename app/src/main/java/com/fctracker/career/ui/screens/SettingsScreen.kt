package com.fctracker.career.ui.screens
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.window.Dialog

import com.fctracker.career.MainViewModel

    val leaguesByCountry = mapOf(
        "England" to listOf("Premier League", "Championship", "League One", "League Two"),
        "Italy" to listOf("Serie A", "Serie B"),
        "France" to listOf("Ligue 1", "Ligue 2"),
        "Germany" to listOf("Bundesliga", "2. Bundesliga", "3. Liga"),
        "Spain" to listOf("La Liga", "Segunda División"),
        "Saudi" to listOf("Roshn Saudi League"),
        "America" to listOf("MLS", "USL Championship"),
        "USA" to listOf("MLS", "USL Championship"),
        "Brazil" to listOf("Brasileirão Série A", "Série B"),
        "Argentina" to listOf("Primera División"),
        "Portugal" to listOf("Liga Portugal"),
        "Netherlands" to listOf("Eredivisie")
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onMenuClick: () -> Unit) {
    val state by viewModel.state.collectAsState()
    
    var clubName by remember { mutableStateOf(state.clubName) }
    var managerName by remember { mutableStateOf(state.managerName) }
    var leagueName by remember { mutableStateOf(state.leagueName) }
    var country by remember { mutableStateOf(state.country) }
    var currencySymbol by remember { mutableStateOf(state.currencySymbol) }
    var primaryColorHex by remember { mutableStateOf(state.primaryColorHex) }
    var secondaryColorHex by remember { mutableStateOf(state.secondaryColorHex) }
    var clubCrestUrl by remember { mutableStateOf(state.clubCrestUrl) }
    var aiAgent by remember { mutableStateOf(state.aiAgent) }
    var aiApiUrl by remember { mutableStateOf(state.aiApiUrl) }
    val storedApiKey by viewModel.aiApiKey.collectAsState()
    var aiApiKey by remember(storedApiKey) { mutableStateOf(storedApiKey) }
    var aiModel by remember { mutableStateOf(state.aiModel) }
    val backgroundSync by viewModel.backgroundSync.collectAsState()
    var aiAgentExpanded by remember { mutableStateOf(false) }
    val aiAgents = listOf("Google Gemini", "Claude", "ChatGPT", "Grok", "Other")

    var showClubNamePromptDialog by remember { mutableStateOf(false) }
    var pendingImportedJson by remember { mutableStateOf<String?>(null) }
    var promptClubNameInput by remember { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current
    
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let {
            try {
                val jsonStr = viewModel.exportStateToJson()
                val encryptedStr = com.fctracker.career.utils.EncryptionUtils.encrypt(jsonStr)
                context.contentResolver.openOutputStream(it)?.use { out ->
                    out.write(encryptedStr.toByteArray())
                }
                android.widget.Toast.makeText(context, "Backup exported successfully", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to export", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val rawText = context.contentResolver.openInputStream(it)?.bufferedReader().use { reader -> reader?.readText() }
                if (rawText != null) {
                    val trimmed = rawText.trim()
                    val jsonStr = if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                        trimmed
                    } else {
                        val decrypted = com.fctracker.career.utils.EncryptionUtils.decrypt(rawText)
                        if (decrypted.isNotEmpty()) decrypted else trimmed
                    }

                    val parsedObj = try {
                        com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
                    } catch (e: Exception) {
                        null
                    }
                    val importedClub = parsedObj?.get("clubName")?.asString ?: ""

                    if (importedClub.startsWith("*")) {
                        if (state.clubName.isNotBlank() && !state.clubName.startsWith("*") && state.clubName != "Richmond FC") {
                            val success = viewModel.importStateFromJson(jsonStr, clubNameOverride = state.clubName)
                            if (success) {
                                primaryColorHex = viewModel.state.value.primaryColorHex
                                secondaryColorHex = viewModel.state.value.secondaryColorHex
                                clubName = viewModel.state.value.clubName
                                managerName = viewModel.state.value.managerName
                                android.widget.Toast.makeText(context, "Career data imported successfully!", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, "Failed to parse career data JSON", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            pendingImportedJson = jsonStr
                            promptClubNameInput = ""
                            showClubNamePromptDialog = true
                        }
                    } else {
                        val success = viewModel.importStateFromJson(jsonStr)
                        if (success) {
                            primaryColorHex = viewModel.state.value.primaryColorHex
                            secondaryColorHex = viewModel.state.value.secondaryColorHex
                            clubName = viewModel.state.value.clubName
                            managerName = viewModel.state.value.managerName
                            android.widget.Toast.makeText(context, "Career data imported successfully!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Failed to parse career data JSON", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to import: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showClubNamePromptDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showClubNamePromptDialog = false
                pendingImportedJson = null
            },
            title = {
                Text(
                    "Created Club Detected",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Your exported career save uses a custom created club. What is your club's name?",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = promptClubNameInput,
                        onValueChange = { promptClubNameInput = it },
                        label = { Text("Club Name") },
                        placeholder = { Text("e.g. Torquay AFC") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val chosenName = if (promptClubNameInput.isNotBlank()) promptClubNameInput.trim() else "My Custom Club"
                        pendingImportedJson?.let { rawJson ->
                            val success = viewModel.importStateFromJson(
                                rawJson,
                                clubNameOverride = chosenName
                            )
                            if (success) {
                                primaryColorHex = viewModel.state.value.primaryColorHex
                                secondaryColorHex = viewModel.state.value.secondaryColorHex
                                clubName = viewModel.state.value.clubName
                                managerName = viewModel.state.value.managerName
                                android.widget.Toast.makeText(context, "Career data imported for $chosenName!", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, "Failed to parse career data JSON", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        showClubNamePromptDialog = false
                        pendingImportedJson = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Import Career")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClubNamePromptDialog = false
                        pendingImportedJson = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings & Data", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
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
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DarkCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Club Profile Settings", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = clubName, onValueChange = { clubName = it }, label = { Text("Club Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = managerName, onValueChange = { managerName = it }, label = { Text("Manager Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    var leagueExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = leagueExpanded,
                        onExpandedChange = { leagueExpanded = !leagueExpanded }
                    ) {
                        OutlinedTextField(
                            value = leagueName,
                            onValueChange = { leagueName = it },
                            label = { Text("League Name") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = leagueExpanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = leagueExpanded,
                            onDismissRequest = { leagueExpanded = false }
                        ) {
                            val leagues = leaguesByCountry[country] ?: listOf("Premier League", "La Liga", "Serie A", "Bundesliga", "Ligue 1", "MLS", "Other")
                            leagues.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        leagueName = selectionOption
                                        leagueExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        var countryExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = countryExpanded,
                            onExpandedChange = { countryExpanded = !countryExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = country,
                                onValueChange = { country = it },
                                label = { Text("Country") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                singleLine = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = countryExpanded,
                                onDismissRequest = { countryExpanded = false }
                            ) {
                                val countries = listOf("England", "Spain", "Italy", "Germany", "France", "USA", "Brazil", "Argentina", "Other")
                                countries.forEach { selectionOption ->
                                    DropdownMenuItem(
                                        text = { Text(selectionOption) },
                                        onClick = {
                                            country = selectionOption
                                            val newLeagues = leaguesByCountry[selectionOption]
                                            if (newLeagues != null && !newLeagues.contains(leagueName)) {
                                                leagueName = newLeagues[0]
                                            }
                                            countryExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        var currencyExpanded by remember { mutableStateOf(false) }
                        val currencies = listOf("€ - EUR", "£ - GBP", "$ - USD")
                        ExposedDropdownMenuBox(
                            expanded = currencyExpanded,
                            onExpandedChange = { currencyExpanded = !currencyExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = currencySymbol,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Currency") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                singleLine = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = currencyExpanded,
                                onDismissRequest = { currencyExpanded = false }
                            ) {
                                currencies.forEach { selectionOption ->
                                    DropdownMenuItem(
                                        text = { Text(selectionOption) },
                                        onClick = {
                                            currencySymbol = selectionOption
                                            currencyExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    var showPrimaryColorPicker by remember { mutableStateOf(false) }
                    var showSecondaryColorPicker by remember { mutableStateOf(false) }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = primaryColorHex, 
                            onValueChange = { primaryColorHex = it }, 
                            label = { Text("Primary Color") }, 
                            modifier = Modifier.weight(1f), 
                            singleLine = true,
                            leadingIcon = {
                                Box(modifier = Modifier.padding(start = 12.dp, end = 4.dp).size(24.dp).background(try { Color(android.graphics.Color.parseColor(primaryColorHex)) } catch (e: Exception) { Color.Transparent }, androidx.compose.foundation.shape.CircleShape))
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPrimaryColorPicker = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Pick Color")
                                }
                            }
                        )
                        OutlinedTextField(
                            value = secondaryColorHex, 
                            onValueChange = { secondaryColorHex = it }, 
                            label = { Text("Secondary Color") }, 
                            modifier = Modifier.weight(1f), 
                            singleLine = true,
                            leadingIcon = {
                                Box(modifier = Modifier.padding(start = 12.dp, end = 4.dp).size(24.dp).background(try { Color(android.graphics.Color.parseColor(secondaryColorHex)) } catch (e: Exception) { Color.Transparent }, androidx.compose.foundation.shape.CircleShape))
                            },
                            trailingIcon = {
                                IconButton(onClick = { showSecondaryColorPicker = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Pick Color")
                                }
                            }
                        )
                    }
                    
                    if (showPrimaryColorPicker) {
                        var tempColorHex by remember { mutableStateOf(primaryColorHex) }
                        val oldColor = try { Color(android.graphics.Color.parseColor(primaryColorHex)) } catch(e: Exception) { Color.Transparent }
                        val newColor = try { Color(android.graphics.Color.parseColor(tempColorHex)) } catch(e: Exception) { Color.Transparent }
                        
                        Dialog(onDismissRequest = { showPrimaryColorPicker = false }) {
                            DarkCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Select Primary Color", color = Color.White, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                                    ColorWheel(modifier = Modifier.fillMaxSize()) { color ->
                                        tempColorHex = color.toHexCode()
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(modifier = Modifier.size(40.dp).background(oldColor, androidx.compose.foundation.shape.CircleShape))
                                        Text("Before", color = Color.White, fontSize = 12.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(modifier = Modifier.size(40.dp).background(newColor, androidx.compose.foundation.shape.CircleShape))
                                        Text("After", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { showPrimaryColorPicker = false }) { Text("Cancel", color = Color.White) }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(onClick = { primaryColorHex = tempColorHex; showPrimaryColorPicker = false }) { Text("Confirm") }
                                }
                            }
                        }
                    }
                    if (showSecondaryColorPicker) {
                        var tempColorHex by remember { mutableStateOf(secondaryColorHex) }
                        val oldColor = try { Color(android.graphics.Color.parseColor(secondaryColorHex)) } catch(e: Exception) { Color.Transparent }
                        val newColor = try { Color(android.graphics.Color.parseColor(tempColorHex)) } catch(e: Exception) { Color.Transparent }
                        
                        Dialog(onDismissRequest = { showSecondaryColorPicker = false }) {
                            DarkCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Select Secondary Color", color = Color.White, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                                    ColorWheel(modifier = Modifier.fillMaxSize()) { color ->
                                        tempColorHex = color.toHexCode()
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(modifier = Modifier.size(40.dp).background(oldColor, androidx.compose.foundation.shape.CircleShape))
                                        Text("Before", color = Color.White, fontSize = 12.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(modifier = Modifier.size(40.dp).background(newColor, androidx.compose.foundation.shape.CircleShape))
                                        Text("After", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { showSecondaryColorPicker = false }) { Text("Cancel", color = Color.White) }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(onClick = { secondaryColorHex = tempColorHex; showSecondaryColorPicker = false }) { Text("Confirm") }
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = clubCrestUrl, onValueChange = { clubCrestUrl = it }, label = { Text("Club Crest URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Button(onClick = { 
                        viewModel.updateSettings(clubName, managerName, leagueName, country, currencySymbol, primaryColorHex, secondaryColorHex, clubCrestUrl, aiAgent, aiApiUrl, aiApiKey, aiModel)
                        android.widget.Toast.makeText(context, "Profile saved", android.widget.Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Save Profile Settings")
                    }
                }
            }

            item {
                DarkCard(modifier = Modifier.fillMaxWidth()) {
                    Text("AI Agent Settings", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ExposedDropdownMenuBox(
                        expanded = aiAgentExpanded,
                        onExpandedChange = { aiAgentExpanded = !aiAgentExpanded }
                    ) {
                        OutlinedTextField(
                            value = aiAgent,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("AI Agent") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aiAgentExpanded) },
                            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = aiAgentExpanded,
                            onDismissRequest = { aiAgentExpanded = false }
                        ) {
                            aiAgents.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        aiAgent = selectionOption
                                        aiAgentExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    if (aiAgent == "Other") {
                        OutlinedTextField(
                            value = aiApiUrl,
                            onValueChange = { aiApiUrl = it },
                            label = { Text("API URL") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = aiApiKey,
                        onValueChange = { aiApiKey = it },
                        label = { Text(if (aiAgent == "Other") "API Key" else "${aiAgent} API Key") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = aiModel,
                        onValueChange = { aiModel = it },
                        label = { Text("Model (blank = ${com.fctracker.career.ai.AiClient.defaultModel(aiAgent).ifBlank { "enter one" }})") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true
                    )
                    Text(
                        "Your key is kept in encrypted storage on this phone and is not included in backups. Tap \"Save Profile Settings\" above to apply.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            item {
                DarkCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live Telemetry Bridge (PC Connection)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        val isOnline = state.bridgeConnectionStatus.startsWith("Online") || state.bridgeConnectionStatus.startsWith("Connected")
                        val statusDotColor = if (isOnline) Color(0xFF10B981) else if (state.bridgeConnectionStatus == "Not Checked") Color(0xFF94A3B8) else Color(0xFFEF4444)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(statusDotColor))
                            Text(
                                state.bridgeConnectionStatus,
                                fontSize = 10.sp,
                                color = statusDotColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Direct local connection to your PC bridge (Start_Bridge.bat). Bypasses third-party services.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Keep syncing when the app is closed", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(
                                "Shows a small \"Live sync\" notification so match and transfer alerts still arrive. For best results, keep the phone on the same Wi-Fi and exclude this app from battery optimisation.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = backgroundSync, onCheckedChange = { viewModel.setBackgroundSync(it) })
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    var bridgeInput by remember(state.telemetryServerUrl) { mutableStateOf(state.telemetryServerUrl) }
                    var testStatusMsg by remember { mutableStateOf<String?>(null) }
                    var isTesting by remember { mutableStateOf(false) }
                    val context = androidx.compose.ui.platform.LocalContext.current

                    OutlinedTextField(
                        value = bridgeInput,
                        onValueChange = { bridgeInput = it },
                        label = { Text("PC Bridge Address") },
                        placeholder = { Text("http://192.168.2.24:5050") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
                    ) {
                        Text("Port:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        val isPort5050 = bridgeInput.trim().removeSuffix("/").endsWith(":5050")
                        
                        androidx.compose.material3.AssistChip(
                            onClick = {
                                val clean = bridgeInput.trim().removeSuffix("/")
                                val baseWithoutPort = if (clean.substringAfter("://", "").contains(":")) clean.substringBeforeLast(":") else clean
                                bridgeInput = "$baseWithoutPort:5050"
                            },
                            label = { Text("5050 (Active)", fontSize = 10.sp) },
                            colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                containerColor = if (isPort5050) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testStatusMsg = "Pinging bridge on port 5050..."
                            viewModel.updateTelemetryServerUrl(bridgeInput.trim())
                            viewModel.testBridgeConnection { success, msg ->
                                isTesting = false
                                testStatusMsg = msg
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isTesting) "Testing..." else "Test Connection (Port 5050)", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.injectTestMatchEvent()
                                testStatusMsg = "⚽ Test match notification sent! Tap notification to open Matches."
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Simulate Match ⚽", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.injectTestTransferEvent()
                                testStatusMsg = "🔄 Test transfer notification sent! Tap notification to open Transfers."
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Simulate Transfer 🔄", fontSize = 11.sp)
                        }
                    }

                    if (testStatusMsg != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            testStatusMsg!!,
                            fontSize = 11.sp,
                            color = if (testStatusMsg!!.startsWith("✅") || testStatusMsg!!.startsWith("⚽") || testStatusMsg!!.startsWith("🔄")) Color(0xFF10B981) else Color(0xFFEF4444),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "💡 Direct Connection Checklist:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "1. Start_Bridge.bat listens on port 5050. Keep its window open while you play.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "2. Windows Network Profile: Set your Wi-Fi to 'Private' in Windows Settings if your phone cannot connect.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "3. Fully automatic: run FC26_Auto_Capture.lua in FC Live Editor. After a match, click 'Advance' to reach the Career Hub and the match and your transfers sync by themselves.",
                                fontSize = 11.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "4. Manual sync: type FC26_AutoCapture_SyncLatest() in the Live Editor console, or use the Web Dashboard (http://localhost:5050).",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "5. Browser Test: Open http://localhost:5050 on your PC to view the Web Dashboard and click 'Send Test Match'.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.updateTelemetryServerUrl(bridgeInput.trim())
                                viewModel.fetchLiveTelemetry()
                                android.widget.Toast.makeText(context, "Bridge URL saved & connection requested", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save & Sync")
                        }
                    }
                }
            }

            item {
                DarkCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Save Slot Management", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    var showNewSavePrompt by remember { mutableStateOf<Int?>(null) }
                    
                    val activeSlot = viewModel.activeSaveSlot
                    val slot1Name = viewModel.saveSlotNames[1] ?: "Save Slot 1"
                    val slot2Name = viewModel.saveSlotNames[2] ?: "Save Slot 2"
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { 
                                if (activeSlot != 1) {
                                    if (viewModel.saveSlots.containsKey(1)) {
                                        viewModel.switchSaveSlot(1)
                                    } else {
                                        showNewSavePrompt = 1
                                    }
                                }
                            }, 
                            modifier = Modifier.weight(1f), 
                            colors = ButtonDefaults.buttonColors(containerColor = if (activeSlot == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(slot1Name + if(activeSlot == 1) " (Active)" else "", color = if (activeSlot == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = { 
                                if (activeSlot != 2) {
                                    if (viewModel.saveSlots.containsKey(2)) {
                                        viewModel.switchSaveSlot(2)
                                    } else {
                                        showNewSavePrompt = 2
                                    }
                                }
                            }, 
                            modifier = Modifier.weight(1f), 
                            colors = ButtonDefaults.buttonColors(containerColor = if (activeSlot == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(slot2Name + if(activeSlot == 2) " (Active)" else "", color = if (activeSlot == 2) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    var renameText by remember(activeSlot, viewModel.saveSlotNames) { mutableStateOf(viewModel.saveSlotNames[activeSlot] ?: "") }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text("Active Save Name") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(onClick = { viewModel.renameSaveSlot(activeSlot, renameText) }) {
                            Text("Rename")
                        }
                    }
                    
                    if (showNewSavePrompt != null) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showNewSavePrompt = null },
                            title = { Text("Create New Save?") },
                            text = { Text("This slot is empty. Do you want to create a new save?") },
                            confirmButton = {
                                Button(onClick = {
                                    val slotToCreate = showNewSavePrompt!!
                                    showNewSavePrompt = null
                                    viewModel.switchSaveSlot(slotToCreate, createNew = true)
                                }) {
                                    Text("Yes")
                                }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = { showNewSavePrompt = null }) {
                                    Text("No")
                                }
                            }
                        )
                    }
                }
            }
            item {
                DarkCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Data Management", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { exportLauncher.launch("manager_backup.aes") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Export Backup JSON")
                    }
                    Button(onClick = { importLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text("Import JSON Backup", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }

                    Button(onClick = { viewModel.deleteCurrentSave() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Delete Current Save")
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
