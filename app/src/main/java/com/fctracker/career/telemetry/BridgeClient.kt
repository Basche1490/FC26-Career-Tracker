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
 * The bridge speaks plain HTTP on the local network: GET /ping (is a bridge here?), GET /events (the news),
 * GET /status (its health report) and POST /resend ("send the latest match again").
 *
 * Everything except /ping is signed with the pairing code (see [BridgeAuth]), and an answer only counts when it carries
 * the bridge's signature. A request without a code is never sent, an answer without the right signature is thrown away.
 */
object BridgeClient {

    const val PORT = 5050

    /**
     * [verified] is true when the answer carried the bridge's signature (so [body] really is from the bridge).
     * [bridgeTime] is the bridge's clock when it said the clocks differ.
     */
    class HttpResult(
        val ok: Boolean, val code: Int, val body: String, val failKind: FailKind, val message: String,
        val verified: Boolean = false, val bridgeTime: Long = 0
    )

    fun classify(e: Exception): FailKind = when (e) {
        is ConnectException -> FailKind.REFUSED
        is SocketTimeoutException -> FailKind.TIMEOUT
        is NoRouteToHostException, is UnknownHostException -> FailKind.NO_ROUTE
        else -> FailKind.OTHER
    }

    private fun reasonOf(body: String): Pair<String, Long> {
        val json = try { org.json.JSONObject(body.trim()) } catch (e: Exception) { return "" to 0L }
        return json.optString("code") to json.optLong("bridgeTime", 0)
    }

