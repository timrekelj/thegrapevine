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
                VStack(alignment: .leading, spacing: 16) {
                    Text("Found a good read?")
                        .font(Theme.display(24)).fixedSize(horizontal: false, vertical: true)
                    Text("Share it with your paper.")
                        .font(.system(size: 15)).lineSpacing(3).foregroundColor(Theme.ink2)
                    if let notice {
                        Text(notice).font(.system(size: 15, weight: .semibold))
                    } else {
                        form
                    }
                }
                .padding(24)
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
        Text("LINK").font(.system(size: 11, weight: .semibold)).foregroundColor(Theme.ink2)
        TextField("https://…", text: $url)
            .focused($editing)
            .keyboardType(.URL)
            .textInputAutocapitalization(.never).autocorrectionDisabled()
            .textContentType(.URL)
            .padding(16).background(Theme.raised, in: RoundedRectangle(cornerRadius: 14))
        Text("YOUR COMMENT").font(.system(size: 11, weight: .semibold)).foregroundColor(Theme.ink2)
        TextField("Add a thought (optional)", text: $comment, axis: .vertical)
            .focused($editing).lineLimit(2...4)
            .padding(16).background(Theme.raised, in: RoundedRectangle(cornerRadius: 14))
        if let result {
            Text(result).font(.system(size: 15, weight: .semibold))
        }
        Button { Task { await post() } } label: {
            Text(busy ? "Posting…" : "Share with your paper")
                .font(.system(size: 16, weight: .semibold))
                .frame(maxWidth: .infinity, minHeight: 48)
        }
        .buttonStyle(.plain)
        .background(Theme.ink, in: Capsule()).foregroundColor(Theme.paper)
        .opacity(busy || url.trimmed.isEmpty || group == nil ? 0.4 : 1)
        .disabled(busy || url.trimmed.isEmpty || group == nil)
        if result != nil { Button("Done") { finish() } }
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
            var story: Story?
            if let id = submitted.story_id {
                story = try? await API.getOne(Story.self, path: "rest/v1/stories",
                                       query: [URLQueryItem(name: "id", value: "eq.\(id)")])
            }
            let title = story?.title ?? "the story"
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
