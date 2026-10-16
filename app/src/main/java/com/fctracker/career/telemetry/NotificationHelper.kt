package com.fctracker.career.telemetry

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.fctracker.career.MainActivity
import com.fctracker.career.R

/** Phone notifications: match results and transfers (loud) and the "live sync is running" notice (quiet). */
object NotificationHelper {

    const val CHANNEL_UPDATES = "ea_fc_telemetry"
    const val CHANNEL_SYNC = "live_sync"
    const val EXTRA_NAVIGATE_TO = "EXTRA_NAVIGATE_TO"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, "Live Updates", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Live Career match results and transfer updates"
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

    fun show(context: Context, notice: TelemetryNotice) {
        try {
            ensureChannels(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val requestCode = (System.currentTimeMillis() % 100000).toInt()
            val icon = if (notice.isTransfer) R.drawable.ic_notification_transfer else R.drawable.ic_notification_ball
            val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
                .setSmallIcon(icon)
                .setContentTitle(notice.title)
                .setContentText(notice.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notice.message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openAppIntent(context, notice.route, requestCode))
                .setAutoCancel(true)
                .build()
            manager.notify(requestCode, notification)
        } catch (e: SecurityException) {
            // Notification permission was denied: the data is still stored, only the alert is skipped.
        }
    }
}
