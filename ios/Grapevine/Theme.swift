import SwiftUI
import CoreImage
import CoreImage.CIFilterBuiltins

enum Theme {
    static let paper = Color(hex: 0xEFEEE9)
    static let raised = Color(hex: 0xF8F7F3)
    static let ink = Color(hex: 0x4A2160)
    static let ink2 = Color(hex: 0x6E5280)
    static let pink = Color(hex: 0xFF6AA6)
    static let onPink = Color(hex: 0x2E1340)
    static let rule = Color(hex: 0x4A2160).opacity(0.30)

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
        // Print in one ink: black becomes grape, white becomes paper.
        let ink = CIFilter.falseColor()
        ink.inputImage = filter.outputImage
        ink.color0 = CIColor(red: 0x4A / 255, green: 0x21 / 255, blue: 0x60 / 255)
        ink.color1 = CIColor(red: 0xEF / 255, green: 0xEE / 255, blue: 0xE9 / 255)
        guard let output = ink.outputImage,
              let printed = CIContext().createCGImage(output, from: output.extent) else { return image }
        return UIImage(cgImage: printed)
    }
}
