import SwiftUI

// MARK: - Touch and loading
// How the app shows work in progress and answers a touch. Nothing spins: three dots pulse in turn,
// buttons give a little under the finger, and content fades in over its placeholder.
// Shared with the share extension, so it depends only on `Theme`.

/// Three dots pulsing in turn: the app's only loading mark. With Reduce Motion the dots
/// only fade, without growing.
struct InkDots: View {
    var size: CGFloat = 7
    var color: Color = Theme.ink

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// One pass over all three dots, and how far apart they pulse.
    private static let cycle = 1.1, stagger = 0.15

    var body: some View {
        TimelineView(.animation) { context in
            let time = context.date.timeIntervalSinceReferenceDate
            HStack(spacing: size * 0.7) {
                ForEach(0..<3, id: \.self) { index in
                    let ink = Self.ink(at: time - Double(index) * Self.stagger)
                    Circle()
                        .fill(color)
                        .frame(width: size, height: size)
                        .scaleEffect(reduceMotion ? 1 : 0.3 + 0.7 * ink)
                        .opacity(0.25 + 0.75 * ink)
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Loading")
    }

    /// 0 → 1 → 0 over the first two-thirds of a cycle, eased at both ends; resting for the rest.
    private static func ink(at time: Double) -> Double {
        let phase = time.truncatingRemainder(dividingBy: cycle) / cycle
        let local = (phase < 0 ? phase + 1 : phase) / 0.66
        guard local < 1 else { return 0 }
        let wave = sin(.pi * local)
        return wave * wave
    }
}

/// Dots over a short line, for a whole page that's on its way.
struct PressLoading: View {
    var text = "Loading"
    var body: some View {
        VStack(spacing: 12) {
            InkDots(size: 8, color: Theme.ink3).accessibilityHidden(true)
            Text(text).font(Theme.text(15, weight: .semibold)).foregroundColor(Theme.ink2)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
    }
}

/// A label that keeps its place while its work is under way: the text stays in the layout, unseen,
/// so nothing around it moves, and the dots pulse where it was.
struct BusyLabel: View {
    let text: String
    var busy: Bool
    var color: Color = Theme.ink
    var dotSize: CGFloat = 6

    var body: some View {
        Text(text)
            .opacity(busy ? 0 : 1)
            .overlay { if busy { InkDots(size: dotSize, color: color).transition(.opacity) } }
            .animation(.easeOut(duration: 0.18), value: busy)
    }
}

/// The primary button: an ink-filled capsule. Dims only the fill when disabled, so the label stays
/// readable. Gives a little under the finger, with a light tap; `busy` swaps the label for dots in the
/// ground colour, keeping the button's size, and stops further taps.
struct InkButtonStyle: ButtonStyle {
    var enabled = true
    var busy = false
    var height: CGFloat = 50

    func makeBody(configuration: Configuration) -> some View {
        InkButton(configuration: configuration, enabled: enabled, busy: busy, height: height)
    }

    private struct InkButton: View {
        let configuration: Configuration
        let enabled: Bool
        let busy: Bool
        let height: CGFloat
        @Environment(\.accessibilityReduceMotion) private var reduceMotion

        var body: some View {
            let pressed = configuration.isPressed && enabled && !busy
            configuration.label
                .font(.system(size: 16, weight: .semibold))
                .opacity(busy ? 0 : 1)
                .overlay { if busy { InkDots(size: 6, color: Theme.paper).transition(.opacity) } }
                .frame(maxWidth: .infinity, minHeight: height)
                // A busy button keeps its full ink: the work is under way, not unavailable.
                .background(Theme.ink.opacity(enabled || busy ? (pressed ? 0.82 : 1) : 0.4),
                            in: Capsule())
                .foregroundColor(Theme.paper)
                .contentShape(Capsule())
                .scaleEffect(pressed && !reduceMotion ? 0.97 : 1)
                .offset(y: pressed && !reduceMotion ? 1 : 0)
                .animation(.spring(response: 0.22, dampingFraction: 0.6), value: pressed)
                .animation(.easeOut(duration: 0.18), value: busy)
                .allowsHitTesting(!busy)
                .sensoryFeedback(.impact(weight: .light), trigger: pressed) { _, now in now }
        }
    }
}

/// For whole stories and quiet controls: a slight give and a dim under the finger.
/// No haptic; the next screen is the answer.
struct PressStyle: ButtonStyle {
    var scale: CGFloat = 0.985

    func makeBody(configuration: Configuration) -> some View {
        Pressed(configuration: configuration, scale: scale)
    }

    private struct Pressed: View {
        let configuration: Configuration
        let scale: CGFloat
        @Environment(\.accessibilityReduceMotion) private var reduceMotion

        var body: some View {
            configuration.label
                .opacity(configuration.isPressed ? 0.7 : 1)
                .scaleEffect(configuration.isPressed && !reduceMotion ? scale : 1)
                .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
        }
    }
}

extension View {
    /// Reads `text` (e.g. "Sending…") to VoiceOver while the control is busy and its label is hidden.
    func busyAccessibility(_ busy: Bool, _ text: String) -> some View {
        accessibilityLabel(Text(text), isEnabled: busy)
    }

    /// A confirmation arriving: it settles in from just above.
    func arrives() -> some View {
        transition(.opacity.combined(with: .offset(y: -6)))
    }
}

/// A submitted link is a grape; the green ring records its arrival.
struct FilingMark: View {
    var busy = false
    var confirmed = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var progress = false
    var body: some View {
        ZStack {
            Image(systemName: "leaf.fill").font(.system(size: 20)).foregroundStyle(Theme.red).offset(x: -10, y: -18)
            Circle().fill(Theme.ink).frame(width: 36, height: 36)
            Circle().trim(from: 0, to: confirmed ? 1 : progress ? 0.85 : 0.2)
                .stroke(Theme.red, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .rotationEffect(.degrees(-90)).frame(width: 46, height: 46)
                .animation(busy && !reduceMotion ? .easeInOut(duration: 1.2).repeatForever(autoreverses: true) : .easeOut(duration: 0.2), value: progress)
        }
        .frame(width: 64, height: 64).accessibilityHidden(true)
        .onAppear { progress = busy }
        .onChange(of: busy) { progress = busy }
    }
}
