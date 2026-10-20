package com.fctracker.career.telemetry

/** The kinds of phone alert the person can switch on or off in Settings. */
enum class NoticeKind { MATCH, TRANSFER, INJURY, OTHER }

/** Which kinds of alert are wanted. Everything is on until the person turns something off. */
data class NotificationPrefs(
    val matches: Boolean = true,
    val transfers: Boolean = true,
    val injuries: Boolean = true
)

object NoticeFilter {
    fun kindOf(notice: TelemetryNotice): NoticeKind = when {
        notice.isTransfer -> NoticeKind.TRANSFER
        notice.route == "matches" -> NoticeKind.MATCH
        notice.route == "squad" -> NoticeKind.INJURY
        else -> NoticeKind.OTHER
    }

    fun allowed(notice: TelemetryNotice, prefs: NotificationPrefs): Boolean = when (kindOf(notice)) {
        NoticeKind.MATCH -> prefs.matches
        NoticeKind.TRANSFER -> prefs.transfers
        NoticeKind.INJURY -> prefs.injuries
        NoticeKind.OTHER -> true
    }

    /** Applied before alerts are bundled into a summary, so a summary only counts what is wanted. */
    fun filter(notices: List<TelemetryNotice>, prefs: NotificationPrefs): List<TelemetryNotice> =
        notices.filter { allowed(it, prefs) }
}
