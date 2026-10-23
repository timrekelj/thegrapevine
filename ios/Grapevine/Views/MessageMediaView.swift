import SwiftUI
import AVKit
import WebKit

struct MessageMediaView: View {
    let media: Media
    @State private var url: URL?
    @State private var player: AVPlayer?
    @State private var error: String?
    @State private var reveal = false

    var body: some View {
        SwiftUI.Group {
            if let player {
                VideoPlayer(player: player).frame(height: 220)
            } else if let url {
                if media.kind == "gif" {
                    GIFView(url: url).frame(height: 240)
                        .accessibilityLabel(media.alt_text ?? "Shared GIF")
                } else {
                    // Printed through the screen like every photo on the web; a tap shows it clean (the web's hover).
                    PlateView(url: url, label: "Attachment unavailable", screen: !reveal)
                        .aspectRatio(4 / 3, contentMode: .fit)
                        .frame(maxWidth: 440, alignment: .leading)
                        .contentShape(Rectangle())
                        .onTapGesture { reveal.toggle() }
                        .accessibilityElement()
                        .accessibilityLabel(media.alt_text ?? "Shared image")
                        .accessibilityAddTraits(.isImage)
                }
            } else if let error {
                Text(error).font(.caption)
            } else { ProgressView() }
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

private struct GIFView: UIViewRepresentable {
    let url: URL

    final class Coordinator { var url: URL? }
    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.defaultWebpagePreferences.allowsContentJavaScript = false
        let view = WKWebView(frame: .zero, configuration: config)
        view.isOpaque = false
        view.backgroundColor = .clear
        view.scrollView.isScrollEnabled = false
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ view: WKWebView, context: Context) {
        guard context.coordinator.url != url else { return }
        context.coordinator.url = url
        let source = url.absoluteString.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "\"", with: "&quot;").replacingOccurrences(of: "<", with: "&lt;")
        view.loadHTMLString("""
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src http: https:; style-src 'unsafe-inline'">
        <style>html,body{margin:0;height:100%;background:transparent}img{width:100%;height:100%;object-fit:contain}</style>
        <img src="\(source)" alt="Shared GIF">
        """, baseURL: nil)
    }
}
