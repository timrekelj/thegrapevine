import Foundation
import SwiftUI

@Observable
final class StoryStore {
    var story: Story?
    var sources: [Source] = []
    var messages: [Message] = []
    var profiles: [UUID: Profile] = [:]
    var history: [StoryHistoryEntry] = []
    var isLoading = false
    var error: String?
    var notice: String?
    var pendingMessageId: UUID?
    private var uploaded: [String] = []
    private var trackedStoryId: UUID?
    private var pendingReactions: Set<String> = []

    @MainActor
    func load(storyId: UUID) async {
        guard !isLoading else { return }
        isLoading = true
        error = nil
        do {
            story = try await API.getOne(Story.self, path: "rest/v1/stories",
                query: [URLQueryItem(name: "id", value: "eq.\(storyId)")])
            guard story != nil else { throw APIError.network("This story is no longer available.") }
            if trackedStoryId != storyId {
                Telemetry.capture("story_opened")
                trackedStoryId = storyId
            }
            sources = try await API.getMany(Source.self, path: "rest/v1/sources", query: [
                URLQueryItem(name: "story_id", value: "eq.\(storyId)"),
                URLQueryItem(name: "order", value: "created_at.desc"),
            ])
            messages = try await API.getMany(Message.self, path: "rest/v1/messages", query: [
                URLQueryItem(name: "select", value: "*,reactions(*),message_media(*)"),
                URLQueryItem(name: "story_id", value: "eq.\(storyId)"),
                URLQueryItem(name: "order", value: "created_at.asc"),
            ])
            history = try await API.getMany(StoryHistoryEntry.self, path: "rest/v1/story_history", query: [
                URLQueryItem(name: "story_id", value: "eq.\(storyId)"),
                URLQueryItem(name: "order", value: "created_at.desc"),
            ])
            let authorIds = Set(messages.map(\.author_id) + sources.compactMap(\.submitted_by))
            if !authorIds.isEmpty {
                let idList = authorIds.map { $0.uuidString }.joined(separator: ",")
                let found: [Profile] = try await API.getMany(Profile.self, path: "rest/v1/profiles", query: [
                    URLQueryItem(name: "select", value: "id,display_name,monogram"),
                    URLQueryItem(name: "id", value: "in.(\(idList))"),
                ])
                profiles = Dictionary(uniqueKeysWithValues: found.map { ($0.id, $0) })
            }
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    @MainActor
    func postMessage(body: String, replyTo: UUID?, attachments: [Attachment] = []) async -> Bool {
        guard let story, let session = API.session else { return false }
        let trimmed = body.trimmed
        guard !trimmed.isEmpty || !attachments.isEmpty else { return false }
        struct NewMessage: Encodable { let id: UUID; let story_id: UUID; let author_id: UUID; let body: String?; let reply_to: UUID? }
        struct NewMedia: Encodable { let message_id: UUID; let kind: String; let url: String; let position: Int; let alt_text: String }
        do {
            try Attachment.validate(attachments)
            if pendingMessageId == nil { pendingMessageId = UUID() }
            let id = pendingMessageId!
            // shortcut: abandoned uploads need server-side cleanup before a wider rollout.
            for index in uploaded.count..<attachments.count {
                let attachment = attachments[index]
                let path = "\(story.group_id.uuidString.lowercased())/\(session.userId.uuidString.lowercased())/\(id.uuidString.lowercased())-\(index).\(attachment.fileExtension)"
                do { try await API.upload(attachment, path: path) }
                catch APIError.http(409, _) { /* A retry may find the previous upload. */ }
                uploaded.append(path)
            }
            let existing = try await API.getOne(Message.self, path: "rest/v1/messages", query: [URLQueryItem(name: "id", value: "eq.\(id)")])
            if existing == nil {
                try await API.postVoid(path: "rest/v1/messages", body: NewMessage(id: id, story_id: story.id, author_id: session.userId, body: trimmed.isEmpty ? nil : trimmed, reply_to: replyTo))
            }
            let media: [Media] = try await API.getMany(Media.self, path: "rest/v1/message_media", query: [URLQueryItem(name: "message_id", value: "eq.\(id)")])
            for (index, path) in uploaded.enumerated() where !media.contains(where: { $0.position == index }) {
                try await API.postVoid(path: "rest/v1/message_media", body: NewMedia(message_id: id, kind: attachments[index].kind, url: path, position: index, alt_text: "Shared \(attachments[index].kind)"))
            }
            pendingMessageId = nil
            Telemetry.capture("message_sent", ["is_reply": replyTo != nil, "attachment_count": attachments.count])
            uploaded = []
            await load(storyId: story.id)
            return true
        } catch {
            self.error = "\(error.localizedDescription) Your draft is kept; retry Send."
            return false
        }
    }

    @MainActor
    func toggleReaction(message: Message, emoji: String) async {
        guard let session = API.session,
              let index = messages.firstIndex(where: { $0.id == message.id }) else { return }
        let key = "\(message.id):\(emoji)"
        // A second tap while the first is still saving would repeat the same insert or delete.
        guard !pendingReactions.contains(key) else { return }
        pendingReactions.insert(key)
        defer { pendingReactions.remove(key) }
        let previous = messages[index].reactions
        let mine = previous?.contains { $0.user_id == session.userId && $0.emoji == emoji } ?? false
        if mine {
            messages[index].reactions?.removeAll { $0.user_id == session.userId && $0.emoji == emoji }
        } else {
            messages[index].reactions = (previous ?? []) + [Reaction(id: UUID(), message_id: message.id, user_id: session.userId, emoji: emoji)]
        }
        do {
            if mine {
                // By key rather than id: an optimistic reaction has no server id yet.
                try await API.delete(path: "rest/v1/reactions", query: [
                    URLQueryItem(name: "message_id", value: "eq.\(message.id)"),
                    URLQueryItem(name: "user_id", value: "eq.\(session.userId)"),
                    URLQueryItem(name: "emoji", value: "eq.\(emoji)"),
                ])
            } else {
                struct NewReaction: Encodable { let message_id: UUID; let user_id: UUID; let emoji: String }
                do {
                    try await API.postVoid(path: "rest/v1/reactions",
                        body: NewReaction(message_id: message.id, user_id: session.userId, emoji: emoji))
                } catch APIError.http(409, _) { /* Already saved, e.g. from another device. */ }
            }
            if let story { await load(storyId: story.id) }
        } catch {
            if let index = messages.firstIndex(where: { $0.id == message.id }) { messages[index].reactions = previous }
            self.error = error.localizedDescription
        }
    }

    struct ReportBody: Encodable { let story_id: UUID; let target: String; let note: String? }
    struct ReportResponse: Codable { let id: UUID; let title: String; let summary: String }

    @MainActor
    func report(target: String, note: String) async -> Bool {
        guard let story else { return false }
        do {
            guard Telemetry.correctionsEnabled() else { throw APIError.network("Corrections are temporarily unavailable.") }
            let _: ReportResponse = try await API.postObject(ReportResponse.self, path: "functions/v1/report-story",
                body: ReportBody(story_id: story.id, target: target, note: note.trimmed.isEmpty ? nil : note.trimmed))
            notice = "Correction checked and saved."
            await load(storyId: story.id)
            return true
        } catch {
            self.error = error.localizedDescription
            return false
        }
    }
}
