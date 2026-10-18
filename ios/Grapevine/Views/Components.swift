import SwiftUI

struct Monogram: View {
    let profile: Profile
    var size: CGFloat = 32
    var body: some View {
        Text(profile.monogram)
            .font(Theme.display(size * 0.47))
            .frame(width: size, height: size)
            .background(Theme.ink)
            .foregroundColor(Theme.paper)
            .clipShape(Circle())
    }
}

struct Stamp: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.system(size: 13, weight: .semibold))
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(Theme.pink).foregroundColor(Theme.onPink)
            .clipShape(RoundedRectangle(cornerRadius: 5))

    }
}

/// The halftone plate: a source image printed in one ink, or a flat plate with the publisher's name.
struct PlateView: View {
    let url: URL?
    let label: String
    let height: CGFloat

    @State private var image: UIImage?
    @State private var failed = false

    var body: some View {
        GeometryReader { geometry in
            SwiftUI.Group {
                if let image, !failed {
                    Image(uiImage: image)
                        .resizable().scaledToFill()
                } else {
                    Rectangle()
                        .fill(Theme.ink.opacity(0.08))
                        .overlay(
                            Text(label)
                                .font(Theme.display(13))
                                .foregroundColor(Theme.ink)
                                .multilineTextAlignment(.center)
                                .lineLimit(2).minimumScaleFactor(0.75)
                                .padding(6)
                        )
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .clipped()
        }
        .frame(height: height)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .task(id: url) { await load() }
    }

    private func load() async {
        image = nil
        failed = false
        guard let url else { return }
        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            if let image = UIImage(data: data) { self.image = Plate.apply(to: image) } else { failed = true }
        } catch { failed = true }
    }
}

struct MetaRow: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.footnote.weight(.medium))
            .foregroundColor(Theme.ink2)
    }
}

func timeAgo(_ date: Date) -> String {
    let formatter = RelativeDateTimeFormatter()
    formatter.unitsStyle = .short
    return formatter.localizedString(for: date, relativeTo: Date())
}

func dayLabel(_ date: Date) -> String {
    let formatter = DateFormatter()
    formatter.dateFormat = "EEEE d MMMM"
    return formatter.string(from: date).uppercased()
}
