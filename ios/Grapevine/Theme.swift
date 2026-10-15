import SwiftUI
import CoreImage
import CoreImage.CIFilterBuiltins

enum Theme {
    static let paper = Color(hex: 0xEFEEE9)
    static let raised = Color(hex: 0xF8F7F3)
    static let ink = Color(hex: 0x141414)
    static let ink2 = Color(hex: 0x4A4A47)
    static let pink = Color(hex: 0xFF6AA6)
    static let onPink = Color(hex: 0x141414)
    static let rule = Color(hex: 0x141414).opacity(0.28)

    /// Section colours; same palette and pick as web/lib/category.ts so a category matches on both.
    static let sections: [Color] = [0xB3261E, 0x1F5AA6, 0x2E6B3A, 0x8A5A00, 0x6B3FA0, 0x00687A, 0xB04A12].map { Color(hex: $0) }
    static func section(_ name: String) -> Color { sections[name.utf16.reduce(0) { $0 + Int($1) } % sections.count] }

    static func display(_ size: CGFloat) -> Font { .custom("Gloock", size: size) }
}

extension Color {
    init(hex: UInt32) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

/// The halftone plate: every source image is printed in one ink, not shown in colour.
enum Plate {
    static func apply(to image: UIImage) -> UIImage {
        guard let cgImage = image.cgImage else { return image }
        let filter = CIFilter.colorControls()
        filter.inputImage = CIImage(cgImage: cgImage)
        filter.saturation = 0
        filter.contrast = 1.15
        filter.brightness = 0.04
        guard let output = filter.outputImage,
              let printed = CIContext().createCGImage(output, from: output.extent) else { return image }
        return UIImage(cgImage: printed)
    }
}
