package si.timrekelj.thegrapevine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/** Messages from others since the reader's last visit: the first one, where the divider goes, and how many. */
data class Unread(val firstMessageId: String, val count: Int)

/** Null on a first visit (no `lastRead`) or when nothing is new; `messages` in timeline order. */
fun unreadSince(messages: List<Message>, lastRead: Instant?, userId: String): Unread? {
    if (lastRead == null) return null
    val fresh = messages.filter { it.created_at.isAfter(lastRead) && it.author_id != userId }
    return fresh.firstOrNull()?.let { Unread(it.id, fresh.size) }
}

/** Mirrors `StoryStore.swift`: one story's timeline, composer and corrections. */
class StoryState(private val api: Api) : ViewModel() {

    var story by mutableStateOf<Story?>(null)
        private set
    var sources by mutableStateOf<List<Source>>(emptyList())
        private set
    var messages by mutableStateOf<List<Message>>(emptyList())
        private set
    var profiles by mutableStateOf<Map<String, Profile>>(emptyMap())
        private set
    var history by mutableStateOf<List<StoryHistoryEntry>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    var pendingMessageId by mutableStateOf<String?>(null)
        private set
    /** When the reader last read this story before this visit; null on a first visit or if unknown. */
    var lastRead by mutableStateOf<Instant?>(null)
        private set

    /** Uploads already accepted by storage, so a retry resumes instead of re-uploading. */
    private val uploaded = mutableListOf<String>()
    private var trackedStoryId: String? = null
    /** `messageId:emoji` toggles still waiting on the server. */
    private val pendingReactions = mutableSetOf<String>()
    /** Set once this visit's first read is recorded, so later marks can't overtake it. */
    private var visiting = false

    /**
     * Starts a visit: records the read and keeps the previous read time for the "new since" divider.
     * If the call fails there is simply no divider.
     */
    fun beginVisit(storyId: String) {
        visiting = false
        lastRead = null
        // In the view model's scope, so a rotation mid-call doesn't lose the visit.
        viewModelScope.launch {
            lastRead = runCatching { recordRead(storyId) }.getOrNull()
            visiting = true
        }
    }

    /** Records that the reader is still here (or just left), so what they saw live isn't new next time. */
    fun markRead(storyId: String) {
        if (!visiting) return
        viewModelScope.launch { runCatching { recordRead(storyId) } }
    }

    private suspend fun recordRead(storyId: String): Instant? =
        api.rpc<String?>("mark_story_read", encodeBody(MarkReadBody(story = storyId)))?.let(InstantSerializer::parse)

