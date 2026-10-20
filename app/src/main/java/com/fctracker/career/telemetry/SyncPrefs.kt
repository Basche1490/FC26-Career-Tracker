package com.fctracker.career.telemetry

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Device-level switches that do not belong in a career save (and are not backed up with it). */
class SyncPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("fc_device_settings", Context.MODE_PRIVATE)
    private val flow = MutableStateFlow(prefs.getBoolean(KEY_BACKGROUND_SYNC, true))
    private val alertFlow = MutableStateFlow(
        NotificationPrefs(
            matches = prefs.getBoolean(KEY_NOTIFY_MATCHES, true),
            transfers = prefs.getBoolean(KEY_NOTIFY_TRANSFERS, true),
            injuries = prefs.getBoolean(KEY_NOTIFY_INJURIES, true)
        )
    )
    private val guideFlow = MutableStateFlow(prefs.getBoolean(KEY_SETUP_GUIDE_DONE, false))

    /** Keep watching the PC bridge when the app is closed (shows a small "Live sync" notification). */
    val backgroundSyncFlow: StateFlow<Boolean> get() = flow

    var backgroundSync: Boolean
        get() = flow.value
        set(value) {
            prefs.edit().putBoolean(KEY_BACKGROUND_SYNC, value).apply()
            flow.value = value
        }

    /** Which kinds of phone alert are wanted. */
    val notificationPrefsFlow: StateFlow<NotificationPrefs> get() = alertFlow

    var notificationPrefs: NotificationPrefs
        get() = alertFlow.value
        set(value) {
            prefs.edit()
                .putBoolean(KEY_NOTIFY_MATCHES, value.matches)
                .putBoolean(KEY_NOTIFY_TRANSFERS, value.transfers)
                .putBoolean(KEY_NOTIFY_INJURIES, value.injuries)
                .apply()
            alertFlow.value = value
        }

    /** True once the first-run setup guide was finished or skipped; it opens by itself only until then. */
    val setupGuideDoneFlow: StateFlow<Boolean> get() = guideFlow

    var setupGuideDone: Boolean
        get() = guideFlow.value
        set(value) {
            prefs.edit().putBoolean(KEY_SETUP_GUIDE_DONE, value).apply()
            guideFlow.value = value
        }

    /** When (epoch milliseconds) the PC bridge last answered; 0 = never. Tells "not set up yet" from "went away". */
    var lastConnectedAt: Long
        get() = prefs.getLong(KEY_LAST_CONNECTED, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_CONNECTED, value).apply()
        }

    private companion object {
        const val KEY_BACKGROUND_SYNC = "background_sync"
        const val KEY_NOTIFY_MATCHES = "notify_matches"
        const val KEY_NOTIFY_TRANSFERS = "notify_transfers"
        const val KEY_NOTIFY_INJURIES = "notify_injuries"
        const val KEY_SETUP_GUIDE_DONE = "setup_guide_done"
        const val KEY_LAST_CONNECTED = "last_connected_at"
    }
}
