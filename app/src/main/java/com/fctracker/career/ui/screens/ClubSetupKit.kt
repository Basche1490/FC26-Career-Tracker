package com.fctracker.career.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.AppState
import com.fctracker.career.MainViewModel
import com.fctracker.career.logic.Accent
import com.fctracker.career.ui.components.ButtonKind
import com.fctracker.career.ui.components.ClubCrest
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.FcDialog
import com.fctracker.career.ui.components.FcField
import com.fctracker.career.ui.components.FcLabel
import com.fctracker.career.ui.components.FcSwitchRow
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.Mono
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.Segmented
import com.fctracker.career.ui.theme.FcTheme
import com.fctracker.career.ui.theme.parseHex

/** The choices on the club setup and club profile forms. */
object ClubSetup {
    val COUNTRIES = listOf("England", "Spain", "Italy", "Germany", "France", "Portugal", "Netherlands", "Saudi Arabia", "America", "Scotland", "Other")

    val LEAGUES: Map<String, List<String>> = mapOf(
        "England" to listOf("Premier League", "EFL Championship", "EFL League One", "EFL League Two"),
        "Spain" to listOf("LALIGA EA SPORTS", "LALIGA HYPERMOTION"),
        "Italy" to listOf("Serie A", "Serie B"),
        "Germany" to listOf("Bundesliga", "2. Bundesliga", "3. Liga"),
        "France" to listOf("Ligue 1", "Ligue 2"),
        "Portugal" to listOf("Liga Portugal"),
        "Netherlands" to listOf("Eredivisie"),
        "Saudi Arabia" to listOf("ROSHN Saudi League"),
        "America" to listOf("MLS"),
        "Scotland" to listOf("Scottish Premiership")
    )

    /** The leagues to pick from for [country], with [current] kept when it is not in the list (an older name or a typed one). */
    fun leaguesFor(country: String, current: String = ""): List<String> {
        val list = LEAGUES[country] ?: LEAGUES[LEAGUES.keys.firstOrNull { it.equals(country, true) || (country.equals("Saudi", true) && it.startsWith("Saudi")) || (country.equals("USA", true) && it == "America") } ?: ""] ?: emptyList()
        return if (current.isNotBlank() && list.none { it.equals(current, true) }) list + current else list
    }

    val CURRENCIES = listOf("Euro (€)", "Pound (£)", "Dollar ($)")
    fun currencyLabel(symbol: String) = CURRENCIES.firstOrNull { it.contains("($symbol)") } ?: CURRENCIES.first()
    fun currencySymbol(label: String) = label.substringAfter("(").substringBefore(")").ifBlank { label.take(1) }

    val AI_AGENTS = listOf("Google Gemini", "Claude", "ChatGPT", "Grok", "Other")
    fun agentLabel(stored: String) = AI_AGENTS.firstOrNull { stored.startsWith(it, true) } ?: if (stored.isBlank()) AI_AGENTS.first() else "Other"
    fun defaultApiUrl(agent: String) = when {
        agent.contains("Gemini", true) -> "https://generativelanguage.googleapis.com"
        agent.contains("Claude", true) -> "https://api.anthropic.com"
        agent.contains("ChatGPT", true) -> "https://api.openai.com"
        agent.contains("Grok", true) -> "https://api.x.ai"
        else -> ""
    }

    val SWATCHES = listOf("#0B57D0", "#B3261E", "#0F6B39", "#7A4D00", "#6A2FB5", "#0E1A2B", "#C6F432", "#FF9D8A", "#FFFFFF", "#111111")

    /** Known colours of real clubs (primary, secondary). */
    val CLUB_COLOURS: Map<String, Pair<String, String>> = mapOf(

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

    fun coloursOf(club: String): Pair<String, String>? = CLUB_COLOURS[club] ?: CLUB_COLOURS.entries.firstOrNull { club.contains(it.key, true) || it.key.contains(club, true) }?.value
}

/** Own club: upload a crest image or paste a link. It replaces the initials in the menu and on Home, and goes on shared cards. */
@Composable
fun CrestBlock(viewModel: MainViewModel, state: AppState, clubName: String, crestUrl: String, onCrestUrl: (String) -> Unit) {
    val c = FcTheme.colors
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            viewModel.importCrestImage(uri) { ok ->
                busy = false
                if (!ok) Toast.makeText(context, "That image could not be used. Try a PNG, JPG or WebP under 6 MB.", Toast.LENGTH_LONG).show()
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FcLabel("CLUB CREST")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)).background(c.inset).border(1.dp, c.border, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                ClubCrest(clubName, state.clubCrestFile, crestUrl, false, 48.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FcButton(if (busy) "Saving…" else "Upload image", { picker.launch("image/*") }, kind = ButtonKind.SECONDARY, icon = Icons.Default.Image, small = true, enabled = !busy)
                if (state.clubCrestFile.isNotBlank()) FcButton("Remove", { viewModel.removeCrestImage() }, kind = ButtonKind.GHOST, icon = Icons.Default.Delete, small = true)
            }
        }
        FcField("Or paste an image link", crestUrl, onCrestUrl, placeholder = "https://…/crest.png", leadingIcon = Icons.Default.Link)
        Muted("PNG, JPG or WebP; a square image works best. The crest shows left of the club name in the menu and on Home, and on shared cards.", 11.sp)
    }
}

