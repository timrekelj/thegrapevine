package si.timrekelj.thegrapevine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.util.UUID

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

    /** Uploads already accepted by storage, so a retry resumes instead of re-uploading. */
    private val uploaded = mutableListOf<String>()
    private var trackedStoryId: String? = null
    /** `messageId:emoji` toggles still waiting on the server. */
    private val pendingReactions = mutableSetOf<String>()

    suspend fun load(storyId: String) {
        if (isLoading) return
        isLoading = true
        error = null
        try {
            val loaded = api.getOne<Story>("rest/v1/stories", listOf("id" to "eq.$storyId"))
                ?: throw ApiError.Network("This story is no longer available.")
            story = loaded
            if (trackedStoryId != storyId) {
                Telemetry.capture("story_opened")
                trackedStoryId = storyId
            }
            sources = api.getMany("rest/v1/sources", listOf("story_id" to "eq.$storyId", "order" to "created_at.desc"))
            messages = api.getMany(
                "rest/v1/messages",
                listOf(
                    "select" to "*,reactions(*),message_media(*)",
                    "story_id" to "eq.$storyId",
                    "order" to "created_at.asc",
                ),
            )
            history = api.getMany("rest/v1/story_history", listOf("story_id" to "eq.$storyId", "order" to "created_at.desc"))

            val authorIds = (messages.map { it.author_id } + sources.mapNotNull { it.submitted_by }).toSet()
            if (authorIds.isNotEmpty()) {
                profiles = api.getMany<Profile>(
                    "rest/v1/profiles",
                    listOf("select" to "id,display_name,monogram", "id" to "in.(${authorIds.joinToString(",")})"),
                ).associateBy { it.id }
            }
        } catch (e: Exception) {
            error = e.message
        }
        isLoading = false
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
