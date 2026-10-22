package com.fctracker.career.ui.screens
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
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

@Composable
fun EditPlayerDialog(startYear: Int = 2025, player: Player?, allEafcPlayers: List<com.fctracker.career.EafcPlayer> = emptyList(), onDismiss: () -> Unit, onUpdate: (Player) -> Unit, onDelete: (() -> Unit)? = null, referenceDate: String? = null, console: Boolean = false) {
    val c = com.fctracker.career.ui.theme.FcTheme.colors
    var shirtNumber by remember { mutableStateOf(player?.number?.takeIf { it > 0 }?.toString() ?: "") }
    var squadRole by remember { mutableStateOf(player?.squadRole ?: "") }
    var contractEnd by remember { mutableStateOf(player?.contractEnd ?: "") }
    var loanDirection by remember { mutableStateOf(player?.loanDirection ?: "") }
    var loanClub by remember { mutableStateOf(player?.loanClub ?: "") }
    var loanEnd by remember { mutableStateOf(player?.loanEnd ?: "") }
    var loanOption by remember { mutableStateOf(player?.loanOptionToBuy ?: false) }
    var name by remember { mutableStateOf(player?.name ?: "") }
    var namePicked by remember { mutableStateOf(player != null) }
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
    LaunchedEffect(birthDate, startYear) {
        birthDate.take(4).toIntOrNull()?.let { age = (startYear - it).toString() }
    }
    var height by remember { mutableStateOf(player?.height?.toString() ?: "180") }
    var weight by remember { mutableStateOf(player?.weight?.toString() ?: "75") }
    var preferredFoot by remember { mutableStateOf(player?.preferredFoot ?: "Right") }
    var weakFoot by remember { mutableStateOf(player?.weakFoot?.toString() ?: "3") }
    var isRegen by remember { mutableStateOf(player?.isRegen ?: false) }
    var headshotUrl by remember { mutableStateOf(player?.headshotUrl ?: "") }
    var status by remember { mutableStateOf(player?.status ?: "Active") }
    // The original player a regen is based on, saved with the player by his short name; never the regen's own name.
    var regenSearchText by remember { mutableStateOf(com.fctracker.career.logic.PlayerNames.shown(player?.regenOriginalName)) }
    var pickedTemplate by remember { mutableStateOf<com.fctracker.career.EafcPlayer?>(null) }
    var regenListOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val positions = listOf("GK", "CB", "LB", "RB", "LWB", "RWB", "CDM", "CM", "CAM", "LM", "RM", "LW", "RW", "CF", "ST")
    fun fillFrom(p: com.fctracker.career.EafcPlayer, wholeCard: Boolean) {
        if (wholeCard) {
            nickname = p.commonName
            nationality = p.nationality
            primaryPosition = p.position
            val alt = p.alternatePositions.split(",")
            secondaryPosition = alt.getOrNull(0)?.trim() ?: ""
            tertiaryPosition = alt.getOrNull(1)?.trim() ?: ""
            val iso = com.fctracker.career.data.PlayerDatabase.isoBirthDate(p.birthdate)
            if (com.fctracker.career.logic.Dates.isDate(iso)) birthDate = iso
            preferredFoot = p.preferredFoot
            weakFoot = p.weakFoot.toString()
            height = p.height.toString()
            weight = p.weight.toString()
            overall = p.rating.toString()
            startingOverall = p.rating.toString()
            if (p.value > 0) valueM = p.value.toString()
            if (p.wage > 0) wageK = p.wage.toString()
        }
        headshotUrl = com.fctracker.career.data.PlayerDatabase.headshotUrl(p.id)
    }

    fun save() {
        val isAcademyPlayer = if (player != null) {
            status.equals("Academy", ignoreCase = true) || (player.isAcademy && !status.equals("Active", ignoreCase = true) && !status.equals("Sold", ignoreCase = true) && !status.equals("Retired", ignoreCase = true))
        } else status.equals("Academy", ignoreCase = true)
        val isRetired = status.equals("Retired", ignoreCase = true)
        // An existing player keeps his current rating and potential unless "Starting OVR" was edited (the only way to
        // correct the rating here). Saving must not reset them.
        val startingEdited = player != null && startingOverall != player.startingOverall.toString()
        val keptOverall = if (player == null || startingEdited) startingOverall.toIntOrNull() ?: 60 else player.overall
        val keptPotential = if (player == null) overall.toIntOrNull() ?: 60 else maxOf(player.potential, keptOverall)
        val savedNickname = nicknameToKeep(isRegen, name, nickname, player, pickedTemplate, allEafcPlayers)
        // the real player by his short name: a full name typed by hand is shortened from the database
        val savedOriginal = if (isRegen) regenSearchText.trim().takeIf { it.isNotEmpty() }?.let { typed ->
            val key = com.fctracker.career.logic.PlayerCards.normalize(typed)
            allEafcPlayers.firstOrNull { com.fctracker.career.logic.PlayerCards.normalize(it.name) == key && it.commonName.isNotBlank() }?.commonName?.trim()
                ?: com.fctracker.career.logic.PlayerNames.shown(typed)
        } else null
        val newPlayer = player?.copy(
            name = name.trim(), nickname = savedNickname,
            // a display name carried over from an old backup beats both name fields; once either was edited here it
            // must not keep hiding the edit
            customDisplayName = if (name.trim() != player.name.trim() || savedNickname != player.nickname) null else player.customDisplayName,
            primaryPosition = primaryPosition, secondaryPosition = secondaryPosition.takeIf { it.isNotBlank() }, tertiaryPosition = tertiaryPosition.takeIf { it.isNotBlank() },
            age = age.toIntOrNull() ?: 21, startingOverall = startingOverall.toIntOrNull() ?: 60, overall = keptOverall, potential = keptPotential,
            valueM = valueM.toDoubleOrNull() ?: 1.0, wageK = wageK.toIntOrNull() ?: 5, nationality = nationality, birthDate = birthDate,
            height = height.toIntOrNull() ?: 180, weight = weight.toIntOrNull() ?: 75, preferredFoot = preferredFoot, weakFoot = weakFoot.toIntOrNull() ?: 3,
            headshotUrl = headshotUrl, isRegen = isRegen, regenOriginalName = savedOriginal,
            isAcademy = if (isRetired) false else isAcademyPlayer, isStartingXI = if (isRetired) false else player.isStartingXI, status = status,
            number = shirtNumber.toIntOrNull() ?: 0, squadRole = squadRole.ifBlank { null },
            contractEnd = if (console) contractEnd.ifBlank { null } else player.contractEnd,
            loanDirection = if (console) loanDirection.ifBlank { null } else player.loanDirection,
            loanClub = if (console) loanClub.ifBlank { null } else player.loanClub,
            loanEnd = if (console) loanEnd.ifBlank { null } else player.loanEnd,
            loanOptionToBuy = if (console) loanOption else player.loanOptionToBuy
        ) ?: Player(
            name = name.trim(), nickname = savedNickname,
            primaryPosition = primaryPosition, secondaryPosition = secondaryPosition.takeIf { it.isNotBlank() }, tertiaryPosition = tertiaryPosition.takeIf { it.isNotBlank() },
            age = age.toIntOrNull() ?: 21, startingOverall = startingOverall.toIntOrNull() ?: 60, overall = startingOverall.toIntOrNull() ?: 60, potential = overall.toIntOrNull() ?: 60,
            valueM = valueM.toDoubleOrNull() ?: 1.0, wageK = wageK.toIntOrNull() ?: 5, nationality = nationality, birthDate = birthDate,
            height = height.toIntOrNull() ?: 180, weight = weight.toIntOrNull() ?: 75, preferredFoot = preferredFoot, weakFoot = weakFoot.toIntOrNull() ?: 3,
            headshotUrl = headshotUrl, isRegen = isRegen, regenOriginalName = savedOriginal, isAcademy = isAcademyPlayer, status = status,
            number = shirtNumber.toIntOrNull() ?: 0, squadRole = squadRole.ifBlank { null }, contractEnd = contractEnd.ifBlank { null },
            loanDirection = loanDirection.ifBlank { null }, loanClub = loanClub.ifBlank { null }, loanEnd = loanEnd.ifBlank { null }, loanOptionToBuy = loanOption
        )
        onUpdate(newPlayer)
        onDismiss()
    }

    com.fctracker.career.ui.components.FcFullScreen(onDismiss = onDismiss) {
        com.fctracker.career.ui.components.FcPage(header = {
            com.fctracker.career.ui.components.FcTopBar(if (player == null) "Add player" else "Edit player", nav = com.fctracker.career.ui.components.NavIcon.CLOSE, onNav = onDismiss)
        }) {
            // -- who he is -------------------------------------------------------------------------------------------
            item {
                com.fctracker.career.ui.components.FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.fctracker.career.ui.components.Headshot(headshotUrl.takeIf { isValidPlayerHeadshot(it) }, name.ifBlank { "?" }, 64.dp)
                        Spacer(Modifier.weight(1f))
                        com.fctracker.career.ui.components.OvrBadge((if (player == null) startingOverall else overall).toIntOrNull() ?: 0, 52.dp)
                    }
                    com.fctracker.career.ui.components.FcField("Full name (required)", name, { name = it; namePicked = false },
                        hint = if (isRegen) "A regen’s name is his own, so it is not looked up in the player database." else "Type to search the player database.")
                    if (!isRegen && !namePicked && name.trim().length >= 2) {
                        val found = searchEafcPlayers(name, allEafcPlayers).take(5)
                        if (found.isNotEmpty()) EafcPickList(found) { p -> name = p.name; namePicked = true; fillFrom(p, wholeCard = true) }
                    }
                    com.fctracker.career.ui.components.FcField("Nickname (optional)", nickname, { nickname = it }, hint = "The app shows the nickname instead of the full name. Clear it to show the full name.")
                    com.fctracker.career.ui.components.FcSwitchRow("Regen", isRegen, { isRegen = it }, sub = "A player the game generated")
                    if (isRegen) {
                        com.fctracker.career.ui.components.FcField("Original player name", regenSearchText, { regenSearchText = it; regenListOpen = true; pickedTemplate = null },
                            hint = "The real player this regen is based on. Pick him from the list: he lends the headshot, and the full name above stays the regen’s own.",
                            trailing = { Icon(androidx.compose.material.icons.Icons.Default.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp)) })
                        if (regenListOpen && regenSearchText.trim().length >= 2) {
                            val found = searchEafcPlayers(regenSearchText, allEafcPlayers).take(4)
                            if (found.isNotEmpty()) EafcPickList(found) { p ->
                                // saved by his short name: "Sergio Busquets", not "Sergio Busquets Burgos"
                                regenSearchText = p.commonName.ifBlank { p.name }
                                regenListOpen = false
                                pickedTemplate = p
                                // a new player takes the whole card from the template; an existing one only the picture
                                fillFrom(p, wholeCard = player == null)
                                if (player == null && name.isBlank()) name = p.name
                            }
                        }
                    }
                }
            }
            // -- positions and status --------------------------------------------------------------------------------
            item {
                com.fctracker.career.ui.components.FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                    com.fctracker.career.ui.components.SectionHeader("Positions & status", icon = androidx.compose.material.icons.Icons.Default.Person)
                    com.fctracker.career.ui.components.FieldPair(
                        { m -> com.fctracker.career.ui.components.FcSelectField("Position (required)", primaryPosition, positions, { primaryPosition = it }, m) },
                        { m -> com.fctracker.career.ui.components.FcSelectField("Secondary", secondaryPosition.ifBlank { "None" }, listOf("None") + positions, { secondaryPosition = if (it == "None") "" else it }, m) }
                    )
                    com.fctracker.career.ui.components.FieldPair(
                        { m -> com.fctracker.career.ui.components.FcSelectField("Tertiary", tertiaryPosition.ifBlank { "None" }, listOf("None") + positions, { tertiaryPosition = if (it == "None") "" else it }, m) },
                        { m ->
                            if (player != null) com.fctracker.career.ui.components.FcSelectField("Status", status, listOf("Active", "Sold", "Loaned Out", "Loaned In", "Injured", "Academy", "Retired"), { status = it }, m)
                            else com.fctracker.career.ui.components.FcNumberField("Shirt number", shirtNumber, { shirtNumber = it.take(2) }, m)
                        }
                    )
                    com.fctracker.career.ui.components.FieldPair(
                        { m -> com.fctracker.career.ui.components.FcNumberField("Market value (€M)", valueM, { valueM = it }, m, decimal = true) },
                        { m -> com.fctracker.career.ui.components.FcNumberField("Weekly wage (€K)", wageK, { wageK = it }, m) }
                    )
                    com.fctracker.career.ui.components.FieldPair(
                        { m ->
                            if (player != null) com.fctracker.career.ui.components.FcNumberField("Shirt number", shirtNumber, { shirtNumber = it.take(2) }, m)
                            else Spacer(m)
                        },
                        { m -> com.fctracker.career.ui.components.FcSelectField("Squad role", squadRole.ifBlank { "Not set" }, listOf("Not set", "Crucial", "Important", "Rotation", "Sporadic", "Prospect"), { squadRole = if (it == "Not set") "" else it }, m) }
                    )
                    com.fctracker.career.ui.components.Muted("On PC the shirt number, squad role and wage follow the game; you can still change them here.", 11.sp)
                }
            }
            // -- contract and loan -----------------------------------------------------------------------------------
            item {
                com.fctracker.career.ui.components.FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                    com.fctracker.career.ui.components.FcLabel("FROM THE GAME")
                    if (console) {
                        com.fctracker.career.ui.components.FcDateField("Contract ends", contractEnd.ifBlank { null }, { contractEnd = it })
                        com.fctracker.career.ui.components.FcSelectField("On loan", when (loanDirection) { "in" -> "Loaned in (to us)"; "out" -> "Loaned out (from us)"; else -> "Not on loan" },
                            listOf("Not on loan", "Loaned in (to us)", "Loaned out (from us)"), { loanDirection = when (it) { "Loaned in (to us)" -> "in"; "Loaned out (from us)" -> "out"; else -> "" } })
                        if (loanDirection.isNotBlank()) {
                            com.fctracker.career.ui.components.FcField(if (loanDirection == "in") "On loan from" else "On loan to", loanClub, { loanClub = it })
                            com.fctracker.career.ui.components.FcDateField("Loan ends", loanEnd.ifBlank { null }, { loanEnd = it })
                            com.fctracker.career.ui.components.FcCheckRow("Option to buy", loanOption, { loanOption = it })
                        }
                        com.fctracker.career.ui.components.Muted("On console these fields are open for you to fill in. On PC the capture script reads them from your career.", 11.sp)
                    } else {
                        val lock = androidx.compose.material.icons.Icons.Default.Lock
                        val chip = player?.let { com.fctracker.career.telemetry.ContractTime.contractChip(it, referenceDate) }
                        val contractText = player?.contractEnd?.let { com.fctracker.career.telemetry.ContractTime.monthYear(it) + (chip?.let { ch -> " · ${leftText(ch)}" } ?: "") } ?: "–"
                        com.fctracker.career.ui.components.FcPickerBox("Contract ends", contractText, lock, {}, muted = true)
                        val loanText = when (player?.loanDirection) {
                            "in" -> "From ${player.loanClub.orEmpty().ifBlank { "another club" }}"
                            "out" -> "To ${player.loanClub.orEmpty().ifBlank { "another club" }}"
                            else -> "Not on loan"
                        }
                        com.fctracker.career.ui.components.FcPickerBox("On loan to / from", loanText, lock, {}, muted = true)
                        com.fctracker.career.ui.components.FieldPair(
                            { m -> com.fctracker.career.ui.components.FcPickerBox("Loan ends", player?.loanEnd?.let { com.fctracker.career.logic.Dates.full(it) } ?: "–", lock, {}, m, muted = true) },
                            { m -> com.fctracker.career.ui.components.FcPickerBox("Option to buy", if (player?.loanDirection == null) "–" else if (player.loanOptionToBuy) "Yes" else "No", lock, {}, m, muted = true) }
                        )
                        com.fctracker.career.ui.components.Muted("On PC this is read from your career by the capture script and kept up to date by it. On console these fields are open for you to fill in.", 11.sp)
                    }
                }
            }
            // -- details -----------------------------------------------------------------------------------------------
            item {
                com.fctracker.career.ui.components.FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                    com.fctracker.career.ui.components.SectionHeader("Details", icon = androidx.compose.material.icons.Icons.Default.Person)
                    com.fctracker.career.ui.components.FcField("Nationality", nationality, { nationality = it })
                    com.fctracker.career.ui.components.FieldPair(
                        { m -> com.fctracker.career.ui.components.FcDateField("Birth date", birthDate, { birthDate = it }, m) },
                        { m -> com.fctracker.career.ui.components.FcPickerBox("Age (auto)", age, androidx.compose.material.icons.Icons.Default.Lock, {}, m, muted = true) }
                    )
                    com.fctracker.career.ui.components.FieldPair(
                        { m -> com.fctracker.career.ui.components.FcNumberField("Starting OVR", startingOverall, { startingOverall = it.take(2) }, m) },
                        { m -> com.fctracker.career.ui.components.FcNumberField("Height (cm)", height, { height = it.take(3) }, m) }
                    )
                    com.fctracker.career.ui.components.FieldPair(
                        { m -> com.fctracker.career.ui.components.FcNumberField("Weight (kg)", weight, { weight = it.take(3) }, m) },
                        { m -> com.fctracker.career.ui.components.FcSelectField("Preferred foot", preferredFoot, listOf("Right", "Left", "Both"), { preferredFoot = it }, m) }
                    )
                    com.fctracker.career.ui.components.FcSelectField("Weak foot (1–5)", weakFoot, listOf("1", "2", "3", "4", "5"), { weakFoot = it })
                    com.fctracker.career.ui.components.FcField("Headshot link", headshotUrl, { headshotUrl = it }, placeholder = "https://…", hint = "Filled in from the player database (or the regen’s original player); paste another picture link to change it.")
                }
            }
            item { com.fctracker.career.ui.components.FcButton("Save", { save() }, icon = androidx.compose.material.icons.Icons.Default.Save, full = true, enabled = name.isNotBlank()) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (player != null && player.isPromoted && !player.isAcademy) {
                        com.fctracker.career.ui.components.FcButton("Move to academy", {
                            onUpdate(player.copy(isAcademy = true, isPromoted = false, status = "Academy", isStartingXI = false)); onDismiss()
                        }, Modifier.weight(1f), kind = com.fctracker.career.ui.components.ButtonKind.SECONDARY)
                    }
                    if (onDelete != null) com.fctracker.career.ui.components.FcButton("Delete", { confirmDelete = true }, Modifier.weight(1f), kind = com.fctracker.career.ui.components.ButtonKind.DANGER)
                    if ((player == null || !(player.isPromoted && !player.isAcademy)) && onDelete == null) com.fctracker.career.ui.components.FcButton("Cancel", onDismiss, Modifier.weight(1f), kind = com.fctracker.career.ui.components.ButtonKind.SECONDARY)
                }
            }
        }
        if (confirmDelete && onDelete != null) {
            com.fctracker.career.ui.components.ConfirmDialog("Delete ${name.ifBlank { "this player" }}?", "He is removed from the squad. This cannot be undone.", "Delete", { onDelete() }, { confirmDelete = false })
        }
    }
}

/** Database players to pick from, with headshot, position, rating and club. */
@Composable
private fun EafcPickList(players: List<com.fctracker.career.EafcPlayer>, onPick: (com.fctracker.career.EafcPlayer) -> Unit) {
    val c = com.fctracker.career.ui.theme.FcTheme.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, c.border, RoundedCornerShape(18.dp))) {
        players.forEachIndexed { i, p ->
            if (i > 0) com.fctracker.career.ui.components.HRule()
            Row(
                Modifier.fillMaxWidth().clickable { onPick(p) }.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                com.fctracker.career.ui.components.Headshot(com.fctracker.career.data.PlayerDatabase.headshotUrl(p.id), p.commonName.ifBlank { p.name }, 36.dp)
                Column(Modifier.weight(1f)) {
                    com.fctracker.career.ui.components.FcText(p.commonName.ifBlank { p.name }, 15.sp, FontWeight.SemiBold, maxLines = 1)
                    com.fctracker.career.ui.components.Muted("${p.position} · OVR ${p.rating}${if (p.team.isNotBlank()) " · ${p.team}" else ""}", 12.sp, maxLines = 1)
                }
            }
        }
    }
}
