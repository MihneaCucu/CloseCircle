package com.closecircle.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.closecircle.app.CloseCircleApp
import com.closecircle.app.data.AlertItem
import com.closecircle.app.data.AppRepository
import com.closecircle.app.data.DurId
import com.closecircle.app.data.Group
import com.closecircle.app.data.Invite
import com.closecircle.app.data.Member
import com.closecircle.app.data.PendingBackendData
import com.closecircle.app.data.SentInvite
import com.closecircle.app.data.Session
import com.closecircle.app.data.UserPrefs
import com.closecircle.app.data.Zone
import com.closecircle.app.data.api.ApiException
import com.closecircle.app.data.api.CloseCircleApi
import com.closecircle.app.domain.lengthText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val POLL_MS = 30_000L
private const val MAX_CUSTOM_MIN = 7 * 24 * 60 // the backend accepts 1 minute to 7 days
const val MIN_CUSTOM_MIN = 5
const val MIN_PASSWORD_CHARS = 12 // backend rule, checked here to save a round trip
private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") // light check; the backend validates the address for real

sealed interface Auth {
    data object Checking : Auth
    data class SignedOut(val error: String? = null, val busy: Boolean = false) : Auth
    data object SignedIn : Auth
}

data class UiState(
    val auth: Auth = Auth.Checking,
    val groups: List<Group> = emptyList(),
    val members: List<Member> = emptyList(),
    val groupId: String? = null,
    /** True once the first answer from the server arrived, so an empty list means "no groups", not "still loading". */
    val loaded: Boolean = false,
    /** Elderly mode: large text, high contrast, big buttons and only the essential actions. */
    val elder: Boolean = false,
    val now: Long = 0,
    val toast: String? = null,
    val invites: List<Invite> = emptyList(),
    /** Invitations sent from the selected group; only loaded for its owner or admins. */
    val sent: List<SentInvite> = emptyList(),
    val alerts: List<AlertItem> = emptyList(),
    val zones: List<Zone> = emptyList(),
    val zoneNotify: Set<String> = setOf("acasa-ana-in", "acasa-ana-out", "acasa-mihai-in", "scoala-ana-in", "scoala-ana-out"),
) {
    val group: Group? get() = groups.firstOrNull { it.id == groupId }
}

