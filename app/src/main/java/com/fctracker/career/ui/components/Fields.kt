package com.fctracker.career.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fctracker.career.logic.Dates
import com.fctracker.career.ui.theme.FcTheme
import com.fctracker.career.ui.theme.Outfit

/** A text field in the design's filled style: the label above, a rounded box, an optional hint under it. */
@Composable
fun FcField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    readOnly: Boolean = false,
    password: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    error: String? = null
) {
    val c = FcTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotEmpty()) FcText(label, 13.sp, FontWeight.Medium, c.muted, Modifier.padding(start = 4.dp))
        val shape = RoundedCornerShape(14.dp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = TextStyle(fontFamily = Outfit, fontSize = 15.sp, color = if (readOnly) c.muted else c.text),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(shape).background(c.surface2)
                        .border(1.dp, if (error != null) c.bad else Color.Transparent, shape).padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (leadingIcon != null) Icon(leadingIcon, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) FcText(placeholder, 15.sp, color = c.muted, maxLines = if (singleLine) 1 else Int.MAX_VALUE)
                        inner()
                    }
                    trailing?.invoke()
                }
            }
        )
        if (error != null) FcText(error, 11.sp, color = c.bad)
        else if (hint != null) Muted(hint, 11.sp, Modifier.padding(start = 4.dp))
    }
}

/** A number field that keeps only digits (and one decimal point when [decimal]). */
@Composable
fun FcNumberField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, decimal: Boolean = false, hint: String? = null, placeholder: String? = null) {
    FcField(
        label, value,
        { raw ->
            val cleaned = if (decimal) raw.replace(',', '.').filter { it.isDigit() || it == '.' }.let { s -> val i = s.indexOf('.'); if (i < 0) s else s.substring(0, i + 1) + s.substring(i + 1).replace(".", "") }
            else raw.filter { it.isDigit() }
            onValueChange(cleaned.take(9))
        },
        modifier, hint = hint, placeholder = placeholder, keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
    )
}

/** A box that opens a choice list. */
@Composable
fun FcSelectField(label: String, value: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier, hint: String? = null, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    FcPickerBox(label, value.ifBlank { "Choose…" }, Icons.Default.KeyboardArrowDown, { if (enabled) open = true }, modifier, hint, muted = value.isBlank() || !enabled)
    if (open) {
        FcDialog(title = label, onDismiss = { open = false }) {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options) { o ->
                    val on = o == value
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) FcTheme.colors.accentBg else Color.Transparent)
                            .clickable { onSelect(o); open = false }.padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FcText(o, 15.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) FcTheme.colors.accent else FcTheme.colors.text, Modifier.weight(1f))
                        if (on) Icon(Icons.Default.Check, contentDescription = null, tint = FcTheme.colors.accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FcPickerBox(label: String, value: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, hint: String? = null, muted: Boolean = false) {
    val c = FcTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotEmpty()) FcText(label, 13.sp, FontWeight.Medium, c.muted, Modifier.padding(start = 4.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2).clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "$label: $value" }.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FcText(value, 15.sp, color = if (muted) c.muted else c.text, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(icon, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
        }
        if (hint != null) Muted(hint, 11.sp, Modifier.padding(start = 4.dp))
    }
}

