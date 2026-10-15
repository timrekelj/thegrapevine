import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import AVKit

struct StoryView: View {
    @Environment(AppState.self) private var app
    @Environment(\.scenePhase) private var scenePhase
    @State private var store = StoryStore()
    let storyId: UUID

    @State private var draft = ""
    @State private var replyTo: Message?
    @State private var reportTarget = "summary"
    @State private var reportNote = ""
    @State private var showReport = false
    @State private var showHistory = false
    @State private var sending = false
    @State private var selectedPhotos: [PhotosPickerItem] = []
    @State private var attachments: [Attachment] = []
    @State private var loadingAttachments = false
    @FocusState private var composing: Bool

    private var readableSources: Int {
        store.sources.filter { $0.extraction_status == "ok" }.count
    }

    private var timeline: [TimelineEntry] {
        (store.sources.map { TimelineEntry.source($0) } + store.messages.map { TimelineEntry.message($0) })
            .sorted { $0.createdAt < $1.createdAt }
    }

    private var dayGroups: [(Date, [TimelineEntry])] {
        let calendar = Calendar.current
        var groups: [(Date, [TimelineEntry])] = []
        for entry in timeline {
            let day = calendar.startOfDay(for: entry.createdAt)
            if groups.last?.0 == day {
                groups[groups.count - 1].1.append(entry)
            } else {
                groups.append((day, [entry]))
            }
        }
        return groups
    }

    private var heroURL: URL? {
        store.sources.first(where: { $0.image_url != nil })?.image_url.flatMap { URL(string: $0) }
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                if let story = store.story {
                    PlateView(url: heroURL, label: store.sources.first?.publisher ?? "The paper", height: 180)
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Text((story.category ?? "News").uppercased())
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(Theme.section(story.category ?? "News"))
                            Spacer()
                            MetaRow(text: "Updated \(timeAgo(story.last_activity_at))")
                        }
                        Text(story.title).font(Theme.display(30))
                        Text(story.summary).font(.subheadline)
                        Button { showHistory = true } label: {
                            MetaRow(text: "Summary from \(readableSources) of \(store.sources.count) sources, newest first · What changed")
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(20)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(store.sources) { SourceClipView(source: $0) }
                        }
                        .padding(.horizontal, 20)
                    }
                    .padding(.vertical, 8)

                    HStack(spacing: 8) {
                        Text("Something off?").font(.system(size: 13)).foregroundColor(Theme.ink2)
                        Button("Report the summary") { reportTarget = "summary"; showReport = true }
                            .font(.system(size: 13, weight: .semibold)).foregroundColor(Theme.ink)
                        Text("·").foregroundColor(Theme.ink2)
                        Button("wrong category") { reportTarget = "category"; showReport = true }
                            .font(.system(size: 13, weight: .semibold)).foregroundColor(Theme.ink)
                    }
                    .padding(.horizontal, 20).padding(.vertical, 10)

                    Rectangle().fill(Theme.ink).frame(height: 1.5).padding(.horizontal, 20)

