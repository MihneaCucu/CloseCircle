package com.closecircle.app

import com.closecircle.app.data.AppRepository
import com.closecircle.app.data.DurId
import com.closecircle.app.data.Session
import com.closecircle.app.data.api.ApiException
import com.closecircle.app.data.api.GroupDto
import com.closecircle.app.data.api.InvitationDto
import com.closecircle.app.data.api.InviterDto
import com.closecircle.app.data.api.LatestLocationDto
import com.closecircle.app.data.api.LocationDto
import com.closecircle.app.data.api.ReceivedInvitationDto
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

    private fun received(id: String = "i1") =
        ReceivedInvitationDto(id, "g9", "Vacanța", InviterDto("u2", "Ana"), "PENDING", "2026-11-16T09:00:00Z", "2026-11-23T09:00:00Z")

    private fun sentInvite(id: String, email: String, status: String = "PENDING") =
        InvitationDto(id, "g1", email, status, "2026-11-16T09:00:00Z", "2026-11-23T09:00:00Z")

    @Test
    fun receivedInvitationsComeFromTheServer() {
        api.received = mutableListOf(received())
        val v = signedIn()
        val inv = v.ui.value.invites.single()
        assertEquals("Ana", inv.by)
        assertEquals("Vacanța", inv.group)
        assertEquals(java.time.Instant.parse("2026-11-23T09:00:00Z").toEpochMilli(), inv.expiresAt)
    }

    @Test
    fun answeringAnInvitationSaysItIsNotAvailableYet() {
        api.received = mutableListOf(received())
        val v = signedIn()
        v.answerInvite()
        assertEquals("Acceptarea și refuzul invitațiilor vor fi disponibile în curând.", v.ui.value.toast)
        assertEquals(1, v.ui.value.invites.size) // nothing was sent, so the invitation stays
    }

    @Test
    fun anOwnerSeesTheSentInvitationsAndAMemberDoesNot() {
        api.sent["g1"] = listOf(sentInvite("s1", "tudor@b.ro"), sentInvite("s2", "elena@b.ro", "EXPIRED"))
        val v = signedIn() // g1: OWNER
        assertEquals(listOf("tudor@b.ro", "elena@b.ro"), v.ui.value.sent.map { it.email })
        v.selectGroup("g2") // MEMBER
        assertTrue(v.ui.value.sent.isEmpty())
        assertTrue(api.calls.none { it == "sent g2" })
    }

    @Test
    fun inviteChecksTheEmailThenSendsAndRefreshes() {
        val v = signedIn()
        val g = v.ui.value.group!!
        assertEquals("Scrie o adresă de email validă.", v.invite(g, "nu-e-email"))
        assertEquals("Scrie o adresă de email validă.", v.invite(g, "  "))
        assertTrue(api.calls.none { it.startsWith("invite") })
        assertNull(v.invite(g, "  tudor@b.ro "))
        assertTrue(api.calls.contains("invite g1 tudor@b.ro")) // trimmed before it is sent
        assertEquals("Invitație trimisă către tudor@b.ro", v.ui.value.toast)
        assertEquals(listOf("tudor@b.ro"), v.ui.value.sent.map { it.email })
    }

    @Test
    fun inviteConflictsGetTheirOwnMessages() {
        val v = signedIn()
        val g = v.ui.value.group!!
        api.failNext = ApiException(409, "ALREADY_MEMBER")
        v.invite(g, "a@b.ro")
        assertEquals("Persoana e deja membră a grupului.", v.ui.value.toast)
        api.failNext = ApiException(409, "INVITATION_ALREADY_PENDING")
        v.invite(g, "a@b.ro")
        assertEquals("Are deja o invitație în așteptare.", v.ui.value.toast)
    }

    @Test
    fun aServerWithoutInvitationRoutesDoesNotBreakTheGroups() {
        api.failReceived = ApiException(404, "NOT_FOUND")
        val v = signedIn()
        assertEquals(Auth.SignedIn, v.ui.value.auth)
        assertEquals(2, v.ui.value.groups.size)
        assertTrue(v.ui.value.invites.isEmpty())
        assertNull(v.ui.value.toast)
        api.failReceived = ApiException(401, null)
        v.refresh()
        assertTrue(v.ui.value.auth is Auth.SignedOut) // an expired token is still caught here
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
