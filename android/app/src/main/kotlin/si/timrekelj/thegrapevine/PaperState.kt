package si.timrekelj.thegrapevine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant

/** Mirrors `PaperStore.swift`: the front page, its ranking, and the seen marker. */
class PaperState(private val api: Api) : ViewModel() {

    var stories by mutableStateOf<List<StoryRow>>(emptyList())
        private set
    var seenAt by mutableStateOf<Instant?>(null)
        private set
    /** Where the archive starts when older stories didn't make this edition, else null. */
    var earlier by mutableStateOf<Instant?>(null)
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
                coroutineScope {
                    val memberships = async {
                        api.getMany<Membership>(
                            "rest/v1/group_members",
                            listOf("user_id" to "eq.${session.userId}", "group_id" to "eq.${group.id}"),
                        )
                    }
                    // Only the columns the front page shows: article excerpts and reactions stay on the server.
                    // Newest activity first and one row past the edition's maximum, so the cut knows if more exist.
                    val rows = async {
                        api.getMany<StoryRow>(
                            "rest/v1/stories",
                            listOf(
                                "select" to "id,title,summary,category,last_activity_at,created_at," +
                                    "sources($SOURCE_COLUMNS),messages($MESSAGE_COLUMNS)",
                                "group_id" to "eq.${group.id}",
                                "order" to "last_activity_at.desc",
                                "limit" to "${Edition.MAX + 1}",
                            ),
                        )
                    }
                    seenAt = memberships.await().firstOrNull()?.last_seen_at
                    val edition = Edition.cut(rows.await())
                    val ranked = Ranking.rank(edition.stories)
                    if (ranked != stories) stories = ranked
                    earlier = edition.earlier
                }

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
        earlier = null
    }

    private companion object {
        const val SOURCE_COLUMNS = "id,story_id,url,domain,publisher,source_title,image_url,submitted_by,extraction_status,created_at"
        const val MESSAGE_COLUMNS = "id,story_id,author_id,body,reply_to,created_at"
    }

    @kotlinx.serialization.Serializable
    private data class SeenBody(val last_seen_at: String)
}
