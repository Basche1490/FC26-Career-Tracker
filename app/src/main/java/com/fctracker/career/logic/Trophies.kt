package com.fctracker.career.logic

import com.fctracker.career.AppState
import com.fctracker.career.TeamTrophy

/**
 * Trophies the capture script reports (a league the club finished top of, a cup final it won). Each one is added once:
 * a trophy already on the list for that competition and season (typed by the person or added earlier) is left alone.
 */
object Trophies {

    /** "FA Cup 2026/27": the season in the name is what the recap, the season list and the season archive look for. */
    fun name(competition: String, startYear: Int): String = "${competition.trim()} ${SeasonView.seasonLabel(startYear)}"

    private val SEASON = Regex("""\(?\b(19|20)\d{2}(\s*[/-]\s*\d{2,4})?\b\)?""")
    private val FILLER = Regex("""\b(champions?|winners?|title|trophy won)\b""", RegexOption.IGNORE_CASE)

    private fun plain(text: String): String =
        FILLER.replace(SEASON.replace(text.lowercase(), " "), " ").replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun sameSeason(name: String, startYear: Int): Boolean {
        val n = name.lowercase()
        return n.contains(SeasonView.seasonLabel(startYear)) || n.contains("($startYear)") || n.contains("$startYear/") || n.contains("$startYear-")
    }

    /** True when [names] already holds this competition's trophy for that season ("FA Cup 2026/27" ≈ "Emirates FA Cup"). */
    fun has(names: List<String>, competition: String, startYear: Int): Boolean {
        val comp = plain(competition)
        if (comp.isEmpty()) return true
        return names.any { n ->
            val base = plain(n)
            sameSeason(n, startYear) && base.isNotEmpty() && (base.contains(comp) || (base.length >= 4 && comp.contains(base)))
        }
    }

    data class Added(val state: AppState, val names: List<String>)

    /** Adds the trophies in [won] (competition, season start year) that are not on the list yet. */
    fun addFromGame(state: AppState, won: List<Pair<String, Int>>): Added {
        var s = state
        val added = mutableListOf<String>()
        for ((competition, year) in won) {
            if (competition.isBlank() || year < 1900) continue
            val trophy = name(competition, year)
            if (year >= s.startYear) {
                if (!has(s.teamTrophies.map { it.name }, competition, year)) {
                    s = s.copy(teamTrophies = s.teamTrophies + TeamTrophy(name = trophy))
                    added += trophy
                }
            } else {
                // A season that is already archived (the season was ended in the app before the game's report came);
                // a season this career has no record of is skipped.
                val idx = s.historicalSeasons.indexOfFirst { it.year == year }
                if (idx < 0) continue
                val h = s.historicalSeasons[idx]
                if (!has(h.trophies + s.teamTrophies.map { it.name }, competition, year)) {
                    s = s.copy(historicalSeasons = s.historicalSeasons.toMutableList().also { it[idx] = h.copy(trophies = h.trophies + trophy) })
                    added += trophy
                }
            }
        }
        return Added(s, added)
    }
}
