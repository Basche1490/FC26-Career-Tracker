package com.fctracker.career.ui.screens
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextOverflow
import com.fctracker.career.Player

private fun searchEafcPlayers(query: String, allPlayers: List<com.fctracker.career.EafcPlayer>): List<com.fctracker.career.EafcPlayer> {
    val cleanQ = query.trim().lowercase()
    if (cleanQ.isEmpty()) return emptyList()

    return allPlayers.asSequence()
        .filter { it.team != "Retired" }
        .mapNotNull { p ->
            val nameNorm = p.name.lowercase()
            val commonNorm = p.commonName.lowercase()
            val score = when {
                nameNorm == cleanQ || commonNorm == cleanQ -> 300
                commonNorm.startsWith(cleanQ) -> 250
                nameNorm.startsWith(cleanQ) -> 200
                commonNorm.contains(cleanQ) || nameNorm.contains(cleanQ) -> 150
                else -> 0
            }
            if (score > 0) Triple(score, p.rating, p) else null
        }
        .sortedWith(compareByDescending<Triple<Int, Int, com.fctracker.career.EafcPlayer>> { it.first }.thenByDescending { it.second })
        .take(6)
        .map { it.third }
        .toList()
}

/**
 * The nickname to save. The app shows the nickname before the Full Name, so a nickname that is only the common name of
 * a regen template (copied in by an older version, or by picking the template in this dialog) must go as soon as the
 * regen is given a name of its own; otherwise the corrected name never shows and "reverts" to the template's name.
 * Any other nickname the person typed is kept.
 */
