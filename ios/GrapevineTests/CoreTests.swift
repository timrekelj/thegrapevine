import XCTest
@testable import Grapevine

final class CoreTests: XCTestCase {
    func testOAuthPKCEAndCallbackValidation() throws {
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
        XCTAssertNil(AppLink(URL(string: "https://thegrapevine.si/login")!))
    }

    @MainActor
    func testVerifyBodySendsEmailType() throws {
        let body = try JSONSerialization.jsonObject(with: JSONEncoder().encode(API.VerifyBody(email: "tim@example.com", token: "123456"))) as? [String: String]
        XCTAssertEqual(body, ["email": "tim@example.com", "token": "123456", "type": "email"])
    }
}
