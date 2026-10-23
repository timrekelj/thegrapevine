package si.timrekelj.thegrapevine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.time.Instant

/** Earlier editions: the stories older than the front page, a page at a time, newest first. */
class ArchiveState(private val api: Api, private val groupId: String, private val start: Instant) : ViewModel() {

    var stories by mutableStateOf<List<ArchivedStory>>(emptyList())
        private set
    /** Whether a page older than the last one shown exists. */
    var hasMore by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    /** True once the first page has come back, so "Nothing older yet." isn't shown while loading. */
    var loaded by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    /** Loads the first page, unless it already has. */
    fun open() {
        if (!loaded) loadPage()
    }

    /** The next page, older than the last story shown; or the first page again after an error. */
    fun loadPage() {
        if (isLoading) return
        val before = stories.lastOrNull()?.last_activity_at ?: start
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val rows = api.getMany<ArchivedStory>(
                    "rest/v1/stories",
                    listOf(
                        "select" to "id,title,category,last_activity_at,sources(count),messages(count)",
                        "group_id" to "eq.$groupId",
                        "last_activity_at" to "lt.${InstantSerializer.format(before)}",
                        "order" to "last_activity_at.desc",
                        "limit" to "${Edition.ARCHIVE_PAGE + 1}",
                    ),
                )
                stories = stories + rows.take(Edition.ARCHIVE_PAGE)
                hasMore = rows.size > Edition.ARCHIVE_PAGE
                loaded = true
            } catch (e: Exception) {
                error = e.message
            }
            isLoading = false
        }
    }
}
