package com.closecircle.app

import com.closecircle.app.data.AppRepository
import com.closecircle.app.data.DataStoreTokenStore
import com.closecircle.app.data.DurId
import com.closecircle.app.data.Session
import com.closecircle.app.data.api.GroupDto
import com.closecircle.app.data.api.LatestLocationDto
import com.closecircle.app.data.api.LocationDto
import com.closecircle.app.data.api.SharedGroupDto
import java.time.Instant
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
        assertEquals(0, mihai.x) // no position, so not drawn on the map
    }

    @Test
    fun aPointRecordedInTheFutureIsNotNegative() = runBlocking {
        api.latest["g1"] = listOf(latest("u1", "Ana", 44.0, minutesAgo = -5, accuracy = null))
        val ana = repo.members("g1", NOW).single()
        assertEquals(0, ana.minutesAgo)
        assertEquals("Precizie necunoscută", ana.accuracy)
    }

    @Test
    fun membersAreSpreadByCoordinatesWithNorthUp() = runBlocking {
        api.latest["g1"] = listOf(latest("north-west", "A", 45.0, 25.0), latest("south-east", "B", 44.0, 27.0))
        val (nw, se) = repo.members("g1", NOW)
        assertTrue(nw.x < se.x)
        assertTrue(nw.y < se.y) // larger latitude is higher on screen, so a smaller y
        assertTrue(nw.x in 1..99 && se.y in 1..99)
    }

    @Test
    fun aSingleMemberSitsInTheMiddle() = runBlocking {
        api.latest["g1"] = listOf(latest("u1", "Ana", 44.4, 26.1))
        val ana = repo.members("g1", NOW).single()
        assertEquals(45, ana.x)
        assertTrue(ana.y in 40..80)
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
        val store = DataStoreTokenStore(MemoryDataStore())
        assertNull(store.read())
        store.write("jwt")
        assertEquals("jwt", store.read())
        store.write(null)
        assertNull(store.read())
    }
}
