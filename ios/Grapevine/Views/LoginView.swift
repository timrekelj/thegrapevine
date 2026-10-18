import SwiftUI
import AuthenticationServices

struct LoginView: View {
    @Environment(AppState.self) private var app
    @Environment(\.webAuthenticationSession) private var webAuthenticationSession
    @State private var email = ""
    @State private var code = ""
    @State private var sent = false
    @State private var busy = false
    @State private var appleNonce: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                HStack(spacing: 10) {
                    Image(systemName: "newspaper").font(.system(size: 22, weight: .light))
                    Text("The Grapevine").font(Theme.display(22))
                }
                .padding(.bottom, 12)

                VStack(alignment: .leading, spacing: 12) {
                    Text(sent ? "Check your inbox." : "Good stories.\nBetter company.")
                        .font(Theme.display(34)).fixedSize(horizontal: false, vertical: true)
                    Text(sent ? "Enter the sign-in code we sent to \(email.trimmed)." : "A private paper, curated by your friends.")
                        .font(.system(size: 16)).lineSpacing(4).foregroundColor(Theme.ink2)
                }

                if sent {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("SIGN-IN CODE").font(.system(size: 11, weight: .semibold)).foregroundColor(Theme.ink2)
                        TextField("Code", text: $code)
                            .keyboardType(.numberPad)
                            .textContentType(.oneTimeCode)
                            .font(.system(size: 24, weight: .medium, design: .monospaced)).tracking(6)
                            .padding(16).background(Theme.raised, in: RoundedRectangle(cornerRadius: 14))
                            .accessibilityLabel("Sign-in code")
                            // Supabase codes are 6 digits by default and up to 10 if the project is configured so.
                            .onChange(of: code) { code = String(code.filter(\.isNumber).prefix(10)) }
                    }
                    Button { Task { await run { try await app.verifyCode(email: email, code: code) } } } label: {
                        Text(busy ? "Signing in…" : "Sign in")
                            .font(.system(size: 16, weight: .semibold))
                            .frame(maxWidth: .infinity, minHeight: 52)
                    }
                    .buttonStyle(.plain)
                    .background(Theme.ink, in: Capsule()).foregroundColor(Theme.paper)
                    .opacity(busy || code.count < 6 ? 0.4 : 1)
                    .disabled(busy || code.count < 6)
                    VStack(alignment: .leading, spacing: 4) {
                        Button("Send a new code") { Task { await run { try await app.signIn(email: email) } } }
                            .frame(minHeight: 44)
                        Button("Use a different email") { sent = false; code = ""; app.error = nil }
                            .frame(minHeight: 44)
                    }
                    .font(.system(size: 14, weight: .medium)).disabled(busy)
                } else {
                    VStack(spacing: 12) {
                        SignInWithAppleButton(.signIn) { request in
                            let nonce = OAuth.verifier()
                            appleNonce = nonce
                            request.requestedScopes = [.email]
                            request.nonce = OAuth.nonceHash(nonce)
                            busy = true
                            app.error = nil
                        } onCompletion: { result in
                            Task {
                                await run {
                                    defer { appleNonce = nil }
                                    let authorization = try result.get()
                                    guard let nonce = appleNonce,
                                          let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
                                          let data = credential.identityToken,
                                          let token = String(data: data, encoding: .utf8), !token.isEmpty else {
                                        throw APIError.network("Apple did not return a sign-in token. Please try again.")
                                    }
                                    try await app.signInWithApple(idToken: token, nonce: nonce)
                                }
                            }
                        }
                            .signInWithAppleButtonStyle(.black)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .frame(height: 52).disabled(busy).opacity(busy ? 0.5 : 1)
                        ForEach([AuthProvider.google, .x], id: \.self) { provider in
                            Button { signIn(provider) } label: {
                                HStack(spacing: 12) {
                                    Image(provider == .google ? "GoogleSignIn" : "XSignIn")
                                        .resizable().scaledToFit().frame(width: 20, height: 20)
                                        .accessibilityHidden(true)
                                    Text(provider.title).font(.system(size: 16, weight: .medium))
                                }
                                .frame(maxWidth: .infinity, minHeight: 52)
                                .foregroundColor(provider == .google ? Color(hex: 0x1F1F1F) : .white)
                                .background(provider == .google ? Color.white : .black, in: RoundedRectangle(cornerRadius: 12))
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(provider == .google ? Color(hex: 0x747775) : .clear, lineWidth: 1))
                            }
                            .buttonStyle(.plain).disabled(busy).opacity(busy ? 0.5 : 1)
                        }
                    }
                    HStack(spacing: 12) {
                        Rectangle().fill(Theme.rule).frame(height: 0.5)
                        Text("or use your email").font(.system(size: 12)).fixedSize()
                        Rectangle().fill(Theme.rule).frame(height: 0.5)
                    }
                    .foregroundColor(Theme.ink2)
                    VStack(alignment: .leading, spacing: 10) {
                        Text("EMAIL ADDRESS").font(.system(size: 11, weight: .semibold)).foregroundColor(Theme.ink2)
                        TextField("you@example.com", text: $email)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never).autocorrectionDisabled()
                            .textContentType(.emailAddress)
                            .padding(16).background(Theme.raised, in: RoundedRectangle(cornerRadius: 14))
                            .accessibilityLabel("Email address")
                    }
                    Button { Task { await run { try await app.signIn(email: email); sent = true } } } label: {
                        Text(busy ? "Sending…" : "Email me a code")
                            .font(.system(size: 16, weight: .semibold))
                            .frame(maxWidth: .infinity, minHeight: 52)
                    }
                    .buttonStyle(.plain)
                    .background(Theme.ink, in: Capsule()).foregroundColor(Theme.paper)
                    .opacity(busy || email.trimmed.isEmpty ? 0.4 : 1)
                    .disabled(busy || email.trimmed.isEmpty)
                }
                if let error = app.error {
                    Text(error).font(.system(size: 14)).foregroundColor(Theme.error)
                }
            }
            .padding(24).padding(.top, 20)
            .frame(maxWidth: 468)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private func signIn(_ provider: AuthProvider) {
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

    private func run(_ action: () async throws -> Void) async {
        busy = true
        app.error = nil
        do { try await action() }
        catch let error as ASWebAuthenticationSessionError where error.code == .canceledLogin { }
        catch let error as ASAuthorizationError where error.code == .canceled { }
        catch { app.error = error.localizedDescription }
        busy = false
    }
}
