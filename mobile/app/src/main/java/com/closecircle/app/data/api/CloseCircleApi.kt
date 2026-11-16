package com.closecircle.app.data.api

import kotlinx.serialization.encodeToString

interface CloseCircleApi {
    suspend fun register(email: String, password: String, displayName: String): AuthResponse
    suspend fun login(email: String, password: String): AuthResponse
    suspend fun groups(): List<GroupDto>
    suspend fun createGroup(name: String): GroupDto

    /** The groups the caller shares with right now (sharing on and not expired). */
    suspend fun mySharing(): List<SharedGroupDto>

    /** Turns sharing on for one group; [durationMinutes] null means no time limit. */
    suspend fun startSharing(groupId: String, durationMinutes: Int?): SharingDto

    suspend fun stopSharing(groupId: String): SharingDto

    /** Other members the caller may see in this group; members who do not share are not listed. */
    suspend fun latestLocations(groupId: String): List<LatestLocationDto>

    /** Owner or admin only. Throws ApiException with code ALREADY_MEMBER or INVITATION_ALREADY_PENDING on a conflict. */
    suspend fun invite(groupId: String, email: String): InvitationDto

    /** The group's invitations, newest first. Owner or admin only. */
    suspend fun sentInvitations(groupId: String): List<InvitationDto>

    /** Pending invitations sent to the caller's account email. */
    suspend fun receivedInvitations(): List<ReceivedInvitationDto>
}

class HttpCloseCircleApi(private val client: ApiClient) : CloseCircleApi {
    private val json get() = client.json

    override suspend fun register(email: String, password: String, displayName: String): AuthResponse =
        client.send("POST", "/api/v1/auth/register", json.encodeToString(RegisterRequest(email, password, displayName))) {
            json.decodeFromString<AuthResponse>(it)
        }

    override suspend fun login(email: String, password: String): AuthResponse =
        client.send("POST", "/api/v1/auth/login", json.encodeToString(LoginRequest(email, password))) {
            json.decodeFromString<AuthResponse>(it)
        }

    // shortcut: first page only (up to 100 groups / members); follow nextCursor when anyone has more
    override suspend fun groups(): List<GroupDto> =
        client.send("GET", "/api/v1/groups?limit=100") { json.decodeFromString<Page<GroupDto>>(it).items }

    override suspend fun createGroup(name: String): GroupDto =
        client.send("POST", "/api/v1/groups", json.encodeToString(CreateGroupRequest(name))) { json.decodeFromString<GroupDto>(it) }

    override suspend fun mySharing(): List<SharedGroupDto> =
        client.send("GET", "/api/v1/users/me/sharing") { json.decodeFromString<SharedGroupsDto>(it).items }

    override suspend fun startSharing(groupId: String, durationMinutes: Int?): SharingDto =
        client.send("PUT", "/api/v1/groups/$groupId/me/sharing", json.encodeToString(SharingUpdate(true, true, durationMinutes))) {
            json.decodeFromString<SharingDto>(it)
        }

    override suspend fun stopSharing(groupId: String): SharingDto =
        client.send("DELETE", "/api/v1/groups/$groupId/me/sharing") { json.decodeFromString<SharingDto>(it) }

    override suspend fun latestLocations(groupId: String): List<LatestLocationDto> =
        client.send("GET", "/api/v1/groups/$groupId/locations/latest?limit=100") { json.decodeFromString<Page<LatestLocationDto>>(it).items }

    override suspend fun invite(groupId: String, email: String): InvitationDto =
        client.send("POST", "/api/v1/groups/$groupId/invitations", json.encodeToString(InviteRequest(email))) { json.decodeFromString<InvitationDto>(it) }

    override suspend fun sentInvitations(groupId: String): List<InvitationDto> =
        client.send("GET", "/api/v1/groups/$groupId/invitations?limit=100") { json.decodeFromString<Page<InvitationDto>>(it).items }

    override suspend fun receivedInvitations(): List<ReceivedInvitationDto> =
        client.send("GET", "/api/v1/invitations?limit=100") { json.decodeFromString<Page<ReceivedInvitationDto>>(it).items }
}
