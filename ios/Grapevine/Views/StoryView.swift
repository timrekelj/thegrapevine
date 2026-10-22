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

    private var lead: Source? {
        store.sources.first(where: { $0.image_url != nil }) ?? store.sources.first
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                DoubleRule().padding(.bottom, 22)
                if let story = store.story {
                    article(story)
                    clippings.padding(.top, 8)
                    conversation.padding(.top, 36)
                    if let notice = store.notice {
                        MetaRow(text: notice).padding(.top, 12)
                    }
                } else if store.isLoading {
                    ProgressView().tint(Theme.ink).frame(maxWidth: .infinity).padding(60)
                } else if let error = store.error {
                    Text(error).font(Theme.serif(17)).padding(.vertical)
                    Button("Try again") { Task { await store.load(storyId: storyId) } }
                        .font(.system(size: 15, weight: .semibold)).underline()
                }
            }
            .padding(.horizontal, 20).padding(.bottom, 24)
        }
        .background(Theme.paper)
        .scrollDismissesKeyboard(.interactively)
        .toolbar(.hidden, for: .tabBar)
        .toolbarVisibility(.visible, for: .navigationBar)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Theme.paper, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text("The Grapevine").font(Theme.nameplate(24)).foregroundColor(Theme.ink)
            }
            // Story links open the app on phones that have it, and the website everywhere else.
            ToolbarItem(placement: .topBarTrailing) {
                ShareLink(item: Config.webURL.appending(path: "story/\(storyId.uuidString.lowercased())")) {
                    Image(systemName: "square.and.arrow.up")
                }
                .accessibilityLabel("Share story")
            }
        }
        .refreshable { await store.load(storyId: storyId) }
        .safeAreaInset(edge: .bottom) { composer }
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
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showHistory) {
            HistorySheet(history: store.history)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
    }
}

