import SwiftUI

/// A member's disc: their initial in ink on a lit gradient of their hue (see `MemberHues` in Vine.swift).
struct MemberAvatar: View {
    let profile: Profile
    var size: CGFloat = 32
    var body: some View {
        let index = memberColorIndex(profile.id.uuidString)
        Text(profile.display_name.prefix(1).uppercased())
            .font(.system(size: size * 0.4, weight: .semibold))
            .foregroundStyle(Color(hex: 0x15221B))
            .frame(width: size, height: size)
            .background(RadialGradient(colors: [memberLightColors[index], memberColors[index]], center: .topLeading, startRadius: 0, endRadius: size), in: Circle())
            .accessibilityLabel(profile.display_name)
    }

}

/// Overlapping discs, each ringed in the ground colour.
struct AvatarStack: View {
    let profiles: [Profile]
    var size: CGFloat = 26
    var limit = 5

    var body: some View {
        HStack(spacing: -size * 0.22) {
            ForEach(profiles.prefix(limit)) { profile in
                MemberAvatar(profile: profile, size: size)
                    .overlay(Circle().stroke(Theme.paper, lineWidth: 2))
            }
        }
    }
}

/// "3 new": a vine-green capsule badge for what is new to you.
struct NewBadge: View {
    let text: String
    var body: some View {
        Text(text)
            .font(Theme.text(13, weight: .semibold))
            .padding(.horizontal, 9).padding(.vertical, 4)
            .background(Theme.red, in: Capsule()).foregroundColor(Theme.onRed)
    }
}

/// A source photograph, shown clean and filling its frame, or, with no picture, a tile with the
/// publisher's name. While the photo is on its way the frame holds a plain placeholder shape.
/// `radius` rounds the placeholder and the tile; callers clip the photo to their own frame.
struct PlateView: View {
    let url: URL?
    let label: String
    var height: CGFloat? = nil
    var radius: CGFloat = 14

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var photo: UIImage?
    @State private var failed = false

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                if let photo {
                    Image(uiImage: photo).resizable().scaledToFill()
                        .transition(.opacity)
                } else if url == nil || failed {
                    PublisherTile(label: label, radius: radius)
                } else {
                    PlaceholderShape(radius: radius).transition(.opacity)
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .clipped()
        }
        .frame(height: height)
        .task(id: url) { await load() }
        .accessibilityHidden(true)
    }

    private func load() async {
        guard let url else { return }
        if let hit = PhotoStore.shared.cached(url) { photo = hit; return }
        do {
            let loaded = try await PhotoStore.shared.image(url)
            withAnimation(reduceMotion ? nil : .easeOut(duration: 0.3)) { photo = loaded }
        } catch { failed = true }
    }
}

/// A source with no picture: the publisher's name on a plain raised tile.
struct PublisherTile: View {
    let label: String
    var radius: CGFloat = 14
    var body: some View {
        Text(label)
            .font(Theme.headline(17))
            .foregroundColor(Theme.ink2)
            .multilineTextAlignment(.center)
            .lineLimit(2).minimumScaleFactor(0.6)
            .padding(8)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Theme.raised2, in: RoundedRectangle(cornerRadius: radius))
    }
}

/// A section title in the display face.
struct SectionHead: View {
    let title: String
    var body: some View {
        Text(title).font(Theme.headline(19)).foregroundStyle(Theme.ink)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isHeader)
    }
}

/// A raised object: the raised surface, rounded, with the soft pillow shadow and a faint edge.
struct Card: ViewModifier {
    var radius: CGFloat = 14
    func body(content: Content) -> some View {
        content
            .background(Theme.raised, in: RoundedRectangle(cornerRadius: radius))
            .overlay(RoundedRectangle(cornerRadius: radius).stroke(Theme.rule.opacity(0.4), lineWidth: 1))
            .shadow(color: Theme.shadow, radius: 14, y: 8)
    }
}

extension View {
    func card(radius: CGFloat = 14) -> some View { modifier(Card(radius: radius)) }
}

struct MetaRow: View {
    let text: String
    var body: some View {
        Text(text)
            .font(Theme.text(13))
            .foregroundColor(Theme.ink3)
    }
}

func plural(_ count: Int, _ word: String, _ plural: String? = nil) -> String {
    "\(count) \(count == 1 ? word : plural ?? word + "s")"
}

@MainActor private let relativeFormatter: RelativeDateTimeFormatter = {
    let formatter = RelativeDateTimeFormatter()
    formatter.unitsStyle = .short
    return formatter
}()

/// Formatters are costly to make and these run in view bodies, so each is made once (views format on the main actor).
@MainActor func timeAgo(_ date: Date) -> String {
    relativeFormatter.localizedString(for: date, relativeTo: Date())
}

func clock(_ date: Date) -> String {
    date.formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute(.twoDigits))
}

@MainActor private let dayFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "EEEE d MMMM"
    return formatter
}()

@MainActor func dayLabel(_ date: Date) -> String {
    dayFormatter.string(from: date)
}

/// The head of a page: a large title in the display face and an optional line under it.
struct PageHead: View {
    let title: String
    var dateline: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(Theme.headline(34)).accessibilityAddTraits(.isHeader)
            if let dateline { Text(dateline).font(Theme.text(15)).foregroundStyle(Theme.ink2) }
        }
        .padding(.top, 24).padding(.bottom, 20)
    }
}
