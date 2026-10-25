import SwiftUI

struct FrontPageView: View {
    @Environment(AppState.self) private var app
    @Environment(PaperStore.self) private var paper
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var share = false
    @State private var people = false
    @State private var account = false
    @State private var loadedOnce = false
    @State private var path = NavigationPath()

    private var fresh: Int {
        paper.stories.reduce(0) { count, story in
            let facts = StoryFacts(story: story, members: app.members, seenAt: paper.seenAt)
            return count + facts.newSources + facts.newMessages
        }
    }

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .center) {
                        VStack(alignment: .leading, spacing: 4) {
                            if let before = paper.earlierBefore {
                                Button { path.append(ArchiveRoute(before: before)) } label: {
                                    HStack { Text(Date.now.formatted(.dateTime.weekday(.wide).day().month(.wide))); Image(systemName: "chevron.right").font(.caption) }
                                }.buttonStyle(.plain)
                            } else {
                                Text(Date.now.formatted(.dateTime.weekday(.wide).day().month(.wide)))
                            }
                            Text(fresh > 0 ? "\(fresh) new contributions" : "You’re all caught up")
                                .foregroundStyle(Theme.red).contentTransition(.numericText())
                        }
                        .font(Theme.mono(15, bold: true))
                        Spacer(minLength: 8)
                        Button { people = true } label: { AvatarStack(profiles: app.members.map(\.profiles), size: 26) }
                            .frame(minWidth: 44, minHeight: 44).accessibilityLabel("People and invitations")
                    }
                    .padding(.vertical, 20)
                    if let error = paper.error {
                        ErrorNote(message: error, screen: "Front page", font: Theme.serif(17), color: Theme.ink).padding(.vertical)
                        Button("Try again") { Task { await paper.load(app: app) } }.padding(.bottom, 20)
                    } else if paper.stories.isEmpty && (!loadedOnce || paper.isLoading) {
                        FrontPagePlaceholder()
                    } else if let lead = paper.stories.first {
                        NavigationLink(value: lead.id) { LeadStoryView(story: lead, members: app.members, seenAt: paper.seenAt) }
                            .buttonStyle(PressStyle()).padding(.bottom, 8)
                        ForEach(paper.stories.dropFirst()) { story in
                            NavigationLink(value: story.id) { StoryRowView(story: story, members: app.members, seenAt: paper.seenAt) }
                                .buttonStyle(PressStyle())
                            Hairline()
                        }
                        if let before = paper.earlierBefore {
                            NavigationLink(value: ArchiveRoute(before: before)) {
                                HStack { Text("Earlier editions"); Spacer(); Image(systemName: "arrow.right") }
                                    .font(Theme.text(15, weight: .semibold)).padding(.vertical, 24)
                            }.buttonStyle(.plain)
                        }
                    } else {
                        VStack(spacing: 24) {
                            SourceBunch(sources: [], size: 80)
                            Text("Share the first link to start the paper.").font(Theme.headline(34)).multilineTextAlignment(.center)
                            Button("Share a link") { share = true }.buttonStyle(InkButtonStyle())
                        }.padding(.vertical, 64)
                    }
                }
                .padding(.horizontal, 20).padding(.bottom, 100)
                .animation(reduceMotion ? nil : .smooth, value: paper.stories.map(\.id))
            }
            .background(Theme.paper)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(app.group?.name ?? "The Grapevine").font(Theme.headline(20)).lineLimit(1)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { account = true } label: {
                        if let profile = app.profile { MemberAvatar(profile: profile, size: 30) }
                        else { Image(systemName: "person.crop.circle") }
                    }.accessibilityLabel("Your account")
                }
            }
            .overlay(alignment: .bottomTrailing) {
                if !paper.stories.isEmpty {
                    Button { share = true } label: { Label("Share a link", systemImage: "link").foregroundStyle(Theme.paper).font(.system(size: 15, weight: .semibold)).padding(.horizontal, 22).frame(minHeight: 52) }
                        .buttonStyle(.glassProminent).tint(Theme.ink).padding(20)
                }
            }
            .navigationDestination(for: UUID.self) { id in
                #if DEBUG
                if DesignFixture.enabled { StoryView(storyId: id, initialStore: DesignFixture.storyStore(id)) }
                else { StoryView(storyId: id) }
                #else
                StoryView(storyId: id)
                #endif
            }
            .navigationDestination(for: ArchiveRoute.self) { ArchiveView(before: $0.before) }
            .sheet(isPresented: $share, onDismiss: { Task { await paper.load(app: app) } }) { ShareView(group: app.group, onOpenStory: { path.append($0) }, onReport: { Feedback.present(FeedbackRequest(screen: "Share a link", error: $0)) }) }
            .sheet(isPresented: $people) { FriendsView().presentationDetents([.large]).presentationDragIndicator(.visible) }
            .sheet(isPresented: $account) { ProfileView().presentationDragIndicator(.visible) }
            .refreshable { await paper.load(app: app) }
            .task(id: app.group?.id) { await paper.load(app: app); loadedOnce = true }
            .onChange(of: app.pendingStoryId, initial: true) {
                if let id = app.pendingStoryId { path = NavigationPath([id]); app.pendingStoryId = nil }
            }
        }
    }
}

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


