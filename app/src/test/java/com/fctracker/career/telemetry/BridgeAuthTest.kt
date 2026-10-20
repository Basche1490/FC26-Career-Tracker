package com.fctracker.career.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pairing code and the signatures. The numbers below are tests/vectors/auth_vectors.json, which the Python and the
 * PowerShell bridge are checked against too (tests/bridge/test_bridge_auth.py checks that every one of them is here).
 */
class BridgeAuthTest {

    private class Req(val method: String, val target: String, val time: Long, val nonce: String, val body: String, val sig: String)
    private class Resp(val nonce: String, val status: Int, val body: String, val sig: String)

    private val code = "ABCD-EFGH-JKLM-NPQR"
    private val keyHex = "6032136b87342f87de8510ebc86d8f1c75b7368ae12cea8f325e5bff6cff4af8"
    private val requests = listOf(
        Req("GET", "/events", 1800000000L, "00112233445566778899aabbccddeeff", "", "90ace0b76324021aa141b43c29875ef665463f97e1cf576e47c0aa4a09ba0fe0"),
        Req("POST", "/resend", 1800000123L, "ffeeddccbbaa99887766554433221100", "", "104177cd5c25ff82170df68bd13f6c48edda4cd2fc1c002734ceb90300bd5581"),
        Req("POST", "/resend", 1800000123L, "ffeeddccbbaa99887766554433221100", "{\"a\":1}", "05e01dc433c9e9b4d054702a4537f0ce7bca1627505ceb773e670828cd0693ac"),
    )
    private val responses = listOf(
        Resp("00112233445566778899aabbccddeeff", 200, "[]", "67635238756838c231448bad6d351aa0b5f23ff8ca2c2f31377b4c586d622842"),
        Resp("00112233445566778899aabbccddeeff", 200, "{\"status\":\"requested\",\"id\":\"r1\"}", "d9e8016114c08f37ed985ab9eafe1f4109cd1d0308d69f420630f469cd9d52b8"),
        Resp("00112233445566778899aabbccddeeff", 409, "{\"status\":\"error\"}", "d91cc48526fd2f65c12e5484be94d0391825d273b4612bf93f5af5b83223b5da"),
    )

    @Test
    fun theKeyAndTheSignaturesMatchTheSharedVectors() {
        assertEquals(keyHex, BridgeAuth.keyHex(code))
        val auth = BridgeAuth.fromCode(code)!!
        for (r in requests) assertEquals(r.sig, auth.signRequest(r.method, r.target, r.time, r.nonce, r.body.toByteArray(Charsets.UTF_8)))
        for (r in responses) assertEquals(r.sig, auth.signResponse(r.nonce, r.status, r.body.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun aCodeMayBeTypedInAnyCaseWithOrWithoutDashes() {
        assertEquals("ABCDEFGHJKLMNPQR", BridgeAuth.normalize("abcd efgh-jklm npqr"))
        assertEquals(keyHex, BridgeAuth.keyHex("abcd efgh jklm npqr"))
        assertEquals("ABCD-EFGH-JKLM-NPQR", BridgeAuth.format("abcdefghjklmnpqr"))
        assertEquals("ABCD-EF", BridgeAuth.format("abcdef"))
    }

    @Test
    fun onlyRealPairingCodesAreAccepted() {
        assertTrue(BridgeAuth.isValid("ABCD-EFGH-JKLM-NPQR"))
        for (bad in listOf("", "ABCD", "ABCD-EFGH-JKLM-NPQ", "ABCD-EFGH-JKLM-NPQRS", "ABCD-EFGH-JKLM-NPQ0", "ABCD-EFGH-JKLM-NPQI", "ABCD-EFGH-JKLM-NPQO")) {
            assertFalse(bad, BridgeAuth.isValid(bad))
            assertNull(bad, BridgeAuth.fromCode(bad))
        }
        assertNull(BridgeAuth.fromCode(null))
    }

    @Test
    fun theAnswerIsCheckedAgainstTheOneTimeNumberTheStatusAndTheBody() {
        val auth = BridgeAuth.fromCode(code)!!
        val r = responses[0]
        val body = r.body.toByteArray(Charsets.UTF_8)
        assertTrue(auth.verifyResponse(r.nonce, r.status, body, r.sig))
        assertTrue(auth.verifyResponse(r.nonce, r.status, body, r.sig.uppercase()))
        assertFalse(auth.verifyResponse(r.nonce, r.status, body, null))
        assertFalse(auth.verifyResponse(r.nonce, r.status, body, ""))
        assertFalse(auth.verifyResponse(r.nonce, 500, body, r.sig))
        assertFalse(auth.verifyResponse("ffffffffffffffffffffffffffffffff", r.status, body, r.sig))
        assertFalse(auth.verifyResponse(r.nonce, r.status, "[{}]".toByteArray(), r.sig))
        assertFalse(BridgeAuth.fromCode("ZZZZ-ZZZZ-ZZZZ-ZZZZ")!!.verifyResponse(r.nonce, r.status, body, r.sig))
    }

    @Test
    fun everyRequestGetsAFreshOneTimeNumber() {
        val auth = BridgeAuth.fromCode(code)!!
        val a = auth.sign("GET", "/events")
        val b = auth.sign("GET", "/events")
        assertNotEquals(a.nonce, b.nonce)
        assertEquals(32, a.nonce.length)
        assertTrue(a.nonce.all { it in "0123456789abcdef" })
        assertNotNull(a.signature)
        assertEquals(auth.signRequest("GET", "/events", a.time, a.nonce), a.signature)
        assertTrue(Math.abs(System.currentTimeMillis() / 1000 - a.time) < 5)
    }
}
