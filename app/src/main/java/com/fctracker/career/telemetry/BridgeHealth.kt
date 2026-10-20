package com.fctracker.career.telemetry

import org.json.JSONObject

/** Version numbers like "2.8.0" (a leading "v" and a missing patch number are fine). */
object VersionCompare {
    fun parse(text: String?): List<Int>? {
        val m = Regex("^\\s*v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?").find(text.orEmpty()) ?: return null
        return try {
            listOf(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].ifEmpty { "0" }.toInt())
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** True when [have] is [need] or newer. An unreadable version is never "at least" anything. */
    fun atLeast(have: String?, need: String?): Boolean {
        val h = parse(have) ?: return false
        val n = parse(need) ?: return false
        for (i in 0..2) {
            if (h[i] != n[i]) return h[i] > n[i]
        }
        return true
    }
}

/** What the capture script (inside Live Editor) says about itself, as the bridge relays it. */
class ScriptState(
    val found: Boolean,
    val alive: Boolean = false,
    val version: String = "",
    val versionOk: Boolean = false,
    val club: String = "",
    val careerDate: String = "",
    val exporter: Boolean = false,
    val ageSeconds: Long = 0,
    val undelivered: Int = 0,
    val resend: ResendResult? = null
)

/** The script's answer to the last "re-send the latest match" request. result: sent | nothing | error. */
class ResendResult(val id: String, val result: String, val detail: String)

/** One line of the bridge's own self check. state: ok | warn | fail | info. */
class BridgeCheck(val id: String, val label: String, val state: String, val detail: String, val fix: String)

/** The bridge's /status answer. */
class BridgeStatus(
    val now: Long,
    val bridgeVersion: String,
    val protocol: Int,
    val minScript: String,
    val kind: String,
    val name: String,
    val address: String,
    val script: ScriptState,
    val outboxPending: Int,
    val lastMatch: Long,
    val lastTransfer: Long,
    val lastSquad: Long,
    val checks: List<BridgeCheck>,
    val summary: String
) {
    companion object {
        /** Null when [body] is not a status report of this app's bridge (an old bridge answers /status with a web page). */
        fun parse(body: String): BridgeStatus? {
            val json = try { JSONObject(body.trim()) } catch (e: Exception) { return null }
            if (json.optString("app") != "fc26-bridge") return null
            val bridge = json.optJSONObject("bridge") ?: return null
            val s = json.optJSONObject("script")
            val script = if (s == null || !s.optBoolean("found", false)) ScriptState(found = false) else {
                val r = s.optJSONObject("resend")
                ScriptState(
                    found = true,
                    alive = s.optBoolean("alive", false),
                    version = s.optString("version"),
                    versionOk = s.optBoolean("versionOk", false),
                    club = s.optString("club"),
                    careerDate = s.optString("careerDate"),
                    exporter = s.optBoolean("exporter", false),
                    ageSeconds = s.optLong("ageSeconds", 0),
                    undelivered = s.optInt("undelivered", 0),
                    resend = if (r == null) null else ResendResult(r.optString("id"), r.optString("result"), r.optString("detail"))
                )
            }
            val events = json.optJSONObject("events")
            val outbox = json.optJSONObject("outbox")
            val checks = mutableListOf<BridgeCheck>()
            val list = json.optJSONArray("checks")
            if (list != null) {
                for (i in 0 until list.length()) {
                    val c = list.optJSONObject(i) ?: continue
                    checks.add(BridgeCheck(c.optString("id"), c.optString("label"), c.optString("state"), c.optString("detail"), c.optString("fix")))
                }
            }
            return BridgeStatus(
                now = json.optLong("now", 0),
                bridgeVersion = bridge.optString("version"),
                protocol = bridge.optInt("protocol", 0),
                minScript = bridge.optString("minScript"),
                kind = bridge.optString("kind"),
                name = bridge.optString("name"),
                address = bridge.optString("address"),
                script = script,
                outboxPending = outbox?.optInt("pending", 0) ?: 0,
                lastMatch = events?.optLong("lastMatch", 0) ?: 0,
                lastTransfer = events?.optLong("lastTransfer", 0) ?: 0,
                lastSquad = events?.optLong("lastSquad", 0) ?: 0,
                checks = checks,
                summary = json.optString("summary", "ok")
            )
        }
    }
}

/**
 * Why the phone could not talk to the bridge. The last six are about the pairing code: [UNPAIRED] no code on the phone,
 * [BAD_CODE] the PC refused it, [CLOCK] phone and PC clocks differ by more than 5 minutes, [LOCKED] too many wrong
 * attempts, [OLD_BRIDGE] a bridge from before pairing codes, [UNTRUSTED] an answer without the right signature (not
 * the bridge that knows the code, so whatever it said was ignored).
 */
enum class FailKind { NONE, REFUSED, TIMEOUT, NO_ROUTE, HTTP, OTHER, UNPAIRED, BAD_CODE, CLOCK, LOCKED, OLD_BRIDGE, UNTRUSTED }

/** The kinds that a new try in ten seconds will not fix: the phone then asks less often until something changes. */
val PAIRING_PROBLEMS = setOf(FailKind.UNPAIRED, FailKind.BAD_CODE, FailKind.CLOCK, FailKind.LOCKED, FailKind.OLD_BRIDGE, FailKind.UNTRUSTED)

/** What the phone knows about its contact with the bridge right now (the input of [HealthModel]). */
class Contact(
    val address: String,
    val reachable: Boolean,
    /** True once the bridge has answered at least once since the address was set. */
    val everConnected: Boolean,
    val failKind: FailKind = FailKind.NONE,
    val httpCode: Int = 0,
    /** Null when the bridge could not be asked, or when it is an older one without a /status page. */
    val status: BridgeStatus? = null,
    /** True when the bridge answered the event list but has no health report: an older bridge. */
    val statusMissing: Boolean = false,
    /** For [FailKind.CLOCK]: how many seconds the PC's clock is ahead of the phone's (negative: behind). */
    val clockOffsetSeconds: Long = 0
)

enum class Level { LIVE, ATTENTION, DOWN }
enum class StepState { OK, WARN, FAIL, UNKNOWN }

class HealthStep(val id: String, val title: String, val state: StepState, val detail: String, val fix: String = "")

/** [pill] is the one or two words shown on the status pill on every screen. */
class Health(val level: Level, val pill: String, val headline: String, val detail: String, val steps: List<HealthStep>)

/**
 * Turns "can the phone reach the PC, is the bridge new enough, is the capture script running" into one answer with
 * steps, in the order the data travels: Phone, then Bridge, then Capture script.
 */
object HealthModel {
    /** The bridge protocol this version of the app needs: 3 is the one with the pairing code. */
    const val REQUIRED_BRIDGE_PROTOCOL = 3

