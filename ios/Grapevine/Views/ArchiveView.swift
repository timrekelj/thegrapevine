import SwiftUI

/// Opens the archive from the front page: stories last active before `before`.
struct ArchiveRoute: Hashable {
    let before: Date
}

/// "Earlier editions": older stories, newest first, under the day they were last active.
struct ArchiveView: View {
    @Environment(AppState.self) private var app
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var store = ArchiveStore()
    let before: Date

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                DoubleRule().padding(.bottom, 22)
                SectionHead(title: "Earlier editions").padding(.bottom, 4)
                if let error = store.error, store.stories.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(error).font(Theme.serif(17))
                        Button("Try again") { Task { await load(force: true) } }
                            .font(.system(size: 15, weight: .semibold)).underline()
                            .buttonStyle(PressStyle())
                    }
                    .padding(.vertical, 32)
                    .transition(.opacity)
                } else if !store.loaded {
                    ArchiveProof().transition(.opacity)
                } else if store.stories.isEmpty {
                    Text("Nothing older yet.").font(Theme.serif(17)).foregroundColor(Theme.ink2).padding(.vertical, 24)
                        .transition(.opacity)
                } else {
                    ForEach(store.days, id: \.day) { day, stories in
                        Kicker(text: dayLabel(day), color: Theme.ink2, size: 10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.top, 28).padding(.bottom, 8)
                            .overlay(alignment: .bottom) { Rectangle().fill(Theme.ink).frame(height: 1) }
                            .accessibilityAddTraits(.isHeader)
                        ForEach(stories) { story in
                            NavigationLink(value: story.id) { ArchiveRowView(story: story) }
                                .buttonStyle(PressStyle())
                                .transition(.opacity)
                            Hairline()
                        }
                    }
                    if store.hasMore || store.error != nil {
                        earlier.padding(.top, 28)
                    }
                }
            }
            .padding(.horizontal, 20).padding(.bottom, 24)
            // The first page inks in over its proof; later pages fade in below.
            .animation(.easeOut(duration: reduceMotion ? 0.2 : 0.4), value: store.loaded)
            .animation(.easeOut(duration: 0.3), value: store.stories.count)
        }
        .background(Theme.paper)
        .toolbarVisibility(.visible, for: .navigationBar)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Theme.paper, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text("The Grapevine").font(Theme.nameplate(24)).foregroundColor(Theme.ink)
            }
        }
        .refreshable { await load(force: true) }
        .task { await load() }
    }

    private func load(force: Bool = false) async {
        guard let group = app.group?.id else { return }
        await store.load(group: group, before: before, force: force)
    }

    /// "Load earlier", or the error from loading the next page with a retry.
    @ViewBuilder private var earlier: some View {
        if let error = store.error {
            Text(error).font(.system(size: 13, weight: .medium)).foregroundColor(Theme.danger).padding(.bottom, 10)
        }
        Button {
            guard let group = app.group?.id else { return }
            Task { await store.loadEarlier(group: group) }
        } label: {
            Kicker(text: "Load earlier", size: 12)
                .opacity(store.isLoading ? 0 : 1)
                .overlay { if store.isLoading { InkDots(size: 6).transition(.opacity) } }
                .animation(.easeOut(duration: 0.18), value: store.isLoading)
                .frame(maxWidth: .infinity, minHeight: 44)
                .overlay(alignment: .top) { DoubleRule() }
                .overlay(alignment: .bottom) { Rectangle().fill(Theme.ink).frame(height: 1) }
                .contentShape(Rectangle())
        }
        .buttonStyle(PressStyle())
        .disabled(store.isLoading)
        .busyAccessibility(store.isLoading, "Loading…")
    }
}

/// A story in the archive: kicker, headline, counts.
struct ArchiveRowView: View {
    let story: ArchiveRow

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Kicker(text: story.category ?? "News")
            Text(story.title.isEmpty ? "A link from the table" : story.title)
                .font(Theme.headline(20))
                .fixedSize(horizontal: false, vertical: true)
            Text("\(plural(story.sourceCount, "source")) · \(plural(story.messageCount, "message"))")
                .font(Theme.mono(12)).foregroundColor(Theme.ink2)
        }
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}
