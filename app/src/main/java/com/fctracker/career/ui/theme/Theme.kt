package com.fctracker.career.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark
)

// We'll map light colors to the dark scheme to enforce the "Elegant Dark" design.
private val LightColorScheme = DarkColorScheme

/**
 * Ensures primary and secondary team colors maintain optimal visual contrast, clarity, and
 * vibrancy against dark surfaces, preventing muddy or invisible UI elements while faithfully
 * preserving the club's distinctive hue and tonal identity.
 */
fun adjustColorForDarkTheme(color: Color, targetMinLightness: Float = 0.58f): Color {
    val colorInt = android.graphics.Color.argb(
        (color.alpha * 255).toInt().coerceIn(0, 255),
        (color.red * 255).toInt().coerceIn(0, 255),
        (color.green * 255).toInt().coerceIn(0, 255),
        (color.blue * 255).toInt().coerceIn(0, 255)
    )
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(colorInt, hsl)

    // hsl[0] is Hue (0-360), hsl[1] is Saturation (0-1), hsl[2] is Lightness (0-1)
    // If the color has low perceived luminance or low lightness (e.g., deep navy #132A75 or dark maroon #961F30),
    // proportionally elevate its lightness so it stands out with crisp, premium clarity.
    if (color.luminance() < 0.22f || hsl[2] < targetMinLightness) {
        val boostedLightness = (targetMinLightness + (hsl[2] * 0.18f)).coerceIn(0.52f, 0.78f)
        hsl[2] = boostedLightness
        if (hsl[1] < 0.45f) {
            hsl[1] = 0.45f // Keep rich, vibrant color saturation
        }
    }
    return Color(ColorUtils.HSLToColor(hsl))
}

@Composable
fun FCTheme(
    primaryHex: String? = null,
    secondaryHex: String? = null,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    var customPrimary = PrimaryDark
    var customSecondary = Color(0xFF03DAC6)
    
    try {
        if (!primaryHex.isNullOrEmpty()) {
            val parsed = Color(android.graphics.Color.parseColor(primaryHex))
            customPrimary = adjustColorForDarkTheme(parsed, targetMinLightness = 0.58f)
        }
        if (!secondaryHex.isNullOrEmpty()) {
            val parsed = Color(android.graphics.Color.parseColor(secondaryHex))
            customSecondary = adjustColorForDarkTheme(parsed, targetMinLightness = 0.52f)
        }
    } catch(e: Exception) {}

    val colorScheme = darkColorScheme(
        primary = customPrimary,
        onPrimary = if (customPrimary.luminance() > 0.45f) Color.Black else Color.White,
        secondary = customSecondary,
        onSecondary = if (customSecondary.luminance() > 0.45f) Color.Black else Color.White,
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnBackgroundDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        outline = OutlineDark
    )

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}