    const val CODE_WHERE = "The code is shown in the launcher window on the PC and on the bridge page (open http://localhost:5050 on the PC)."

    const val UPDATE_HINT = "Run FC26_Launcher.bat on the PC: it updates the bridge and the capture script."

    fun span(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return when {
            s < 90 -> "${s}s"
            s < 5400 -> "${s / 60} min"
            s < 172800 -> "${s / 3600} h"
            else -> "${s / 86400} days"
        }
    }

    fun ago(seconds: Long): String = span(seconds) + " ago"

    /** What to try, for each way the connection can fail. */
    fun failureFix(kind: FailKind, address: String): String = when (kind) {
        FailKind.REFUSED -> "The PC answered but nothing is listening on that port. Start the bridge (FC26_Launcher.bat) and keep its window open."
        FailKind.TIMEOUT -> "No answer. Check that the PC is on, the phone is on the same Wi-Fi, and that Windows allows the bridge (the launcher sets this up)."
        FailKind.NO_ROUTE -> "The phone cannot find $address. Join the same Wi-Fi as the PC, or tap Find my PC."
        FailKind.HTTP -> "Something answered, but it is not the bridge. Check the address, or tap Find my PC."
        FailKind.UNPAIRED -> "Type the pairing code in PC Connection. $CODE_WHERE"
        FailKind.BAD_CODE -> "Check the pairing code in PC Connection. If a new code was made on the PC, type the new one. $CODE_WHERE"
        FailKind.CLOCK -> "Set the clock of the PC (or the phone) to the right time: Windows Settings > Time & language > Set time automatically."
        FailKind.LOCKED -> "The PC stopped answering this phone for a minute after several wrong codes. Check the code, then wait a minute."
        FailKind.OLD_BRIDGE -> UPDATE_HINT
        FailKind.UNTRUSTED -> "Do not use this address: the device there does not know your pairing code. Tap Find my PC, or type the address the launcher shows."
        else -> "Check the address, or tap Find my PC."
    }

