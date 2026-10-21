package com.fctracker.career.ui.recap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.fctracker.career.AppState
import com.fctracker.career.R
import com.fctracker.career.logic.Accent
import com.fctracker.career.logic.RecapBlock
import com.fctracker.career.logic.RecapPage
import com.fctracker.career.ui.screens.getTeamCrestUrl
import java.io.File

/**
 * Draws the season recap pages as images (4:5, the shape most apps show best) in the club's colours, with the crest
 * and the players' headshots. The same images are shown in the app, shared one by one or together, and put in the PDF.
 */
object RecapRenderer {
    const val WIDTH = 1080
    const val HEIGHT = 1350

    /** Images a page needs, loaded before drawing: the crest and the headshots by link. */
    class Assets(val crest: Bitmap?, val headshots: Map<String, Bitmap>)

    class Style(
        val top: Int,
        val bottom: Int,
        val body: Typeface,
        val medium: Typeface,
        val semi: Typeface,
        val mono: Typeface,
        val monoSemi: Typeface
    )

    private const val WHITE = Color.WHITE
    private val MUTED = Color.argb(184, 255, 255, 255)
    private val SOFT = Color.argb(217, 255, 255, 255)
    private val FAINT = Color.argb(153, 255, 255, 255)
    private val RULE = Color.argb(51, 255, 255, 255)
    private val GOOD = Color.parseColor("#7EE0A4")
    private val WARN = Color.parseColor("#FFC857")
    private val BAD = Color.parseColor("#FF8F86")
    private val ON_RESULT = Color.parseColor("#05140D")

    fun style(context: Context, primaryHex: String, secondaryHex: String): Style {
        fun font(id: Int, fallback: Typeface) = runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: fallback
        // The club's main colour, darkened when it is too light for white text (and the second colour when the main one is grey).
        val primary = Accent.parse(primaryHex)?.takeIf { !isGrey(it) } ?: Accent.parse(secondaryHex)?.takeIf { !isGrey(it) } ?: Accent.parse(Accent.DEFAULT)!!
        var top = Color.rgb((primary.first * 255).toInt(), (primary.second * 255).toInt(), (primary.third * 255).toInt())
        var guard = 0
        while (contrastWithWhite(top) < 3.2 && guard++ < 12) top = blend(top, Color.BLACK, 0.12f)
        val bottom = blend(top, Color.parseColor("#0B0F0D"), 0.82f)
        return Style(
            top, bottom,
            font(R.font.outfit_regular, Typeface.DEFAULT), font(R.font.outfit_medium, Typeface.DEFAULT),
            font(R.font.outfit_semibold, Typeface.DEFAULT_BOLD), font(R.font.plex_mono_medium, Typeface.MONOSPACE), font(R.font.plex_mono_semibold, Typeface.MONOSPACE)
        )
    }

    private fun isGrey(rgb: Triple<Double, Double, Double>): Boolean {
        val mx = maxOf(rgb.first, rgb.second, rgb.third)
        val mn = minOf(rgb.first, rgb.second, rgb.third)
        return mx - mn < 0.094
    }

