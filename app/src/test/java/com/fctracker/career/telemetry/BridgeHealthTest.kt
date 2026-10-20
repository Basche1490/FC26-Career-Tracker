package com.fctracker.career.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeHealthTest {

    // /status as the bridge (telemetry_bridge.py and bridge.ps1 give the same fields) answers it with the script running.
    private val running = """
        {"app":"fc26-bridge","now":1800000000,
         "bridge":{"version":"2.0.0","protocol":2,"minScript":"2.8.0","kind":"python","name":"gaming-pc","port":5050,"address":"http://192.168.2.24:5050","started":1792459068},
         "script":{"found":true,"folder":"C:\\Users\\Joel\\Documents\\FC26 Export","version":"2.8.0","protocol":2,"time":1799999997,"ageSeconds":3,"alive":true,"versionOk":true,
                   "club":"Pumas","careerDate":"2026-08-09","exporter":true,"scans":12,"lastMatch":1799996400,"lastTransfer":0,"lastSquad":1799999500,
                   "pendingMatches":0,"undelivered":0,
                   "resend":{"id":"r1799999990","time":1799999995,"result":"sent","detail":"Pumas 2-1 Arsenal"}},
         "outbox":{"pending":0,"bad":0,"oldestSeconds":0,"writable":true},
         "events":{"count":1,"lastMatch":1799996400,"lastTransfer":0,"lastSquad":0},
         "phone":{"lastSeen":1799999996,"ageSeconds":4,"ip":"192.168.2.40"},
         "checks":[{"id":"bridge","label":"Bridge","state":"ok","detail":"Version 2.0.0 is running on gaming-pc (port 5050).","fix":""},
                   {"id":"script","label":"Capture script","state":"ok","detail":"Running for Pumas, version 2.8.0.","fix":""}],
         "summary":"ok"}
    """

    private val noScript = """
        {"app":"fc26-bridge","now":1800000000,
         "bridge":{"version":"2.0.0","protocol":2,"minScript":"2.8.0","kind":"powershell","name":"gaming-pc","port":5050,"address":"http://192.168.2.24:5050","started":1},
         "script":{"found":false},"outbox":{"pending":0,"bad":0,"oldestSeconds":0,"writable":true},
         "events":{"count":0,"lastMatch":0,"lastTransfer":0,"lastSquad":0},
         "phone":{"lastSeen":0,"ageSeconds":-1,"ip":""},"checks":[],"summary":"fail"}
    """

    private fun status(json: String) = BridgeStatus.parse(json)!!
    private fun contact(
        status: BridgeStatus? = null,
        reachable: Boolean = true,
        ever: Boolean = true,
        kind: FailKind = FailKind.NONE,
        missing: Boolean = false
    ) = Contact("http://192.168.2.24:5050", reachable, ever, kind, 0, status, missing)

    private fun step(h: Health, id: String) = h.steps.first { it.id == id }

    // ---- parsing -------------------------------------------------------------------------------------------

    @Test
    fun parsesTheBridgesStatus() {
        val s = status(running)
        assertEquals("2.0.0", s.bridgeVersion)
        assertEquals(2, s.protocol)
        assertEquals("2.8.0", s.minScript)
        assertEquals("gaming-pc", s.name)
        assertTrue(s.script.found && s.script.alive && s.script.versionOk && s.script.exporter)
        assertEquals("Pumas", s.script.club)
        assertEquals("2026-08-09", s.script.careerDate)
        assertEquals("sent", s.script.resend!!.result)
        assertEquals("r1799999990", s.script.resend!!.id)
        assertEquals(1799996400L, s.lastMatch)
        assertEquals(2, s.checks.size)
        assertFalse(status(noScript).script.found)
    }

    @Test
    fun anythingElseIsNotAStatus() {
        assertNull(BridgeStatus.parse("<!DOCTYPE html><html>old bridge dashboard</html>"))
        assertNull(BridgeStatus.parse("[]"))
        assertNull(BridgeStatus.parse("""{"app":"something-else","bridge":{}}"""))
        assertNull(BridgeStatus.parse("""{"app":"fc26-bridge"}"""))
        assertNull(BridgeStatus.parse(""))
    }

    @Test
    fun versionCompare() {
        assertTrue(VersionCompare.atLeast("2.8.0", "2.8.0"))
        assertTrue(VersionCompare.atLeast("2.10.0", "2.8.0"))
        assertTrue(VersionCompare.atLeast("v3.0", "2.8.9"))
        assertFalse(VersionCompare.atLeast("2.7.9", "2.8.0"))
        assertFalse(VersionCompare.atLeast("", "2.8.0"))
        assertFalse(VersionCompare.atLeast("beta", "2.8.0"))
        assertFalse(VersionCompare.atLeast("2.8.0", "x"))
    }

    // ---- health ------------------------------------------------------------------------------------------

    @Test
    fun everythingWorkingIsLive() {
        val h = HealthModel.evaluate(contact(status(running)))
        assertEquals(Level.LIVE, h.level)
        assertEquals("Live", h.pill)
        assertTrue(h.steps.all { it.state == StepState.OK })
        assertTrue(h.detail, h.detail.contains("last match 60 min ago"))
        assertEquals(listOf("phone", "bridge", "script", "stats"), h.steps.map { it.id })
    }

    @Test
    fun phoneCannotReachThePc() {
        val lost = HealthModel.evaluate(contact(reachable = false, kind = FailKind.TIMEOUT))
        assertEquals(Level.DOWN, lost.level)
        assertEquals("Offline", lost.pill)
        assertEquals("Can't reach your PC", lost.headline)
        assertEquals(StepState.FAIL, step(lost, "phone").state)
        assertEquals(StepState.UNKNOWN, step(lost, "bridge").state)
        assertEquals(StepState.UNKNOWN, step(lost, "script").state)
        assertTrue(lost.detail.contains("same Wi-Fi"))

        val never = HealthModel.evaluate(contact(reachable = false, ever = false))
        assertEquals("Not connected", never.headline)
        assertTrue(step(never, "phone").fix.contains("Find my PC"))

        assertTrue(HealthModel.failureFix(FailKind.REFUSED, "x").contains("Start the bridge"))
        assertTrue(HealthModel.failureFix(FailKind.NO_ROUTE, "http://1.2.3.4:5050").contains("http://1.2.3.4:5050"))
    }

    @Test
    fun scriptNotRunning() {
        val none = HealthModel.evaluate(contact(status(noScript)))
        assertEquals(Level.DOWN, none.level)
        assertEquals("Script off", none.pill)
        assertEquals(StepState.OK, step(none, "phone").state)
        assertEquals(StepState.OK, step(none, "bridge").state)
        assertEquals(StepState.FAIL, step(none, "script").state)
        assertTrue(none.detail.contains("Execute"))
        assertTrue(none.steps.none { it.id == "stats" })

        val stale = running.replace("\"alive\":true", "\"alive\":false").replace("\"ageSeconds\":3", "\"ageSeconds\":600")
        val h = HealthModel.evaluate(contact(status(stale)))
        assertEquals(Level.DOWN, h.level)
        assertTrue(step(h, "script").detail, step(h, "script").detail.contains("10 min ago"))
    }

    @Test
    fun oldScriptIsAWarningWithTheLauncherAsTheFix() {
        val old = running.replace("\"versionOk\":true", "\"versionOk\":false").replace("\"version\":\"2.8.0\",\"protocol\":2,\"time\"", "\"version\":\"2.7.0\",\"protocol\":2,\"time\"")
        val h = HealthModel.evaluate(contact(status(old)))
        assertEquals(Level.ATTENTION, h.level)
        assertEquals("Check", h.pill)
        assertEquals(StepState.WARN, step(h, "script").state)
        assertTrue(step(h, "script").detail.contains("2.7.0"))
        assertTrue(h.detail.contains("FC26_Launcher.bat"))
    }

    @Test
    fun missingExporterIsAWarningAboutEstimatedStats() {
        val h = HealthModel.evaluate(contact(status(running.replace("\"exporter\":true", "\"exporter\":false"))))
        assertEquals(Level.ATTENTION, h.level)
        assertEquals(StepState.WARN, step(h, "stats").state)
        assertTrue(step(h, "stats").detail.contains("estimated"))
    }

    @Test
    fun anOlderBridgeStillWorksButSaysSo() {
        val noStatusPage = HealthModel.evaluate(contact(null, missing = true))
        assertEquals(Level.ATTENTION, noStatusPage.level)
        assertEquals(StepState.WARN, step(noStatusPage, "bridge").state)
        assertEquals(StepState.UNKNOWN, step(noStatusPage, "script").state)
        assertTrue(noStatusPage.detail.contains("FC26_Launcher.bat"))

        val protocol1 = running.replace("\"protocol\":2,\"minScript\"", "\"protocol\":1,\"minScript\"")
        val h = HealthModel.evaluate(contact(status(protocol1)))
        assertEquals(StepState.WARN, step(h, "bridge").state)
    }

    @Test
    fun spans() {
        assertEquals("5s", HealthModel.span(5))
        assertEquals("2 min", HealthModel.span(120))
        assertEquals("3 h", HealthModel.span(3 * 3600 + 10))
        assertEquals("2 days", HealthModel.span(2 * 86400 + 5))
        assertEquals("0s", HealthModel.span(-4))
        assertNotNull(HealthModel.ago(4))
    }
}
