package com.closecircle.app.data.api

import kotlinx.serialization.Serializable

// Shapes of the backend JSON (docs/api-contract.md and docs/contracts/location-api.openapi.json on dev).
// Responses may gain fields, so decoding ignores unknown ones; requests only send what the contract lists.

@Serializable
data class RegisterRequest(val email: String, val password: String, val displayName: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class UserDto(val userId: String, val email: String, val displayName: String)

@Serializable
data class AuthResponse(val accessToken: String, val expiresAt: String, val user: UserDto)

@Serializable
data class GroupDto(val groupId: String, val name: String, val memberCount: Long, val myRole: String? = null)

@Serializable
data class CreateGroupRequest(val name: String)

@Serializable
data class Page<T>(val items: List<T>, val nextCursor: String? = null)

@Serializable
data class SharingUpdate(val sharingEnabled: Boolean, val shareLiveLocation: Boolean, val durationMinutes: Int? = null)

@Serializable
data class SharingDto(
    val groupId: String,
    val sharingEnabled: Boolean,
    val shareLiveLocation: Boolean,
    val expiresAt: String? = null,
    val updatedAt: String? = null,
)

/** A group the caller currently shares with. [remainingSeconds] is computed by the server, so the phone clock is not trusted. */
@Serializable
data class SharedGroupDto(
    val groupId: String,
    val groupName: String,
    val expiresAt: String? = null,
    val remainingSeconds: Long? = null,
    val updatedAt: String? = null,
)

@Serializable
data class SharedGroupsDto(val items: List<SharedGroupDto>)

@Serializable
data class LocationDto(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double? = null,
    val recordedAt: String,
)

@Serializable
data class LatestLocationDto(val userId: String, val displayName: String, val location: LocationDto? = null)

@Serializable
data class ProblemDto(val code: String? = null, val title: String? = null, val detail: String? = null)