    /**
     * One request. With [auth] it is signed and the answer must carry the bridge's signature; without it nothing is
     * signed and nothing is checked (only /ping is meant to be asked that way).
     */
    private fun request(url: String, method: String, timeoutMs: Int, auth: BridgeAuth?): HttpResult {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.instanceFollowRedirects = false
                val noBody = ByteArray(0)
                var nonce = ""
                if (auth != null) {
                    val signed = auth.sign(method, URL(url).file.ifEmpty { "/" }, noBody)
                    nonce = signed.nonce
                    conn.setRequestProperty(BridgeAuth.HEADER_TIME, signed.time.toString())
                    conn.setRequestProperty(BridgeAuth.HEADER_NONCE, signed.nonce)
                    conn.setRequestProperty(BridgeAuth.HEADER_SIGNATURE, signed.signature)
                }
                if (method == "POST") {
                    // An empty body, sent with "Content-Length: 0" (Windows' HTTP server wants the length). Not in
                    // streaming mode: there the Java HTTP client cannot read a 401 answer, which says why it refused.
                    conn.doOutput = true
                    conn.outputStream.close()
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
                val body = String(bytes, Charsets.UTF_8)
                val signature = conn.getHeaderField(BridgeAuth.HEADER_SIGNATURE)
                if (auth == null) {
                    return if (code in 200..299) HttpResult(true, code, body, FailKind.NONE, "")
                    else HttpResult(false, code, body, FailKind.HTTP, "HTTP $code")
                }
                // From here on the answer has to prove it comes from the bridge that knows the code.
                if (signature != null) {
                    if (!auth.verifyResponse(nonce, code, bytes, signature)) {
                        return HttpResult(false, code, "", FailKind.UNTRUSTED, "The answer was not signed with your pairing code.")
                    }
                    return if (code in 200..299) HttpResult(true, code, body, FailKind.NONE, "", verified = true)
                    else HttpResult(false, code, body, FailKind.HTTP, "HTTP $code", verified = true)
                }
                if (code in 200..299) {
                    return HttpResult(false, code, "", FailKind.OLD_BRIDGE, "The bridge did not sign its answer: it is too old.")
                }
                val (reason, bridgeTime) = reasonOf(body)
                when {
                    code == 401 && reason == "bad_signature" ->
                        HttpResult(false, code, body, FailKind.BAD_CODE, "The PC did not accept the pairing code.")
                    code == 401 && reason == "clock_skew" ->
                        HttpResult(false, code, body, FailKind.CLOCK, "The clocks of the phone and the PC differ.", bridgeTime = bridgeTime)
                    code == 429 ->
                        HttpResult(false, code, body, FailKind.LOCKED, "Too many wrong attempts. Wait a minute.")
                    else -> HttpResult(false, code, body, FailKind.HTTP, "HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: IOException) {
            HttpResult(false, 0, "", classify(e), e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    /** An unsigned GET, for the places that need no code (the address check before a code is entered). */
    fun get(url: String, timeoutMs: Int = 5000): HttpResult = request(url, "GET", timeoutMs, null)

    /** A signed GET: the answer only counts when it carries the bridge's signature. */
    fun get(url: String, timeoutMs: Int, auth: BridgeAuth): HttpResult = request(url, "GET", timeoutMs, auth)

    fun post(url: String, timeoutMs: Int, auth: BridgeAuth): HttpResult = request(url, "POST", timeoutMs, auth)

    /** The answer to "how are you?": [status] is null when the bridge is an older one without a health report. */
    class StatusFetch(val result: HttpResult, val status: BridgeStatus?) {
        /** True when the bridge answered, but not with a health report. */
        val missing: Boolean get() = result.ok && status == null
    }

    fun fetchStatus(base: String, auth: BridgeAuth, timeoutMs: Int = 5000): StatusFetch {
        val result = get("$base/status", timeoutMs, auth)
        val status = if (result.ok) BridgeStatus.parse(result.body) else null
        return StatusFetch(result, status)
    }

    class ResendReply(val ok: Boolean, val id: String, val message: String)

    /** Asks the bridge to have the capture script send its latest match again. */
    fun requestResend(base: String, auth: BridgeAuth, timeoutMs: Int = 8000): ResendReply {
        val result = post("$base/resend", timeoutMs, auth)
        // Only a signed answer is believed: its words are shown to the person.
        val json = if (result.verified) try { org.json.JSONObject(result.body.trim()) } catch (e: Exception) { null } else null
        return when {
            result.ok && json != null && json.optString("status") == "requested" ->
                ResendReply(true, json.optString("id"), "Asked the script to send the latest match again.")
            json != null && json.optString("message").isNotBlank() -> ResendReply(false, "", json.optString("message"))
            result.failKind == FailKind.BAD_CODE -> ResendReply(false, "", "The PC did not accept the pairing code. Check it in PC Connection.")
            result.failKind == FailKind.CLOCK -> ResendReply(false, "", "The clocks of the phone and the PC differ by more than 5 minutes.")
            result.failKind == FailKind.LOCKED -> ResendReply(false, "", "Too many wrong attempts. Wait a minute, then try again.")
            result.failKind == FailKind.OLD_BRIDGE -> ResendReply(false, "", "This bridge is too old for that. ${HealthModel.UPDATE_HINT}")
            result.failKind == FailKind.UNTRUSTED -> ResendReply(false, "", "The answer did not come from your PC's bridge, so it was ignored.")
            result.code == 404 || result.code == 405 -> ResendReply(false, "", "This bridge is too old for that. ${HealthModel.UPDATE_HINT}")
            result.failKind != FailKind.NONE && result.code == 0 -> ResendReply(false, "", "Cannot reach the PC (${result.message}).")
            else -> ResendReply(false, "", "The bridge did not accept the request (${result.message.ifBlank { "HTTP ${result.code}" }}).")
        }
    }

    // -- finding the PC ----------------------------------------------------------------------------------

    /** What a bridge says about itself to anybody who asks (GET /ping). [old] is a bridge from before pairing codes. */
    class Found(val base: String, val name: String, val version: String, val protocol: Int, val old: Boolean)

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

    /**
     * Asks one address "are you a bridge?" with the unsigned /ping, which tells nothing but the name and version.
     * An older bridge (no /ping) is recognised by its page. Null when it is something else.
     */
    fun probe(base: String, timeoutMs: Int = 800): Found? {
        val result = get("$base/ping", timeoutMs)
        if (!result.ok) return null
        val json = try { org.json.JSONObject(result.body.trim()) } catch (e: Exception) { null }
        if (json != null && json.optString("app") == "fc26-bridge") {
            return Found(base, json.optString("name"), json.optString("version"), json.optInt("protocol", 0), old = false)
        }
        return if (result.body.contains("Telemetry Bridge", ignoreCase = true)) Found(base, "", "", 0, old = true) else null
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
