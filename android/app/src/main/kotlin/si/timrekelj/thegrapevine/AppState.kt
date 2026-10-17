package si.timrekelj.thegrapevine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

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

    init {
        api.onSessionChanged = { refreshed ->
            store.save(refreshed)
            session = refreshed
        }
        restoreSession()
    }

    private fun restoreSession() {
        viewModelScope.launch {
            val stored = store.load()
            if (stored == null) {
                OneSignalManager.logout()
                checked = true
                return@launch
            }
            if (Jwt.isExpired(stored.accessToken)) {
                val refreshed = try {
                    api.refreshSession(stored.refreshToken)
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
                store.save(refreshed)
            } else {
                adopt(stored)
            }
            loadProfileAndGroup()
            checked = true
        }
    }

    /** An invitation or story link; both wait until the app can show them. */
    fun handleDeepLink(uri: String) {
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
        val verified = api.verifyOtp(email, code)
        store.save(verified)
        adopt(verified)
        loadProfileAndGroup()
    }

    fun signOut() {
        OneSignalManager.logout()
        store.delete()
        session = null
        profile = null
        group = null
        members = emptyList()
        api.session = null
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
        loadProfileAndGroup()
    }

    suspend fun joinGroup(code: String) {
        val joined: String = api.rpc(
            "join_group_with_invite",
            encodeBody(JoinBody(code = DeepLink.inviteCode(code))),
        )
        group = api.getOne<Group>("rest/v1/groups", listOf("id" to "eq.$joined"))
        loadProfileAndGroup()
    }

    private fun adopt(value: Session) {
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
