import SwiftUI

struct FriendsView: View {
    @Environment(AppState.self) private var app

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    PageHead(kicker: "The people behind the paper", title: "Friends",
                             dateline: "\(app.members.count) at the table · \(app.group?.name ?? "Your paper")")
                    ForEach(Array(app.members.enumerated()), id: \.element.id) { index, member in
                        if index > 0 { Hairline() }
                        HStack(spacing: 14) {
                            MemberAvatar(profile: member.profiles, size: 42)
                            Text(member.profiles.display_name).font(Theme.headline(20))
                            Spacer()
                            if member.role == "owner" {
                                Kicker(text: "Owner", color: Theme.ink2, size: 10)
                                    .padding(.horizontal, 7).padding(.vertical, 4)
                                    .overlay(Rectangle().stroke(Theme.ink2, lineWidth: 1))
                            }
                        }
                        .padding(.vertical, 12)
                    }
                    if let group = app.group {
                        VStack(alignment: .center, spacing: 14) {
                            Kicker(text: "You’re cordially invited to", color: Theme.ink2, size: 10)
                            Text(group.name).font(Theme.nameplate(38)).multilineTextAlignment(.center)
                                .lineLimit(2).minimumScaleFactor(0.5)
                            Text("◆ ◆ ◆").font(Theme.serif(12)).foregroundColor(Theme.ink3)
                            Text("Good papers start with good company. Share an invitation to yours.")
                                .font(Theme.serif(17)).foregroundColor(Theme.ink2).multilineTextAlignment(.center)
                                .fixedSize(horizontal: false, vertical: true)
                            VStack(spacing: 6) {
                                Kicker(text: "Invite code", color: Theme.ink2, size: 10)
                                Text(group.invite_code).font(Theme.mono(17, bold: true))
                                    .textSelection(.enabled)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .clipping()
                            }
                            ShareLink(item: Config.webURL.appending(path: "invite").appending(queryItems: [URLQueryItem(name: "code", value: group.invite_code)])) {
                                Label("Share invitation", systemImage: "square.and.arrow.up")
                            }
                            .buttonStyle(InkButtonStyle())
                        }
                        .padding(.horizontal, 22).padding(.vertical, 26)
                        .background(Theme.raised)
                        .overlay(Rectangle().stroke(Theme.ink, lineWidth: 1))
                        .overlay(Rectangle().stroke(Theme.ink, lineWidth: 1).padding(5))
                        .padding(.top, 32)
                    }
                }
                .padding(.horizontal, 20).padding(.bottom, 24)
            }
            .background(Theme.paper)
            .toolbarVisibility(.hidden, for: .navigationBar)
        }
    }
}
