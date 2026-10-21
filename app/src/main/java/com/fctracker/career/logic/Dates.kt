package com.fctracker.career.logic

/**
 * Calendar arithmetic on yyyy-MM-dd strings without java.time (the app still runs on Android 7, where java.time is
 * missing). Days are counted from 1970-01-01 with the usual civil-calendar formulas.
 */
object Dates {

    private val ISO = Regex("^(\\d{4})-(\\d{2})-(\\d{2})")

    val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val MONTHS_SHORT = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val WEEKDAYS_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    data class Ymd(val year: Int, val month: Int, val day: Int)

    fun parse(iso: String?): Ymd? {
        val m = ISO.find(iso?.trim().orEmpty()) ?: return null
        val y = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        val d = m.groupValues[3].toInt()
        if (y < 1900 || y > 2200 || mo !in 1..12 || d !in 1..daysInMonth(y, mo)) return null
        return Ymd(y, mo, d)
    }

    fun isDate(iso: String?): Boolean = parse(iso) != null

    fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    fun daysInMonth(y: Int, m: Int): Int = when (m) {
        2 -> if (isLeap(y)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    /** Days since 1970-01-01. */
    fun toEpochDay(y: Int, m: Int, d: Int): Long {
        val yy = if (m <= 2) y - 1 else y
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = yy - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    fun toEpochDay(iso: String?): Long? = parse(iso)?.let { toEpochDay(it.year, it.month, it.day) }

    fun fromEpochDay(z0: Long): String {
        val z = z0 + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = yoe + era * 400 + if (m <= 2) 1 else 0
        return "%04d-%02d-%02d".format(y, m, d)
    }

    fun plusDays(iso: String?, days: Int): String? = toEpochDay(iso)?.let { fromEpochDay(it + days) }

    /** Days from [from] to [to] (negative when [to] is earlier); null when either cannot be read. */
    fun daysBetween(from: String?, to: String?): Long? {
        val a = toEpochDay(from) ?: return null
        val b = toEpochDay(to) ?: return null
        return b - a
    }

    /** 0 = Monday ... 6 = Sunday. */
    fun weekday(iso: String?): Int? = toEpochDay(iso)?.let { (((it + 3) % 7 + 7) % 7).toInt() }

    /** "12 Oct" */
    fun dayMonth(iso: String?): String {
        val d = parse(iso) ?: return iso.orEmpty()
        return "${d.day} ${MONTHS_SHORT[d.month - 1]}"
    }

    /** "12 Oct 2026" */
    fun full(iso: String?): String {
        val d = parse(iso) ?: return iso.orEmpty()
        return "${d.day} ${MONTHS_SHORT[d.month - 1]} ${d.year}"
    }

    /** "Sat 24 Oct" */
    fun weekdayDayMonth(iso: String?): String {
        val d = parse(iso) ?: return iso.orEmpty()
        val w = weekday(iso) ?: return dayMonth(iso)
        return "${WEEKDAYS_SHORT[w]} ${d.day} ${MONTHS_SHORT[d.month - 1]}"
    }

    /** "Tuesday 20 October" */
    fun longWeekday(iso: String?): String {
        val d = parse(iso) ?: return ""
        val w = weekday(iso) ?: return ""
        return "${WEEKDAYS[w]} ${d.day} ${MONTHS[d.month - 1]}"
    }

    /** "October 2026" */
    fun monthYear(iso: String?): String {
        val d = parse(iso) ?: return ""
        return "${MONTHS[d.month - 1]} ${d.year}"
    }

    /** "Oct 2026" */
    fun shortMonthYear(iso: String?): String {
        val d = parse(iso) ?: return ""
        return "${MONTHS_SHORT[d.month - 1]} ${d.year}"
    }

    /**
     * The season a date belongs to, by its start year: July to December belong to the season starting that year,
     * January to June to the one that started the year before.
     */
    fun seasonOf(iso: String?): Int? {
        val d = parse(iso) ?: return null
        return if (d.month >= 7) d.year else d.year - 1
    }

    /** yyyy-MM-dd of the phone's own clock, for when the game's date is not known. */
    fun phoneToday(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    /**
     * The injury day counter in the game's career_playerlastgrowth table counts days from 22 December 1995: the
     * plain 1 January 1996 start read every injury 10 days late (an injury the game expected over around 9 September
     * read as 19 September). Kept here as well as in the capture script so both agree.
     */
    const val INJURY_EPOCH = "1995-12-22"

    fun injuryDayToIso(day: Int): String? {
        if (day <= 0) return null
        val base = toEpochDay(INJURY_EPOCH) ?: return null
        return fromEpochDay(base + day)
    }
}