    private fun clockText(offset: Long): String = when {
        offset > 0 -> "The PC's clock is ${span(offset)} ahead of the phone's."
        offset < 0 -> "The PC's clock is ${span(-offset)} behind the phone's."
        else -> "The clocks of the phone and the PC differ by more than 5 minutes."
    }

    /** The pairing step: is the bridge the one that knows the code, and does it accept this phone? */
    private fun pairingStep(contact: Contact): HealthStep = when {
        !contact.reachable -> HealthStep("pairing", "Pairing code", StepState.UNKNOWN, "Waiting for the connection.")
        contact.failKind == FailKind.UNPAIRED -> HealthStep(
            "pairing", "Pairing code", StepState.FAIL, "No pairing code entered yet.", failureFix(FailKind.UNPAIRED, contact.address)
        )
        contact.failKind == FailKind.BAD_CODE -> HealthStep(
            "pairing", "Pairing code", StepState.FAIL, "The PC did not accept the code.", failureFix(FailKind.BAD_CODE, contact.address)
        )
        contact.failKind == FailKind.CLOCK -> HealthStep(
            "pairing", "Pairing code", StepState.FAIL, clockText(contact.clockOffsetSeconds), failureFix(FailKind.CLOCK, contact.address)
        )
        contact.failKind == FailKind.LOCKED -> HealthStep(
            "pairing", "Pairing code", StepState.FAIL, "Too many wrong attempts.", failureFix(FailKind.LOCKED, contact.address)
        )
        contact.failKind == FailKind.OLD_BRIDGE -> HealthStep(
            "pairing", "Pairing code", StepState.FAIL, "This bridge is from before pairing codes and cannot be paired.", UPDATE_HINT
        )
        contact.failKind == FailKind.UNTRUSTED -> HealthStep(
            "pairing", "Pairing code", StepState.FAIL, "The answer was not signed with your code, so it was ignored.",
            failureFix(FailKind.UNTRUSTED, contact.address)
        )
        else -> HealthStep("pairing", "Pairing code", StepState.OK, "Paired: every answer from the PC is checked.")
    }

