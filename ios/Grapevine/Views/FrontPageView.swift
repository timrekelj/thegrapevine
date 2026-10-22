import SwiftUI

struct FrontPageView: View {
    @Environment(AppState.self) private var app
    @Environment(PaperStore.self) private var paper
    @State private var share = false
    @State private var path: [UUID] = []

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                LazyVStack(spacing: 0) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("YOUR PRIVATE PAPER")
                            .font(.system(size: 10, weight: .bold)).tracking(2)
                            .foregroundColor(Theme.ink2)
                        HStack(alignment: .center, spacing: 16) {
                            Text(app.group?.name ?? "The paper")
                                .font(Theme.display(32))
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Button { share = true } label: {
                                Image(systemName: "plus")
                                    .font(.system(size: 20, weight: .medium))
                                    .frame(width: 48, height: 48)
                                    .background(Theme.ink, in: Circle())
                                    .foregroundColor(Theme.paper)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Share a link")
                        }
                        Text(Date.now, format: .dateTime.weekday(.wide).day().month(.wide))
                            .font(.system(size: 12)).foregroundColor(Theme.ink2)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(24)

                    if let error = paper.error {
                        Text(error).font(.callout).padding()
                        Button("Try again") { Task { await paper.load(app: app) } }
                    } else if paper.isLoading && paper.stories.isEmpty {
                        ProgressView().tint(Theme.ink).frame(maxWidth: .infinity).padding(40)
                    } else if let lead = paper.stories.first {
                        NavigationLink(value: lead.id) {
                            LeadStoryView(story: lead, members: app.members, seenAt: paper.seenAt)
                        }
                        .buttonStyle(.plain)
                        if paper.stories.count > 1 {
                        Text("MORE FROM YOUR FRIENDS")
                            .font(.system(size: 10, weight: .bold)).tracking(1.5)
                            .foregroundColor(Theme.ink2)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 24).padding(.top, 28).padding(.bottom, 4)
                        }
                        ForEach(paper.stories.dropFirst()) { story in
                            NavigationLink(value: story.id) {
                                StoryRowView(story: story, members: app.members, seenAt: paper.seenAt)
                            }
                            .buttonStyle(.plain)
                        }
                    } else {
                        VStack(spacing: 12) {
                            Text("Share the first link to start the paper.")
                                .font(Theme.display(21)).fixedSize(horizontal: false, vertical: true)
                                .multilineTextAlignment(.center)
                            Button("Share a link") { share = true }
                                .buttonStyle(.plain)
                                .padding(.horizontal, 18).frame(height: 44)
                                .background(Theme.ink).foregroundColor(Theme.paper)
                                .clipShape(Capsule())
                        }
                        .frame(maxWidth: .infinity)
                        .padding(40)
                    }
                }
            }
            .background(Theme.paper)
            .navigationDestination(for: UUID.self) { id in StoryView(storyId: id) }
            .toolbarVisibility(.hidden, for: .navigationBar)
            .sheet(isPresented: $share, onDismiss: { Task { await paper.load(app: app) } }) { ShareView(group: app.group) }
            .refreshable { await paper.load(app: app) }
            .task(id: app.group?.id) { await paper.load(app: app) }
            .onChange(of: app.pendingStoryId, initial: true) {
                if let id = app.pendingStoryId { path = [id]; app.pendingStoryId = nil }
            }
        }
    }
}

struct LeadStoryView: View {
    let story: StoryRow
    let members: [Member]
    let seenAt: Date?

    private var newSources: Int {
        story.sources.filter { source in seenAt.map { $0 < source.created_at } ?? true }.count
    }
    private var newMessages: Int {
        story.messages.filter { message in seenAt.map { $0 < message.created_at } ?? true }.count
    }
    private var submitters: [Profile] {
        members.compactMap { member in
            story.sources.contains(where: { $0.submitted_by == member.user_id }) ? member.profiles : nil
        }
    }
    private var imageURL: URL? {
        story.sources.first(where: { $0.image_url != nil })?.image_url.flatMap { URL(string: $0) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            PlateView(url: imageURL, label: story.sources.first?.publisher ?? "The paper", height: 172)
            HStack {
                Text((story.category ?? "News").uppercased())
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(Theme.ink2)
                if newSources > 0 {
                    Stamp(text: "+\(newSources) source\(newSources == 1 ? "" : "s")")
                }
            }
            Text(story.title.isEmpty ? "A link from the table" : story.title)
                .font(Theme.display(28)).fixedSize(horizontal: false, vertical: true)
            if !story.summary.isEmpty {
                Text(story.summary)
                    .font(.system(size: 15)).lineSpacing(4).fixedSize(horizontal: false, vertical: true)
                    .foregroundColor(Theme.ink2)
            }
            HStack(spacing: 12) {
                MetaRow(text: "\(story.sources.count) source\(story.sources.count == 1 ? "" : "s") · \(story.messages.count) message\(story.messages.count == 1 ? "" : "s")")
                    .fixedSize(horizontal: false, vertical: true)
                Spacer()
                ForEach(submitters.prefix(2)) { Monogram(profile: $0, size: 24) }
            }
            if newMessages > 0 {
                HStack(spacing: 4) {
                    Circle().fill(Theme.pink).frame(width: 8, height: 8)
                    MetaRow(text: "\(newMessages) new")
                }
            }

        }
        .padding(16)
        .background(Theme.raised, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Theme.rule, lineWidth: 0.5))
        .padding(.horizontal, 20)
    }
}

struct StoryRowView: View {
    let story: StoryRow
    let members: [Member]
    let seenAt: Date?

    private var newCount: Int {
        story.sources.filter { source in seenAt.map { $0 < source.created_at } ?? true }.count
            + story.messages.filter { message in seenAt.map { $0 < message.created_at } ?? true }.count
    }
    private var imageURL: URL? {
        story.sources.first(where: { $0.image_url != nil })?.image_url.flatMap { URL(string: $0) }
    }

    var body: some View {
        HStack(alignment: .top, spacing: 16) {
            VStack(alignment: .leading, spacing: 6) {
                Text((story.category ?? "News").uppercased())
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(Theme.ink2)
                Text(story.title.isEmpty ? "A link from the table" : story.title)
                    .font(Theme.display(21)).fixedSize(horizontal: false, vertical: true)
                VStack(alignment: .leading, spacing: 6) {
                    MetaRow(text: "\(story.sources.count) source\(story.sources.count == 1 ? "" : "s") · \(story.messages.count) message\(story.messages.count == 1 ? "" : "s")")
                        .fixedSize(horizontal: false, vertical: true)
                    if newCount > 0 {
                        HStack(spacing: 4) {
                            Circle().fill(Theme.pink).frame(width: 8, height: 8)
                            MetaRow(text: "\(newCount) new")
                        }
                    }
                }
            }
            Spacer()
            PlateView(url: imageURL, label: story.sources.first?.publisher ?? "?", height: 72)
                .frame(width: 72)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 20).padding(.horizontal, 24)
        .overlay(alignment: .bottom) { Rectangle().fill(Theme.rule).frame(height: 0.5).padding(.horizontal, 24) }
    }
}
