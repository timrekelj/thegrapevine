package si.timrekelj.thegrapevine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Mirrors `AppState.swift`: who is signed in, and which paper they belong to. */
class AppState(
    private val api: Api,
    private val store: SessionStore,
) : ViewModel() {

    var session by mutableStateOf<Session?>(null)
        private set
    var profile by mutableStateOf<Profile?>(null)
        private set
    var group by mutableStateOf<Group?>(null)
        private set
    var members by mutableStateOf<List<Member>>(emptyList())
        private set
    var checked by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    /** An invitation link that arrived before sign-in/onboarding finished. */
    var pendingInviteCode by mutableStateOf<String?>(null)

    /** A story link, opened once the paper is showing. */
    var pendingStoryId by mutableStateOf<String?>(null)

    /** The problem or idea being reported; the root shows its sheet while this is set. */
    var feedback by mutableStateOf<FeedbackDraft?>(null)
        private set

    private val sessionListener: (Session) -> Unit = { refreshed ->
        store.save(refreshed)
        session = refreshed
    }

    init {
        api.onSessionChanged = sessionListener
        restoreSession()
    }

    private fun restoreSession() {
        viewModelScope.launch {
            // The first read opens the Keystore-backed store, which is slow; keep it off the main thread.
            val stored = withContext(Dispatchers.IO) { store.load() }
            if (stored == null) {
                Telemetry.reset()
                OneSignalManager.logout()
                checked = true
                return@launch
            }
            if (Jwt.isExpired(stored.accessToken)) {
                val refreshed = try {
                    api.restore(stored)
                } catch (e: ApiError.Unauthorized) {
                    signOut()
                    checked = true
                    return@launch
                } catch (e: Exception) {
                    adopt(stored)
                    error = e.message
                    checked = true
                    return@launch
                }
                adopt(refreshed)
                withContext(Dispatchers.IO) { store.save(refreshed) }
            } else {
                adopt(stored)
            }
            loadProfileAndGroup()
            checked = true
        }
    }

    /** An invitation or story link; both wait until the app can show them. */
    fun handleDeepLink(uri: String) {
        if (OAuth.isCallback(uri)) {
            viewModelScope.launch {
                // Kept in the encrypted store, so it survives the browser trip even if the process doesn't.
                val verifier = withContext(Dispatchers.IO) { store.takeOAuthVerifier() } ?: return@launch
                try {
                    finishSignIn(api.exchangeCode(OAuth.code(uri), verifier))
                } catch (e: Exception) {
                    error = e.message
                }
            }
            return
        }
        when (val route = DeepLink.route(uri)) {
            is DeepLink.Route.Invite -> pendingInviteCode = route.code
            is DeepLink.Route.Story -> pendingStoryId = route.id
            null -> Unit
        }
    }

    suspend fun signIn(email: String) {
        error = null
        api.signInWithOtp(email)
    }

    /** Signs in with the code from the email. */
    suspend fun verifyCode(email: String, code: String) {
        error = null
        // Finish account loading even after sign-in removes the login screen.
        viewModelScope.async { finishSignIn(api.verifyOtp(email, code)) }.await()
    }

    suspend fun startOAuth(provider: AuthProvider): String {
        error = null
        val verifier = OAuth.verifier()
        withContext(Dispatchers.IO) { store.saveOAuthVerifier(verifier) }
        return OAuth.authorizeURL(Config.supabaseURL, provider, verifier)
    }

    private suspend fun finishSignIn(verified: Session) {
        error = null
        withContext(Dispatchers.IO) { store.save(verified) }
        checked = false
        try {
            adopt(verified)
            Telemetry.capture("signed_in")
            loadProfileAndGroup()
        } finally { checked = true }
    }

    fun signOut() {
        Telemetry.reset()
        OneSignalManager.logout()
        store.delete()
        session = null
        profile = null
        group = null
        members = emptyList()
        feedback = null
        api.session = null
    }

    /**
     * Starts an email change. Returns whether Auth applied it immediately or left it waiting on
     * the confirmation messages (`double_confirm_changes` in this project).
     */
    suspend fun changeEmail(email: String): EmailChange {
        val current = session ?: throw ApiError.Unauthorized
        val trimmed = email.trim()
        if (!isSignInEmail(trimmed)) throw ApiError.Network("Enter a valid email address.")
        if (trimmed.equals(current.email, ignoreCase = true)) throw ApiError.Network("That is already your sign-in email.")
        val updated = api.updateEmail(trimmed)
        val applied = updated.email?.takeIf { it.equals(trimmed, ignoreCase = true) }
        if (applied != null) {
            val next = current.copy(email = applied)
            withContext(Dispatchers.IO) { store.save(next) }
            session = next
            api.session = next
        }
        return EmailChange(
            requested = updated.newEmail?.takeIf { it.isNotBlank() } ?: trimmed,
            appliedNow = applied != null,
            appliedEmail = applied,
        )
    }

    /** Deletes the account on the server. The screen signs the user out locally after this returns. */
    suspend fun deleteAccount() {
        if (session == null) throw ApiError.Unauthorized
        api.deleteAccount()
    }

    suspend fun loadProfileAndGroup() {
        val current = session ?: return
        try {
            profile = api.getOne<Profile>("rest/v1/profiles", listOf("id" to "eq.${current.userId}"))

            val membershipQuery = mutableListOf(
                "user_id" to "eq.${current.userId}",
                "order" to "joined_at.asc",
                "limit" to "1",
            )
            group?.let { membershipQuery += "group_id" to "eq.${it.id}" }
            val memberships = api.getMany<Membership>("rest/v1/group_members", membershipQuery)
            val membership = memberships.firstOrNull() ?: run {
                group = null
                return
            }

            group = api.getOne<Group>("rest/v1/groups", listOf("id" to "eq.${membership.group_id}"))
            members = api.getMany(
                "rest/v1/group_members",
                listOf(
                    "select" to "user_id,role,profiles(id,display_name,monogram)",
                    "group_id" to "eq.${membership.group_id}",
                    "order" to "joined_at.asc",
                ),
            )
            error = null
        } catch (e: Exception) {
            error = e.message
            if (e is ApiError.Unauthorized) signOut()
        }
    }

    suspend fun saveDisplayName(name: String) {
        val current = session ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > 80) throw ApiError.Network("Use a name between 1 and 80 characters.")
        val updated: Profile = api.patchRow(
            "rest/v1/profiles",
            listOf("id" to "eq.${current.userId}"),
            encodeBody(DisplayNameBody(display_name = trimmed, monogram = trimmed.take(1).uppercase())),
        )
        profile = updated
        members = members.map { if (it.user_id == updated.id) it.copy(profiles = updated) else it }
    }

    suspend fun loadNotificationPreferences(): NotificationPreferences {
        val current = session ?: throw ApiError.Unauthorized
        val paper = group ?: throw ApiError.Unauthorized
        return api.getOne<NotificationPreferences>(
            "rest/v1/notification_preferences",
            listOf("user_id" to "eq.${current.userId}", "group_id" to "eq.${paper.id}"),
        ) ?: NotificationPreferences(current.userId, paper.id)
    }

    suspend fun saveNotificationPreferences(preferences: NotificationPreferences): NotificationPreferences {
        if (preferences.user_id != session?.userId || preferences.group_id != group?.id) throw ApiError.Unauthorized
        return api.upsertRow("rest/v1/notification_preferences", encodeBody(preferences))
    }

    suspend fun createGroup(name: String) {
        val current = session ?: return
        val created: Group = api.postRow(
            "rest/v1/groups",
            encodeBody(NewGroupBody(name = name, created_by = current.userId)),
        )
        api.postVoid(
            "rest/v1/group_members",
            encodeBody(NewMemberBody(group_id = created.id, user_id = current.userId)),
        )
        group = created
        Telemetry.capture("paper_created")
        loadProfileAndGroup()
    }

    suspend fun joinGroup(code: String) {
        val joined: String = api.rpc(
            "join_group_with_invite",
            encodeBody(JoinBody(code = DeepLink.inviteCode(code))),
        )
        Telemetry.capture("paper_joined")
        group = api.getOne<Group>("rest/v1/groups", listOf("id" to "eq.$joined"))
        loadProfileAndGroup()
    }

    /** Opens the report sheet, unless one is already open; [screenshot] is attached, removably. */
    fun report(kind: FeedbackKind, screen: String?, error: String? = null, screenshot: FeedbackImage? = null) {
        if (feedback != null || session == null) return
        feedback = FeedbackDraft(kind, screen, error).apply { screenshot?.let { images += it } }
    }

    fun closeReport() {
        if (feedback?.busy == true) return
        feedback = null
    }

    /** Sends the open report. On failure the draft stays as written, with the reason. */
    fun sendReport() {
        val draft = feedback ?: return
        val userId = session?.userId ?: return
        if (!draft.canSend) return
        draft.busy = true
        draft.failure = null
        viewModelScope.launch {
            try {
                draft.result = Feedback.send(api, userId, draft)
                Telemetry.capture("feedback_sent")
            } catch (e: Exception) {
                draft.failure = e.message ?: "Could not send your report. Please try again."
                if (e is ApiError.Unauthorized) signOut()
            } finally {
                draft.busy = false
            }
        }
    }

    override fun onCleared() {
        // The Api outlives this ViewModel: don't leave it holding a callback into a cleared one, but
        // keep persisting refreshed sessions (refresh tokens rotate, so a lost one signs the user out).
        val store = store
        if (api.onSessionChanged === sessionListener) api.onSessionChanged = { store.save(it) }
    }

    private fun adopt(value: Session) {
        Telemetry.identify(value.userId)
        OneSignalManager.login(value.userId.lowercase())
        session = value
        api.session = value
    }

    @kotlinx.serialization.Serializable
    private data class DisplayNameBody(val display_name: String, val monogram: String)

    @kotlinx.serialization.Serializable
    private data class NewGroupBody(val name: String, val created_by: String)

    @kotlinx.serialization.Serializable
    private data class NewMemberBody(val group_id: String, val user_id: String, val role: String = "owner")

    @kotlinx.serialization.Serializable
    private data class JoinBody(val code: String)
}
