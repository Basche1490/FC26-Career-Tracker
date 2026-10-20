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
class FcApplication : Application() {

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
}
