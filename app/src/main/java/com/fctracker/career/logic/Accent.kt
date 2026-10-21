package com.fctracker.career.logic

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** The accent the app uses, where it came from, and why (shown under the colour pickers). */
data class AccentChoice(val hex: String, val why: String, val fromHex: String?)

/**
 * Club colours as the app's accent: the more colourful of the two club colours, lightened (keeping its hue) until it
 * reads on the dark background. Black, white and grey are skipped; if both colours are, the default mint stays.
 * Win, draw and loss colours never take the accent.
 */
object Accent {
    const val DEFAULT = "#62E0B0"
    const val BACKGROUND = "#0B0F0D"

    fun parse(hex: String?): Triple<Double, Double, Double>? {
        val h = hex?.trim()?.removePrefix("#") ?: return null
        val six = when (h.length) {
            3 -> h.map { "$it$it" }.joinToString("")
            6 -> h
            8 -> h.substring(2)
            else -> return null
        }
        val v = six.toLongOrNull(16) ?: return null
        return Triple(((v shr 16) and 0xFF) / 255.0, ((v shr 8) and 0xFF) / 255.0, (v and 0xFF) / 255.0)
    }

    fun hex(r: Double, g: Double, b: Double): String {
        fun c(x: Double) = (x.coerceIn(0.0, 1.0) * 255).roundToInt()
        return "#%02X%02X%02X".format(c(r), c(g), c(b))
    }

    private fun lin(c: Double) = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    fun luminance(hex: String): Double {
        val (r, g, b) = parse(hex) ?: return 0.0
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    fun contrast(a: String, b: String): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** RGB (0..1) to hue, lightness, saturation (0..1), the way Python's colorsys does it. */
    fun rgbToHls(r: Double, g: Double, b: Double): Triple<Double, Double, Double> {
        val maxc = max(r, max(g, b))
        val minc = min(r, min(g, b))
        val l = (minc + maxc) / 2.0
        if (minc == maxc) return Triple(0.0, l, 0.0)
        val s = if (l <= 0.5) (maxc - minc) / (maxc + minc) else (maxc - minc) / (2.0 - maxc - minc)
        val rc = (maxc - r) / (maxc - minc)
        val gc = (maxc - g) / (maxc - minc)
        val bc = (maxc - b) / (maxc - minc)
        var h = when {
            r == maxc -> bc - gc
            g == maxc -> 2.0 + rc - bc
            else -> 4.0 + gc - rc
        }
        h = (h / 6.0) % 1.0
        if (h < 0) h += 1.0
        return Triple(h, l, s)
    }

    fun hlsToRgb(h: Double, l: Double, s: Double): Triple<Double, Double, Double> {
        if (s == 0.0) return Triple(l, l, l)
        val m2 = if (l <= 0.5) l * (1.0 + s) else l + s - l * s
        val m1 = 2.0 * l - m2
        fun v(hue0: Double): Double {
            var hue = hue0 % 1.0
            if (hue < 0) hue += 1.0
            return when {
                hue < 1.0 / 6.0 -> m1 + (m2 - m1) * hue * 6.0
                hue < 0.5 -> m2
                hue < 2.0 / 3.0 -> m1 + (m2 - m1) * (2.0 / 3.0 - hue) * 6.0
                else -> m1
            }
        }
        return Triple(v(h + 1.0 / 3.0), v(h), v(h - 1.0 / 3.0))
    }

    fun choose(primary: String?, secondary: String?, background: String = BACKGROUND): AccentChoice {
        for (c in listOf(primary, secondary)) {
            val rgb = parse(c) ?: continue
            val (h, l0, s) = rgbToHls(rgb.first, rgb.second, rgb.third)
            if (s < 0.25 || l0 < 0.06 || l0 > 0.94) continue
            var l = l0
            var cur = hex(rgb.first, rgb.second, rgb.third)
            while (contrast(cur, background) < 4.5 && l < 0.95) {
                l += 0.02
                val (r, g, b) = hlsToRgb(h, l, s)
                cur = hex(r, g, b)
            }
            val original = hex(rgb.first, rgb.second, rgb.third)
            return AccentChoice(cur, if (cur.equals(original, ignoreCase = true)) "kept as it is" else "lightened to stay readable", original)
        }
        return AccentChoice(DEFAULT, "both colours are black, white or grey: the default mint is used", null)
    }

    /** Dark or white text on top of [hex], whichever reads better. */
    fun onColour(hex: String): String = if (contrast("#05140D", hex) >= contrast("#FFFFFF", hex)) "#05140D" else "#FFFFFF"
}
