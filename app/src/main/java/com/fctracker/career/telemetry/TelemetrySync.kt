package com.fctracker.career.telemetry

import android.content.Context
import com.fctracker.career.data.CareerStore
import com.fctracker.career.data.PlayerDatabase
import com.fctracker.career.ui.screens.getTeamCrestUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Talks to the PC bridge: fetches the event list, hands it to [TelemetryProcessor] and shows notifications.
 *
 * One polling loop serves everybody. Whoever needs live data calls [acquire] (the open app, the background
 * sync service); the loop runs while anyone holds it and stops when the last holder calls [release].
 */
class TelemetrySync(
    private val context: Context,
    private val store: CareerStore,
    private val players: PlayerDatabase
) {
    private val loopScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val holders = mutableSetOf<String>()
    private var loop: Job? = null

    fun acquire(holder: String) {
        synchronized(holders) {
            holders.add(holder)
            if (loop?.isActive != true) loop = loopScope.launch { runLoop() }
        }
    }

    fun release(holder: String) {
        synchronized(holders) {
            holders.remove(holder)
            if (holders.isEmpty()) {
                loop?.cancel()
                loop = null
            }
        }
    }

    private suspend fun runLoop() {
        while (loopScope.isActive) {
            try {
                poll(notify = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // poll() reports its own problems through the bridge status; keep looping
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    // -- fetching ------------------------------------------------------------------------------------

    /** Fetches the bridge's events once and applies them. Returns true when the bridge answered. */
    suspend fun poll(notify: Boolean): Boolean = withContext(Dispatchers.IO) {
        val base = normalizeBridgeUrl(store.state.value.telemetryServerUrl)
        try {
            val conn = URL("$base/events").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val code = conn.responseCode
                if (code != 200) {
                    setStatus("HTTP $code", null)
                    return@withContext false
                }
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
                val events = parseEvents(body)
                setStatus("Online (${events.size} events)", events.size)
                apply(events, notify)
                true
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setStatus("Offline: ${e.localizedMessage ?: e.javaClass.simpleName}", null)
            false
        }
    }

    class TestResult(val ok: Boolean, val message: String)

    /** The "Test connection" button: like [poll] but with a detailed, human-readable answer. */
    suspend fun testConnection(): TestResult = withContext(Dispatchers.IO) {
        val base = normalizeBridgeUrl(store.state.value.telemetryServerUrl)
        val started = System.currentTimeMillis()
        try {
            val conn = URL("$base/events").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                val elapsed = System.currentTimeMillis() - started
                if (code == 200) {
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
                    val count = try { JSONArray(body).length() } catch (e: Exception) { 0 }
                    setStatus("Connected (${elapsed}ms, $count queued)", count)
                    poll(notify = true)
                    TestResult(true, "✅ Connected! (${elapsed}ms) - $count event(s) in queue.")
                } else {
                    val hint = if (code == 503) {
                        " (Windows HTTP.sys queue unassigned. Run updated Start_Bridge.bat as Admin or use Start_Bridge_Python.bat)"
                    } else " Check PC bridge window."
                    setStatus("HTTP $code: $hint", null)
                    TestResult(false, "Bridge responded with HTTP $code.$hint")
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - started
            val port = base.substringAfterLast(":", "5050").substringBefore("/")
            val detail = when (e) {
                is ConnectException -> "Connection refused on port $port ($base). Ensure Start_Bridge.bat is open."
                is SocketTimeoutException -> "Timed out (${elapsed}ms) on port $port. Windows Firewall or Public Wi-Fi profile is blocking incoming connections."
                is NoRouteToHostException -> "No route to host ($base). Verify your PC's IP and make sure both phone and PC are on the same Wi-Fi network."
                is IOException -> e.localizedMessage ?: e.javaClass.simpleName
                else -> e.localizedMessage ?: e.javaClass.simpleName
            }
            setStatus("Offline: $detail", null)
            TestResult(false, "❌ $detail")
        }
    }

    private fun setStatus(text: String, eventCount: Int?) {
        store.state.update {
            it.copy(bridgeConnectionStatus = text, bridgeEventCount = eventCount ?: it.bridgeEventCount)
        }
    }

    // -- applying ------------------------------------------------------------------------------------

    /**
     * Applies events to the career. The change is computed inside StateFlow.update, so it is based on the
     * newest state even if the user edited something at the same moment.
     */
    fun apply(events: List<JSONObject>, notify: Boolean) {
        if (events.isEmpty()) return
        val nowSeconds = System.currentTimeMillis() / 1000
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        var notices: List<TelemetryNotice> = emptyList()
        store.state.update { current ->
            val result = TelemetryProcessor.apply(
                state = current,
                eventObjects = events,
                notify = notify,
                nowSeconds = nowSeconds,
                today = today,
                crestUrlFor = ::getTeamCrestUrl,
                signedPlayer = { name -> players.signedPlayer(name, current.startYear) }
            )
            notices = result.notices
            result.state
        }
        notices.forEach { NotificationHelper.show(context, it) }
    }

    companion object {
        const val POLL_INTERVAL_MS = 10_000L

        /** What the bridge sends: normally a JSON array, but one JSON object per line is accepted too. */
        fun parseEvents(body: String): List<JSONObject> {
            val list = mutableListOf<JSONObject>()
            if (body.startsWith("[")) {
                val array = JSONArray(body)
                // One malformed entry must not throw away every other event in the list.
                for (i in 0 until array.length()) array.optJSONObject(i)?.let { list.add(it) }
            } else {
                for (line in body.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.isNotBlank()) {
                        try { list.add(JSONObject(trimmed)) } catch (e: Exception) { /* skip it */ }
                    }
                }
            }
            return list
        }

        /** Accepts "192.168.2.24", "host:5050", "http://host:5050/events" and returns "http://host:5050". */
        fun normalizeBridgeUrl(raw: String): String {
            var url = raw.trim()
            if (url.isEmpty()) return "http://192.168.2.24:5050"
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
            url = url.removeSuffix("/")
            for (suffix in listOf("/events", "/json", "/api/telemetry")) url = url.removeSuffix(suffix)
            // An address without a port gets the bridge's default port.
            if (!url.substringAfter("://").contains(":")) url = "$url:5050"
            return url
        }
    }
}
