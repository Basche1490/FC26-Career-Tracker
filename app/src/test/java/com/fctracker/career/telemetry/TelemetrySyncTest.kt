package com.fctracker.career.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetrySyncTest {

    @Test
    fun bridgeAddressesAreTidiedUp() {
        val n = TelemetrySync.Companion::normalizeBridgeUrl
        assertEquals("http://192.168.2.24:5050", n(""))
        assertEquals("http://192.168.2.24:5050", n("   "))
        assertEquals("http://192.168.1.50:5050", n("192.168.1.50"))
        assertEquals("http://192.168.1.50:5050", n("http://192.168.1.50"))
        assertEquals("http://192.168.1.50:8080", n("192.168.1.50:8080"))
        assertEquals("http://192.168.1.50:5050", n("http://192.168.1.50:5050/events"))
        assertEquals("http://192.168.1.50:5050", n("http://192.168.1.50:5050/"))
        assertEquals("https://pc.local:5050", n("https://pc.local:5050/api/telemetry"))
    }

    @Test
    fun anEventArrayWithOneBadEntryKeepsTheGoodOnes() {
        val events = TelemetrySync.parseEvents("""[{"id":1,"title":"a"}, 42, {"id":2,"title":"b"}]""")
        assertEquals(listOf("a", "b"), events.map { it.getString("title") })
    }

    @Test
    fun oneJsonObjectPerLineIsAccepted() {
        val events = TelemetrySync.parseEvents("{\"title\":\"a\"}\n\nnot json\n{\"title\":\"b\"}\n")
        assertEquals(listOf("a", "b"), events.map { it.getString("title") })
    }

    @Test
    fun anEmptyBridgeGivesNoEvents() {
        assertEquals(0, TelemetrySync.parseEvents("[]").size)
        assertEquals(0, TelemetrySync.parseEvents("").size)
    }
}
