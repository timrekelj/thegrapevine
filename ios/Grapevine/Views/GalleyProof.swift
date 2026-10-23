import SwiftUI

// MARK: - Galley proofs
// Before the edition arrives, the page is set as a proof: grey bars where the headlines will run,
// lines where the columns will be, and plates of fine dots where the pictures go. A slow sheen
// passes over the proof, and the plates' dots swell and settle, so the page reads as being printed.

/// A bar of proof ink: a headline, a kicker or a line of a column.
struct ProofBar: View {
    var width: CGFloat? = nil
    var height: CGFloat = 12
    var heavy = false

    var body: some View {
        Rectangle()
            .fill(heavy ? Theme.rule : Theme.tint)
            .frame(width: width, height: height)
            .frame(maxWidth: width == nil ? .infinity : nil, alignment: .leading)
    }
}

/// Column text: full lines with a short last one.
struct ProofLines: View {
    var count = 4
    var height: CGFloat = 9
    var spacing: CGFloat = 9
    var last: CGFloat = 0.6

    var body: some View {
        GeometryReader { geometry in
            VStack(alignment: .leading, spacing: spacing) {
                ForEach(0..<count, id: \.self) { index in
                    ProofBar(width: index == count - 1 ? geometry.size.width * last : nil, height: height)
                }
            }
        }
        .frame(height: CGFloat(count) * height + CGFloat(count - 1) * spacing)
    }
}

/// A plate waiting for its picture: a fine halftone screen whose dots swell in a slow diagonal
/// wave. Still with Reduce Motion.
struct ProofPlate: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 24, paused: reduceMotion)) { context in
            let time = reduceMotion ? 0 : context.date.timeIntervalSinceReferenceDate
            Canvas { canvas, size in
                // One path, filled once, as in `TypePlate`.
                let step: CGFloat = 5
                var dots = Path()
                for x in stride(from: step / 2, to: size.width, by: step) {
                    for y in stride(from: step / 2, to: size.height, by: step) {
                        let wave = 0.5 + 0.5 * sin(time * 1.8 - Double(x + y) / 46)
                        let radius = 0.6 + 0.75 * wave
                        dots.addEllipse(in: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
                    }
                }
                canvas.fill(dots, with: .color(Theme.rule))
            }
        }
        .background(Theme.tint)
        .accessibilityHidden(true)
    }
}

/// A slow sheen across a proof, left to right, like light on wet ink. Off with Reduce Motion.
struct ProofSheen: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        if reduceMotion {
            content
        } else {
            TimelineView(.animation(minimumInterval: 1 / 30)) { context in
                let period = 1.8
                let phase = context.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: period) / period
                // The band runs from well off the left edge to well off the right.
                let centre = -0.4 + 1.8 * phase
                content.mask {
                    LinearGradient(stops: [
                        .init(color: .black.opacity(0.55), location: 0),
                        .init(color: .black.opacity(0.55), location: 0.35),
                        .init(color: .black, location: 0.5),
                        .init(color: .black.opacity(0.55), location: 0.65),
                        .init(color: .black.opacity(0.55), location: 1),
                    ], startPoint: UnitPoint(x: centre - 0.5, y: 0.3), endPoint: UnitPoint(x: centre + 0.5, y: 0.7))
                }
            }
        }
    }
}

extension View {
    func proofSheen() -> some View { modifier(ProofSheen()) }
}

