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

    var body: some View {
        #if DEBUG
        if DesignFixture.enabled { DesignPreviewView() } else { content }
        #else
        content
        #endif
    }

    private var content: some View {
        SwiftUI.Group {
            if !app.checked {
                LaunchPlate().transition(.opacity)
            } else if app.session == nil {
                LoginView().transition(.opacity)
            } else if app.group == nil {
                OnboardingView().transition(.opacity)
            } else {
                FrontPageView()
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

/// While the saved session is checked: the grape bunch mark on the ground, as on the icon.
private struct LaunchPlate: View {
    var body: some View {
        BunchMark(size: 104)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .accessibilityElement()
            .accessibilityLabel("The Grapevine, loading")
    }
}
