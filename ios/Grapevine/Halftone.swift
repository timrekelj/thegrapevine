import UIKit
import CoreImage
import CoreImage.CIFilterBuiltins

/// The halftone plate: a photograph printed in colour through a fine dot screen, as on the web.
/// The photo shows only through dots whose size follows the tone of the picture underneath —
/// big dots in the shadows on newsprint, big dots in the highlights on the evening edition's black paper.
enum Halftone {
    /// Dot pitch on screen, in points.
    static let pitch: CGFloat = 2.8
    /// Screen angle, as on a printing press.
    private static let angle: CGFloat = 28 * .pi / 180

    private static let cache = NSCache<NSString, UIImage>()
    private static let context = CIContext()

    static func cached(_ key: String) -> UIImage? { cache.object(forKey: key as NSString) }

    /// Prints `image` to fill `size` points at `scale`. Pure CPU work: call it off the main thread.
    static func print(_ image: UIImage, size: CGSize, scale: CGFloat, dark: Bool, key: String) -> UIImage? {
        if let hit = cache.object(forKey: key as NSString) { return hit }
        guard size.width > 0, size.height > 0, let source = image.cgImage else { return nil }
        let width = Int(size.width * scale), height = Int(size.height * scale)
        guard width > 0, height > 0 else { return nil }

        // The colour photo, a little richer to make up for the paper between the dots.
        let boosted = CIFilter.colorControls()
        boosted.inputImage = CIImage(cgImage: source)
        boosted.saturation = 1.3
        boosted.contrast = 1.05
        boosted.brightness = dark ? 0.06 : 0
        let photo = boosted.outputImage.flatMap { context.createCGImage($0, from: $0.extent) } ?? source

        // Aspect-fill rectangle for the photo in the output.
        let imageSize = CGSize(width: photo.width, height: photo.height)
        let fill = max(CGFloat(width) / imageSize.width, CGFloat(height) / imageSize.height)
        let drawn = CGRect(x: (CGFloat(width) - imageSize.width * fill) / 2, y: (CGFloat(height) - imageSize.height * fill) / 2,
                           width: imageSize.width * fill, height: imageSize.height * fill)

        // The tone of each cell, from a small grey copy of the filled frame.
        let cell = pitch * scale
        let columns = max(1, Int((CGFloat(width) / cell).rounded(.up)))
        let rows = max(1, Int((CGFloat(height) / cell).rounded(.up)))
        var tones = [UInt8](repeating: 255, count: columns * rows)
        tones.withUnsafeMutableBytes { buffer in
            guard let grey = CGContext(data: buffer.baseAddress, width: columns, height: rows, bitsPerComponent: 8, bytesPerRow: columns,
                                       space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGImageAlphaInfo.none.rawValue) else { return }
            grey.interpolationQuality = .medium
            let shrink = CGFloat(columns) / CGFloat(width)
            grey.draw(photo, in: CGRect(x: drawn.minX * shrink, y: drawn.minY * shrink, width: drawn.width * shrink, height: drawn.height * shrink))
        }
        func tone(_ x: CGFloat, _ y: CGFloat) -> CGFloat {
            // CGContext rows run bottom-up; the output is drawn in the same space, so no flip.
            let column = min(columns - 1, max(0, Int(x / cell)))
            let row = min(rows - 1, max(0, Int(y / cell)))
            return CGFloat(tones[(rows - 1 - row) * columns + column]) / 255
        }

        // The screen: a rotated lattice of dots, sized by coverage.
        let dots = CGMutablePath()
        let cosA = cos(angle), sinA = sin(angle)
        let reach = (CGFloat(width) + CGFloat(height)) / cell + 2
        let centre = CGPoint(x: CGFloat(width) / 2, y: CGFloat(height) / 2)
        let maxRadius = cell * 0.64
        var i = -reach
        while i <= reach {
            var j = -reach
            while j <= reach {
                let x = centre.x + (i * cosA - j * sinA) * cell
                let y = centre.y + (i * sinA + j * cosA) * cell
                if x > -cell, y > -cell, x < CGFloat(width) + cell, y < CGFloat(height) + cell {
                    let lightness = tone(x, y)
                    let coverage = dark ? pow(lightness, 0.75) : 1 - lightness
                    let radius = maxRadius * sqrt(max(0, min(1, coverage)))
                    if radius > 0.25 * scale / 3 {
                        dots.addEllipse(in: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
                    }
                }
                j += 1
            }
            i += 1
        }

        guard let out = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                                  space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        let paper = UIColor(hex: dark ? 0x121110 : 0xF4F1EA).cgColor
        out.setFillColor(paper)
        out.fill(CGRect(x: 0, y: 0, width: width, height: height))
        out.saveGState()
        out.addPath(dots)
        out.clip()
        out.interpolationQuality = .high
        out.draw(photo, in: drawn)
        out.restoreGState()
        if !dark {
            // Newsprint tint: the paper shows through the ink.
            out.setBlendMode(.multiply)
            out.setFillColor(paper)
            out.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
        guard let printed = out.makeImage() else { return nil }
        let result = UIImage(cgImage: printed, scale: scale, orientation: .up)
        cache.setObject(result, forKey: key as NSString)
        return result
    }
}

/// Downloaded photographs, kept for the session so a plate can be reprinted at another size or appearance.
actor PhotoStore {
    static let shared = PhotoStore()
    private var images: [URL: UIImage] = [:]

    func image(_ url: URL) async throws -> UIImage {
        if let image = images[url] { return image }
        let (data, _) = try await URLSession.shared.data(from: url)
        guard let image = UIImage(data: data) else { throw URLError(.cannotDecodeContentData) }
        images[url] = image
        return image
    }
}
