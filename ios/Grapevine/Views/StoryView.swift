import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import AVKit

struct StoryView: View {
    @Environment(AppState.self) private var app
    @Environment(\.scenePhase) private var scenePhase
    @State private var store: StoryStore
    init(storyId: UUID, initialStore: StoryStore = StoryStore()) {
        self.storyId = storyId
        _store = State(initialValue: initialStore)
    }
    let storyId: UUID

    @State private var catchUpExpanded = true
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
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var readableSources: Int {
        store.sources.filter { $0.extraction_status == "ok" }.count
    }

    /// Sources and messages in time order, under their day.
    private var dayGroups: [(Date, [TimelineEntry])] {
        let timeline = (store.sources.map { TimelineEntry.source($0) } + store.messages.map { TimelineEntry.message($0) })
            .sorted { $0.createdAt < $1.createdAt }
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
                if let story = store.story {
                    SwiftUI.Group {
                        article(story)
                        clippings.padding(.top, 8)
                        conversation.padding(.top, 36)
                        if let notice = store.notice {
                            MetaRow(text: notice).padding(.top, 12)
                        }
                    }
                    .transition(.opacity)
                } else if let error = store.error, !store.isLoading {
                    SwiftUI.Group {
                        ErrorNote(message: error, screen: "Story", font: Theme.serif(17), color: Theme.ink).padding(.vertical)
                        Button("Try again") { Task { await store.load(storyId: storyId) } }
                            .font(.system(size: 15, weight: .semibold)).underline()
                            .buttonStyle(PressStyle())
                    }
                    .transition(.opacity)
                } else {
                    // Also before the first load starts, so the page never shows blank.
                    StoryPlaceholder().transition(.opacity)
                }
            }
            .padding(.horizontal, 20).padding(.bottom, 24)
            // The story fades in over its placeholder; new messages slide up into the conversation.
            .animation(.easeOut(duration: reduceMotion ? 0.2 : 0.45), value: store.story == nil)
            .animation(reduceMotion ? .easeOut(duration: 0.2) : .snappy(duration: 0.4), value: store.messages.map(\.id))
        }
        .background(Theme.paper)
        .scrollDismissesKeyboard(.interactively)
        .toolbar(.hidden, for: .tabBar)
        .toolbarVisibility(.visible, for: .navigationBar)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text(store.story?.title ?? "Story").lineLimit(1).font(.system(size: 17, weight: .semibold)).foregroundColor(Theme.ink)
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
            #if DEBUG
            guard !DesignFixture.enabled else { return }
            #endif
            guard scenePhase == .active else { return }
            async let read: Void = store.markRead(storyId: storyId)
            await store.load(storyId: storyId)
            await read
            // shortcut: poll while this story is visible; use Realtime subscriptions for larger groups.
            while !Task.isCancelled {
                do { try await Task.sleep(for: .seconds(10)) }
                catch { return }
                guard !sending && store.pendingMessageId == nil else { continue }
                // What arrived while reading has been seen: don't mark it new next time. Leaving the
                // story marks it too, so a poll that found nothing new needn't write.
                if await store.load(storyId: storyId) { await store.markRead(storyId: storyId) }
            }
        }
        .onDisappear { Task { [store, storyId] in await store.markRead(storyId: storyId) } }
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
        Text("\(story.category ?? "Unfiled") · Updated \(timeAgo(story.last_activity_at))")
            .font(Theme.text(15, weight: .semibold)).foregroundColor(Theme.ink3)
        Text(story.title.isEmpty ? "Reading this story" : story.title)
            .font(Theme.headline(34)).tracking(-0.8)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 12).padding(.bottom, 16)
            .accessibilityAddTraits(.isHeader)
        VStack(alignment: .leading, spacing: 12) {
            Button {
                catchUpExpanded.toggle()
                let key = "catch-up.\(app.session?.userId.uuidString ?? "member").\(storyId)"
                if !catchUpExpanded { UserDefaults.standard.set(story.summary, forKey: key) }
                else { UserDefaults.standard.removeObject(forKey: key) }
            } label: {
                HStack { Text("The catch-up").font(.system(size: 15, weight: .semibold)); Spacer(); Image(systemName: catchUpExpanded ? "chevron.up" : "chevron.down").accessibilityHidden(true) }
                    .frame(minHeight: 32).contentShape(Rectangle())
            }.buttonStyle(.plain).accessibilityValue(catchUpExpanded ? "Expanded" : "Collapsed")
            Text(story.summary.isEmpty ? "The summary could not be written." : story.summary)
                .font(Theme.serif(17)).lineSpacing(4).lineLimit(catchUpExpanded ? nil : 2)
                .fixedSize(horizontal: false, vertical: true)
            if catchUpExpanded {
                HStack(spacing: 12) {
                    Text("\(readableSources) of \(store.sources.count) sources")
                    Spacer(minLength: 0)
                    Button("What changed") { showHistory = true }.underline()
                    Button("Report") { reportTarget = "summary"; showReport = true }.underline()
                }.font(Theme.text(13)).padding(.top, 4)
            }
        }
        .padding(20).foregroundStyle(Theme.onCatchUp)
        .background(Theme.catchUp, in: RoundedRectangle(cornerRadius: 28))
        .task(id: story.summary) {
            let key = "catch-up.\(app.session?.userId.uuidString ?? "member").\(storyId)"
            catchUpExpanded = UserDefaults.standard.string(forKey: key) != story.summary
        }
        Button("Report title or category") { reportTarget = "category"; showReport = true }
            .font(Theme.text(13)).foregroundStyle(Theme.ink3).padding(.vertical, 12)

    }

    @ViewBuilder private var clippings: some View {
        if !store.sources.isEmpty {
            FieldLabel(text: "Sources · \(store.sources.count)", size: 15)
                .padding(.top, 10)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(store.sources) { SourceClipView(source: $0, seenAt: store.lastRead) }
                }
                .padding(.horizontal, 20).padding(.vertical, 12)
            }
            .padding(.horizontal, -20)
        }
    }

    @ViewBuilder private var conversation: some View {
        let divider = UnreadDivider(messages: store.messages, lastRead: store.lastRead, userId: app.session?.userId)
        let dayGroups = self.dayGroups
        SectionHead(title: "The conversation").padding(.bottom, 4)
        if dayGroups.isEmpty {
            Text("Start the conversation.").font(Theme.serif(17)).foregroundColor(Theme.ink2).padding(.vertical, 20)
        }
        ForEach(dayGroups, id: \.0) { day, entries in
            HStack(spacing: 12) {
                Hairline()
                Text(dayLabel(day)).font(Theme.text(13, weight: .semibold)).foregroundColor(Theme.ink3).fixedSize()
                Hairline()
            }
            .padding(.top, 16).padding(.bottom, 2)
            ForEach(entries) { entry in
                if let divider, case .message(let message) = entry, message.id == divider.firstId {
                    NewSinceDivider(divider: divider)
                }
                TimelineEntryView(entry: entry, store: store,
                                  userId: app.session?.userId ?? UUID(),
                                  onReply: { replyTo = $0 })
                    .transition(reduceMotion ? .opacity : .asymmetric(insertion: .opacity.combined(with: .offset(y: 18)), removal: .opacity))
            }
        }
    }

    private var composer: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let error = store.error {
                ErrorNote(message: error, screen: "Story", font: .system(size: 13, weight: .medium))
                    .arrives()
            }
            if !attachments.isEmpty {
                HStack {
                    Text(plural(attachments.count, "attachment")).font(Theme.text(13))
                    Spacer()
                    Button("Remove") { selectedPhotos = []; attachments = [] }
                        .font(Theme.text(13, weight: .semibold)).underline().foregroundColor(Theme.ink)
                        .disabled(sending || store.pendingMessageId != nil)
                }
                .padding(.horizontal, 12).padding(.vertical, 8).background(Theme.raised2, in: RoundedRectangle(cornerRadius: 10))
                .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
            if let replyTo {
                HStack {
                    Text("Replying to \(store.profiles[replyTo.author_id]?.display_name ?? "…"): \(replyTo.body ?? "attachment")")
                        .font(Theme.text(13)).foregroundColor(Theme.ink2)
                        .lineLimit(1)
                    Spacer()
                    Button { self.replyTo = nil } label: { Image(systemName: "xmark").font(.system(size: 12, weight: .semibold)) }
                        .tint(Theme.ink2)
                        .disabled(sending || store.pendingMessageId != nil)
                }
                .padding(.horizontal, 12).padding(.vertical, 8).background(Theme.raised2, in: RoundedRectangle(cornerRadius: 10))
                .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
            HStack(spacing: 8) {
                // PhotosPicker's label closure isn't main-actor isolated, so it reads a copy.
                let loadingAttachments = loadingAttachments
                PhotosPicker(selection: $selectedPhotos, maxSelectionCount: 10, matching: .any(of: [.images, .videos])) {
                    SwiftUI.Group {
                        if loadingAttachments {
                            InkDots(size: 5)
                        } else {
                            Image(systemName: "plus").font(.system(size: 16, weight: .medium))
                        }
                    }
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(PressStyle(scale: 0.94))
                .foregroundColor(Theme.ink)
                .accessibilityLabel("Attach images or video")
                .disabled(sending || loadingAttachments || store.pendingMessageId != nil)
                TextField("Tell the others", text: $draft)
                    .focused($composing)
                    .font(Theme.serif(17))
                    .padding(.horizontal, 12).frame(height: 44)
                    .background(Color.clear)
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
                    Image(systemName: "arrow.up").frame(width: 44)
                }
                .buttonStyle(InkButtonStyle(enabled: canSend, busy: sending, height: 44))
                .fixedSize()
                .disabled(!canSend)
                .accessibilityLabel(sending ? "Sending…" : "Send message")
            }
            .padding(4).glassEffect()
        }
        .padding(.horizontal, 16).padding(.top, 10).padding(.bottom, 8)
        .animation(.snappy(duration: 0.25), value: replyTo?.id)
        .animation(.snappy(duration: 0.25), value: attachments.count)
        .animation(.snappy(duration: 0.25), value: store.error)
        .background(Theme.paper.opacity(0.94))
        .background(.ultraThinMaterial)

    }
}

