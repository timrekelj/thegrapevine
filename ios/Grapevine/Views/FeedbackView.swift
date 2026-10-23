import SwiftUI
import PhotosUI

/// "Report a problem or idea": a letter to the editor, filed as a public GitHub issue.
/// Presented by `Feedback.present`; `close` dismisses it.
struct FeedbackView: View {
    let close: () -> Void
    private let screen: String?
    private let errorShown: String?

    @State private var kind: FeedbackKind
    @State private var text = ""
    @State private var images: [FeedbackImage]
    @State private var picked: [PhotosPickerItem] = []
    @State private var loadingImages = false
    /// Screenshots already uploaded, so a retry after a failed send doesn't upload them again.
    @State private var uploaded: Set<UUID> = []
    @State private var busy = false
    @State private var error: String?
    @State private var filed: FeedbackResult?
    @FocusState private var writing: Bool

    init(request: FeedbackRequest, close: @escaping () -> Void) {
        self.close = close
        screen = request.screen
        errorShown = request.error
        _kind = State(initialValue: request.kind)
        _images = State(initialValue: request.screenshot.flatMap { FeedbackImage(jpeg: FeedbackImage.jpeg(from: $0)) }.map { [$0] } ?? [])
    }

    private var canSend: Bool {
        !busy && !loadingImages && !text.trimmed.isEmpty && text.trimmed.count <= 4000
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 6) {
                        Kicker(text: "Letters to the editor", color: Theme.ink2)
                        Text("Report a problem or idea")
                            .font(Theme.headline(32)).fixedSize(horizontal: false, vertical: true)
                            .accessibilityAddTraits(.isHeader)
                        Text("Tell us what went wrong, or what would make the paper better.")
                            .font(Theme.serif(17)).foregroundColor(Theme.ink2)
                    }
                    DoubleRule().padding(.bottom, 6)
                    if let filed {
                        thanks(filed).arrives()
                    } else {
                        form
                    }
                }
                .padding(.horizontal, 24).padding(.vertical, 16)
                .animation(.snappy(duration: 0.3), value: error)
                .animation(.snappy(duration: 0.3), value: filed)
                .animation(.snappy(duration: 0.25), value: images.map(\.id))
            }
            .scrollDismissesKeyboard(.interactively)
            .sensoryFeedback(.success, trigger: filed) { _, now in now != nil }
            .sensoryFeedback(.error, trigger: error) { _, now in now != nil }
            .background(Theme.paper)
            .foregroundColor(Theme.ink)
            .tint(Theme.ink)
            .navigationTitle("Send feedback")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if filed == nil { Button("Cancel") { close() }.disabled(busy) }
                }
            }
        }
        // Swiping away a half-written report would lose it.
        .interactiveDismissDisabled(busy || (filed == nil && !text.trimmed.isEmpty))
    }

    // MARK: - Form

    @ViewBuilder private var form: some View {
        Kicker(text: "What kind?")
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 8) { kindButtons }
            VStack(spacing: 8) { kindButtons }
        }
        .sensoryFeedback(.selection, trigger: kind)

        Kicker(text: kind.question).padding(.top, 8)
        TextEditor(text: $text)
            .focused($writing)
            .font(Theme.serif(18))
            .scrollContentBackground(.hidden)
            .padding(.horizontal, 9).padding(.vertical, 8)
            .frame(minHeight: 160)
            .background(alignment: .topLeading) {
                if text.isEmpty {
                    Text(kind.placeholder)
                        .font(Theme.serif(18)).foregroundColor(Theme.ink3)
                        .padding(.horizontal, 14).padding(.vertical, 16)
                        .accessibilityHidden(true)
                }
            }
            .background(Theme.raised, in: RoundedRectangle(cornerRadius: Theme.radius))
            .overlay(RoundedRectangle(cornerRadius: Theme.radius)
                .stroke(text.trimmed.count > 4000 ? Theme.danger : writing ? Theme.ink : Theme.rule, lineWidth: 1))
            .disabled(busy)
            .accessibilityLabel(kind.question)
            .accessibilityHint(kind.placeholder)
        if text.trimmed.count > 3600 {
            Text("\(text.trimmed.count) / 4000 characters")
                .font(Theme.mono(11)).foregroundColor(text.trimmed.count > 4000 ? Theme.danger : Theme.ink3)
        }

        if let errorShown {
            VStack(alignment: .leading, spacing: 4) {
                Kicker(text: "The error you saw", color: Theme.ink2, size: 10)
                Text(errorShown).font(Theme.mono(12)).foregroundColor(Theme.ink2)
            }
            .padding(10).frame(maxWidth: .infinity, alignment: .leading)
            .clipping()
            .accessibilityElement(children: .combine)
        }

        HStack(spacing: 6) {
            Kicker(text: "Screenshots")
            Text("(optional, up to \(FeedbackImage.limit))").font(Theme.mono(11)).foregroundColor(Theme.ink3)
        }
        .padding(.top, 8)
        screenshots

        Text("Reports are posted publicly on GitHub so we can track them. Leave out anything private. Your app version, iOS version and phone model are included.")
            .font(Theme.mono(11)).foregroundColor(Theme.ink3)
            .fixedSize(horizontal: false, vertical: true)
        if let error {
            Text(error).font(.system(size: 13, weight: .medium)).foregroundColor(Theme.danger).arrives()
        }
        Button { Task { await send() } } label: {
            Text("Send report")
        }
        .buttonStyle(InkButtonStyle(enabled: canSend, busy: busy))
        .disabled(!canSend)
        .busyAccessibility(busy, "Sending…")
        .padding(.top, 6)
    }

    private var kindButtons: some View {
        ForEach(FeedbackKind.allCases) { option in
            let selected = option == kind
            Button { kind = option } label: {
                Text(option.label)
                    .font(.system(size: 15, weight: selected ? .semibold : .regular))
                    .foregroundColor(selected ? Theme.paper : Theme.ink)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(selected ? Theme.ink : Theme.raised, in: RoundedRectangle(cornerRadius: Theme.radius))
                    .overlay(RoundedRectangle(cornerRadius: Theme.radius).stroke(selected ? Theme.ink : Theme.rule, lineWidth: 1))
                    .contentShape(Rectangle())
            }
            .buttonStyle(PressStyle(scale: 0.96))
            .disabled(busy)
            .accessibilityAddTraits(selected ? .isSelected : [])
        }
    }

    /// The attached screenshots, each with a remove button, then a tile to add more.
    private var screenshots: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(Array(images.enumerated()), id: \.element.id) { index, image in
                    Image(uiImage: image.preview)
                        .resizable().scaledToFill()
                        .frame(width: 76, height: 96).clipped()
                        .overlay(Rectangle().stroke(Theme.rule, lineWidth: 1))
                        .overlay(alignment: .topTrailing) {
                            Button { images.removeAll { $0.id == image.id } } label: {
                                Image(systemName: "xmark")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(Theme.paper)
                                    .frame(width: 22, height: 22)
                                    .background(Theme.ink, in: Circle())
                                    .frame(width: 44, height: 44)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(PressStyle(scale: 0.9))
                            .offset(x: 10, y: -10)
                            .disabled(busy)
                            .accessibilityLabel("Remove screenshot \(index + 1)")
                        }
                        .accessibilityElement(children: .contain)
                        .accessibilityLabel("Screenshot \(index + 1)")
                        .transition(.opacity.combined(with: .scale(scale: 0.9)))
                }
                if images.count < FeedbackImage.limit {
                    // PhotosPicker's label closure isn't main-actor isolated, so it reads a copy.
                    let loadingImages = loadingImages
                    PhotosPicker(selection: $picked, maxSelectionCount: FeedbackImage.limit - images.count, matching: .images) {
                        SwiftUI.Group {
                            if loadingImages {
                                InkDots(size: 5)
                            } else {
                                Image(systemName: "plus").font(.system(size: 18, weight: .medium))
                            }
                        }
                        .frame(width: 76, height: 96)
                        .overlay(Rectangle().stroke(Theme.ink3, style: StrokeStyle(lineWidth: 1, dash: [3, 3])))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PressStyle(scale: 0.94))
                    .foregroundColor(Theme.ink)
                    .disabled(busy || loadingImages)
                    .accessibilityLabel(loadingImages ? "Adding screenshots…" : "Add screenshots")
                }
            }
            // Room for the remove buttons that stand out past each thumbnail's corner.
            .padding(.top, 12).padding(.trailing, 12)
        }
        .onChange(of: picked) {
            guard !picked.isEmpty else { return }
            let items = picked
            picked = []
            Task { await add(items) }
        }
    }

    // MARK: - Filed

    @ViewBuilder private func thanks(_ result: FeedbackResult) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            if let number = result.issue_number {
                Text("Thanks — filed as #\(number)").font(Theme.headline(26))
            } else {
                Text("Thanks — we've got it.").font(Theme.headline(26))
            }
            Text(kind == .bug ? "We'll look into it." : "We read every one.")
                .font(Theme.serif(17)).foregroundColor(Theme.ink2)
            if let link = result.issue_url.flatMap(URL.init(string:)) {
                Link(destination: link) {
                    Label("Follow it on GitHub", systemImage: "arrow.up.forward.square")
                        .font(.system(size: 15, weight: .medium)).frame(minHeight: 44)
                }
            }
            Button("Done") { close() }
                .buttonStyle(InkButtonStyle())
                .padding(.top, 6)
        }
        .accessibilityElement(children: .contain)
    }

    // MARK: - Work

    private func add(_ items: [PhotosPickerItem]) async {
        loadingImages = true
        error = nil
        defer { loadingImages = false }
        for item in items {
            guard images.count < FeedbackImage.limit else { break }
            do {
                guard let data = try await item.loadTransferable(type: Data.self),
                      let jpeg = await Task.detached(priority: .userInitiated, operation: { FeedbackImage.jpeg(from: data) }).value,
                      let image = FeedbackImage(jpeg: jpeg) else { throw APIError.network("Could not read the selected image.") }
                images.append(image)
            } catch {
                self.error = error.localizedDescription
            }
        }
    }

    private func send() async {
        guard canSend, let session = API.session else { return }
        busy = true
        writing = false
        error = nil
        defer { busy = false }
        do {
            var paths: [String] = []
            for image in images {
                let path = image.path(for: session.userId)
                if !uploaded.contains(image.id) {
                    do {
                        try await API.upload(Attachment(data: image.jpeg, kind: "image", contentType: "image/jpeg", fileExtension: "jpg"),
                                             path: path, bucket: "feedback")
                    } catch APIError.http(409, _) { /* A retry may find the previous upload. */ }
                    uploaded.insert(image.id)
                }
                paths.append(path)
            }
            filed = try await API.postObject(FeedbackResult.self, path: "functions/v1/submit-feedback", body: FeedbackBody(
                kind: kind.rawValue, description: text.trimmed, images: paths,
                context: .current(screen: screen, error: errorShown)))
        } catch where error.isCancellation {
        } catch {
            self.error = "\(error.localizedDescription) Your report is kept; try Send again."
        }
    }
}

/// An error the reader can pass on: the message, then a "Report" link that opens the report form
/// with the message filled in.
struct ErrorNote: View {
    let message: String
    /// Where the reader was, for the report's context.
    var screen: String?
    var font: Font = .caption
    var color: Color = Theme.danger

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(message).font(font).foregroundColor(color)
                .fixedSize(horizontal: false, vertical: true)
            Button {
                Feedback.present(FeedbackRequest(kind: .bug, screen: screen, error: message))
            } label: {
                Text("Report this problem")
                    .font(.system(size: 13, weight: .medium)).underline()
                    .foregroundColor(Theme.ink2)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(PressStyle())
            .accessibilityHint("Opens a bug report with this error filled in.")
        }
    }
}
