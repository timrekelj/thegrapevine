import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// "The Grapevine" in the system share sheet: the app's share form, with the
/// shared link filled in, posted to the paper with the app's saved session.
final class ShareViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        let items = extensionContext?.inputItems as? [NSExtensionItem] ?? []
        let host = UIHostingController(rootView: ShareExtensionView(items: items) { [weak self] in
            self?.extensionContext?.completeRequest(returningItems: nil)
        })
        addChild(host)
        host.view.frame = view.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(host.view)
        host.didMove(toParent: self)
    }
}

struct ShareExtensionView: View {
    let items: [NSExtensionItem]
    let close: () -> Void
    @State private var url: String?
    @State private var group: Group?
    @State private var notice: String?

    var body: some View {
        SwiftUI.Group {
            if let url {
                ShareView(group: group, url: url, notice: notice, close: close)
            } else {
                PressLoading(text: "Opening your paper").frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(Theme.paper.ignoresSafeArea())
        .task {
            async let link = sharedLink()
            await loadGroup()
            url = await link ?? ""
        }
    }

    /// The shared web link, or the first link in shared text (a post's text often carries it).
    private func sharedLink() async -> String? {
        let providers = items.flatMap { $0.attachments ?? [] }
        for provider in providers where provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
            if let url = try? await provider.loadItem(forTypeIdentifier: UTType.url.identifier) as? URL,
               ["http", "https"].contains(url.scheme?.lowercased() ?? "") {
                return url.absoluteString
            }
        }
        for provider in providers where provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
            if let link = SharedLink.extract(try? await provider.loadItem(forTypeIdentifier: UTType.plainText.identifier) as? String) {
                return link
            }
        }
        return items.lazy.compactMap { SharedLink.extract($0.attributedContentText?.string) }.first
    }

    /// The paper the app shows: the user's first membership, as in `AppState.loadProfileAndGroup`.
    @MainActor
    private func loadGroup() async {
        // The app's session, shared through the keychain access group.
        guard let data = Keychain.load(), let session = try? JSONDecoder.supabase.decode(Session.self, from: data) else {
            notice = "Open The Grapevine and sign in to share links with your paper."
            return
        }
        API.session = session
        do {
            let memberships = try await API.getMany(Membership.self, path: "rest/v1/group_members", query: [
                URLQueryItem(name: "user_id", value: "eq.\(session.userId)"),
                URLQueryItem(name: "order", value: "joined_at.asc"),
                URLQueryItem(name: "limit", value: "1"),
            ])
            guard let membership = memberships.first else {
                notice = "Open The Grapevine and join or start a paper to share links."
                return
            }
            group = try await API.getOne(Group.self, path: "rest/v1/groups",
                query: [URLQueryItem(name: "id", value: "eq.\(membership.group_id)")])
            if group == nil { notice = "Open The Grapevine and join or start a paper to share links." }
        } catch APIError.unauthorized {
            notice = "Your session has expired. Open The Grapevine and sign in again."
        } catch {
            notice = error.localizedDescription
        }
    }
}
