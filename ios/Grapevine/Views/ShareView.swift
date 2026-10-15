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
            Form {
                Section("Link") {
                    TextField("https://…", text: $url)
                        .focused($editing)
                        .keyboardType(.URL)
                        .autocapitalization(.none)
                        .textContentType(.URL)
                }
                Section("Your comment") {
                    TextField("Optional", text: $comment)
                        .focused($editing)
                }
                if let result {
                    Section {
                        Text(result).font(.system(size: 15, weight: .semibold))
                    }
                }
                Section {
                    Button(busy ? "Posting…" : "Post") { Task { await post() } }
                        .disabled(busy || url.trimmed.isEmpty)
                    if result != nil {
                        Button("Done") { dismiss() }
                    }
                }
            }
            .navigationTitle("Share a link")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
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
