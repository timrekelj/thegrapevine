import SwiftUI

@main
struct GrapevineApp: App {
    @State private var app = AppState()
    @State private var paper = PaperStore()

    init() {
        // Avatars and photographs are served with long cache lifetimes; keep them across launches.
        URLCache.shared = URLCache(memoryCapacity: 8 * 1024 * 1024, diskCapacity: 100 * 1024 * 1024)
        Telemetry.initialize()
        OneSignalManager.shared.initialize()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(app)
                .environment(paper)
                .task { await app.restoreSession() }
                .onOpenURL { app.handleLink($0) }
        }
    }
}

struct ContentView: View {
    @Environment(AppState.self) private var app
    @State private var tab = 0

    var body: some View {
        SwiftUI.Group {
            if !app.checked {
                LaunchPlate().transition(.opacity)
            } else if app.session == nil {
                LoginView().transition(.opacity)
            } else if app.group == nil {
                OnboardingView().transition(.opacity)
            } else {
                TabView(selection: $tab) {
                    Tab("Paper", systemImage: "newspaper", value: 0) { FrontPageView() }
                    Tab("Friends", systemImage: "person.2.fill", value: 1) { FriendsView() }
                    Tab("Profile", systemImage: "person.crop.circle", value: 2) { ProfileView() }
                }
                .tint(Theme.ink)
                .onChange(of: app.pendingStoryId) { _, id in if id != nil { tab = 0 } }
                .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.35), value: app.checked)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.paper.ignoresSafeArea())
        .foregroundColor(Theme.ink)
        .tint(Theme.ink)
        // Shaking the phone anywhere, once signed in, opens a bug report with a screenshot.
        .onReceive(NotificationCenter.default.publisher(for: .deviceDidShake)) { _ in
            if app.checked && app.session != nil { Feedback.presentForShake() }
        }
        .task(id: app.checked && app.session != nil) {
            if app.checked && app.session != nil {
                OneSignalManager.shared.requestPushPermission()
            }
        }
    }
}

/// While the saved session is checked: the nameplate on a blank page, and the dots going to press.
private struct LaunchPlate: View {
    var body: some View {
        VStack(spacing: 0) {
            Text("The Grapevine").font(Theme.nameplate(44))
                .lineLimit(1).minimumScaleFactor(0.6)
                .padding(.bottom, 10)
            DoubleRule()
            PressLoading().padding(.top, 28)
        }
        .padding(.horizontal, 40)
        .frame(maxWidth: 420)
    }
}
