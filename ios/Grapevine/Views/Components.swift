import SwiftUI

/// A member's avatar: their colour halftone disc, drawn by the web app from their id (`/avatar/<id>.png`).
/// Loaded through `PhotoStore`, so the many copies of one avatar on a page share a single download and,
/// once loaded, draw straight away when scrolled back into view.
struct MemberAvatar: View {
    let profile: Profile
    var size: CGFloat = 32
    @State private var image: UIImage?

    private var url: URL { Config.webURL.appending(path: "avatar/\(profile.id.uuidString.lowercased()).png") }

    var body: some View {
        SwiftUI.Group {
            if let image = image ?? PhotoStore.shared.cached(url) {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Theme.tint
            }
        }
        .task(id: profile.id) {
            if PhotoStore.shared.cached(url) == nil { image = try? await PhotoStore.shared.image(url) }
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
/// with the publisher's name. `screen = false` shows the photo clean.
struct PlateView: View {
    let url: URL?
    let label: String
    var height: CGFloat? = nil
    var screen = true

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.displayScale) private var displayScale
    @State private var printed: UIImage?
    @State private var failed = false

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                if let printed {
                    if screen {
                        Image(uiImage: printed).resizable()
                    } else {
                        Image(uiImage: printed).resizable().scaledToFill()
                    }
                } else if url == nil || failed {
                    TypePlate(label: label)
                } else {
                    Theme.tint
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .clipped()
            .task(id: "\(url?.absoluteString ?? "")|\(Int(geometry.size.width))x\(Int(geometry.size.height))|\(colorScheme == .dark)|\(screen)") {
                await load(size: geometry.size)
            }
        }
        .frame(height: height)
        .accessibilityHidden(true)
    }

    private func load(size: CGSize) async {
        guard let url, size.width > 1, size.height > 1 else { return }
        if !screen {
            do { printed = try await PhotoStore.shared.image(url) } catch { failed = true }
            return
        }
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
                // One path, filled once, rather than a fill per dot.
                let step: CGFloat = 4
                var dots = Path()
                for x in stride(from: step / 2, to: size.width, by: step) {
                    for y in stride(from: step / 2, to: size.height, by: step) {
                        dots.addEllipse(in: CGRect(x: x - 0.9, y: y - 0.9, width: 1.8, height: 1.8))
                    }
                }
                context.fill(dots, with: .color(Theme.rule))
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
    dayFormatter.string(from: date).uppercased()
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
