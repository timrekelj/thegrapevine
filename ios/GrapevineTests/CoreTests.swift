import XCTest
import UIKit
@testable import Grapevine

final class CoreTests: XCTestCase {
    func testOAuthPKCEAndCallbackValidation() throws {
        XCTAssertNotEqual(OAuth.verifier(), OAuth.verifier())
        XCTAssertTrue(OAuth.verifier().allSatisfy(\.isHexDigit))
        let verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        XCTAssertEqual(OAuth.challenge(verifier), "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
        XCTAssertEqual(OAuth.verifier().count, 64)
        for provider in AuthProvider.allCases {
            let items = URLComponents(url: OAuth.authorizeURL(provider: provider, verifier: verifier), resolvingAgainstBaseURL: false)!.queryItems!
            XCTAssertTrue(items.contains(URLQueryItem(name: "provider", value: provider.rawValue)))
            XCTAssertTrue(items.contains(URLQueryItem(name: "code_challenge_method", value: "s256")))
        }
        XCTAssertEqual(try OAuth.code(from: URL(string: "grapevine://auth/callback?code=abc")!), "abc")
        for url in ["https://evil.example/callback?code=abc", "grapevine://auth/wrong?code=abc", "grapevine://auth/callback?code=", "grapevine://auth/callback?error=access_denied&code=abc"] {
            XCTAssertThrowsError(try OAuth.code(from: URL(string: url)!))
        }
    }

    @MainActor
    func testAppleNonceAndTokenBody() throws {
        XCTAssertEqual(OAuth.nonceHash("abc"), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        let body = try JSONEncoder().encode(API.AppleTokenBody(id_token: "apple-token", nonce: "raw-nonce"))
        let object = try JSONSerialization.jsonObject(with: body) as? [String: String]
        XCTAssertEqual(object, ["provider": "apple", "id_token": "apple-token", "nonce": "raw-nonce"])
    }

    func testOAuthPreservesProviderErrors() {
        for suffix in ["?error=server_error&error_description=Apple%20rejected%20the%20request&code=ignored",
                       "#error=server_error&error_description=Apple%20rejected%20the%20request"] {
            XCTAssertThrowsError(try OAuth.code(from: URL(string: "grapevine://auth/callback" + suffix)!)) {
                XCTAssertEqual($0.localizedDescription, "Apple rejected the request")
            }
        }
        XCTAssertThrowsError(try OAuth.code(from: URL(string: "https://evil.example/callback?error=denied&error_description=Untrusted")!)) {
            XCTAssertEqual($0.localizedDescription, "Sign-in could not be completed. Please try again.")
        }
    }

    @MainActor
    func testLeadIsNewestThenDiscussionRanksTheRest() {
        let now = Date()
        func row(age: TimeInterval, count: Int) -> StoryRow {
            let id = UUID()
            let messages = (0..<count).map { _ in Message(id: UUID(), story_id: id, author_id: UUID(), body: "A comment", reply_to: nil, created_at: now, reactions: nil, message_media: nil) }
            return StoryRow(id: id, title: "Story", summary: "Summary", category: nil, last_activity_at: now.addingTimeInterval(-age), created_at: now, sources: [], messages: messages)
        }
        let lead = row(age: 0, count: 0)
        let quiet = row(age: 12 * 3600, count: 0)
        let busy = row(age: 24 * 3600, count: 2)
        XCTAssertEqual(PaperStore().rank([quiet, busy, lead], now: now).map(\.id), [lead.id, busy.id, quiet.id])
        XCTAssertTrue(PaperStore().rank([]).isEmpty)
    }

    func testAttachmentLimits() throws {
        let image = Attachment(data: Data([1]), kind: "image", contentType: "image/jpeg", fileExtension: "jpg")
        XCTAssertNoThrow(try Attachment.validate(Array(repeating: image, count: 10)))
        XCTAssertThrowsError(try Attachment.validate(Array(repeating: image, count: 11)))
        let video = Attachment(data: Data(count: 20 * 1024 * 1024 + 1), kind: "video", contentType: "video/mp4", fileExtension: "mp4")
        XCTAssertThrowsError(try Attachment.validate([video]))
    }

    func testSupabaseDatesWithAndWithoutFraction() throws {
        let fractional = try JSONDecoder.supabase.decode(Date.self, from: Data("\"2026-10-13T12:00:00.123456+00:00\"".utf8))
        let plain = try JSONDecoder.supabase.decode(Date.self, from: Data("\"2026-10-13T12:00:00Z\"".utf8))
        XCTAssertEqual(fractional.timeIntervalSince(plain), 0.123456, accuracy: 0.001)
    }

    func testNotificationPreferencesMatchServerDefaultsAndRoundTrip() throws {
        var preferences = NotificationPreferences(user_id: UUID(), group_id: UUID())
        XCTAssertTrue(preferences.new_stories && preferences.direct_replies && preferences.mentions)
        XCTAssertFalse(preferences.muted)
        preferences.new_stories = false
        preferences.muted = true
        let data = try JSONEncoder().encode(preferences)
        XCTAssertEqual(try JSONDecoder().decode(NotificationPreferences.self, from: data), preferences)
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(Set(body.keys), ["user_id", "group_id", "new_stories", "direct_replies", "mentions", "muted"])
    }

    func testSessionRoundTrip() throws {
        let session = Session(accessToken: "access", refreshToken: "refresh", userId: UUID(), email: "tim@example.com")
        let stored = try JSONDecoder.supabase.decode(Session.self, from: JSONEncoder().encode(session))
        XCTAssertEqual(stored.refreshToken, session.refreshToken)
        XCTAssertEqual(stored.userId, session.userId)
    }

    func testJWTExpiryUsesTheExpClaim() throws {
        let payload = try JSONSerialization.data(withJSONObject: ["sub": UUID().uuidString, "exp": Date().timeIntervalSince1970 + 3600])
        let encoded = payload.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        XCTAssertFalse(JWT.isExpired("header.\(encoded).signature"))
        XCTAssertTrue(JWT.isExpired("broken"))
    }

    func testAppLinksRouteInvitesAndStories() {
        let id = UUID()
        XCTAssertEqual(AppLink(URL(string: "https://thegrapevine.si/invite?code=GrapevineDemo")!), .invite("grapevinedemo"))
        XCTAssertEqual(AppLink(URL(string: "https://thegrapevine.si/story/\(id.uuidString.lowercased())")!), .story(id))
        XCTAssertNil(AppLink(URL(string: "https://thegrapevine.si/story/not-a-uuid")!))
        XCTAssertNil(AppLink(URL(string: "https://thegrapevine.si/invite")!))
        XCTAssertNil(AppLink(URL(string: "https://thegrapevine.si/invite?code=%3Cscript%3E")!))
        XCTAssertNil(AppLink(URL(string: "https://thegrapevine.si/invite?code=\(String(repeating: "a", count: 65))")!))
        XCTAssertEqual(AppLink(URL(string: "https://thegrapevine.si/invite?code=a1b2-c3_d4")!), .invite("a1b2-c3_d4"))
        XCTAssertNil(AppLink(URL(string: "https://thegrapevine.si/login")!))
        XCTAssertNil(AppLink(URL(string: "https://evil.example/story/\(id.uuidString.lowercased())")!))
        XCTAssertNil(AppLink(URL(string: "grapevine://story/\(id.uuidString.lowercased())")!))
    }

    func testSourceLinksOpenOnlyWebURLs() {
        func source(_ url: String) -> Source {
            Source(id: UUID(), story_id: UUID(), url: url, domain: "example.com", publisher: "", source_title: "", excerpt: nil,
                   image_url: nil, author: nil, submitted_by: nil, submitter_comment: nil, extraction_status: "ok", created_at: Date())
        }
        XCTAssertEqual(source("https://example.com/a").webURL, URL(string: "https://example.com/a"))
        XCTAssertNil(source("javascript:alert(1)").webURL)
        XCTAssertNil(source("file:///etc/hosts").webURL)
        XCTAssertNil(source("tel:123").webURL)
        XCTAssertEqual(source("https://example.com/a").displayPublisher, "example.com")
    }

    func testSharedTextYieldsItsLink() {
        XCTAssertEqual(SharedLink.extract("Look at this https://x.com/nasa/status/123"), "https://x.com/nasa/status/123")
        XCTAssertEqual(SharedLink.extract("\"Headline\" (https://example.com/a)."), "https://example.com/a")
        XCTAssertEqual(SharedLink.extract("https://en.wikipedia.org/wiki/Grape_(fruit)"), "https://en.wikipedia.org/wiki/Grape_(fruit)")
        XCTAssertEqual(SharedLink.extract("https://example.com/?q=1"), "https://example.com/?q=1")
        XCTAssertNil(SharedLink.extract("no link here"))
        XCTAssertNil(SharedLink.extract(nil))
    }

    @MainActor
    func testEmailChangeBodyNoticeAndDecode() throws {
        let body = try JSONSerialization.jsonObject(with: JSONEncoder().encode(API.EmailBody(email: "new@example.com"))) as? [String: String]
        XCTAssertEqual(body, ["email": "new@example.com"])

        let pending = EmailChange.interpret(current: "tim@example.com", requested: "new@example.com", email: "tim@example.com", newEmail: "new@example.com")
        XCTAssertNil(pending.appliedEmail)
        XCTAssertTrue(pending.message.contains("new@example.com"))
        XCTAssertTrue(pending.message.contains("tim@example.com"))
        XCTAssertTrue(pending.message.contains("both addresses"))

        let applied = EmailChange.interpret(current: "tim@example.com", requested: "New@Example.com", email: "new@example.com", newEmail: nil)
        XCTAssertEqual(applied.appliedEmail, "new@example.com")
        XCTAssertEqual(applied.message, "Your email is now new@example.com.")

        let adding = EmailChange.interpret(current: "  ", requested: "new@example.com", email: nil, newEmail: "")
        XCTAssertNil(adding.appliedEmail)
        XCTAssertFalse(adding.message.contains("both addresses"))
        XCTAssertTrue(adding.message.contains("new@example.com"))

        XCTAssertTrue(EmailChange.isPlausibleAddress("a@b.co"))
        XCTAssertFalse(EmailChange.isPlausibleAddress("not-an-email"))
        XCTAssertFalse(EmailChange.isPlausibleAddress("a@b"))
        XCTAssertFalse(EmailChange.isPlausibleAddress("a b@c.com"))

        let json = #"{"id":"7c9e6679-7425-40de-944b-e07fc1f90ae7","email":"tim@example.com","new_email":"new@example.com","aud":"authenticated"}"#
        let update = try JSONDecoder.supabase.decode(API.EmailUpdate.self, from: Data(json.utf8))
        XCTAssertEqual(update, API.EmailUpdate(email: "tim@example.com", new_email: "new@example.com"))
        let bare = try JSONDecoder.supabase.decode(API.EmailUpdate.self, from: Data(#"{"email":"tim@example.com"}"#.utf8))
        XCTAssertNil(bare.new_email)
    }

    func testDeleteAccountErrorBodyIsTheMessageShown() {
        let failure = APIError.http(500, #"{"error":"Your account could not be deleted. Nothing was removed. Please try again."}"#)
        XCTAssertEqual(failure.localizedDescription, "Your account could not be deleted. Nothing was removed. Please try again.")
        let denied = APIError.http(401, #"{"error":"Sign in first"}"#)
        XCTAssertEqual(denied.localizedDescription, "Sign in first")
        let gateway = APIError.http(401, #"{"message":"Invalid JWT"}"#)
        XCTAssertEqual(gateway.localizedDescription, "Invalid JWT")
    }

    @MainActor
    func testAccountDeletionRequiresTheDeletedFlag() throws {
        let ok = try JSONDecoder().decode(API.AccountDeletion.self, from: Data(#"{"deleted":true}"#.utf8))
        XCTAssertTrue(ok.deleted)
        let refused = try JSONDecoder().decode(API.AccountDeletion.self, from: Data(#"{"deleted":false}"#.utf8))
        XCTAssertFalse(refused.deleted)
        XCTAssertThrowsError(try JSONDecoder().decode(API.AccountDeletion.self, from: Data(#"{}"#.utf8)))
    }

    @MainActor
    func testVerifyBodySendsEmailType() throws {
        let body = try JSONSerialization.jsonObject(with: JSONEncoder().encode(API.VerifyBody(email: "tim@example.com", token: "123456"))) as? [String: String]
        XCTAssertEqual(body, ["email": "tim@example.com", "token": "123456", "type": "email"])
    }

    func testEditionKeepsThisWeekWithinItsBounds() {
        let now = Date()
        let day: TimeInterval = 24 * 3600
        func ages(_ days: [Double]) -> [Date] { days.map { now.addingTimeInterval(-$0 * day) } }
        func cut(_ rows: [Date]) -> (kept: Int, more: Bool) {
            let result = Edition.cut(rows, activity: { $0 }, now: now)
            return (result.kept.count, result.more)
        }
        // A quiet week still prints ten stories, then stops at the first older one.
        let quiet = cut(ages(Array(repeating: 30, count: 15)))
        XCTAssertEqual(quiet.kept, 10); XCTAssertTrue(quiet.more)
        // A busy week prints everything from the last seven days...
        let busy = cut(ages(Array(repeating: 1, count: 12) + [8, 9]))
        XCTAssertEqual(busy.kept, 12); XCTAssertTrue(busy.more)
        // ...but never more than forty, and the first query fetches one more to know.
        let packed = cut(ages(Array(repeating: 1, count: 41)))
        XCTAssertEqual(packed.kept, 40); XCTAssertTrue(packed.more)
        let full = cut(ages(Array(repeating: 1, count: 40)))
        XCTAssertEqual(full.kept, 40); XCTAssertFalse(full.more)
        // Small papers print everything.
        let small = cut(ages([0, 20, 40]))
        XCTAssertEqual(small.kept, 3); XCTAssertFalse(small.more)
        XCTAssertEqual(cut([]).kept, 0)
        // The cut is a prefix: a recent story after an old one past the minimum is left out.
        let prefix = cut(ages(Array(repeating: 1, count: 10) + [10, 1]))
        XCTAssertEqual(prefix.kept, 10); XCTAssertTrue(prefix.more)
    }

    func testArchiveCursorKeepsMicroseconds() throws {
        let date = try JSONDecoder.supabase.decode(Date.self, from: Data("\"2026-10-16T09:30:05.123456+00:00\"".utf8))
        XCTAssertEqual(Edition.timestamp(date), "2026-10-16T09:30:05.123456Z")
        XCTAssertEqual(Edition.timestamp(Date(timeIntervalSince1970: 0)), "1970-01-01T00:00:00.000000Z")
    }

    func testArchiveRowDecodesCountEmbeds() throws {
        let json = #"[{"id":"7c9e6679-7425-40de-944b-e07fc1f90ae7","title":"Ferry strike","category":"Local","last_activity_at":"2026-10-16T09:30:05.123456+00:00","sources":[{"count":3}],"messages":[{"count":1}]}]"#
        let rows = try JSONDecoder.supabase.decode([ArchiveRow].self, from: Data(json.utf8))
        XCTAssertEqual(rows.first?.sourceCount, 3)
        XCTAssertEqual(rows.first?.messageCount, 1)
    }

    func testMarkStoryReadDecodesATimeOrNull() throws {
        XCTAssertNil(try JSONDecoder.supabase.decode(Date?.self, from: Data("null".utf8)))
        let date = try JSONDecoder.supabase.decode(Date?.self, from: Data("\"2026-10-16T09:30:05.123456+00:00\"".utf8))
        XCTAssertEqual(date.map(Edition.timestamp), "2026-10-16T09:30:05.123456Z")
        let body = try JSONSerialization.jsonObject(with: JSONEncoder().encode(StoryStore.ReadBody(story: UUID()))) as? [String: String]
        XCTAssertEqual(body?.keys.sorted(), ["story"])
    }

    func testUnreadDividerCountsOthersMessagesSinceLastRead() {
        let me = UUID(), friend = UUID(), story = UUID()
        let lastRead = Date(timeIntervalSince1970: 1_000)
        func message(_ author: UUID, at seconds: TimeInterval) -> Message {
            Message(id: UUID(), story_id: story, author_id: author, body: "Hi", reply_to: nil,
                    created_at: Date(timeIntervalSince1970: seconds), reactions: nil, message_media: nil)
        }
        let old = message(friend, at: 900), mine = message(me, at: 1_100)
        let first = message(friend, at: 1_200), second = message(friend, at: 1_300)
        let divider = UnreadDivider(messages: [old, mine, first, second], lastRead: lastRead, userId: me)
        XCTAssertEqual(divider?.firstId, first.id)
        XCTAssertEqual(divider?.count, 2)
        XCTAssertEqual(divider?.since, lastRead)
        // First visit, or nothing new from anyone else: no divider.
        XCTAssertNil(UnreadDivider(messages: [old, first], lastRead: nil, userId: me))
        XCTAssertNil(UnreadDivider(messages: [old, mine], lastRead: lastRead, userId: me))
        XCTAssertNil(UnreadDivider(messages: [message(friend, at: 1_000)], lastRead: lastRead, userId: me))
    }

    func testFeedbackImagesFitWithin2048AsJPEG() throws {
        XCTAssertEqual(FeedbackImage.fittedSize(CGSize(width: 4032, height: 3024)), CGSize(width: 2048, height: 1536))
        XCTAssertEqual(FeedbackImage.fittedSize(CGSize(width: 1206, height: 2622)), CGSize(width: 942, height: 2048))
        XCTAssertEqual(FeedbackImage.fittedSize(CGSize(width: 800, height: 600)), CGSize(width: 800, height: 600))

        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        let screen = UIGraphicsImageRenderer(size: CGSize(width: 402, height: 874), format: format).image { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 402, height: 874))
        }
        let shot = FeedbackImage.jpeg(from: screen)
        XCTAssertEqual(Array(shot.prefix(2)), [0xFF, 0xD8])
        XCTAssertEqual(UIImage(data: shot)?.cgImage.map { CGSize(width: $0.width, height: $0.height) }, CGSize(width: 942, height: 2048))

        // A picked PNG comes back as a downscaled JPEG.
        format.scale = 1
        let png = UIGraphicsImageRenderer(size: CGSize(width: 3000, height: 1000), format: format).pngData { context in
            UIColor.blue.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 3000, height: 1000))
        }
        let picked = try XCTUnwrap(FeedbackImage.jpeg(from: png))
        XCTAssertEqual(Array(picked.prefix(2)), [0xFF, 0xD8])
        XCTAssertEqual(UIImage(data: picked)?.cgImage.map { CGSize(width: $0.width, height: $0.height) }, CGSize(width: 2048, height: 683))
        XCTAssertNil(FeedbackImage.jpeg(from: Data("not an image".utf8)))

        let image = try XCTUnwrap(FeedbackImage(jpeg: picked))
        let user = UUID()
        XCTAssertEqual(image.path(for: user), "\(user.uuidString.lowercased())/\(image.id.uuidString.lowercased()).jpg")
    }

    func testFeedbackBodyMatchesTheFunction() throws {
        let body = FeedbackBody(kind: FeedbackKind.enhancement.rawValue, description: "Bigger type", images: ["a/b.jpg"],
                                context: FeedbackContext(app_version: "1.2 (34)", os: "iOS 26.1", device: "iPhone17,1", screen: "Profile"))
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(body)) as? [String: Any])
        XCTAssertEqual(object["kind"] as? String, "enhancement")
        XCTAssertEqual(object["images"] as? [String], ["a/b.jpg"])
        // No error was shown, so none is sent.
        XCTAssertEqual(object["context"] as? [String: String],
                       ["platform": "ios", "app_version": "1.2 (34)", "os": "iOS 26.1", "device": "iPhone17,1", "screen": "Profile"])
        XCTAssertEqual(FeedbackKind.allCases.map(\.rawValue), ["bug", "enhancement", "feature"])

        let filed = try JSONDecoder().decode(FeedbackResult.self, from: Data(#"{"id":"3f1c","issue_number":42,"issue_url":"https://github.com/x/y/issues/42"}"#.utf8))
        XCTAssertEqual(filed.issue_number, 42)
        let saved = try JSONDecoder().decode(FeedbackResult.self, from: Data(#"{"id":"3f1c","issue_number":null,"issue_url":null}"#.utf8))
        XCTAssertNil(saved.issue_url)
    }
}
