package com.closecircle.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.closecircle.app.data.AppRepository
import com.closecircle.app.data.DataStoreTokenStore
import com.closecircle.app.data.DurId
import com.closecircle.app.data.Session
import com.closecircle.app.data.api.GroupDto
import com.closecircle.app.data.api.InvitationDto
import com.closecircle.app.data.api.InviterDto
import com.closecircle.app.data.api.LatestLocationDto
import com.closecircle.app.data.api.LocationDto
import com.closecircle.app.data.api.ReceivedInvitationDto
import com.closecircle.app.data.api.SharedGroupDto
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN = 60_000L
private const val NOW = 1_800_000_000_000L

class RepositoryTest {
    private val api = FakeApi()
    private val repo = AppRepository(api)

    private fun group(id: String, name: String = id, members: Long = 3) = GroupDto(id, name, members, "MEMBER")

    @Test
    fun groupsAreOnlyOnWhenTheServerListsThemAsShared() = runBlocking {
        api.groups = mutableListOf(group("g1"), group("g2"))
        api.shared = mutableListOf(SharedGroupDto("g1", "g1", "2026-11-16T10:00:00Z", 1800, "2026-11-16T09:00:00Z"))
        val (g1, g2) = repo.groups(NOW)
        assertTrue(g1.on)
        assertFalse(g2.on)
        assertNull(g2.endsAt)
    }

    @Test
    fun remainingTimeAndLengthComeFromTheServerNumbers() = runBlocking {
        api.groups = mutableListOf(group("g1"))
        api.shared = mutableListOf(SharedGroupDto("g1", "g1", "2026-11-16T10:00:00Z", 1800, "2026-11-16T09:00:00Z"))
        val g = repo.groups(NOW).single()
        assertEquals(NOW + 1800_000, g.endsAt)
        assertEquals(DurId.H1, g.durId)
        assertEquals(g.endsAt!! - 60 * MIN, g.startedAt) // started one total length before it ends
        assertEquals(3, g.members)
    }

    @Test
    fun noExpiryMeansPermanentAndOddLengthsAreCustom() = runBlocking {
        api.groups = mutableListOf(group("g1"), group("g2"))
        api.shared = mutableListOf(
            SharedGroupDto("g1", "g1", null, null, "2026-11-16T09:00:00Z"),
            SharedGroupDto("g2", "g2", "2026-11-16T12:30:00Z", 600, "2026-11-16T09:00:00Z"),
        )
        val (g1, g2) = repo.groups(NOW)
        assertEquals(DurId.ALWAYS, g1.durId)
        assertNull(g1.endsAt)
        assertEquals(DurId.CUSTOM, g2.durId)
        assertEquals("3 h 30 min", g2.customLabel)
    }

    private fun latest(id: String, name: String, lat: Double?, lon: Double = 0.0, minutesAgo: Int = 0, accuracy: Double? = null) =
        LatestLocationDto(id, name, lat?.let { LocationDto(it, lon, accuracy, Instant.ofEpochMilli(NOW - minutesAgo * MIN).toString()) })

    @Test
    fun membersShowAgeAccuracyAndInitials() = runBlocking {
        api.latest["g1"] = listOf(latest("u1", "Ana Popescu", 44.0, 26.0, minutesAgo = 7, accuracy = 12.5), latest("u2", "Mihai", null))
        val (ana, mihai) = repo.members("g1", NOW)
        assertEquals("AP", ana.initials)
        assertEquals(7, ana.minutesAgo)
        assertEquals("±13 m", ana.accuracy)
        assertNull(mihai.minutesAgo)
        assertEquals("Nicio actualizare", mihai.accuracy)
    }

    @Test
    fun aPointRecordedInTheFutureIsNotNegative() = runBlocking {
        api.latest["g1"] = listOf(latest("u1", "Ana", 44.0, minutesAgo = -5, accuracy = null))
        val ana = repo.members("g1", NOW).single()
        assertEquals(0, ana.minutesAgo)
        assertEquals("Precizie necunoscută", ana.accuracy)
    }

