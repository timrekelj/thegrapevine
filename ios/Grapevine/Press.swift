import SwiftUI

// MARK: - The press
// How the paper shows work in progress and answers a touch. Nothing spins: ink dots print in turn,
// buttons sink like a stamp, and the page inks in when the edition is ready.
// Shared with the share extension, so it depends only on `Theme`.

/// Three dots of ink printing in turn: the paper's only loading mark. With Reduce Motion the dots
/// only fade, without swelling.
struct InkDots: View {
    var size: CGFloat = 7
    var color: Color = Theme.ink

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// One impression of all three dots, and how far apart they strike.
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

/// Ink dots over a kicker: "Going to press…", for a whole page that's on its way.
struct PressLoading: View {
    var text = "Going to press…"
    var body: some View {
        VStack(spacing: 14) {
            InkDots(size: 8).accessibilityHidden(true)
            Kicker(text: text, color: Theme.ink2, size: 10)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
    }
}

/// A label that keeps its place while its work is under way: the text stays in the layout, unseen,
/// so nothing around it moves, and ink dots print where it was.
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

/// The ink block button. Dims only the fill when disabled, so the label stays readable. Sinks like a
/// stamp under the finger, with a light tap on primary actions; `busy` swaps the label for paper-white
/// ink dots, keeping the button's size, and stops further taps.
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
                            in: RoundedRectangle(cornerRadius: Theme.radius))
                .foregroundColor(Theme.paper)
                .contentShape(RoundedRectangle(cornerRadius: Theme.radius))
                .scaleEffect(pressed && !reduceMotion ? 0.97 : 1)
                .offset(y: pressed && !reduceMotion ? 1 : 0)
                .animation(.spring(response: 0.22, dampingFraction: 0.6), value: pressed)
                .animation(.easeOut(duration: 0.18), value: busy)
                .allowsHitTesting(!busy)
                .sensoryFeedback(.impact(weight: .light), trigger: pressed) { _, now in now }
        }
    }
}

/// For whole stories and quiet controls: a slight give and a dim under the finger, as a page
/// pressed flat. No haptic; the next screen is the answer.
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
