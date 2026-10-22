import Foundation
import CoreTransferable
import UniformTypeIdentifiers

struct Profile: Codable, Identifiable, Hashable {
    let id: UUID
    let display_name: String
    let monogram: String
}

struct NotificationPreferences: Codable, Equatable {
    let user_id: UUID
    let group_id: UUID
    var new_stories = true
    var direct_replies = true
    var mentions = true
    var muted = false
}

struct Group: Codable, Identifiable {
    let id: UUID
    let name: String
    let invite_code: String
}

struct Membership: Codable {
    let group_id: UUID
    let last_seen_at: Date?
}

struct Member: Codable, Identifiable {
    let user_id: UUID
    let role: String
    let profiles: Profile
    var id: UUID { user_id }
}

struct Story: Codable, Identifiable {
    let id: UUID
    let group_id: UUID
    let title: String
    let summary: String
    let category: String?
    let last_activity_at: Date
    let created_at: Date
}

struct Source: Codable, Identifiable {
    let id: UUID
    let story_id: UUID
    let url: String
    let domain: String
    let publisher: String
    let source_title: String
    let excerpt: String?
    let image_url: String?
    let author: String?
    let submitted_by: UUID? // nil once the member has deleted their account
    let submitter_comment: String?
    let extraction_status: String
    let created_at: Date
}

struct Reaction: Codable, Identifiable {
    let id: UUID
    let message_id: UUID
    let user_id: UUID
    let emoji: String
}

struct Media: Codable, Identifiable {
    let id: UUID
    let message_id: UUID
    let kind: String
    let url: String
    let alt_text: String?
    let position: Int
}

struct Attachment {
    let data: Data
    let kind: String
    let contentType: String
    let fileExtension: String

    static func validate(_ attachments: [Attachment]) throws {
        guard attachments.filter({ $0.kind == "image" }).count <= 10 else {
            throw APIError.network("Attach up to 10 images.")
        }
        guard attachments.allSatisfy({ ["image", "video"].contains($0.kind) && !$0.data.isEmpty && $0.data.count <= 20 * 1024 * 1024 }) else {
            throw APIError.network("Each attachment must be an image or video, no larger than 20 MB.")
        }
    }
}

struct PickedFile: Transferable {
    let data: Data

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(importedContentType: .image) { file in try read(file.file) }
        FileRepresentation(importedContentType: .movie) { file in try read(file.file) }
    }

    private static func read(_ url: URL) throws -> PickedFile {
        let attributes = try FileManager.default.attributesOfItem(atPath: url.path)
        guard let size = attributes[.size] as? NSNumber, size.intValue <= 20 * 1024 * 1024 else {
            throw APIError.network("Attachments must be no larger than 20 MB.")
        }
        return PickedFile(data: try Data(contentsOf: url))
    }
}

struct Message: Codable, Identifiable {
    let id: UUID
    let story_id: UUID
    let author_id: UUID
    let body: String?
    let reply_to: UUID?
    let created_at: Date
    let reactions: [Reaction]?
    let message_media: [Media]?
}

struct StoryHistoryEntry: Codable, Identifiable {
    let id: UUID
    let story_id: UUID
    let title: String
    let summary: String
    let category: String?
    let created_at: Date
}

/// Front-page row: a story with its embedded sources and messages.
struct StoryRow: Codable, Identifiable {
    let id: UUID
    let title: String
    let summary: String
    let category: String?
    let last_activity_at: Date
    let created_at: Date
    let sources: [Source]
    let messages: [Message]
}

struct Session: Codable {
    var accessToken: String
    var refreshToken: String
    var userId: UUID
    var email: String
}

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}

/// The link inside text shared from another app, e.g. "Look at this https://x.com/… via @someone".
enum SharedLink {
    static func extract(_ text: String?) -> String? {
        guard let text, let range = text.range(of: #"https?://[^\s<>"]+"#, options: [.regularExpression, .caseInsensitive]) else { return nil }
        // Sentence punctuation after a link isn't part of it; a closing bracket is only when it opens inside.
        let punctuation = CharacterSet(charactersIn: ".,;:!?'\"")
        var url = String(text[range]).trimmingCharacters(in: punctuation)
        while url.hasSuffix(")"), url.filter({ $0 == ")" }).count > url.filter({ $0 == "(" }).count {
            url = String(url.dropLast()).trimmingCharacters(in: punctuation)
        }
        return url
    }
}

extension JSONDecoder {
    static let supabase: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let string = try container.decode(String.self)
            let withFraction = ISO8601DateFormatter()
            withFraction.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            if let date = withFraction.date(from: string) { return date }
            let plain = ISO8601DateFormatter()
            if let date = plain.date(from: string) { return date }
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Invalid date: \(string)")
        }
        return decoder
    }()
}