    @Test
    fun membersCarryTheirRealCoordinates() = runBlocking {
        api.latest["g1"] = listOf(latest("u1", "Ana", 44.4268, 26.1025), latest("u2", "Mihai", null))
        val (ana, mihai) = repo.members("g1", NOW)
        assertEquals(44.4268, ana.latitude!!, 0.0)
        assertEquals(26.1025, ana.longitude!!, 0.0)
        assertNull(mihai.latitude)
        assertNull(mihai.longitude)
    }

    @Test
    fun noOneSharingGivesAnEmptyList() = runBlocking {
        assertTrue(repo.members("g1", NOW).isEmpty())
    }

    @Test
    fun startSharingTranslatesChipsToMinutes() = runBlocking {
        api.groups = mutableListOf(group("g1"))
        repo.startSharing("g1", DurId.H4, null)
        repo.startSharing("g1", DurId.ALWAYS, null)
        repo.startSharing("g1", DurId.CUSTOM, 95)
        assertEquals(listOf("start g1 240", "start g1 null", "start g1 95"), api.calls)
    }

    @Test
    fun stopSharingAndCreateGroupReachTheApi() = runBlocking {
        repo.createGroup("Familia")
        api.shared = mutableListOf(SharedGroupDto("g-1", "Familia"))
        repo.stopSharing("g-1")
        assertEquals(listOf("createGroup Familia", "stop g-1"), api.calls)
    }

    @Test
    fun sessionKeepsTheTokenAcrossRestarts() = runBlocking {
        val store = MemoryTokenStore()
        val first = Session(store)
        first.load()
        assertNull(first.token)
        first.signIn("abc")
        assertEquals("abc", first.token)
        val restarted = Session(store)
        restarted.load()
        assertEquals("abc", restarted.token)
        restarted.signOut()
        assertNull(restarted.token)
        assertNull(store.token)
    }

    @Test
    fun theDataStoreTokenStoreWritesReadsAndClears() = runBlocking {
        // an in-memory DataStore: the real file-backed one cannot replace its file on Windows, where developers run these tests
        val memory = object : DataStore<Preferences> {
            private val state = MutableStateFlow(emptyPreferences())
            override val data: Flow<Preferences> = state
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                transform(state.value).also { state.value = it }
        }
        val store = DataStoreTokenStore(memory)
        assertNull(store.read())
        store.write("jwt")
        assertEquals("jwt", store.read())
        store.write(null)
        assertNull(store.read())
    }

    @Test
    fun groupsKeepTheCallersRoleSoTheAppKnowsWhoMayInvite() = runBlocking {
        api.groups = mutableListOf(GroupDto("g1", "A", 2, "OWNER"), GroupDto("g2", "B", 2, "ADMIN"), GroupDto("g3", "C", 2, "MEMBER"))
        val roles = repo.groups(NOW).associate { it.id to it.canInvite }
        assertEquals(mapOf("g1" to true, "g2" to true, "g3" to false), roles)
    }

    @Test
    fun invitationsAreMappedToWhatTheScreensShow() = runBlocking {
        api.received = mutableListOf(ReceivedInvitationDto("i1", "g9", "Sinaia", InviterDto("u2", "Ana"), "PENDING", "2026-11-16T09:00:00Z", "2026-11-23T09:00:00Z"))
        api.sent["g1"] = listOf(InvitationDto("s1", "g1", "tudor@b.ro", "EXPIRED", "2026-11-01T09:00:00Z", "2026-11-08T09:00:00Z"))
        val received = repo.receivedInvites().single()
        assertEquals("Ana", received.by)
        assertEquals("Sinaia", received.group)
        assertEquals(Instant.parse("2026-11-23T09:00:00Z").toEpochMilli(), received.expiresAt)
        val sent = repo.sentInvites("g1").single()
        assertEquals("tudor@b.ro", sent.email)
        assertEquals("EXPIRED", sent.status)
        repo.invite("g1", "elena@b.ro")
        assertTrue(api.calls.contains("invite g1 elena@b.ro"))
    }
}
