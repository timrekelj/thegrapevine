import SwiftUI

struct ShareView: View {
    @Environment(AppState.self) private var app
    @Environment(\.dismiss) private var dismiss
    @State private var url = ""
    @State private var comment = ""
    @State private var busy = false
    @State private var result: String?
    @FocusState private var editing: Bool

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
                    .opacity(busy || url.trimmed.isEmpty ? 0.4 : 1)
                    .disabled(busy || url.trimmed.isEmpty)
                    if result != nil { Button("Done") { dismiss() } }
                }
                .padding(24)
            }
            .background(Theme.paper)
            .navigationTitle("Share a link")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .presentationDetents([.height(580), .large])
        .presentationDragIndicator(.visible)
    }

    private func post() async {
        guard let group = app.group else { return }
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
