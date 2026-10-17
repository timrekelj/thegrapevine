import XCTest
@testable import Grapevine

final class CoreTests: XCTestCase {
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
