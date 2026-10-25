import SwiftUI

struct OnboardingView: View {
    @Environment(AppState.self) private var app
    @State private var displayName = ""
    @State private var groupName = ""
    @State private var inviteCode = ""
    @State private var joining = false
    @State private var busy = false
    @State private var localError: String?
    @FocusState private var focus: Field?

    private enum Field { case name, paper, code }

    var body: some View {
        GeometryReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    VStack(alignment: .leading, spacing: 10) {
                        FieldLabel(text: "Make yourself at home", size: 15)
                        Text("Your people.\nYour paper.")
                            .font(Theme.headline(34)).lineSpacing(-4).foregroundColor(Theme.ink)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 36)
                    PaperPreview(profile: app.profile, name: displayName, paper: joining ? "" : groupName,
                                 placeholder: joining ? "Your friends’ paper" : "Your paper")
                        .padding(.horizontal, 4)
                    form.padding(.top, 28)
                }
                .padding(.horizontal, 24).padding(.vertical, 16)
                .frame(maxWidth: 468)
                .frame(maxWidth: .infinity, minHeight: proxy.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .scrollDismissesKeyboard(.interactively)
        .animation(.snappy(duration: 0.25), value: joining)
        .animation(.snappy(duration: 0.25), value: localError ?? app.error)
        .onAppear { if displayName.isEmpty { displayName = app.profile?.display_name ?? "" } }
        .onChange(of: app.pendingInviteCode, initial: true) {
            if let code = app.pendingInviteCode { inviteCode = code; joining = true }
        }
    }

    private var form: some View {
        VStack(alignment: .leading, spacing: 8) {
            label("Your name")
            paperField("What friends call you", text: $displayName).textContentType(.name)
                .focused($focus, equals: .name)
                .submitLabel(.next).onSubmit { focus = joining ? .code : .paper }
            if joining {
                label("Invite code").padding(.top, 10)
                paperField("From your friend’s invite", text: $inviteCode)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .focused($focus, equals: .code)
                    .submitLabel(.join).onSubmit { Task { await join() } }
                errors
                Button("Join your friends") { Task { await join() } }
                    .buttonStyle(InkButtonStyle(enabled: ready && !inviteCode.trimmed.isEmpty, busy: busy))
                    .disabled(busy || !ready || inviteCode.trimmed.isEmpty)
                    .busyAccessibility(busy, "Joining…")
                    .padding(.top, 8)
                quietButton("Start a new paper instead") { joining = false; localError = nil }
            } else {
                label("Name your paper").padding(.top, 10)
                paperField("The Sunday Club", text: $groupName)
                    .focused($focus, equals: .paper)
                    .submitLabel(.go).onSubmit { Task { await create() } }
                errors
                Button("Start the paper") { Task { await create() } }
                    .buttonStyle(InkButtonStyle(enabled: ready && !groupName.trimmed.isEmpty, busy: busy))
                    .disabled(busy || !ready || groupName.trimmed.isEmpty)
                    .busyAccessibility(busy, "Starting…")
                    .padding(.top, 8)
                quietButton("Have an invite code? Join your friends") { joining = true; localError = nil }
            }
        }
    }

    private var ready: Bool { !displayName.trimmed.isEmpty }

    // MARK: - Pieces

    private func label(_ text: String) -> some View {
        FieldLabel(text: text)
    }

    private func paperField(_ placeholder: String, text: Binding<String>) -> some View {
        TextField(placeholder, text: text).paperField()
    }

    private func quietButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.system(size: 15, weight: .medium))
                .frame(maxWidth: .infinity, minHeight: 46)
                .foregroundColor(Theme.ink)
                .overlay(Capsule().stroke(Theme.rule, lineWidth: 1))
                .contentShape(Rectangle())
        }
        .buttonStyle(PressStyle()).disabled(busy)
        .padding(.top, 8)
    }

    @ViewBuilder private var errors: some View {
        if let message = localError ?? app.error {
            Label(message, systemImage: "exclamationmark.circle")
                .font(.system(size: 14, weight: .medium)).foregroundColor(Theme.danger)
                .arrives()
            if localError == nil {
                Button("Retry loading your paper") { Task { await app.loadProfileAndGroup() } }
                    .font(.subheadline.weight(.medium)).foregroundColor(Theme.ink)
            }
        }
    }

    // MARK: - Actions

    private func create() async {
        guard !busy, ready, !groupName.trimmed.isEmpty else { return }
        busy = true; localError = nil
        do {
            try await app.saveDisplayName(displayName)
            try await app.createGroup(name: groupName)
        } catch { localError = error.localizedDescription }
        busy = false
    }

    private func join() async {
        guard !busy, ready, !inviteCode.trimmed.isEmpty else { return }
        busy = true; localError = nil
        do {
            try await app.saveDisplayName(displayName)
            try await app.joinGroup(code: inviteCode)
        } catch { localError = error.localizedDescription }
        busy = false
    }
}

/// The paper being made, updated as you type: a card with its name, your disc and the first story to come.
private struct PaperPreview: View {
    let profile: Profile?
    let name: String
    let paper: String
    let placeholder: String

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(paper.trimmed.isEmpty ? placeholder : paper.trimmed)
                        .font(Theme.headline(24)).foregroundColor(paper.trimmed.isEmpty ? Theme.ink3 : Theme.ink)
                        .lineLimit(2).minimumScaleFactor(0.6)
                    Text(Date.now.formatted(.dateTime.weekday(.wide).day().month(.wide)))
                        .font(Theme.text(15, weight: .semibold)).foregroundColor(Theme.ink2)
                }
                Spacer(minLength: 0)
                if let profile { MemberAvatar(profile: profile, size: 34) }
            }
            Hairline().padding(.vertical, 16)
            HStack(spacing: 14) {
                PublisherTile(label: "Your first link").frame(width: 64, height: 64)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Share a link and it becomes the lead story.")
                        .font(Theme.headline(17)).foregroundColor(Theme.ink)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("\(name.trimmed.isEmpty ? "You" : name.trimmed) · 1 member")
                        .font(Theme.text(13)).foregroundColor(Theme.ink3).lineLimit(1)
                }
            }
        }
        .padding(20)
        .card(radius: 28)
        .accessibilityHidden(true)
    }
}
