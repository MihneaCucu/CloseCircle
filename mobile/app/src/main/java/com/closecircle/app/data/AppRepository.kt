package com.closecircle.app.data

import com.closecircle.app.data.api.CloseCircleApi
import com.closecircle.app.data.api.LatestLocationDto
import com.closecircle.app.domain.durationOf
import com.closecircle.app.domain.initialsOf
import java.time.Instant

private const val MIN_MS = 60_000L

/** Reads and changes sharing through the real API and turns the answers into what the screens show. */
class AppRepository(private val api: CloseCircleApi) {

    suspend fun groups(now: Long): List<Group> {
        val shared = api.mySharing().associateBy { it.groupId }
        return api.groups().map { g ->
            val s = shared[g.groupId]
            if (s == null) {
                Group(g.groupId, g.name, false, DurId.H1, "", now, null, g.memberCount.toInt(), g.myRole)
            } else {
                // Server-side numbers on both sides: remaining time and total length, so the phone clock only anchors "now".
                val total = if (s.expiresAt != null && s.updatedAt != null) {
                    Instant.parse(s.expiresAt).toEpochMilli() - Instant.parse(s.updatedAt).toEpochMilli()
                } else {
                    null
                }
                val endsAt = s.remainingSeconds?.let { now + it * 1000 }
                val (durId, label) = durationOf(if (endsAt == null) null else total)
                Group(g.groupId, g.name, true, durId, label, if (endsAt != null && total != null) endsAt - total else now, endsAt, g.memberCount.toInt(), g.myRole)
            }
        }
    }

    /** Only members who share with you and whom you may see; members who do not share are not returned by the API. */
    suspend fun members(groupId: String, now: Long): List<Member> = api.latestLocations(groupId).map { it.toMember(now) }

    suspend fun receivedInvites(): List<Invite> = api.receivedInvitations().map {
        Invite(it.invitationId, it.invitedBy.displayName, it.groupName, Instant.parse(it.expiresAt).toEpochMilli())
    }

    suspend fun sentInvites(groupId: String): List<SentInvite> = api.sentInvitations(groupId).map {
        SentInvite(it.invitationId, it.email, it.status, Instant.parse(it.expiresAt).toEpochMilli())
    }

    suspend fun invite(groupId: String, email: String) {
        api.invite(groupId, email)
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
        return Member(
            userId, displayName, initialsOf(displayName), minutes, if (loc == null) "Nicio actualizare" else accuracy,
            latitude = loc?.latitude, longitude = loc?.longitude,
        )
    }
}
