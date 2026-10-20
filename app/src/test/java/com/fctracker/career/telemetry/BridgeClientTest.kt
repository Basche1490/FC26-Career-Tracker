package com.fctracker.career.telemetry

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * The phone's requests against a pretend bridge that checks signatures the way the real ones do (docs/SECURITY.md),
 * and that can misbehave on purpose: answer unsigned (an old bridge), sign with another code (an impostor), refuse the
 * clock or lock the phone out.
 */
class BridgeClientTest {

    private lateinit var server: HttpServer
    private var base = ""
    private val code = "ABCD-EFGH-JKLM-NPQR"
    private val phone = BridgeAuth.fromCode(code)!!
    private val bridge = BridgeAuth.fromCode(code)!!
    private val impostor = BridgeAuth.fromCode("ZZZZ-YYYY-XXXX-WWWW")!!

    /** ok | unsigned | forged | clock | locked */
    private var mode = "ok"
    private var statusBody = STATUS
    private var statusCode = 200
    private var resendBody = """{"status":"requested","id":"r123"}"""
    private var resendCode = 200
    private var resendCalls = 0
    private var pingBody = """{"app":"fc26-bridge","protocol":3,"version":"3.0.0","name":"gaming-pc","auth":"hmac-sha256"}"""
    private val seenTargets = mutableListOf<String>()

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ping") { reply(it, 200, pingBody) }
        server.createContext("/status") { signed(it, statusCode, statusBody) }
        server.createContext("/events") { signed(it, 200, "[]") }
        server.createContext("/resend") { ex ->
            if (ex.requestMethod == "POST") signed(ex, resendCode, resendBody, countResend = true) else reply(ex, 405, "")
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun stop() = server.stop(0)

    private fun reply(ex: HttpExchange, code: Int, body: String, signature: String? = null) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        if (signature != null) ex.responseHeaders.add(BridgeAuth.HEADER_SIGNATURE, signature)
        ex.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
        ex.close()
    }

