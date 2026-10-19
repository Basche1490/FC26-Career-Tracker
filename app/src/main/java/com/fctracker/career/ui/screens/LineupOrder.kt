package com.fctracker.career.ui.screens

import com.fctracker.career.PlayerPerformance
import com.fctracker.career.Substitution

/**
 * Orders a match line-up the way it reads on a team sheet: strikers first, then wingers, attacking midfielders,
 * midfielders, defensive midfielders, full-backs, centre-backs and the goalkeeper last. A substitute is listed
 * directly under the player he replaced, so a swap reads as two neighbouring rows.
 */
object LineupOrder {
    const val UNKNOWN_RANK = 9

    /** 0 = striker ... 8 = goalkeeper, [UNKNOWN_RANK] when the position is not known. */
    fun rank(position: String?): Int = when (position?.trim()?.uppercase()) {
        "ST", "CF", "LS", "RS", "SS" -> 0
        "LW", "RW", "LF", "RF", "LWF", "RWF" -> 1
        "CAM", "LAM", "RAM", "AM" -> 2
        "LM", "RM" -> 3
        "CM", "LCM", "RCM" -> 4
        "CDM", "LDM", "RDM", "DM" -> 5
        "LWB", "RWB", "LB", "RB", "WB" -> 6
        "CB", "LCB", "RCB", "SW" -> 7
        "GK" -> 8
        else -> UNKNOWN_RANK
    }

    /** 0 = left, 1 = centre, 2 = right: puts LB before RB and LW before RW inside the same line. */
    fun side(position: String?): Int {
        val p = position?.trim()?.uppercase() ?: return 1
        return when {
            p.startsWith("L") -> 0
            p.startsWith("R") -> 2
            else -> 1
        }
    }

    fun order(
        performances: List<PlayerPerformance>,
        substitutions: List<Substitution>,
        positionOf: (PlayerPerformance) -> String?,
        nameOf: (PlayerPerformance) -> String
    ): List<PlayerPerformance> {
        val byId = performances.associateBy { it.playerId }
        val starters = performances.filter { it.subInMinute == null }
            .sortedWith(
                compareBy<PlayerPerformance>({ rank(positionOf(it)) }, { side(positionOf(it)) }, { nameOf(it).lowercase() })
            )
        val subs = performances.filter { it.subInMinute != null }

        // Who did each substitute replace? The recorded substitutions say so exactly; for a match edited by hand
        // the same minute (and, if possible, the same line of the team) does.
        val replaced = HashMap<String, String>()   // substitute id -> id of the player he replaced
        val taken = HashSet<String>()              // players that already have a replacement
        for (sub in subs) {
            val out = substitutions.firstOrNull { it.playerInId == sub.playerId }?.playerOutId
            if (out != null && out != sub.playerId && byId.containsKey(out) && taken.add(out)) replaced[sub.playerId] = out
        }
        for (sub in subs.filter { !replaced.containsKey(it.playerId) }.sortedBy { it.subInMinute }) {
            val candidates = performances.filter {
                it.playerId != sub.playerId && it.subOutMinute == sub.subInMinute && !taken.contains(it.playerId)
            }
            val pick = candidates.firstOrNull { rank(positionOf(it)) == rank(positionOf(sub)) } ?: candidates.firstOrNull()
            if (pick != null) {
                taken.add(pick.playerId)
                replaced[sub.playerId] = pick.playerId
            }
        }

        val children = HashMap<String, MutableList<PlayerPerformance>>()
        for (sub in subs) {
            val parent = replaced[sub.playerId] ?: continue
            children.getOrPut(parent) { mutableListOf() }.add(sub)
        }

        val result = ArrayList<PlayerPerformance>(performances.size)
        val placed = HashSet<String>()
        fun place(p: PlayerPerformance) {
            if (!placed.add(p.playerId)) return
            result.add(p)
            children[p.playerId]?.sortedBy { it.subInMinute }?.forEach { place(it) }
        }
        starters.forEach { place(it) }
        // substitutes whose replaced player is not in the list, in the order they came on
        subs.filter { !placed.contains(it.playerId) }
            .sortedWith(compareBy<PlayerPerformance>({ it.subInMinute ?: 0 }, { rank(positionOf(it)) }, { nameOf(it).lowercase() }))
            .forEach { place(it) }
        return result
    }
}
