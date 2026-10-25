import SwiftUI

/// Each member owns one hue, in the order they joined the paper (the founder raspberry, then marigold,
/// terra, sky, grape, teal, cocoa). `AppState` records the order when the members load; anyone not in
/// it (a deleted account) falls back to a hash that matches web and Android.
enum MemberHues {
    nonisolated(unsafe) static var order: [String: Int] = [:]
    static func record(_ ids: [UUID]) {
        order = Dictionary(uniqueKeysWithValues: ids.enumerated().map { ($1.uuidString.lowercased(), $0) })
    }
}

func memberColorIndex(_ seed: String) -> Int {
    if let position = MemberHues.order[seed.lowercased()] { return position % 7 }
    let hash = seed.lowercased().utf8.reduce(UInt32(0)) { ($0 &* 31) &+ UInt32($1) }
    return Int(hash % 7)
}
let memberColors: [Color] = [0xD23D7E, 0xE3A512, 0xE2552B, 0x2F7BEA, 0x8E4FD8, 0x12A29A, 0x9C6B3C].map { Color(hex: $0) }
let memberLightColors: [Color] = [0xE66A9C, 0xF2BE3E, 0xF07A55, 0x5C9BF5, 0xAB7AE6, 0x3FC1B9, 0xB98858].map { Color(hex: $0) }

struct SourceBunch: View {
    let sources: [Source]
    var seenAt: Date? = nil
    var size: CGFloat = 44
    private let positions: [CGPoint] = [.init(x:13,y:19), .init(x:31,y:19), .init(x:22,y:29), .init(x:13,y:37), .init(x:31,y:37), .init(x:22,y:46), .init(x:22,y:16)]
    var body: some View {
        Canvas { context, bounds in
            let scale = bounds.width / 44
            context.scaleBy(x: scale, y: scale)
            var stem = Path()
            stem.move(to: .init(x:22,y:12)); stem.addCurve(to: .init(x:29,y:2), control1: .init(x:21,y:7), control2: .init(x:24,y:3))
            context.stroke(stem, with: .color(Theme.red), style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
            var leaf = Path()
            leaf.move(to: .init(x:22,y:10)); leaf.addQuadCurve(to: .init(x:7,y:11), control: .init(x:10,y:0)); leaf.addQuadCurve(to: .init(x:22,y:10), control: .init(x:14,y:16))
            context.fill(leaf, with: .color(Theme.red))
            for (index, source) in sources.sorted { $0.created_at > $1.created_at }.prefix(7).enumerated() {
                let point = positions[index]
                let hue = memberColorIndex(source.submitted_by?.uuidString ?? source.id.uuidString)
                let circle = Path(ellipseIn: CGRect(x: point.x-8,y: point.y-8,width:16,height:16))
                context.fill(circle, with: .radialGradient(Gradient(colors:[memberLightColors[hue],memberColors[hue]]), center: .init(x:point.x-3,y:point.y-3), startRadius:0,endRadius:13))
                context.stroke(circle, with: .color(Theme.paper), lineWidth: 2)
                if seenAt.map({ source.created_at > $0 }) ?? true {
                    context.stroke(Path(ellipseIn: CGRect(x:point.x-10,y:point.y-10,width:20,height:20)), with: .color(Theme.red), lineWidth:2)
                }
            }
        }
        .frame(width: size, height: size * 1.23)
        .accessibilityHidden(true)
    }
}

/// The app's mark, as on the icon: five ink grapes, one ringed in vine green, under a green stem and leaf.
struct BunchMark: View {
    var size: CGFloat = 96

    var body: some View {
        Canvas { context, bounds in
            let scale = bounds.width / 100
            context.scaleBy(x: scale, y: scale)
            var leaf = Path()
            leaf.move(to: .init(x: 48, y: 28))
            leaf.addQuadCurve(to: .init(x: 21, y: 31), control: .init(x: 32, y: 14))
            leaf.addQuadCurve(to: .init(x: 48, y: 28), control: .init(x: 35, y: 23))
            context.fill(leaf, with: .color(Theme.red))
            var stem = Path()
            stem.move(to: .init(x: 50, y: 30))
            stem.addCurve(to: .init(x: 60, y: 13), control1: .init(x: 49, y: 21), control2: .init(x: 53, y: 17))
            context.stroke(stem, with: .color(Theme.red), style: StrokeStyle(lineWidth: 4.5, lineCap: .round))
            let grapes: [(CGFloat, CGFloat, CGFloat)] = [(32, 44, 13.5), (68, 44, 13.5), (50, 59, 13.5), (35, 75, 11.5)]
            for (x, y, r) in grapes {
                let grape = Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
                context.stroke(grape, with: .color(Theme.paper), lineWidth: 4)
                context.fill(grape, with: .color(Theme.ink))
            }
            // The newest grape, ringed: a ground-coloured gap, then the green ring.
            let ring = Path(ellipseIn: CGRect(x: 66 - 17, y: 77 - 17, width: 34, height: 34))
            context.fill(ring, with: .color(Theme.paper))
            context.stroke(ring, with: .color(Theme.red), lineWidth: 4)
            context.fill(Path(ellipseIn: CGRect(x: 66 - 10.5, y: 77 - 10.5, width: 21, height: 21)), with: .color(Theme.ink))
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}
