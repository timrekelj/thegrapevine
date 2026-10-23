import Foundation

/// The front page is this week's edition: the stories active in the last week, never fewer than
/// `min` nor more than `max`. Older stories are in the archive, "Earlier editions".
enum Edition {
    static let days = 7
    static let min = 10
    static let max = 40
    static let archivePage = 20

    /// Keeps the prefix of `rows` (newest activity first) that belongs in this week's edition, and
    /// reports whether anything was left out, i.e. whether there are earlier editions to link to.
    static func cut<Row>(_ rows: [Row], activity: (Row) -> Date, now: Date = Date()) -> (kept: [Row], more: Bool) {
        let since = now.addingTimeInterval(-Double(days) * 24 * 3600)
        var kept: [Row] = []
        for (index, row) in rows.enumerated() {
            guard kept.count < max, index < min || activity(row) >= since else { break }
            kept.append(row)
        }
        return (kept, rows.count > kept.count)
    }

    /// A timestamp for a PostgREST filter, to the microsecond like Postgres, in UTC (a `Z` rather than
    /// `+00:00`, whose plus would read as a space in a query string).
    static func timestamp(_ date: Date) -> String {
        let micros = (date.timeIntervalSince1970 * 1_000_000).rounded()
        let seconds = (micros / 1_000_000).rounded(.down)
        let fraction = Int(micros - seconds * 1_000_000)
        let whole = Date(timeIntervalSince1970: seconds).ISO8601Format() // 2026-10-16T12:00:00Z
        return "\(whole.dropLast()).\(String(format: "%06d", fraction))Z"
    }
}

/// Where the conversation's "N new since HH:MM" divider goes: before the first message someone
/// else wrote after the reader's previous visit. Nothing on a first visit (no previous read).
struct UnreadDivider: Equatable {
    let firstId: UUID
    let count: Int
    let since: Date

    init?(messages: [Message], lastRead: Date?, userId: UUID?) {
        guard let lastRead else { return nil }
        let unread = messages
            .filter { $0.created_at > lastRead && $0.author_id != userId }
            .sorted { $0.created_at < $1.created_at }
        guard let first = unread.first else { return nil }
        firstId = first.id
        count = unread.count
        since = lastRead
    }
}