                    ForEach(dayGroups, id: \.0) { day, entries in
                        Text(dayLabel(day))
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(Theme.ink2)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 20).padding(.top, 16).padding(.bottom, 4)
                        ForEach(entries) { entry in
                            TimelineEntryView(entry: entry, store: store,
                                              userId: app.session?.userId ?? UUID(),
                                              onReply: { replyTo = $0 })
                        }
                    }

                    if let notice = store.notice {
                        MetaRow(text: notice).padding(.horizontal, 20).padding(.top, 12)
                    }
                } else if store.isLoading {
                    ProgressView().tint(Theme.ink).frame(maxWidth: .infinity).padding(60)
                } else if let error = store.error {
                    Text(error).padding()
                    Button("Try again") { Task { await store.load(storyId: storyId) } }
                }
            }
        }
        .background(Theme.paper)
        .scrollDismissesKeyboard(.interactively)
        .toolbar(.hidden, for: .tabBar)
        .toolbarVisibility(.visible, for: .navigationBar)
        .refreshable { await store.load(storyId: storyId) }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 8) {
                if let error = store.error {
                    Text(error).font(.caption).padding(.horizontal)
                }
                if !attachments.isEmpty {
                    HStack {
                        Text("\(attachments.count) attachment\(attachments.count == 1 ? "" : "s")").font(.caption)
                        Button("Remove") { selectedPhotos = []; attachments = [] }
                            .disabled(sending || store.pendingMessageId != nil)
                    }
                }
                if let replyTo {
                    HStack {
                        Text("Replying to \(store.profiles[replyTo.author_id]?.display_name ?? "…"): \(replyTo.body ?? "attachment")")
                            .font(.system(size: 13)).foregroundColor(Theme.ink2)
                            .lineLimit(1)
                        Spacer()
                        Button { self.replyTo = nil } label: { Image(systemName: "xmark") }
                            .tint(Theme.ink2)
                            .disabled(sending || store.pendingMessageId != nil)
                    }
                    .padding(.horizontal, 20)
                }
                HStack(spacing: 8) {
                    PhotosPicker(selection: $selectedPhotos, maxSelectionCount: 10, matching: .any(of: [.images, .videos])) {
                        Image(systemName: "plus").frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("Attach images or video")
                    .disabled(sending || loadingAttachments || store.pendingMessageId != nil)
                    TextField("Message", text: $draft)
                        .focused($composing)
                        .padding(.horizontal, 16).frame(height: 44)
                        .background(Theme.raised).clipShape(Capsule())
                        .disabled(sending || store.pendingMessageId != nil)
                    Button {
                        Task {
                            sending = true
                            if await store.postMessage(body: draft, replyTo: replyTo?.id, attachments: attachments) {
                                draft = ""
                                self.replyTo = nil
                                attachments = []
                                selectedPhotos = []
                                composing = false
                            }
                            sending = false
                        }
                    } label: {
                        Image(systemName: "arrow.up.circle.fill").font(.system(size: 32))
                    }
                    .tint(Theme.ink)
                    .disabled(sending || loadingAttachments || (draft.trimmed.isEmpty && attachments.isEmpty))
                    .accessibilityLabel("Send message")
                }
                .padding(.horizontal, 16).padding(.vertical, 8)
            }
            .padding(.top, 8)
            .background(.ultraThinMaterial)
        }
        .onChange(of: selectedPhotos) {
            Task {
                loadingAttachments = true
                defer { loadingAttachments = false }
                do {
                    var loaded: [Attachment] = []
                    for item in selectedPhotos {
                        guard let type = item.supportedContentTypes.first(where: { $0.conforms(to: .image) || $0.conforms(to: .movie) }),
                              let file = try await item.loadTransferable(type: PickedFile.self) else { throw APIError.network("Could not read the selected photo or video.") }
                        loaded.append(Attachment(data: file.data, kind: type.conforms(to: .movie) ? "video" : "image", contentType: type.preferredMIMEType ?? "application/octet-stream", fileExtension: type.preferredFilenameExtension ?? "bin"))
                        try Attachment.validate(loaded)
                    }
                    attachments = loaded
                    store.error = nil
                } catch { store.error = error.localizedDescription }
            }
        }
        .task(id: scenePhase) {
            guard scenePhase == .active else { return }
            await store.load(storyId: storyId)
            // shortcut: poll while this story is visible; use Realtime subscriptions for larger groups.
            while !Task.isCancelled {
                do { try await Task.sleep(for: .seconds(10)) }
                catch { return }
                if !sending && store.pendingMessageId == nil { await store.load(storyId: storyId) }
            }
        }
        .sheet(isPresented: $showReport) {
            ReportSheet(store: store, target: $reportTarget, note: $reportNote)
        }
        .sheet(isPresented: $showHistory) {
            HistorySheet(history: store.history)
        }
    }
}

enum TimelineEntry: Identifiable {
    case source(Source)
    case message(Message)

    var id: String {
        switch self {
        case .source(let source): return "s\(source.id)"
        case .message(let message): return "m\(message.id)"
        }
    }

    var createdAt: Date {
        switch self {
        case .source(let source): return source.created_at
        case .message(let message): return message.created_at
        }
    }
}

struct TimelineEntryView: View {
    let entry: TimelineEntry
    let store: StoryStore
    let userId: UUID
    let onReply: (Message) -> Void

    var body: some View {
        switch entry {
        case .source(let source):
            SourceEntryView(source: source, store: store)
        case .message(let message):
            MessageEntryView(message: message, store: store, userId: userId, onReply: onReply)
        }
    }
}

struct SourceEntryView: View {
    let source: Source
    let store: StoryStore

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                if let profile = store.profiles[source.submitted_by] {
                    Monogram(profile: profile, size: 28)
                    Text(profile.display_name).font(.system(size: 15, weight: .semibold))
                }
                Spacer()
                MetaRow(text: timeAgo(source.created_at))
            }
            if let comment = source.submitter_comment, !comment.isEmpty {
                Text(comment).font(.body)
            }
            VStack(alignment: .leading, spacing: 6) {
                MetaRow(text: source.publisher + (source.author.map { " · \($0)" } ?? ""))
                Text(source.source_title.isEmpty ? source.domain : source.source_title)
                    .font(Theme.display(17))
                if source.extraction_status == "paywalled" {
                    MetaRow(text: "Paywalled — headline only")
                } else if source.extraction_status == "failed" {
                    MetaRow(text: "Couldn't read this page")
                } else if source.extraction_status == "deleted" {
                    MetaRow(text: "Post deleted or unavailable")
                }
                if let url = URL(string: source.url) {
                    Link(destination: url) { MetaRow(text: "Open ↗") }
                }
            }
            .padding(12)
            .background(Theme.raised)
            .clipShape(RoundedRectangle(cornerRadius: 2))
            .overlay(RoundedRectangle(cornerRadius: 2).stroke(Theme.ink, lineWidth: 1))
        }
        .padding(.horizontal, 20).padding(.vertical, 10)
    }
}

