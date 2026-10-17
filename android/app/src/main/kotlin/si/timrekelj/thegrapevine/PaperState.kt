package si.timrekelj.thegrapevine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.time.Instant

/** Mirrors `PaperStore.swift`: the front page, its ranking, and the seen marker. */
class PaperState(private val api: Api) : ViewModel() {

    var stories by mutableStateOf<List<StoryRow>>(emptyList())
        private set
    var seenAt by mutableStateOf<Instant?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    fun load(app: AppState) {
        val session = app.session ?: return
        val group = app.group ?: return
        if (isLoading) return
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val memberships = api.getMany<Membership>(
                    "rest/v1/group_members",
                    listOf("user_id" to "eq.${session.userId}", "group_id" to "eq.${group.id}"),
                )
                seenAt = memberships.firstOrNull()?.last_seen_at

                val rows = api.getMany<StoryRow>(
                    "rest/v1/stories",
                    listOf(
                        "select" to "id,title,summary,category,last_activity_at,created_at,sources(*),messages(*)",
                        "group_id" to "eq.${group.id}",
                    ),
                )
                stories = Ranking.rank(rows)

                // Record this edition as seen.
                runCatching {
                    api.patchVoid(
                        "rest/v1/group_members",
                        listOf("group_id" to "eq.${group.id}", "user_id" to "eq.${session.userId}"),
                        encodeBody(SeenBody(last_seen_at = InstantSerializer.format(Instant.now()))),
                    )
                }
            } catch (e: Exception) {
                error = e.message
            }
            isLoading = false
        }
    }

    fun clear() {
        stories = emptyList()
        seenAt = null
    }

    @kotlinx.serialization.Serializable
    private data class SeenBody(val last_seen_at: String)
}
