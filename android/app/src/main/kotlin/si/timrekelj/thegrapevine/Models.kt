package si.timrekelj.thegrapevine

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class Profile(
    val id: String,
    val display_name: String,
    val monogram: String,
)

@Serializable
data class NotificationPreferences(
    val user_id: String,
    val group_id: String,
    val new_stories: Boolean = true,
    val direct_replies: Boolean = true,
    val mentions: Boolean = true,
    val muted: Boolean = false,
)

@Serializable
data class Group(
    val id: String,
    val name: String,
    val invite_code: String,
)

@Serializable
data class Membership(
    val group_id: String,
    @Serializable(with = InstantSerializer::class) val last_seen_at: Instant? = null,
)

@Serializable
data class Member(
    val user_id: String,
    val role: String,
    val profiles: Profile,
)

@Serializable
data class Story(
    val id: String,
    val group_id: String,
    val title: String,
    val summary: String,
    val category: String? = null,
    @Serializable(with = InstantSerializer::class) val last_activity_at: Instant,
    @Serializable(with = InstantSerializer::class) val created_at: Instant,
)

@Serializable
data class Source(
    val id: String,
    val story_id: String,
    val url: String,
    val domain: String,
    val publisher: String,
    val source_title: String,
    val excerpt: String? = null,
    val image_url: String? = null,
    val author: String? = null,
    val submitted_by: String? = null, // null once the member has deleted their account
    val submitter_comment: String? = null,
    val extraction_status: String,
    @Serializable(with = InstantSerializer::class) val created_at: Instant,
)

@Serializable
data class Reaction(
    val id: String,
    val message_id: String,
    val user_id: String,
    val emoji: String,
)

@Serializable
data class Media(
    val id: String,
    val message_id: String,
    val kind: String,
    val url: String,
    val alt_text: String? = null,
    val position: Int,
)

@Serializable
data class Message(
    val id: String,
    val story_id: String,
    val author_id: String,
    val body: String? = null,
    val reply_to: String? = null,
    @Serializable(with = InstantSerializer::class) val created_at: Instant,
    val reactions: List<Reaction>? = null,
    val message_media: List<Media>? = null,
)

@Serializable
data class StoryHistoryEntry(
    val id: String,
    val story_id: String,
    val title: String,
    val summary: String,
    val category: String? = null,
    @Serializable(with = InstantSerializer::class) val created_at: Instant,
)

/** Front-page row: a story with its embedded sources and messages. */
@Serializable
data class StoryRow(
    val id: String,
    val title: String,
    val summary: String,
    val category: String? = null,
    @Serializable(with = InstantSerializer::class) val last_activity_at: Instant,
    @Serializable(with = InstantSerializer::class) val created_at: Instant,
    val sources: List<Source> = emptyList(),
    val messages: List<Message> = emptyList(),
)

@Serializable
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
)

/** A picked image or video held in memory until the message is sent. */
class Attachment(
    val data: ByteArray,
    val kind: String,
    val contentType: String,
    val fileExtension: String,
) {
    companion object {
        const val MAX_IMAGES = 10
        const val MAX_BYTES = 20 * 1024 * 1024

        private val KINDS = setOf("image", "video")

        /** Mirrors `Attachment.validate` in the iOS app. */
        fun validate(attachments: List<Attachment>) {
            fun fail(message: String): Nothing = throw ApiError.Network(message)
            if (attachments.count { it.kind == "image" } > MAX_IMAGES) fail("Attach up to 10 images.")
            val ok = attachments.all {
                it.kind in KINDS && it.data.isNotEmpty() && it.data.size <= MAX_BYTES
            }
            if (!ok) fail("Each attachment must be an image or video, no larger than 20 MB.")
        }
    }
}