class AppViewModel(
    private val api: CloseCircleApi,
    private val repo: AppRepository,
    private val session: Session,
    private val prefs: UserPrefs,
    private val clock: () -> Long = System::currentTimeMillis,
    pollMs: Long? = POLL_MS,
) : ViewModel() {
    private val _ui = MutableStateFlow(
        UiState(now = clock(), alerts = PendingBackendData.alerts(), zones = PendingBackendData.zones()),
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { prefs.elder.collect { on -> _ui.update { it.copy(elder = on) } } }
        viewModelScope.launch {
            session.load()
            if (session.token != null) {
                _ui.update { it.copy(auth = Auth.SignedIn) }
                refreshNow()
            } else {
                _ui.update { it.copy(auth = Auth.SignedOut()) }
            }
        }
        if (pollMs != null) {
            viewModelScope.launch {
                while (true) {
                    delay(pollMs)
                    if (_ui.value.auth == Auth.SignedIn) refreshNow()
                }
            }
        }
    }

    // sign in / out

    fun signIn(email: String, password: String) = authenticate(email, password) { api.login(email.trim(), password) }

    fun register(email: String, password: String, displayName: String) {
        when {
            displayName.isBlank() -> return failAuth("Scrie numele tău.")
            password.length < MIN_PASSWORD_CHARS -> return failAuth("Parola trebuie să aibă cel puțin $MIN_PASSWORD_CHARS caractere.")
        }
        authenticate(email, password) { api.register(email.trim(), password, displayName.trim()) }
    }

    private fun failAuth(message: String) = _ui.update { it.copy(auth = Auth.SignedOut(error = message)) }

    private fun authenticate(email: String, password: String, call: suspend () -> com.closecircle.app.data.api.AuthResponse) {
        if (email.isBlank() || password.isEmpty()) return failAuth("Introdu emailul și parola.")
        _ui.update { it.copy(auth = Auth.SignedOut(busy = true)) }
        viewModelScope.launch {
            try {
                session.signIn(call().accessToken)
                _ui.update { it.copy(auth = Auth.SignedIn) }
                refreshNow()
            } catch (e: ApiException) {
                failAuth(authMessage(e))
            }
        }
    }

    fun signOut(message: String? = null) {
        viewModelScope.launch {
            session.signOut()
            _ui.update { UiState(auth = Auth.SignedOut(error = message), elder = it.elder, now = clock(), invites = PendingBackendData.invites(), alerts = PendingBackendData.alerts(), zones = PendingBackendData.zones()) }
        }
    }

    // reading

    fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    private suspend fun refreshNow() {
        val now = clock()
        try {
            val groups = repo.groups(now)
            val id = _ui.value.groupId?.takeIf { sel -> groups.any { it.id == sel } } ?: groups.firstOrNull()?.id
            val members = if (id != null) repo.members(id, now) else emptyList()
            _ui.update { it.copy(groups = groups, groupId = id, members = members, now = now, loaded = true) }
        } catch (e: ApiException) {
            handle(e)
            return
        }
        refreshInvites()
    }

    /** Separate from the main refresh: a server that has no invitation routes yet must not break groups and members. */
    private suspend fun refreshInvites() {
        try {
            val invites = repo.receivedInvites()
            val group = _ui.value.group
            val sent = if (group != null && group.canInvite) repo.sentInvites(group.id) else emptyList()
            _ui.update { it.copy(invites = invites, sent = sent) }
        } catch (e: ApiException) {
            if (e.unauthorized) handle(e) // anything else keeps the lists as they were
        }
    }

    fun selectGroup(id: String) {
        _ui.update { it.copy(groupId = id, members = emptyList(), sent = emptyList()) }
        refresh()
    }

    // changing

    private fun act(success: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                refreshNow()
                if (success != null) toast(success)
            } catch (e: ApiException) {
                handle(e)
            }
        }
    }

    fun toggleGroup(g: Group) {
        if (g.on) act("Partajarea oprită în ${g.name}") { repo.stopSharing(g.id) }
        // Turning sharing on starts with the shortest quick option; the user then picks a duration.
        else act("Partajare activată în ${g.name}") { repo.startSharing(g.id, DurId.H1, null) }
    }

    fun pickDuration(g: Group, durId: DurId, label: String) = act("Durată trimisă: $label") { repo.startSharing(g.id, durId, null) }

    /** Returns false when the length is outside the allowed range (5 minutes to 7 days). */
    fun applyCustom(g: Group, minutes: Int): Boolean {
        if (minutes !in MIN_CUSTOM_MIN..MAX_CUSTOM_MIN) return false
        act("Durată trimisă: ${lengthText(minutes)}") { repo.startSharing(g.id, DurId.CUSTOM, minutes) }
        return true
    }

    /** There is no "stop everywhere" endpoint, so this revokes each group that is on, one call per group. */
    fun stopAll() {
        val on = _ui.value.groups.filter { it.on }
        act("Partajarea a fost oprită în toate grupurile") { on.forEach { repo.stopSharing(it.id) } }
    }

    /** Returns an error message, or null when the request was sent. */
    fun createGroup(raw: String): String? {
        val name = raw.trim()
        if (name.isEmpty()) return "Scrie un nume pentru grup."
        if (name.length > 100) return "Maximum 100 de caractere."
        act("Grup creat: $name") { repo.createGroup(name) }
        return null
    }

    /** Returns an error message, or null when the invitation was sent. Owner or admin only; the backend enforces it. */
    fun invite(g: Group, raw: String): String? {
        val email = raw.trim()
        if (!EMAIL.matches(email)) return "Scrie o adresă de email validă."
        act("Invitație trimisă către $email") { repo.invite(g.id, email) }
        return null
    }

    // not backed by the server yet

    // shortcut: accepting or declining has no endpoint on dev yet (SCRUM-13); call it here once it lands.
    fun answerInvite() = toast("Acceptarea și refuzul invitațiilor vor fi disponibile în curând.")

    fun toggleZoneNotify(key: String) =
        _ui.update { it.copy(zoneNotify = if (key in it.zoneNotify) it.zoneNotify - key else it.zoneNotify + key) }

    // settings

    fun setElder(on: Boolean) {
        viewModelScope.launch { prefs.setElder(on) }
        toast(if (on) "Mod pentru persoane în vârstă activat" else "Interfața standard a fost restabilită")
    }

    // messages

    fun showMessage(msg: String) = toast(msg)
    fun consumeToast() = _ui.update { it.copy(toast = null) }
    private fun toast(msg: String) = _ui.update { it.copy(toast = msg) }

    private fun handle(e: ApiException) {
        if (e.unauthorized) signOut("Sesiunea a expirat. Autentifică-te din nou.") else toast(message(e))
    }

    companion object {
        fun message(e: ApiException): String = when {
            e.unreachable -> "Nu mă pot conecta la server."
            e.code == "ALREADY_MEMBER" -> "Persoana e deja membră a grupului."
            e.code == "INVITATION_ALREADY_PENDING" -> "Are deja o invitație în așteptare."
            e.status == 400 -> "Datele trimise nu sunt valide."
            e.status == 403 -> "Nu ai voie să faci asta."
            e.status == 404 -> "Grupul nu mai există sau nu mai ești membru."
            e.status == 429 -> "Prea multe cereri. Încearcă mai târziu."
            else -> "Eroare de server (${e.code ?: e.status})."
        }

        fun authMessage(e: ApiException): String = when {
            e.unreachable -> "Nu mă pot conecta la server."
            e.status == 401 -> "Email sau parolă greșite."
            e.status == 409 -> "Există deja un cont cu acest email."
            e.status == 400 -> "Emailul sau parola nu sunt valide."
            else -> message(e)
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CloseCircleApp
                AppViewModel(app.api, app.repository, app.session, app.prefs)
            }
        }
    }
}
