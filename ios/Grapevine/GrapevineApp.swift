import SwiftUI

@main
struct GrapevineApp: App {
    @State private var app = AppState()
    @State private var paper = PaperStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(app)
                .environment(paper)
                .task { await app.restoreSession() }
        }
    }
}

struct ContentView: View {
    @Environment(AppState.self) private var app

    var body: some View {
        SwiftUI.Group {
            if !app.checked {
                ProgressView().tint(Theme.ink)
            } else if app.session == nil {
                LoginView()
            } else if app.group == nil {
                OnboardingView()
            } else {
                TabView {
                    Tab("Paper", systemImage: "newspaper") { FrontPageView() }
                    Tab("Friends", systemImage: "person.2") { FriendsView() }
                }
                .tint(Theme.ink)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.paper.ignoresSafeArea())
        .foregroundColor(Theme.ink)
    }
}