extension StoryView {
    @ViewBuilder private func article(_ story: Story) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Kicker(text: story.category ?? "Unfiled")
            Text("Updated \(timeAgo(story.last_activity_at))").font(Theme.mono(12)).foregroundColor(Theme.ink3)
        }
        Text(story.title.isEmpty ? "Reading this story" : story.title)
            .font(Theme.headline(40)).lineSpacing(-4)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 12).padding(.bottom, 16)
            .accessibilityAddTraits(.isHeader)
        Button { showHistory = true } label: {
            Text("Filed from \(Text("\(readableSources)").font(Theme.mono(12, bold: true)).foregroundColor(Theme.ink)) of \(Text("\(store.sources.count)").font(Theme.mono(12, bold: true)).foregroundColor(Theme.ink)) \(store.sources.count == 1 ? "source" : "sources") · \(Text("What changed").underline().font(Theme.mono(12, bold: true)).foregroundColor(Theme.ink))")
                .font(Theme.mono(12)).foregroundColor(Theme.ink2)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 9)
                .overlay(alignment: .top) { Rectangle().fill(Theme.ink).frame(height: 1) }
                .overlay(alignment: .bottom) { Hairline() }
        }
        .buttonStyle(.plain)
        PlateView(url: heroURL, label: lead.map { $0.publisher.isEmpty ? $0.domain : $0.publisher } ?? "The paper")
            .aspectRatio(16 / 9, contentMode: .fit)
            .padding(.top, 20)
        if let lead {
            Text("Pictured via \(lead.publisher.isEmpty ? lead.domain : lead.publisher)")
                .font(Theme.mono(11)).foregroundColor(Theme.ink3).padding(.top, 8)
        }
        if story.summary.isEmpty {
            Text("The summary could not be written.").font(Theme.serif(19)).padding(.vertical, 20)
        } else {
            DropCapText(text: story.summary, size: 19).padding(.vertical, 20)
        }
        HStack(spacing: 0) {
            Text("Something off? ").foregroundColor(Theme.ink3)
            Button("Report the summary") { reportTarget = "summary"; showReport = true }
                .fontWeight(.semibold).underline().foregroundColor(Theme.ink)
            Text(" · ").foregroundColor(Theme.ink3)
            Button("wrong category") { reportTarget = "category"; showReport = true }
                .fontWeight(.semibold).underline().foregroundColor(Theme.ink)
        }
        .font(Theme.mono(12))
        .buttonStyle(.plain)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(alignment: .top) { Hairline() }
    }

    @ViewBuilder private var clippings: some View {
        if !store.sources.isEmpty {
            HStack(alignment: .firstTextBaseline) {
                Kicker(text: "Clippings")
                Spacer()
                Text("\(store.sources.count)").font(Theme.mono(12)).foregroundColor(Theme.ink2)
            }
            .padding(.vertical, 10)
            .overlay(alignment: .bottom) { Rectangle().fill(Theme.ink).frame(height: 1) }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(store.sources) { SourceClipView(source: $0) }
                }
                .padding(.horizontal, 20).padding(.vertical, 12)
            }
            .padding(.horizontal, -20)
        }
    }

    @ViewBuilder private var conversation: some View {
        SectionHead(title: "The conversation").padding(.bottom, 4)
        if timeline.isEmpty {
            Text("Start the conversation.").font(Theme.serif(17)).foregroundColor(Theme.ink2).padding(.vertical, 20)
        }
        ForEach(dayGroups, id: \.0) { day, entries in
            HStack(spacing: 12) {
                Hairline()
                Text(dayLabel(day)).font(Theme.mono(10, bold: true)).tracking(1.2).foregroundColor(Theme.ink3).fixedSize()
                Hairline()
            }
            .padding(.top, 16).padding(.bottom, 2)
            ForEach(entries) { entry in
                TimelineEntryView(entry: entry, store: store,
                                  userId: app.session?.userId ?? UUID(),
                                  onReply: { replyTo = $0 })
            }
        }
    }

    private var composer: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let error = store.error {
                Text(error).font(.system(size: 13, weight: .medium)).foregroundColor(Theme.danger)
            }
            if !attachments.isEmpty {
                HStack {
                    Text(plural(attachments.count, "attachment")).font(Theme.mono(12))
                    Spacer()
                    Button("Remove") { selectedPhotos = []; attachments = [] }
                        .font(Theme.mono(12, bold: true)).underline().foregroundColor(Theme.ink)
                        .disabled(sending || store.pendingMessageId != nil)
                }
                .padding(.horizontal, 10).padding(.vertical, 7).background(Theme.tint)
            }
            if let replyTo {
                HStack {
                    Text("Replying to \(store.profiles[replyTo.author_id]?.display_name ?? "…"): \(replyTo.body ?? "attachment")")
                        .font(Theme.mono(12)).foregroundColor(Theme.ink2)
                        .lineLimit(1)
                    Spacer()
                    Button { self.replyTo = nil } label: { Image(systemName: "xmark").font(.system(size: 12, weight: .semibold)) }
                        .tint(Theme.ink2)
                        .disabled(sending || store.pendingMessageId != nil)
                }
                .padding(.horizontal, 10).padding(.vertical, 7).background(Theme.tint)
            }
            HStack(spacing: 8) {
                PhotosPicker(selection: $selectedPhotos, maxSelectionCount: 10, matching: .any(of: [.images, .videos])) {
                    Image(systemName: "plus").font(.system(size: 16, weight: .medium))
                        .frame(width: 44, height: 44)
                        .overlay(RoundedRectangle(cornerRadius: Theme.radius).stroke(Theme.rule, lineWidth: 1))
                }
                .foregroundColor(Theme.ink)
                .accessibilityLabel("Attach images or video")
                .disabled(sending || loadingAttachments || store.pendingMessageId != nil)
                TextField("Write to the table…", text: $draft)
                    .focused($composing)
                    .font(Theme.serif(17))
                    .padding(.horizontal, 12).frame(height: 44)
                    .background(Theme.raised, in: RoundedRectangle(cornerRadius: Theme.radius))
                    .overlay(RoundedRectangle(cornerRadius: Theme.radius).stroke(composing ? Theme.ink : Theme.rule, lineWidth: 1))
                    .disabled(sending || store.pendingMessageId != nil)
                let canSend = !(sending || loadingAttachments || (draft.trimmed.isEmpty && attachments.isEmpty))
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
                    Text(sending ? "Sending…" : "Send").padding(.horizontal, 16)
                }
                .buttonStyle(InkButtonStyle(enabled: canSend, height: 44))
                .fixedSize()
                .disabled(!canSend)
                .accessibilityLabel("Send message")
            }
        }
        .padding(.horizontal, 16).padding(.top, 10).padding(.bottom, 8)
        .background(Theme.paper.opacity(0.94))
        .background(.ultraThinMaterial)
        .overlay(alignment: .top) { Rectangle().fill(Theme.ink).frame(height: 1) }
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
        HStack(alignment: .top, spacing: 12) {
            if let id = source.submitted_by, let profile = store.profiles[id] {
                MemberAvatar(profile: profile, size: 36)
            } else {
                Circle().fill(Theme.tint).frame(width: 36, height: 36)
            }
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(source.submitted_by.flatMap { store.profiles[$0]?.display_name } ?? "A member")
                        .font(.system(size: 15, weight: .semibold))
                    Text("filed a source · \(clock(source.created_at))").font(Theme.mono(12)).foregroundColor(Theme.ink3)
                }
                if let comment = source.submitter_comment, !comment.isEmpty {
                    Text(comment).font(Theme.serif(18)).fixedSize(horizontal: false, vertical: true)
                }
                HStack(alignment: .top, spacing: 12) {
                    PlateView(url: source.image_url.flatMap { URL(string: $0) }, label: source.publisher)
                        .frame(width: 72, height: 72)
                    VStack(alignment: .leading, spacing: 6) {
                        Kicker(text: source.publisher + (source.author != nil ? " · on X" : ""), size: 10)
                        Text(source.extraction_status == "failed" ? source.url : source.source_title.isEmpty ? source.domain : source.source_title)
                            .font(Theme.headline(18)).fixedSize(horizontal: false, vertical: true)
                        HStack {
                            Text(clock(source.created_at)).foregroundColor(Theme.ink3)
                            Spacer()
                            if let url = URL(string: source.url) {
                                Link(destination: url) { Text("Open\(source.author != nil ? " on X" : "") ↗").fontWeight(.semibold).underline() }
                                    .foregroundColor(Theme.ink)
                            }
                        }
                        .font(Theme.mono(12))
                        if let note {
                            HStack(alignment: .firstTextBaseline, spacing: 4) {
                                if source.extraction_status == "paywalled" { Image(systemName: "lock.fill").font(.system(size: 10)) }
                                Text(note)
                            }
                            .font(Theme.mono(11)).foregroundColor(Theme.ink2)
                            .padding(.top, 6)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .overlay(alignment: .top) { Hairline() }
                        }
                    }
                }
                .padding(12)
                .clipping()
            }
        }
        .padding(.vertical, 12)
    }

    private var note: String? {
        switch source.extraction_status {
        case "paywalled": "Paywalled — headline only. The summary does not use this source."
        case "failed": "Couldn't read this page"
        case "deleted": "Post deleted or unavailable"
        default: nil
        }
    }
}

