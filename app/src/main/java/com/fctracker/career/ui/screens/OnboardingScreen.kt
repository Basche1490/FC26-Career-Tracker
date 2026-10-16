package com.fctracker.career.ui.screens
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(viewModel: com.fctracker.career.MainViewModel, onComplete: (manager: String, club: String, country: String, league: String, startYear: Int, currencySymbol: String, primary: String, secondary: String, logo: String, isExistingTeam: Boolean, aiAgent: String, aiApiUrl: String, aiApiKey: String) -> Unit) {
    var isExistingTeam by remember { mutableStateOf(false) }
    
    var managerName by remember { mutableStateOf("") }
    var clubName by remember { mutableStateOf("") }
    var clubSearchExpanded by remember { mutableStateOf(false) }
    var realClubs by remember { mutableStateOf(emptyList<String>()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val clubs = viewModel.getUniqueClubs()
            realClubs = clubs
        }
    }
    
        val clubColors = mapOf(
        // ENGLAND
        "Arsenal" to ("#EF0107" to "#063672"),
        "Aston Villa" to ("#670E36" to "#95BFE5"),
        "Bournemouth" to ("#B50E12" to "#000000"),
        "Brentford" to ("#E30613" to "#000000"),
        "Brighton" to ("#0057B8" to "#FFFFFF"),
        "Burnley" to ("#6C1D45" to "#99D6EA"),
        "Chelsea" to ("#034694" to "#EE242C"),
        "Crystal Palace" to ("#1B458F" to "#A7A5A6"),
        "Everton" to ("#003399" to "#FFFFFF"),
        "Fulham" to ("#FFFFFF" to "#000000"),
        "Liverpool" to ("#C8102E" to "#00B2A9"),
        "Luton Town" to ("#F78F1E" to "#000099"),
        "Manchester City" to ("#6CABDD" to "#1C2C5B"),
        "Manchester United" to ("#DA291C" to "#FBE122"),
        "Newcastle United" to ("#241F20" to "#F1F1F1"),
        "Nottingham Forest" to ("#E53233" to "#FFFFFF"),
        "Sheffield United" to ("#EE2737" to "#000000"),
        "Tottenham Hotspur" to ("#132257" to "#FFFFFF"),
        "West Ham" to ("#7A263A" to "#1BB1E7"),
        "Wolverhampton" to ("#FDB913" to "#231F20"),
        "Leeds United" to ("#FFCD00" to "#1D428A"),
        "Leicester City" to ("#003090" to "#FDBE11"),
        "Southampton" to ("#D71920" to "#130C0E"),

        // SPAIN
        "Real Madrid" to ("#FFFFFF" to "#00529F"),
        "Barcelona" to ("#004D98" to "#A50044"),
        "Atletico Madrid" to ("#CB3524" to "#272E61"),
        "Sevilla" to ("#F43333" to "#FFFFFF"),
        "Real Sociedad" to ("#0067B1" to "#FFFFFF"),
        "Athletic Club" to ("#EE2523" to "#FFFFFF"),
        "Real Betis" to ("#0BB363" to "#FFFFFF"),
        "Valencia" to ("#FFFFFF" to "#000000"),
        "Villarreal" to ("#FCE216" to "#00508F"),
        "Girona" to ("#E63B2E" to "#FFFFFF"),

        // GERMANY
        "Bayern Munich" to ("#DC052D" to "#0066B2"),
        "Borussia Dortmund" to ("#FDE100" to "#000000"),
        "Bayer Leverkusen" to ("#E32221" to "#000000"),
        "RB Leipzig" to ("#DD013F" to "#002D54"),
        "Eintracht Frankfurt" to ("#E1000F" to "#000000"),
        "Stuttgart" to ("#E32221" to "#FFFFFF"),
        "Monchengladbach" to ("#000000" to "#FFFFFF"),
        "Wolfsburg" to ("#009F00" to "#FFFFFF"),

        // ITALY
        "Juventus" to ("#000000" to "#FFFFFF"),
        "AC Milan" to ("#FB090B" to "#000000"),
        "Inter Milan" to ("#010E80" to "#000000"),
        "Napoli" to ("#12A0D7" to "#FFFFFF"),
        "Roma" to ("#F0BC42" to "#8E1F2F"),
        "Lazio" to ("#87D8F7" to "#FFFFFF"),
        "Atalanta" to ("#19548E" to "#000000"),
        "Fiorentina" to ("#4A2583" to "#FFFFFF"),
        "Bologna" to ("#A32034" to "#1A2F4B"),

        // FRANCE
        "PSG" to ("#004170" to "#DA291C"),
        "Monaco" to ("#E32221" to "#FFFFFF"),
        "Marseille" to ("#2FAEE0" to "#FFFFFF"),
        "Lyon" to ("#DA0812" to "#003E7E"),
        "Lille" to ("#E01E13" to "#252B32"),
        "Lens" to ("#ED1C24" to "#FFD900"),
        "Nice" to ("#ED1C24" to "#000000"),
        "Rennes" to ("#ED1C24" to "#000000"),

        // SAUDI
        "Al Hilal" to ("#0055A5" to "#FFFFFF"),
        "Al Nassr" to ("#FFD700" to "#00205B"),
        "Al Ittihad" to ("#FCE300" to "#000000"),
        "Al Ahli" to ("#00A650" to "#FFFFFF"),
        "Al Ettifaq" to ("#006B3F" to "#E3001B"),

        // AMERICA
        "Inter Miami" to ("#F7B5CD" to "#000000"),
        "LA Galaxy" to ("#00245D" to "#FFD200"),
        "LAFC" to ("#000000" to "#C39E6D"),
        "Seattle Sounders" to ("#5D9732" to "#005587"),
        "Atlanta United" to ("#80000B" to "#221F1F"),
        "New York City FC" to ("#6CABDD" to "#00285E"),
        "Philadelphia Union" to ("#071B2C" to "#B18947"),
        
        // PORTUGAL
        "Benfica" to ("#ED1C24" to "#FFFFFF"),
        "Porto" to ("#00428C" to "#FFFFFF"),
        "Sporting CP" to ("#008057" to "#FFFFFF"),
        "Braga" to ("#E32221" to "#FFFFFF"),
        
        // NETHERLANDS
        "Ajax" to ("#D2122E" to "#FFFFFF"),
        "PSV" to ("#F11738" to "#FFFFFF"),
        "Feyenoord" to ("#EE2223" to "#000000"),
        "AZ Alkmaar" to ("#E32221" to "#FFFFFF")
    )

    val clubDomains = mapOf(
        // ENGLAND
        "Arsenal" to "arsenal.com",
        "Aston Villa" to "avfc.co.uk",
        "Bournemouth" to "afcb.co.uk",
        "Brentford" to "brentfordfc.com",
        "Brighton" to "brightonandhovealbion.com",
        "Burnley" to "burnleyfootballclub.com",
        "Chelsea" to "chelseafc.com",
        "Crystal Palace" to "cpfc.co.uk",
        "Everton" to "evertonfc.com",
        "Fulham" to "fulhamfc.com",
        "Liverpool" to "liverpoolfc.com",
        "Luton Town" to "lutontown.co.uk",
        "Manchester City" to "mancity.com",
        "Manchester United" to "manutd.com",
        "Newcastle United" to "nufc.co.uk",
        "Nottingham Forest" to "nottinghamforest.co.uk",
        "Sheffield United" to "sufc.co.uk",
        "Tottenham Hotspur" to "tottenhamhotspur.com",
        "West Ham" to "whufc.com",
        "Wolverhampton" to "wolves.co.uk",
        "Leeds United" to "leedsunited.com",
        "Leicester City" to "lcfc.com",
        "Southampton" to "southamptonfc.com",

        // SPAIN
        "Real Madrid" to "realmadrid.com",
        "Barcelona" to "fcbarcelona.com",
        "Atletico Madrid" to "atleticodemadrid.com",
        "Sevilla" to "sevillafc.es",
        "Real Sociedad" to "realsociedad.eus",
        "Athletic Club" to "athletic-club.eus",
        "Real Betis" to "realbetisbalompie.es",
        "Valencia" to "valenciacf.com",
        "Villarreal" to "villarrealcf.es",
        "Girona" to "gironafc.cat",

        // GERMANY
        "Bayern Munich" to "fcbayern.com",
        "Borussia Dortmund" to "bvb.de",
        "Bayer Leverkusen" to "bayer04.de",
        "RB Leipzig" to "rbleipzig.com",
        "Eintracht Frankfurt" to "eintracht.de",
        "Stuttgart" to "vfb.de",
        "Monchengladbach" to "borussia.de",
        "Wolfsburg" to "vfl-wolfsburg.de",

        // ITALY
        "Juventus" to "juventus.com",
        "AC Milan" to "acmilan.com",
        "Inter Milan" to "inter.it",
        "Napoli" to "sscnapoli.it",
        "Roma" to "asroma.com",
        "Lazio" to "sslazio.it",
        "Atalanta" to "atalanta.it",
        "Fiorentina" to "acffiorentina.com",
        "Bologna" to "bolognafc.it",

        // FRANCE
        "PSG" to "psg.fr",
        "Monaco" to "asmonaco.com",
        "Marseille" to "om.fr",
        "Lyon" to "ol.fr",
        "Lille" to "losc.fr",
        "Lens" to "rclens.fr",
        "Nice" to "ognice.com",
        "Rennes" to "staderennais.com",

        // SAUDI
        "Al Hilal" to "alhilal.com",
        "Al Nassr" to "alnassr.sa",
        "Al Ittihad" to "ittihadclub.sa",
        "Al Ahli" to "alahlifc.sa",
        "Al Ettifaq" to "ettifaq.com",

        // AMERICA
        "Inter Miami" to "intermiamicf.com",
        "LA Galaxy" to "lagalaxy.com",
        "LAFC" to "lafc.com",
        "Seattle Sounders" to "soundersfc.com",
        "Atlanta United" to "atlutd.com",
        "New York City FC" to "nycfc.com",
        "Philadelphia Union" to "philadelphiaunion.com",
        
        // PORTUGAL
        "Benfica" to "slbenfica.pt",
        "Porto" to "fcporto.pt",
        "Sporting CP" to "sporting.pt",
        "Braga" to "scbraga.pt",
        
        // NETHERLANDS
        "Ajax" to "ajax.nl",
        "PSV" to "psv.nl",
        "Feyenoord" to "feyenoord.nl",
        "AZ Alkmaar" to "az.nl"
    )

    val countries = listOf("England", "Italy", "France", "Germany", "Spain", "Saudi", "America", "Portugal", "Netherlands")
    var countryExpanded by remember { mutableStateOf(false) }
    var selectedCountry by remember { mutableStateOf(countries[0]) }
    
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
    
    var leagueExpanded by remember { mutableStateOf(false) }
    var selectedLeague by remember { mutableStateOf(leaguesByCountry[selectedCountry]?.get(0) ?: "Premier League") }
    
    var startYear by remember { mutableStateOf("2025") }
    
    val currencies = listOf("€ - EUR", "£ - GBP", "$ - USD")
    var currencyExpanded by remember { mutableStateOf(false) }
    var selectedCurrency by remember { mutableStateOf(currencies[0]) }
    
    var primaryColor by remember { mutableStateOf("#bfff00") }
    var secondaryColor by remember { mutableStateOf("#ffffff") }
    var clubCrestUrl by remember { mutableStateOf("") }
    
    val aiAgents = listOf("Google Gemini", "Claude", "ChatGPT", "Grok", "Other")
    var aiAgentExpanded by remember { mutableStateOf(false) }
    var selectedAiAgent by remember { mutableStateOf(aiAgents[0]) }
    
    var aiApiUrl by remember { mutableStateOf("") }
    var aiApiKey by remember { mutableStateOf("") }

    val context = LocalContext.current
    var showClubNamePromptDialog by remember { mutableStateOf(false) }
    var pendingImportedJson by remember { mutableStateOf<String?>(null) }
    var promptClubNameInput by remember { mutableStateOf("") }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val rawText = context.contentResolver.openInputStream(it)?.bufferedReader().use { reader -> reader?.readText() }
                if (!rawText.isNullOrBlank()) {
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

                    if (parsedObj != null) {
                        val importedClub = parsedObj.get("clubName")?.asString ?: ""
                        val importedManager = parsedObj.get("managerName")?.asString ?: ""
                        val importedLeague = parsedObj.get("leagueName")?.asString ?: ""
                        val importedYear = try { parsedObj.get("startYear")?.asInt?.toString() ?: "2025" } catch(e: Exception) { "2025" }
                        val importedPrimary = parsedObj.get("primaryColorHex")?.asString ?: ""
                        val importedSecondary = parsedObj.get("secondaryColorHex")?.asString ?: ""

                        // Update colors for theming
                        if (importedPrimary.isNotBlank()) primaryColor = importedPrimary.trim()
                        if (importedSecondary.isNotBlank()) secondaryColor = importedSecondary.trim()

                        // Autofill input fields on onboarding screen
                        if (importedManager.isNotBlank()) managerName = importedManager
                        if (importedLeague.isNotBlank()) selectedLeague = importedLeague
                        startYear = importedYear
                        isExistingTeam = false

                        // Check club name
                        if (importedClub.startsWith("*")) {
                            if (clubName.isNotBlank() && !clubName.startsWith("*")) {
                                val success = viewModel.importStateFromJson(
                                    jsonStr,
                                    clubNameOverride = clubName
                                )
                                if (success) {
                                    startYear = viewModel.state.value.startYear.toString()
                                    primaryColor = viewModel.state.value.primaryColorHex
                                    secondaryColor = viewModel.state.value.secondaryColorHex
                                    selectedLeague = viewModel.state.value.leagueName
                                    selectedCountry = viewModel.state.value.country
                                    android.widget.Toast.makeText(context, "Career imported for $clubName!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                pendingImportedJson = jsonStr
                                promptClubNameInput = ""
                                showClubNamePromptDialog = true
                            }
                        } else {
                            val success = viewModel.importStateFromJson(
                                jsonStr,
                                clubNameOverride = if (importedClub.isNotBlank()) importedClub else null
                            )
                            if (success) {
                                clubName = viewModel.state.value.clubName
                                managerName = viewModel.state.value.managerName
                                startYear = viewModel.state.value.startYear.toString()
                                primaryColor = viewModel.state.value.primaryColorHex
                                secondaryColor = viewModel.state.value.secondaryColorHex
                                selectedLeague = viewModel.state.value.leagueName
                                selectedCountry = viewModel.state.value.country
                                android.widget.Toast.makeText(context, "Career data imported successfully!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Invalid JSON file format", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to read file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showClubNamePromptDialog && pendingImportedJson != null) {
        AlertDialog(
            onDismissRequest = { showClubNamePromptDialog = false },
            title = { Text("Create-a-Club Name Detected", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Your exported career save uses a custom created club. What is your club's name?",
                        fontSize = 14.sp
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
                        val chosenName = if (promptClubNameInput.isNotBlank()) promptClubNameInput.trim() else "Torquay AFC"
                        clubName = chosenName
                        val success = viewModel.importStateFromJson(
                            pendingImportedJson!!,
                            clubNameOverride = chosenName
                        )
                        showClubNamePromptDialog = false
                        pendingImportedJson = null
                        if (success) {
                            clubName = viewModel.state.value.clubName
                            managerName = viewModel.state.value.managerName
                            startYear = viewModel.state.value.startYear.toString()
                            primaryColor = viewModel.state.value.primaryColorHex
                            secondaryColor = viewModel.state.value.secondaryColorHex
                            selectedLeague = viewModel.state.value.leagueName
                            selectedCountry = viewModel.state.value.country
                            android.widget.Toast.makeText(context, "Career imported for $chosenName!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Import & Launch")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showClubNamePromptDialog = false
                    pendingImportedJson = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding).imePadding()
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Onboarding & Club Setup", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)

                // Dedicated Career Save Import Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    "Import Career Mode Save",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Text(
                                    "Import JSON to autofill setup, squad, trophies & past seasons",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Button(
                            onClick = { importLauncher.launch("*/*") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose JSON File to Import", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isExistingTeam, onCheckedChange = { 
                        isExistingTeam = it
                        if (!it) {
                            clubName = ""
                            clubCrestUrl = ""
                        }
                    })
                    Text("Manage an Existing Real Club", color = Color.White)
                }
                
                if (isExistingTeam) {
                    val filteredClubs = realClubs.filter { it.contains(clubName, ignoreCase = true) }
                    ExposedDropdownMenuBox(
                        expanded = clubSearchExpanded && clubName.isNotEmpty() && filteredClubs.isNotEmpty(),
                        onExpandedChange = { clubSearchExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = clubName,
                            onValueChange = { 
                                clubName = it
                                clubSearchExpanded = true
                            },
                            label = { Text("Search Real Club") },
                            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clubSearchExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = clubSearchExpanded && clubName.isNotEmpty() && filteredClubs.isNotEmpty(),
                            onDismissRequest = { clubSearchExpanded = false }
                        ) {
                            filteredClubs.take(5).forEach { club ->
                                DropdownMenuItem(
                                    text = { Text(club) },
                                    onClick = {
                                        clubName = club
                                                                                isExistingTeam = true
                                        val t = club.lowercase().replace(" ", "")
                                        val domain = when {
                                            clubDomains.containsKey(club) -> clubDomains[club]
                                            t.contains("arsenal") -> "arsenal.com"
                                            t.contains("astonvilla") -> "avfc.co.uk"
                                            t.contains("chelsea") -> "chelseafc.com"
                                            t.contains("everton") -> "evertonfc.com"
                                            t.contains("liverpool") -> "liverpoolfc.com"
                                            t.contains("mancity") || t.contains("manchestercity") -> "mancity.com"
                                            t.contains("manutd") || t.contains("manchesterunited") -> "manutd.com"
                                            t.contains("newcastle") -> "nufc.co.uk"
                                            t.contains("tottenham") || t.contains("spurs") -> "tottenhamhotspur.com"
                                            t.contains("realmadrid") -> "realmadrid.com"
                                            t.contains("barcelona") -> "fcbarcelona.com"
                                            t.contains("atletico") -> "en.atleticodemadrid.com"
                                            t.contains("bayern") -> "fcbayern.com"
                                            t.contains("dortmund") -> "bvb.de"
                                            t.contains("leverkusen") -> "bayer04.de"
                                            t.contains("juventus") -> "juventus.com"
                                            t.contains("milan") && !t.contains("inter") -> "acmilan.com"
                                            t.contains("inter") -> "inter.it"
                                            t.contains("napoli") -> "sscnapoli.it"
                                            t.contains("roma") -> "asroma.com"
                                            t.contains("psg") || t.contains("paris") -> "en.psg.fr"
                                            t.contains("marseille") -> "om.fr"
                                            t.contains("lyon") -> "ol.fr"
                                            t.contains("ajax") -> "english.ajax.nl"
                                            t.contains("psv") -> "psv.nl"
                                            t.contains("feyenoord") -> "feyenoord.com"
                                            t.contains("benfica") -> "slbenfica.pt"
                                            t.contains("porto") -> "fcporto.pt"
                                            t.contains("sporting") -> "sporting.pt"
                                            t.contains("alnassr") -> "alnassr.sa"
                                            t.contains("alhilal") -> "alhilal.com"
                                            t.contains("alittihad") -> "ittihadfc.com"
                                            t.contains("intermiami") -> "intermiamicf.com"
                                            t.contains("lagalaxy") -> "lagalaxy.com"
                                            t.contains("brighton") -> "brightonandhovealbion.com"
                                            t.contains("westham") -> "whufc.com"
                                            t.contains("crystalpalace") -> "cpfc.co.uk"
                                            t.contains("fulham") -> "fulhamfc.com"
                                            t.contains("brentford") -> "brentfordfc.com"
                                            t.contains("bournemouth") -> "afcb.co.uk"
                                            t.contains("wolverhampton") || t.contains("wolves") -> "wolves.co.uk"
                                            t.contains("nottingham") -> "nottinghamforest.co.uk"
                                            else -> t + ".com"
                                        }
                                        clubCrestUrl = "https://icon.horse/icon/$domain"
                                        
                                        clubColors[club]?.let { colors ->
                                            primaryColor = colors.first
                                            secondaryColor = colors.second
                                        } ?: run {
                                            primaryColor = "#546E7A"
                                            secondaryColor = "#FFC107"
                                        }
                                        clubSearchExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = clubName,
                        onValueChange = { clubName = it },
                        label = { Text("Club Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                
                OutlinedTextField(
                    value = managerName,
                    onValueChange = { managerName = it },
                    label = { Text("Manager Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = countryExpanded,
                    onExpandedChange = { countryExpanded = !countryExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCountry,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Country") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded) },
                        modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = countryExpanded,
                        onDismissRequest = { countryExpanded = false }
                    ) {
                        countries.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    selectedCountry = selectionOption
                                    selectedLeague = leaguesByCountry[selectionOption]?.get(0) ?: "League"
                                    countryExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = leagueExpanded,
                    onExpandedChange = { leagueExpanded = !leagueExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedLeague,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("League") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = leagueExpanded) },
                        modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = leagueExpanded,
                        onDismissRequest = { leagueExpanded = false }
                    ) {
                        leaguesByCountry[selectedCountry]?.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    selectedLeague = selectionOption
                                    leagueExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startYear,
                        onValueChange = { startYear = it },
                        label = { Text("Starting Year") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    
                    ExposedDropdownMenuBox(
                        expanded = currencyExpanded,
                        onExpandedChange = { currencyExpanded = !currencyExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedCurrency,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Currency") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = currencyExpanded,
                            onDismissRequest = { currencyExpanded = false }
                        ) {
                            currencies.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        selectedCurrency = selectionOption
                                        currencyExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                OutlinedTextField(
                    value = clubCrestUrl,
                    onValueChange = { clubCrestUrl = it },
                    label = { Text("Club Crest URL (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    var showPrimaryPicker by remember { mutableStateOf(false) }
                    var showSecondaryPicker by remember { mutableStateOf(false) }
                    val colorOptions = listOf("#bfff00", "#ff0000", "#00ff00", "#0000ff", "#ffffff", "#000000", "#ffff00", "#00ffff", "#ff00ff", "#808080", "#ffa500", "#800080", "#008000", "#000080", "#800000", "#008080")
                    
                    if (showPrimaryPicker) {
                        androidx.compose.ui.window.Dialog(onDismissRequest = { showPrimaryPicker = false }) {
                            Card(modifier = Modifier.padding(16.dp)) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Select Primary Color", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                    ColorWheel(modifier = Modifier.size(200.dp)) { color ->
                                        primaryColor = color.toHexCode()
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Or choose a preset:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                    val rows = colorOptions.chunked(4)
                                    rows.forEach { row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                            row.forEach { colorStr ->
                                                Box(modifier = Modifier.size(40.dp).background(Color(android.graphics.Color.parseColor(colorStr)), androidx.compose.foundation.shape.CircleShape).clickable { primaryColor = colorStr; showPrimaryPicker = false }.border(1.dp, Color.Gray, androidx.compose.foundation.shape.CircleShape))
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { showPrimaryPicker = false }) { Text("Done") }
                                }
                            }
                        }
                    }
                    if (showSecondaryPicker) {
                        androidx.compose.ui.window.Dialog(onDismissRequest = { showSecondaryPicker = false }) {
                            Card(modifier = Modifier.padding(16.dp)) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Select Secondary Color", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                    ColorWheel(modifier = Modifier.size(200.dp)) { color ->
                                        secondaryColor = color.toHexCode()
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Or choose a preset:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                                    val rows = colorOptions.chunked(4)
                                    rows.forEach { row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                            row.forEach { colorStr ->
                                                Box(modifier = Modifier.size(40.dp).background(Color(android.graphics.Color.parseColor(colorStr)), androidx.compose.foundation.shape.CircleShape).clickable { secondaryColor = colorStr; showSecondaryPicker = false }.border(1.dp, Color.Gray, androidx.compose.foundation.shape.CircleShape))
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { showSecondaryPicker = false }) { Text("Done") }
                                }
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("Primary Color", fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(bottom = 4.dp))
                        Box(modifier = Modifier.size(48.dp).background(try { Color(android.graphics.Color.parseColor(primaryColor)) } catch(e:Exception){Color.Transparent}, androidx.compose.foundation.shape.CircleShape).clickable { showPrimaryPicker = true }.border(1.dp, Color.White, androidx.compose.foundation.shape.CircleShape))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("Secondary Color", fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(bottom = 4.dp))
                        Box(modifier = Modifier.size(48.dp).background(try { Color(android.graphics.Color.parseColor(secondaryColor)) } catch(e:Exception){Color.Transparent}, androidx.compose.foundation.shape.CircleShape).clickable { showSecondaryPicker = true }.border(1.dp, Color.White, androidx.compose.foundation.shape.CircleShape))
                    }
                }
                
                Text("AI Agent Setting", fontWeight = FontWeight.Bold, color = Color.White)
                
                ExposedDropdownMenuBox(
                    expanded = aiAgentExpanded,
                    onExpandedChange = { aiAgentExpanded = !aiAgentExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedAiAgent,
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
                                    selectedAiAgent = selectionOption
                                    aiAgentExpanded = false
                                }
                            )
                        }
                    }
                }
                
                if (selectedAiAgent == "Other") {
                    OutlinedTextField(
                        value = aiApiUrl,
                        onValueChange = { aiApiUrl = it },
                        label = { Text("API URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = aiApiKey,
                    onValueChange = { aiApiKey = it },
                    label = { Text(if (selectedAiAgent == "Other") "API Key" else "${selectedAiAgent} API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )

                Button(
                    onClick = {
                        val finalManager = if (managerName.isNotBlank()) managerName else "Manager"
                        val finalClub = if (clubName.isNotBlank()) clubName else "FC Club"
                        val parsedYear = startYear.toIntOrNull() ?: 2025
                        onComplete(finalManager, finalClub, selectedCountry, selectedLeague, parsedYear, selectedCurrency, primaryColor, secondaryColor, clubCrestUrl, isExistingTeam, selectedAiAgent, aiApiUrl, aiApiKey)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Complete Setup", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
