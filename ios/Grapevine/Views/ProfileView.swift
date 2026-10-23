import SwiftUI

struct ProfileView: View {
    @Environment(AppState.self) private var app
    @Environment(PaperStore.self) private var paper
    @State private var name = ""
    @State private var savingName = false
    @State private var nameSaved = false
    @State private var nameError: String?
    @State private var preferences: NotificationPreferences?
    @State private var savingPreferences = false
    @State private var preferenceError: String?
    @State private var email = ""
    @State private var savingEmail = false
    @State private var emailMessage: String?
    @State private var emailError: String?
    @State private var confirmDelete = false
    @State private var deleting = false
    @State private var deleteError: String?
    @FocusState private var emailFocused: Bool

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 32) {
                    VStack(alignment: .leading, spacing: 0) {
                        PageHead(kicker: "Your corner of the paper", title: "Profile")
                        HStack(spacing: 16) {
                            if let profile = app.profile { MemberAvatar(profile: profile, size: 60) }
                            VStack(alignment: .leading, spacing: 4) {
                                Text(app.profile?.display_name ?? "Your account").font(Theme.headline(26))
                                Text(app.session?.email ?? "")
                                    .font(Theme.mono(12)).foregroundColor(Theme.ink2).textSelection(.enabled)
                            }
                        }
                        .padding(.top, 14)
                    }

                    VStack(alignment: .leading, spacing: 14) {
                        SectionHead(title: "Your name")
                        Text("This is how you appear to your friends.")
                            .font(Theme.serif(17)).foregroundColor(Theme.ink2)
                        TextField("Display name", text: $name)
                            .textContentType(.name)
                            .paperField()
                            .disabled(savingName)
                            .onChange(of: name) {
                                if name.trimmed != app.profile?.display_name { nameSaved = false }
                                nameError = nil
                            }
                        Button { Task { await saveName() } } label: {
                            Text(savingName ? "Saving…" : "Save name")
                        }
                        .buttonStyle(InkButtonStyle(enabled: canSaveName))
                        .disabled(!canSaveName)
                        if name.trimmed.count > 80 { Text("Use a name up to 80 characters.").font(.caption).foregroundColor(Theme.danger) }
                        if nameSaved { Text("Name saved").font(Theme.mono(12)).foregroundColor(Theme.ink2) }
                        if let nameError { Text(nameError).font(.caption).foregroundColor(Theme.danger) }
                    }

                    VStack(alignment: .leading, spacing: 14) {
                        SectionHead(title: "Your email")
                        Text(emailIntro)
                            .font(Theme.serif(17)).foregroundColor(Theme.ink2)
                        TextField("New email address", text: $email)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never).autocorrectionDisabled()
                            .textContentType(.emailAddress)
                            .submitLabel(.send).onSubmit { Task { await changeEmail() } }
                            .focused($emailFocused)
                            .paperField(focused: emailFocused, invalid: emailError != nil)
                            .disabled(savingEmail || deleting)
                            .accessibilityLabel("New email address")
                            .onChange(of: email) {
                                emailMessage = nil
                                emailError = nil
                            }
                        Button { Task { await changeEmail() } } label: {
                            Text(savingEmail ? "Sending…" : "Change email")
                        }
                        .buttonStyle(InkButtonStyle(enabled: canChangeEmail))
                        .disabled(!canChangeEmail)
                        if let emailMessage { Text(emailMessage).font(Theme.serif(17)).foregroundColor(Theme.ink2) }
                        if let emailError { Text(emailError).font(.caption).foregroundColor(Theme.danger) }
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        SectionHead(title: "Notifications")
                        Text("For \(app.group?.name ?? "your paper"). Changes save automatically.")
                            .font(Theme.serif(17)).foregroundColor(Theme.ink2)
                        if preferences != nil {
                            preferenceToggle("Mute this paper", detail: "Pause all notifications from this paper.", keyPath: \.muted)
                            Hairline()
                            preferenceToggle("New stories", detail: "When friends bring something new to the paper.", keyPath: \.new_stories)
                            preferenceToggle("Replies to you", detail: "When someone replies to your message.", keyPath: \.direct_replies)
                            preferenceToggle("Mentions", detail: "When someone mentions you in a conversation.", keyPath: \.mentions)
                        } else if preferenceError == nil {
                            ProgressView("Loading preferences…").tint(Theme.ink).padding(.vertical, 12)
                        }
                        if savingPreferences { ProgressView("Saving…").font(.caption).tint(Theme.ink) }
                        if let preferenceError {
                            Text(preferenceError).font(.caption).foregroundColor(Theme.danger)
                            if preferences == nil {
                                Button("Try again") { Task { await loadPreferences() } }.frame(minHeight: 44)
                            }
                        }
                        Text("Push notifications also need permission on this device.")
                            .font(Theme.mono(11)).foregroundColor(Theme.ink3)
                        Button {
                            Task {
                                let opened = await UIApplication.shared.open(URL(string: UIApplication.openNotificationSettingsURLString)!)
                                if !opened { await UIApplication.shared.open(URL(string: UIApplication.openSettingsURLString)!) }
                            }
                        } label: {
                            Label("Device notification settings", systemImage: "arrow.up.forward.app")
                                .font(.subheadline.weight(.medium)).frame(minHeight: 44)
                        }
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        SectionHead(title: "About & support")
                        Link(destination: URL(string: "mailto:hello@thegrapevine.si")!) {
                            Label("Help & account requests", systemImage: "envelope").frame(minHeight: 44)
                        }
                        Link(destination: Config.webURL.appending(path: "privacy")) {
                            Label("Privacy policy", systemImage: "hand.raised").frame(minHeight: 44)
                        }
                        Link(destination: Config.webURL.appending(path: "terms")) {
                            Label("Terms of service", systemImage: "doc.text").frame(minHeight: 44)
                        }
                        Text("The Grapevine · Version \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "")")
                            .font(Theme.mono(11)).foregroundColor(Theme.ink3)
                    }
                    .font(.system(size: 15))

                    VStack(alignment: .leading, spacing: 12) {
                        SectionHead(title: "Account")
                        Text("Sign out of The Grapevine on this device.")
                            .font(Theme.serif(17)).foregroundColor(Theme.ink2)
                        Button("Sign out", role: .destructive) {
                            paper.stories = []
                            paper.seenAt = nil
                            app.signOut()
                        }
                        .font(.system(size: 15, weight: .semibold)).frame(minHeight: 44)
                        .disabled(accountBusy)

                        Text("Deleting your account removes your sign-in and your name. Stories and comments you shared stay on the paper, without your name. A paper only you belong to is deleted. This cannot be undone.")
                            .font(Theme.serif(17)).foregroundColor(Theme.ink2)
                            .padding(.top, 8)
                        Button(deleting ? "Deleting…" : "Delete account", role: .destructive) {
                            confirmDelete = true
                        }
                        .font(.system(size: 15, weight: .semibold)).frame(minHeight: 44)
                        .disabled(accountBusy)
                        if deleting { ProgressView("Deleting your account…").tint(Theme.ink) }
                        if let deleteError { Text(deleteError).font(.caption).foregroundColor(Theme.danger) }
                    }
                }
                .padding(.horizontal, 20).padding(.bottom, 24)
            }
            .background(Theme.paper)
            .scrollDismissesKeyboard(.interactively)
            .toolbarVisibility(.hidden, for: .navigationBar)
            .confirmationDialog("Delete your account?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Delete account", role: .destructive) { Task { await deleteAccount() } }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This permanently deletes your sign-in. Your name comes off the paper; stories and comments you shared stay, without your name. A paper only you belong to is deleted. This cannot be undone.")
            }
            .task(id: app.group?.id) {
                name = app.profile?.display_name ?? ""
                await loadPreferences()
            }
        }
    }

    private var canSaveName: Bool {
        !savingName && !name.trimmed.isEmpty && name.trimmed.count <= 80 && name.trimmed != app.profile?.display_name
    }

    private var accountBusy: Bool { savingName || savingPreferences || savingEmail || deleting }

    private var emailIntro: String {
        let current = app.session?.email.trimmed ?? ""
        if current.isEmpty { return "Add the address that should receive sign-in codes and account mail." }
        return "Sign-in codes and account mail go to \(current)."
    }

    private var canChangeEmail: Bool {
        !accountBusy && emailMessage == nil && EmailChange.isPlausibleAddress(email)
            && email.trimmed.compare(app.session?.email ?? "", options: .caseInsensitive) != .orderedSame
    }

    private func preferenceToggle(_ title: String, detail: String, keyPath: WritableKeyPath<NotificationPreferences, Bool>) -> some View {
        Toggle(isOn: Binding(
            get: { preferences?[keyPath: keyPath] ?? false },
            set: { value in
                guard let previous = preferences else { return }
                var updated = previous
                updated[keyPath: keyPath] = value
                preferences = updated
                savingPreferences = true
                preferenceError = nil
                Task {
                    defer { savingPreferences = false }
                    do { preferences = try await app.saveNotificationPreferences(updated) }
                    catch { preferences = previous; preferenceError = error.localizedDescription }
                }
            }
        )) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(Theme.headline(18))
                Text(detail).font(Theme.serif(15)).foregroundColor(Theme.ink2)
            }
        }
        .padding(.vertical, 6)
        .disabled(savingPreferences || (keyPath != \.muted && preferences?.muted == true))
    }

    private func loadPreferences() async {
        preferenceError = nil
        preferences = nil
        do { preferences = try await app.loadNotificationPreferences() }
        catch { preferenceError = error.localizedDescription }
    }

    private func changeEmail() async {
        guard canChangeEmail else { return }
        savingEmail = true
        emailError = nil
        emailMessage = nil
        defer { savingEmail = false }
        do { emailMessage = try await app.changeEmail(to: email) }
        catch { emailError = error.localizedDescription }
    }

    private func deleteAccount() async {
        deleting = true
        deleteError = nil
        defer { deleting = false }
        do {
            try await app.deleteAccount()
            paper.stories = []
            paper.seenAt = nil
            app.signOut()
        } catch {
            deleteError = error.localizedDescription
        }
    }

    private func saveName() async {
        savingName = true
        nameError = nil
        defer { savingName = false }
        do {
            try await app.saveDisplayName(name)
            name = app.profile?.display_name ?? name
            nameSaved = true
        } catch { nameError = error.localizedDescription }
    }
}
