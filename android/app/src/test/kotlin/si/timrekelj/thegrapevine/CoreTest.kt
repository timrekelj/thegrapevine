package si.timrekelj.thegrapevine

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** Ports of `ios/GrapevineTests/CoreTests.swift`. */
class CoreTest {

    @Test
    fun leadIsNewestThenDiscussionRanksTheRest() {
        val now = Instant.now()
        fun row(ageSeconds: Long, count: Int): StoryRow {
            val id = UUID.randomUUID().toString()
            val messages = List(count) {
                Message(id = UUID.randomUUID().toString(), story_id = id, author_id = "a", body = "A comment", created_at = now)
            }
            return StoryRow(id, "Story", "Summary", null, now.minusSeconds(ageSeconds), now, emptyList(), messages)
        }
        val lead = row(0, 0)
        val quiet = row(12 * 3600, 0)
        val busy = row(24 * 3600, 2)
        assertEquals(listOf(lead.id, busy.id, quiet.id), Ranking.rank(listOf(quiet, busy, lead), now).map { it.id })
        assertTrue(Ranking.rank(emptyList()).isEmpty())
    }

    @Test
    fun attachmentLimits() {
        val image = Attachment(byteArrayOf(1), "image", "image/jpeg", "jpg")
        Attachment.validate(List(10) { image })
        assertThrows { Attachment.validate(List(11) { image }) }
        val video = Attachment(ByteArray(Attachment.MAX_BYTES + 1), "video", "video/mp4", "mp4")
        assertThrows { Attachment.validate(listOf(video)) }
    }

    @Test
    fun supabaseDatesWithAndWithoutFraction() {
        val fractional = InstantSerializer.parse("2026-10-13T12:00:00.123456+00:00")
        val plain = InstantSerializer.parse("2026-10-13T12:00:00Z")
        assertEquals(123_456_000, fractional.nano - plain.nano)
        assertEquals(fractional.epochSecond, plain.epochSecond)
    }

    @Test
    fun sessionRoundTrip() {
        val session = Session("access", "refresh", UUID.randomUUID().toString(), "tim@example.com")
        val stored = SupabaseJson.decodeFromString<Session>(SupabaseJson.encodeToString(session))
        assertEquals(session, stored)
    }

    @Test
    fun jwtExpiryUsesTheExpClaim() {
        val exp = Instant.now().epochSecond + 3600
        val payload = """{"sub":"${UUID.randomUUID()}","exp":$exp.5}"""
        val token = "header.${Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())}.signature"
        assertFalse(Jwt.isExpired(token))
        assertTrue(Jwt.isExpired("broken"))
    }

    @Serializable
    private data class WithDefault(val role: String = "owner", val note: String? = null)

    @Test
    fun requestBodiesSendDefaultsButOmitNulls() {
        // Swift's Encodable sends `role = "owner"` and omits nil; Supabase relies on both.
        assertEquals("""{"role":"owner"}""", SupabaseJson.encodeToString(WithDefault()))
    }

    @Test
    fun inviteCodeFromLinkOrBareCode() {
        assertEquals("grapevinedemo", DeepLink.inviteCode("https://thegrapevine.si/invite?code=GrapevineDemo"))
        assertEquals("grapevinedemo", DeepLink.inviteCode("  grapevinedemo "))
    }

    @Test
    fun plateMapsBlackToInkAndWhiteToPaper() {
        // Black prints as grape ink and white as paper, as in Plate.swift.
        assertArrayEquals(intArrayOf(74, 33, 96), Plate.toInk(0, 0, 0))
        assertArrayEquals(intArrayOf(239, 238, 233), Plate.toInk(255, 255, 255))
    }

    private fun assertThrows(block: () -> Unit) {
        val threw = runCatching(block).isFailure
        assertTrue("expected an exception", threw)
    }
}