struct StoryMeta: View {
    let facts: StoryFacts
    var publishers = false
    var body: some View {
        HStack(spacing: 14) {
            SourceBunch(sources: facts.story.sources, size: 44)
            VStack(alignment: .leading, spacing: 2) {
                Text(plural(facts.story.sources.count, "source") + (facts.newSources > 0 ? ", \(facts.newSources) new" : ""))
                Text(plural(facts.story.messages.count, "message"))
            }.font(Theme.serif(15)).foregroundStyle(Theme.ink2)
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
            PlateView(url: facts.imageURL, label: facts.label, radius: 0)
                .aspectRatio(1.6, contentMode: .fit)
                .overlay(alignment: .topLeading) {
                    if facts.newSources + facts.newMessages > 0 { NewBadge(text: "\(facts.newSources + facts.newMessages) new").padding(16) }
                }
            VStack(alignment: .leading, spacing: 14) {
                Text("\(facts.category) · \(clock(story.last_activity_at))").font(Theme.mono(13)).foregroundStyle(Theme.ink3)
                Text(facts.title).font(Theme.headline(34)).tracking(-0.8).fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 14) {
                    SourceBunch(sources: story.sources, seenAt: seenAt)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(plural(story.sources.count, "source") + (facts.newSources > 0 ? ", \(facts.newSources) new" : ""))
                        Text(plural(story.messages.count, "message"))
                    }.font(Theme.serif(15)).foregroundStyle(Theme.ink2)
                }
                if let latest = story.messages.max(by: { $0.created_at < $1.created_at }) {
                    Hairline()
                    HStack(spacing: 10) {
                        if let author = members.first(where: { $0.user_id == latest.author_id })?.profiles { MemberAvatar(profile: author, size: 28) }
                        let name = members.first(where: { $0.user_id == latest.author_id })?.profiles.display_name ?? "A member"
                        Text("\(Text(name).fontWeight(.semibold))  \(latest.body ?? "Shared an attachment.")").font(Theme.serif(15)).lineLimit(1)
                        Spacer(minLength: 0)
                        Text(clock(latest.created_at)).font(Theme.mono(13)).foregroundStyle(Theme.ink3)
                    }
                }
            }.padding(20)
        }
        .background(Theme.raised, in: RoundedRectangle(cornerRadius: 28))
        .clipShape(RoundedRectangle(cornerRadius: 28))
        .shadow(color: Theme.shadow, radius: 15, y: 10)
        .contentShape(RoundedRectangle(cornerRadius: 28))
    }
}

struct StoryRowView: View {
    let story: StoryRow
    let members: [Member]
    let seenAt: Date?
    var body: some View {
        let facts = StoryFacts(story: story, members: members, seenAt: seenAt)
        HStack(spacing: 12) {
            SourceBunch(sources: story.sources, seenAt: seenAt, size: 32)
            VStack(alignment: .leading, spacing: 6) {
                Text(facts.title).font(Theme.headline(19)).fixedSize(horizontal: false, vertical: true)
                if facts.newSources + facts.newMessages > 0 { Text(plural(facts.newSources + facts.newMessages, "new contribution")).font(Theme.mono(13, bold: true)).foregroundStyle(Theme.red) }
                Text("\(facts.category) · \(clock(story.last_activity_at))").font(Theme.mono(13)).foregroundStyle(Theme.ink3)
                Text("\(plural(story.sources.count, "source")) · \(plural(story.messages.count, "message"))").font(Theme.mono(13)).foregroundStyle(Theme.ink3)
            }.frame(maxWidth: .infinity, alignment: .leading)
            PlateView(url: facts.imageURL, label: facts.label).frame(width: 64, height: 64).clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .padding(.vertical, 18).contentShape(Rectangle())
    }
}
