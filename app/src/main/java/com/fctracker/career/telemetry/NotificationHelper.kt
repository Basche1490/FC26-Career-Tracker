package com.fctracker.career.telemetry

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fctracker.career.MainActivity
import com.fctracker.career.R
import java.util.concurrent.atomic.AtomicInteger

/** Phone notifications: match results and transfers (loud) and the "live sync is running" notice (quiet). */
object NotificationHelper {

    /**
     * The alert channel. Android keeps the importance a channel was first created with (and any switch the user
     * flipped on it) for good, so a channel that once ended up silent or off can only be replaced by a new id.
     */
    const val CHANNEL_UPDATES = "match_transfer_alerts"
    private const val LEGACY_CHANNEL_UPDATES = "ea_fc_telemetry"
    const val CHANNEL_SYNC = "live_sync"
    const val EXTRA_NAVIGATE_TO = "EXTRA_NAVIGATE_TO"

    /** More new items than this arrive as one summary instead of a burst of separate alerts. */
    const val MAX_SEPARATE_ALERTS = 3

    private val nextId = AtomicInteger((System.currentTimeMillis() % 100000).toInt())

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.deleteNotificationChannel(LEGACY_CHANNEL_UPDATES)
        } catch (e: Exception) {
            // an old channel that cannot be removed is simply left alone
        }
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, "Match & transfer alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A new match in your match log, and transfers made in your career"
                enableVibration(true)
                setShowBadge(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SYNC, "Live sync", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while the app watches your PC for new matches and transfers"
                setShowBadge(false)
            }
        )
    }

    /** False when Android is hiding this app's alerts (notifications turned off, or the alert channel switched off). */
    fun alertsAllowed(context: Context): Boolean {
        return try {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ensureChannels(context)
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = manager.getNotificationChannel(CHANNEL_UPDATES)
                if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false
            }
            true
        } catch (e: Exception) {
            true
        }
    }

    /** The system page where the person can allow this app's notifications. */
    fun settingsIntent(context: Context): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}"))
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun openAppIntent(context: Context, route: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, route)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Posts one alert. Returns false when Android refused it; never throws (the data is stored either way). */
    fun show(context: Context, notice: TelemetryNotice): Boolean {
        return try {
            ensureChannels(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val id = nextId.incrementAndGet()
            val icon = if (notice.isTransfer) R.drawable.ic_notification_transfer else R.drawable.ic_notification_ball
            val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
                .setSmallIcon(icon)
                .setContentTitle(notice.title)
                .setContentText(notice.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notice.message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setContentIntent(openAppIntent(context, notice.route, id))
                .setAutoCancel(true)
                .build()
            manager.notify(id, notification)
            alertsAllowed(context)
        } catch (e: Exception) {
            false
        }
    }

    /** Shows what the processor produced: up to [MAX_SEPARATE_ALERTS] alerts one by one, more as a single summary. */
    fun showAll(context: Context, notices: List<TelemetryNotice>) {
        for (notice in summarise(notices)) show(context, notice)
    }

    /** The "Send test alert" button in Settings. */
    fun showTest(context: Context): Boolean = show(
        context,
        TelemetryNotice(
            "Alerts are working",
            "This is how a new match or transfer will show up on this phone.",
            "dashboard",
            false
        )
    )

    fun summarise(notices: List<TelemetryNotice>): List<TelemetryNotice> {
        if (notices.size <= MAX_SEPARATE_ALERTS) return notices
        val transfers = notices.count { it.isTransfer }
        val matches = notices.count { !it.isTransfer && it.route == "matches" }
        val others = notices.size - transfers - matches
        val parts = buildList {
            if (matches > 0) add(if (matches == 1) "1 match" else "$matches matches")
            if (transfers > 0) add(if (transfers == 1) "1 transfer" else "$transfers transfers")
            if (others > 0) add(if (others == 1) "1 other update" else "$others other updates")
        }
        val route = when {
            transfers == 0 && others == 0 -> "matches"
            matches == 0 && others == 0 -> "transfers"
            else -> "dashboard"
        }
        return listOf(
            TelemetryNotice("${notices.size} new updates", parts.joinToString(", "), route, matches == 0 && others == 0)
        )
    }
}