/** A date box (yyyy-MM-dd inside, "12 Oct 2026" on screen) with the calendar picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FcDateField(label: String, iso: String?, onChange: (String) -> Unit, modifier: Modifier = Modifier, hint: String? = null) {
    var open by remember { mutableStateOf(false) }
    FcPickerBox(label, if (Dates.isDate(iso)) Dates.full(iso) else "Choose a date", Icons.Default.CalendarMonth, { open = true }, modifier, hint, muted = !Dates.isDate(iso))
    if (open) {
        val initial = Dates.toEpochDay(iso)?.let { it * 86_400_000L }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { onChange(Dates.fromEpochDay(Math.floorDiv(it, 86_400_000L))) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        ) { DatePicker(state = picker) }
    }
}

/** A switch with its title and an explanation, the design's 52 by 30 track. */
@Composable
fun FcSwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, sub: String? = null, enabled: Boolean = true) {
    val c = FcTheme.colors
    Row(modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FcText(title, 14.sp, FontWeight.SemiBold, if (enabled) c.text else c.muted)
            if (sub != null) Muted(sub)
        }
        val shape = RoundedCornerShape(15.dp)
        Box(
            Modifier.width(52.dp).heightIn(min = 30.dp).clip(shape).background(if (checked) c.accent else c.surface2).border(1.dp, if (checked) c.accent else c.border, shape)
                .semantics { contentDescription = title },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.offset(x = if (checked) 24.dp else 3.dp).size(22.dp).clip(CircleShape).background(if (checked) c.onAccent else c.muted))
        }
    }
}

/** A checkbox row. */
@Composable
fun FcCheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, sub: String? = null) {
    val c = FcTheme.colors
    Row(modifier.heightIn(min = 44.dp).clickable(role = Role.Checkbox) { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (checked) c.accent else Color.Transparent).border(1.5.dp, if (checked) c.accent else c.muted, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) { if (checked) Icon(Icons.Default.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(16.dp)) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FcText(label, 14.sp, FontWeight.Medium)
            if (sub != null) Muted(sub)
        }
    }
}

/** A stepper: minus, the number, plus (the console table editor). */
@Composable
fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, min: Int = 0, max: Int = 999) {
    val c = FcTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Muted(label, 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StepButton("−", "Less $label") { if (value > min) onChange(value - 1) }
            Mono("$value", 16.sp, c.text, FontWeight.SemiBold, Modifier.width(28.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
            StepButton("+", "More $label") { if (value < max) onChange(value + 1) }
        }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    val c = FcTheme.colors
    Box(Modifier.size(36.dp).clip(CircleShape).background(c.surface2).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        FcText(symbol, 18.sp, FontWeight.SemiBold)
    }
}

/**
 * The design's dialog: a rounded surface with a title, a scrolling body and the actions at the bottom right.
 */
@Composable
fun FcDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = FcTheme.colors
    // The dialog draws edge to edge (Android 15 and newer always do): its own padding keeps the buttons clear of the
    // phone's navigation bar and the keyboard.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            modifier.fillMaxWidth().systemBarsPadding().imePadding().padding(horizontal = 14.dp, vertical = 24.dp).clip(RoundedCornerShape(28.dp)).background(c.surface)
                .border(1.dp, c.border, RoundedCornerShape(28.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FcText(title, 18.sp, FontWeight.SemiBold)
            Column(
                Modifier.weight(1f, fill = false).let { if (scroll) it.verticalScroll(rememberScrollState()) else it },
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
            if (actions != null) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}

/** "Delete X?" with a red confirm button. */
@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = true) {
    FcDialog(title, onDismiss, actions = {
        FcButton("Cancel", onDismiss, kind = ButtonKind.SECONDARY, small = true)
        FcButton(confirm, { onConfirm(); onDismiss() }, kind = if (danger) ButtonKind.DANGER else ButtonKind.PRIMARY, small = true)
    }) { Muted(message, 14.sp) }
}

/** A sheet that slides up from the bottom (the archived season picker). */
@Composable
fun FcBottomSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = FcTheme.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().statusBarsPadding().imePadding(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.surface).navigationBarsPadding().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).heightIn(min = 4.dp).clip(RoundedCornerShape(2.dp)).background(c.border))
                FcText(title, 18.sp, FontWeight.SemiBold)
                content()
            }
        }
    }
}

/** Fixed-width spacer between two fields in a row. */
@Composable
fun FieldPair(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit, modifier: Modifier = Modifier, gap: Dp = 10.dp) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        first(Modifier.weight(1f))
        second(Modifier.weight(1f))
    }
}
