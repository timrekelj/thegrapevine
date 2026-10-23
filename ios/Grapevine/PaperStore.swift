import Foundation
import SwiftUI

@MainActor
@Observable
final class PaperStore {
    var stories: [StoryRow] = []
    var seenAt: Date?
    var isLoading = false
    var error: String?

    func load(app: AppState) async {
        guard !isLoading else { return }
        guard let session = app.session, let group = app.group else { return }
        isLoading = true
        error = nil
        do {
            let memberships: [Membership] = try await API.getMany(Membership.self, path: "rest/v1/group_members", query: [
                URLQueryItem(name: "user_id", value: "eq.\(session.userId)"),
                URLQueryItem(name: "group_id", value: "eq.\(group.id)"),
            ])
            seenAt = memberships.first?.last_seen_at
            let rows: [StoryRow] = try await API.getMany(StoryRow.self, path: "rest/v1/stories", query: [
                URLQueryItem(name: "select", value: "id,title,summary,category,last_activity_at,created_at,sources(\(Source.columns)),messages(*)"),
                URLQueryItem(name: "group_id", value: "eq.\(group.id)"),
            ])
            stories = rank(rows)
            // record this edition as seen
            struct SeenBody: Encodable { let last_seen_at: String }
            try? await API.patchVoid(path: "rest/v1/group_members", query: [
                URLQueryItem(name: "group_id", value: "eq.\(group.id)"),
                URLQueryItem(name: "user_id", value: "eq.\(session.userId)"),
            ], body: SeenBody(last_seen_at: Date.now.ISO8601Format()))
        } catch where error.isCancellation {
            // Leaving the page cancels its load; that isn't something to report.
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    /// Lead slot: most recent activity. The rest: discussion + new sources + recency.
    nonisolated func rank(_ rows: [StoryRow], now: Date = Date()) -> [StoryRow] {
        let cutoff = now.addingTimeInterval(-48 * 3600)
        let score: (StoryRow) -> Double = { story in
            let messages = Double(story.messages.filter { $0.created_at >= cutoff }.count)
            let sources = Double(story.sources.filter { $0.created_at >= cutoff }.count)
            let age = max(0, now.timeIntervalSince(story.last_activity_at))
            return 3 * messages + 2 * sources + 4 * pow(2, -age / (24 * 3600))
        }
        let byActivity = rows.sorted { $0.last_activity_at > $1.last_activity_at }
        guard let lead = byActivity.first else { return [] }
        return [lead] + byActivity.dropFirst().sorted { score($0) > score($1) }
    }
}
