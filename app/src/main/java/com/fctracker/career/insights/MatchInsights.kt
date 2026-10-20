package com.fctracker.career.insights

import com.fctracker.career.MatchResult

/** The result chips above the match list. */
enum class ResultFilter(val label: String) {
    ALL("All"),
    WINS("Wins"),
    DRAWS("Draws"),
    LOSSES("Losses"),
    /** Matches the game sent without full detail: the app filled the gaps with typical values. */
    ESTIMATED("Estimated")
}

/** Everything the match history screen works out from a list of matches, with nothing Android in it. */
object MatchInsights {

    const val ALL_COMPETITIONS = "All"

    /** "All" followed by every competition that has a match, A to Z. */
    fun competitions(matches: List<MatchResult>): List<String> =
        listOf(ALL_COMPETITIONS) + matches.map { it.competition }.filter { it.isNotBlank() }.distinct().sorted()

    fun byCompetition(matches: List<MatchResult>, competition: String): List<MatchResult> =
        if (competition == ALL_COMPETITIONS) matches else matches.filter { it.competition == competition }

    fun fits(match: MatchResult, filter: ResultFilter): Boolean = when (filter) {
        ResultFilter.ALL -> true
        ResultFilter.WINS -> match.resultBadge == "W"
        ResultFilter.DRAWS -> match.resultBadge == "D"
        ResultFilter.LOSSES -> match.resultBadge == "L"
        ResultFilter.ESTIMATED -> match.statsEstimated
    }

    /** The matches of one competition (or all) that also fit the result chip. The list keeps its order. */
    fun filter(matches: List<MatchResult>, competition: String, result: ResultFilter): List<MatchResult> =
        byCompetition(matches, competition).filter { fits(it, result) }

    fun estimatedCount(matches: List<MatchResult>): Int = matches.count { it.statsEstimated }

    class Record(val played: Int, val won: Int, val drawn: Int, val lost: Int, val goalsFor: Int, val goalsAgainst: Int) {
        val points: Int get() = won * 3 + drawn
        val goalDifference: Int get() = goalsFor - goalsAgainst
    }

    fun record(matches: List<MatchResult>): Record {
        var won = 0
        var drawn = 0
        var lost = 0
        var gf = 0
        var ga = 0
        for (m in matches) {
            when (m.resultBadge) {
                "W" -> won++
                "L" -> lost++
                else -> drawn++
            }
            gf += m.goalsFor
            ga += m.goalsAgainst
        }
        return Record(matches.size, won, drawn, lost, gf, ga)
    }

    /**
     * The last [count] results as "W", "D" and "L", oldest first, so the newest result is at the right-hand end of
     * the strip (the way form guides are written). The list may come in any order: it is sorted by match date, and
     * matches on the same day keep their list order (the list holds the newest first).
     */
    fun form(matches: List<MatchResult>, count: Int = 5): List<String> {
        val newestFirst = matches.withIndex()
            .sortedWith(compareByDescending<IndexedValue<MatchResult>> { it.value.matchDate }.thenBy { it.index })
            .map { it.value }
        return newestFirst.take(count).map { it.resultBadge }.reversed()
    }

    /** "2025/26": the label of a season that starts in [startYear]. */
    fun seasonLabel(startYear: Int): String = "$startYear/${((startYear + 1) % 100).toString().padStart(2, '0')}"
}
