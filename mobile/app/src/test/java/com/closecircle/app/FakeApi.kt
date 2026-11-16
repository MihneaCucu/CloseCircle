package com.closecircle.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.closecircle.app.data.TokenStore
import com.closecircle.app.data.api.ApiException
import com.closecircle.app.data.api.AuthResponse
import com.closecircle.app.data.api.CloseCircleApi
import com.closecircle.app.data.api.GroupDto
import com.closecircle.app.data.api.LatestLocationDto
import com.closecircle.app.data.api.SharedGroupDto
import com.closecircle.app.data.api.SharingDto
import com.closecircle.app.data.api.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** An in-memory DataStore: the file-backed one cannot replace its file on Windows, where developers run these tests. */
class MemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

class MemoryTokenStore(var token: String? = null) : TokenStore {
    override suspend fun read() = token
    override suspend fun write(token: String?) {
        this.token = token
    }
}

/** An in-memory backend: records every call and can be told to fail the next one. */
class FakeApi : CloseCircleApi {
    val calls = mutableListOf<String>()
    var groups = mutableListOf<GroupDto>()
    var shared = mutableListOf<SharedGroupDto>()
    var latest = mutableMapOf<String, List<LatestLocationDto>>()
    var failNext: ApiException? = null

    private fun call(name: String) {
        calls += name
        failNext?.let {
            failNext = null
            throw it
        }
    }

    private fun auth(email: String, name: String) = AuthResponse("token-$email", "2026-11-16T22:00:00Z", UserDto("u1", email, name))

    override suspend fun register(email: String, password: String, displayName: String): AuthResponse {
        call("register $email")
        return auth(email, displayName)
    }

    override suspend fun login(email: String, password: String): AuthResponse {
        call("login $email")
        return auth(email, "Ana")
    }

    override suspend fun groups(): List<GroupDto> {
        call("groups")
        return groups.toList()
    }

    override suspend fun createGroup(name: String): GroupDto {
        call("createGroup $name")
        return GroupDto("g-${groups.size + 1}", name, 1, "OWNER").also { groups += it }
    }

    override suspend fun mySharing(): List<SharedGroupDto> {
        call("mySharing")
        return shared.toList()
    }

    override suspend fun startSharing(groupId: String, durationMinutes: Int?): SharingDto {
        call("start $groupId $durationMinutes")
        val g = groups.first { it.groupId == groupId }
        shared.removeAll { it.groupId == groupId }
        shared += SharedGroupDto(groupId, g.name, null, null, null)
        return SharingDto(groupId, true, true)
    }

    override suspend fun stopSharing(groupId: String): SharingDto {
        call("stop $groupId")
        shared.removeAll { it.groupId == groupId }
        return SharingDto(groupId, false, false)
    }

    override suspend fun latestLocations(groupId: String): List<LatestLocationDto> {
        call("latest $groupId")
        return latest[groupId].orEmpty()
    }
}
