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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
    private val players: PlayerDatabase,
    private val prefs: SyncPrefs? = null
) {
    private val loopScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val holders = mutableSetOf<String>()
    private var loop: Job? = null

    // What the phone knows about its contact with the PC, and the health answer worked out from it.
    private val contactFlow = MutableStateFlow(
        Contact(
            address = normalizeBridgeUrl(store.state.value.telemetryServerUrl),
            reachable = false,
            everConnected = (prefs?.lastConnectedAt ?: 0L) > 0L
        )
    )
    private val healthFlow = MutableStateFlow(HealthModel.evaluate(contactFlow.value))
    private var lastSavedContact = 0L

    /** The current contact with the bridge (address, reachable, the bridge's health report). */
    val contact: StateFlow<Contact> get() = contactFlow

    /** One answer to "is everything working?", with the steps behind it. Shown on every screen as a pill. */
    val health: StateFlow<Health> get() = healthFlow

    private fun publish(contact: Contact) {
        contactFlow.value = contact
        healthFlow.value = HealthModel.evaluate(contact)
    }

    /** The address was changed (typed, or found by "Find my PC"): forget what was known about the old one. */
    fun addressChanged() {
        publish(Contact(address = normalizeBridgeUrl(store.state.value.telemetryServerUrl), reachable = false, everConnected = false))
    }

    /** The pairing code was typed, changed or forgotten: the next answer decides again. */
    fun pairingChanged() = addressChanged()

    private var authCache: Pair<String, BridgeAuth?> = "" to null

    /** The signer for the stored pairing code, or null when none (or an invalid one) is stored. */
    private fun auth(): BridgeAuth? {
        val code = store.bridgeCode.value
        if (authCache.first != code) authCache = code to BridgeAuth.fromCode(code)
        return authCache.second
    }

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
            // A missing or refused pairing code is not fixed by asking again in ten seconds (and asking a refused
            // code again and again would get the phone locked out): ask once a minute until something changes.
            delay(if (contactFlow.value.failKind in PAIRING_PROBLEMS) SLOW_POLL_INTERVAL_MS else POLL_INTERVAL_MS)
        }
    }

    // -- fetching ------------------------------------------------------------------------------------

    /** Fetches the bridge's events once and applies them. Returns true when the bridge answered (signed). */
    suspend fun poll(notify: Boolean): Boolean = withContext(Dispatchers.IO) {
        val base = normalizeBridgeUrl(store.state.value.telemetryServerUrl)
        try {
            val auth = auth()
            if (auth == null) {
                // No code yet: nothing is asked of the bridge. The unsigned "is a bridge there?" ping still tells
                // the person whether the address is right.
                val found = BridgeClient.probe(base, 3000)
                setStatus(if (found != null) "Enter the pairing code" else "Offline", null)
                publish(
                    Contact(base, reachable = found != null, everConnected = contactFlow.value.everConnected,
                        failKind = if (found == null) FailKind.TIMEOUT else if (found.old) FailKind.OLD_BRIDGE else FailKind.UNPAIRED)
                )
                return@withContext false
            }
            val reply = BridgeClient.get("$base/events", 5000, auth)
            if (!reply.ok) {
                setStatus(statusText(reply), null)
                publish(unreachable(base, reply))
                return@withContext false
            }
            val events = parseEvents(reply.body.trim())
            setStatus("Online (${events.size} events)", events.size)
            apply(events, notify)
            refreshStatus(base, auth)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setStatus("Offline: ${e.localizedMessage ?: e.javaClass.simpleName}", null)
            publish(
                Contact(base, reachable = false, everConnected = contactFlow.value.everConnected,
                    failKind = BridgeClient.classify(e))
            )
            false
        }
    }

    private fun statusText(reply: BridgeClient.HttpResult): String = when {
        reply.failKind in PAIRING_PROBLEMS -> "Not paired: ${reply.message}"
        reply.code != 0 -> "HTTP ${reply.code}"
        else -> "Offline: ${reply.message}"
    }

    /** What a failed answer means. A bridge that refused the code (or whose answer was not signed) did answer. */
    private fun unreachable(base: String, reply: BridgeClient.HttpResult): Contact {
        val pairing = reply.failKind in PAIRING_PROBLEMS
        return Contact(
            address = base,
            reachable = pairing,
            everConnected = contactFlow.value.everConnected,
            failKind = reply.failKind,
            httpCode = reply.code,
            clockOffsetSeconds = if (reply.bridgeTime > 0) reply.bridgeTime - System.currentTimeMillis() / 1000 else 0
        )
    }

    /** Asks the bridge for its health report and publishes it together with "the bridge answered". */
    private fun refreshStatus(base: String, auth: BridgeAuth) {
        val now = System.currentTimeMillis()
        if (prefs != null && now - lastSavedContact > 60_000L) {
            lastSavedContact = now
            prefs.lastConnectedAt = now
        }
        val previous = contactFlow.value
        val fetch = BridgeClient.fetchStatus(base, auth)
        val status: BridgeStatus?
        val missing: Boolean
        when {
            fetch.result.ok -> { status = fetch.status; missing = fetch.missing }
            fetch.result.code == 404 && fetch.result.verified -> { status = null; missing = true }
            else -> { status = previous.status; missing = previous.statusMissing }   // a hiccup: keep the last report
        }
        publish(Contact(base, reachable = true, everConnected = true, status = status, statusMissing = missing))
    }

    class TestResult(val ok: Boolean, val message: String)

    /** The "Test connection" button: like [poll] but with a detailed, human-readable answer. */
    suspend fun testConnection(): TestResult = withContext(Dispatchers.IO) {
        val base = normalizeBridgeUrl(store.state.value.telemetryServerUrl)
        val auth = auth()
        if (auth == null) {
            poll(notify = false)
            return@withContext TestResult(false, "❌ Enter the pairing code first. ${HealthModel.CODE_WHERE}")
        }
        val started = System.currentTimeMillis()
        val reply = BridgeClient.get("$base/events", 5000, auth)
        val elapsed = System.currentTimeMillis() - started
        if (reply.ok) {
            val count = try { JSONArray(reply.body.trim()).length() } catch (e: Exception) { 0 }
            setStatus("Connected (${elapsed}ms, $count queued)", count)
            poll(notify = true)
            return@withContext TestResult(true, "✅ Connected and paired (${elapsed}ms) - $count event(s) in queue.")
        }
        publish(unreachable(base, reply))
        val port = base.substringAfterLast(":", "5050").substringBefore("/")
        val detail = when {
            reply.failKind in PAIRING_PROBLEMS -> reply.message + " " + HealthModel.failureFix(reply.failKind, base)
            reply.code == 503 ->
                "Bridge responded with HTTP 503 (Windows HTTP.sys queue unassigned). Run FC26_Launcher.bat again (it picks the right bridge for your PC)"
            reply.code != 0 -> "Bridge responded with HTTP ${reply.code}. Check PC bridge window."
            reply.failKind == FailKind.REFUSED -> "Connection refused on port $port ($base). Ensure the bridge window is open."
            reply.failKind == FailKind.TIMEOUT ->
                "Timed out (${elapsed}ms) on port $port. Windows Firewall or Public Wi-Fi profile is blocking incoming connections."
            reply.failKind == FailKind.NO_ROUTE ->
                "No route to host ($base). Verify your PC's IP and make sure both phone and PC are on the same Wi-Fi network."
            else -> reply.message
        }
        setStatus("Offline: $detail", null)
        TestResult(false, "❌ $detail")
    }

    // -- "re-send last match" and "find my PC" ----------------------------------------------------------

    class ActionResult(val ok: Boolean, val message: String)

    /**
     * The "Re-send last match" button: asks the capture script (through the bridge) to send its latest match again,
     * then waits a few seconds for the script's own answer so the person sees what happened.
     */
    suspend fun resendLatestMatch(): ActionResult = withContext(Dispatchers.IO) {
        val base = normalizeBridgeUrl(store.state.value.telemetryServerUrl)
        val auth = auth() ?: return@withContext ActionResult(false, "Enter the pairing code first (PC Connection). ${HealthModel.CODE_WHERE}")
        val asked = BridgeClient.requestResend(base, auth)
        if (!asked.ok) return@withContext ActionResult(false, asked.message)
        for (attempt in 1..10) {
            delay(1500)
            val fetch = BridgeClient.fetchStatus(base, auth, 4000)
            val answer = fetch.status?.script?.resend
            if (answer != null && answer.id == asked.id) {
                poll(notify = true)   // the re-sent match arrives through the bridge: take it in right away
                return@withContext when (answer.result) {
                    "sent" -> ActionResult(true, "The script sent: ${answer.detail}. It shows up in a moment if it was not already in your match list.")
                    "nothing" -> ActionResult(false, answer.detail.ifBlank { "There is no completed match to send yet." })
                    else -> ActionResult(false, "The script could not do it: ${answer.detail}")
                }
            }
        }
        ActionResult(false, "The script did not answer. Is Live Editor still open with the capture script running?")
    }

    class FindResult(val found: List<BridgeClient.Found>, val message: String)

    /** "Find my PC": looks for the bridge on the phone's own Wi-Fi network. */
    suspend fun findMyPc(): FindResult = withContext(Dispatchers.IO) {
        val phoneIp = LocalNetwork.ipv4(context)
            ?: return@withContext FindResult(emptyList(), "The phone is not on a Wi-Fi network. Join the same Wi-Fi as your PC and try again.")
        val candidates = BridgeClient.candidates(phoneIp, store.state.value.telemetryServerUrl)
        if (candidates.isEmpty()) {
            return@withContext FindResult(emptyList(), "The phone's network ($phoneIp) does not look like a home network. Join the same Wi-Fi as your PC.")
        }
        val found = BridgeClient.discover(candidates)
        if (found.isEmpty()) {
            FindResult(
                emptyList(),
                "No bridge found on $phoneIp's network. Start FC26_Launcher.bat on the PC (and keep its window open), and check that the PC is on the same Wi-Fi."
            )
        } else FindResult(found, "")
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
                signedPlayer = { name -> players.signedPlayer(name, current.startYear) },
                lookup = players.index
            )
            notices = result.notices
            result.state
        }
        NotificationHelper.showAll(context, notices, prefs?.notificationPrefs ?: NotificationPrefs())
    }

    companion object {
        const val POLL_INTERVAL_MS = 10_000L
        const val SLOW_POLL_INTERVAL_MS = 60_000L

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