    fun evaluate(contact: Contact): Health {
        val steps = mutableListOf<HealthStep>()

        // 1. Phone -> PC
        val phoneStep = when {
            contact.reachable -> HealthStep("phone", "Phone to PC", StepState.OK, "Connected to ${contact.address}")
            contact.everConnected -> HealthStep(
                "phone", "Phone to PC", StepState.FAIL, "Lost contact with ${contact.address}.",
                failureFix(contact.failKind, contact.address)
            )
            else -> HealthStep(
                "phone", "Phone to PC", StepState.FAIL, "Not connected to a PC yet.",
                if (contact.failKind == FailKind.NONE) "Tap Find my PC, or type the address the bridge shows on the PC."
                else failureFix(contact.failKind, contact.address)
            )
        }
        steps.add(phoneStep)

        // 2. Pairing code
        val pairing = pairingStep(contact)
        steps.add(pairing)
        val paired = pairing.state == StepState.OK

        // 3. Bridge
        val status = contact.status
        val bridgeStep = when {
            !contact.reachable -> HealthStep("bridge", "PC bridge", StepState.UNKNOWN, "Waiting for the connection.")
            !paired -> HealthStep("bridge", "PC bridge", StepState.UNKNOWN, "Cannot be checked until the phone is paired.")
            status == null || contact.statusMissing -> HealthStep(
                "bridge", "PC bridge", StepState.WARN, "An older bridge: it works, but it cannot report the script's health.", UPDATE_HINT
            )
            status.protocol < REQUIRED_BRIDGE_PROTOCOL -> HealthStep(
                "bridge", "PC bridge", StepState.WARN, "Version ${status.bridgeVersion} is older than this app expects.", UPDATE_HINT
            )
            else -> HealthStep(
                "bridge", "PC bridge", StepState.OK,
                "Version ${status.bridgeVersion}" + if (status.name.isNotBlank()) " on ${status.name}" else ""
            )
        }
        steps.add(bridgeStep)

        // 4. Capture script
        val script = status?.script
        val scriptStep = when {
            !contact.reachable || !paired || status == null || contact.statusMissing ->
                HealthStep("script", "Capture script", StepState.UNKNOWN, "Cannot be checked yet.")
            script == null || !script.found -> HealthStep(
                "script", "Capture script", StepState.FAIL, "The script has not reported yet.",
                "In Live Editor open your career, then Lua Scripts > FC26_Auto_Capture.lua > Execute."
            )
            !script.alive -> HealthStep(
                "script", "Capture script", StepState.FAIL, "Last heard from ${ago(script.ageSeconds)}.",
                "Live Editor was closed or the script stopped. Execute FC26_Auto_Capture.lua again."
            )
            !script.versionOk -> HealthStep(
                "script", "Capture script", StepState.WARN,
                "Version ${script.version.ifBlank { "?" }} is older than ${status.minScript}.", UPDATE_HINT
            )
            else -> HealthStep(
                "script", "Capture script", StepState.OK,
                "Running" + (if (script.club.isNotBlank()) " for ${script.club}" else "") + ", version ${script.version}"
            )
        }
        steps.add(scriptStep)

        // 5. Match statistics (only when the script runs)
        if (paired && script != null && script.found && script.alive) {
            steps.add(
                if (script.exporter) HealthStep("stats", "Match statistics", StepState.OK, "Project Atlas exporter is running.")
                else HealthStep(
                    "stats", "Match statistics", StepState.WARN,
                    "The Project Atlas exporter is not running: matches arrive with estimated statistics.",
                    "Execute the Project Atlas exporter script in Live Editor as well."
                )
            )
        }

        val firstFail = steps.firstOrNull { it.state == StepState.FAIL }
        val firstWarn = steps.firstOrNull { it.state == StepState.WARN }
        return when {
            phoneStep.state == StepState.FAIL -> Health(
                Level.DOWN, "Offline",
                if (contact.everConnected) "Can't reach your PC" else "Not connected",
                phoneStep.fix.ifBlank { phoneStep.detail },
                steps
            )
            !paired -> Health(
                Level.DOWN, if (contact.failKind == FailKind.OLD_BRIDGE) "Update PC" else "Not paired",
                "Pairing: ${pairing.detail.trimEnd('.')}", pairing.fix, steps
            )
            firstFail != null -> Health(
                Level.DOWN, "Script off", "${firstFail.title}: ${firstFail.detail.trimEnd('.')}", firstFail.fix, steps
            )
            firstWarn != null -> Health(Level.ATTENTION, "Check", firstWarn.detail.trimEnd('.'), firstWarn.fix, steps)
            else -> Health(Level.LIVE, "Live", "Live", liveDetail(status), steps)
        }
    }

    /** "Last match 2 h ago" etc. for the all-clear line. */
    private fun liveDetail(status: BridgeStatus?): String {
        if (status == null) return "Watching your PC."
        val parts = mutableListOf<String>()
        if (status.lastMatch > 0 && status.now >= status.lastMatch) parts.add("last match ${ago(status.now - status.lastMatch)}")
        if (status.lastTransfer > 0 && status.now >= status.lastTransfer) parts.add("last transfer ${ago(status.now - status.lastTransfer)}")
        return if (parts.isEmpty()) "Watching your PC. Nothing has arrived yet." else "Watching your PC; " + parts.joinToString(", ")
    }
}
