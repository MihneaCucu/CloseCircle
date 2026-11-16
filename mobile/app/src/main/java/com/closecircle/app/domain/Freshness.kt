package com.closecircle.app.domain

enum class Freshness { RECENT, LAST_KNOWN, UNKNOWN }

private const val RECENT_MAX_MIN = 5 // CF-17
private const val LAST_KNOWN_MAX_MIN = 24 * 60 // CF-17

fun freshnessOf(minutesAgo: Int?): Freshness = when {
    minutesAgo == null || minutesAgo > LAST_KNOWN_MAX_MIN -> Freshness.UNKNOWN
    minutesAgo <= RECENT_MAX_MIN -> Freshness.RECENT
    else -> Freshness.LAST_KNOWN
}

fun freshnessLabel(minutesAgo: Int?): String = when (freshnessOf(minutesAgo)) {
    Freshness.RECENT -> "Recentă · $minutesAgo min"
    Freshness.LAST_KNOWN -> "Ultima cunoscută · $minutesAgo min"
    Freshness.UNKNOWN -> "Necunoscută"
}

private const val DELAYED_AFTER_MIN = 15 // CF-24

/** A zone notification built from an update delayed by more than 15 minutes is marked "ÎNTÂRZIAT". */
fun isDelayed(delayMin: Int): Boolean = delayMin > DELAYED_AFTER_MIN
