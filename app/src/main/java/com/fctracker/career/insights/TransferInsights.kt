package com.fctracker.career.insights

import com.fctracker.career.TransferLog
import com.fctracker.career.isIncomingDeal

/** The three parts of a football year, by the month a deal was made. */
enum class WindowKind { SUMMER, WINTER, IN_SEASON }

/** Money in and out over a set of deals. Fees the game does not store are counted as unknown, never guessed. */
class TransferSummary(
    val spent: Double,
    val received: Double,
    val signings: Int,
    val departures: Int,
    /** Deals whose fee is not known: the totals do not include them. */
    val unknownFees: Int
) {
    /** Positive = earned more than spent. */
    val net: Double get() = received - spent
}

/** The deals of one transfer window (or of the stretch between windows). */
class TransferWindow(val key: String, val label: String, val kind: WindowKind, val logs: List<TransferLog>) {
    val summary: TransferSummary get() = TransferInsights.summarise(logs)
}

object TransferInsights {

    private val DATE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})")

    private fun isRetirement(log: TransferLog): Boolean =
        log.type.equals("Retirement", ignoreCase = true) || log.type.equals("Retired", ignoreCase = true)

    private fun round2(x: Double): Double = Math.round(x * 100.0) / 100.0

    fun summarise(logs: List<TransferLog>): TransferSummary {
        var spent = 0.0
        var received = 0.0
        var signings = 0
        var departures = 0
        var unknown = 0
        for (log in logs) {
            if (isRetirement(log)) continue
            if (log.isIncomingDeal()) {
                spent += log.feeM
                signings++
            } else {
                received += log.feeM
                departures++
            }
            if (log.feeUnknown && log.feeM <= 0.0) unknown++
        }
        return TransferSummary(round2(spent), round2(received), signings, departures, unknown)
    }

    private class Slot(val kind: WindowKind, val seasonStart: Int, val year: Int)

    private fun slotOf(date: String): Slot? {
        val m = DATE.find(date.trim()) ?: return null
        val year = m.groupValues[1].toInt()
        val month = m.groupValues[2].toInt()
        return when (month) {
            in 6..9 -> Slot(WindowKind.SUMMER, year, year)
            12 -> Slot(WindowKind.WINTER, year, year)
            1, 2 -> Slot(WindowKind.WINTER, year - 1, year)
            10, 11 -> Slot(WindowKind.IN_SEASON, year, year)
            3, 4, 5 -> Slot(WindowKind.IN_SEASON, year - 1, year)
            else -> null
        }
    }

    /**
     * The summer window is June to September, the winter window December to February; deals in the months between
     * belong to "in season". A deal with no readable date is not in any window. Newest window first.
     */
    fun windows(logs: List<TransferLog>): List<TransferWindow> {
        val groups = LinkedHashMap<String, MutableList<TransferLog>>()
        val meta = HashMap<String, Pair<WindowKind, String>>()
        for (log in logs) {
            val slot = slotOf(log.date) ?: continue
            val season = MatchInsights.seasonLabel(slot.seasonStart)
            val (key, label) = when (slot.kind) {
                WindowKind.SUMMER -> "summer-${slot.seasonStart}" to "Summer ${slot.seasonStart}"
                WindowKind.WINTER -> "winter-${slot.seasonStart}" to "Winter $season"
                WindowKind.IN_SEASON -> "season-${slot.seasonStart}" to "In season $season"
            }
            groups.getOrPut(key) { mutableListOf() }.add(log)
            meta[key] = slot.kind to label
        }
        return groups.entries
            .sortedByDescending { entry -> entry.value.maxOf { it.date.trim().take(10) } }
            .map { (key, list) -> TransferWindow(key, meta.getValue(key).second, meta.getValue(key).first, list) }
    }

    /** "+€12.5M", "-€3.0M" or "€0.0M". */
    fun money(value: Double, currencySymbol: String, signed: Boolean = false): String {
        val abs = Math.abs(value)
        val text = String.format(java.util.Locale.US, "%.1f", abs)
        val sign = when {
            value > 0.0 && signed -> "+"
            value < 0.0 && text != "0.0" -> "-"
            else -> ""
        }
        return "$sign$currencySymbol${text}M"
    }
}
