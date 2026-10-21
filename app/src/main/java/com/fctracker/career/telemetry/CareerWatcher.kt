package com.fctracker.career.telemetry

import android.content.Context
import com.fctracker.career.AppState
import com.fctracker.career.LiveEvent
import com.fctracker.career.data.CareerStore
import com.fctracker.career.logic.CareerOps
import com.fctracker.career.logic.Milestones
import com.fctracker.career.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Watches the career while the app process runs (also during background sync): notices new milestones (announced
 * once each, with an alert when switched on) and keeps the home-screen widgets up to date.
 */
class CareerWatcher(private val context: Context, private val store: CareerStore, private val scope: CoroutineScope, private val prefs: SyncPrefs) {

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            store.state.debounce(1500).collect { s ->
                runCatching { milestones(s) }
                runCatching { WidgetUpdater.update(context, store.state.value) }
            }
        }
    }

    private fun milestones(s: AppState) {
        if (!s.isOnboarded || s.viewingSeasonYear != null) return
        val found = Milestones.detect(s, CareerOps.today(s))
        val primed = s.milestonesPrimed
        if (found.isEmpty()) {
            if (!primed) store.state.update { if (it.clubName == s.clubName) it.copy(milestonesPrimed = true) else it }
            return
        }
        val now = System.currentTimeMillis()
        var added = emptyList<com.fctracker.career.Milestone>()
        store.state.update { cur ->
            // Only add to the same career the check was made on (a save switch in between is left alone).
            if (cur.clubName != s.clubName || cur.startYear != s.startYear) return@update cur
            val fresh = found.filter { f -> cur.milestones.none { it.key == f.key } }
            added = fresh
            if (fresh.isEmpty()) return@update cur.copy(milestonesPrimed = true)
            val events = if (primed) fresh.map { LiveEvent("Milestone: ${it.title}", it.detail, now) } else emptyList()
            cur.copy(milestones = cur.milestones + fresh, milestonesPrimed = true, liveEvents = (cur.liveEvents + events).takeLast(100))
        }
        // The first check of a career only records what is already true: no burst of old milestones.
        if (!primed) return
        val notices = added.filter { Milestones.alerts(s, it) }.map { TelemetryNotice(it.title, it.detail, "milestones", false) }
        if (notices.isNotEmpty()) for (n in NotificationHelper.summarise(notices)) NotificationHelper.show(context, n)
    }
}
