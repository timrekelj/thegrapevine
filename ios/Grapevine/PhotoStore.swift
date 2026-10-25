import UIKit
import ImageIO

/// Downloaded photographs, kept so a picture shown in several places (or scrolled back into view) draws at once.
/// Requests for the same URL share one download; images are decoded no larger than any frame needs,
/// and the cache is bounded by decoded size.
actor PhotoStore {
    static let shared = PhotoStore()
    /// The widest a photo is shown (full width at 3x), with room for aspect-fill crops.
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
    nonisolated func removeAll() { images.removeAllObjects() }

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

    /// Decodes now (not lazily at first draw) and downsamples anything larger than a frame needs.
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
