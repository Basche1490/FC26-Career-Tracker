package com.fctracker.career.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fctracker.career.logic.Accent

/** The colours the design uses beyond Material's own scheme. Read with `FcTheme.colors`. */
@Immutable
data class FcColors(
    val accent: Color = FcMint,
    val onAccent: Color = FcOnMint,
    /** The accent at low strength, for selected chips, icon tiles and the highlighted table row. */
    val accentBg: Color = FcMint.copy(alpha = 0.14f),
    val accentLine: Color = FcMint.copy(alpha = 0.4f),
    /** The second club colour: section icons, charts, the menu's selected place. Never a result or money colour. */
    val accent2: Color = FcInfo,
    val accent2Bg: Color = FcInfo.copy(alpha = 0.14f),
    val bg: Color = FcBg,
    val surface: Color = FcSurface,
    val surface2: Color = FcSurface2,
    val inset: Color = FcInset,
    val border: Color = FcBorder,
    val text: Color = FcText,
    val muted: Color = FcMuted,
    val good: Color = FcGood,
    val goodBg: Color = FcGoodBg,
    val warn: Color = FcWarn,
    val warnBg: Color = FcWarnBg,
    val bad: Color = FcBad,
    val badBg: Color = FcBadBg,
    val info: Color = FcInfo,
    val infoBg: Color = FcInfoBg,
    val live: Color = FcLive,
    val liveBg: Color = FcLiveBg,
    val liveLine: Color = FcLiveLine,
    val navBg: Color = FcNavBg,
    val scrim: Color = FcScrim
)

val LocalFcColors = staticCompositionLocalOf { FcColors() }

object FcTheme {
    val colors: FcColors
        @Composable get() = LocalFcColors.current
}

fun parseHex(hex: String?): Color? = Accent.parse(hex)?.let { (r, g, b) -> Color(r.toFloat(), g.toFloat(), b.toFloat()) }

/** Kept for code that lightens a club colour by hand; the accent itself now comes from [Accent.choose]. */
fun adjustColorForDarkTheme(color: Color, @Suppress("UNUSED_PARAMETER") targetMinLightness: Float = 0.58f): Color {
    val hex = "#%02X%02X%02X".format((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
    return parseHex(Accent.choose(hex, null).hex) ?: color
}

private val FcShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * The app's theme. With [useClubColours] on, the accent is the more colourful club colour, lightened until it reads
 * on the dark background; otherwise (or when both club colours are black, white or grey) it is the default mint.
 */
@Composable
fun FCTheme(
    primaryHex: String? = null,
    secondaryHex: String? = null,
    useClubColours: Boolean = true,
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val accentHex = remember(primaryHex, secondaryHex, useClubColours) {
        if (useClubColours) Accent.choose(primaryHex, secondaryHex).hex else Accent.DEFAULT
    }
    val accent2Hex = remember(primaryHex, secondaryHex, useClubColours, accentHex) {
        if (useClubColours) Accent.chooseSecondary(primaryHex, secondaryHex, accentHex) else "#7CC4FF"
    }
    val accent = parseHex(accentHex) ?: FcMint
    val accent2 = parseHex(accent2Hex) ?: FcInfo
    val onAccent = parseHex(Accent.onColour(accentHex)) ?: FcOnMint
    val colors = FcColors(accent = accent, onAccent = onAccent, accentBg = accent.copy(alpha = 0.14f), accentLine = accent.copy(alpha = 0.4f),
        accent2 = accent2, accent2Bg = accent2.copy(alpha = 0.14f))

    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = colors.accentBg,
        onPrimaryContainer = accent,
        secondary = accent2,
        onSecondary = parseHex(Accent.onColour(accent2Hex)) ?: FcOnMint,
        secondaryContainer = FcSurface2,
        onSecondaryContainer = FcText,
        tertiary = FcInfo,
        onTertiary = FcBg,
        background = FcBg,
        onBackground = FcText,
        surface = FcSurface,
        onSurface = FcText,
        surfaceVariant = FcSurface2,
        onSurfaceVariant = FcMuted,
        surfaceContainerLowest = FcInset,
        surfaceContainerLow = FcSurface,
        surfaceContainer = FcSurface,
        surfaceContainerHigh = FcSurface2,
        surfaceContainerHighest = FcSurface2,
        inverseSurface = FcText,
        inverseOnSurface = FcBg,
        outline = FcBorder,
        outlineVariant = FcBorder,
        error = FcBad,
        onError = FcBg,
        errorContainer = FcBadBg,
        onErrorContainer = FcBad,
        scrim = FcScrim
    )

    CompositionLocalProvider(LocalFcColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = Typography, shapes = FcShapes, content = content)
    }
}
