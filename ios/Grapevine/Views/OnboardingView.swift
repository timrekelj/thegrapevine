import SwiftUI

struct OnboardingView: View {
    @Environment(AppState.self) private var app
    @State private var displayName = ""
    @State private var groupName = ""
    @State private var inviteCode = ""
    @State private var busy = false
    @State private var localError: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                Text("Welcome to the paper").font(Theme.display(24))
                TextField("Your name", text: $displayName)
                    .padding().background(Theme.raised).clipShape(RoundedRectangle(cornerRadius: 14))
                Text("Start a paper").font(Theme.display(17))
                TextField("Name your paper", text: $groupName)
                    .padding().background(Theme.raised).clipShape(RoundedRectangle(cornerRadius: 14))
                Button { Task { await create() } } label: {
                    Text(busy ? "Starting…" : "Start the paper")
                        .frame(maxWidth: .infinity).frame(height: 48)
                }
                .buttonStyle(.plain)
                .background(Theme.ink).foregroundColor(Theme.paper)
                .clipShape(RoundedRectangle(cornerRadius: 24))
                .disabled(busy || groupName.trimmed.isEmpty)
                Text("or join a friend's paper")
                    .font(.system(size: 13)).foregroundColor(Theme.ink2)
                TextField("Invite code", text: $inviteCode)
                    .padding().background(Theme.raised).clipShape(RoundedRectangle(cornerRadius: 14))
                Button { Task { await join() } } label: {
                    Text("Join with a code")
                        .frame(maxWidth: .infinity).frame(height: 48)
                }
                .buttonStyle(.plain)
                .background(Theme.ink).foregroundColor(Theme.paper)
                .clipShape(RoundedRectangle(cornerRadius: 24))
                .disabled(busy || inviteCode.trimmed.isEmpty)
                if let localError {
                    Text(localError).font(.system(size: 13)).foregroundColor(Theme.pink)
                }
                if let error = app.error {
                    Text(error).font(.callout)
                    Button("Retry loading your paper") { Task { await app.loadProfileAndGroup() } }
                }
            }
            .padding(20)
        }
    }

    private func create() async {
        busy = true; localError = nil
        do {
            if !displayName.trimmed.isEmpty { try await app.saveDisplayName(displayName) }
            try await app.createGroup(name: groupName)
        } catch { localError = error.localizedDescription }
        busy = false
    }

    private func join() async {
        busy = true; localError = nil
        do {
            if !displayName.trimmed.isEmpty { try await app.saveDisplayName(displayName) }
            try await app.joinGroup(code: inviteCode)
        } catch { localError = error.localizedDescription }
        busy = false
    }
}
