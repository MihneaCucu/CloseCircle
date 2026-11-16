package com.closecircle.app.data

enum class DurId(val minutes: Int?) {
    H1(60), H4(240), H8(480), ALWAYS(null), CUSTOM(null)
}

data class Group(
    val id: String,
    val name: String,
    val on: Boolean,
    val durId: DurId,
    val customLabel: String,
    /** Start and end of the current sharing, on the phone's clock; both null-safe for "permanent". */
    val startedAt: Long,
    val endsAt: Long?,
    val members: Int,
)

data class Member(
    val id: String,
    val name: String,
    val initials: String,
    val minutesAgo: Int?,
    val accuracy: String,
    /** Position on the placeholder map, in percent; 0 means not on the map. */
    val x: Int = 0,
    val y: Int = 0,
)

// The types below are not served by the backend yet (no invitation-decline, zone or notification endpoints on dev).

data class Invite(val id: String, val by: String, val group: String, val expiresInHours: Int)

enum class AlertKind { ARRIVE, LEAVE, STOP, EXPIRED }

data class AlertItem(
    val id: String,
    val kind: AlertKind,
    val who: String,
    val verb: String,
    val what: String,
    val tail: String,
    val meta: String,
    val delayMin: Int,
)

data class Zone(val id: String, val name: String, val sub: String, val home: Boolean)
