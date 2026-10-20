package com.fctracker.career.telemetry

import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The phone's side of the conversation with the PC bridge, with nothing Android in it so it can be tested on a plain JVM.
 * The bridge speaks plain HTTP on the local network: GET /events (the news), GET /status (its health report) and
 * POST /resend ("send the latest match again").
 */
object BridgeClient {

    const val PORT = 5050

    class HttpResult(val ok: Boolean, val code: Int, val body: String, val failKind: FailKind, val message: String)

    fun classify(e: Exception): FailKind = when (e) {
        is ConnectException -> FailKind.REFUSED
        is SocketTimeoutException -> FailKind.TIMEOUT
        is NoRouteToHostException, is UnknownHostException -> FailKind.NO_ROUTE
        else -> FailKind.OTHER
    }

    private fun request(url: String, method: String, timeoutMs: Int): HttpResult {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.instanceFollowRedirects = false
                if (method == "POST") {
                    conn.doOutput = true
                    conn.setFixedLengthStreamingMode(0)
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code in 200..299) HttpResult(true, code, body, FailKind.NONE, "")
                else HttpResult(false, code, body, FailKind.HTTP, "HTTP $code")
            } finally {
                conn.disconnect()
            }
        } catch (e: IOException) {
            HttpResult(false, 0, "", classify(e), e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    fun get(url: String, timeoutMs: Int = 5000): HttpResult = request(url, "GET", timeoutMs)

    fun post(url: String, timeoutMs: Int = 5000): HttpResult = request(url, "POST", timeoutMs)

    /** The answer to "how are you?": [status] is null when the bridge is an older one without a health report. */
    class StatusFetch(val result: HttpResult, val status: BridgeStatus?) {
        /** True when the bridge answered, but not with a health report. */
        val missing: Boolean get() = result.ok && status == null
    }

    fun fetchStatus(base: String, timeoutMs: Int = 5000): StatusFetch {
        val result = get("$base/status", timeoutMs)
        val status = if (result.ok) BridgeStatus.parse(result.body) else null
        return StatusFetch(result, status)
    }

    class ResendReply(val ok: Boolean, val id: String, val message: String)

    /** Asks the bridge to have the capture script send its latest match again. */
    fun requestResend(base: String, timeoutMs: Int = 8000): ResendReply {
        val result = post("$base/resend", timeoutMs)
        val json = try { org.json.JSONObject(result.body.trim()) } catch (e: Exception) { null }
        return when {
            result.ok && json != null && json.optString("status") == "requested" ->
                ResendReply(true, json.optString("id"), "Asked the script to send the latest match again.")
            json != null && json.optString("message").isNotBlank() -> ResendReply(false, "", json.optString("message"))
            result.code == 404 || result.code == 405 -> ResendReply(false, "", "This bridge is too old for that. ${HealthModel.UPDATE_HINT}")
            result.failKind != FailKind.NONE && result.code == 0 -> ResendReply(false, "", "Cannot reach the PC (${result.message}).")
            else -> ResendReply(false, "", "The bridge did not accept the request (${result.message.ifBlank { "HTTP ${result.code}" }}).")
        }
    }

    // -- finding the PC ----------------------------------------------------------------------------------

    class Found(val base: String, val status: BridgeStatus?)

    /** True for 10.x.x.x, 172.16-31.x.x and 192.168.x.x: addresses of a home or office network. */
    fun isPrivateIpv4(ip: String): Boolean {
        val parts = ip.split(".").map { it.toIntOrNull() ?: return false }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 || (parts[0] == 172 && parts[1] in 16..31) || (parts[0] == 192 && parts[1] == 168)
    }

    /**
     * The addresses to try for "Find my PC": every host of the phone's own /24 network (the first three numbers of
     * its address), the one the app already uses first. Empty when the phone is not on a home network.
     */
    fun candidates(phoneIp: String, preferredBase: String? = null): List<String> {
        if (!isPrivateIpv4(phoneIp)) return emptyList()
        val prefix = phoneIp.substringBeforeLast(".")
        val own = phoneIp.substringAfterLast(".").toIntOrNull()
        val hosts = (1..254).filter { it != own }.map { "$prefix.$it" }
        val preferredHost = preferredBase?.substringAfter("://")?.substringBefore(":")?.substringBefore("/")
        val ordered = if (preferredHost != null && preferredHost in hosts) listOf(preferredHost) + hosts.filter { it != preferredHost } else hosts
        return ordered.map { "http://$it:$PORT" }
    }

    /** The first answer that is this app's bridge, or null. An older bridge (no /status) is recognised by its page. */
    fun probe(base: String, timeoutMs: Int = 800): Found? {
        val result = get("$base/status", timeoutMs)
        if (!result.ok) return null
        val status = BridgeStatus.parse(result.body)
        if (status != null) return Found(base, status)
        return if (result.body.contains("Telemetry Bridge", ignoreCase = true)) Found(base, null) else null
    }

    /** Probes all [bases] in parallel and returns those that are a bridge, in the order of [bases]. */
    fun discover(bases: List<String>, timeoutMs: Int = 800, parallel: Int = 48): List<Found> {
        if (bases.isEmpty()) return emptyList()
        val pool = Executors.newFixedThreadPool(parallel.coerceAtMost(bases.size))
        try {
            val futures = bases.map { base -> pool.submit(Callable<Found?> { probe(base, timeoutMs) }) }
            return futures.mapNotNull { f ->
                try { f.get(timeoutMs * 6L, TimeUnit.MILLISECONDS) } catch (e: Exception) { null }
            }
        } finally {
            pool.shutdownNow()
        }
    }
}
