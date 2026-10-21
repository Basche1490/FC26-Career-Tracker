package com.fctracker.career.logic

import com.fctracker.career.Source
import com.fctracker.career.WorldTopScorer
import com.fctracker.career.WorldWinner

/**
 * Around the World: the winners of the top five leagues, their cups and super cups, and the European cups, for the
 * season that just finished. The page changes once a year, in June (after the last finals, before the new season);
 * international and women's competitions are left out.
 */
object WorldWinners {

    val REGIONS = listOf("Europe", "England", "Spain", "Italy", "Germany", "France")

    private val EXCLUDED = listOf(
        "world cup", "nations league", "international", "women", "féminine", "feminine", "frauen", "femenina", "femminile",
        "copa américa", "copa america", "africa cup", "asian cup", "gold cup", "olympic", "friendly", "u21", "u-21", "u19", "u-19",
        "u17", "youth", "uefa euro", "european championship", "euro 20", "finalissima", "club world cup"
    )

    /** True for a competition the page shows; false for internationals, women's football and youth competitions. */
    fun isShown(competition: String): Boolean {
        val c = competition.trim().lowercase()
        if (c.isEmpty()) return false
        if (EXCLUDED.any { c.contains(it) }) return false
        // "European International Cup" is a pre-season club tournament, not a European cup.
        if (c.contains("european") && c.contains("cup") && !c.contains("uefa")) return false
        return true
    }

    /**
     * The season a report from the game belongs to, or null outside the update window. In June and July the game's
     * "holder" of each competition is the winner of the season that just finished (start year = this year - 1).
     */
    fun seasonForUpdate(careerDate: String?): Int? {
        val d = Dates.parse(careerDate) ?: return null
        return if (d.month == 6 || d.month == 7) d.year - 1 else null
    }

    /** When the next update is due ("June 2027"), for the note on the page. */
    fun nextUpdate(careerDate: String?, currentSeason: Int): String {
        val d = Dates.parse(careerDate)
        val year = if (d != null && d.month <= 6) d.year else currentSeason + 1
        return "June $year"
    }

    /** The latest season with winners, or null before the first June. */
    fun latestSeason(winners: List<WorldWinner>): Int? = winners.maxOfOrNull { it.season }

    /**
     * Merges a report into the stored list: the game's entries for that season replace its earlier ones; entries the
     * person typed are kept unless the game now reports the same competition.
     */
    fun merge(existing: List<WorldWinner>, incoming: List<WorldWinner>, season: Int): List<WorldWinner> {
        val shown = incoming.filter { isShown(it.competition) && it.winner.isNotBlank() }.map { it.copy(season = season, source = Source.GAME) }
        val incomingComps = shown.map { it.competition.lowercase() }.toSet()
        val kept = existing.filterNot { it.season == season && (it.source == Source.GAME || it.competition.lowercase() in incomingComps) }
        return kept + shown
    }

    fun mergeScorers(existing: List<WorldTopScorer>, incoming: List<WorldTopScorer>, season: Int): List<WorldTopScorer> {
        val shown = incoming.filter { it.player.isNotBlank() }.map { it.copy(season = season, source = Source.GAME) }
        val regions = shown.map { it.region.lowercase() }.toSet()
        return existing.filterNot { it.season == season && (it.source == Source.GAME || it.region.lowercase() in regions) } + shown
    }

    private fun rank(w: WorldWinner): Int {
        val c = w.competition.lowercase()
        return when {
            w.isLeague -> 0
            c.contains("champions league") -> 0
            c.contains("europa league") -> 1
            c.contains("conference league") -> 2
            c.contains("super") || c.contains("shield") || c.contains("trophée") || c.contains("trophee") -> 4
            else -> 3
        }
    }

    /** The winners of one season by region, in the page's order (Europe first, then the five leagues, then others). */
    fun byRegion(winners: List<WorldWinner>, season: Int): List<Pair<String, List<WorldWinner>>> {
        val ofSeason = winners.filter { it.season == season && isShown(it.competition) }
        val regions = (REGIONS + ofSeason.map { it.region }).distinct()
        return regions.mapNotNull { r ->
            val list = ofSeason.filter { it.region.equals(r, ignoreCase = true) }.sortedWith(compareBy({ rank(it) }, { it.competition }))
            if (list.isEmpty()) null else r to list
        }
    }

    /** The detail line under a winner: "2–1 v Arsenal · 5–4 pens", "84 pts · Liverpool 2nd", or "Winner · no final score kept". */
    fun detail(w: WorldWinner): String {
        if (w.isLeague) return w.detail
        val base = when {
            w.score.isBlank() -> "Winner · no final score kept"
            w.runnerUp.isNotBlank() -> "${w.score} v ${w.runnerUp}"
            else -> w.score
        }
        return if (w.penalties.isNotBlank()) "$base · ${w.penalties} pens" else base
    }
}
