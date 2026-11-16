package com.closecircle.app

import com.closecircle.app.data.AppRepository
import com.closecircle.app.data.DurId
import com.closecircle.app.data.Session
import com.closecircle.app.data.api.ApiException
import com.closecircle.app.data.api.GroupDto
import com.closecircle.app.data.api.LatestLocationDto
import com.closecircle.app.data.api.LocationDto
import com.closecircle.app.data.api.SharedGroupDto
import com.closecircle.app.ui.Auth
import com.closecircle.app.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val NOW = 1_800_000_000_000L

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val api = FakeApi()
    private val store = MemoryTokenStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        api.groups = mutableListOf(GroupDto("g1", "Familia", 3, "OWNER"), GroupDto("g2", "Bunicii", 2, "MEMBER"))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = AppViewModel(api, AppRepository(api), Session(store), clock = { NOW }, pollMs = null)

    private fun signedIn(): AppViewModel {
        store.token = "saved"
        return vm()
    }

    @Test
    fun withoutASavedTokenTheUserIsSignedOut() {
        assertEquals(Auth.SignedOut(), vm().ui.value.auth)
    }

    @Test
    fun aSavedTokenSignsInAndLoadsTheGroups() {
        val v = signedIn()
        assertEquals(Auth.SignedIn, v.ui.value.auth)
        assertEquals(listOf("Familia", "Bunicii"), v.ui.value.groups.map { it.name })
        assertEquals("g1", v.ui.value.groupId)
        assertTrue(v.ui.value.loaded)
        assertTrue(v.ui.value.invites.isNotEmpty()) // pending-backend data is available
    }

    @Test
    fun signInStoresTheTokenAndLoads() {
        val v = vm()
        v.signIn(" ana@b.ro ", "parola")
        assertEquals(Auth.SignedIn, v.ui.value.auth)
        assertEquals("token-ana@b.ro", store.token) // the email is trimmed before it is sent
        assertTrue(v.ui.value.loaded)
    }

    @Test
    fun signInErrorsAreShownInRomanian() {
        val v = vm()
        api.failNext = ApiException(401, "INVALID_CREDENTIALS")
        v.signIn("a@b.ro", "gresita")
        assertEquals(Auth.SignedOut(error = "Email sau parolă greșite."), v.ui.value.auth)
        api.failNext = ApiException(0, "NETWORK")
        v.signIn("a@b.ro", "x")
        assertEquals("Nu mă pot conecta la server.", (v.ui.value.auth as Auth.SignedOut).error)
        v.signIn("", "x")
        assertEquals("Introdu emailul și parola.", (v.ui.value.auth as Auth.SignedOut).error)
    }

    @Test
    fun registerChecksTheBackendRulesFirst() {
        val v = vm()
        v.register("a@b.ro", "scurta", "Ana")
        assertEquals("Parola trebuie să aibă cel puțin 12 caractere.", (v.ui.value.auth as Auth.SignedOut).error)
        v.register("a@b.ro", "o-parola-destul-de-lunga", "  ")
        assertEquals("Scrie numele tău.", (v.ui.value.auth as Auth.SignedOut).error)
        assertTrue(api.calls.none { it.startsWith("register") })
        v.register("a@b.ro", "o-parola-destul-de-lunga", "Ana")
        assertEquals(Auth.SignedIn, v.ui.value.auth)
        assertEquals("register a@b.ro", api.calls.first { it.startsWith("register") })
    }

    @Test
    fun anExistingEmailGetsItsOwnMessage() {
        val v = vm()
        api.failNext = ApiException(409, "EMAIL_TAKEN")
        v.register("a@b.ro", "o-parola-destul-de-lunga", "Ana")
        assertEquals("Există deja un cont cu acest email.", (v.ui.value.auth as Auth.SignedOut).error)
    }

    @Test
    fun anExpiredSessionSendsTheUserBackToSignIn() {
        val v = signedIn()
        api.failNext = ApiException(401, null)
        v.refresh()
        assertEquals(Auth.SignedOut(error = "Sesiunea a expirat. Autentifică-te din nou."), v.ui.value.auth)
        assertNull(store.token)
        assertTrue(v.ui.value.groups.isEmpty())
    }

    @Test
    fun signOutClearsTheToken() {
        val v = signedIn()
        v.signOut()
        assertEquals(Auth.SignedOut(), v.ui.value.auth)
        assertNull(store.token)
    }

    @Test
    fun otherErrorsBecomeAMessage() {
        val v = signedIn()
        api.failNext = ApiException(0, "NETWORK")
        v.refresh()
        assertEquals("Nu mă pot conecta la server.", v.ui.value.toast)
        assertEquals(Auth.SignedIn, v.ui.value.auth)
        v.consumeToast()
        assertNull(v.ui.value.toast)
        assertEquals("Datele trimise nu sunt valide.", AppViewModel.message(ApiException(400, null)))
        assertEquals("Nu ai voie să faci asta.", AppViewModel.message(ApiException(403, null)))
        assertEquals("Grupul nu mai există sau nu mai ești membru.", AppViewModel.message(ApiException(404, null)))
        assertEquals("Prea multe cereri. Încearcă mai târziu.", AppViewModel.message(ApiException(429, null)))
        assertEquals("Eroare de server (BOOM).", AppViewModel.message(ApiException(500, "BOOM")))
        assertEquals("Emailul sau parola nu sunt valide.", AppViewModel.authMessage(ApiException(400, null)))
    }

    @Test
    fun selectingAGroupLoadsItsMembers() {
        api.latest["g2"] = listOf(LatestLocationDto("u9", "Ioana", LocationDto(44.0, 26.0, 10.0, java.time.Instant.ofEpochMilli(NOW).toString())))
        val v = signedIn()
        assertTrue(v.ui.value.members.isEmpty())
        v.selectGroup("g2")
        assertEquals("g2", v.ui.value.groupId)
        assertEquals(listOf("Ioana"), v.ui.value.members.map { it.name })
    }

    @Test
    fun aGroupThatDisappearedFallsBackToTheFirst() {
        val v = signedIn()
        v.selectGroup("g2")
        api.groups.removeAll { it.groupId == "g2" }
        v.refresh()
        assertEquals("g1", v.ui.value.groupId)
    }

    @Test
    fun togglingSharingCallsTheRightEndpointAndRefreshes() {
        val v = signedIn()
        val g1 = v.ui.value.groups.first()
        v.toggleGroup(g1)
        assertTrue(api.calls.contains("start g1 60")) // starts with the shortest quick option
        assertTrue(v.ui.value.groups.first().on)
        assertEquals("Partajare activată în Familia", v.ui.value.toast)
        v.toggleGroup(v.ui.value.groups.first())
        assertTrue(api.calls.contains("stop g1"))
        assertFalse(v.ui.value.groups.first().on)
    }

    @Test
    fun pickingADurationSendsItAndShowsIt() {
        val v = signedIn()
        v.pickDuration(v.ui.value.groups.first(), DurId.H8, "8 ore")
        assertTrue(api.calls.contains("start g1 480"))
        assertEquals("Durată trimisă: 8 ore", v.ui.value.toast)
    }

    @Test
    fun customDurationIsRangeChecked() {
        val v = signedIn()
        val g = v.ui.value.groups.first()
        assertFalse(v.applyCustom(g, 4))
        assertFalse(v.applyCustom(g, 7 * 24 * 60 + 1))
        assertTrue(api.calls.none { it.startsWith("start") })
        assertTrue(v.applyCustom(g, 90))
        assertTrue(api.calls.contains("start g1 90"))
        assertEquals("Durată trimisă: 1 h 30 min", v.ui.value.toast)
    }

    @Test
    fun stopAllRevokesOnlyTheGroupsThatAreOn() {
        api.shared = mutableListOf(SharedGroupDto("g1", "Familia", null, null, null))
        val v = signedIn()
        v.stopAll()
        assertEquals(listOf("stop g1"), api.calls.filter { it.startsWith("stop") })
        assertTrue(v.ui.value.groups.none { it.on })
        assertEquals("Partajarea a fost oprită în toate grupurile", v.ui.value.toast)
    }

    @Test
    fun createGroupValidatesTheName() {
        val v = signedIn()
        assertEquals("Scrie un nume pentru grup.", v.createGroup("   "))
        assertEquals("Maximum 100 de caractere.", v.createGroup("x".repeat(101)))
        assertNull(v.createGroup("  Prieteni  "))
        assertTrue(api.calls.contains("createGroup Prieteni"))
        assertTrue(v.ui.value.groups.any { it.name == "Prieteni" })
    }

    @Test
    fun invitationsLeaveTheListWhenAnswered() {
        val v = signedIn()
        val id = v.ui.value.invites.first().id
        v.answerInvite(id, true)
        assertTrue(v.ui.value.invites.isEmpty())
        assertEquals("Invitație acceptată · partajarea rămâne oprită", v.ui.value.toast)
        val w = signedIn()
        w.answerInvite(w.ui.value.invites.first().id, false)
        assertEquals("Invitație refuzată", w.ui.value.toast)
    }

    @Test
    fun zoneAlertChoicesToggle() {
        val v = signedIn()
        val key = "acasa-ana-in"
        assertTrue(key in v.ui.value.zoneNotify)
        v.toggleZoneNotify(key)
        assertFalse(key in v.ui.value.zoneNotify)
        v.toggleZoneNotify(key)
        assertTrue(key in v.ui.value.zoneNotify)
        v.showMessage("salut")
        assertEquals("salut", v.ui.value.toast)
    }
}
