package com.fctracker.career.logic

import com.fctracker.career.AppState
import com.fctracker.career.Player
import com.fctracker.career.TransferLog

/**
 * "Regen of Sergio Busquets", never "Regen of Sergio Busquets Burgos": the real player a regen is based on is named
 * by his short (common) name everywhere. [shortOf] gives the short name for a full name from the player database,
 * or null when it has none.
 */
object RegenNames {

    fun short(name: String?, shortOf: (String) -> String?): String? {
        val n = name?.trim()?.takeIf { it.isNotEmpty() } ?: return name
        return shortOf(n)?.takeIf { it.isNotBlank() && !it.equals(n, ignoreCase = true) } ?: n
    }

    /** Every regen's "based on" name in the career shortened, in the squad, the academy, the transfers and past seasons. */
    fun shorten(state: AppState, shortOf: (String) -> String?): AppState {
        fun p(list: List<Player>) = list.map { pl ->
            val s = short(pl.regenOriginalName, shortOf)
            if (s != pl.regenOriginalName) pl.copy(regenOriginalName = s) else pl
        }
        fun t(list: List<TransferLog>) = list.map { tl ->
            val s = short(tl.regenOriginalName, shortOf)
            if (s != tl.regenOriginalName) tl.copy(regenOriginalName = s) else tl
        }
        val next = state.copy(
            players = p(state.players), academyPlayers = p(state.academyPlayers), transfers = t(state.transfers),
            historicalSeasons = state.historicalSeasons.map { h -> h.copy(players = p(h.players), academyPlayers = p(h.academyPlayers), transfers = t(h.transfers)) }
        )
        return if (next == state) state else next
    }
}
