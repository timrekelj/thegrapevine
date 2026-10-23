import SwiftUI
import UIKit

/// The paper's tokens — the same as `web/app/globals.css`. Newsprint and ink carry the page;
/// colour comes only from photographs and members' discs. Press red has one meaning: new to you.
/// Every colour has an evening edition that follows the system appearance.
enum Theme {
    private static let newsprint: UInt32 = 0xF4F1EA, eveningPaper: UInt32 = 0x121110
    static let paper = Color(light: newsprint, dark: eveningPaper)
    /// The paper for drawing outside SwiftUI: the halftone plates print onto it.
    static func paper(dark: Bool) -> UIColor { UIColor(hex: dark ? eveningPaper : newsprint) }
    static let raised = Color(light: 0xFBF9F4, dark: 0x1B1A17)
    static let ink = Color(light: 0x161513, dark: 0xEDE9E1)
    static let ink2 = Color(light: 0x4A4740, dark: 0xB9B3A8)
    static let ink3 = Color(light: 0x75716A, dark: 0x8C877D)
    static let rule = Color(light: 0x161513, dark: 0xEDE9E1, opacity: 0.16)
    static let tint = Color(light: 0x161513, dark: 0xEDE9E1, opacity: 0.06)
    /// The spot ink: only for things that are new to you.
    static let red = Color(light: 0xC8231B, dark: 0xFF5B4F)
    static let onRed = Color(light: 0xFFFFFF, dark: 0x1A0705)
    static let danger = Color(light: 0xA3261F, dark: 0xFF8A80)

    // MARK: Type
    // A blackletter nameplate, a news serif with optical sizes for everything you read,
    // a typewriter mono for datelines and counts, and the system sans for controls.

    /// Nameplates only: the paper's name and "The Grapevine".
    static func nameplate(_ size: CGFloat) -> Font { .custom("UnifrakturMaguntia", size: size) }
    /// Headlines. Large sizes use Newsreader's display cut, small ones its text cut.
    static func headline(_ size: CGFloat) -> Font {
        .custom(size >= 28 ? "Newsreader72pt-SemiBold" : "Newsreader-SemiBold", size: size)
    }
    static func serif(_ size: CGFloat) -> Font { .custom("Newsreader-Regular", size: size) }
    static func italic(_ size: CGFloat, medium: Bool = false) -> Font {
        .custom(medium ? "Newsreader-MediumItalic" : "Newsreader-Italic", size: size)
    }
    static func mono(_ size: CGFloat, bold: Bool = false) -> Font {
        .custom(bold ? "IBMPlexMono-SemiBold" : "IBMPlexMono-Medium", size: size)
    }

    /// Corner radius for fields and buttons: set square, like type in a forme.
    static let radius: CGFloat = 3
}

extension Color {
    init(light: UInt32, dark: UInt32, opacity: CGFloat = 1) {
        self.init(uiColor: UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light, alpha: opacity)
        })
    }
}

extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: alpha)
    }
}

/// Small caps label: mono, uppercase, tracked.
struct Kicker: View {
    let text: String
    var color: Color = Theme.ink
    var size: CGFloat = 11
    var body: some View {
        Text(text.uppercased())
            .font(Theme.mono(size, bold: true)).tracking(size * 0.12)
            .foregroundColor(color)
    }
}

/// The thick-and-thin rule under a nameplate.
struct DoubleRule: View {
    var body: some View {
        VStack(spacing: 2) {
            Rectangle().fill(Theme.ink).frame(height: 3)
            Rectangle().fill(Theme.ink).frame(height: 1)
        }
        .accessibilityHidden(true)
    }
}

struct Hairline: View {
    var color: Color = Theme.rule
    var body: some View { Rectangle().fill(color).frame(height: 1).accessibilityHidden(true) }
}

// The ink block button, `InkButtonStyle`, is with the rest of the press in Press.swift.

/// A paper field: raised, hairline, square corners, set in the serif.
struct PaperFieldStyle: ViewModifier {
    var focused = false
    var invalid = false
    func body(content: Content) -> some View {
        content
            .font(Theme.serif(18))
            .foregroundColor(Theme.ink)
            .padding(.horizontal, 14).frame(minHeight: 50)
            .background(Theme.raised, in: RoundedRectangle(cornerRadius: Theme.radius))
            .overlay(RoundedRectangle(cornerRadius: Theme.radius)
                .stroke(invalid ? Theme.danger : focused ? Theme.ink : Theme.rule, lineWidth: 1))
    }
}

extension View {
    func paperField(focused: Bool = false, invalid: Bool = false) -> some View {
        modifier(PaperFieldStyle(focused: focused, invalid: invalid))
    }
}
