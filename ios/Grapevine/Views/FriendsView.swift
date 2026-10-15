import SwiftUI

struct FriendsView: View {
    @Environment(AppState.self) private var app
    @Environment(PaperStore.self) private var paper

    var body: some View {
        NavigationStack {
            List {
                Section(app.group?.name ?? "Your paper") {
                    ForEach(app.members) { member in
                        HStack(spacing: 12) {
                            Monogram(profile: member.profiles)
                            Text(member.profiles.display_name)
                            Spacer()
                            if member.role == "owner" { Text("Owner").foregroundStyle(.secondary) }
                        }
                    }
                }
                if let group = app.group {
                    Section("Invite a friend") {
                        Text(group.invite_code).textSelection(.enabled)
                        ShareLink(item: Config.webURL.appending(path: "invite").appending(queryItems: [URLQueryItem(name: "code", value: group.invite_code)])) {
                            Label("Share invitation", systemImage: "square.and.arrow.up")
                        }
                    }
                }
                Section {
                    Button("Sign out", role: .destructive) {
                        paper.stories = []
                        paper.seenAt = nil
                        app.signOut()
                    }
                }
            }
            .navigationTitle("Friends")
        }
    }
}