/// "3 new since 14:05": a vine-green line across the conversation before the first message that's new to you.
struct NewSinceDivider: View {
    let divider: UnreadDivider

    var body: some View {
        HStack(spacing: 14) {
            Rectangle().fill(Theme.red.opacity(0.5)).frame(height: 1)
            Text("\(divider.count) new since \(clock(divider.since))")
                .font(Theme.text(15, weight: .semibold)).foregroundColor(Theme.red)
                .padding(.horizontal, 12).padding(.vertical, 5)
                .background(Theme.vineSoft, in: Capsule())
                .fixedSize()
            Rectangle().fill(Theme.red.opacity(0.5)).frame(height: 1)
        }
        .padding(.top, 26).padding(.bottom, 6)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(plural(divider.count, "new message")) since \(clock(divider.since))")
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
                    Text("filed a source · \(clock(source.created_at))").font(Theme.text(13)).foregroundColor(Theme.ink3)
                }
                if let comment = source.submitter_comment, !comment.isEmpty {
                    Text(comment).font(Theme.serif(18)).fixedSize(horizontal: false, vertical: true)
                }
                HStack(alignment: .top, spacing: 12) {
                    PlateView(url: source.image_url.flatMap { URL(string: $0) }, label: source.publisher, radius: 10)
                        .frame(width: 64, height: 64)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    VStack(alignment: .leading, spacing: 6) {
                        Text(source.publisher + (source.author != nil ? " on X" : ""))
                            .font(Theme.text(13, weight: .semibold)).foregroundColor(Theme.ink3)
                        Text(source.extraction_status == "failed" ? source.url : source.source_title.isEmpty ? source.domain : source.source_title)
                            .font(Theme.headline(18)).fixedSize(horizontal: false, vertical: true)
                        HStack {
                            Text(clock(source.created_at)).foregroundColor(Theme.ink3)
                            Spacer()
                            if let url = source.webURL {
                                Link(destination: url) { Text("Open\(source.author != nil ? " on X" : "") ↗").fontWeight(.semibold).underline() }
                                    .foregroundColor(Theme.ink)
                            }
                        }
                        .font(Theme.text(13))
                        if let note {
                            HStack(alignment: .firstTextBaseline, spacing: 4) {
                                if source.extraction_status == "paywalled" { Image(systemName: "lock.fill").font(.system(size: 10)) }
                                Text(note)
                            }
                            .font(Theme.text(13)).foregroundColor(Theme.ink2)
                            .padding(.top, 6)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .overlay(alignment: .top) { Hairline() }
                        }
                    }
                }
                .padding(12)
                .card(radius: 14)
            }
        }
        .padding(.vertical, 12)
    }

    private var note: String? {
        switch source.extraction_status {
        case "paywalled": "Paywalled: headline only. The summary does not use this source."
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
    @State private var replyTaps = 0

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
                    Text(clock(message.created_at)).font(Theme.text(13)).foregroundColor(Theme.ink3)
                }
                if let parentId = message.reply_to,
                   let parent = store.messages.first(where: { $0.id == parentId }) {
                    Text("\(Text(store.profiles[parent.author_id]?.display_name ?? "…").fontWeight(.semibold).foregroundColor(Theme.ink)) \(parent.body ?? "Attachment")")
                        .font(Theme.text(15)).foregroundColor(Theme.ink2)
                        .lineLimit(2)
                        .padding(.leading, 12).padding(.trailing, 10).padding(.vertical, 8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .overlay(alignment: .leading) {
                            Capsule().fill(memberColors[memberColorIndex(parent.author_id.uuidString)])
                                .frame(width: 3).padding(.vertical, 8).padding(.leading, 4)
                        }
                        .background(Theme.raised2, in: RoundedRectangle(cornerRadius: 10))
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
                                ReactionChip(emoji: emoji,
                                             count: message.reactions?.filter { $0.emoji == emoji }.count ?? 0,
                                             mine: isMine(emoji)) { toggle(emoji) }
                                    .transition(.scale(scale: 0.6).combined(with: .opacity))
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
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(PressStyle(scale: 0.9))
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
                        .animation(.snappy(duration: 0.3), value: used)
                    }
                    Button { replyTaps += 1; onReply(message) } label: {
                        Image(systemName: "arrowshape.turn.up.left")
                            .font(.system(size: 15, weight: .regular))
                            .foregroundColor(Theme.ink2)
                            .frame(width: 40, height: 40)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(PressStyle(scale: 0.9))
                    .sensoryFeedback(.selection, trigger: replyTaps)
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

/// One reaction and its count. A tap pops the chip and rolls the count over; the count also rolls
/// when friends' reactions arrive.
struct ReactionChip: View {
    let emoji: String
    let count: Int
    let mine: Bool
    let action: () -> Void
    @State private var taps = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Button { taps += 1; action() } label: {
            HStack(spacing: 4) {
                Text(emoji).font(.system(size: 13))
                Text("\(count)").font(Theme.mono(12, bold: true))
                    .contentTransition(.numericText(value: Double(count)))
            }
            .padding(.horizontal, 8).frame(minHeight: 30)
            .background(mine ? Theme.ink : Color.clear)
            .foregroundColor(mine ? Theme.paper : Theme.ink)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(mine ? Theme.ink : Theme.rule, lineWidth: 1))
            .contentShape(Capsule())
        }
        .buttonStyle(PressStyle(scale: 0.92))
        .keyframeAnimator(initialValue: 1.0, trigger: taps) { content, scale in
            content.scaleEffect(scale)
        } keyframes: { _ in
            SpringKeyframe(reduceMotion ? 1 : 1.18, duration: 0.12, spring: .snappy)
            SpringKeyframe(1, duration: 0.35, spring: .bouncy)
        }
        .animation(.snappy(duration: 0.25), value: count)
        .animation(.easeOut(duration: 0.15), value: mine)
        .sensoryFeedback(.impact(weight: .light), trigger: taps)
        .accessibilityLabel("\(emoji), \(count) \(count == 1 ? "reaction" : "reactions")")
        .accessibilityAddTraits(mine ? .isSelected : [])
    }
}

/// The full palette of reactions, in a popover.
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
                        .background(isMine(emoji) ? Theme.tint : Color.clear, in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(isMine(emoji) ? Theme.ink : .clear, lineWidth: 1))
                }
                .buttonStyle(PressStyle(scale: 0.85))
                .accessibilityLabel(isMine(emoji) ? "Remove \(emoji)" : "React \(emoji)")
            }
        }
        .padding(12)
        .background(Theme.paper)
    }
}

