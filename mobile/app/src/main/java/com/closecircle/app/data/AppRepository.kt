package com.closecircle.app.data

import com.closecircle.app.data.api.CloseCircleApi
import com.closecircle.app.data.api.LatestLocationDto
import com.closecircle.app.domain.durationOf
import com.closecircle.app.domain.initialsOf
import java.time.Instant

private const val MIN_MS = 60_000L
private const val NEAR_EDGE = 12
private const val MAP_LEFT = 22
private const val MAP_RIGHT = 68
private const val MAP_TOP = 50
private const val MAP_BOTTOM = 74

/** Reads and changes sharing through the real API and turns the answers into what the screens show. */
class AppRepository(private val api: CloseCircleApi) {

    suspend fun groups(now: Long): List<Group> {
        val shared = api.mySharing().associateBy { it.groupId }
        return api.groups().map { g ->
            val s = shared[g.groupId]
            if (s == null) {
                Group(g.groupId, g.name, false, DurId.H1, "", now, null, g.memberCount.toInt())
            } else {
                // Server-side numbers on both sides: remaining time and total length, so the phone clock only anchors "now".
                val total = if (s.expiresAt != null && s.updatedAt != null) {
                    Instant.parse(s.expiresAt).toEpochMilli() - Instant.parse(s.updatedAt).toEpochMilli()
                } else {
                    null
                }
                val endsAt = s.remainingSeconds?.let { now + it * 1000 }
                val (durId, label) = durationOf(if (endsAt == null) null else total)
                Group(g.groupId, g.name, true, durId, label, if (endsAt != null && total != null) endsAt - total else now, endsAt, g.memberCount.toInt())
            }
        }
    }

    /** Only members who share with you and whom you may see; members who do not share are not returned by the API. */
    suspend fun members(groupId: String, now: Long): List<Member> {
        val raw = api.latestLocations(groupId)
        return layout(raw.map { it.toMember(now) }, raw)
    }

    suspend fun createGroup(name: String) {
        api.createGroup(name)
    }

    suspend fun startSharing(groupId: String, durId: DurId, customMin: Int?) {
        api.startSharing(groupId, if (durId == DurId.CUSTOM) customMin else durId.minutes)
    }

    suspend fun stopSharing(groupId: String) {
        api.stopSharing(groupId)
    }

    private fun LatestLocationDto.toMember(now: Long): Member {
        val loc = location
        val minutes = loc?.let { maxOf(0L, (now - Instant.parse(it.recordedAt).toEpochMilli()) / MIN_MS).toInt() }
        val accuracy = loc?.accuracyM?.let { "±${Math.round(it)} m" } ?: "Precizie necunoscută"
        return Member(userId, displayName, initialsOf(displayName), minutes, if (loc == null) "Nicio actualizare" else accuracy)
    }

    /** Spreads members over the placeholder map by their coordinates; a single member sits in the middle. */
    private fun layout(members: List<Member>, raw: List<LatestLocationDto>): List<Member> {
        val points = raw.mapNotNull { r -> r.location?.let { r.userId to (it.latitude to it.longitude) } }.toMap()
        if (points.isEmpty()) return members
        val lats = points.values.map { it.first }
        val lons = points.values.map { it.second }
        fun scale(v: Double, lo: Double, hi: Double, from: Int, to: Int): Int =
            if (hi - lo < 1e-9) (from + to) / 2 else (from + (v - lo) / (hi - lo) * (to - from)).toInt().coerceIn(NEAR_EDGE, 100 - NEAR_EDGE)
        return members.map { m ->
            val p = points[m.id] ?: return@map m
            // north is up: a larger latitude means a smaller y
            m.copy(x = scale(p.second, lons.min(), lons.max(), MAP_LEFT, MAP_RIGHT), y = scale(p.first, lats.min(), lats.max(), MAP_BOTTOM, MAP_TOP))
        }
    }
}
