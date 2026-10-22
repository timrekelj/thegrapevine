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

                    Button("Sign out", role: .destructive) {
                        paper.stories = []
                        paper.seenAt = nil
                        app.signOut()
                    }
                    .font(.system(size: 15, weight: .semibold)).frame(minHeight: 44)
                    .disabled(savingName || savingPreferences)
                }
                .padding(.horizontal, 20).padding(.bottom, 24)
            }
            .background(Theme.paper)
            .scrollDismissesKeyboard(.interactively)
            .toolbarVisibility(.hidden, for: .navigationBar)
            .task(id: app.group?.id) {
                name = app.profile?.display_name ?? ""
                await loadPreferences()
            }
        }
    }

    private var canSaveName: Bool {
        !savingName && !name.trimmed.isEmpty && name.trimmed.count <= 80 && name.trimmed != app.profile?.display_name
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
