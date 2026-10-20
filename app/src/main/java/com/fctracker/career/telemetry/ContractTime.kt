package com.fctracker.career.telemetry

import com.fctracker.career.AppState
import com.fctracker.career.Player

/** How much attention a contract or loan end deserves. Used for the colour of the chip and the "needs attention" list. */
enum class Urgency { OK, SOON, URGENT }

/**
 * Time left on a contract or a loan, worked out the way the game shows it ("2y 10m", "10m").
 *
 * The time left is measured against the GAME's date, not the phone's clock: a career runs years ahead of (or behind)
 * real life. The date comes from the capture script's squad report ([AppState.careerDate]); until one has arrived the
 * newest match date is used, and when there is neither, no time left is shown at all (never a guess).
 */
object ContractTime {

    private val ISO_DATE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

    private class Ymd(val year: Int, val month: Int, val day: Int)

    private fun parse(iso: String?): Ymd? {
        val m = ISO_DATE.matchEntire(iso?.trim().orEmpty()) ?: return null
        val y = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        val d = m.groupValues[3].toInt()
        if (y < 1900 || y > 2200 || mo !in 1..12 || d !in 1..31) return null
        return Ymd(y, mo, d)
    }

    /** True when [iso] is a real yyyy-MM-dd date. */
    fun isDate(iso: String?): Boolean = parse(iso) != null

    /**
     * Whole months from [nowIso] to [endIso], rounded down: 0 = under a month left, negative = already over.
     * Null when either date cannot be read.
     */
    fun monthsLeft(endIso: String?, nowIso: String?): Int? {
        val end = parse(endIso) ?: return null
        val now = parse(nowIso) ?: return null
        var months = (end.year - now.year) * 12 + (end.month - now.month)
        if (end.day < now.day) months -= 1
        return months
    }

    /** "2y 10m", "2y", "10m", "<1m", or "Over" when the end has passed. */
    fun label(months: Int): String = when {
        months < 0 -> "Over"
        months == 0 -> "<1m"
        months < 12 -> "${months}m"
        months % 12 == 0 -> "${months / 12}y"
        else -> "${months / 12}y ${months % 12}m"
    }

    /** Under six months: other clubs can already sign him on a pre-contract. Under a year: time to talk. */
    fun contractUrgency(months: Int): Urgency = when {
        months < 6 -> Urgency.URGENT
        months < 12 -> Urgency.SOON
        else -> Urgency.OK
    }

    /** A loan about to end: the player goes back, or the option to buy has to be taken. */
    fun loanUrgency(months: Int): Urgency = when {
        months < 1 -> Urgency.URGENT
        months < 3 -> Urgency.SOON
        else -> Urgency.OK
    }

    private val MONTH_NAMES = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** "Jun 2029". Blank when the date cannot be read. */
    fun monthYear(iso: String?): String {
        val d = parse(iso) ?: return ""
        return "${MONTH_NAMES[d.month - 1]} ${d.year}"
    }

    /** "1 Jul 2029". Blank when the date cannot be read. */
    fun fullDate(iso: String?): String {
        val d = parse(iso) ?: return ""
        return "${d.day} ${MONTH_NAMES[d.month - 1]} ${d.year}"
    }

    /** The game's current date: the last squad report's, else the newest match, else unknown (null). */
    fun referenceDate(state: AppState): String? {
        state.careerDate?.takeIf { isDate(it) }?.let { return it }
        return state.recentMatches.map { it.matchDate }.filter { isDate(it) }.maxOrNull()
    }

    /** What to show for one end date: the label and how urgent it is. Null when there is nothing real to show. */
    class Chip(val text: String, val urgency: Urgency, val months: Int)

    fun contractChip(player: Player, nowIso: String?): Chip? {
        val months = monthsLeft(player.contractEnd, nowIso) ?: return null
        return Chip(label(months), contractUrgency(months), months)
    }

    fun loanChip(player: Player, nowIso: String?): Chip? {
        if (player.loanDirection == null) return null
        val months = monthsLeft(player.loanEnd, nowIso) ?: return null
        return Chip(label(months), loanUrgency(months), months)
    }

    /** True when the contract or the loan of this player needs a decision soon. */
    fun needsAttention(player: Player, nowIso: String?): Boolean {
        val contract = contractChip(player, nowIso)
        val loan = loanChip(player, nowIso)
        return (contract != null && contract.urgency != Urgency.OK) || (loan != null && loan.urgency != Urgency.OK)
    }

    /** The players whose contract or loan needs a decision, the soonest first. */
    fun attentionList(players: List<Player>, nowIso: String?): List<Player> =
        players.filter { needsAttention(it, nowIso) }.sortedBy { p ->
            listOfNotNull(contractChip(p, nowIso)?.months, loanChip(p, nowIso)?.months).minOrNull() ?: Int.MAX_VALUE
        }
}
