import SwiftUI

struct LoginView: View {
    @Environment(AppState.self) private var app
    @State private var email = ""
    @State private var sent = false
    @State private var busy = false

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Text("The Grapevine").font(Theme.display(28))
            Text("A paper between friends.").font(Theme.display(17))
            Text("Sign in with your email. No password, no public feed.")
                .font(.system(size: 15)).foregroundColor(Theme.ink2)
                .multilineTextAlignment(.center)
            if sent {
                Text("Check your email for a sign-in link.")
                    .font(.system(size: 15, weight: .semibold))
                    .multilineTextAlignment(.center)
                Button("Request another link") { sent = false }
            } else {
                TextField("Your email", text: $email)
                    .keyboardType(.emailAddress)
                    .autocapitalization(.none)
                    .textContentType(.emailAddress)
                    .padding()
                    .background(Theme.raised)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                Button { Task { await send() } } label: {
                    Text(busy ? "Sending…" : "Send a sign-in link")
                        .frame(maxWidth: .infinity).frame(height: 48)
                }
                .buttonStyle(.plain)
                .background(Theme.ink)
                .foregroundColor(Theme.paper)
                .clipShape(RoundedRectangle(cornerRadius: 24))
                .disabled(busy || email.trimmed.isEmpty)
            }
            if let error = app.error {
                Text(error).font(.system(size: 13)).foregroundColor(Theme.pink)
            }
            Spacer()
        }
        .padding(20)
    }

    private func send() async {
        busy = true
        app.error = nil
        do {
            try await app.signIn(email: email)
            sent = true
        } catch {
            app.error = error.localizedDescription
        }
        busy = false
    }
}
