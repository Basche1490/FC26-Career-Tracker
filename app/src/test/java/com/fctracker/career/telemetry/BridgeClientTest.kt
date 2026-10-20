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

class BridgeClientTest {

    private lateinit var server: HttpServer
    private var base = ""
    private var statusBody = STATUS
    private var statusCode = 200
    private var resendBody = """{"status":"requested","id":"r123"}"""
    private var resendCode = 200
    private var resendCalls = 0

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/status") { reply(it, statusCode, statusBody) }
        server.createContext("/events") { reply(it, 200, "[]") }
        server.createContext("/resend") { ex ->
            if (ex.requestMethod == "POST") { resendCalls++; reply(ex, resendCode, resendBody) } else reply(ex, 405, "")
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun stop() = server.stop(0)

    private fun reply(ex: HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        ex.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
        ex.close()
    }

    private fun closedPort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun fetchesAndParsesTheStatus() {
        val fetch = BridgeClient.fetchStatus(base)
        assertTrue(fetch.result.ok)
        assertNotNull(fetch.status)
        assertFalse(fetch.missing)
        assertEquals("2.0.0", fetch.status!!.bridgeVersion)
    }

    @Test
    fun anOlderBridgeAnswersWithItsDashboardPage() {
        statusBody = "<html><title>EA FC Telemetry Bridge</title></html>"
        val fetch = BridgeClient.fetchStatus(base)
        assertTrue(fetch.result.ok)
        assertNull(fetch.status)
        assertTrue(fetch.missing)
    }

    @Test
    fun errorsAreClassified() {
        val refused = BridgeClient.get("http://127.0.0.1:${closedPort()}/events", 1500)
        assertFalse(refused.ok)
        assertEquals(FailKind.REFUSED, refused.failKind)

        statusCode = 404
        val http = BridgeClient.fetchStatus(base)
        assertFalse(http.result.ok)
        assertEquals(FailKind.HTTP, http.result.failKind)
        assertEquals(404, http.result.code)
        assertFalse(http.missing)
    }

    @Test
    fun resendReplies() {
        val ok = BridgeClient.requestResend(base)
        assertTrue(ok.ok)
        assertEquals("r123", ok.id)
        assertEquals(1, resendCalls)

        resendCode = 409
        resendBody = """{"status":"error","code":"script_not_running","message":"The capture script is not running."}"""
        val refused = BridgeClient.requestResend(base)
        assertFalse(refused.ok)
        assertEquals("The capture script is not running.", refused.message)

        resendCode = 404
        resendBody = ""
        val old = BridgeClient.requestResend(base)
        assertFalse(old.ok)
        assertTrue(old.message, old.message.contains("too old"))

        val gone = BridgeClient.requestResend("http://127.0.0.1:${closedPort()}", 1500)
        assertFalse(gone.ok)
        assertTrue(gone.message, gone.message.startsWith("Cannot reach the PC"))
    }

    @Test
    fun findsTheBridgeAmongOtherAddresses() {
        val dead = "http://127.0.0.1:${closedPort()}"
        val found = BridgeClient.discover(listOf(dead, base, dead), timeoutMs = 1000)
        assertEquals(listOf(base), found.map { it.base })
        assertNotNull(found[0].status)

        statusBody = "<html>EA FC Telemetry Bridge (old)</html>"
        val old = BridgeClient.discover(listOf(base), timeoutMs = 1000)
        assertEquals(1, old.size)
        assertNull(old[0].status)

        statusBody = "<html>some router page</html>"
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
        const val STATUS = """{"app":"fc26-bridge","now":1,"bridge":{"version":"2.0.0","protocol":2,"minScript":"2.8.0","kind":"python","name":"pc","port":5050,"address":"http://192.168.2.24:5050"},
            "script":{"found":false},"outbox":{"pending":0},"events":{"count":0},"checks":[],"summary":"fail"}"""
    }
}
