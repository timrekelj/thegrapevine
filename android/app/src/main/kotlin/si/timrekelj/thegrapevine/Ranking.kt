package si.timrekelj.thegrapevine

import java.net.URI
import java.net.URLDecoder
import kotlin.math.pow
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Mirrors `PaperStore.rank` in the iOS app. */
object Ranking {
    const val WINDOW_HOURS = 48L

    fun rank(rows: List<StoryRow>, now: Instant = Instant.now()): List<StoryRow> {
        val cutoff = now.minus(WINDOW_HOURS, ChronoUnit.HOURS)

        fun score(story: StoryRow): Double {
            val messages = story.messages.count { !it.created_at.isBefore(cutoff) }.toDouble()
            val sources = story.sources.count { !it.created_at.isBefore(cutoff) }.toDouble()
            val age = maxOf(0.0, (now.epochSecond - story.last_activity_at.epochSecond).toDouble())
            return 3 * messages + 2 * sources + 4 * 2.0.pow(-age / (24 * 3600))
        }

        val byActivity = rows.sortedByDescending { it.last_activity_at }
        val lead = byActivity.firstOrNull() ?: return emptyList()
        return listOf(lead) + byActivity.drop(1).sortedByDescending { score(it) }
    }
}

/**
 * The front page is this week's edition: stories active in the last [DAYS], never fewer than [MIN]
 * (a quiet paper still has a front page) nor more than [MAX]. Older stories move to the archive,
 * [ARCHIVE_PAGE] at a time. Mirrors `cutEdition` in the web's `lib/queries.ts`.
 */
object Edition {
    const val DAYS = 7L
    const val MIN = 10
    const val MAX = 40
    const val ARCHIVE_PAGE = 20

    class Cut<T>(
        val stories: List<T>,
        /** The archive cursor: the last kept story's activity, when anything older exists. */
        val earlier: Instant?,
    )

    /** Cuts an edition from stories newest-activity first (fetched with `MAX + 1` rows). */
    fun <T> cut(newestFirst: List<T>, now: Instant = Instant.now(), activity: (T) -> Instant): Cut<T> {
        val cutoff = now.minus(DAYS, ChronoUnit.DAYS)
        val stories = mutableListOf<T>()
        for (story in newestFirst) {
            if (stories.size >= MAX || (stories.size >= MIN && activity(story).isBefore(cutoff))) break
            stories += story
        }
        val earlier = if (newestFirst.size > stories.size) stories.lastOrNull()?.let(activity) else null
        return Cut(stories, earlier)
    }

    fun cut(newestFirst: List<StoryRow>, now: Instant = Instant.now()): Cut<StoryRow> =
        cut(newestFirst, now) { it.last_activity_at }
}

/** Deep links: https://thegrapevine.si invitations and stories. */
object DeepLink {
    /** The only site whose links the app opens (the App Links intent filter's host). */
    private const val HOST = "thegrapevine.si"

    sealed interface Route {
        data class Invite(val code: String) : Route
        data class Story(val id: String) : Route
    }

    private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    /** What an invite code can look like (today 12 hex digits); the server caps codes at 64 characters. */
    private val INVITE_PATTERN = Regex("[0-9a-z_-]{1,64}")

    /** Where an incoming link should go, or null for links the app doesn't handle. */
    fun route(uri: String): Route? {
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
        // MainActivity is exported, so any app can hand it a VIEW intent: only take our own links.
        if (parsed.scheme != "https" || !parsed.host.equals(HOST, ignoreCase = true)) return null
        val path = parsed.path.orEmpty().trimEnd('/')
        return when {
            path == "/invite" -> queryParams(parsed.rawQuery.orEmpty())["code"]
                ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                ?.trim()?.lowercase()?.takeIf(INVITE_PATTERN::matches)
                ?.let { Route.Invite(it) }
            path.startsWith("/story/") -> path.removePrefix("/story/").takeIf(UUID_PATTERN::matches)?.let { Route.Story(it.lowercase()) }
            else -> null
        }
    }

    /**
     * Invitation links carry the code as a query param; a bare code is accepted
     * too, which is what the Onboarding screen passes.
     */
    fun inviteCode(input: String): String {
        val trimmed = input.trim()
        val fromUrl = runCatching { queryParams(URI(trimmed).query.orEmpty())["code"] }.getOrNull()
        return (fromUrl ?: trimmed).trim().lowercase()
    }

    private fun queryParams(raw: String): Map<String, String> =
        raw.split('&').mapNotNull { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
}

/** The link inside text shared from another app, e.g. "Look at this https://x.com/… via @someone". */
object SharedLink {
    private val URL_PATTERN = Regex("https?://[^\\s<>\"]+", RegexOption.IGNORE_CASE)

    fun extract(text: String?): String? {
        val match = URL_PATTERN.find(text.orEmpty())?.value ?: return null
        // Sentence punctuation after a link isn't part of it; a closing bracket is only when it opens inside.
        var url = match.trimEnd('.', ',', ';', ':', '!', '?', '\'', '"')
        while (url.endsWith(")") && url.count { it == ')' } > url.count { it == '(' }) {
            url = url.dropLast(1).trimEnd('.', ',', ';', ':', '!', '?', '\'', '"')
        }
        return url
    }
}
