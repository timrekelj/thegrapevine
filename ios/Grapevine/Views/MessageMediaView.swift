import SwiftUI
import AVKit
import ImageIO

struct MessageMediaView: View {
    let media: Media
    @State private var url: URL?
    @State private var player: AVPlayer?
    @State private var error: String?

    var body: some View {
        SwiftUI.Group {
            if let player {
                VideoPlayer(player: player).frame(height: 220).clipShape(RoundedRectangle(cornerRadius: 14))
            } else if let url {
                if media.kind == "gif" {
                    GIFView(url: url).frame(height: 240).clipShape(RoundedRectangle(cornerRadius: 14))
                        .accessibilityLabel(media.alt_text ?? "Shared GIF")
                } else {
                    PlateView(url: url, label: "Attachment unavailable")
                        .aspectRatio(4 / 3, contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .frame(maxWidth: 440, alignment: .leading)
                        .accessibilityElement()
                        .accessibilityLabel(media.alt_text ?? "Shared image")
                        .accessibilityAddTraits(.isImage)
                }
            } else if let error {
                Text(error).font(.caption)
            } else {
                // A plain placeholder where the attachment will show.
                PlaceholderShape(radius: 14)
                    .aspectRatio(4 / 3, contentMode: .fit)
                    .frame(maxWidth: 440, alignment: .leading)
                    .accessibilityElement()
                    .accessibilityLabel("Loading attachment")
            }
        }
        .task(id: media.url) {
            do {
                let resolved = try await API.mediaURL(media.url)
                url = resolved
                if media.kind == "video" { player = AVPlayer(url: resolved) }
            } catch { self.error = error.localizedDescription }
        }
        .onDisappear { player?.pause() }
    }
}

/// A GIF, animated by ImageIO a frame at a time: no web view, and no decoding the whole animation up front.
private struct GIFView: UIViewRepresentable {
    let url: URL

    final class Coordinator {
        var url: URL?
        var task: Task<Void, Never>?
        /// Set to stop the animation that's playing.
        var stop: (() -> Void)?
    }
    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> UIImageView {
        let view = UIImageView()
        view.contentMode = .scaleAspectFit
        view.clipsToBounds = true
        // Take the frame SwiftUI gives, not the GIF's own size.
        for axis in [NSLayoutConstraint.Axis.horizontal, .vertical] {
            view.setContentHuggingPriority(.defaultLow, for: axis)
            view.setContentCompressionResistancePriority(.defaultLow, for: axis)
        }
        return view
    }

    func updateUIView(_ view: UIImageView, context: Context) {
        let coordinator = context.coordinator
        guard coordinator.url != url else { return }
        coordinator.url = url
        Self.dismantleUIView(view, coordinator: coordinator)
        let url = url
        coordinator.task = Task { @MainActor [weak view, weak coordinator] in
            // GIF links are member-supplied: only fetch from the web.
            guard ["http", "https"].contains(url.scheme?.lowercased() ?? ""),
                  let (data, _) = try? await URLSession.shared.data(from: url),
                  !Task.isCancelled, let coordinator else { return }
            var stopped = false
            coordinator.stop = { stopped = true }
            // ImageIO decodes each frame when it's due and calls back on the main queue.
            CGAnimateImageDataWithBlock(data as CFData, nil) { _, image, stop in
                guard !stopped, let view else { stop.pointee = true; return }
                view.image = UIImage(cgImage: image)
            }
        }
    }

    static func dismantleUIView(_ view: UIImageView, coordinator: Coordinator) {
        coordinator.task?.cancel()
        coordinator.stop?()
        coordinator.stop = nil
    }
}
