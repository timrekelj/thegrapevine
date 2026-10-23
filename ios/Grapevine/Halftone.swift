import UIKit
import CoreImage
import CoreImage.CIFilterBuiltins
import ImageIO

/// The halftone plate: a photograph printed in colour through a fine dot screen, as on the web.
/// The photo shows only through dots whose size follows the tone of the picture underneath —
/// big dots in the shadows on newsprint, big dots in the highlights on the evening edition's black paper.
enum Halftone {
    /// Dot pitch on screen, in points.
    static let pitch: CGFloat = 2.8
    /// Screen angle, as on a printing press.
    private static let angle: CGFloat = 28 * .pi / 180

    /// Printed plates, bounded by their decoded size so a long session can't grow without limit.
    /// NSCache is thread-safe; plates are printed off the main thread.
    nonisolated(unsafe) private static let cache: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.totalCostLimit = 48 * 1024 * 1024
        return cache
    }()
    private static let context = CIContext()

    static func cached(_ key: String) -> UIImage? { cache.object(forKey: key as NSString) }

    /// Prints `image` to fill `size` points at `scale`. Pure CPU work: call it off the main thread.
    static func print(_ image: UIImage, size: CGSize, scale: CGFloat, dark: Bool, key: String) -> UIImage? {
        if let hit = cache.object(forKey: key as NSString) { return hit }
        guard size.width > 0, size.height > 0, let source = image.cgImage else { return nil }
        let width = Int(size.width * scale), height = Int(size.height * scale)
        guard width > 0, height > 0 else { return nil }

        // Aspect-fill rectangle for the photo in the output.
        let fill = max(CGFloat(width) / CGFloat(source.width), CGFloat(height) / CGFloat(source.height))
        let drawn = CGRect(x: (CGFloat(width) - CGFloat(source.width) * fill) / 2, y: (CGFloat(height) - CGFloat(source.height) * fill) / 2,
                           width: CGFloat(source.width) * fill, height: CGFloat(source.height) * fill)

        // The colour photo at its printed size, a little richer to make up for the paper between the dots.
        // Scaling first keeps the filter and the drawing proportional to the plate rather than the download.
        var scaled = source
        if fill < 1, let shrunk = CGContext(data: nil, width: Int(drawn.width.rounded()), height: Int(drawn.height.rounded()), bitsPerComponent: 8,
                                            bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) {
            shrunk.interpolationQuality = .high
            shrunk.draw(source, in: CGRect(x: 0, y: 0, width: shrunk.width, height: shrunk.height))
            scaled = shrunk.makeImage() ?? source
        }
        let boosted = CIFilter.colorControls()
        boosted.inputImage = CIImage(cgImage: scaled)
        boosted.saturation = 1.3
        boosted.contrast = 1.05
        boosted.brightness = dark ? 0.06 : 0
        let photo = boosted.outputImage.flatMap { context.createCGImage($0, from: $0.extent) } ?? scaled

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

        // The screen: a rotated lattice of dots, sized by coverage. Point (i, j) of the lattice sits at
        // centre + R(angle) · (i − reach, j − reach) · cell.
        let cosA = cos(angle), sinA = sin(angle)
        let reach = (CGFloat(width) + CGFloat(height)) / cell + 2
        let side = Int(2 * reach) + 1
        let centre = CGPoint(x: CGFloat(width) / 2, y: CGFloat(height) / 2)
        let maxRadius = cell * 0.64
        var radii = [Float](repeating: 0, count: side * side)
        for i in 0..<side {
            for j in 0..<side {
                let u = CGFloat(i) - reach, v = CGFloat(j) - reach
                let x = centre.x + (u * cosA - v * sinA) * cell
                let y = centre.y + (u * sinA + v * cosA) * cell
                guard x > -cell, y > -cell, x < CGFloat(width) + cell, y < CGFloat(height) + cell else { continue }
                let lightness = tone(x, y)
                let coverage = dark ? pow(lightness, 0.75) : 1 - lightness
                let radius = maxRadius * sqrt(max(0, min(1, coverage)))
                if radius > 0.25 * scale / 3 { radii[i * side + j] = Float(radius) }
            }
        }

        // The dots as an anti-aliased coverage mask, one byte per pixel. Each pixel can only fall in the
        // dots of the four lattice points around it (a dot is at most 0.64 cells across its centre).
        // Computing it directly is far cheaper than clipping to a path of thousands of ellipses.
        var mask = [UInt8](repeating: 0, count: width * height)
        let cellF = Float(cell), reachF = Float(reach), cosF = Float(cosA), sinF = Float(sinA)
        let cx = Float(centre.x), cy = Float(centre.y)
        radii.withUnsafeBufferPointer { radii in
            mask.withUnsafeMutableBufferPointer { mask in
                for row in 0..<height {
                    // Memory rows run top-down; CG y runs bottom-up.
                    let dy = Float(height - row) - 0.5 - cy
                    for column in 0..<width {
                        let dx = Float(column) + 0.5 - cx
                        let u = (dx * cosF + dy * sinF) / cellF + reachF
                        let v = (-dx * sinF + dy * cosF) / cellF + reachF
                        let i0 = Int(u.rounded(.down)), j0 = Int(v.rounded(.down))
                        var best: Float = 0
                        for i in i0...(i0 + 1) where i >= 0 && i < side {
                            for j in j0...(j0 + 1) where j >= 0 && j < side {
                                let radius = radii[i * side + j]
                                guard radius > 0 else { continue }
                                let du = u - Float(i), dv = v - Float(j)
                                let distance = (du * du + dv * dv).squareRoot() * cellF
                                best = max(best, min(1, radius - distance + 0.5))
                            }
                        }
                        if best > 0 { mask[row * width + column] = UInt8(best * 255) }
                    }
                }
            }
        }
        guard let provider = CGDataProvider(data: Data(mask) as CFData),
              let dots = CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: width,
                                 space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.none.rawValue),
                                 provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent) else { return nil }

        guard let out = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                                  space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        let paper = Theme.paper(dark: dark).cgColor
        let bounds = CGRect(x: 0, y: 0, width: width, height: height)
        out.setFillColor(paper)
        out.fill(bounds)
        out.saveGState()
        out.clip(to: bounds, mask: dots)
        out.interpolationQuality = .high
        out.draw(photo, in: drawn)
        out.restoreGState()
        if !dark {
            // Newsprint tint: the paper shows through the ink.
            out.setBlendMode(.multiply)
            out.setFillColor(paper)
            out.fill(bounds)
        }
        guard let printed = out.makeImage() else { return nil }
        let result = UIImage(cgImage: printed, scale: scale, orientation: .up)
        cache.setObject(result, forKey: key as NSString, cost: printed.bytesPerRow * printed.height)
        return result
    }
}

