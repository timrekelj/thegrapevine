#if DEBUG
import SwiftUI

/// Explicit launch-only fixture. It never creates a session or replaces authenticated data.
enum DesignFixture {
    static var enabled: Bool { ProcessInfo.processInfo.arguments.contains("--design-preview") }
    struct Data: Decodable { let group: Group; let members: [Member]; let stories: [StoryRow] }
    static let data: Data = {
        let url = Bundle.main.url(forResource: "DesignFixture", withExtension: "json")!
        return try! JSONDecoder.supabase.decode(Data.self, from: Foundation.Data(contentsOf: url))
    }()
    @MainActor static func storyStore(_ id: UUID) -> StoryStore {
        let store = StoryStore()
        guard let row = data.stories.first(where: { $0.id == id }) else { return store }
        store.story = Story(id: row.id, group_id: data.group.id, title: row.title, summary: row.summary, category: row.category, last_activity_at: row.last_activity_at, created_at: row.created_at)
        store.sources = row.sources; store.messages = row.messages
        store.profiles = Dictionary(uniqueKeysWithValues: data.members.map { ($0.user_id, $0.profiles) })
        store.lastRead = ISO8601DateFormatter().date(from: "2025-10-23T19:10:00Z")
        return store
    }
}

struct DesignPreviewView: View {
    @State private var app: AppState
    @State private var paper: PaperStore
    init() {
        MemberHues.record(DesignFixture.data.members.map(\.user_id)); let app = AppState(); app.group = DesignFixture.data.group; app.members = DesignFixture.data.members; app.profile = app.members.first?.profiles
        let paper = PaperStore(); paper.stories = DesignFixture.data.stories; paper.seenAt = ISO8601DateFormatter().date(from: "2025-10-23T19:10:00Z")
        _app = State(initialValue: app); _paper = State(initialValue: paper)
    }
    var body: some View { FrontPageView().environment(app).environment(paper).foregroundStyle(Theme.ink).tint(Theme.ink) }
}
#endif
