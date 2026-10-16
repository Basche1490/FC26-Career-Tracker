package com.fctracker.career.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun ColorWheel(
    modifier: Modifier = Modifier,
    onColorSelected: (Color) -> Unit
) {
    val colors = listOf(
        Color.Red, Color.Magenta, Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Red
    )
    
    var currentOffset by remember { mutableStateOf(Offset.Zero) }
    var radius by remember { mutableStateOf(0f) }
    var center by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    currentOffset = offset
                    updateColor(offset, center, radius, onColorSelected)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    currentOffset = change.position
                    updateColor(change.position, center, radius, onColorSelected)
                }
            }
    ) {
        radius = size.minDimension / 2f
        center = Offset(size.width / 2, size.height / 2)
        
        val brush = Brush.sweepGradient(colors, center = center)
        drawCircle(
            brush = brush,
            radius = radius,
            center = center
        )
    }
}

private fun updateColor(offset: Offset, center: Offset, radius: Float, onColorSelected: (Color) -> Unit) {
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    val distance = sqrt(dx * dx + dy * dy)
    if (distance <= radius) {
        var angle = atan2(dy, dx)
        if (angle < 0) angle += (2 * PI).toFloat()
        
        val hue = (angle / (2 * PI) * 360f).toFloat()
        val hsv = floatArrayOf(hue, distance / radius, 1f)
        val colorInt = android.graphics.Color.HSVToColor(hsv)
        onColorSelected(Color(colorInt))
    }
}

fun Color.toHexCode(): String {
    val r = (this.red * 255).toInt()
    val g = (this.green * 255).toInt()
    val b = (this.blue * 255).toInt()
    return String.format("#%02X%02X%02X", r, g, b)
}