/// A proof of the front page under the masthead: the lead with its plate and drop-capped column,
/// then the featured side story.
struct FrontPageProof: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ProofBar(width: 72, height: 10, heavy: true)
            VStack(alignment: .leading, spacing: 10) {
                ProofBar(height: 30, heavy: true)
                ProofBar(height: 30, heavy: true)
                ProofBar(width: 170, height: 30, heavy: true)
            }
            .padding(.top, 14).padding(.bottom, 18)
            ProofPlate().aspectRatio(16 / 9, contentMode: .fit)
            ProofBar(width: 190, height: 8).padding(.vertical, 10)
            Hairline()
            HStack(alignment: .top, spacing: 10) {
                ProofBar(width: 38, height: 44, heavy: true)
                ProofLines(count: 3, height: 10, spacing: 7)
            }
            .padding(.top, 16)
            ProofLines(count: 3, height: 10, spacing: 7, last: 0.45).padding(.top, 7)
            ProofBar(width: 220, height: 9).padding(.top, 18)

            Hairline().padding(.vertical, 22)
            ProofPlate().aspectRatio(3 / 2, contentMode: .fit)
            ProofBar(width: 60, height: 10, heavy: true).padding(.top, 14)
            VStack(alignment: .leading, spacing: 9) {
                ProofBar(height: 22, heavy: true)
                ProofBar(width: 200, height: 22, heavy: true)
            }
            .padding(.top, 10)
            ProofLines(count: 3, height: 9, spacing: 8).padding(.top, 12)
        }
        .padding(.top, 22)
        .proofSheen()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Loading the edition")
    }
}

/// A proof of a story page: kicker, a long headline, the sources rule, the hero plate and the column.
struct StoryProof: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                ProofBar(width: 70, height: 10, heavy: true)
                ProofBar(width: 90, height: 10)
            }
            VStack(alignment: .leading, spacing: 10) {
                ProofBar(height: 32, heavy: true)
                ProofBar(height: 32, heavy: true)
                ProofBar(width: 150, height: 32, heavy: true)
            }
            .padding(.top, 14).padding(.bottom, 18)
            ProofBar(width: 240, height: 9)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 11)
                .overlay(alignment: .top) { Rectangle().fill(Theme.rule).frame(height: 1) }
                .overlay(alignment: .bottom) { Hairline() }
            ProofPlate().aspectRatio(16 / 9, contentMode: .fit).padding(.top, 20)
            HStack(alignment: .top, spacing: 10) {
                ProofBar(width: 40, height: 46, heavy: true)
                ProofLines(count: 3, height: 10, spacing: 8)
            }
            .padding(.top, 22)
            ProofLines(count: 5, height: 10, spacing: 8, last: 0.4).padding(.top, 8)
        }
        .proofSheen()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Loading the story")
    }
}

/// A proof of the archive: a day's rule, then rows of kicker, headline and counts.
struct ArchiveProof: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ProofBar(width: 130, height: 9, heavy: true)
                .padding(.top, 30).padding(.bottom, 9)
                .frame(maxWidth: .infinity, alignment: .leading)
                .overlay(alignment: .bottom) { Rectangle().fill(Theme.rule).frame(height: 1) }
            ForEach(0..<4, id: \.self) { index in
                VStack(alignment: .leading, spacing: 8) {
                    ProofBar(width: 60, height: 9, heavy: true)
                    ProofBar(height: 18, heavy: true)
                    ProofBar(width: index.isMultiple(of: 2) ? 180 : 120, height: 18, heavy: true)
                    ProofBar(width: 140, height: 8)
                }
                .padding(.vertical, 16)
                Hairline()
            }
        }
        .proofSheen()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Loading earlier editions")
    }
}

/// The red "new" dot announces itself once: a ring of spot ink spreads from it and fades, as ink
/// soaking into newsprint. Nothing with Reduce Motion.
struct InkPulse: ViewModifier {
    var color: Color = Theme.red
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var spread = false

    func body(content: Content) -> some View {
        content
            .background {
                if !reduceMotion {
                    Circle().fill(color)
                        .scaleEffect(spread ? 2.6 : 1)
                        .opacity(spread ? 0 : 0.5)
                }
            }
            .onAppear {
                guard !reduceMotion, !spread else { return }
                withAnimation(.easeOut(duration: 1.1).delay(0.25)) { spread = true }
            }
    }
}

extension View {
    func inkPulse(_ color: Color = Theme.red) -> some View { modifier(InkPulse(color: color)) }
}
