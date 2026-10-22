package si.timrekelj.thegrapevine

import java.net.URI
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

/** Deep links: https://thegrapevine.si invitations and stories. */
object DeepLink {
    sealed interface Route {
        data class Invite(val code: String) : Route
        data class Story(val id: String) : Route
    }

    private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    /** Where an incoming link should go, or null for links the app doesn't handle. */
    fun route(uri: String): Route? {
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
        if (parsed.scheme != "https" && parsed.scheme != "http") return null
        val path = parsed.path.orEmpty().trimEnd('/')
        return when {
            path == "/invite" -> queryParams(parsed.rawQuery.orEmpty())["code"]?.let { Route.Invite(it.trim().lowercase()) }
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
