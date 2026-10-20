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
                val health by viewModel.health.collectAsState()
                DarkCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live sync with your PC", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        StatusPill(viewModel)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        if (health.level == com.fctracker.career.telemetry.Level.LIVE) health.detail else health.headline,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { viewModel.navigateToScreen("connection") }, modifier = Modifier.weight(1f)) {
                            Text("PC Connection", fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = { viewModel.navigateToScreen("setup") }, modifier = Modifier.weight(1f)) {
                            Text("Setup guide", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Keep syncing in the background", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(
                                "Shows a small \"Live sync\" notification so match and transfer alerts still arrive while the app is in the background. Swiping the app away from recent apps stops syncing and removes the notification. For best results, keep the phone on the same Wi-Fi and exclude this app from battery optimisation.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = backgroundSync, onCheckedChange = { viewModel.setBackgroundSync(it) })
                    }
                }
            }

            item {
                val prefs by viewModel.notificationPrefs.collectAsState()
                val alertContext = androidx.compose.ui.platform.LocalContext.current
                var alertsAllowed by remember { mutableStateOf(true) }
                var alertTestMsg by remember { mutableStateOf<String?>(null) }
                // Re-checked while this screen is open, so coming back from Android's settings page updates it.
                LaunchedEffect(Unit) {
                    while (true) {
                        alertsAllowed = com.fctracker.career.telemetry.NotificationHelper.alertsAllowed(alertContext)
                        kotlinx.coroutines.delay(1500)
                    }
                }
                DarkCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Notifications", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (alertsAllowed) "Android allows alerts from this app. Choose which ones you want:"
                        else "Blocked: Android is hiding this app's alerts. Open the notification settings below and allow notifications for this app.",
                        fontSize = 12.sp,
                        color = if (alertsAllowed) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFEF4444)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    NotificationToggle("Matches", "The final score of each match, as soon as it is played.", prefs.matches) {
                        viewModel.setNotificationPrefs(prefs.copy(matches = it))
                    }
                    NotificationToggle("Transfers", "Signings, sales and loans, when the deal is done.", prefs.transfers) {
                        viewModel.setNotificationPrefs(prefs.copy(transfers = it))
                    }
                    NotificationToggle("Injuries", "A player of yours gets injured, and for how long.", prefs.injuries) {
                        viewModel.setNotificationPrefs(prefs.copy(injuries = it))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tapping an alert opens the matching screen. These switches only affect this phone; the data is still saved when an alert is off.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val shown = com.fctracker.career.telemetry.NotificationHelper.showTest(alertContext)
                                alertTestMsg = if (shown) "Test alert sent. If you cannot see it, check the notification settings."
                                else "Android blocked the test alert. Open the notification settings and allow notifications."
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Send test alert", fontSize = 11.sp) }
                        OutlinedButton(
                            onClick = {
                                try {
                                    alertContext.startActivity(com.fctracker.career.telemetry.NotificationHelper.settingsIntent(alertContext))
                                } catch (e: Exception) {
                                    alertTestMsg = "Could not open the settings page. Open Android Settings > Apps > this app > Notifications."
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Android settings", fontSize = 11.sp) }
                    }
                    val testMsg = alertTestMsg
                    if (testMsg != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(testMsg, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun NotificationToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
