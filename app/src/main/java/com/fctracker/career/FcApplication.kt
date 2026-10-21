package com.fctracker.career

import android.app.Application
import com.fctracker.career.data.CareerStore
import com.fctracker.career.data.PlayerDatabase
import com.fctracker.career.data.SaveStore
import com.fctracker.career.data.SecretStore
import com.fctracker.career.telemetry.SyncPrefs
import com.fctracker.career.telemetry.TelemetrySync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Holds the objects the screens and the background sync service share while the app process is alive. */
class FcApplication : Application(), coil.ImageLoaderFactory {

    val store: CareerStore by lazy {
        CareerStore(
            saves = SaveStore(File(filesDir, "saves")),
            secrets = SecretStore(this),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
    }

    val playerDatabase: PlayerDatabase by lazy { PlayerDatabase(this) }

    val telemetry: TelemetrySync by lazy { TelemetrySync(this, store, playerDatabase, syncPrefs) }

    val syncPrefs: SyncPrefs by lazy { SyncPrefs(this) }

    private val watcher by lazy { com.fctracker.career.telemetry.CareerWatcher(this, store, CoroutineScope(SupervisorJob() + Dispatchers.Default), syncPrefs) }

    override fun onCreate() {
        super.onCreate()
        watcher.start()
    }

    /** One image loader for the whole app (screens, recap images, widgets): SVG crests, and the referer some headshot sites need. */
    override fun newImageLoader(): coil.ImageLoader {
        val http = okhttp3.OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                if (original.url.host.contains("futbin")) builder.header("Referer", "https://www.futbin.com/")
                chain.proceed(builder.build())
            }
            .build()
        return coil.ImageLoader.Builder(this)
            .okHttpClient(http)
            .components { add(coil.decode.SvgDecoder.Factory()) }
            .crossfade(true)
            .allowHardware(false)
            .build()
    }
}
