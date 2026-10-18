import SwiftUI

struct FriendsView: View {
    @Environment(AppState.self) private var app

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("THE PEOPLE BEHIND THE PAPER")
                            .font(.system(size: 10, weight: .bold)).tracking(1.5)
                            .foregroundColor(Theme.ink2)
                        Text("Friends").font(Theme.display(34))
                        Text("\(app.members.count) at the table · \(app.group?.name ?? "Your paper")")
                            .font(.system(size: 14)).foregroundColor(Theme.ink2)
                    }
                    VStack(spacing: 0) {
                        ForEach(app.members) { member in
                            HStack(spacing: 14) {
                                Monogram(profile: member.profiles, size: 40)
                                Text(member.profiles.display_name)
                                    .font(.system(size: 16, weight: .medium))
                                Spacer()
                                if member.role == "owner" {
                                    Text("Owner").font(.system(size: 12, weight: .medium))
                                        .foregroundColor(Theme.ink2)
                                        .padding(.horizontal, 10).padding(.vertical, 5)
                                        .background(Theme.paper, in: Capsule())
                                }
                            }
                            .padding(16)
                        }
                    }
                    .background(Theme.raised, in: RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.rule, lineWidth: 0.5))
                    if let group = app.group {
                        VStack(alignment: .leading, spacing: 14) {
                            Text("Make room for a friend").font(Theme.display(23))
                            Text("Good papers start with good company. Share an invitation to yours.")
                                .font(.system(size: 15)).lineSpacing(3).foregroundColor(Theme.ink2)
                            Text("INVITE CODE")
                                .font(.system(size: 10, weight: .semibold)).tracking(1)
                                .foregroundColor(Theme.ink2)
                            Text(group.invite_code).font(.system(.body, design: .monospaced))
                                .textSelection(.enabled)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(14).background(Theme.paper, in: RoundedRectangle(cornerRadius: 10))
                            ShareLink(item: Config.webURL.appending(path: "invite").appending(queryItems: [URLQueryItem(name: "code", value: group.invite_code)])) {
                                Label("Share invitation", systemImage: "square.and.arrow.up")
                                    .font(.system(size: 15, weight: .semibold))
                                    .frame(maxWidth: .infinity, minHeight: 48)
                            }
                            .buttonStyle(.plain)
                            .background(Theme.ink, in: Capsule()).foregroundColor(Theme.paper)
                        }
                        .padding(20).background(Theme.raised, in: RoundedRectangle(cornerRadius: 18))
                        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.rule, lineWidth: 0.5))
                    }

                }
                .padding(24)
            }
            .background(Theme.paper)
            .toolbarVisibility(.hidden, for: .navigationBar)
        }
    }
}