/// Downloaded photographs and avatars, kept so a plate can be reprinted at another size or appearance.
/// Requests for the same URL share one download; images are decoded no larger than any plate needs,
/// and the cache is bounded by decoded size.
actor PhotoStore {
    static let shared = PhotoStore()
    /// The widest a plate is printed (a full-width plate at 3x), with room for aspect-fill crops.
    private static let maxPixelSize = 1600

    /// NSCache is thread-safe, so `cached(_:)` can read it without hopping onto the actor.
    nonisolated(unsafe) private let images: NSCache<NSURL, UIImage> = {
        let cache = NSCache<NSURL, UIImage>()
        cache.totalCostLimit = 64 * 1024 * 1024
        return cache
    }()
    private var loading: [URL: Task<UIImage, Error>] = [:]

    /// Already downloaded and decoded, for drawing without a placeholder.
    nonisolated func cached(_ url: URL) -> UIImage? { images.object(forKey: url as NSURL) }

    func image(_ url: URL) async throws -> UIImage {
        if let image = images.object(forKey: url as NSURL) { return image }
        // Image URLs come from shared pages; never let one read a local file or another scheme.
        guard ["http", "https"].contains(url.scheme?.lowercased() ?? "") else { throw URLError(.unsupportedURL) }
        if let task = loading[url] { return try await task.value }
        let task = Task.detached(priority: .userInitiated) {
            let (data, _) = try await URLSession.shared.data(from: url)
            return try Self.decode(data)
        }
        loading[url] = task
        defer { loading[url] = nil }
        let image = try await task.value
        images.setObject(image, forKey: url as NSURL, cost: image.cgImage.map { $0.bytesPerRow * $0.height } ?? 0)
        return image
    }

    /// Decodes now (not lazily at first draw) and downsamples anything larger than a plate needs.
    private static func decode(_ data: Data) throws -> UIImage {
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSize,
        ]
        guard let source = CGImageSourceCreateWithData(data as CFData, [kCGImageSourceShouldCache: false] as CFDictionary),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            throw URLError(.cannotDecodeContentData)
        }
        return UIImage(cgImage: image)
    }
}
