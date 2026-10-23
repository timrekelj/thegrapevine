import SwiftUI

/// Posts a link to the paper. Also shown over other apps by the share extension,
/// with the shared link filled in.
struct ShareView: View {
    let group: Group?
    /// Shown instead of the form when there's no paper to post to.
    var notice: String?
    /// Replaces dismissing the sheet; the share extension completes its request.
    var close: (() -> Void)?
    @Environment(\.dismiss) private var dismiss
    @State private var url: String
    @State private var comment = ""
    @State private var busy = false
    @State private var result: String?
    @FocusState private var editing: Bool

    init(group: Group?, url: String = "", notice: String? = nil, close: (() -> Void)? = nil) {
        self.group = group
        self.notice = notice
        self.close = close
        _url = State(initialValue: url)
    }

    struct SubmitBody: Encodable { let group_id: UUID; let url: String; let comment: String? }
    struct SubmitResult: Codable { let status: String; let story_id: UUID? }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 6) {
                        Kicker(text: "Filing for \(group?.name ?? "your paper")", color: Theme.ink2)
                        Text("Found a good read?")
                            .font(Theme.headline(32)).fixedSize(horizontal: false, vertical: true)
                        Text("Share it with your paper.")
                            .font(Theme.serif(17)).foregroundColor(Theme.ink2)
                    }
                    DoubleRule().padding(.bottom, 6)
                    if let notice {
                        Text(notice).font(Theme.serif(17))
                    } else {
                        form
                    }
                }
                .padding(.horizontal, 24).padding(.vertical, 16)
                .animation(.snappy(duration: 0.3), value: result)
            }
            .background(Theme.paper)
            .foregroundColor(Theme.ink)
            .tint(Theme.ink)
            .navigationTitle("Share a link")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { finish() }
                }
            }
        }
        .presentationDetents([.height(580), .large])
        .presentationDragIndicator(.visible)
    }

    @ViewBuilder private var form: some View {
        Kicker(text: "Link")
        TextField("https://…", text: $url)
            .focused($editing)
            .keyboardType(.URL)
            .textInputAutocapitalization(.never).autocorrectionDisabled()
            .textContentType(.URL)
            .paperField()
        HStack(spacing: 6) {
            Kicker(text: "Your comment")
            Text("(optional)").font(Theme.mono(11)).foregroundColor(Theme.ink3)
        }
        .padding(.top, 4)
        TextField("Add a thought", text: $comment, axis: .vertical)
            .focused($editing).lineLimit(2...4)
            .padding(.vertical, 12)
            .paperField()
        Text("Everyone in \(group?.name ?? "your paper") sees this with your name.")
            .font(Theme.mono(11)).foregroundColor(Theme.ink3)
        if let result {
            Text(result).font(Theme.serif(17)).padding(.vertical, 4).arrives()
        }
        Button { Task { await post() } } label: {
            Text("Share with your paper")
        }
        .buttonStyle(InkButtonStyle(enabled: !(busy || url.trimmed.isEmpty || group == nil), busy: busy))
        .disabled(busy || url.trimmed.isEmpty || group == nil)
        .busyAccessibility(busy, "Filing…")
        .padding(.top, 6)
        if result != nil {
            Button("Done") { finish() }
                .font(.system(size: 15, weight: .medium)).underline().foregroundColor(Theme.ink)
                .frame(maxWidth: .infinity, minHeight: 44)
                .buttonStyle(PressStyle())
                .arrives()
        }
    }

    private func finish() {
        if let close { close() } else { dismiss() }
    }

    /// The server accepts the link and files it in the background, then sends a push when it's
    /// on the paper. So the sheet closes right away, and the share extension returns to the app
    /// the link came from.
    private func post() async {
        guard let group else { return }
        busy = true
        editing = false
        result = nil
        do {
            let submitted: SubmitResult = try await API.postObject(SubmitResult.self, path: "functions/v1/submit-source",
                body: SubmitBody(group_id: group.id, url: url.trimmed,
                                comment: comment.trimmed.isEmpty ? nil : comment.trimmed))
            if submitted.status == "duplicate" {
                result = "Your contribution was not added because it was a duplicate."
            } else {
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                finish()
                return
            }
        } catch {
            result = error.localizedDescription
        }
        busy = false
    }
}