struct SourceClipView: View {
    let source: Source
    var seenAt: Date? = nil

    @ViewBuilder var body: some View {
        if let url = source.webURL { Link(destination: url) { content }.buttonStyle(.plain) }
        else { content }
    }
    private var content: some View {
        HStack(alignment: .top, spacing: 10) {
            SourceBunch(sources: [source], seenAt: seenAt, size: 32)
            VStack(alignment: .leading, spacing: 4) {
                Text(source.publisher).font(Theme.text(13, weight: .semibold)).foregroundColor(Theme.ink3)
                Text(source.source_title.isEmpty ? source.domain : source.source_title)
                    .font(Theme.headline(15)).lineLimit(3)
                    .fixedSize(horizontal: false, vertical: true)
                if source.extraction_status == "paywalled" {
                    Label("Headline only", systemImage: "lock.fill").font(Theme.text(11, weight: .medium)).foregroundColor(Theme.ink2)
                }
            }
            Spacer(minLength: 0)
        }
        .frame(width: 236, alignment: .leading)
        .padding(10)
        .card(radius: 14)
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
                Button {
                    Task {
                        busy = true
                        let success = await store.report(target: target, note: note)
                        busy = false
                        if success { dismiss() }
                    }
                } label: {
                    BusyLabel(text: "Report", busy: busy)
                }
                .disabled(busy)
                .busyAccessibility(busy, "Reporting…")
                if let error = store.error { ErrorNote(message: error, screen: "Story correction", color: Theme.ink).arrives() }
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
