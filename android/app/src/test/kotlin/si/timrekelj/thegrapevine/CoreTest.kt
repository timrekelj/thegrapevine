package si.timrekelj.thegrapevine

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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
    fun notificationPreferencesMatchServerDefaultsAndRoundTrip() {
        val defaults = NotificationPreferences("user", "group")
        assertTrue(defaults.new_stories && defaults.direct_replies && defaults.mentions)
        assertFalse(defaults.muted)
        val preferences = defaults.copy(new_stories = false, muted = true)
        val json = encodeBody(preferences)
        assertEquals(preferences, SupabaseJson.decodeFromString<NotificationPreferences>(json))
        val body = SupabaseJson.decodeFromString<kotlinx.serialization.json.JsonObject>(json)
        assertEquals(setOf("user_id", "group_id", "new_stories", "direct_replies", "mentions", "muted"), body.keys)
    }

    @Test
    fun oauthPKCEAndCallbacks() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", OAuth.challenge(verifier))
        assertEquals(43, OAuth.verifier().length)
        AuthProvider.entries.forEach {
            val url = OAuth.authorizeURL("https://example.supabase.co/", it, verifier)
            assertTrue(url.contains("provider=${it.value}"))
            assertTrue(url.contains("code_challenge_method=s256"))
            assertFalse(url.contains(verifier))
        }
        assertEquals("abc", OAuth.code("grapevine://auth/callback?code=abc"))
        listOf(
            "https://evil.example/callback?code=abc",
            "grapevine://auth/wrong?code=abc",
            "grapevine://auth/callback?code=",
            "grapevine://auth/callback?error=access_denied&code=abc",
        ).forEach { url -> assertThrows { OAuth.code(url) } }
    }


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
    fun appLinksRouteInvitesAndStories() {
        val id = "8f02e116-450a-4808-82e4-42098cb7b393"
        assertEquals(DeepLink.Route.Invite("grapevinedemo"), DeepLink.route("https://thegrapevine.si/invite?code=GrapevineDemo"))
        assertEquals(DeepLink.Route.Story(id), DeepLink.route("https://thegrapevine.si/story/${id.uppercase()}/"))
        assertEquals(null, DeepLink.route("https://thegrapevine.si/story/not-a-uuid"))
        assertEquals(null, DeepLink.route("https://thegrapevine.si/invite"))
        assertEquals(null, DeepLink.route("https://thegrapevine.si/login"))
    }

    @Test
    fun sharedTextYieldsItsLink() {
        assertEquals("https://x.com/nasa/status/123", SharedLink.extract("Look at this https://x.com/nasa/status/123"))
        assertEquals("https://example.com/a", SharedLink.extract("\"Headline\" (https://example.com/a)."))
        assertEquals("https://en.wikipedia.org/wiki/Grape_(fruit)", SharedLink.extract("https://en.wikipedia.org/wiki/Grape_(fruit)"))
        assertEquals("https://example.com/?q=1", SharedLink.extract("https://example.com/?q=1"))
        assertEquals(null, SharedLink.extract("no link here"))
        assertEquals(null, SharedLink.extract(null))
    }

    @Test
    fun plateGivesDarkPartsTheBigDotsOnPaper() {
        // On paper, white needs no ink and black is a solid; in between, dots grow with darkness.
        assertEquals(0f, Plate.radius(1f, dark = false), 0.0001f)
        assertEquals(Plate.MAX_RADIUS, Plate.radius(0f, dark = false), 0.0001f)
        assertTrue(Plate.radius(0.3f, dark = false) > Plate.radius(0.7f, dark = false))
        // A mid-grey dot has half the area of a solid one.
        val half = Plate.radius(0.5f, dark = false)
        val solid = Plate.radius(0f, dark = false)
        assertEquals(0.5f, half * half / (solid * solid), 0.001f)
    }

    @Test
    fun plateGivesLightPartsTheBigDotsOnTheEveningEdition() {
        assertEquals(0f, Plate.radius(0f, dark = true), 0.0001f)
        assertEquals(Plate.MAX_RADIUS, Plate.radius(1f, dark = true), 0.0001f)
        assertTrue(Plate.radius(0.7f, dark = true) > Plate.radius(0.3f, dark = true))
    }

    @Test
    fun plateMeasuresLuminanceAndScreensAtTheWebPitch() {
        assertEquals(0f, Plate.luminance(0, 0, 0), 0.0001f)
        assertEquals(1f, Plate.luminance(255, 255, 255), 0.0001f)
        assertTrue(Plate.luminance(0, 255, 0) > Plate.luminance(255, 0, 0))
        assertEquals(138, Plate.columns(360f))
    }

    private fun assertThrows(block: () -> Unit) {
        val threw = runCatching(block).isFailure
        assertTrue("expected an exception", threw)
    }
}
