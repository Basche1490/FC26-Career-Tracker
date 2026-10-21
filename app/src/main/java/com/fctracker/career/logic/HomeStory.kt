package com.fctracker.career.logic

import com.fctracker.career.MatchResult
import com.fctracker.career.Player

/** The greeting at the top of Home: a headline and one sentence about the last few matches. */
data class Story(val headline: String, val summary: String)

object HomeStory {

    private val WORDS = listOf("No", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")
    private val COUNT = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")

    fun word(n: Int) = WORDS.getOrElse(n) { n.toString() }
    fun count(n: Int) = COUNT.getOrElse(n) { n.toString() }

    fun ordinal(n: Int) = Milestones.ordinal(n)

    private fun chronological(ms: List<MatchResult>) = ms.withIndex()
        .sortedWith(compareBy<IndexedValue<MatchResult>> { it.value.matchDate }.thenByDescending { it.index }).map { it.value }

    /** The player who scored in the most matches in a row, counting back from the latest; only from three. */
    fun scoringStreak(matches: List<MatchResult>, players: List<Player>): Pair<String, Int>? {
        val recent = chronological(matches).asReversed()
        var best: Pair<String, Int>? = null
        for (p in players) {
            var n = 0
            for (m in recent) {
                val scored = m.playerPerformances.any { (it.playerId == p.id || it.playerName.equals(p.name, true)) && it.goals > 0 } ||
                    m.goalDetails.any { it.scorerId == p.id || it.scorerName.equals(p.name, true) || it.scorerName.equals(p.displayName, true) }
                if (scored) n++ else break
            }
            if (n >= 3 && (best == null || n > best.second)) best = p.displayName to n
        }
        return best
    }

    fun story(manager: String, matches: List<MatchResult>, players: List<Player>, isLeague: (String) -> Boolean): Story {
        val name = manager.ifBlank { "boss" }
        val all = chronological(matches)
        if (all.isEmpty()) return Story("Welcome, $name.", "Log your first match, or let your PC send it, and this page tells the story of your season.")
        val last3 = all.takeLast(3)
        val points = last3.sumOf { when (it.resultBadge) { "W" -> 3; "D" -> 1; else -> 0 } }
        val headline = when {
            last3.size >= 3 && points >= 7 -> "A strong week, $name."
            last3.size >= 3 && points <= 2 -> "A tough week, $name."
            last3.size < 3 && all.last().resultBadge == "W" -> "A winning start, $name."
            last3.size < 3 && all.last().resultBadge == "L" -> "A difficult start, $name."
            else -> "A steady week, $name."
        }
        val parts = mutableListOf<String>()
        val wins = last3.count { it.resultBadge == "W" }
        val streak = SeasonFacts.currentWins(all)
        parts += when {
            streak >= 3 -> "${word(streak)} wins in a row"
            last3.size == 3 -> "${word(wins)} ${if (wins == 1) "win" else "wins"} in three"
            else -> "${word(wins)} ${if (wins == 1) "win" else "wins"} in ${count(last3.size)}"
        }
        val positions = SeasonFacts.leaguePositions(all, isLeague)
        if (positions.isNotEmpty()) {
            val now = positions.last()
            val before = positions.getOrNull(positions.size - 4) ?: positions.first()
            parts += when {
                now < before -> "up to ${ordinal(now)}"
                now > before -> "down to ${ordinal(now)}"
                else -> "still ${ordinal(now)}"
            }
        }
        scoringStreak(all, players)?.let { (who, n) -> parts += "$who has scored in ${count(n)} straight" }
        val sentence = when (parts.size) {
            1 -> parts[0]
            2 -> "${parts[0]}, ${parts[1]}"
            else -> parts.dropLast(1).joinToString(", ") + ", and " + parts.last()
        }
        return Story(headline, sentence.replaceFirstChar { it.uppercase() } + ".")
    }
}
