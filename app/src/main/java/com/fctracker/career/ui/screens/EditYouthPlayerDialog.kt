package com.fctracker.career.ui.screens
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fctracker.career.Player

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditYouthPlayerDialog(startYear: Int = 2025, player: Player? = null, onDismiss: () -> Unit, onUpdate: (Player) -> Unit, onDelete: (() -> Unit)? = null) {
    var name by remember { mutableStateOf(player?.name ?: "") }
    var nickname by remember { mutableStateOf(player?.nickname ?: "") }
    var primaryPosition by remember { mutableStateOf(player?.primaryPosition ?: "CM") }
    var secondaryPosition by remember { mutableStateOf(player?.secondaryPosition ?: "") }
    var tertiaryPosition by remember { mutableStateOf(player?.tertiaryPosition ?: "") }
    var age by remember { mutableStateOf(player?.age?.toString() ?: "21") }
    
    var overall by remember { mutableStateOf(player?.overall?.toString() ?: "60") }
    var potentialRange by remember { mutableStateOf(player?.potentialRange ?: "70-90") }
    var valueM by remember { mutableStateOf(player?.valueM?.toString() ?: "1.0") }
    var wageK by remember { mutableStateOf(player?.wageK?.toString() ?: "5") }
    var nationality by remember { mutableStateOf(player?.nationality ?: "") }
    var birthDate by remember { mutableStateOf(player?.birthDate ?: "2000-01-01") }
    var height by remember { mutableStateOf(player?.height?.toString() ?: "180") }
    var weight by remember { mutableStateOf(player?.weight?.toString() ?: "75") }
    var preferredFoot by remember { mutableStateOf(player?.preferredFoot ?: "Right") }
    var weakFoot by remember { mutableStateOf(player?.weakFoot?.toString() ?: "3") }
    var headshotUrl by remember { mutableStateOf(player?.headshotUrl ?: "") }

    val positions = listOf("GK", "CB", "LB", "RB", "LWB", "RWB", "CDM", "CM", "CAM", "LM", "RM", "LW", "RW", "CF", "ST", "")
    val forms = listOf("Excellent", "Good", "Okay", "Poor")
    val morales = listOf("Happy", "Content", "Unhappy")
    val feet = listOf("Right", "Left", "Both")
    val weakFootOptions = listOf("1", "2", "3", "4", "5")
    LaunchedEffect(birthDate, startYear) {
        try {
            val parts = birthDate.split("-")
            if (parts.size >= 1) {
                val birthYear = parts[0].toInt()
                age = (startYear - birthYear).toString()
            }
        } catch(e: Exception) {}
    }


    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp).heightIn(max = 600.dp)) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (player == null) "Add Youth Prospect" else "Edit Youth Prospect", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name (Required)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("Nickname (Optional)") }, modifier = Modifier.fillMaxWidth())
                
                DropdownField("Position (Required)", primaryPosition, positions.filter { it.isNotEmpty() }) { primaryPosition = it }
                DropdownField("Secondary Position", secondaryPosition, positions) { secondaryPosition = it }
                DropdownField("Tertiary Position", tertiaryPosition, positions) { tertiaryPosition = it }


                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = overall, onValueChange = { overall = it }, label = { Text("Overall") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = potentialRange, onValueChange = { potentialRange = it }, label = { Text("Potential Range") }, modifier = Modifier.weight(1f))
                }
                

                
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = valueM, onValueChange = { valueM = it }, label = { Text("Market Value (€M)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = wageK, onValueChange = { wageK = it }, label = { Text("Weekly Wage (€K)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
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
                OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Age (Auto)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), readOnly = true, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant, disabledBorderColor = MaterialTheme.colorScheme.outline, disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant))

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
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    if (onDelete != null) {
                        Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Delete") }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Button(onClick = {
                        val newPlayer = player?.copy(
                            name = name,
                            nickname = nickname.takeIf { it.isNotBlank() },
                            primaryPosition = primaryPosition,
                            secondaryPosition = secondaryPosition.takeIf { it.isNotBlank() },
                            tertiaryPosition = tertiaryPosition.takeIf { it.isNotBlank() },
                            age = age.toIntOrNull() ?: 21,
                            startingOverall = overall.toIntOrNull() ?: 60,
                            overall = overall.toIntOrNull() ?: 60,
potential = 85, potentialRange = potentialRange.takeIf { it.isNotBlank() },
                            
                            valueM = valueM.toDoubleOrNull() ?: 1.0,
                            wageK = wageK.toIntOrNull() ?: 5,
                            nationality = nationality,
                            birthDate = birthDate,
                            height = height.toIntOrNull() ?: 180,
                            weight = weight.toIntOrNull() ?: 75,
                            preferredFoot = preferredFoot,
                            weakFoot = weakFoot.toIntOrNull() ?: 3,
                            headshotUrl = headshotUrl
                        ) ?: Player(
                            isAcademy = true,
                            name = name,
                            nickname = nickname.takeIf { it.isNotBlank() },
                            primaryPosition = primaryPosition,
                            secondaryPosition = secondaryPosition.takeIf { it.isNotBlank() },
                            tertiaryPosition = tertiaryPosition.takeIf { it.isNotBlank() },
                            age = age.toIntOrNull() ?: 21,
                            startingOverall = overall.toIntOrNull() ?: 60,
                            overall = overall.toIntOrNull() ?: 60,
potential = 85, potentialRange = potentialRange.takeIf { it.isNotBlank() },
                            
                            valueM = valueM.toDoubleOrNull() ?: 1.0,
                            wageK = wageK.toIntOrNull() ?: 5,
                            nationality = nationality,
                            birthDate = birthDate,
                            height = height.toIntOrNull() ?: 180,
                            weight = weight.toIntOrNull() ?: 75,
                            preferredFoot = preferredFoot,
                            weakFoot = weakFoot.toIntOrNull() ?: 3,
                            headshotUrl = headshotUrl
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