    /** What the real bridges do: check the request's signature, then sign the answer over its one-time number. */
    private fun signed(ex: HttpExchange, code: Int, body: String, countResend: Boolean = false) {
        val h = ex.requestHeaders
        val target = ex.requestURI.rawPath + (ex.requestURI.rawQuery?.let { "?$it" } ?: "")
        seenTargets.add(target)
        val time = h.getFirst(BridgeAuth.HEADER_TIME)?.toLongOrNull()
        val nonce = h.getFirst(BridgeAuth.HEADER_NONCE).orEmpty()
        val sig = h.getFirst(BridgeAuth.HEADER_SIGNATURE).orEmpty()
        val requestBody = ex.requestBody.readBytes()
        if (time == null || bridge.signRequest(ex.requestMethod, target, time, nonce, requestBody) != sig) {
            return reply(ex, 401, """{"status":"error","message":"The pairing code was not accepted.","code":"bad_signature"}""")
        }
        when (mode) {
            "clock" -> return reply(ex, 401, """{"status":"error","code":"clock_skew","bridgeTime":1800000600}""")
            "locked" -> return reply(ex, 429, """{"status":"error","code":"locked","retryAfter":60}""")
        }
        if (countResend) {
            resendCalls++
            assertEquals("0", h.getFirst("Content-Length"))       // Windows' HTTP server refuses a POST without a length
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        when (mode) {
            "unsigned" -> reply(ex, code, body)
            "forged" -> reply(ex, code, body, impostor.signResponse(nonce, code, bytes))
            else -> reply(ex, code, body, bridge.signResponse(nonce, code, bytes))
        }
    }

    private fun closedPort(): Int = ServerSocket(0).use { it.localPort }

    // ---- signed requests and checked answers --------------------------------------------------------------------

    @Test
    fun aSignedRequestIsAnsweredAndTheAnswerIsChecked() {
        val fetch = BridgeClient.fetchStatus(base, phone)
        assertTrue(fetch.result.message, fetch.result.ok)
        assertTrue(fetch.result.verified)
        assertNotNull(fetch.status)
        assertFalse(fetch.missing)
        assertEquals("3.0.0", fetch.status!!.bridgeVersion)
        assertEquals(listOf("/status"), seenTargets)
        val events = BridgeClient.get("$base/events", 3000, phone)
        assertTrue(events.ok)
        assertEquals("[]", events.body)
    }

    @Test
    fun theBridgeRefusesAWrongCode() {
        val wrong = BridgeClient.get("$base/events", 3000, BridgeAuth.fromCode("ZZZZ-ZZZZ-ZZZZ-ZZZZ")!!)
        assertFalse(wrong.ok)
        assertEquals(FailKind.BAD_CODE, wrong.failKind)
        assertEquals(401, wrong.code)
    }

    @Test
    fun anUnsignedAnswerIsNotBelieved() {
        mode = "unsigned"
        val r = BridgeClient.get("$base/events", 3000, phone)
        assertFalse(r.ok)
        assertEquals(FailKind.OLD_BRIDGE, r.failKind)
        assertEquals("", r.body)
        assertNull(BridgeClient.fetchStatus(base, phone).status)
    }

    @Test
    fun anAnswerSignedWithAnotherCodeIsThrownAway() {
        mode = "forged"
        val r = BridgeClient.get("$base/events", 3000, phone)
        assertFalse(r.ok)
        assertEquals(FailKind.UNTRUSTED, r.failKind)
        assertEquals("", r.body)
        val fetch = BridgeClient.fetchStatus(base, phone)
        assertNull(fetch.status)
        assertFalse(fetch.missing)
    }

    @Test
    fun aWrongClockAndALockoutAreTold() {
        mode = "clock"
        val clock = BridgeClient.get("$base/events", 3000, phone)
        assertEquals(FailKind.CLOCK, clock.failKind)
        assertEquals(1800000600L, clock.bridgeTime)
        mode = "locked"
        assertEquals(FailKind.LOCKED, BridgeClient.get("$base/events", 3000, phone).failKind)
    }

    @Test
    fun errorsAreClassified() {
        val refused = BridgeClient.get("http://127.0.0.1:${closedPort()}/events", 1500, phone)
        assertFalse(refused.ok)
        assertEquals(FailKind.REFUSED, refused.failKind)

        statusCode = 404
        val http = BridgeClient.fetchStatus(base, phone)
        assertFalse(http.result.ok)
        assertEquals(FailKind.HTTP, http.result.failKind)
        assertEquals(404, http.result.code)
        assertTrue(http.result.verified)
        assertFalse(http.missing)
    }

    @Test
    fun resendReplies() {
        val ok = BridgeClient.requestResend(base, phone)
        assertTrue(ok.message, ok.ok)
        assertEquals("r123", ok.id)
        assertEquals(1, resendCalls)
        assertEquals(listOf("/resend"), seenTargets)

        resendCode = 409
        resendBody = """{"status":"error","code":"script_not_running","message":"The capture script is not running."}"""
        val refused = BridgeClient.requestResend(base, phone)
        assertFalse(refused.ok)
        assertEquals("The capture script is not running.", refused.message)

        // the same words without the bridge's signature are not shown
        mode = "unsigned"
        val unsigned = BridgeClient.requestResend(base, phone)
        assertFalse(unsigned.ok)
        assertFalse(unsigned.message, unsigned.message.contains("capture script is not running"))
        mode = "ok"

        resendCode = 404
        resendBody = ""
        val old = BridgeClient.requestResend(base, phone)
        assertFalse(old.ok)
        assertTrue(old.message, old.message.contains("too old"))

        val wrong = BridgeClient.requestResend(base, BridgeAuth.fromCode("ZZZZ-ZZZZ-ZZZZ-ZZZZ")!!)
        assertTrue(wrong.message, wrong.message.contains("pairing code"))

        val gone = BridgeClient.requestResend("http://127.0.0.1:${closedPort()}", phone, 1500)
        assertFalse(gone.ok)
        assertTrue(gone.message, gone.message.startsWith("Cannot reach the PC"))
    }

    // ---- finding the PC: the unsigned ping -----------------------------------------------------------------------

    @Test
    fun findsTheBridgeAmongOtherAddresses() {
        val dead = "http://127.0.0.1:${closedPort()}"
        val found = BridgeClient.discover(listOf(dead, base, dead), timeoutMs = 1000)
        assertEquals(listOf(base), found.map { it.base })
        assertEquals("gaming-pc", found[0].name)
        assertEquals(3, found[0].protocol)
        assertFalse(found[0].old)
        assertTrue(seenTargets.isEmpty())                     // finding the PC never needs (or sends) the code

        pingBody = "<html>EA FC Telemetry Bridge (old)</html>"
        val old = BridgeClient.discover(listOf(base), timeoutMs = 1000)
        assertEquals(1, old.size)
        assertTrue(old[0].old)

        pingBody = "<html>some router page</html>"
        assertTrue(BridgeClient.discover(listOf(base), timeoutMs = 1000).isEmpty())
        assertTrue(BridgeClient.discover(emptyList()).isEmpty())
    }

    @Test
    fun candidatesAreThePhonesOwnNetwork() {
        val list = BridgeClient.candidates("192.168.2.40")
        assertEquals(253, list.size)
        assertEquals("http://192.168.2.1:5050", list.first())
        assertFalse(list.contains("http://192.168.2.40:5050"))
        assertTrue(list.contains("http://192.168.2.254:5050"))

        val preferred = BridgeClient.candidates("192.168.2.40", "http://192.168.2.24:5050")
        assertEquals("http://192.168.2.24:5050", preferred.first())
        assertEquals(253, preferred.size)
        assertEquals(253, preferred.toSet().size)

        // a preferred address on another network is ignored; a phone on mobile data has nothing to scan
        assertEquals("http://192.168.2.1:5050", BridgeClient.candidates("192.168.2.40", "http://10.0.0.5:5050").first())
        assertTrue(BridgeClient.candidates("100.88.1.2").isEmpty())
        assertTrue(BridgeClient.candidates("").isEmpty())
        assertTrue(BridgeClient.candidates("192.168.2").isEmpty())
    }

    @Test
    fun privateAddresses() {
        assertTrue(BridgeClient.isPrivateIpv4("10.1.2.3"))
        assertTrue(BridgeClient.isPrivateIpv4("172.16.0.9"))
        assertTrue(BridgeClient.isPrivateIpv4("172.31.255.1"))
        assertFalse(BridgeClient.isPrivateIpv4("172.32.0.1"))
        assertTrue(BridgeClient.isPrivateIpv4("192.168.0.1"))
        assertFalse(BridgeClient.isPrivateIpv4("8.8.8.8"))
        assertFalse(BridgeClient.isPrivateIpv4("192.168.0.300"))
        assertFalse(BridgeClient.isPrivateIpv4("fe80::1"))
    }

    private companion object {
        const val STATUS = """{"app":"fc26-bridge","now":1,"bridge":{"version":"3.0.0","protocol":3,"minScript":"2.8.0","kind":"python","name":"pc","port":5050,"address":"http://192.168.2.24:5050"},
            "script":{"found":false},"outbox":{"pending":0},"events":{"count":0},"checks":[],"summary":"fail"}"""
    }
}
