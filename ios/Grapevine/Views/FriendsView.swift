import SwiftUI

struct FriendsView: View {
    @Environment(AppState.self) private var app

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    PageHead(title: "People",
                             dateline: "\(app.members.count) in \(app.group?.name ?? "your paper")")
                    VStack(spacing: 0) {
                        ForEach(Array(app.members.enumerated()), id: \.element.id) { index, member in
                            if index > 0 { Hairline() }
                            HStack(spacing: 14) {
                                MemberAvatar(profile: member.profiles, size: 42)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(member.profiles.display_name).font(Theme.text(17))
                                    if let detail = detail(member) {
                                        Text(detail).font(Theme.text(15)).foregroundColor(Theme.ink2)
                                    }
                                }
                                Spacer()
                            }
                            .padding(.horizontal, 16).padding(.vertical, 12)
                            .accessibilityElement(children: .combine)
                        }
                    }
                    .card(radius: 24)
                    if let group = app.group {
                        VStack(alignment: .leading, spacing: 12) {
                            FieldLabel(text: "Invite someone", size: 15)
                            HStack {
                                Text("Invite code").font(Theme.text(15)).foregroundColor(Theme.ink2)
                                Spacer()
                                Text(group.invite_code).font(Theme.text(20, weight: .bold)).monospacedDigit()
                                    .textSelection(.enabled)
                                Spacer()
                            }
                            .padding(.horizontal, 16).frame(minHeight: 56)
                            .background(Theme.raised2, in: RoundedRectangle(cornerRadius: 14))
                            Text("Anyone with the code can join and read everything in the paper.")
                                .font(Theme.text(15)).foregroundColor(Theme.ink2)
                                .fixedSize(horizontal: false, vertical: true)
                            ShareLink(item: Config.webURL.appending(path: "invite").appending(queryItems: [URLQueryItem(name: "code", value: group.invite_code)])) {
                                Label("Share invitation", systemImage: "square.and.arrow.up")
                            }
                            .buttonStyle(InkButtonStyle())
                            .padding(.top, 4)
                        }
                        .padding(.top, 32)
                    }
                }
                .padding(.horizontal, 20).padding(.bottom, 24)
            }
            .background(Theme.paper)
            .toolbarVisibility(.hidden, for: .navigationBar)
        }
    }

    private func detail(_ member: Member) -> String? {
        let you = member.user_id == app.session?.userId
        let owner = member.role == "owner"
        switch (you, owner) {
        case (true, true): return "You · started the paper"
        case (true, false): return "You"
        case (false, true): return "Started the paper"
        default: return nil
        }
    }
}