    private fun lum(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c))
    }

    private fun contrastWithWhite(c: Int) = 1.05 / (lum(c) + 0.05)

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )

    /** Loads the crest and every headshot the pages show. Missing images fall back to initials. */
    suspend fun loadAssets(context: Context, state: AppState, pages: List<RecapPage>): Assets {
        val loader = Coil.imageLoader(context)
        suspend fun load(data: Any): Bitmap? = runCatching {
            val result = loader.execute(ImageRequest.Builder(context).data(data).size(240).allowHardware(false).build())
            (result as? SuccessResult)?.drawable?.toBitmap()
        }.getOrNull()
        val crestFile = state.clubCrestFile.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isFile }
        val crest = when {
            crestFile != null -> runCatching { BitmapFactory.decodeFile(crestFile.path) }.getOrNull()
            state.clubCrestUrl.isNotBlank() -> load(state.clubCrestUrl)
            state.isExistingTeam -> getTeamCrestUrl(state.clubName).takeIf { it.isNotBlank() }?.let { load(it) }
            else -> null
        }
        val urls = pages.flatMap { p -> p.blocks.filterIsInstance<RecapBlock.Person>().map { it.headshotUrl } }.filter { it.isNotBlank() }.distinct()
        val heads = mutableMapOf<String, Bitmap>()
        for (u in urls) load(u)?.let { heads[u] = it }
        return Assets(crest, heads)
    }

    // -- drawing ------------------------------------------------------------------------------------------------------

    private class Placed(val height: Float, val draw: (Canvas, Float, Float) -> Unit)

    private fun tp(tf: Typeface, size: Float, color: Int, spacing: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf; textSize = size; this.color = color; letterSpacing = spacing
    }

    private fun sl(text: CharSequence, p: TextPaint, width: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, maxLines: Int = 6): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt().coerceAtLeast(1))
            .setAlignment(align).setIncludePad(false).setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build()

    private fun Canvas.layoutAt(l: StaticLayout, x: Float, y: Float) { save(); translate(x, y); l.draw(this); restore() }

    private fun initials(name: String) = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" }

    private fun drawCircleImage(c: Canvas, bmp: Bitmap?, name: String, cx: Float, cy: Float, r: Float, s: Style) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.argb(46, 255, 255, 255)
        c.drawCircle(cx, cy, r, p)
        if (bmp != null) {
            c.save()
            val path = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
            c.clipPath(path)
            val scale = maxOf(2 * r / bmp.width, 2 * r / bmp.height)
            val w = bmp.width * scale
            val h = bmp.height * scale
            c.drawBitmap(bmp, null, RectF(cx - w / 2, cy - r * 1.0f - (h - 2 * r) * 0.15f, cx + w / 2, cy - r - (h - 2 * r) * 0.15f + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            c.restore()
        } else {
            val t = tp(s.semi, r * 0.72f, WHITE)
            val label = initials(name)
            val w = t.measureText(label)
            c.drawText(label, cx - w / 2, cy - (t.descent() + t.ascent()) / 2, t)
        }
    }

    private fun plan(b: RecapBlock, u: Float, w: Float, s: Style, a: Assets, pageNumber: Int): Placed = when (b) {
        is RecapBlock.Big -> {
            val v = sl(b.value, tp(s.semi, 64 * u, WHITE), w, maxLines = 1)
            val cap = if (b.caption.isNotBlank()) sl(b.caption, tp(s.body, 14 * u, SOFT), w, maxLines = 2) else null
            Placed(v.height + (cap?.let { it.height + 4 * u } ?: 0f)) { c, x, y ->
                c.layoutAt(v, x, y)
                cap?.let { c.layoutAt(it, x, y + v.height + 4 * u) }
            }
        }
        is RecapBlock.Figures -> {
            val n = b.items.size.coerceAtLeast(1)
            val gap = 10 * u
            val cw = (w - gap * (n - 1)) / n
            val long = b.items.any { it.second.length > 6 }
            var size = (if (long) 15 else 17) * u
            val vp = tp(s.monoSemi, size, WHITE)
            while (size > 10 * u && b.items.any { vp.measureText(it.second) > cw }) { size -= u; vp.textSize = size }
            val lp = tp(s.body, 11 * u, MUTED)
            val labels = b.items.map { sl(it.first, lp, cw, maxLines = 1) }
            val values = b.items.map { sl(it.second, vp, cw, maxLines = 1) }
            val h = (labels.maxOf { it.height }) + 2 * u + values.maxOf { it.height }
            Placed(h) { c, x, y ->
                b.items.indices.forEach { i ->
                    val cx = x + i * (cw + gap)
                    c.layoutAt(labels[i], cx, y)
                    c.layoutAt(values[i], cx, y + labels[i].height + 2 * u)
                }
            }
        }
        is RecapBlock.Line -> {
            val kp = tp(s.body, 13 * u, MUTED)
            val vp = tp(s.semi, 13 * u, WHITE)
            val gap = 10 * u
            val kw = minOf(kp.measureText(b.key) + 2, w * 0.58f)
            val vw = minOf(vp.measureText(b.value) + 2, w - kw - gap)
            val keyW = if (kw + gap + vw > w) w - vw - gap else kw
            val kl = sl(b.key, kp, maxOf(keyW, w * 0.3f), maxLines = 3)
            val vl = sl(b.value, vp, w - kl.width - gap, Layout.Alignment.ALIGN_OPPOSITE, maxLines = 3)
            Placed(maxOf(kl.height, vl.height).toFloat()) { c, x, y ->
                c.layoutAt(kl, x, y)
                c.layoutAt(vl, x + kl.width + gap, y)
            }
        }
        RecapBlock.Rule -> Placed(maxOf(1f, u)) { c, x, y -> c.drawRect(x, y, x + w, y + maxOf(1f, u), Paint().apply { color = RULE }) }
        is RecapBlock.Label -> {
            val l = sl(b.text.uppercase(), tp(s.medium, 11 * u, MUTED, 0.08f), w, maxLines = 2)
            Placed(l.height.toFloat()) { c, x, y -> c.layoutAt(l, x, y) }
        }
        is RecapBlock.Person -> {
            val photo = (if (b.big) 72 else 34) * u
            val gap = (if (b.big) 14 else 10) * u
            val rankW = if (b.rank != null) 12 * u + 10 * u else 0f
            val vp = tp(s.monoSemi, 17 * u, WHITE)
            val vw = if (b.value.isNotBlank()) vp.measureText(b.value) + 10 * u else 0f
            val textW = w - rankW - photo - gap - vw
            val name = sl(b.name, tp(s.semi, (if (b.big) 20 else 14) * u, WHITE), textW, maxLines = 1)
            val sub = if (b.sub.isNotBlank()) sl(b.sub, tp(s.body, (if (b.big) 13 else 11) * u, if (b.big) SOFT else MUTED), textW, maxLines = 2) else null
            val textH = name.height + (sub?.height ?: 0)
            val h = maxOf(photo, textH.toFloat())
            Placed(h) { c, x, y ->
                var cx = x
                if (b.rank != null) {
                    val rp = tp(s.mono, 12 * u, MUTED)
                    c.drawText("${b.rank}", cx, y + h / 2 - (rp.descent() + rp.ascent()) / 2, rp)
                    cx += rankW
                }
                drawCircleImage(c, a.headshots[b.headshotUrl], b.name, cx + photo / 2, y + h / 2, photo / 2, s)
                cx += photo + gap
                val ty = y + (h - textH) / 2
                c.layoutAt(name, cx, ty)
                sub?.let { c.layoutAt(it, cx, ty + name.height) }
                if (b.value.isNotBlank()) {
                    val tw = vp.measureText(b.value)
                    c.drawText(b.value, x + w - tw, y + h / 2 - (vp.descent() + vp.ascent()) / 2, vp)
                }
            }
        }
        is RecapBlock.Form -> {
            val many = b.results.size > 10
            val size = (if (many) 20 else 18) * u
            val gap = (if (many) 4 else 3) * u
            val note = if (b.note.isNotBlank()) sl(b.note, tp(s.body, 12 * u, SOFT), w * 0.4f, Layout.Alignment.ALIGN_OPPOSITE, 1) else null
            val avail = w - (note?.let { it.width + 8 * u } ?: 0f)
            val perRow = maxOf(1, ((avail + gap) / (size + gap)).toInt())
            val rows = (b.results.size + perRow - 1) / perRow
            val h = maxOf(rows * size + (rows - 1) * gap, note?.height?.toFloat() ?: 0f)
            Placed(h) { c, x, y ->
                val fill = Paint(Paint.ANTI_ALIAS_FLAG)
                val tpaint = tp(s.semi, maxOf(9f, size / u / 2 + 1) * u, ON_RESULT)
                b.results.forEachIndexed { i, r ->
                    val col = i % perRow
                    val row = i / perRow
                    val cx = x + col * (size + gap) + size / 2
                    val cy = y + row * (size + gap) + size / 2
                    fill.color = when (r) { "W" -> GOOD; "D" -> WARN; else -> BAD }
                    c.drawCircle(cx, cy, size / 2, fill)
                    val tw = tpaint.measureText(r)
                    c.drawText(r, cx - tw / 2, cy - (tpaint.descent() + tpaint.ascent()) / 2, tpaint)
                }
                note?.let { c.layoutAt(it, x + w - it.width, y + (h - it.height) / 2) }
            }
        }
        is RecapBlock.Chart -> {
            val h = 90 * u
            Placed(h) { c, x, y ->
                val clubs = maxOf(2, b.clubs, b.positions.maxOrNull() ?: 2)
                fun py(p: Int) = y + 6 * u + (h - 12 * u) * (p - 1) / (clubs - 1)
                val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 255, 255, 255); strokeWidth = maxOf(1f, u * 0.8f) }
                c.drawLine(x + 8 * u, py((clubs + 1) / 2), x + w - 8 * u, py((clubs + 1) / 2), guide)
                val path = Path()
                val n = b.positions.size
                b.positions.forEachIndexed { i, p ->
                    val px = x + 8 * u + (w - 16 * u) * (if (n > 1) i.toFloat() / (n - 1) else 0f)
                    if (i == 0) path.moveTo(px, py(p)) else path.lineTo(px, py(p))
                }
                c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = WHITE; strokeWidth = 2.5f * u; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND })
                b.positions.lastOrNull()?.let { p -> c.drawCircle(x + w - 8 * u, py(p), 4 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE }) }
            }
        }
    }

    /**
     * One page as an image. [clubLine] is "Club name · 2025/26". Everything is sized from the width, and a page with
     * many lines is drawn a little smaller so it always fits the 4:5 frame.
     */
    fun render(page: RecapPage, total: Int, clubLine: String, clubName: String, style: Style, assets: Assets, width: Int = WIDTH): Bitmap {
        val height = width * HEIGHT / WIDTH
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply { shader = LinearGradient(width * 0.62f, 0f, width * 0.38f, height.toFloat(), intArrayOf(style.top, style.bottom), floatArrayOf(0f, 0.72f), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)

        var u = width / 330f
        var placed: List<Placed> = emptyList()
        var headH = 0f
        var titleL: StaticLayout? = null
        var footH = 0f
        var gap = 14 * u
        var pad = 20 * u
        for (attempt in 0 until 4) {
            pad = 20 * (width / 330f)
            val w = width - 2 * pad
            gap = 14 * u
            headH = 26 * u
            titleL = sl(page.title, tp(style.semi, 24 * u, WHITE), w, maxLines = 2)
            footH = tp(style.body, 11 * u, FAINT).let { it.descent() - it.ascent() }
            placed = page.blocks.map { plan(it, u, w, style, assets, page.number) }
            val need = pad + headH + gap + titleL.height + gap + placed.sumOf { it.height.toDouble() }.toFloat() + gap * placed.size + footH + pad
            if (need <= height) break
            u *= (height / need) * 0.98f
        }
        val w = width - 2 * pad
        var y = pad
        // Header: crest, club and season, page number.
        val crestSize = 26 * u
        assets.crest?.let { cr ->
            val scale = minOf(crestSize / cr.width, crestSize / cr.height)
            val cw = cr.width * scale
            val ch = cr.height * scale
            c.drawBitmap(cr, null, RectF(pad + (crestSize - cw) / 2, y + (crestSize - ch) / 2, pad + (crestSize + cw) / 2, y + (crestSize + ch) / 2), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } ?: run {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(56, 255, 255, 255) }
            c.drawRoundRect(RectF(pad, y, pad + crestSize, y + crestSize), crestSize * 0.28f, crestSize * 0.28f, p)
            val t = tp(style.semi, crestSize * 0.38f, WHITE)
            val label = initials(clubName)
            c.drawText(label, pad + (crestSize - t.measureText(label)) / 2, y + crestSize / 2 - (t.descent() + t.ascent()) / 2, t)
        }
        val numP = tp(style.mono, 11 * u, MUTED)
        val num = if (total > 1) "${page.number} / $total" else ""
        val numW = numP.measureText(num)
        if (num.isNotEmpty()) c.drawText(num, pad + w - numW, y + crestSize / 2 - (numP.descent() + numP.ascent()) / 2, numP)
        val clubL = sl(clubLine, tp(style.body, 12 * u, SOFT), w - crestSize - 10 * u - numW - 10 * u, maxLines = 1)
        c.layoutAt(clubL, pad + crestSize + 10 * u, y + (crestSize - clubL.height) / 2)
        y += headH + gap
        titleL?.let { c.layoutAt(it, pad, y); y += it.height + gap }
        placed.forEach { p -> p.draw(c, pad, y); y += p.height + gap }
        // Footer.
        val fp = tp(style.body, 11 * u, FAINT)
        val brand = "FC Tracker"
        c.drawText(brand, width - pad - fp.measureText(brand), height - pad - fp.descent(), fp)
        return bmp
    }
}
