import SwiftUI

@main
struct GrapevineApp: App {
    @State private var app = AppState()
    @State private var paper = PaperStore()

    init() {
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
                ProgressView().tint(Theme.ink)
            } else if app.session == nil {
                LoginView()
            } else if app.group == nil {
                OnboardingView()
            } else {
                TabView(selection: $tab) {
                    Tab("Paper", systemImage: "newspaper", value: 0) { FrontPageView() }
                    Tab("Friends", systemImage: "person.2", value: 1) { FriendsView() }
                }
                .tint(Theme.ink)
                .onChange(of: app.pendingStoryId) { _, id in if id != nil { tab = 0 } }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.paper.ignoresSafeArea())
        .foregroundColor(Theme.ink)
        .task(id: app.checked && app.session != nil) {
            if app.checked && app.session != nil {
                OneSignalManager.shared.requestPushPermission()
            }
        }
    }
}
