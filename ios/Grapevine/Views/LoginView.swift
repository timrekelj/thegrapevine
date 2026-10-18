import SwiftUI
import AuthenticationServices

struct LoginView: View {
    @Environment(AppState.self) private var app
    @Environment(\.webAuthenticationSession) private var webAuthenticationSession
    @State private var email = ""
    @State private var code = ""
    @State private var sent = false
    @State private var busy = false

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                Text("The Grapevine").font(Theme.display(28))
                Text("A paper between friends.").font(Theme.display(17))
                Text("Sign in to your private paper. No password, no public feed.")
                    .font(.system(size: 15)).foregroundColor(Theme.ink2)
                    .multilineTextAlignment(.center)
                if sent {
                    Text("We emailed a sign-in code to \(email.trimmed).")
                        .font(.system(size: 15, weight: .semibold))
                        .multilineTextAlignment(.center)
                    TextField("Code", text: $code)
                        .keyboardType(.numberPad)
                        .textContentType(.oneTimeCode)
                        .padding()
                        .background(Theme.raised)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        // Supabase codes are 6 digits by default and up to 10 if the project is configured so.
                        .onChange(of: code) { code = String(code.filter(\.isNumber).prefix(10)) }
                    Button { Task { await run { try await app.verifyCode(email: email, code: code) } } } label: {
                        Text(busy ? "Signing in…" : "Sign in")
                            .frame(maxWidth: .infinity).frame(height: 48)
                    }
                    .buttonStyle(.plain)
                    .background(Theme.ink)
                    .foregroundColor(Theme.paper)
                    .clipShape(RoundedRectangle(cornerRadius: 24))
                    .disabled(busy || code.count < 6)
                    HStack {
                        Button("Send a new code") { Task { await run { try await app.signIn(email: email) } } }
                        Button("Use a different email") { sent = false; code = ""; app.error = nil }
                    }
                    .disabled(busy)
                } else {
                    VStack(spacing: 12) {
                        ForEach(AuthProvider.allCases, id: \.self) { provider in
                            Button(provider.title) {
                                Task {
                                    await run {
                                        let verifier = OAuth.verifier()
                                        let callback = try await webAuthenticationSession.authenticate(
                                            using: OAuth.authorizeURL(provider: provider, verifier: verifier),
                                            callbackURLScheme: "grapevine")
                                        try await app.signIn(callback: callback, verifier: verifier)
                                    }
                                }
                            }
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .buttonStyle(.bordered)
                            .disabled(busy)
                        }
                        Text("or use your email").font(.system(size: 13)).foregroundColor(Theme.ink2)
                    }
                    TextField("Your email", text: $email)
                        .keyboardType(.emailAddress)
                        .autocapitalization(.none)
                        .textContentType(.emailAddress)
                        .padding()
                        .background(Theme.raised)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    Button { Task { await run { try await app.signIn(email: email); sent = true } } } label: {
                        Text(busy ? "Sending…" : "Email me a code")
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
            }
            .padding(20)
        }
    }

    private func run(_ action: () async throws -> Void) async {
        busy = true
        app.error = nil
        do { try await action() }
        catch let error as ASWebAuthenticationSessionError where error.code == .canceledLogin { }
        catch { app.error = error.localizedDescription }
        busy = false
    }
}