/** Real club: the crest comes with the club. Tapping it later opens the club's history. */
@Composable
fun RealCrestRow(viewModel: MainViewModel, state: AppState, clubName: String, onUseOwn: () -> Unit) {
    val c = FcTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FcLabel("CLUB CREST")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)).background(c.inset), contentAlignment = Alignment.Center) {
                ClubCrest(clubName, state.clubCrestFile, "", true, 48.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                FcText("Taken from the club", 14.sp, FontWeight.SemiBold)
                Muted("The crest of the club you picked.", 12.sp)
                FcButton("Use my own image instead", onUseOwn, kind = ButtonKind.GHOST, icon = Icons.Default.Image, small = true)
            }
        }
        Muted("Tap the crest in the menu or on Home to read the club’s history.", 11.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(selected: String, onPick: (String) -> Unit) {
    val c = FcTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (ClubSetup.SWATCHES + selected.uppercase()).distinct().forEach { hex ->
            val on = hex.equals(selected, true)
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(parseHex(hex) ?: Color.Gray)
                    .border(if (on) 3.dp else 1.dp, if (on) c.text else c.border, CircleShape).clickable { onPick(hex) }
            )
        }
    }
}

/**
 * The club colours (primary and secondary), the switch for using them in the app, and a preview of the accent the
 * app makes from them.
 */
@Composable
fun ColourRows(primary: String, secondary: String, onColours: (String, String) -> Unit, useClubColours: Boolean, onUseClubColours: (Boolean) -> Unit, note: String? = null) {
    val c = FcTheme.colors
    var wheel by remember { mutableStateOf<Int?>(null) }
    val acc = Accent.choose(primary, secondary)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FcLabel("PRIMARY COLOUR")
        Swatches(primary) { onColours(it, secondary) }
        FcLabel("SECONDARY COLOUR")
        Swatches(secondary) { onColours(primary, it) }
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(c.surface2).border(1.dp, c.border, RoundedCornerShape(20.dp)).clickable { wheel = 0 }.padding(start = 8.dp, end = 12.dp).height(40.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Palette, null, tint = c.accent, modifier = Modifier.size(22.dp))
            FcText("More colours", 13.sp, FontWeight.SemiBold)
        }
        if (note != null) Muted(note, 11.sp)
        FcSwitchRow("Use club colours in the app", useClubColours, onUseClubColours, sub = "Buttons, links and highlights take the club colour. Win, draw and loss colours never change.")
        val accentColour = if (useClubColours) parseHex(acc.hex) ?: c.accent else parseHex(Accent.DEFAULT) ?: c.accent
        val accentHex = if (useClubColours) acc.hex else Accent.DEFAULT
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.inset).border(1.dp, c.border, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(parseHex(primary) ?: Color.Gray).border(1.dp, c.border, CircleShape))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.muted, modifier = Modifier.size(16.dp))
            Box(Modifier.size(26.dp).clip(CircleShape).background(accentColour))
            Column(Modifier.weight(1f)) {
                FcText("App accent ${accentHex.uppercase()}", 13.sp, FontWeight.SemiBold)
                Muted(if (useClubColours) acc.why else "club colours are switched off: the default mint is used", 11.sp)
            }
            Box(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(accentColour).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                FcText("Log match", 12.sp, FontWeight.Bold, parseHex(Accent.onColour(accentHex)) ?: Color.Black)
            }
        }
    }
    wheel?.let { start -> ColourWheelDialog(primary, secondary, start, onDismiss = { wheel = null }, onUse = { p, s -> onColours(p, s); wheel = null }) }
}

