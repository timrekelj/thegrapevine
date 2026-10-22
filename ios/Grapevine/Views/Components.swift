import SwiftUI

/// A member's avatar: their colour halftone disc, drawn by the web app from their id (`/avatar/<id>.png`).
struct MemberAvatar: View {
    let profile: Profile
    var size: CGFloat = 32

    var body: some View {
        AsyncImage(url: Config.webURL.appending(path: "avatar/\(profile.id.uuidString.lowercased()).png")) { image in
            image.resizable().scaledToFill()
        } placeholder: {
            Theme.tint
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityLabel(profile.display_name)
    }
}

/// Overlapping avatars, each ringed in paper.
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

/// A block of red: something new to you.
struct Stamp: View {
    let text: String
    var body: some View {
        Text(text.uppercased())
            .font(Theme.mono(11, bold: true)).tracking(0.6)
            .padding(.horizontal, 7).padding(.vertical, 3)
            .background(Theme.red).foregroundColor(Theme.onRed)
            .rotationEffect(.degrees(-2))
    }
}

/// A dot of red before a count of new things.
struct NewMark: View {
    let text: String
    var body: some View {
        HStack(spacing: 5) {
            Circle().fill(Theme.red).frame(width: 7, height: 7)
            Text(text).font(Theme.mono(12, bold: true)).foregroundColor(Theme.ink)
        }
    }
}

/// A photograph printed through the colour halftone screen, or — with no picture — a type plate
/// with the publisher's name.
struct PlateView: View {
    let url: URL?
    let label: String
    var height: CGFloat? = nil

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.displayScale) private var displayScale
    @State private var printed: UIImage?
    @State private var failed = false

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                if let printed {
                    Image(uiImage: printed).resizable()
                } else if url == nil || failed {
                    TypePlate(label: label)
                } else {
                    Theme.tint
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .clipped()
            .task(id: "\(url?.absoluteString ?? "")|\(Int(geometry.size.width))x\(Int(geometry.size.height))|\(colorScheme == .dark)") {
                await load(size: geometry.size)
            }
        }
        .frame(height: height)
        .accessibilityHidden(true)
    }

    private func load(size: CGSize) async {
        guard let url, size.width > 1, size.height > 1 else { return }
        let dark = colorScheme == .dark
        let scale = displayScale
        let key = "\(url.absoluteString)|\(Int(size.width))x\(Int(size.height))@\(scale)|\(dark)"
        if let hit = Halftone.cached(key) { printed = hit; return }
        do {
            let photo = try await PhotoStore.shared.image(url)
            let result = await Task.detached(priority: .userInitiated) {
                Halftone.print(photo, size: size, scale: scale, dark: dark, key: key)
            }.value
            if let result { printed = result } else { failed = true }
        } catch { failed = true }
    }
}

/// A source with no picture: the publisher's name, set on a light screen.
struct TypePlate: View {
    let label: String
    var body: some View {
        ZStack {
            Canvas { context, size in
                let step: CGFloat = 4
                for x in stride(from: step / 2, to: size.width, by: step) {
                    for y in stride(from: step / 2, to: size.height, by: step) {
                        context.fill(Path(ellipseIn: CGRect(x: x - 0.9, y: y - 0.9, width: 1.8, height: 1.8)), with: .color(Theme.rule))
                    }
                }
            }
            Text(label)
                .font(Theme.italic(15, medium: true))
                .foregroundColor(Theme.ink)
                .multilineTextAlignment(.center)
                .lineLimit(2).minimumScaleFactor(0.6)
                .padding(.horizontal, 5).padding(.vertical, 2)
                .background(Theme.raised)
                .padding(6)
        }
        .background(Theme.raised)
        .overlay(Rectangle().stroke(Theme.rule, lineWidth: 1))
    }
}

/// A section head: a thick rule, then an italic title running into a hairline.
struct SectionHead: View {
    let title: String
    var body: some View {
        VStack(spacing: 10) {
            Rectangle().fill(Theme.ink).frame(height: 3)
            HStack(spacing: 14) {
                Text(title).font(Theme.italic(26, medium: true)).foregroundColor(Theme.ink).fixedSize()
                Hairline()
            }
        }
        .accessibilityAddTraits(.isHeader)
    }
}

/// A dashed border, like a cut-out clipping.
struct Clipping: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(Theme.raised)
            .overlay(Rectangle().stroke(Theme.ink3, style: StrokeStyle(lineWidth: 1, dash: [3, 3])))
    }
}

extension View {
    func clipping() -> some View { modifier(Clipping()) }
}

struct MetaRow: View {
    let text: String
    var body: some View {
        Text(text)
            .font(Theme.mono(12))
            .foregroundColor(Theme.ink2)
    }
}

func plural(_ count: Int, _ word: String, _ plural: String? = nil) -> String {
    "\(count) \(count == 1 ? word : plural ?? word + "s")"
}

func timeAgo(_ date: Date) -> String {
    let formatter = RelativeDateTimeFormatter()
    formatter.unitsStyle = .short
    return formatter.localizedString(for: date, relativeTo: Date())
}

func clock(_ date: Date) -> String {
    date.formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute(.twoDigits))
}

func dayLabel(_ date: Date) -> String {
    let formatter = DateFormatter()
    formatter.dateFormat = "EEEE d MMMM"
    return formatter.string(from: date).uppercased()
}

/// The head of a tab page: a small nameplate, the double rule, then a kicker and a headline.
struct PageHead: View {
    let kicker: String
    let title: String
    var dateline: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("The Grapevine").font(Theme.nameplate(30))
                .frame(maxWidth: .infinity).padding(.top, 12).padding(.bottom, 10)
            DoubleRule()
            Kicker(text: kicker, color: Theme.ink2, size: 10).padding(.top, 20)
            Text(title).font(Theme.headline(44)).padding(.top, 4)
                .accessibilityAddTraits(.isHeader)
            if let dateline {
                Text(dateline).font(Theme.mono(12)).foregroundColor(Theme.ink2).padding(.top, 2)
            }
            Rectangle().fill(Theme.ink).frame(height: 1).padding(.top, 14).padding(.bottom, 4)
        }
    }
}
