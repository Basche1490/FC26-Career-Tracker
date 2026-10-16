package com.fctracker.career.telemetry

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fctracker.career.FcApplication
import com.fctracker.career.R

/**
 * Keeps the app watching the PC bridge while it is closed, so match and transfer alerts still arrive.
 * It only shows a small "Live sync" notification; the polling itself is done by [TelemetrySync].
 */
class LiveSyncService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            (application as FcApplication).syncPrefs.backgroundSync = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        (application as FcApplication).telemetry.acquire(HOLDER)
        return START_STICKY
    }

    override fun onDestroy() {
        (application as FcApplication).telemetry.release(HOLDER)
        super.onDestroy()
    }

    /** Android 15 limits "data sync" services to about six hours a day; stop cleanly when it says so. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    private fun enterForeground(): Boolean {
        return try {
            NotificationHelper.ensureChannels(this)
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            // For example: Android refused to start a foreground service from the background.
            Log.w(TAG, "Could not start live sync in the foreground", e)
            false
        }
    }

    private fun buildNotification(): Notification {
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, LiveSyncService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_notification_ball)
            .setContentTitle("Live sync is on")
            .setContentText("Watching your PC for new matches and transfers")
            .setContentIntent(NotificationHelper.openAppIntent(this, "dashboard", 0))
            .addAction(0, "Turn off", stop)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "LiveSyncService"
        private const val HOLDER = "service"
        private const val NOTIFICATION_ID = 4242
        const val ACTION_STOP = "com.fctracker.career.STOP_LIVE_SYNC"

        /** Starts the service. Never throws: if Android refuses, live sync simply stays off until next time. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, LiveSyncService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Live sync could not be started", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, LiveSyncService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Live sync could not be stopped", e)
            }
        }
    }
}
