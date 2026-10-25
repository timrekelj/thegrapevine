import SwiftUI

// MARK: - Loading placeholders
// Before content arrives, plain shapes in the tint colour stand where it will go, rounded like the
// real layout: 28 for the lead card's photo, 14 for thumbnails, capsules for lines of text. They
// wait a moment before showing (a fast load never flashes them), then breathe with a gentle
// opacity pulse. With Reduce Motion they appear without the pulse.

/// A rounded placeholder for a photo, a card or a thumbnail.
struct PlaceholderShape: View {
    var radius: CGFloat = 14
    var body: some View {
        RoundedRectangle(cornerRadius: radius).fill(Theme.tint)
            .accessibilityHidden(true)
    }
}

/// A line of text that hasn't arrived: a capsule.
struct PlaceholderLine: View {
    var width: CGFloat? = nil
    var height: CGFloat = 12

    var body: some View {
        Capsule().fill(Theme.tint)
            .frame(width: width, height: height)
            .frame(maxWidth: width == nil ? .infinity : nil, alignment: .leading)
    }
}

/// Waits `delay` before showing the placeholder, then pulses its opacity gently.
struct PlaceholderPulse: ViewModifier {
    var delay: Duration = .milliseconds(250)
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown = false
    @State private var dim = false

    func body(content: Content) -> some View {
        content
            .opacity(shown ? (dim ? 0.55 : 1) : 0)
            .task {
                try? await Task.sleep(for: delay)
                guard !Task.isCancelled else { return }
                withAnimation(.easeOut(duration: 0.2)) { shown = true }
                guard !reduceMotion else { return }
                withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) { dim = true }
            }
    }
}

extension View {
    func placeholderPulse() -> some View { modifier(PlaceholderPulse()) }
}

/// A row placeholder: the bunch, two lines, and the 64pt thumbnail.
private struct RowPlaceholder: View {
    var wide = true
    var body: some View {
        HStack(spacing: 12) {
            Circle().fill(Theme.tint).frame(width: 32, height: 32)
            VStack(alignment: .leading, spacing: 8) {
                PlaceholderLine(height: 14)
                PlaceholderLine(width: wide ? 160 : 110, height: 14)
                PlaceholderLine(width: 120, height: 10)
            }
            PlaceholderShape(radius: 14).frame(width: 60, height: 60)
        }
        .padding(.vertical, 18)
    }
}

/// The front page while the first load runs: the lead card, then story rows.
struct FrontPagePlaceholder: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                PlaceholderShape(radius: 28).aspectRatio(1.6, contentMode: .fit)
                VStack(alignment: .leading, spacing: 12) {
                    PlaceholderLine(width: 110, height: 10)
                    PlaceholderLine(height: 26)
                    PlaceholderLine(width: 190, height: 26)
                    HStack(spacing: 14) {
                        Circle().fill(Theme.tint).frame(width: 44, height: 44)
                        VStack(alignment: .leading, spacing: 6) {
                            PlaceholderLine(width: 130, height: 11)
                            PlaceholderLine(width: 90, height: 11)
                        }
                    }
                    .padding(.top, 4)
                }
                .padding(20)
            }
            .padding(.bottom, 8)
            ForEach(0..<3, id: \.self) { index in
                RowPlaceholder(wide: index.isMultiple(of: 2))
            }
        }
        .placeholderPulse()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Loading the paper")
    }
}

/// A story while it loads: the meta line, the title, the catch-up card and the sources.
struct StoryPlaceholder: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            PlaceholderLine(width: 130, height: 11)
            VStack(alignment: .leading, spacing: 10) {
                PlaceholderLine(height: 28)
                PlaceholderLine(width: 200, height: 28)
            }
            .padding(.top, 14).padding(.bottom, 18)
            PlaceholderShape(radius: 28).frame(height: 190)
            PlaceholderLine(width: 90, height: 11).padding(.top, 24)
            HStack(spacing: 24) {
                ForEach(0..<4, id: \.self) { _ in Circle().fill(Theme.tint).frame(width: 30, height: 30) }
            }
            .padding(.top, 14)
        }
        .placeholderPulse()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Loading the story")
    }
}

/// Earlier stories while they load: a day label, then rows.
struct ArchivePlaceholder: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            PlaceholderLine(width: 140, height: 11).padding(.top, 24).padding(.bottom, 4)
            ForEach(0..<4, id: \.self) { index in
                VStack(alignment: .leading, spacing: 8) {
                    PlaceholderLine(width: 70, height: 10)
                    PlaceholderLine(height: 16)
                    PlaceholderLine(width: index.isMultiple(of: 2) ? 180 : 120, height: 16)
                    PlaceholderLine(width: 140, height: 10)
                }
                .padding(.vertical, 16)
            }
        }
        .placeholderPulse()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Loading earlier stories")
    }
}
