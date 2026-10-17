import SwiftUI

@main
struct GrapevineApp: App {
    @State private var app = AppState()
    @State private var paper = PaperStore()

    init() {
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
    @State private var showPushReady = false
    /// Kept alive here: OneSignal holds push subscription observers weakly.
    @State private var pushObserver: AnyObject?

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
        // OneSignal integration check: shown once the device has a server-assigned
        // push subscription; this is the only place push permission is requested.
        .onAppear {
            guard pushObserver == nil else { return }
            pushObserver = OneSignalManager.shared.observePushRegistration { showPushReady = true }
        }
        .alert("Your OneSignal SDK integration is complete!", isPresented: $showPushReady) {
            Button("Got it") { OneSignalManager.shared.requestPushPermission() }
        } message: {
            Text("You can now send Push Notifications & In-App Messages through OneSignal. Tap below to enable push notifications.")
        }
    }
}
