package com.closecircle.app

import com.closecircle.app.data.api.ApiClient
import com.closecircle.app.data.api.ApiException
import com.closecircle.app.data.api.HttpCloseCircleApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiTest {
    private lateinit var server: MockWebServer
    private var token: String? = "t0ken"
    private lateinit var api: HttpCloseCircleApi

    @Before
    fun start() {
        server = MockWebServer().also { it.start() }
        api = HttpCloseCircleApi(ApiClient(server.url("/").toString(), token = { token }))
    }

    @After
    fun stop() = server.shutdown()

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private val sharing = """{"groupId":"g1","userId":"u1","sharingEnabled":true,"shareLiveLocation":true,"expiresAt":"2026-11-16T10:00:00Z","updatedAt":"2026-11-16T09:00:00Z"}"""

    @Test
    fun registerAndLoginSendCredentialsAndDecodeTheToken() = runBlocking {
        val auth = """{"accessToken":"abc","tokenType":"Bearer","expiresAt":"2026-11-16T10:00:00Z","user":{"userId":"u1","email":"a@b.ro","displayName":"Ana","createdAt":"2026-11-16T09:00:00Z"}}"""
        server.enqueue(json(auth, 201))
        server.enqueue(json(auth))
        val r = api.register("a@b.ro", "parola-lunga-123", "Ana")
        val l = api.login("a@b.ro", "parola-lunga-123")
        assertEquals("abc", r.accessToken)
        assertEquals("Ana", l.user.displayName)
        val first = server.takeRequest()
        assertEquals("POST", first.method)
        assertEquals("/api/v1/auth/register", first.path)
        assertTrue(first.body.readUtf8().contains("\"displayName\":\"Ana\""))
        assertEquals("/api/v1/auth/login", server.takeRequest().path)
    }

    @Test
    fun requestsCarryTheBearerTokenWhenThereIsOne() = runBlocking {
        server.enqueue(json("""{"items":[],"nextCursor":null}"""))
        api.groups()
        assertEquals("Bearer t0ken", server.takeRequest().getHeader("Authorization"))
        token = null
        server.enqueue(json("""{"items":[]}"""))
        api.groups()
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun groupsAndCreateGroup() = runBlocking {
        val group = """{"groupId":"g1","name":"Familia","createdBy":"u1","createdAt":"2026-11-16T09:00:00Z","myRole":"OWNER","memberCount":2}"""
        server.enqueue(json("""{"items":[$group],"nextCursor":null,"extra":"ignored"}"""))
        server.enqueue(json(group, 201))
        assertEquals("Familia", api.groups().single().name)
        assertEquals("/api/v1/groups?limit=100", server.takeRequest().path)
        assertEquals(2L, api.createGroup("Familia").memberCount)
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("""{"name":"Familia"}""", create.body.readUtf8())
    }

    @Test
    fun startSharingSendsBothFlagsAndTheDuration() = runBlocking {
        server.enqueue(json(sharing))
        server.enqueue(json(sharing))
        api.startSharing("g1", 90)
        val withDuration = server.takeRequest()
        assertEquals("PUT", withDuration.method)
        assertEquals("/api/v1/groups/g1/me/sharing", withDuration.path)
        assertEquals("""{"sharingEnabled":true,"shareLiveLocation":true,"durationMinutes":90}""", withDuration.body.readUtf8())
        api.startSharing("g1", null)
        // no time limit: the field is left out, not sent as null
        assertEquals("""{"sharingEnabled":true,"shareLiveLocation":true}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun stopSharingAndMySharing() = runBlocking {
        server.enqueue(json("""{"groupId":"g1","userId":"u1","sharingEnabled":false,"shareLiveLocation":false}"""))
        server.enqueue(json("""{"items":[{"groupId":"g1","groupName":"Familia","expiresAt":"2026-11-16T10:00:00Z","remainingSeconds":1800,"updatedAt":"2026-11-16T09:00:00Z"}]}"""))
        assertFalse(api.stopSharing("g1").sharingEnabled)
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/api/v1/groups/g1/me/sharing", delete.path)
        assertEquals(1800L, api.mySharing().single().remainingSeconds)
        assertEquals("/api/v1/users/me/sharing", server.takeRequest().path)
    }

    @Test
    fun latestLocationsDecodeMembersWithAndWithoutAPoint() = runBlocking {
        server.enqueue(
            json(
                """{"items":[
                {"userId":"u2","displayName":"Ana","location":{"locationId":"l1","groupId":"g1","userId":"u2","latitude":44.43,"longitude":26.1,"accuracyM":12.5,"recordedAt":"2026-11-16T09:00:00Z","receivedAt":"2026-11-16T09:00:01Z"},"freshness":{"status":"RECENT"}},
                {"userId":"u3","displayName":"Mihai","location":null}],"nextCursor":null}""",
            ),
        )
        val items = api.latestLocations("g1")
        assertEquals(12.5, items[0].location?.accuracyM!!, 0.0)
        assertNull(items[1].location)
        assertEquals("/api/v1/groups/g1/locations/latest?limit=100", server.takeRequest().path)
    }

    @Test
    fun problemDetailsBecomeAnApiExceptionWithTheCode() = runBlocking {
        server.enqueue(json("""{"type":"x","title":"Not found","status":404,"code":"GROUP_NOT_FOUND"}""", 404))
        try {
            api.stopSharing("nope")
            fail("expected an exception")
        } catch (e: ApiException) {
            assertEquals(404, e.status)
            assertEquals("GROUP_NOT_FOUND", e.code)
            assertFalse(e.unauthorized)
            assertFalse(e.unreachable)
        }
    }

    @Test
    fun anEmptyOrUnreadableErrorBodyStillGivesTheStatus() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(json("<html>oops</html>", 500))
        try {
            api.groups()
            fail()
        } catch (e: ApiException) {
            assertTrue(e.unauthorized)
            assertNull(e.code)
        }
        try {
            api.groups()
            fail()
        } catch (e: ApiException) {
            assertEquals(500, e.status)
        }
    }

    @Test
    fun anUnreachableServerIsStatusZero() = runBlocking {
        server.shutdown()
        try {
            api.groups()
            fail()
        } catch (e: ApiException) {
            assertTrue(e.unreachable)
            assertEquals("NETWORK", e.code)
        }
    }
}
