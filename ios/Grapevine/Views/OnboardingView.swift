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
    private let field = RoundedRectangle(cornerRadius: 14, style: .continuous)

    var body: some View {
        GeometryReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("MAKE YOURSELF AT HOME")
                            .font(.system(size: 11, weight: .semibold)).tracking(0.8).foregroundColor(Theme.ink2)
                        Text("Your people.\nYour paper.")
                            .font(.custom("Gloock", size: 40, relativeTo: .largeTitle)).foregroundColor(Theme.ink)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 36)
                    PaperPreview(name: displayName, paper: joining ? "" : groupName,
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
        .onAppear { if displayName.isEmpty { displayName = app.profile?.display_name ?? "" } }
        .onChange(of: app.pendingInviteCode, initial: true) {
            if let code = app.pendingInviteCode { inviteCode = code; joining = true }
        }
    }

    private var form: some View {
        VStack(alignment: .leading, spacing: 8) {
            label("YOUR NAME")
            paperField("What friends call you", text: $displayName).textContentType(.name)
                .focused($focus, equals: .name)
                .submitLabel(.next).onSubmit { focus = joining ? .code : .paper }
            if joining {
                label("INVITE CODE").padding(.top, 10)
                paperField("From your friend’s invite", text: $inviteCode)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .focused($focus, equals: .code)
                    .submitLabel(.join).onSubmit { Task { await join() } }
                errors
                Button(busy ? "Joining…" : "Join your friends") { Task { await join() } }
                    .buttonStyle(InkButtonStyle(enabled: ready && !inviteCode.trimmed.isEmpty, shape: field))
                    .disabled(busy || !ready || inviteCode.trimmed.isEmpty)
                    .padding(.top, 8)
                quietButton("Start a new paper instead") { joining = false; localError = nil }
            } else {
                label("NAME YOUR PAPER").padding(.top, 10)
                paperField("The Sunday Club", text: $groupName)
                    .focused($focus, equals: .paper)
                    .submitLabel(.go).onSubmit { Task { await create() } }
                errors
                Button(busy ? "Starting…" : "Start the paper") { Task { await create() } }
                    .buttonStyle(InkButtonStyle(enabled: ready && !groupName.trimmed.isEmpty, shape: field))
                    .disabled(busy || !ready || groupName.trimmed.isEmpty)
                    .padding(.top, 8)
                quietButton("Have an invite code? Join your friends") { joining = true; localError = nil }
            }
        }
    }

    private var ready: Bool { !displayName.trimmed.isEmpty }

    // MARK: - Pieces

    private func label(_ text: String) -> some View {
        Text(text).font(.system(size: 11, weight: .semibold)).tracking(0.8).foregroundColor(Theme.ink2)
    }

    private func paperField(_ placeholder: String, text: Binding<String>) -> some View {
        TextField(placeholder, text: text)
            .foregroundColor(Theme.ink)
            .padding(.horizontal, 16).frame(minHeight: 50)
            .background(Theme.raised, in: field)
            .overlay(field.stroke(Theme.rule, lineWidth: 1))
    }

    private func quietButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.subheadline.weight(.medium))
                .frame(maxWidth: .infinity, minHeight: 44)
                .foregroundColor(Theme.ink)
                .overlay(field.stroke(Theme.rule, lineWidth: 1))
                .contentShape(field)
        }
        .buttonStyle(.plain).disabled(busy)
        .padding(.top, 8)
    }

    @ViewBuilder private var errors: some View {
        if let message = localError ?? app.error {
            Label(message, systemImage: "exclamationmark.circle")
                .font(.subheadline.weight(.medium)).foregroundColor(Theme.error)
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

/// The paper being made, updated as you type: masthead, monogram and a first-edition stamp.
private struct PaperPreview: View {
    let name: String
    let paper: String
    let placeholder: String

    var body: some View {
        let initial = name.trimmed.first.map { String($0).uppercased() } ?? "?"
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(Date.now.formatted(.dateTime.weekday(.wide).day().month(.wide)))
                    .font(.system(size: 10, weight: .semibold)).tracking(0.8).textCase(.uppercase)
                    .foregroundColor(Theme.ink2)
                Spacer()
                Text(initial).font(Theme.display(13))
                    .frame(width: 26, height: 26)
                    .background(Theme.ink, in: Circle()).foregroundColor(Theme.paper)
                    .contentTransition(.numericText())
            }
            Text(paper.trimmed.isEmpty ? placeholder : paper.trimmed)
                .font(Theme.display(30)).foregroundColor(paper.trimmed.isEmpty ? Theme.ink2 : Theme.ink)
                .lineLimit(2).minimumScaleFactor(0.6)
                .padding(.top, 10)
            VStack(spacing: 2) {
                Rectangle().fill(Theme.ink).frame(height: 1)
                Rectangle().fill(Theme.ink).frame(height: 1)
            }
            .padding(.top, 10).padding(.bottom, 14)
            HStack(spacing: 14) {
                Halftone()
                    .frame(width: 96, height: 72)
                    .overlay(
                        Text("Your first link").font(Theme.display(12)).foregroundColor(Theme.ink)
                            .padding(.horizontal, 5).padding(.vertical, 3).background(Theme.raised)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                VStack(alignment: .leading, spacing: 6) {
                    Text("Share a link and it becomes the lead story.")
                        .font(Theme.display(17)).foregroundColor(Theme.ink)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("\(name.trimmed.isEmpty ? "You" : name.trimmed) · 1 member")
                        .font(.system(size: 13, weight: .medium)).foregroundColor(Theme.ink2).lineLimit(1)
                }
            }
        }
        .padding(.horizontal, 18).padding(.top, 16).padding(.bottom, 18)
        .background(Theme.raised, in: RoundedRectangle(cornerRadius: 4))
        .overlay(RoundedRectangle(cornerRadius: 4).stroke(Theme.rule, lineWidth: 1))
        .overlay(alignment: .topTrailing) {
            Stamp(text: "First edition").rotationEffect(.degrees(4)).offset(x: -14, y: -12)
        }
        .shadow(color: Theme.onPink.opacity(0.18), radius: 16, y: 12)
        .rotationEffect(.degrees(-1))
        .animation(.snappy(duration: 0.2), value: initial)
        .accessibilityHidden(true)
    }
}

/// A flat 30% screen, the plate printed for a story with no picture.
private struct Halftone: View {
    var body: some View {
        Canvas { context, size in
            let step: CGFloat = 4.5
            for x in stride(from: step / 2, to: size.width, by: step) {
                for y in stride(from: step / 2, to: size.height, by: step) {
                    context.fill(Path(ellipseIn: CGRect(x: x - 1.1, y: y - 1.1, width: 2.2, height: 2.2)), with: .color(Theme.ink))
                }
            }
        }
        .background(Theme.paper)
    }
}
