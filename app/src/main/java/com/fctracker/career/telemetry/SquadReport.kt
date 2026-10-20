package com.fctracker.career.telemetry

import com.fctracker.career.Player
import org.json.JSONObject
import java.text.Normalizer

/**
 * The capture script's squad report (dataType "squad"): for every player of the club the end of his contract and, for
 * a loan, the other club, the end date and the option to buy. These are the game's own values, so they are stored as
 * they are; a player the report says nothing about keeps whatever he had.
 */
object SquadReport {

    class Row(
        val name: String,
        val gameId: Int,
        val contractEnd: String?,
        val loanDirection: String?,
        val loanClub: String,
        val loanEnd: String?,
        val loanOptionToBuy: Boolean
    )

    class Report(val careerDate: String?, val rows: List<Row>)

    class Applied(
        val squad: List<Player>,
        val academy: List<Player>,
        val matched: Int,
        val unmatched: Int,
        val changedCount: Int
    ) {
        val changed: Boolean get() = changedCount > 0
    }

    fun parse(data: JSONObject): Report {
        val careerDate = data.optString("careerDate").trim().takeIf { ContractTime.isDate(it) }
        val rows = mutableListOf<Row>()
        val array = data.optJSONArray("players")
        if (array != null) {
            for (i in 0 until array.length()) {
                val p = array.optJSONObject(i) ?: continue
                val name = p.optString("name").trim()
                if (name.isEmpty()) continue
                val loan = p.optJSONObject("loan")
                val dir = loan?.optString("dir")?.trim()?.lowercase()?.takeIf { it == "in" || it == "out" }
                rows.add(
                    Row(
                        name = name,
                        gameId = p.optInt("pid", 0),
                        contractEnd = p.optString("contractEnd").trim().takeIf { ContractTime.isDate(it) },
                        loanDirection = dir,
                        loanClub = if (dir != null) loan.optString("club").trim() else "",
                        loanEnd = if (dir != null) loan.optString("end").trim().takeIf { ContractTime.isDate(it) } else null,
                        loanOptionToBuy = dir != null && loan.optBoolean("toBuy", false)
                    )
                )
            }
        }
        return Report(careerDate, rows)
    }

    /** Lower case, no accents, letters and digits only: "D. Millán" and "d millan" are the same name. */
    fun normalise(name: String): String {
        val decomposed = Normalizer.normalize(name, Normalizer.Form.NFD)
        return decomposed.filter { it.isLetterOrDigit() }.lowercase()
    }

    private fun namesOf(p: Player): Set<String> =
        listOf(p.name, p.nickname, p.customDisplayName, p.displayName)
            .mapNotNull { it?.let(::normalise) }
            .filter { it.isNotEmpty() }
            .toSet()

    /**
     * Matches report rows to players. A player the game's id was remembered for is found by it, even if he was renamed.
     * Otherwise by name (any of his names, accents and punctuation ignored); a name that fits two players, or two
     * report rows, is not guessed at.
     */
    private fun matchRows(players: List<Player>, rows: List<Row>): Map<String, Row> {
        val byId = rows.filter { it.gameId > 0 }.groupBy { it.gameId }
        val byName = rows.groupBy { normalise(it.name) }
        val result = mutableMapOf<String, Row>()
        val nameClaims = mutableMapOf<String, MutableList<Player>>()
        for (p in players) {
            val byGameId = p.gamePlayerId?.let { byId[it] }
            if (byGameId != null && byGameId.size == 1) {
                result[p.id] = byGameId[0]
                continue
            }
            for (n in namesOf(p)) {
                val candidates = byName[n] ?: continue
                if (candidates.size == 1) nameClaims.getOrPut(n) { mutableListOf() }.add(p)
            }
        }
        for ((n, claimants) in nameClaims) {
            val distinct = claimants.distinctBy { it.id }
            if (distinct.size != 1) continue          // two players with the same name: not guessed
            val p = distinct[0]
            if (!result.containsKey(p.id)) result[p.id] = byName.getValue(n)[0]
        }
        return result
    }

    private fun update(p: Player, row: Row): Player {
        val onLoan = row.loanDirection != null
        return p.copy(
            gamePlayerId = if (row.gameId > 0) row.gameId else p.gamePlayerId,
            contractEnd = row.contractEnd ?: p.contractEnd,
            loanDirection = row.loanDirection,
            loanClub = if (onLoan) row.loanClub.ifBlank { null } else null,
            loanEnd = if (onLoan) row.loanEnd else null,
            loanOptionToBuy = onLoan && row.loanOptionToBuy
        )
    }

    fun apply(squad: List<Player>, academy: List<Player>, report: Report): Applied {
        val all = squad + academy
        val matches = matchRows(all, report.rows)
        var changed = 0
        fun run(list: List<Player>): List<Player> = list.map { p ->
            val row = matches[p.id] ?: return@map p
            val updated = update(p, row)
            if (updated != p) changed++
            updated
        }
        val newSquad = run(squad)
        val newAcademy = run(academy)
        return Applied(newSquad, newAcademy, matches.size, report.rows.size - matches.size, changed)
    }
}
