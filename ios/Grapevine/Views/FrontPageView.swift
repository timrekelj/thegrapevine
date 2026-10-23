import SwiftUI

/// The front page: a broadsheet. Nameplate and dateline, the lead above the fold, ruled stories below.
struct FrontPageView: View {
    @Environment(AppState.self) private var app
    @Environment(PaperStore.self) private var paper
    @State private var share = false
    @State private var path = NavigationPath()
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var profiles: [Profile] { app.members.map(\.profiles) }
    /// The first load, before there's an edition to show: the page is set as a proof.
    /// Also before the first load starts, so the empty edition never flashes.
    private var proofing: Bool { paper.error == nil && paper.stories.isEmpty && (paper.isLoading || !loadedOnce) }
    @State private var loadedOnce = false

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    masthead
                    if let error = paper.error {
                        VStack(alignment: .leading, spacing: 12) {
                            ErrorNote(message: error, screen: "Front page", font: Theme.serif(17), color: Theme.ink)
                            Button("Try again") { Task { await paper.load(app: app) } }
                                .font(.system(size: 15, weight: .semibold)).underline()
                                .buttonStyle(PressStyle())
                        }
                        .padding(.vertical, 32)
                        .transition(.opacity)
                    } else if proofing {
                        FrontPageProof().transition(.opacity)
                    } else if let lead = paper.stories.first {
                        SwiftUI.Group {
                            edition(lead: lead)
                            if let before = paper.earlierBefore {
                                earlierEditions(before: before)
                            }
                        }
                        .transition(.opacity)
                    } else {
                        empty.transition(.opacity)
                    }
                    colophon
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
                // The edition inks in over its proof.
                .animation(.easeOut(duration: reduceMotion ? 0.2 : 0.45), value: proofing)
            }
            .background(Theme.paper)
            .navigationDestination(for: UUID.self) { id in StoryView(storyId: id) }
            .navigationDestination(for: ArchiveRoute.self) { route in ArchiveView(before: route.before) }
            .toolbarVisibility(.hidden, for: .navigationBar)
            .sheet(isPresented: $share, onDismiss: { Task { await paper.load(app: app) } }) {
                ShareView(group: app.group, onReport: { Feedback.present(FeedbackRequest(screen: "Share a link", error: $0)) })
            }
            .refreshable { await paper.load(app: app) }
            .task(id: app.group?.id) { await paper.load(app: app); loadedOnce = true }
            .onChange(of: app.pendingStoryId, initial: true) {
                if let id = app.pendingStoryId { path = NavigationPath([id]); app.pendingStoryId = nil }
            }
        }
    }

    // MARK: - Nameplate

    private var edition: (volume: String, number: Int)? {
        guard let founded = app.group?.created_at else { return nil }
        let days = Calendar.current.dateComponents([.day], from: Calendar.current.startOfDay(for: founded), to: Calendar.current.startOfDay(for: .now)).day ?? 0
        let number = max(1, days + 1)
        return (roman((number - 1) / 365 + 1), number)
    }

    private var fresh: Int {
        paper.stories.filter { story in
            story.sources.contains { isNew($0.created_at) } || story.messages.contains { isNew($0.created_at) }
        }.count
    }

    private func isNew(_ date: Date) -> Bool { paper.seenAt.map { $0 < date } ?? true }

    private var masthead: some View {
        VStack(spacing: 0) {
            Text(app.group?.name ?? "The paper")
                .font(Theme.nameplate(46))
                .multilineTextAlignment(.center)
                .lineLimit(2).minimumScaleFactor(0.5)
                .frame(maxWidth: .infinity)
                .padding(.top, 12).padding(.bottom, 12)
                .accessibilityAddTraits(.isHeader)
            DoubleRule()
            HStack {
                if let edition {
                    Kicker(text: "Vol. \(edition.volume) · No. \(edition.number)", color: Theme.ink2, size: 10)
                }
                Spacer()
                HStack(spacing: 0) {
                    Kicker(text: plural(paper.stories.count, "story", "stories"), color: Theme.ink2, size: 10)
                    if fresh > 0 {
                        Kicker(text: " · \(fresh) new", color: Theme.red, size: 10)
                    }
                }
            }
            .padding(.vertical, 8)
            Hairline()
            HStack {
                Text(Date.now.formatted(.dateTime.weekday(.wide).day().month(.wide).year()))
                    .font(Theme.italic(16, medium: true))
                Spacer()
                AvatarStack(profiles: profiles, size: 24)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("At the table: \(profiles.map(\.display_name).joined(separator: ", "))")
            }
            .padding(.vertical, 8)
            Rectangle().fill(Theme.ink).frame(height: 1)
            sections
        }
    }

    private var sections: some View {
        VStack(spacing: 10) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 20) {
                    Kicker(text: "Front page", size: 10)
                    ForEach(categories, id: \.self) { Kicker(text: $0, color: Theme.ink2, size: 10) }
                }
                .padding(.vertical, 2)
            }
            Button { share = true } label: {
                Label("Share a link", systemImage: "plus")
            }
            .buttonStyle(InkButtonStyle(height: 44))
        }
        .padding(.vertical, 10)
        .overlay(alignment: .bottom) { Hairline() }
    }

    private var categories: [String] {
        var seen = Set<String>()
        return paper.stories.compactMap(\.category).filter { seen.insert($0).inserted }
    }

    // MARK: - The edition

    @ViewBuilder private func edition(lead: StoryRow) -> some View {
        NavigationLink(value: lead.id) {
            LeadStoryView(story: lead, members: app.members, seenAt: paper.seenAt)
        }
        .buttonStyle(PressStyle())
        .padding(.top, 22)

        let rest = Array(paper.stories.dropFirst())
        ForEach(Array(rest.prefix(3).enumerated()), id: \.element.id) { index, story in
            Hairline().padding(.vertical, 20)
            NavigationLink(value: story.id) {
                SideStoryView(story: story, members: app.members, seenAt: paper.seenAt, featured: index == 0)
            }
            .buttonStyle(PressStyle())
        }

        if rest.count > 3 {
            SectionHead(title: "Also in this edition").padding(.top, 40).padding(.bottom, 8)
            ForEach(Array(rest.dropFirst(3).enumerated()), id: \.element.id) { index, story in
                if index > 0 { Hairline() }
                NavigationLink(value: story.id) {
                    StoryRowView(story: story, members: app.members, seenAt: paper.seenAt)
                }
                .buttonStyle(PressStyle())
            }
        }

        let letters = paper.stories
            .flatMap { story in story.messages.map { (message: $0, story: story) } }
            .sorted { $0.message.created_at > $1.message.created_at }
            .prefix(4)
        if !letters.isEmpty {
            SectionHead(title: "Letters from the table").padding(.top, 40).padding(.bottom, 16)
            ForEach(Array(letters.enumerated()), id: \.element.message.id) { index, letter in
                if index > 0 { Hairline().padding(.vertical, 16) }
                NavigationLink(value: letter.story.id) {
                    LetterView(message: letter.message, story: letter.story, author: app.members.first { $0.user_id == letter.message.author_id }?.profiles)
                }
                .buttonStyle(PressStyle())
            }
        }
    }

    /// The way into the archive, for stories older than this week's edition.
    private func earlierEditions(before: Date) -> some View {
        NavigationLink(value: ArchiveRoute(before: before)) {
            Kicker(text: "Earlier editions →", size: 12)
                .frame(maxWidth: .infinity, minHeight: 44)
                .overlay(alignment: .top) { DoubleRule() }
                .overlay(alignment: .bottom) { Rectangle().fill(Theme.ink).frame(height: 1) }
                .contentShape(Rectangle())
        }
        .buttonStyle(PressStyle())
        .padding(.top, 48)
    }

    private var empty: some View {
        VStack(spacing: 16) {
            Kicker(text: "Stop the presses")
            Text("This edition is waiting for its first story.")
                .font(Theme.headline(34)).multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Text("Share a link and it becomes the lead. Links about the same event are filed together, and every story gets its own conversation.")
                .font(Theme.serif(17)).foregroundColor(Theme.ink2).multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 56)
    }

    private var colophon: some View {
        VStack(alignment: .leading, spacing: 6) {
            Rectangle().fill(Theme.ink).frame(height: 1).padding(.bottom, 8)
            Text("The Grapevine").font(Theme.nameplate(22))
            Text("Printed at \(clock(.now)) for \(plural(app.members.count, "reader")) · Private edition")
                .font(Theme.mono(11)).foregroundColor(Theme.ink3)
        }
        .padding(.top, 48)
    }
}