struct MessageEntryView: View {
    let message: Message
    let store: StoryStore
    let userId: UUID
    let onReply: (Message) -> Void

    private static let emojis = ["🚀", "👏", "😂", "❤️", "🔥", "👀"]

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                if let author = store.profiles[message.author_id] {
                    Monogram(profile: author, size: 28)
                    Text(author.id == userId ? "You" : author.display_name)
                        .font(.system(size: 15, weight: .semibold))
                }
                Spacer()
                MetaRow(text: timeAgo(message.created_at))
            }
            if let parentId = message.reply_to,
               let parent = store.messages.first(where: { $0.id == parentId }) {
                HStack(spacing: 6) {
                    Rectangle().fill(Theme.rule).frame(width: 2)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(store.profiles[parent.author_id]?.display_name ?? "…")
                            .font(.system(size: 13, weight: .semibold))
                        Text(parent.body ?? "Attachment")
                            .font(.system(size: 13)).foregroundColor(Theme.ink2)
                            .lineLimit(1)
                    }
                }
                .padding(.leading, 8)
            }
            if let body = message.body, !body.isEmpty {
                Text(body).font(.body)
            }
            ForEach((message.message_media ?? []).sorted { $0.position < $1.position }) { media in
                MessageMediaView(media: media)
            }
            HStack(spacing: 6) {
                ForEach(Self.emojis, id: \.self) { emoji in
                    let count = message.reactions?.filter { $0.emoji == emoji }.count ?? 0
                    let mine = message.reactions?.contains { $0.emoji == emoji && $0.user_id == userId } ?? false
                    Button {
                        Task { await store.toggleReaction(message: message, emoji: emoji) }
                    } label: {
                        Text(count > 0 ? "\(emoji) \(count)" : emoji)
                            .font(.system(size: 13))
                            .padding(.horizontal, 8).padding(.vertical, 4)
                            .background(mine ? Theme.ink : Color.clear)
                            .foregroundColor(mine ? Theme.paper : Theme.ink)
                            .clipShape(Capsule())
                            .overlay(Capsule().stroke(mine ? Theme.ink : Theme.rule, lineWidth: 0.5))
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
                Button { onReply(message) } label: {
                    Image(systemName: "arrowshape.turn.up.left")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(Theme.ink2)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Reply to message")
                .disabled(store.pendingMessageId != nil)
            }
        }
        .padding(.horizontal, 20).padding(.vertical, 8)
    }
}

struct SourceClipView: View {
    let source: Source

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            PlateView(url: source.image_url.flatMap { URL(string: $0) }, label: source.publisher, height: 64)
                .frame(width: 64)
            MetaRow(text: source.publisher)
            Text(source.source_title.isEmpty ? source.domain : source.source_title)
                .font(Theme.display(13))
                .lineLimit(3)
        }
        .frame(width: 150, alignment: .leading)
        .padding(8)
        .overlay(RoundedRectangle(cornerRadius: 2).stroke(Theme.ink, lineWidth: 1))
    }
}

struct ReportSheet: View {
    let store: StoryStore
    @Binding var target: String
    @Binding var note: String
    @Environment(\.dismiss) private var dismiss
    @State private var busy = false

    var body: some View {
        NavigationStack {
            Form {
                Picker("What is wrong?", selection: $target) {
                    Text("Summary").tag("summary")
                    Text("Title").tag("title")
                    Text("Category").tag("category")
                }
                Section("Note (optional)") {
                    TextField("…", text: $note, axis: .vertical)
                }
                Button(busy ? "Reporting…" : "Report") {
                    Task {
                        busy = true
                        let success = await store.report(target: target, note: note)
                        busy = false
                        if success { dismiss() }
                    }
                }
                .disabled(busy)
                if let error = store.error { Text(error).font(.caption) }
            }
            .navigationTitle("Report a correction")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}

struct HistorySheet: View {
    let history: [StoryHistoryEntry]
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List(history) { version in
                VStack(alignment: .leading, spacing: 6) {
                    MetaRow(text: "\(dayLabel(version.created_at)) · \(timeAgo(version.created_at))")
                    Text(version.title).font(Theme.display(17))
                    Text(version.summary).font(.system(size: 15)).foregroundColor(Theme.ink2)
                }
            }
            .navigationTitle("What changed")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }
}
