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
    /** The caller's role in this group: OWNER, ADMIN or MEMBER. */
    val role: String? = null,
) {
    val canInvite: Boolean get() = role == "OWNER" || role == "ADMIN"
}

data class Member(
    val id: String,
    val name: String,
    val initials: String,
    val minutesAgo: Int?,
    val accuracy: String,
    /** Position of the last point; null when the member has not uploaded one. */
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** An invitation waiting for the signed-in user. [expiresAt] is epoch milliseconds. */
data class Invite(val id: String, val by: String, val group: String, val expiresAt: Long)

/** An invitation the owner or an admin sent from this group. [status] is PENDING, EXPIRED, ACCEPTED or REVOKED. */
data class SentInvite(val id: String, val email: String, val status: String, val expiresAt: Long)

// The types below are not served by the backend yet (no zone or notification endpoints on dev).

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
