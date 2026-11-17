package com.closecircle.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MIN_MS = 60_000L

/** "2 h 14 min", "3 zile 2 h", "14 min": time remaining, rounded up to the minute. */
fun leftText(ms: Long): String {
    val m = (maxOf(0L, ms) + MIN_MS - 1) / MIN_MS
    val d = m / 1440
    val h = (m % 1440) / 60
    val mi = m % 60
    return when {
        d > 0 -> "$d ${if (d == 1L) "zi" else "zile"}${if (h > 0) " $h h" else ""}"
        h > 0 -> "$h h${if (mi > 0) " $mi min" else ""}"
        else -> "$mi min"
    }
}

/** Length of a duration given in minutes: "30 min", "2 h", "3 zile". */
fun lengthText(min: Int): String {
    if (min >= 1440 && min % 1440 == 0) return "${min / 1440} ${if (min / 1440 == 1) "zi" else "zile"}"
    val h = min / 60
    val mi = min % 60
    return if (h > 0) "$h h${if (mi > 0) " $mi min" else ""}" else "$mi min"
}

private val clock = DateTimeFormatter.ofPattern("HH:mm")

/** "23:04" */
fun clockText(t: Long, zone: ZoneId = ZoneId.systemDefault()): String = clock.format(Instant.ofEpochMilli(t).atZone(zone))
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ro"))

/** "la 16:00", "mâine la 08:00" or "pe 14 nov, 08:00". */
fun whenText(t: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(t).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (at.toLocalDate()) {
        today -> "la ${clock.format(at)}"
        today.plusDays(1) -> "mâine la ${clock.format(at)}"
        else -> "pe ${dayMonth.format(at)}, ${clock.format(at)}"
    }
}
