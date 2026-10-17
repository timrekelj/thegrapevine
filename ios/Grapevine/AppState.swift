import Foundation
import SwiftUI

@Observable
final class AppState {
    var session: Session? {
        didSet {
            if let session {
                Telemetry.identify(session.userId)
                OneSignalManager.shared.login(externalId: session.userId.uuidString.lowercased())
            } else {
                Telemetry.reset()
                OneSignalManager.shared.logout()
            }
        }
    }
    var profile: Profile?
    var group: Group?
    var members: [Member] = []
    var checked = false
    var error: String?
    /// An invitation link that arrived before onboarding.
    var pendingInviteCode: String?
    /// A story link, opened once the paper is showing.
    var pendingStoryId: UUID?

    func handleLink(_ url: URL) {
        switch AppLink(url) {
        case .invite(let code): pendingInviteCode = code
        case .story(let id): pendingStoryId = id
        case nil: break
        }
    }

    @MainActor
    func restoreSession() async {
        guard let data = Keychain.load(),
              let stored = try? JSONDecoder.supabase.decode(Session.self, from: data) else {
            OneSignalManager.shared.logout()
            Telemetry.reset()
            checked = true
            return
        }
        var session = stored
        if JWT.isExpired(session.accessToken) {
            do { session = try await API.refreshSession(refreshToken: session.refreshToken) }
            catch {
                if case APIError.unauthorized = error { signOut() }
                else {
                    self.session = session
                    API.session = session
                    self.error = error.localizedDescription
                }
                checked = true
                return
            }
        }
        self.session = session
        API.session = session
        do { try save(session) }
        catch { self.error = error.localizedDescription }
        await loadProfileAndGroup()
        checked = true
    }

    @MainActor
    func signIn(email: String) async throws {
        error = nil
        try await API.signInWithOtp(email: email)
    }

    /// Signs in with the code from the email.
    @MainActor
    func verifyCode(email: String, code: String) async throws {
        error = nil
        let session = try await API.verifyOtp(email: email, code: code)
        try save(session)
        self.session = session
        API.session = session
        Telemetry.capture("signed_in")
        await loadProfileAndGroup()
    }

    @MainActor
    func signOut() {
        Keychain.delete()
        session = nil; profile = nil; group = nil; members = []
        API.session = nil
        checked = true
    }

    @MainActor
    func loadProfileAndGroup() async {
        guard let session else { return }
        do {
            profile = try await API.getOne(Profile.self, path: "rest/v1/profiles",
                query: [URLQueryItem(name: "id", value: "eq.\(session.userId)")])
            var membershipQuery = [
                URLQueryItem(name: "user_id", value: "eq.\(session.userId)"),
                URLQueryItem(name: "order", value: "joined_at.asc"),
                URLQueryItem(name: "limit", value: "1"),
            ]
            if let group { membershipQuery.append(URLQueryItem(name: "group_id", value: "eq.\(group.id)")) }
            let memberships: [Membership] = try await API.getMany(Membership.self, path: "rest/v1/group_members", query: membershipQuery)
            guard let membership = memberships.first else { group = nil; return }
            group = try await API.getOne(Group.self, path: "rest/v1/groups",
                query: [URLQueryItem(name: "id", value: "eq.\(membership.group_id)")])
            members = try await API.getMany(Member.self, path: "rest/v1/group_members", query: [
                URLQueryItem(name: "select", value: "user_id,role,profiles(id,display_name,monogram)"),
                URLQueryItem(name: "group_id", value: "eq.\(membership.group_id)"),
                URLQueryItem(name: "order", value: "joined_at.asc"),
            ])
            error = nil
        } catch {
            self.error = error.localizedDescription
            if case APIError.unauthorized = error { signOut() }
        }
    }

    @MainActor
    func saveDisplayName(_ name: String) async throws {
        guard let session else { return }
        let name = name.trimmed
        guard !name.isEmpty, name.count <= 80 else { throw APIError.network("Use a name between 1 and 80 characters.") }
        let updated: Profile = try await API.patchRow(Profile.self, path: "rest/v1/profiles",
            query: [URLQueryItem(name: "id", value: "eq.\(session.userId)")],
            body: ["display_name": name, "monogram": String(name.prefix(1)).uppercased()])
        profile = updated
    }

    struct NewGroupBody: Encodable { let name: String; let created_by: UUID }
    struct NewMemberBody: Encodable { let group_id: UUID; let user_id: UUID; let role = "owner" }

    @MainActor
    func createGroup(name: String) async throws {
        guard let session else { return }
        let created: Group = try await API.postRow(Group.self, path: "rest/v1/groups",
            body: NewGroupBody(name: name, created_by: session.userId))
        try await API.postVoid(path: "rest/v1/group_members",
            body: NewMemberBody(group_id: created.id, user_id: session.userId))
        group = created
        Telemetry.capture("paper_created")
        await loadProfileAndGroup()
    }

    struct JoinBody: Encodable { let code: String }

    @MainActor
    func joinGroup(code: String) async throws {
        let code = URLComponents(string: code.trimmed)?.queryItems?.first(where: { $0.name == "code" })?.value ?? code.trimmed
        let joined: UUID = try await API.rpc(UUID.self, name: "join_group_with_invite",
            body: JoinBody(code: code.trimmed.lowercased()))
        Telemetry.capture("paper_joined")
        group = try await API.getOne(Group.self, path: "rest/v1/groups",
            query: [URLQueryItem(name: "id", value: "eq.\(joined)")])
        await loadProfileAndGroup()
    }

    private func save(_ session: Session) throws {
        try Keychain.save(JSONEncoder().encode(session))
    }
}

/// Where an incoming https://thegrapevine.si link should go.
enum AppLink: Equatable {
    case invite(String)
    case story(UUID)

    init?(_ url: URL) {
        guard ["https", "http"].contains(url.scheme ?? "") else { return nil }
        let parts = url.pathComponents.filter { $0 != "/" }
        if parts == ["invite"],
           let code = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "code" })?.value?.trimmed,
           !code.isEmpty {
            self = .invite(code.lowercased())
        } else if parts.count == 2, parts[0] == "story", let id = UUID(uuidString: parts[1]) {
            self = .story(id)
        } else {
            return nil
        }
    }
}