/// Story facts shared by every slot on the page, worked out once per story rather than on every read
/// (each slot reads several of them, some twice).
struct StoryFacts {
    let story: StoryRow
    let newSources: Int
    let newMessages: Int
    /// Newest first, without repeats.
    let publishers: [String]
    let submitters: [Profile]
    let imageURL: URL?

    init(story: StoryRow, members: [Member], seenAt: Date?) {
        self.story = story
        func isNew(_ date: Date) -> Bool { seenAt.map { $0 < date } ?? true }
        newSources = story.sources.count(where: { isNew($0.created_at) })
        newMessages = story.messages.count(where: { isNew($0.created_at) })
        let newest = story.sources.sorted { $0.created_at > $1.created_at }
        var seen = Set<String>()
        publishers = newest.map(\.displayPublisher).filter { seen.insert($0).inserted }
        let submitterIds = Set(story.sources.compactMap(\.submitted_by))
        submitters = members.filter { submitterIds.contains($0.user_id) }.map(\.profiles)
        imageURL = newest.first { $0.image_url != nil }?.image_url.flatMap { URL(string: $0) }
    }

    var title: String { story.title.isEmpty ? "A link from the table" : story.title }
    var category: String { story.category ?? "News" }
    var label: String { publishers.first ?? "The paper" }
}

/// Counts in mono, with red marks for what's new.
struct StoryMeta: View {
    let facts: StoryFacts
    var publishers = false

