package com.fctracker.career.telemetry

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Device-level switches that do not belong in a career save (and are not backed up with it). */
class SyncPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("fc_device_settings", Context.MODE_PRIVATE)
    private val flow = MutableStateFlow(prefs.getBoolean(KEY_BACKGROUND_SYNC, true))

    /** Keep watching the PC bridge when the app is closed (shows a small "Live sync" notification). */
    val backgroundSyncFlow: StateFlow<Boolean> get() = flow

    var backgroundSync: Boolean
        get() = flow.value
        set(value) {
            prefs.edit().putBoolean(KEY_BACKGROUND_SYNC, value).apply()
            flow.value = value
        }

    private companion object {
        const val KEY_BACKGROUND_SYNC = "background_sync"
    }
}
