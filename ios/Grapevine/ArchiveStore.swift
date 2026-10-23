import Foundation
import SwiftUI

/// "Earlier editions": the group's stories older than this week's front page, a page at a time.
@MainActor
@Observable
final class ArchiveStore {
    var stories: [ArchiveRow] = []
    var hasMore = false
    var isLoading = false
    var loaded = false
    var error: String?

    /// Loads the first page, of stories last active before `before`; again only when `force`d.
    func load(group: UUID, before: Date, force: Bool = false) async {
        guard force || !loaded else { return }
        await fetch(group: group, before: before, replacing: true)
    }

    func loadEarlier(group: UUID) async {
        guard let last = stories.last else { return }
        await fetch(group: group, before: last.last_activity_at, replacing: false)
    }

    private func fetch(group: UUID, before: Date, replacing: Bool) async {
        guard !isLoading else { return }
        isLoading = true
        error = nil
        do {
            let rows: [ArchiveRow] = try await API.getMany(ArchiveRow.self, path: "rest/v1/stories", query: [
                URLQueryItem(name: "select", value: ArchiveRow.select),
                URLQueryItem(name: "group_id", value: "eq.\(group)"),
                URLQueryItem(name: "last_activity_at", value: "lt.\(Edition.timestamp(before))"),
                URLQueryItem(name: "order", value: "last_activity_at.desc"),
                URLQueryItem(name: "limit", value: "\(Edition.archivePage + 1)"),
            ])
            let page = Array(rows.prefix(Edition.archivePage))
            if replacing {
                stories = page
            } else {
                let known = Set(stories.map(\.id))
                stories += page.filter { !known.contains($0.id) }
            }
            hasMore = rows.count > Edition.archivePage
            loaded = true
        } catch where error.isCancellation {
            // Leaving the archive cancels its load; that isn't something to report.
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    /// The stories under their day, newest day first.
    var days: [(day: Date, stories: [ArchiveRow])] {
        let calendar = Calendar.current
        var groups: [(day: Date, stories: [ArchiveRow])] = []
        for story in stories {
            let day = calendar.startOfDay(for: story.last_activity_at)
            if groups.last?.day == day {
                groups[groups.count - 1].stories.append(story)
            } else {
                groups.append((day, [story]))
            }
        }
        return groups
    }
}
