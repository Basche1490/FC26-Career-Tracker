package com.fctracker.career.telemetry

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The pairing code and the signing rules the phone and the PC bridge share (written up in docs/SECURITY.md).
 *
 * The bridge shows a pairing code (16 letters and digits, 80 bits of randomness). The phone derives a key from it and
 * signs every request with HMAC-SHA256 over the method, the path, the time, a one-time number and the body. The
 * bridge signs its answer over the request's one-time number, the status and the body. Whoever does not know the code
 * can neither ask the bridge for anything nor make the phone believe an answer.
 *
 * Plain JVM code (no Android classes) so it is tested on any computer against the same vectors as the bridges.
 */
class BridgeAuth private constructor(private val key: ByteArray) {

    /** The three headers to add to a request. [nonce] is needed again to check the answer. */
    class Signed(val time: Long, val nonce: String, val signature: String)

    fun signRequest(method: String, target: String, time: Long, nonce: String, body: ByteArray = ByteArray(0)): String =
        hmacHex(key, listOf("FC26-REQ-V1", method.uppercase(), target, time.toString(), nonce, sha256Hex(body)).joinToString("\n"))

    fun signResponse(nonce: String, status: Int, body: ByteArray): String =
        hmacHex(key, listOf("FC26-RESP-V1", nonce, status.toString(), sha256Hex(body)).joinToString("\n"))

    /** Signs a request now (or at [time]) with a fresh one-time number. */
    fun sign(method: String, target: String, body: ByteArray = ByteArray(0), time: Long = System.currentTimeMillis() / 1000, nonce: String = newNonce()): Signed =
        Signed(time, nonce, signRequest(method, target, time, nonce, body))

    /** True when [signature] is the bridge's signature of this answer. Compares without stopping at the first difference. */
    fun verifyResponse(nonce: String, status: Int, body: ByteArray, signature: String?): Boolean {
        if (signature.isNullOrBlank()) return false
        val expected = signResponse(nonce, status, body)
        return MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), signature.trim().lowercase().toByteArray(Charsets.US_ASCII))
    }

    companion object {
        /** No I, O, 0 or 1: nothing to mix up when typing. */
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val LENGTH = 16
        private const val KEY_LABEL = "fc26-bridge-v1:"
        private val random = SecureRandom()

        const val HEADER_TIME = "X-FC-Time"
        const val HEADER_NONCE = "X-FC-Nonce"
        const val HEADER_SIGNATURE = "X-FC-Sig"

        /** What was typed, without spaces and dashes, in capitals: "abcd-efgh ..." becomes "ABCDEFGH...". */
        fun normalize(text: String?): String = text.orEmpty().filter { it.isLetterOrDigit() }.uppercase()

        fun isValid(text: String?): Boolean {
            val code = normalize(text)
            return code.length == LENGTH && code.all { it in ALPHABET }
        }

        /** "ABCD-EFGH-JKLM-NPQR": the code in groups of four, as the bridge shows it. */
        fun format(text: String?): String = normalize(text).chunked(4).joinToString("-")

        /** The signer for a typed code, or null when the code is not a valid pairing code. */
        fun fromCode(text: String?): BridgeAuth? {
            if (!isValid(text)) return null
            return BridgeAuth(sha256(((KEY_LABEL + normalize(text)).toByteArray(Charsets.US_ASCII))))
        }

        /** 16 random bytes as 32 hex characters: never used twice. */
        fun newNonce(): String {
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            return hex(bytes)
        }

        fun keyHex(text: String?): String? = fromCode(text)?.let { hex(it.key) }

        private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

        fun sha256Hex(data: ByteArray): String = hex(sha256(data))

        private fun hmacHex(key: ByteArray, text: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return hex(mac.doFinal(text.toByteArray(Charsets.UTF_8)))
        }

        private fun hex(bytes: ByteArray): String {
            val out = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                val v = b.toInt() and 0xff
                out.append("0123456789abcdef"[v shr 4]).append("0123456789abcdef"[v and 15])
            }
            return out.toString()
        }
    }
}