struct MessageEntryView: View {
    let message: Message
    let store: StoryStore
    let userId: UUID
    let onReply: (Message) -> Void
    @State private var picking = false

    static let emojis = ["👍", "❤️", "😂", "😮", "😢", "🔥", "👏", "🚀", "👀", "🎉"]

    /// Only the emojis someone has used, in palette order.
    private var used: [String] {
        let present = (message.reactions ?? []).map(\.emoji)
        let unknown = present.filter { !Self.emojis.contains($0) }.reduce(into: [String]()) { if !$0.contains($1) { $0.append($1) } }
        return Self.emojis.filter(present.contains) + unknown
    }

    private func isMine(_ emoji: String) -> Bool {
        message.reactions?.contains { $0.emoji == emoji && $0.user_id == userId } ?? false
    }

    private func toggle(_ emoji: String) {
        Task { await store.toggleReaction(message: message, emoji: emoji) }
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            if let author = store.profiles[message.author_id] {
                MemberAvatar(profile: author, size: 36)
            } else {
                Circle().fill(Theme.tint).frame(width: 36, height: 36)
            }
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(message.author_id == userId ? "You" : store.profiles[message.author_id]?.display_name ?? "Member")
                        .font(.system(size: 15, weight: .semibold))
                    Text(clock(message.created_at)).font(Theme.mono(12)).foregroundColor(Theme.ink3)
                }
                if let parentId = message.reply_to,
                   let parent = store.messages.first(where: { $0.id == parentId }) {
                    Text("\(Text(store.profiles[parent.author_id]?.display_name ?? "…").font(.system(size: 13, weight: .semibold)).foregroundColor(Theme.ink)) \(parent.body ?? "Attachment")")
                        .font(Theme.italic(15)).foregroundColor(Theme.ink2)
                        .lineLimit(1)
                        .padding(.leading, 10)
                        .overlay(alignment: .leading) { Rectangle().fill(Theme.ink).frame(width: 2) }
                }
                if let body = message.body, !body.isEmpty {
                    Text(body).font(Theme.serif(18)).fixedSize(horizontal: false, vertical: true)
                }
                ForEach((message.message_media ?? []).sorted { $0.position < $1.position }) { media in
                    MessageMediaView(media: media)
                }
                HStack(spacing: 4) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 4) {
                            ForEach(used, id: \.self) { emoji in
                                let count = message.reactions?.filter { $0.emoji == emoji }.count ?? 0
                                let mine = isMine(emoji)
                                Button { toggle(emoji) } label: {
                                    HStack(spacing: 4) {
                                        Text(emoji).font(.system(size: 13))
                                        Text("\(count)").font(Theme.mono(12, bold: true))
                                    }
                                    .padding(.horizontal, 8).frame(minHeight: 30)
                                    .background(mine ? Theme.ink : Color.clear)
                                    .foregroundColor(mine ? Theme.paper : Theme.ink)
                                    .clipShape(Capsule())
                                    .overlay(Capsule().stroke(mine ? Theme.ink : Theme.rule, lineWidth: 1))
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("\(emoji), \(count) \(count == 1 ? "reaction" : "reactions")")
                                .accessibilityAddTraits(mine ? .isSelected : [])
                            }
                            Button { picking = true } label: {
                                Image(systemName: "face.smiling")
                                    .font(.system(size: 15))
                                    .overlay(alignment: .topTrailing) {
                                        Image(systemName: "plus")
                                            .font(.system(size: 7, weight: .heavy))
                                            .padding(1.5)
                                            .background(Theme.paper, in: Circle())
                                            .offset(x: 4, y: -3)
                                    }
                                    .foregroundColor(Theme.ink3)
                                    .padding(.horizontal, 8).frame(minHeight: 30)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Add reaction")
                            .popover(isPresented: $picking) {
                                ReactionPicker(isMine: isMine) { emoji in
                                    picking = false
                                    toggle(emoji)
                                }
                                .presentationCompactAdaptation(.popover)
                            }
                        }
                        .padding(.vertical, 1)
                    }
                    Button { onReply(message) } label: {
                        Image(systemName: "arrowshape.turn.up.left")
                            .font(.system(size: 15, weight: .regular))
                            .foregroundColor(Theme.ink2)
                            .frame(width: 40, height: 40)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Reply to message")
                    .disabled(store.pendingMessageId != nil)
                }
            }
        }
        .padding(.top, 14).padding(.bottom, 2)
        .contentShape(Rectangle())
        .onLongPressGesture { picking = true }
        .sensoryFeedback(.impact(weight: .medium), trigger: picking) { _, open in open }
    }
}

