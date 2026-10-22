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
    struct SubmitResult: Codable { let status: String; let story_id: UUID?; let story_title: String? }

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
            Text(result).font(Theme.serif(17)).padding(.vertical, 4)
        }
        Button { Task { await post() } } label: {
            Text(busy ? "Filing…" : "Share with your paper")
        }
        .buttonStyle(InkButtonStyle(enabled: !(busy || url.trimmed.isEmpty || group == nil)))
        .disabled(busy || url.trimmed.isEmpty || group == nil)
        .padding(.top, 6)
        if result != nil {
            Button("Done") { finish() }
                .font(.system(size: 15, weight: .medium)).underline().foregroundColor(Theme.ink)
                .frame(maxWidth: .infinity)
        }
    }

    private func finish() {
        if let close { close() } else { dismiss() }
    }

    private func post() async {
        guard let group else { return }
        busy = true
        editing = false
        result = nil
        do {
            let submitted: SubmitResult = try await API.postObject(SubmitResult.self, path: "functions/v1/submit-source",
                body: SubmitBody(group_id: group.id, url: url.trimmed,
                                comment: comment.trimmed.isEmpty ? nil : comment.trimmed))
            let title = submitted.story_title.flatMap { $0.isEmpty ? nil : $0 } ?? "the story"
            switch submitted.status {
            case "duplicate":
                result = "Your contribution was not added because it was a duplicate."
            case "joined":
                result = "Filed under \(title)."
            case "new_story":
                result = "New story: \(title)."
            default:
                result = "Posted."
            }
        } catch {
            result = error.localizedDescription
        }
        busy = false
    }
}
