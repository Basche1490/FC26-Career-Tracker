package com.fctracker.career.logic

import com.fctracker.career.AppState
import com.fctracker.career.Player
import java.text.Normalizer

/**
 * A player is either in the first team or in the youth academy, never both. When the game (or the person) promotes a
 * prospect, his academy card goes and the first-team card carries the Promoted tag.
 */
object Academy {

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase().trim()

    private fun same(a: Player, b: Player): Boolean =
        a.id == b.id || (a.gamePlayerId != null && a.gamePlayerId == b.gamePlayerId) ||
            (a.gamePlayerId == null || b.gamePlayerId == null) && norm(a.name).isNotEmpty() && norm(a.name) == norm(b.name)

    fun isFirstTeam(p: Player) = !p.isAcademy && !p.status.equals("Academy", true)

    /** Moves an academy player who also appears in the first team out of the academy, and tags him Promoted. */
    fun firstTeamOnly(state: AppState): AppState {
        val firstTeam = state.players.filter { isFirstTeam(it) && !it.status.equals("Sold", true) && !it.status.equals("Retired", true) }
        val (twice, keep) = state.academyPlayers.partition { a -> firstTeam.any { same(a, it) } }
        if (twice.isEmpty()) return state
        val players = state.players.map { p ->
            val prospect = twice.firstOrNull { same(it, p) }
            if (prospect != null && isFirstTeam(p) && !p.isPromoted) {
                p.copy(
                    isPromoted = true, promotedSeasonYear = p.promotedSeasonYear ?: state.startYear,
                    // the academy card knew him longer: keep its picture and potential when the new card has none
                    headshotUrl = p.headshotUrl.ifBlank { prospect.headshotUrl },
                    potential = maxOf(p.potential, prospect.potential),
                    potentialRange = p.potentialRange ?: prospect.potentialRange
                )
            } else p
        }
        return state.copy(players = players, academyPlayers = keep)
    }

    /**
     * The game promoted [name] from the academy: his academy card becomes a first-team card (Promoted), instead of a new
     * card being added next to it. Returns null when he is not in the academy.
     */
    fun promote(squad: List<Player>, academy: List<Player>, name: String, season: Int): Pair<List<Player>, List<Player>>? {
        val key = norm(name)
        val inAcademy = academy.firstOrNull { norm(it.name) == key || norm(it.displayName) == key }
        val inSquad = squad.indexOfFirst { (norm(it.name) == key || norm(it.displayName) == key) }
        if (inAcademy == null && (inSquad < 0 || isFirstTeam(squad[inSquad]))) return null
        val newSquad = squad.toMutableList()
        if (inSquad >= 0) {
            newSquad[inSquad] = newSquad[inSquad].copy(isAcademy = false, isPromoted = true, promotedSeasonYear = newSquad[inSquad].promotedSeasonYear ?: season, status = "Active")
        } else if (inAcademy != null) {
            newSquad += inAcademy.copy(isAcademy = false, isPromoted = true, promotedSeasonYear = season, status = "Active", isStartingXI = false)
        }
        return newSquad to academy.filterNot { inAcademy != null && it.id == inAcademy.id }
    }
}
