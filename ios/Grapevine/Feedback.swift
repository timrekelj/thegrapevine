import SwiftUI
import UIKit
import ImageIO

// MARK: - Reports to the developer
// "Report a problem or idea": a bug, an improvement or a feature request, with screenshots, filed by
// `functions/v1/submit-feedback` as a public GitHub issue. Opened from Profile, by shaking the phone,
// or from an error's "Report" link.

enum FeedbackKind: String, CaseIterable, Identifiable {
    case bug, enhancement, feature

    var id: String { rawValue }

    var label: String {
        switch self {
        case .bug: "Bug"
        case .enhancement: "Improvement"
        case .feature: "New feature"
        }
    }

    var question: String {
        switch self {
        case .bug: "What happened?"
        case .enhancement: "What could be better?"
        case .feature: "What would you like?"
        }
    }

    var placeholder: String {
        switch self {
        case .bug: "What did you do, what happened, and what did you expect instead?"
        case .enhancement: "What works, but could work better — and how?"
        case .feature: "What would you like the paper to do, and what would it help with?"
        }
    }
}

/// How the report form opens: its kind, where the reader was, the error they saw, a screenshot to attach.
struct FeedbackRequest {
    var kind: FeedbackKind = .bug
    var screen: String?
    var error: String?
    var screenshot: UIImage?
}

/// The device facts sent with every report; the server shows them in the issue's context table.
struct FeedbackContext: Encodable, Equatable {
    var platform = "ios"
    let app_version: String
    let os: String
    let device: String
    var screen: String?
    var error: String?

    @MainActor static func current(screen: String?, error: String?) -> FeedbackContext {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return FeedbackContext(app_version: "\(version) (\(build))", os: "iOS \(UIDevice.current.systemVersion)",
                               device: deviceModel, screen: screen, error: error)
    }

    /// The model identifier, e.g. "iPhone17,1"; on the simulator, the phone it simulates.
    private static var deviceModel: String {
        if let simulated = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] { return simulated }
        var system = utsname()
        uname(&system)
        return withUnsafeBytes(of: system.machine) { String(decoding: $0.prefix(while: { $0 != 0 }), as: UTF8.self) }
    }
}

struct FeedbackBody: Encodable {
    let kind: String
    let description: String
    let images: [String]
    let context: FeedbackContext
}

struct FeedbackResult: Decodable, Equatable {
    let id: String
    let issue_number: Int?
    let issue_url: String?
}

/// A screenshot ready to upload: always a JPEG no larger than 2048 px on its longest side, so a
/// HEIC photo or a 3x screen capture never goes up as it is.
struct FeedbackImage: Identifiable {
    let id = UUID()
    let jpeg: Data
    let preview: UIImage

    static let maxSide: CGFloat = 2048
    static let quality: CGFloat = 0.8
    static let limit = 5

    /// The storage path the server expects: `{uid}/{uuid}.jpg`, lowercase.
    func path(for userId: UUID) -> String {
        "\(userId.uuidString.lowercased())/\(id.uuidString.lowercased()).jpg"
    }

    init?(jpeg: Data) {
        guard let preview = UIImage(data: jpeg) else { return nil }
        self.jpeg = jpeg
        self.preview = preview
    }

    /// `size` scaled down, never up, so its longest side is at most `maxSide`.
    static func fittedSize(_ size: CGSize, maxSide: CGFloat = maxSide) -> CGSize {
        let longest = max(size.width, size.height)
        guard longest > maxSide else { return size }
        let scale = maxSide / longest
        return CGSize(width: (size.width * scale).rounded(), height: (size.height * scale).rounded())
    }

    /// A picked photo in any format ImageIO reads (HEIC, PNG, …), upright and downscaled, as JPEG.
    static func jpeg(from data: Data) -> Data? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                  kCGImageSourceCreateThumbnailFromImageAlways: true,
                  kCGImageSourceCreateThumbnailWithTransform: true,
                  kCGImageSourceThumbnailMaxPixelSize: maxSide,
              ] as CFDictionary) else { return nil }
        return UIImage(cgImage: image).jpegData(compressionQuality: quality)
    }

    /// A drawn image (a screenshot), downscaled in pixels, as JPEG.
    static func jpeg(from image: UIImage) -> Data {
        let size = fittedSize(CGSize(width: image.size.width * image.scale, height: image.size.height * image.scale))
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: size, format: format).jpegData(withCompressionQuality: quality) { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
    }
}

/// The one presenter for the report form. It is presented over whatever is on top — the paper, the
/// share sheet, the corrections sheet — which a SwiftUI sheet at the root could not do.
@MainActor
enum Feedback {
    private final class Host: UIHostingController<FeedbackView> {}

    private static var keyWindow: UIWindow? {
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows).first(where: \.isKeyWindow)
    }

    /// Opens the form, signed in and not already open.
    static func present(_ request: FeedbackRequest) {
        guard API.session != nil, var top = keyWindow?.rootViewController else { return }
        while let next = top.presentedViewController {
            if next is Host { return }
            top = next
        }
        weak var presented: Host?
        let host = Host(rootView: FeedbackView(request: request, close: { presented?.dismiss(animated: true) }))
        presented = host
        host.view.backgroundColor = UIColor(Theme.paper)
        host.sheetPresentationController?.prefersGrabberVisible = true
        top.present(host, animated: true)
    }

    /// A shake: the screen as it is, before the form covers it, attached to a bug report.
    static func presentForShake() {
        guard API.session != nil, let window = keyWindow else { return }
        var top = window.rootViewController
        while let next = top?.presentedViewController {
            if next is Host { return }
            top = next
        }
        let screenshot = UIGraphicsImageRenderer(bounds: window.bounds).image { _ in
            window.drawHierarchy(in: window.bounds, afterScreenUpdates: false)
        }
        present(FeedbackRequest(kind: .bug, screen: "Shake", screenshot: screenshot))
    }
}

extension Notification.Name {
    /// The phone was shaken; the root view opens a bug report with a screenshot.
    static let deviceDidShake = Notification.Name("GrapevineDeviceDidShake")
}

extension UIWindow {
    open override func motionEnded(_ motion: UIEvent.EventSubtype, with event: UIEvent?) {
        if motion == .motionShake { NotificationCenter.default.post(name: .deviceDidShake, object: nil) }
        super.motionEnded(motion, with: event)
    }
}
