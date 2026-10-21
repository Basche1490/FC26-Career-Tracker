package com.fctracker.career.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.fctracker.career.AppState
import com.fctracker.career.FcApplication
import com.fctracker.career.MainActivity
import com.fctracker.career.R
import com.fctracker.career.telemetry.NotificationHelper
import com.fctracker.career.ui.screens.getTeamCrestUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Large widget: crest, club, position, next match, form and the last result. */
class LargeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.refreshAsync(context, goAsync())
}

/** Small widget: league position, points and form. */
class SmallWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdater.refreshAsync(context, goAsync())
}

object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastKey: Any? = null
    private var crestKey: String? = null
    private var crest: Bitmap? = null

    private val GOOD = Color.parseColor("#7EE0A4")
    private val GOOD_BG = Color.parseColor("#11301F")
    private val WARN = Color.parseColor("#FFC857")
    private val WARN_BG = Color.parseColor("#3A2D0B")
    private val BAD = Color.parseColor("#FF8F86")
    private val BAD_BG = Color.parseColor("#3C1816")

    fun refreshAsync(context: Context, pending: android.content.BroadcastReceiver.PendingResult?) {
        val app = context.applicationContext as? FcApplication
        scope.launch {
            try { if (app != null) update(context.applicationContext, app.store.state.value, force = true) } finally { pending?.finish() }
        }
    }

    private fun ids(context: Context, cls: Class<*>): IntArray = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls))

    /** Redraws every placed widget when what they show changed (or always with [force]). Call off the main thread. */
    suspend fun update(context: Context, state: AppState, force: Boolean = false) {
        val large = ids(context, LargeWidget::class.java)
        val small = ids(context, SmallWidget::class.java)
        if (large.isEmpty() && small.isEmpty()) return
        val data = WidgetData.of(state)
        val key = data to (state.clubCrestFile + state.clubCrestUrl + state.isExistingTeam)
        if (!force && key == lastKey) return
        lastKey = key
        val crestBmp = loadCrest(context, state)
        val manager = AppWidgetManager.getInstance(context)
        val open = PendingIntent.getActivity(
            context, 4100, Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(NotificationHelper.EXTRA_NAVIGATE_TO, "dashboard")
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val density = context.resources.displayMetrics.density
        if (large.isNotEmpty()) {
            val v = RemoteViews(context.packageName, R.layout.widget_large)
            v.setTextViewText(R.id.w_club, data.club)
            v.setTextViewText(R.id.w_position, data.positionLine)
            v.setTextViewText(R.id.w_next, data.nextTitle)
            v.setTextViewText(R.id.w_next_line, data.nextLine)
            v.setTextViewText(R.id.w_last, data.last)
            v.setImageViewBitmap(R.id.w_form, formStrip(data.form, (20 * density).toInt(), (4 * density).toInt()))
            if (crestBmp != null) v.setImageViewBitmap(R.id.w_crest, crestBmp) else v.setImageViewBitmap(R.id.w_crest, initialsCrest(data.club, (52 * density).toInt()))
            v.setOnClickPendingIntent(R.id.w_root, open)
            manager.updateAppWidget(large, v)
        }
        if (small.isNotEmpty()) {
            val v = RemoteViews(context.packageName, R.layout.widget_small)
            v.setTextViewText(R.id.w_league, data.league)
            v.setTextViewText(R.id.w_big_position, data.position)
            v.setTextViewText(R.id.w_points, "${data.points} pts · ${data.played} played")
            v.setImageViewBitmap(R.id.w_form, formStrip(data.form, (16 * density).toInt(), (3 * density).toInt()))
            if (crestBmp != null) v.setImageViewBitmap(R.id.w_crest, crestBmp) else v.setImageViewBitmap(R.id.w_crest, initialsCrest(data.club, (36 * density).toInt()))
            v.setOnClickPendingIntent(R.id.w_root, open)
            manager.updateAppWidget(small, v)
        }
    }

    private suspend fun loadCrest(context: Context, state: AppState): Bitmap? {
        val key = "${state.clubCrestFile}|${state.clubCrestUrl}|${state.isExistingTeam}|${state.clubName}"
        if (key == crestKey) return crest
        val file = state.clubCrestFile.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isFile }
        val url = state.clubCrestUrl.ifBlank { if (state.isExistingTeam) getTeamCrestUrl(state.clubName) else "" }
        val bmp: Bitmap? = runCatching {
            when {
                file != null -> BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 2 })
                url.isNotBlank() -> (Coil.imageLoader(context).execute(ImageRequest.Builder(context).data(url).size(128).allowHardware(false).build()) as? SuccessResult)?.drawable?.toBitmap()
                else -> null
            }
        }.getOrNull()?.let { scaleDown(it, 160) }
        crestKey = key
        crest = bmp
        return bmp
    }

    private fun scaleDown(b: Bitmap, max: Int): Bitmap {
        val m = maxOf(b.width, b.height)
        if (m <= max) return b
        val f = max.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
    }

    private fun initialsCrest(club: String, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#62E0B0") }
        c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.28f, size * 0.28f, p)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#05140D"); textSize = size * 0.38f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val label = club.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" }
        c.drawText(label, size / 2f, size / 2f - (t.descent() + t.ascent()) / 2, t)
        return bmp
    }

    /** W / D / L circles, oldest first, as one image (widgets cannot draw shapes per result themselves). */
    fun formStrip(form: List<String>, size: Int, gap: Int): Bitmap {
        val n = form.size.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(n * size + (n - 1) * gap, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size * 0.5f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        form.forEachIndexed { i, r ->
            val cx = i * (size + gap) + size / 2f
            fill.color = when (r) { "W" -> GOOD_BG; "D" -> WARN_BG; else -> BAD_BG }
            c.drawCircle(cx, size / 2f, size / 2f, fill)
            text.color = when (r) { "W" -> GOOD; "D" -> WARN; else -> BAD }
            c.drawText(r, cx, size / 2f - (text.descent() + text.ascent()) / 2, text)
        }
        return bmp
    }
}
