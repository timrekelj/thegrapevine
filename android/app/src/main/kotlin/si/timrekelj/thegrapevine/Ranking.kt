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

/** Deep links: invitation codes. */
object DeepLink {
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
