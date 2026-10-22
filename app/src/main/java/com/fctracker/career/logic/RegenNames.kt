package com.fctracker.career.logic

import com.fctracker.career.AppState
import com.fctracker.career.MatchResult
import com.fctracker.career.Player
import com.fctracker.career.TransferLog

/**
 * "Regen of Sergio Busquets", never "Regen of Sergio Busquets Burgos" nor "Regen of Sergio Busquets (Sergio Busquets
 * Burgos)": the real player a regen is based on, and the player of every deal, are named by the short (common) name
 * everywhere. [shortOf] gives the short name for a full name from the player database, or null when it has none.
 */
object RegenNames {

    fun short(name: String?, shortOf: (String) -> String?): String? = PlayerNames.short(name, shortOf)

    /**
     * Every regen's "based on" name in the career shortened, in the squad, the academy and past seasons, and the player
     * names of the deals (an old pick from the database was stored as "Pedri (Pedro González López)").
     */
    fun shorten(state: AppState, shortOf: (String) -> String?): AppState {
        fun p(list: List<Player>) = list.map { pl ->
            val s = short(pl.regenOriginalName, shortOf)
            if (s != pl.regenOriginalName) pl.copy(regenOriginalName = s) else pl
        }
        fun t(list: List<TransferLog>) = list.map { tl ->
            val orig = short(tl.regenOriginalName, shortOf)
            val name = short(tl.playerName, shortOf) ?: tl.playerName
            val swap = short(tl.swapPlayerName, shortOf)
            if (orig != tl.regenOriginalName || name != tl.playerName || swap != tl.swapPlayerName)
                tl.copy(regenOriginalName = orig, playerName = name, swapPlayerName = swap) else tl
        }
        val next = state.copy(
            players = p(state.players), academyPlayers = p(state.academyPlayers), transfers = t(state.transfers),
            historicalSeasons = state.historicalSeasons.map { h -> h.copy(players = p(h.players), academyPlayers = p(h.academyPlayers), transfers = t(h.transfers)) }
        )
        return if (next == state) state else next
    }
}

/**
 * Cards made with guessed details (a signing the database did not know by that name: born 1 January 1999, playing CM)
 * corrected from the database card of the same player, and every player named by the short name the game shows.
 */
object PlayerRepair {

    private fun named(p: Player, lookup: PlayerLookup, db: Player?): Player {
        if (!p.nickname.isNullOrBlank() || !p.customDisplayName.isNullOrBlank()) return p
        val short = db?.nickname?.takeIf { it.isNotBlank() } ?: lookup.shortName(p.name)
        return if (short.isNotBlank() && !short.equals(p.name, ignoreCase = true)) p.copy(nickname = short) else p
    }

    private fun card(p: Player, lookup: PlayerLookup, seasonYear: Int, fix: Boolean): Player {
        val db = p.gamePlayerId?.let { lookup.byGameId(it, seasonYear) }
            ?: if (PlayerCards.isPlaceholder(p) && !p.isRegen) lookup.byName(p.name, seasonYear) else null
        var out = named(p, lookup, db)
        if (fix && db != null) out = PlayerCards.withDatabase(out, db)
        return out
    }

    /**
     * Line-up rows, scorers and substitutes that a match stored under the game's id (the squad card did not know that id
     * yet, or the game gave no name) are linked to the squad card with that id. Shown as "Unknown player" before.
     */
    fun linkMatches(matches: List<MatchResult>, players: List<Player>): List<MatchResult> {
        val byGame = players.mapNotNull { p -> p.gamePlayerId?.let { it.toString() to p.id } }.toMap()
        if (byGame.isEmpty() || matches.isEmpty()) return matches
        val ids = players.map { it.id }.toSet()
        fun link(id: String?): String? = if (id == null || id in ids) id else byGame[id] ?: id
        var changed = false
        val out = matches.map { m ->
            val perfs = m.playerPerformances.map { pp ->
                val pid = link(pp.playerId) ?: pp.playerId
                val assisters = pp.goalAssisters.map { link(it) }
                if (pid != pp.playerId || assisters != pp.goalAssisters) pp.copy(playerId = pid, goalAssisters = assisters) else pp
            }.distinctBy { it.playerId }
            val goals = m.goalDetails.map { g ->
                val s = link(g.scorerId); val a = link(g.assisterId)
                if (s != g.scorerId || a != g.assisterId) g.copy(scorerId = s, assisterId = a) else g
            }
            val subs = m.substitutions.map { sb ->
                val o = link(sb.playerOutId) ?: sb.playerOutId; val i = link(sb.playerInId) ?: sb.playerInId
                if (o != sb.playerOutId || i != sb.playerInId) sb.copy(playerOutId = o, playerInId = i) else sb
            }
            if (perfs != m.playerPerformances || goals != m.goalDetails || subs != m.substitutions) {
                changed = true
                m.copy(playerPerformances = perfs, goalDetails = goals, substitutions = subs)
            } else m
        }
        return if (changed) out else matches
    }

    fun repair(state: AppState, lookup: PlayerLookup): AppState {
        val year = state.startYear
        val players = state.players.map { card(it, lookup, year, fix = true) }
        val next = state.copy(
            players = players,
            academyPlayers = state.academyPlayers.map { card(it, lookup, year, fix = true) },
            recentMatches = linkMatches(state.recentMatches, players + state.academyPlayers),
            historicalSeasons = state.historicalSeasons.map { h ->
                h.copy(players = h.players.map { card(it, lookup, h.year, fix = false) }, academyPlayers = h.academyPlayers.map { card(it, lookup, h.year, fix = false) })
            }
        )
        return if (next == state) state else next
    }
}