/** More colours: a colour wheel with brightness and a hex field, and what the app will make of the colour. */
@Composable
fun ColourWheelDialog(primary: String, secondary: String, startOn: Int, onDismiss: () -> Unit, onUse: (String, String) -> Unit) {
    val c = FcTheme.colors
    var which by remember { mutableStateOf(startOn) }
    var p by remember { mutableStateOf(primary.uppercase()) }
    var s by remember { mutableStateOf(secondary.uppercase()) }
    val current = if (which == 0) p else s
    fun hsv(hex: String): FloatArray { val out = FloatArray(3); runCatching { android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(hex), out) }; return out }
    var base by remember(which) { mutableStateOf(hsv(current)) }
    var hexText by remember(which) { mutableStateOf(current) }
    fun set(hex: String) { if (which == 0) p = hex else s = hex; hexText = hex }
    fun fromHsv(h: FloatArray) = String.format("#%06X", 0xFFFFFF and android.graphics.Color.HSVToColor(h))
    val acc = Accent.choose(current, if (which == 0) s else p)
    FcDialog(if (which == 0) "Primary colour" else "Secondary colour", onDismiss, actions = {
        FcButton("Cancel", onDismiss, kind = ButtonKind.SECONDARY, small = true)
        FcButton("Use colour", { onUse(p, s) }, small = true)
    }) {
        Segmented(listOf("Primary", "Secondary"), which, { which = it })
        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(horizontal = 24.dp)) {
            ColorWheel(Modifier.fillMaxWidth().aspectRatio(1f)) { col ->
                val h = FloatArray(3)
                android.graphics.Color.colorToHSV(android.graphics.Color.rgb((col.red * 255).toInt(), (col.green * 255).toInt(), (col.blue * 255).toInt()), h)
                h[2] = base[2].takeIf { it > 0.05f } ?: 1f
                base = h
                set(fromHsv(h))
            }
        }
        Muted("Brightness", 12.sp)
        Slider(value = base[2], onValueChange = { v -> val h = base.copyOf(); h[2] = v; base = h; set(fromHsv(h)) },
            colors = SliderDefaults.colors(thumbColor = parseHex(current) ?: c.accent, activeTrackColor = parseHex(current) ?: c.accent, inactiveTrackColor = c.surface2))
        FcField("Hex code", hexText, { t ->
            hexText = t.uppercase()
            val clean = t.trim().let { if (it.startsWith("#")) it else "#$it" }
            if (Regex("#[0-9A-Fa-f]{6}").matches(clean)) { if (which == 0) p = clean.uppercase() else s = clean.uppercase(); base = hsv(clean) }
        })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Club colour" to current, "In the app" to acc.hex).forEach { (label, hex) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(c.inset).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Muted(label, 11.sp)
                    Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp)).background(parseHex(hex) ?: Color.Gray))
                    Mono(hex.uppercase(), 12.sp)
                }
            }
        }
        Muted(if (acc.hex.equals(current, true)) "This colour reads well on the dark background, so the app uses it as it is."
              else "The app uses ${acc.hex.uppercase()} (${acc.why}). The club colour itself is saved as you picked it.", 11.sp)
    }
}

/**
 * Importing a career backup (the app's own encrypted .aes file or plain JSON). Returns the function that opens the
 * file picker; asks for the club's name when the save is of a created club.
 */
@Composable
fun rememberCareerImport(viewModel: MainViewModel, onImported: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@rememberLauncherForActivityResult
            val trimmed = raw.trim()
            val json = if (trimmed.startsWith("{") || trimmed.startsWith("[")) trimmed else com.fctracker.career.utils.EncryptionUtils.decrypt(raw).ifEmpty { trimmed }
            val club = runCatching { com.google.gson.JsonParser.parseString(json).asJsonObject.get("clubName")?.asString }.getOrNull().orEmpty()
            val current = viewModel.state.value.clubName
            if (club.startsWith("*") && (current.isBlank() || current.startsWith("*") || current == "Richmond FC")) {
                pending = json
            } else {
                val ok = viewModel.importStateFromJson(json, clubNameOverride = if (club.startsWith("*")) current else null)
                Toast.makeText(context, if (ok) "Career imported" else "That file is not a career backup", Toast.LENGTH_SHORT).show()
                if (ok) onImported()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not read the file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    pending?.let { json ->
        var name by remember { mutableStateOf("") }
        FcDialog("Created club", { pending = null }, actions = {
            FcButton("Cancel", { pending = null }, kind = ButtonKind.SECONDARY, small = true)
            FcButton("Import career", {
                val ok = viewModel.importStateFromJson(json, clubNameOverride = name.trim().ifBlank { "My Custom Club" })
                Toast.makeText(context, if (ok) "Career imported" else "That file is not a career backup", Toast.LENGTH_SHORT).show()
                pending = null
                if (ok) onImported()
            }, small = true)
        }) {
            Muted("This save is of a club you created in the game. What is your club's name?", 13.sp)
            FcField("Club name", name, { name = it }, placeholder = "Torquay AFC")
        }
    }
    return { launcher.launch("*/*") }
}