internal fun nicknameToKeep(
    isRegen: Boolean,
    typedName: String,
    typedNickname: String,
    old: Player?,
    pickedTemplate: com.fctracker.career.EafcPlayer?,
    allEafcPlayers: List<com.fctracker.career.EafcPlayer>
): String? {
    val nickname = typedNickname.trim()
    if (nickname.isEmpty()) return null
    val name = typedName.trim()
    if (isRegen) {
        // picked in this dialog
        if (pickedTemplate != null && pickedTemplate.commonName.isNotBlank() &&
            nickname.equals(pickedTemplate.commonName, ignoreCase = true) &&
            !name.equals(pickedTemplate.name, ignoreCase = true) && !name.equals(pickedTemplate.commonName, ignoreCase = true)
        ) return null
        // copied in earlier and left untouched since: the name was corrected, the nickname is the template's
        if (old != null && old.isRegen && name != old.name.trim() && nickname == old.nickname?.trim() &&
            allEafcPlayers.any { it.commonName.equals(nickname, ignoreCase = true) && !it.name.equals(name, ignoreCase = true) }
        ) return null
    }
    return typedNickname.takeIf { it.isNotBlank() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlayerDialog(startYear: Int = 2025, player: Player?, allEafcPlayers: List<com.fctracker.career.EafcPlayer> = emptyList(), onDismiss: () -> Unit, onUpdate: (Player) -> Unit, onDelete: (() -> Unit)? = null) {
    var name by remember { mutableStateOf(player?.name ?: "") }
    var nickname by remember { mutableStateOf(player?.nickname ?: "") }
    var primaryPosition by remember { mutableStateOf(player?.primaryPosition ?: "CM") }
    var secondaryPosition by remember { mutableStateOf(player?.secondaryPosition ?: "") }
    var tertiaryPosition by remember { mutableStateOf(player?.tertiaryPosition ?: "") }
    var age by remember { mutableStateOf(player?.age?.toString() ?: "21") }
    var startingOverall by remember { mutableStateOf(player?.startingOverall?.toString() ?: "60") }
    var overall by remember { mutableStateOf(player?.overall?.toString() ?: "60") }
    var valueM by remember { mutableStateOf(player?.valueM?.toString() ?: "1.0") }
    var wageK by remember { mutableStateOf(player?.wageK?.toString() ?: "5") }
    var nationality by remember { mutableStateOf(player?.nationality ?: "") }
    var birthDate by remember { mutableStateOf(player?.birthDate ?: "2000-01-01") }

    // Compute age dynamically
    LaunchedEffect(birthDate, startYear) {
        try {
            val parts = birthDate.split("-")
            if (parts.size >= 1) {
                val birthYear = parts[0].toInt()
                age = (startYear - birthYear).toString()
            }
        } catch(e: Exception) {}
    }

    var height by remember { mutableStateOf(player?.height?.toString() ?: "180") }
    var weight by remember { mutableStateOf(player?.weight?.toString() ?: "75") }
    var preferredFoot by remember { mutableStateOf(player?.preferredFoot ?: "Right") }
    var weakFoot by remember { mutableStateOf(player?.weakFoot?.toString() ?: "3") }
    var isRegen by remember { mutableStateOf(player?.isRegen ?: false) }
    var headshotUrl by remember { mutableStateOf(player?.headshotUrl ?: "") }
    var status by remember { mutableStateOf(player?.status ?: "Active") }

    val positions = listOf("GK", "CB", "LB", "RB", "LWB", "RWB", "CDM", "CM", "CAM", "LM", "RM", "LW", "RW", "CF", "ST", "")
    val forms = listOf("Excellent", "Good", "Okay", "Poor")
    val morales = listOf("Happy", "Content", "Unhappy")
    val feet = listOf("Right", "Left", "Both")
    val weakFootOptions = listOf("1", "2", "3", "4", "5")

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp).heightIn(max = 600.dp)) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (player == null) "Add Player" else "Edit Player", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Box(modifier = Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        if (isValidPlayerHeadshot(headshotUrl)) {
                            coil.compose.AsyncImage(
                                model = headshotUrl,
                                contentDescription = "Player Face",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Person, contentDescription = "Face Placeholder", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(overall, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Overall", fontSize = 12.sp, color = Color.LightGray)
                    }
                }
                
                var nameExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = nameExpanded, onExpandedChange = { nameExpanded = !nameExpanded }) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { 
                            name = it
                            // A regen has a name of his own that is not in the player database; the regen search
                            // below lends the picture, so this field must not offer to overwrite the card.
                            nameExpanded = !isRegen
                        },
                        label = { Text("Full Name (Required)") },
                        modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable, true).fillMaxWidth(),
                        singleLine = true
                    )
                    if (!isRegen && name.isNotEmpty() && name.length >= 1) {
                        val filtered = searchEafcPlayers(name, allEafcPlayers)
                        if (filtered.isNotEmpty()) {
                            ExposedDropdownMenu(expanded = nameExpanded, onDismissRequest = { nameExpanded = false }) {
                                filtered.forEach { p ->
                                    val displayName = if (p.commonName.isNotBlank()) "${p.commonName} (${p.name})" else p.name
                                    val thumbUrl = "https://cdn.futbin.com/content/fifa25/img/players/${p.id}.png"
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    coil.compose.AsyncImage(
                                                        model = thumbUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                    )
                                                }
                                                Spacer(Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = displayName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${p.position} • OVR ${p.rating} • ${p.team}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            name = p.name
                                            nickname = p.commonName
                                            primaryPosition = p.position
                                            val alt = p.alternatePositions.split(",")
                                            secondaryPosition = alt.getOrNull(0)?.trim() ?: ""
                                            tertiaryPosition = alt.getOrNull(1)?.trim() ?: ""
                                            val birthYear = try { p.birthdate.split("/")[2].split(" ")[0].toInt() } catch(e:Exception) { 2000 }
                                            age = (startYear - birthYear).toString()
                                            overall = p.rating.toString()
                                            startingOverall = p.rating.toString()
                                            valueM = p.value.toString()
                                            wageK = p.wage.toString()
                                            nationality = p.nationality
                                            try {
                                                val parts = p.birthdate.split(" ")[0].split("/")
                                                birthDate = "${parts[2]}-${parts[0].padStart(2, '0')}-${parts[1].padStart(2, '0')}"
                                            } catch(e: Exception) {}
                                            height = p.height.toString()
                                            weight = p.weight.toString()
                                            preferredFoot = p.preferredFoot
                                            weakFoot = p.weakFoot.toString()
                                            headshotUrl = "https://cdn.futbin.com/content/fifa25/img/players/${p.id}.png"
                                            nameExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("Nickname (Optional)") }, modifier = Modifier.fillMaxWidth())
                if (nickname.isNotBlank()) {
                    Text(
                        "The app shows the nickname instead of the Full Name. Clear it to show the Full Name.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                DropdownField("Position (Required)", primaryPosition, positions.filter { it.isNotEmpty() }) { primaryPosition = it }
                DropdownField("Secondary Position", secondaryPosition, positions) { secondaryPosition = it }
                DropdownField("Tertiary Position", tertiaryPosition, positions) { tertiaryPosition = it }
                if (player != null) { DropdownField("Status", status, listOf("Active", "Sold", "Loaned Out", "Loaned In", "Injured", "Academy", "Retired")) { status = it } }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = valueM, onValueChange = { valueM = it }, label = { Text("Market Value (€M)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = wageK, onValueChange = { wageK = it }, label = { Text("Weekly Wage (€K)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }

                // The original player a regen is based on. It is saved with the player: it starts from what was saved
                // (blank when nothing was), never from the regen's own name.
                var regenSearchText by remember { mutableStateOf(player?.regenOriginalName.orEmpty()) }
                // The template picked in this dialog, so the card's name is not overridden by the template's common name
                var pickedTemplate by remember { mutableStateOf<com.fctracker.career.EafcPlayer?>(null) }
                // The suggestions only open while typing, not when the dialog opens with the saved original player
                var regenListOpen by remember { mutableStateOf(false) }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isRegen,
                        onCheckedChange = { isRegen = it }
                    )
                    Text("Is Regen", color = Color.White)
                }

                if (isRegen) {
                    val filteredRegens = remember(regenSearchText, allEafcPlayers, regenListOpen) {
                        if (regenListOpen && regenSearchText.trim().isNotEmpty()) {
                            searchEafcPlayers(regenSearchText, allEafcPlayers)
                        } else emptyList()
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = regenSearchText,
                            onValueChange = {
                                regenSearchText = it
                                regenListOpen = true
                            },
                            label = { Text("Search Original Player / Regen Template") },
                            placeholder = { Text("Type player name (e.g. Messi, Haaland, Modric)") },
                            supportingText = { Text("The real player this regen is based on. It is saved with the player; the Full Name above stays the regen's own name.") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                if (regenSearchText.isNotEmpty()) {
                                    IconButton(onClick = { regenSearchText = ""; pickedTemplate = null; regenListOpen = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                        if (filteredRegens.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(4.dp)) {
                                    filteredRegens.forEach { p ->
                                        val displayName = if (p.commonName.isNotBlank()) "${p.commonName} (${p.name})" else p.name
                                        val thumbUrl = "https://cdn.futbin.com/content/fifa25/img/players/${p.id}.png"
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    regenSearchText = displayName
                                                    regenListOpen = false
                                                    pickedTemplate = p
                                                    // Editing an existing player: the regen template only lends its picture.
                                                    // Adding a new player: it fills in the whole card.
                                                    if (player == null) {
                                                        if (name.isBlank() || name == player?.name) {
                                                            name = p.name
                                                        }
                                                        nickname = p.commonName
                                                        nationality = p.nationality
                                                        primaryPosition = p.position
                                                        val alt = p.alternatePositions.split(",")
                                                        secondaryPosition = alt.getOrNull(0)?.trim() ?: ""
                                                        tertiaryPosition = alt.getOrNull(1)?.trim() ?: ""
                                                        val birthYear = try { p.birthdate.split("/")[2].split(" ")[0].toInt() } catch(e:Exception) { 2000 }
                                                        age = (startYear - birthYear).toString()
                                                        try {
                                                            val parts = p.birthdate.split(" ")[0].split("/")
                                                            birthDate = "${parts[2]}-${parts[0].padStart(2, '0')}-${parts[1].padStart(2, '0')}"
                                                        } catch(e: Exception) {}
                                                        preferredFoot = p.preferredFoot
                                                        weakFoot = p.weakFoot.toString()
                                                        height = p.height.toString()
                                                        weight = p.weight.toString()
                                                        overall = p.rating.toString()
                                                        startingOverall = p.rating.toString()
                                                    }
                                                    headshotUrl = "https://cdn.futbin.com/content/fifa25/img/players/${p.id}.png"
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(MaterialTheme.colorScheme.background),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                coil.compose.AsyncImage(
                                                    model = thumbUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = displayName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${p.position} • OVR ${p.rating} • ${p.team}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(value = nationality, onValueChange = { nationality = it }, label = { Text("Nationality") }, modifier = Modifier.fillMaxWidth())
                var showDatePicker by remember { mutableStateOf(false) }
                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState()
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                                    birthDate = sdf.format(java.util.Date(millis))
                                }
                                showDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }
                OutlinedTextField(
                    value = birthDate,
                    onValueChange = {},
                    label = { Text("Birth Date") },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Age (Auto)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), readOnly = true)
                    OutlinedTextField(value = startingOverall, onValueChange = { startingOverall = it }, label = { Text("Starting OVR") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text("Height (cm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) { DropdownField("Preferred Foot", preferredFoot, feet) { preferredFoot = it } }
                    Box(modifier = Modifier.weight(1f)) { DropdownField("Weak Foot", weakFoot, weakFootOptions) { weakFoot = it } }
                }

                OutlinedTextField(value = headshotUrl, onValueChange = { headshotUrl = it }, label = { Text("Headshot URL") }, modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(16.dp))

                if (player != null && (player.isPromoted || !player.isAcademy)) {
                    OutlinedButton(
                        onClick = {
                            onUpdate(player.copy(isAcademy = true, isPromoted = false, status = "Academy", isStartingXI = false))
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E676)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Move Back to Youth Academy", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    if (onDelete != null) {
                        Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Delete") }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Button(onClick = {
                        val isAcademyPlayer = if (player != null) {
                            status.equals("Academy", ignoreCase = true) || (player.isAcademy && !status.equals("Active", ignoreCase = true) && !status.equals("Sold", ignoreCase = true) && !status.equals("Retired", ignoreCase = true))
                        } else {
                            status.equals("Academy", ignoreCase = true)
                        }

                        val isRetired = status.equals("Retired", ignoreCase = true)
                        // An existing player keeps his current rating and potential unless "Starting OVR" was edited
                        // (that field is the only way to correct the rating here). Saving must not reset them.
                        val startingEdited = player != null && startingOverall != player.startingOverall.toString()
                        val keptOverall = if (player == null || startingEdited) startingOverall.toIntOrNull() ?: 60 else player.overall
                        val keptPotential = if (player == null) overall.toIntOrNull() ?: 60 else maxOf(player.potential, keptOverall)
                        val savedNickname = nicknameToKeep(isRegen, name, nickname, player, pickedTemplate, allEafcPlayers)
                        val savedOriginal = if (isRegen) regenSearchText.trim().takeIf { it.isNotEmpty() } else null
                        val newPlayer = player?.copy(
                            name = name.trim(),
                            nickname = savedNickname,
                            // A display name carried over from an old backup beats both name fields; once either was
                            // edited here it must not keep hiding the edit.
                            customDisplayName = if (name.trim() != player.name.trim() || savedNickname != player.nickname) null
                            else player.customDisplayName,
                            primaryPosition = primaryPosition,
                            secondaryPosition = secondaryPosition.takeIf { it.isNotBlank() },
                            tertiaryPosition = tertiaryPosition.takeIf { it.isNotBlank() },
                            age = age.toIntOrNull() ?: 21,
                            startingOverall = startingOverall.toIntOrNull() ?: 60,
                            overall = keptOverall,
                            potential = keptPotential,
                            valueM = valueM.toDoubleOrNull() ?: 1.0,
                            wageK = wageK.toIntOrNull() ?: 5,
                            nationality = nationality,
                            birthDate = birthDate,
                            height = height.toIntOrNull() ?: 180,
                            weight = weight.toIntOrNull() ?: 75,
                            preferredFoot = preferredFoot,
                            weakFoot = weakFoot.toIntOrNull() ?: 3,
                            headshotUrl = headshotUrl,
                            isRegen = isRegen,
                            regenOriginalName = savedOriginal,
                            isAcademy = if (isRetired) false else isAcademyPlayer,
                            isStartingXI = if (isRetired) false else player.isStartingXI,
                            status = status
                        ) ?: Player(
                            name = name.trim(),
                            nickname = savedNickname,
                            primaryPosition = primaryPosition,
                            secondaryPosition = secondaryPosition.takeIf { it.isNotBlank() },
                            tertiaryPosition = tertiaryPosition.takeIf { it.isNotBlank() },
                            age = age.toIntOrNull() ?: 21,
                            startingOverall = startingOverall.toIntOrNull() ?: 60,
                            overall = startingOverall.toIntOrNull() ?: 60,
                            potential = overall.toIntOrNull() ?: 60,
                            valueM = valueM.toDoubleOrNull() ?: 1.0,
                            wageK = wageK.toIntOrNull() ?: 5,
                            nationality = nationality,
                            birthDate = birthDate,
                            height = height.toIntOrNull() ?: 180,
                            weight = weight.toIntOrNull() ?: 75,
                            preferredFoot = preferredFoot,
                            weakFoot = weakFoot.toIntOrNull() ?: 3,
                            headshotUrl = headshotUrl,
                            isRegen = isRegen,
                            regenOriginalName = savedOriginal,
                            isAcademy = isAcademyPlayer,
                            status = status
                        )
                        onUpdate(newPlayer)
                        onDismiss()
                    }) { Text("Save") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(if(option.isEmpty()) "None" else option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
