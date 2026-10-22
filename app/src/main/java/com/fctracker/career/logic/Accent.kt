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

    /**
     * The colours that carry a meaning: win (and money coming in), draw, loss (and money going out). An accent never
     * sits within [CLASH_DEGREES] of their hues, so a highlight is never mistaken for a result or a fee.
     */
    val RESERVED = listOf("win" to "#7EE0A4", "draw" to "#FFC857", "loss" to "#FF8F86")
    const val CLASH_DEGREES = 28.0

    fun hueDegrees(colour: String): Double? {
        val (r, g, b) = parse(colour) ?: return null
        val (h, _, s) = rgbToHls(r, g, b)
        return if (s < 0.2) null else h * 360.0
    }

    private fun gap(a: Double, b: Double): Double { val d = Math.abs(a - b) % 360.0; return if (d > 180.0) 360.0 - d else d }
    private fun reservedHues() = RESERVED.mapNotNull { (name, hex) -> hueDegrees(hex)?.let { name to it } }

    /** The reserved colour this one could be mistaken for, or null. */
    fun clashesWith(colour: String): String? {
        val h = hueDegrees(colour) ?: return null
        return reservedHues().filter { gap(h, it.second) < CLASH_DEGREES }.minByOrNull { gap(h, it.second) }?.first
    }

    /** The colour turned just far enough round the colour wheel to keep clear of every reserved hue. */
    fun awayFromReserved(colour: String, background: String = BACKGROUND): String {
        val (r0, g0, b0) = parse(colour) ?: return colour
        val (h0, l0, s) = rgbToHls(r0, g0, b0)
        if (s < 0.2) return colour
        val hue = h0 * 360.0
        val reserved = reservedHues().map { it.second }
        if (reserved.all { gap(hue, it) >= CLASH_DEGREES }) return colour
        val candidates = reserved.flatMap { listOf(it - CLASH_DEGREES - 1, it + CLASH_DEGREES + 1) }.map { ((it % 360.0) + 360.0) % 360.0 }
        val best = candidates.filter { c -> reserved.all { gap(c, it) >= CLASH_DEGREES } }.minByOrNull { gap(it, hue) } ?: return colour
        var l = l0
        var (r, g, b) = hlsToRgb(best / 360.0, l, s)
        var cur = hex(r, g, b)
        while (contrast(cur, background) < 4.5 && l < 0.95) {
            l += 0.02
            val t = hlsToRgb(best / 360.0, l, s); r = t.first; g = t.second; b = t.third
            cur = hex(r, g, b)
        }
        return cur
    }

    /** A club colour made readable on the dark background (same hue, lighter), or null for black, white and grey. */
    private fun readable(colour: String?, background: String): String? {
        val rgb = parse(colour) ?: return null
        val (h, l0, s) = rgbToHls(rgb.first, rgb.second, rgb.third)
        if (s < 0.25 || l0 < 0.06 || l0 > 0.94) return null
        var l = l0
        var cur = hex(rgb.first, rgb.second, rgb.third)
        while (contrast(cur, background) < 4.5 && l < 0.95) {
            l += 0.02
            val (r, g, b) = hlsToRgb(h, l, s)
            cur = hex(r, g, b)
        }
        return cur
    }

    /**
     * The accent: the first club colour that is not black, white or grey, made readable. A colour that would look like
     * a result colour gives way to the other club colour when that one is clear of them; when neither is, it is turned
     * just far enough round the colour wheel.
     */
    fun choose(primary: String?, secondary: String?, background: String = BACKGROUND): AccentChoice {
        val options = listOf(primary, secondary).mapNotNull { c -> readable(c, background)?.let { c to it } }
        if (options.isEmpty()) return AccentChoice(DEFAULT, "both colours are black, white or grey: the default mint is used", null)
        val clear = options.firstOrNull { clashesWith(it.second) == null }
        val (from, made) = clear ?: options.first()
        val original = parse(from)!!.let { (r, g, b) -> hex(r, g, b) }
        val clash = if (clear == null) clashesWith(made) else null
        val chosen = if (clash != null) awayFromReserved(made, background) else made
        val why = when {
            clash != null -> "turned a little so it does not look like the $clash colour"
            from != options.first().first -> "the other club colour, so it does not look like a result colour"
            chosen.equals(original, ignoreCase = true) -> "kept as it is"
            else -> "lightened to stay readable"
        }
        return AccentChoice(chosen, why, original)
    }

    /**
     * The second accent (section icons, charts, the menu): the club colour the main accent did not use, readable and
     * clear of the result colours; for a club whose other colour is black, white or grey, a lighter shade of the
     * main accent.
     */
    fun chooseSecondary(primary: String?, secondary: String?, accentHex: String, background: String = BACKGROUND): String {
        val main = choose(primary, secondary, background)
        val other = listOf(primary, secondary).filter { it != null && !(parse(it)?.let { (r, g, b) -> hex(r, g, b) }).equals(main.fromHex, ignoreCase = true) }
        for (c in other) {
            val made = readable(c, background) ?: continue
            val clear = if (clashesWith(made) != null) awayFromReserved(made, background) else made
            if (gap(hueDegrees(clear) ?: 0.0, hueDegrees(accentHex) ?: 180.0) >= 20.0) return clear
        }
        val (r, g, b) = parse(accentHex) ?: return accentHex
        val (h, l, s) = rgbToHls(r, g, b)
        val (r2, g2, b2) = hlsToRgb(h, (l + 0.16).coerceAtMost(0.88), (s * 0.8).coerceAtLeast(0.3))
        return hex(r2, g2, b2)
    }

    /** Dark or white text on top of [hex], whichever reads better. */
    fun onColour(hex: String): String = if (contrast("#05140D", hex) >= contrast("#FFFFFF", hex)) "#05140D" else "#FFFFFF"
}
