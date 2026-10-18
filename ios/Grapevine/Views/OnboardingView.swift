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
            VStack(alignment: .leading, spacing: 24) {
                Text("MAKE YOURSELF AT HOME")
                    .font(.system(size: 10, weight: .bold)).tracking(1.5).foregroundColor(Theme.ink2)
                Text("Your people.\nYour paper.").font(Theme.display(34))
                Text("A small place to share what’s worth reading.")
                    .font(.system(size: 16)).foregroundColor(Theme.ink2)
                Text("YOUR NAME").font(.system(size: 11, weight: .semibold)).foregroundColor(Theme.ink2)
                TextField("Your name", text: $displayName)
                    .padding().background(Theme.raised).clipShape(RoundedRectangle(cornerRadius: 14))
                VStack(alignment: .leading, spacing: 16) {
                    Text("Start a paper").font(Theme.display(23))
                    Text("Give your group a name. Invite friends next.")
                        .font(.system(size: 14)).foregroundColor(Theme.ink2)
                    TextField("Name your paper", text: $groupName)
                        .padding().background(Theme.paper).clipShape(RoundedRectangle(cornerRadius: 14))
                    Button { Task { await create() } } label: {
                        Text(busy ? "Starting…" : "Start the paper")
                            .frame(maxWidth: .infinity).frame(height: 48)
                    }
                    .buttonStyle(.plain)
                    .background(Theme.ink).foregroundColor(Theme.paper)
                    .clipShape(RoundedRectangle(cornerRadius: 24))
                    .opacity(busy || groupName.trimmed.isEmpty ? 0.4 : 1)
                    .disabled(busy || groupName.trimmed.isEmpty)
                }
                .padding(20).background(Theme.raised, in: RoundedRectangle(cornerRadius: 18))
                Text("ALREADY HAVE AN INVITATION?")
                    .font(.system(size: 13)).foregroundColor(Theme.ink2)
                VStack(alignment: .leading, spacing: 16) {
                    Text("Join your friends").font(Theme.display(23))
                    TextField("Invite code", text: $inviteCode)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                        .padding().background(Theme.paper).clipShape(RoundedRectangle(cornerRadius: 14))
                    Button { Task { await join() } } label: {
                        Text("Join with a code")
                            .frame(maxWidth: .infinity).frame(height: 48)
                    }
                    .buttonStyle(.plain)
                    .background(Theme.ink).foregroundColor(Theme.paper)
                    .clipShape(RoundedRectangle(cornerRadius: 24))
                    .opacity(busy || inviteCode.trimmed.isEmpty ? 0.4 : 1)
                    .disabled(busy || inviteCode.trimmed.isEmpty)
                }
                .padding(20).background(Theme.raised, in: RoundedRectangle(cornerRadius: 18))
                if let localError {
                    Text(localError).font(.system(size: 13)).foregroundColor(Theme.error)
                }
                if let error = app.error {
                    Text(error).font(.callout)
                    Button("Retry loading your paper") { Task { await app.loadProfileAndGroup() } }
                }
            }
            .padding(24).padding(.top, 24)
        }
        .onChange(of: app.pendingInviteCode, initial: true) {
            if let code = app.pendingInviteCode { inviteCode = code }
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