    suspend fun load(storyId: String) {
        if (isLoading) return
        isLoading = true
        error = null
        try {
            // The four reads are independent; run them together. Polling assigns only what changed,
            // so an unchanged timeline doesn't recompose.
            coroutineScope {
                val loadedStory = async { api.getOne<Story>("rest/v1/stories", listOf("select" to Columns.STORY, "id" to "eq.$storyId")) }
                val loadedSources = async {
                    api.getMany<Source>(
                        "rest/v1/sources",
                        listOf("select" to Columns.SOURCE, "story_id" to "eq.$storyId", "order" to "created_at.desc"),
                    )
                }
                val loadedMessages = async {
                    api.getMany<Message>(
                        "rest/v1/messages",
                        listOf(
                            "select" to "*,reactions(*),message_media(*)",
                            "story_id" to "eq.$storyId",
                            "order" to "created_at.asc",
                        ),
                    )
                }
                val loadedHistory = async {
                    api.getMany<StoryHistoryEntry>(
                        "rest/v1/story_history",
                        listOf("select" to Columns.STORY_HISTORY, "story_id" to "eq.$storyId", "order" to "created_at.desc"),
                    )
                }
                val loaded = loadedStory.await() ?: throw ApiError.Network("This story is no longer available.")
                if (loaded != story) story = loaded
                if (trackedStoryId != storyId) {
                    Telemetry.capture("story_opened")
                    trackedStoryId = storyId
                }
                loadedSources.await().let { if (it != sources) sources = it }
                loadedMessages.await().let { if (it != messages) messages = it }
                loadedHistory.await().let { if (it != history) history = it }
            }

            val authorIds = (messages.map { it.author_id } + sources.mapNotNull { it.submitted_by }).toSet()
            if (authorIds.isNotEmpty()) {
                val loaded = api.getMany<Profile>(
                    "rest/v1/profiles",
                    listOf("select" to "id,display_name,monogram", "id" to "in.(${authorIds.joinToString(",")})"),
                ).associateBy { it.id }
                if (loaded != profiles) profiles = loaded
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    suspend fun postMessage(body: String, replyTo: String?, attachments: List<Attachment>): Boolean {
        val current = story ?: return false
        val session = api.session ?: return false
        val trimmed = body.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) return false

        return try {
            Attachment.validate(attachments)
            val id = pendingMessageId ?: UUID.randomUUID().toString().also { pendingMessageId = it }
            // shortcut: abandoned uploads need server-side cleanup before a wider rollout.
            for (index in uploaded.size until attachments.size) {
                val attachment = attachments[index]
                val path = "${current.group_id.lowercase()}/${session.userId.lowercase()}/" +
                    "${id.lowercase()}-$index.${attachment.fileExtension}"
                try {
                    api.upload(attachment, path)
                } catch (e: ApiError.Http) {
                    // A retry may find the previous upload.
                    if (e.code != 409) throw e
                }
                uploaded += path
            }

            val existing = api.getOne<Message>("rest/v1/messages", listOf("id" to "eq.$id"))
            if (existing == null) {
                api.postVoid(
                    "rest/v1/messages",
                    encodeBody(
                        NewMessage(
                            id = id,
                            story_id = current.id,
                            author_id = session.userId,
                            body = trimmed.ifEmpty { null },
                            reply_to = replyTo,
                        ),
                    ),
                )
            }

            val media = api.getMany<Media>("rest/v1/message_media", listOf("message_id" to "eq.$id"))
            uploaded.forEachIndexed { index, path ->
                if (media.none { it.position == index }) {
                    api.postVoid(
                        "rest/v1/message_media",
                        encodeBody(
                            NewMedia(
                                message_id = id,
                                kind = attachments[index].kind,
                                url = path,
                                position = index,
                                alt_text = "Shared ${attachments[index].kind}",
                            ),
                        ),
                    )
                }
            }

            pendingMessageId = null
            Telemetry.capture("message_sent", mapOf("is_reply" to (replyTo != null), "attachment_count" to attachments.size))
            uploaded.clear()
            load(current.id)
            true
        } catch (e: Exception) {
            error = "${e.message} Your draft is kept; retry Send."
            false
        }
    }

    suspend fun toggleReaction(messageId: String, emoji: String) {
        val session = api.session ?: return
        val key = "$messageId:$emoji"
        if (key in pendingReactions) return
        val before = messages.firstOrNull { it.id == messageId }?.reactions.orEmpty()
        val adding = before.none { it.user_id == session.userId && it.emoji == emoji }
        // Optimistic: the row shows the change at once, and a quick second tap sees it.
        setReactions(
            messageId,
            if (adding) before + Reaction(id = "", message_id = messageId, user_id = session.userId, emoji = emoji)
            else before.filterNot { it.user_id == session.userId && it.emoji == emoji },
        )
        pendingReactions += key
        try {
            if (adding) {
                try {
                    api.postVoid(
                        "rest/v1/reactions",
                        encodeBody(NewReaction(message_id = messageId, user_id = session.userId, emoji = emoji)),
                    )
                } catch (e: ApiError.Http) {
                    // Already saved by an earlier tap.
                    if (e.code != 409) throw e
                }
            } else {
                api.delete(
                    "rest/v1/reactions",
                    listOf("message_id" to "eq.$messageId", "user_id" to "eq.${session.userId}", "emoji" to "eq.$emoji"),
                )
            }
            pendingReactions -= key
            story?.let { load(it.id) }
        } catch (e: Exception) {
            pendingReactions -= key
            setReactions(messageId, before)
            error = e.message
        }
    }

    private fun setReactions(messageId: String, reactions: List<Reaction>) {
        messages = messages.map { if (it.id == messageId) it.copy(reactions = reactions) else it }
    }

    suspend fun report(target: String, note: String): Boolean {
        val current = story ?: return false
        return try {
            if (!Telemetry.correctionsEnabled()) throw ApiError.Network("Corrections are temporarily unavailable.")
            api.postObject<ReportResponse>(
                "functions/v1/report-story",
                encodeBody(ReportBody(story_id = current.id, target = target, note = note.trim().ifEmpty { null })),
            )
            notice = "Correction checked and saved."
            load(current.id)
            true
        } catch (e: Exception) {
            error = e.message
            false
        }
    }

    @kotlinx.serialization.Serializable
    private data class MarkReadBody(val story: String)

    @kotlinx.serialization.Serializable
    private data class NewMessage(
        val id: String,
        val story_id: String,
        val author_id: String,
        val body: String?,
        val reply_to: String?,
    )

    @kotlinx.serialization.Serializable
    private data class NewMedia(
        val message_id: String,
        val kind: String,
        val url: String,
        val position: Int,
        val alt_text: String,
    )

    @kotlinx.serialization.Serializable
    private data class NewReaction(val message_id: String, val user_id: String, val emoji: String)

    @kotlinx.serialization.Serializable
    private data class ReportBody(val story_id: String, val target: String, val note: String?)

    @kotlinx.serialization.Serializable
    private data class ReportResponse(val id: String, val title: String, val summary: String)
}
