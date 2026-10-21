package com.fctracker.career.logic

import com.fctracker.career.telemetry.TelemetryProcessor

/** Which competitions are leagues (the club's own league table) and which are cups. */
object Competitions {

    fun isLeague(competition: String, careerLeague: String): Boolean {
        val c = competition.trim()
        if (c.isEmpty()) return false
        if (c.equals(careerLeague.trim(), ignoreCase = true)) return true
        if (CupRuns.isEuropean(c)) return false
        val l = c.lowercase()
        if (l.contains("cup") || l.contains("copa") || l.contains("coppa") || l.contains("coupe") || l.contains("pokal") || l.contains("shield") ||
            l.contains("super") || l.contains("trophée") || l.contains("trophee") || l.contains("trophy") || l.contains("friendl")
        ) return false
        return TelemetryProcessor.isLeagueCompetition(c) || l.contains("liga") || l.contains("ligue") || l.contains("serie ") || l.contains("eredivisie") || l.contains("bundesliga")
    }

    fun isLeagueFn(careerLeague: String): (String) -> Boolean = { isLeague(it, careerLeague) }
}
