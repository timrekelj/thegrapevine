import SwiftUI
import UIKit

/// Shared vine roles: leaf-white surfaces, green-black ink, and green for new contributions.
/// Each role has an evening appearance that follows the system.
enum Theme {
    /// The ground: leaf-white by day, night on the vine in dark mode. (Legacy role name.)
    static let paper = Color(light: 0xF0F4EB, dark: 0x0E1711)
    static let raised = Color(light: 0xFFFFFF, dark: 0x172219)
    static let ink = Color(light: 0x15221B, dark: 0xEEF3EA)
    static let ink2 = Color(light: 0x3F4C45, dark: 0xB5C1B8)
    static let ink3 = Color(light: 0x5E6B63, dark: 0x8C9A90)
    static let rule = Color(light: 0x15221B, dark: 0xEEF3EA, opacity: 0.16)
    static let tint = Color(light: 0x15221B, dark: 0xEEF3EA, opacity: 0.06)
    /// Legacy role name retained across the app; vine green means new to you.
    static let red = Color(light: 0x1E7A39, dark: 0x4CC46F)
    static let onRed = Color(light: 0xFFFFFF, dark: 0x0E1711)
    static let danger = Color(light: 0xB42318, dark: 0xFF7A6E)

    static let raised2 = Color(light: 0xF6F8F3, dark: 0x1F2C22)
    static let vineSoft = Color(light: 0xDCEFE0, dark: 0x1D3A26)
    static let catchUp = Color(light: 0x15221B, dark: 0x1D3A26)
    static let onCatchUp = Color(hex: 0xEEF3EA)

    static func headline(_ size: CGFloat) -> Font {
        .custom("BricolageGrotesque-96ptExtraBold", size: size, relativeTo: size >= 28 ? .largeTitle : .headline).weight(size >= 28 ? .heavy : .bold)
    }
    /// SF Pro at `size` and `weight`, scaled with Dynamic Type.
    static func text(_ size: CGFloat, weight: UIFont.Weight = .regular) -> Font { scaledSystem(size, weight, .body) }
    // `serif`, `italic` and `mono` are legacy names: all three are SF Pro now.
    static func serif(_ size: CGFloat) -> Font { scaledSystem(size, .regular, .body) }
    static func italic(_ size: CGFloat, medium: Bool = false) -> Font { scaledSystem(size, medium ? .semibold : .regular, .body) }
    static func mono(_ size: CGFloat, bold: Bool = false) -> Font { scaledSystem(max(11, size), bold ? .semibold : .regular, .caption1).monospacedDigit() }
    /// SF Pro at a design size, scaled with Dynamic Type. (`Font.custom` cannot resolve the system face by name.)
    private static func scaledSystem(_ size: CGFloat, _ weight: UIFont.Weight, _ style: UIFont.TextStyle) -> Font {
        Font(UIFontMetrics(forTextStyle: style).scaledFont(for: UIFont.systemFont(ofSize: size, weight: weight)))
    }
    static let radius: CGFloat = 14

    /// The pillow shadow under raised objects (cards, clip cards).
    static let shadow = Color(hex: 0x15221B).opacity(0.12)
}

extension Color {
    init(hex: UInt32) { self.init(uiColor: UIColor(hex: hex)) }
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

/// A sentence-case label in the platform face: field names, section labels, small headings over a group.
struct FieldLabel: View {
    let text: String
    var color: Color = Theme.ink2
    var size: CGFloat = 13
    var body: some View {
        Text(text).font(Theme.text(size, weight: .semibold)).foregroundColor(color)
    }
}

struct Hairline: View {
    var color: Color = Theme.rule
    var body: some View { Rectangle().fill(color).frame(height: 1).accessibilityHidden(true) }
}

// The ink capsule button, `InkButtonStyle`, is in Press.swift with the other touch and loading styles.

/// A raised field with rounded corners and a clear focus boundary.
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
