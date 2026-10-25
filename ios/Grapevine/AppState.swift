import Foundation
import SwiftUI

@MainActor
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

    func signIn(email: String) async throws {
        error = nil
        try await API.signInWithOtp(email: email)
    }

    /// Signs in with the code from the email.
    func verifyCode(email: String, code: String) async throws {
        error = nil
        try await finishSignIn(try await API.verifyOtp(email: email, code: code))
    }

    func signIn(callback: URL, verifier: String) async throws {
        error = nil
        try await finishSignIn(try await API.exchangeCode(OAuth.code(from: callback), verifier: verifier))
    }

    func signInWithApple(idToken: String, nonce: String) async throws {
        error = nil
        try await finishSignIn(try await API.signInWithApple(idToken: idToken, nonce: nonce))
    }

    private func finishSignIn(_ session: Session) async throws {
        try save(session)
        self.session = session
        API.session = session
        Telemetry.capture("signed_in")
        await loadProfileAndGroup()
    }

    func signOut() {
        Keychain.delete()
        session = nil; profile = nil; group = nil; members = []
        API.reset()
        PhotoStore.shared.removeAll()
        checked = true
    }

    /// Starts Supabase's email-change flow and returns the confirmation (or applied) message.
    /// A refreshed access token from that request is kept; only the address is overwritten when it changed.
    func changeEmail(to email: String) async throws -> String {
        guard let session else { throw APIError.unauthorized }
        let requested = email.trimmed
        guard EmailChange.isPlausibleAddress(requested) else { throw APIError.network("Enter a valid email address.") }
        guard requested.compare(session.email, options: .caseInsensitive) != .orderedSame else {
            throw APIError.network("That's already the email on this account.")
        }
        let update = try await API.updateEmail(to: requested)
        let change = EmailChange.interpret(current: session.email, requested: requested, email: update.email, newEmail: update.new_email)
        if let applied = change.appliedEmail {
            var updated = (API.session?.userId == session.userId ? API.session : nil) ?? session
            updated.email = applied
            try save(updated)
            API.session = updated
            self.session = updated
        }
        return change.message
    }

    /// Deletes the account on the server. The caller clears local paper state, then signs out.
    func deleteAccount() async throws {
        guard session != nil else { throw APIError.unauthorized }
        try await API.deleteAccount()
    }

    func loadProfileAndGroup() async {
        guard let session else { return }
        do {
            // Launch waits on these: each step's independent requests go out together.
            let membershipQuery = [
                URLQueryItem(name: "user_id", value: "eq.\(session.userId)"),
                URLQueryItem(name: "order", value: "joined_at.asc"),
                URLQueryItem(name: "limit", value: "1"),
            ] + (group.map { [URLQueryItem(name: "group_id", value: "eq.\($0.id)")] } ?? [])
            async let ownProfile = API.getOne(Profile.self, path: "rest/v1/profiles",
                query: [URLQueryItem(name: "id", value: "eq.\(session.userId)")])
            async let memberships = API.getMany(Membership.self, path: "rest/v1/group_members", query: membershipQuery)
            let (loadedProfile, membership) = try await (ownProfile, memberships.first)
            profile = loadedProfile
            guard let membership else { group = nil; return }
            async let paper = API.getOne(Group.self, path: "rest/v1/groups",
                query: [URLQueryItem(name: "id", value: "eq.\(membership.group_id)")])
            async let table = API.getMany(Member.self, path: "rest/v1/group_members", query: [
                URLQueryItem(name: "select", value: "user_id,role,profiles(id,display_name,monogram)"),
                URLQueryItem(name: "group_id", value: "eq.\(membership.group_id)"),
                URLQueryItem(name: "order", value: "joined_at.asc,user_id.asc"),
            ])
            let (loadedGroup, loadedMembers) = try await (paper, table)
            // Members first: the paper appears as soon as `group` is set, with its readers already known.
            MemberHues.record(loadedMembers.map(\.user_id))
            members = loadedMembers
            group = loadedGroup
            error = nil
        } catch {
            self.error = error.localizedDescription
            if case APIError.unauthorized = error { signOut() }
        }
    }

    func saveDisplayName(_ name: String) async throws {
        guard let session else { return }
        let name = name.trimmed
        guard !name.isEmpty, name.count <= 80 else { throw APIError.network("Use a name between 1 and 80 characters.") }
        let updated: Profile = try await API.patchRow(Profile.self, path: "rest/v1/profiles",
            query: [URLQueryItem(name: "id", value: "eq.\(session.userId)")],
            body: ["display_name": name, "monogram": String(name.prefix(1)).uppercased()])
        profile = updated
        members = members.map { member in
            member.user_id == updated.id ? Member(user_id: member.user_id, role: member.role, profiles: updated) : member
        }
    }

    func loadNotificationPreferences() async throws -> NotificationPreferences {
        guard let session, let group else { throw APIError.unauthorized }
        return try await API.getOne(NotificationPreferences.self, path: "rest/v1/notification_preferences", query: [
            URLQueryItem(name: "user_id", value: "eq.\(session.userId)"),
            URLQueryItem(name: "group_id", value: "eq.\(group.id)"),
        ]) ?? NotificationPreferences(user_id: session.userId, group_id: group.id)
    }

    func saveNotificationPreferences(_ preferences: NotificationPreferences) async throws -> NotificationPreferences {
        guard preferences.user_id == session?.userId, preferences.group_id == group?.id else { throw APIError.unauthorized }
        return try await API.upsertRow(NotificationPreferences.self, path: "rest/v1/notification_preferences", body: preferences)
    }

    struct NewGroupBody: Encodable { let name: String; let created_by: UUID }
    struct NewMemberBody: Encodable { let group_id: UUID; let user_id: UUID; let role = "owner" }

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

    func joinGroup(code: String) async throws {
        // A pasted invitation link works as well as the bare code.
        let code = (URLComponents(string: code.trimmed)?.queryItems?.first(where: { $0.name == "code" })?.value ?? code).trimmed
        guard AppLink.isInviteCode(code) else { throw APIError.network("This invite code is not valid.") }
        let joined: UUID = try await API.rpc(UUID.self, name: "join_group_with_invite",
            body: JoinBody(code: code.lowercased()))
        Telemetry.capture("paper_joined")
        pendingInviteCode = nil
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

    /// Hosts whose links the app opens: the site (claimed through associated domains) and, in development, the local web app.
    static let hosts: Set<String> = ["thegrapevine.si", "www.thegrapevine.si", Config.webURL.host() ?? "thegrapevine.si"]

    init?(_ url: URL) {
        guard ["https", "http"].contains(url.scheme ?? ""), let host = url.host()?.lowercased(), Self.hosts.contains(host) else { return nil }
        let parts = url.pathComponents.filter { $0 != "/" }
        if parts == ["invite"],
           let code = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "code" })?.value?.trimmed,
           Self.isInviteCode(code) {
            self = .invite(code.lowercased())
        } else if parts.count == 2, parts[0] == "story", let id = UUID(uuidString: parts[1]) {
            self = .story(id)
        } else {
            return nil
        }
    }

    /// A link fills the invite field unprompted, so only something shaped like a code: letters, digits,
    /// `-` and `_`, no longer than the server accepts.
    static func isInviteCode(_ code: String) -> Bool {
        (1...64).contains(code.count) && code.unicodeScalars.allSatisfy { $0.isASCII && (CharacterSet.alphanumerics.contains($0) || $0 == "-" || $0 == "_") }
    }
}
