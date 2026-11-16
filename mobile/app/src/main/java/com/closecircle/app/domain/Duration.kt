package com.closecircle.app.domain

import com.closecircle.app.data.DurId

private const val MIN_MS = 60_000L

/**
 * The backend stores when sharing started and when it ends, not which chip was used.
 * A length of exactly 1, 4 or 8 hours shows the matching chip; anything else shows as a custom length.
 */
fun durationOf(totalMs: Long?): Pair<DurId, String> {
    if (totalMs == null) return DurId.ALWAYS to ""
    val min = Math.round(totalMs.toDouble() / MIN_MS).toInt()
    return when (min) {
        60 -> DurId.H1 to ""
        240 -> DurId.H4 to ""
        480 -> DurId.H8 to ""
        else -> DurId.CUSTOM to lengthText(min)
    }
}

fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
