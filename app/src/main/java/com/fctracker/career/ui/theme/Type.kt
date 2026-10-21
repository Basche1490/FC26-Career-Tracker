package com.fctracker.career.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fctracker.career.R

/** Outfit for everything people read; IBM Plex Mono for figures, so numbers line up. */
val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold)
)

val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.plex_mono_semibold, FontWeight.Bold)
)

private fun style(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) =
    TextStyle(fontFamily = Outfit, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)

val Typography = Typography(
    displayLarge = style(54, FontWeight.SemiBold, 58, -1.0),
    displayMedium = style(40, FontWeight.SemiBold, 44, -0.6),
    displaySmall = style(32, FontWeight.SemiBold, 36, -0.6),
    headlineLarge = style(28, FontWeight.SemiBold, 32, -0.3),
    headlineMedium = style(24, FontWeight.SemiBold, 30),
    headlineSmall = style(21, FontWeight.SemiBold, 27),
    titleLarge = style(20, FontWeight.SemiBold, 26),
    titleMedium = style(17, FontWeight.SemiBold, 22),
    titleSmall = style(15, FontWeight.SemiBold, 20),
    bodyLarge = style(16, FontWeight.Normal, 23),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(12, FontWeight.Normal, 17),
    labelLarge = style(15, FontWeight.SemiBold, 20),
    labelMedium = style(13, FontWeight.Medium, 18),
    labelSmall = style(11, FontWeight.Medium, 15, 0.2)
)
