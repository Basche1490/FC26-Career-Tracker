package com.fctracker.career.logic

import com.fctracker.career.Fixture
import com.fctracker.career.MatchResult

enum class RoundState { WON, LOST, PLAYED, NEXT, TODO }

data class CupRound(val code: String, val state: RoundState, val match: MatchResult? = null, val fixture: Fixture? = null)

data class CupRun(
    val competition: String,
    val rounds: List<CupRound>,
    /** One line: "Round 3 next: Stoke City (H), Sat 9 Jan", "Out in Round 3: lost 1–2 at Brighton", "Winners". */
    val status: String,
    /** "good", "bad", "accent" or "" (the colour of the status line). */
    val tone: String,
    val isEuropean: Boolean,
    val won: Boolean,
    val out: Boolean
)

/**
 * The cup runs on Season Summary: every round of each cup the club played in, coloured won, lost, next or still to
 * come, worked out from the matches played and the fixtures still to play.
 */
object CupRuns {

    private val ORDINALS = mapOf("first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5, "sixth" to 6, "seventh" to 7, "eighth" to 8)

    /** A short code for a round ("R3", "QF", "SF", "F", "LP", "PO", "R16"); null when the stage says nothing usable. */
    fun roundCode(stage: String?): String? {
        val s = stage?.trim()?.lowercase().orEmpty()
        if (s.isEmpty()) return null
        return when {
            s.contains("semi") -> "SF"
            s.contains("quarter") -> "QF"
            s.contains("final") -> "F"
            s.contains("round of 16") || s.contains("last 16") || s.contains("1/8") || s.contains("eighth-final") || s.contains("eighth final") -> "R16"
            s.contains("round of 32") || s.contains("last 32") || s.contains("1/16") -> "R32"
            s.contains("play-off") || s.contains("playoff") || s.contains("play off") -> "PO"
            s.contains("league phase") || s.contains("league stage") || s.contains("group") || s.contains("matchday") -> "LP"
            else -> {
                Regex("round\\s*(\\d+)").find(s)?.let { return "R${it.groupValues[1]}" }
                Regex("(\\d+)(st|nd|rd|th)\\s*round").find(s)?.let { return "R${it.groupValues[1]}" }
                Regex("^r(\\d+)$").find(s)?.let { return "R${it.groupValues[1]}" }
                ORDINALS.entries.firstOrNull { s.contains("${it.key} round") }?.let { "R${it.value}" }
            }
        }
    }

    fun roundName(code: String): String = when {
        code == "F" -> "Final"
        code == "SF" -> "Semi-final"
        code == "QF" -> "Quarter-final"
        code == "LP" -> "League phase"
        code == "PO" -> "Knockout play-off"
        code == "R16" -> "Round of 16"
        code == "R32" -> "Round of 32"
        code.startsWith("R") -> "Round ${code.drop(1)}"
        else -> code
    }

    /** "Round 3", "the quarter-final", "the final": the round as it reads after "Out in" or "Through to". */
    fun phrase(code: String): String {
        val name = roundName(code)
        return if (name.startsWith("Round ") && code.drop(1).all { it.isDigit() }) name else "the " + name.lowercase()
    }

    fun isEuropean(competition: String): Boolean {
        val c = competition.lowercase()
        return c.contains("champions league") || c.contains("europa league") || c.contains("conference league") ||
            c.contains("uefa") && !c.contains("super")
    }

    private fun isSuperCup(competition: String): Boolean {
        val c = competition.lowercase()
        return c.contains("super") || c.contains("shield") || c.contains("trophée") || c.contains("trophee") || c.contains("supercopa") || c.contains("supercoppa")
    }

    /** The rounds a cup has, in order. Unknown cups get the rounds that were seen plus the usual last three. */
    fun template(competition: String): List<String> {
        val c = competition.lowercase()
        return when {
            isSuperCup(competition) -> listOf("F")
            isEuropean(competition) -> listOf("LP", "PO", "R16", "QF", "SF", "F")
            c.contains("fa cup") -> listOf("R1", "R2", "R3", "R4", "R5", "QF", "SF", "F")
            c.contains("carabao") || c.contains("efl cup") || c.contains("league cup") -> listOf("R1", "R2", "R3", "R4", "QF", "SF", "F")
            c.contains("copa del rey") -> listOf("R1", "R2", "R32", "R16", "QF", "SF", "F")
            c.contains("coppa italia") || c.contains("dfb") || c.contains("pokal") -> listOf("R1", "R2", "R16", "QF", "SF", "F")
            else -> emptyList()
        }
    }

    private fun ours(m: MatchResult): Pair<Int, Int> = if (m.isHome) m.homePenalties to m.awayPenalties else m.awayPenalties to m.homePenalties

    /** W, L or D for one cup match, counting a shoot-out. */
    fun outcome(m: MatchResult): String {
        if (m.goalsFor != m.goalsAgainst) return m.resultBadge
        val (p, q) = ours(m)
        return when {
            p > q -> "W"
            q > p -> "L"
            else -> "D"
        }
    }

    private fun scoreText(m: MatchResult): String {
        val (p, q) = ours(m)
        val pens = if (m.goalsFor == m.goalsAgainst && (p > 0 || q > 0)) " ($p–$q on penalties)" else ""
        return "${m.goalsFor}–${m.goalsAgainst}$pens"
    }

    fun run(competition: String, matches: List<MatchResult>, fixtures: List<Fixture>): CupRun? {
        val ms = matches.filter { it.competition.equals(competition, ignoreCase = true) }.sortedBy { it.matchDate }
        val fx = fixtures.filter { it.competition.equals(competition, ignoreCase = true) }.sortedBy { it.date }
        if (ms.isEmpty() && fx.isEmpty()) return null

        val seen = (ms.mapNotNull { roundCode(it.stage) } + fx.mapNotNull { roundCode(it.round) }).distinct()
        var rounds = template(competition)
        if (rounds.isEmpty()) {
            rounds = seen.toMutableList().also { l -> listOf("QF", "SF", "F").forEach { if (it !in l) l += it } }
        } else {
            val first = rounds.indexOfFirst { it in seen }
            if (first > 0) rounds = rounds.drop(first)
            seen.filter { it !in rounds }.forEach { extra -> rounds = rounds + extra }
        }
        val order = rounds.withIndex().associate { it.value to it.index }
        val furthestReached = (ms.mapNotNull { roundCode(it.stage) } + fx.mapNotNull { roundCode(it.round) })
            .mapNotNull { order[it] }.maxOrNull() ?: -1

        val cupRounds = rounds.mapIndexed { i, code ->
            val inRound = ms.filter { roundCode(it.stage) == code }
            val next = fx.firstOrNull { roundCode(it.round) == code }
            val last = inRound.lastOrNull()
            val state = when {
                last != null && i < furthestReached -> RoundState.WON
                last != null && outcome(last) == "W" -> RoundState.WON
                last != null && outcome(last) == "L" -> RoundState.LOST
                last != null -> RoundState.PLAYED
                next != null -> RoundState.NEXT
                else -> RoundState.TODO
            }
            CupRound(code, state, last, next)
        }

        val final = cupRounds.firstOrNull { it.code == "F" }
        val lost = cupRounds.lastOrNull { it.state == RoundState.LOST }
        val nextRound = cupRounds.firstOrNull { it.state == RoundState.NEXT }
        val lastPlayed = ms.lastOrNull()
        val european = isEuropean(competition)

        return when {
            final?.state == RoundState.WON && final.match != null -> {
                val m = final.match
                CupRun(competition, cupRounds, "Winners: beat ${m.opponentName} ${scoreText(m)} in the final", "good", european, won = true, out = false)
            }
            lost?.match != null && nextRound == null -> {
                val m = lost.match
                val where = if (m.isHome) "to" else "at"
                CupRun(competition, cupRounds, "Out in ${phrase(lost.code)}: lost ${scoreText(m)} $where ${m.opponentName}", "bad", european, won = false, out = true)
            }
            nextRound?.fixture != null -> {
                val f = nextRound.fixture
                CupRun(competition, cupRounds, "${roundName(nextRound.code)} next: ${f.opponent} (${if (f.isHome) "H" else "A"}), ${Dates.weekdayDayMonth(f.date)}", "accent", european, won = false, out = false)
            }
            lastPlayed != null -> {
                val after = cupRounds.firstOrNull { it.state == RoundState.TODO }
                val text = if (after != null) "Through to ${phrase(after.code)}" else "Played ${ms.size} ${if (ms.size == 1) "match" else "matches"}"
                CupRun(competition, cupRounds, text, "good", european, won = false, out = false)
            }
            else -> CupRun(competition, cupRounds, "Not started", "", european, won = false, out = false)
        }
    }

    /** Every cup the club played in (or has a fixture in) this season; domestic cups first, then Europe. */
    fun all(matches: List<MatchResult>, fixtures: List<Fixture>, isLeague: (String) -> Boolean): List<CupRun> {
        val comps = (matches.map { it.competition } + fixtures.map { it.competition })
            .filter { it.isNotBlank() && !isLeague(it) }
            .distinctBy { it.lowercase() }
        return comps.mapNotNull { run(it, matches, fixtures) }.sortedBy { if (it.isEuropean) 1 else 0 }
    }
}