/// The full palette, set in a ruled box like the rest of the paper.
struct ReactionPicker: View {
    let isMine: (String) -> Bool
    let onPick: (String) -> Void

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.fixed(44), spacing: 4), count: 5), spacing: 4) {
            ForEach(MessageEntryView.emojis, id: \.self) { emoji in
                Button { onPick(emoji) } label: {
                    Text(emoji)
                        .font(.system(size: 26))
                        .frame(width: 44, height: 44)
                        .background(isMine(emoji) ? Theme.tint : Color.clear, in: RoundedRectangle(cornerRadius: 3))
                        .overlay(RoundedRectangle(cornerRadius: 3).stroke(isMine(emoji) ? Theme.ink : .clear, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(isMine(emoji) ? "Remove \(emoji)" : "React \(emoji)")
            }
        }
        .padding(12)
        .background(Theme.paper)
    }
}

struct SourceClipView: View {
    let source: Source

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            PlateView(url: source.image_url.flatMap { URL(string: $0) }, label: source.publisher)
                .frame(width: 64, height: 64)
            VStack(alignment: .leading, spacing: 4) {
                Text(source.publisher).font(Theme.mono(12, bold: true))
                Text(source.source_title.isEmpty ? source.domain : source.source_title)
                    .font(Theme.headline(15)).lineLimit(3)
                    .fixedSize(horizontal: false, vertical: true)
                if source.extraction_status == "paywalled" {
                    Label("Headline only", systemImage: "lock.fill").font(Theme.mono(10)).foregroundColor(Theme.ink2)
                }
            }
            Spacer(minLength: 0)
        }
        .frame(width: 236, alignment: .leading)
        .padding(10)
        .clipping()
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
            .scrollContentBackground(.hidden).background(Theme.paper)
            .navigationTitle("Report a correction")
            .navigationBarTitleDisplayMode(.inline)
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
            List {
                if history.isEmpty { Text("No earlier versions yet.").foregroundColor(Theme.ink2) }
                ForEach(history) { version in
                    VStack(alignment: .leading, spacing: 6) {
                        MetaRow(text: "\(dayLabel(version.created_at)) · \(timeAgo(version.created_at))")
                        Text(version.title).font(Theme.headline(20))
                        Text(version.summary).font(Theme.serif(16)).foregroundColor(Theme.ink2)
                    }
                    .listRowBackground(Theme.paper)
                }
            }
            .scrollContentBackground(.hidden).background(Theme.paper)
            .navigationTitle("What changed")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }
}
