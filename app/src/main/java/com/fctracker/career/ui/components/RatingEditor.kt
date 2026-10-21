package com.fctracker.career.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.ui.theme.FcTheme
import com.fctracker.career.ui.theme.PlexMono

private fun fmt(v: Double) = String.format(java.util.Locale.US, "%.1f", v)

/**
 * The match rating: a slider that takes the colour of the rating (red below 7, amber to 7.9, green from 8) and a box
 * to type the number into. Moving one moves the other.
 */
@Composable
fun RatingEditor(value: Double, onChange: (Double) -> Unit, modifier: Modifier = Modifier) {
    val c = FcTheme.colors
    val v = value.coerceIn(1.0, 10.0)
    val (fg, _) = toneColors(ratingTone(v))
    var text by remember { mutableStateOf(fmt(v)) }
    LaunchedEffect(v) { if (text.replace(',', '.').toDoubleOrNull() != v) text = fmt(v) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FcText("Rating", 14.sp, FontWeight.SemiBold)
                Muted("Drag the slider or type the number: the other follows.", 11.sp)
            }
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    val cleaned = raw.replace(',', '.').filter { it.isDigit() || it == '.' }.take(4)
                    text = cleaned
                    cleaned.toDoubleOrNull()?.takeIf { it in 1.0..10.0 }?.let { onChange((it * 10).toInt() / 10.0) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(fontFamily = PlexMono, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = fg, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(fg),
                modifier = Modifier.width(68.dp).height(44.dp).clip(RoundedCornerShape(12.dp)).background(c.surface2).border(2.dp, fg, RoundedCornerShape(12.dp))
                    .semantics { contentDescription = "Rating, 1.0 to 10.0" },
                decorationBox = { inner -> Box(contentAlignment = Alignment.Center) { inner() } }
            )
        }
        Slider(
            value = v.toFloat(),
            onValueChange = { onChange((it * 10).toInt() / 10.0) },
            valueRange = 1f..10f,
            colors = SliderDefaults.colors(thumbColor = fg, activeTrackColor = fg, inactiveTrackColor = c.surface2, activeTickColor = fg, inactiveTickColor = c.surface2)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(Tone.BAD to "Below 7", Tone.WARN to "7 to 7.9", Tone.GOOD to "8 and up").forEach { (t, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(toneColors(t).first))
                    Muted(label, 11.sp)
                }
            }
        }
    }
}