    var body: some View {
        let story = facts.story
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top, spacing: 12) {
                Text("\(Text("\(story.sources.count)").foregroundColor(Theme.ink).font(Theme.mono(12, bold: true))) \(story.sources.count == 1 ? "source" : "sources")\(publishers && !facts.publishers.isEmpty ? ": " + facts.publishers.joined(separator: ", ") : "")  \(Text("\(story.messages.count)").foregroundColor(Theme.ink).font(Theme.mono(12, bold: true))) \(story.messages.count == 1 ? "message" : "messages")")
                    .font(Theme.mono(12)).foregroundColor(Theme.ink2)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                AvatarStack(profiles: facts.submitters, size: 22, limit: 3)
            }
            if facts.newSources > 0 || facts.newMessages > 0 {
                HStack(spacing: 12) {
                    if facts.newSources > 0 { NewMark(text: plural(facts.newSources, "new source")) }
                    if facts.newMessages > 0 { NewMark(text: plural(facts.newMessages, "new message")) }
                }
            }
        }
    }
}

struct LeadStoryView: View {
    let story: StoryRow
    let members: [Member]
    let seenAt: Date?

    var body: some View {
        let facts = StoryFacts(story: story, members: members, seenAt: seenAt)
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Kicker(text: facts.category)
                if facts.newSources > 0 { Stamp(text: "+" + plural(facts.newSources, "source")) }
            }
            Text(facts.title)
                .font(Theme.headline(38)).lineSpacing(-4)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 10).padding(.bottom, 16)
            PlateView(url: facts.imageURL, label: facts.label)
                .aspectRatio(16 / 9, contentMode: .fit)
            if let publisher = facts.publishers.first {
                Text("Pictured via \(publisher)\(facts.submitters.first.map { ", shared by \($0.display_name)" } ?? "")")
                    .font(Theme.mono(11)).foregroundColor(Theme.ink3)
                    .padding(.top, 8).padding(.bottom, 8)
            }
            Hairline()
            DropCapText(text: story.summary.isEmpty ? "The summary is still being prepared. Open the story to read the shared link." : story.summary, size: 18)
                .padding(.top, 14).padding(.bottom, 14)
            StoryMeta(facts: facts, publishers: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}

/// The three stories beside the lead; the first carries a picture and a summary.
struct SideStoryView: View {
    let story: StoryRow
    let members: [Member]
    let seenAt: Date?
    let featured: Bool

    var body: some View {
        let facts = StoryFacts(story: story, members: members, seenAt: seenAt)
        VStack(alignment: .leading, spacing: 8) {
            if featured {
                PlateView(url: facts.imageURL, label: facts.label)
                    .aspectRatio(3 / 2, contentMode: .fit)
                    .padding(.bottom, 4)
            }
            Kicker(text: facts.category)
            Text(facts.title)
                .font(Theme.headline(featured ? 28 : 23))
                .fixedSize(horizontal: false, vertical: true)
            if featured, !story.summary.isEmpty {
                Text(story.summary).font(Theme.serif(16)).foregroundColor(Theme.ink2).lineSpacing(2).lineLimit(3)
            }
            StoryMeta(facts: facts)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}

/// "Also in this edition": text beside a small square plate.
struct StoryRowView: View {
    let story: StoryRow
    let members: [Member]
    let seenAt: Date?

    var body: some View {
        let facts = StoryFacts(story: story, members: members, seenAt: seenAt)
        HStack(alignment: .top, spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                Kicker(text: facts.category)
                Text(facts.title).font(Theme.headline(21)).fixedSize(horizontal: false, vertical: true)
                StoryMeta(facts: facts)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            PlateView(url: facts.imageURL, label: facts.label)
                .frame(width: 96, height: 96)
        }
        .padding(.vertical, 18)
        .contentShape(Rectangle())
    }
}

struct LetterView: View {
    let message: Message
    let story: StoryRow
    let author: Profile?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("“").font(Theme.headline(54)).foregroundColor(Theme.ink3).frame(height: 22, alignment: .top)
            Text(message.body ?? "Shared something with the table.")
                .font(Theme.italic(21)).lineSpacing(2)
                .fixedSize(horizontal: false, vertical: true)
            Text("\(Text(author?.display_name ?? "A member").font(Theme.mono(12, bold: true)).foregroundColor(Theme.ink)) on \(story.title) · \(clock(message.created_at))")
                .font(Theme.mono(12)).foregroundColor(Theme.ink2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}

/// Body copy that opens with a raised initial, as in a newspaper column.
struct DropCapText: View {
    let text: String
    var size: CGFloat = 18

    var body: some View {
        let trimmed = text.trimmed
        if let first = trimmed.first {
            Text("\(Text(String(first)).font(Theme.headline(size * 2.6)))\(Text(String(trimmed.dropFirst())).font(Theme.serif(size)))")
                .lineSpacing(size * 0.32)
                .foregroundColor(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

private func roman(_ number: Int) -> String {
    var left = number, out = ""
    for (value, glyph) in [(10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")] {
        while left >= value { out += glyph; left -= value }
    }
    return out
}
