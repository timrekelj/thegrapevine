package si.timrekelj.thegrapevine

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
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
    fun editionKeepsThisWeekWithinItsBounds() {
        val now = Instant.parse("2026-10-23T12:00:00Z")
        val day = 86_400L
        // Ages in days, newest first.
        fun cut(ages: List<Double>) = Edition.cut(ages.map { now.minusSeconds((it * day).toLong()) }, now) { it }

        // A quiet paper: everything is old, but the first EDITION_MIN still make the front page.
        val quiet = cut(List(15) { 30.0 + it })
        assertEquals(Edition.MIN, quiet.stories.size)
        assertEquals(quiet.stories.last(), quiet.earlier)

        // A busy week: all recent stories, cut at EDITION_MAX; the extra fetched row means more exist.
        val busy = cut(List(Edition.MAX + 1) { it * 0.1 })
        assertEquals(Edition.MAX, busy.stories.size)
        assertEquals(busy.stories.last(), busy.earlier)

        // A normal week: past the minimum, the first story older than a week ends the edition.
        val week = cut(List(12) { it * 0.5 } + listOf(7.5, 8.0, 0.1))
        assertEquals(12, week.stories.size)
        assertEquals(now.minusSeconds((5.5 * day).toLong()), week.earlier)

        // Everything fits: no archive link.
        val small = cut(listOf(1.0, 20.0, 40.0))
        assertEquals(3, small.stories.size)
        assertEquals(null, small.earlier)
        assertEquals(null, cut(emptyList()).earlier)
        assertEquals(null, cut(List(Edition.MAX) { 0.0 }).earlier)
    }

    @Test
    fun unreadCountsOthersMessagesSinceTheLastRead() {
        val lastRead = Instant.parse("2026-10-23T10:00:00Z")
        fun message(id: String, author: String, minutes: Long) =
            Message(id = id, story_id = "s", author_id = author, body = "Hi", created_at = lastRead.plusSeconds(minutes * 60))
        val messages = listOf(
            message("old", "friend", -5),
            message("at", "friend", 0),
            message("mine", "me", 2),
            message("first", "friend", 3),
            message("second", "other", 9),
        )
        assertEquals(Unread("first", 2), unreadSince(messages, lastRead, "me"))
        // A first visit, or nothing new from others, shows no divider.
        assertEquals(null, unreadSince(messages, null, "me"))
        assertEquals(null, unreadSince(messages.take(3), lastRead, "me"))
    }

    @Test
    fun archiveRowsDecodeCountEmbeds() {
        val json = """[{"id":"a","title":"T","category":null,"last_activity_at":"2026-10-16T09:30:00.123456+00:00",""" +
            """"sources":[{"count":3}],"messages":[{"count":0}]}]"""
        val row = SupabaseJson.decodeFromString<List<ArchivedStory>>(json).single()
        assertEquals(3, row.sourceCount)
        assertEquals(0, row.messageCount)
        assertEquals(Instant.parse("2026-10-16T09:30:00.123456Z"), row.last_activity_at)
        assertEquals(null, SupabaseJson.decodeFromString<String?>("null"))
        assertEquals("2026-10-16T09:30:00Z", SupabaseJson.decodeFromString<String?>("\"2026-10-16T09:30:00+00:00\"")?.let { InstantSerializer.format(InstantSerializer.parse(it)) })
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
    fun signInEmailAcceptsAnOrdinaryAddress() {
        assertTrue(isSignInEmail("tim@example.com"))
        assertTrue(isSignInEmail("  a.b+c@mail.thegrapevine.si "))
        assertFalse(isSignInEmail(""))
        assertFalse(isSignInEmail("tim"))
        assertFalse(isSignInEmail("tim@"))
        assertFalse(isSignInEmail("@example.com"))
        assertFalse(isSignInEmail("tim@example"))
        assertFalse(isSignInEmail("tim @example.com"))
        assertFalse(isSignInEmail("tim@example.com extra"))
    }

    @Test
    fun emailChangeExplainsConfirmationOrAnImmediateSwitch() {
        val pending = EmailChange(requested = "new@example.com", appliedNow = false)
        assertEquals(
            "Check your inbox. We sent a confirmation to new@example.com and to old@example.com. Your sign-in email stays old@example.com until you confirm both.",
            emailChangeNotice("old@example.com", pending),
        )
        val applied = EmailChange(requested = "new@example.com", appliedNow = true, appliedEmail = "new@example.com")
        assertEquals("Your sign-in email is now new@example.com.", emailChangeNotice("old@example.com", applied))
        val first = EmailChange(requested = "new@example.com", appliedNow = false)
        assertEquals(
            "Check your inbox. We sent a confirmation to new@example.com. Your sign-in email updates once you confirm it.",
            emailChangeNotice("", first),
        )
    }

    @Test
    fun deleteAccountAcceptsOnlyDeletedTrueAndSurfacesTheErrorField() {
        assertEquals("functions/v1/delete-account", Api.DELETE_ACCOUNT_FUNCTION)
        assertTrue(accountWasDeleted("""{"deleted":true}"""))
        assertFalse(accountWasDeleted("""{"deleted":false}"""))
        assertFalse(accountWasDeleted("{}"))
        assertFalse(accountWasDeleted(""))
        // 401 is not rewritten for this call, so the function's error text is what the dialog shows.
        assertEquals("Invalid session", ApiError.Http(401, """{"error":"Invalid session"}""").message)
        assertEquals(
            "Your account could not be deleted. Nothing was removed. Please try again.",
            ApiError.Http(500, """{"error":"Your account could not be deleted. Nothing was removed. Please try again."}""").message,
        )
    }

    @Test
    fun serverErrorMessagesReachTheUser() {
        // submit-source and report-story rate limits answer 429 {"error": "…"}.
        assertEquals("Slow down", ApiError.Http(429, """{"error":"Slow down"}""").message)
        assertEquals("Bad", ApiError.Http(400, """{"message":"Bad"}""").message)
        assertEquals("Server error (502)", ApiError.Http(502, "<html>").message)
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
        assertEquals(DeepLink.Route.Invite("a1b2c3"), DeepLink.route("https://thegrapevine.si/invite?code=%20A1B2C3"))
        assertEquals(null, DeepLink.route("https://thegrapevine.si/invite?code=a%27%3B--"))
        assertEquals(null, DeepLink.route("https://thegrapevine.si/invite?code=" + "a".repeat(65)))
        assertEquals(null, DeepLink.route("https://thegrapevine.si/login"))
        // Only our own https links: MainActivity is exported, so any app can send it a VIEW intent.
        assertEquals(null, DeepLink.route("https://evil.example/invite?code=GrapevineDemo"))
        assertEquals(null, DeepLink.route("http://thegrapevine.si/story/$id"))
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
    fun feedbackScreenshotsAreScaledToTheLongestSide() {
        assertEquals(1080 to 2400, Feedback.scaledSize(1080, 2400, maxSide = 4000))
        assertEquals(922 to 2048, Feedback.scaledSize(1080, 2400))
        assertEquals(2048 to 1536, Feedback.scaledSize(4032, 3024))
        assertEquals(2048 to 1, Feedback.scaledSize(10000, 1))
        assertEquals(2048 to 2048, Feedback.scaledSize(2048, 2048))
    }

    @Test
    fun feedbackBodyMatchesTheFunctionContract() {
        val context = FeedbackContext(app_version = "1.2.0 (7)", os = "Android 16 (API 36)", device = "Google Pixel 9", screen = "Story")
        val json = encodeBody(Feedback.body(FeedbackKind.Enhancement, "  Bigger type, please \n", listOf("u/i.jpg"), context))
        val body = SupabaseJson.decodeFromString<kotlinx.serialization.json.JsonObject>(json)
        assertEquals(setOf("kind", "description", "images", "context"), body.keys)
        assertEquals("enhancement", body["kind"]!!.jsonPrimitive.content)
        assertEquals("Bigger type, please", body["description"]!!.jsonPrimitive.content)
        // No error was shown, so none is sent.
        assertEquals(setOf("platform", "app_version", "os", "device", "screen"), body["context"]!!.jsonObject.keys)
        assertEquals("android", body["context"]!!.jsonObject["platform"]!!.jsonPrimitive.content)
        assertEquals(listOf("bug", "enhancement", "feature"), FeedbackKind.entries.map { it.value })
        val result = SupabaseJson.decodeFromString<FeedbackResult>("""{"id":"r","issue_number":42,"issue_url":"https://github.com/x/y/issues/42"}""")
        assertEquals(42, result.issue_number)
        assertEquals(null, SupabaseJson.decodeFromString<FeedbackResult>("""{"id":"r"}""").issue_url)
    }

    @Test
    fun feedbackNamesTheDeviceOnce() {
        assertEquals("Google Pixel 9", Feedback.device("Google", "Pixel 9"))
        assertEquals("Samsung SM-S921B", Feedback.device("samsung", "SM-S921B"))
        assertEquals("OnePlus CPH2581", Feedback.device("OnePlus", "CPH2581"))
        assertEquals("motorola edge 50", Feedback.device("motorola", "motorola edge 50"))
    }

    @Test
    fun shakeNeedsSustainedAccelerationAndRestsAfterwards() {
        val still = ShakeDetector()
        assertFalse((0L..2000L step 60).any { still.onSample(0f, 0f, 9.81f, it) })
        // One bump (a phone set down hard) is not a shake.
        val bump = ShakeDetector()
        assertFalse((0L..600L step 60).any { bump.onSample(0f, 0f, if (it == 120L) 30f else 9.81f, it) })
        val shaken = ShakeDetector()
        val hits = (0L..3000L step 60).filter { shaken.onSample(20f, 5f, 9.81f, it) }
        assertTrue(hits.isNotEmpty())
        // Rests for two seconds after a shake, however long it goes on.
        assertTrue(hits.zipWithNext().all { (a, b) -> b - a >= 2000 })
    }

    private fun assertThrows(block: () -> Unit) {
        val threw = runCatching(block).isFailure
        assertTrue("expected an exception", threw)
    }
}
