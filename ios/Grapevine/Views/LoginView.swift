import SwiftUI
import AuthenticationServices

/// Must match `otp_length` in supabase/config.toml and the hosted project.
let signInCodeLength = 6

struct LoginView: View {
    @Environment(AppState.self) private var app
    @Environment(\.webAuthenticationSession) private var webAuthenticationSession
    @State private var email = ""
    @State private var code = ""
    @State private var sent = false
    @State private var busy = false
    @State private var appleNonce: String?
    @State private var resendAt = Date.distantPast
    @FocusState private var focus: Field?

    private enum Field { case email, code }

    var body: some View {
        GeometryReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    nameplate
                    Spacer(minLength: 40)
                    VStack(alignment: .leading, spacing: 24) {
                        intro
                        if sent { codeStep } else { emailStep }
                    }
                    footer.padding(.top, 32)
                }
                .padding(.horizontal, 24).padding(.vertical, 16)
                .frame(maxWidth: 468)
                .frame(maxWidth: .infinity, minHeight: proxy.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .scrollDismissesKeyboard(.interactively)
        .animation(.snappy(duration: 0.25), value: sent)
        .animation(.snappy(duration: 0.25), value: app.error)
        .sensoryFeedback(.error, trigger: app.error) { _, now in now != nil }
    }

    // MARK: - Sections

    private var nameplate: some View {
        VStack(spacing: 0) {
            Text("The Grapevine").font(Theme.nameplate(52)).foregroundColor(Theme.ink)
                .lineLimit(1).minimumScaleFactor(0.6)
                .padding(.bottom, 10)
            DoubleRule()
            HStack {
                Kicker(text: Date.now.formatted(.dateTime.weekday(.wide).day().month(.wide)), size: 10)
                Spacer()
                Kicker(text: "Private edition", size: 10)
            }
            .padding(.vertical, 7)
            Rectangle().fill(Theme.ink).frame(height: 1)
        }
        .overlay(alignment: .topLeading) {
            if sent {
                Button { changeEmail() } label: {
                    Image(systemName: "chevron.left").font(.system(size: 17, weight: .semibold))
                        .frame(width: 44, height: 44)
                }
                .foregroundColor(Theme.ink).offset(x: -12, y: -6)
                .accessibilityLabel("Use a different email")
            }
        }
    }

    private var intro: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(sent ? "Check your inbox." : "Good stories.\nBetter company.")
                .font(Theme.headline(46)).lineSpacing(-6).foregroundColor(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)
            if sent {
                Text("We sent a \(signInCodeLength)-digit code to \(Text(email.trimmed).fontWeight(.semibold).foregroundColor(Theme.ink))")
                    .font(Theme.serif(18)).foregroundColor(Theme.ink2)
                Button("Change email") { changeEmail() }
                    .font(.system(size: 15, weight: .medium)).foregroundColor(Theme.ink).underline()
                    .disabled(busy)
            } else {
                Text("A private paper, curated by your friends.").font(Theme.serif(18)).foregroundColor(Theme.ink2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var emailStep: some View {
        VStack(alignment: .leading, spacing: 24) {
            VStack(alignment: .leading, spacing: 8) {
                Kicker(text: "Email address")
                TextField("you@example.com", text: $email)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .textContentType(.emailAddress)
                    .submitLabel(.send).onSubmit(sendCode)
                    .focused($focus, equals: .email)
                    .paperField(focused: focus == .email, invalid: app.error != nil)
                    .accessibilityLabel("Email address")
                errorText
                primaryButton("Email me a code", busyText: "Sending…", enabled: !email.trimmed.isEmpty, action: sendCode)
                    .padding(.top, 8)
            }
            HStack(spacing: 12) {
                Rectangle().fill(Theme.rule).frame(height: 1)
                Text("or").font(Theme.italic(17)).foregroundColor(Theme.ink3)
                Rectangle().fill(Theme.rule).frame(height: 1)
            }
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
                    // The white style has no outline of its own, so all three buttons share our hairline.
                    .signInWithAppleButtonStyle(.white)
                    .frame(height: 50)
                    .clipShape(field)
                    .overlay(field.stroke(Theme.rule, lineWidth: 1).allowsHitTesting(false))
                    .disabled(busy).opacity(busy ? 0.5 : 1)
                ForEach([AuthProvider.google, .x], id: \.self) { provider in
                    Button { signIn(provider) } label: {
                        HStack(spacing: 10) {
                            Image(provider == .google ? "GoogleSignIn" : "XSignIn")
                                .resizable().scaledToFit().frame(width: 17, height: 17)
                                .accessibilityHidden(true)
                            Text(provider.title).font(.system(size: 17, weight: .medium))
                        }
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .foregroundColor(.black)
                        .background(Color.white, in: field)
                        .overlay(field.stroke(Theme.rule, lineWidth: 1))
                    }
                    .buttonStyle(PressStyle()).disabled(busy).opacity(busy ? 0.5 : 1)
                }
            }
        }
    }

    private var codeStep: some View {
        VStack(alignment: .leading, spacing: 8) {
            ZStack {
                // One real field (so the keyboard's code suggestion and VoiceOver just work), drawn as cells.
                TextField("", text: $code)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .focused($focus, equals: .code)
                    .foregroundStyle(.clear).tint(.clear)
                    .frame(maxWidth: .infinity, minHeight: 58)
                    .accessibilityLabel("Sign-in code")
                    .onChange(of: code) {
                        code = String(code.filter(\.isNumber).prefix(signInCodeLength))
                        if code.count == signInCodeLength && !busy { verifyCode() }
                    }
                HStack(spacing: 8) {
                    ForEach(0..<signInCodeLength, id: \.self) { index in
                        let digits = Array(code)
                        let active = focus == .code && index == min(digits.count, signInCodeLength - 1)
                        Text(index < digits.count ? String(digits[index]) : "")
                            .font(Theme.headline(30)).monospacedDigit()
                            // Each digit is set into its cell like a sort dropped into the forme.
                            .contentTransition(.numericText())
                            .foregroundColor(Theme.ink)
                            .frame(maxWidth: .infinity, minHeight: 62)
                            .background(Theme.raised, in: field)
                            .overlay(field.stroke(app.error != nil ? Theme.danger : active ? Theme.ink : Theme.rule, lineWidth: 1))
                            .background(field.fill(active ? Theme.rule : .clear).offset(x: 3, y: 3))
                    }
                }
                .allowsHitTesting(false)
                .accessibilityHidden(true)
                .animation(.snappy(duration: 0.2), value: code)
                .animation(.snappy(duration: 0.2), value: focus)
            }
            .contentShape(Rectangle())
            .onTapGesture { focus = .code }
            errorText
            primaryButton("Sign in", busyText: "Signing in…", enabled: code.count == signInCodeLength, action: verifyCode)
                .padding(.top, 8)
            TimelineView(.periodic(from: .now, by: 1)) { context in
                let left = max(0, Int(resendAt.timeIntervalSince(context.date).rounded(.up)))
                Button { resendCode() } label: {
                    Text(left > 0 ? "Send a new code in 0:\(String(format: "%02d", left))" : "Send a new code")
                        .font(.system(size: 15, weight: .medium)).monospacedDigit()
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .foregroundColor(left > 0 ? Theme.ink2 : Theme.ink)
                        .overlay(field.stroke(left > 0 ? .clear : Theme.rule, lineWidth: 1))
                }
                .buttonStyle(PressStyle()).disabled(busy || left > 0)
            }
            .padding(.top, 8)
        }
        .onAppear { focus = .code }
    }

    private var footer: some View {
        Text(LocalizedStringKey("By continuing, you agree to the [Terms](\(Config.webURL.appending(path: "terms").absoluteString)) and [Privacy Policy](\(Config.webURL.appending(path: "privacy").absoluteString))."))
            .font(Theme.mono(11)).foregroundColor(Theme.ink3).tint(Theme.ink)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
    }

    // MARK: - Pieces

    private var field: RoundedRectangle { RoundedRectangle(cornerRadius: Theme.radius) }

    @ViewBuilder private var errorText: some View {
        if let error = app.error {
            Label(error, systemImage: "exclamationmark.circle")
                .font(.system(size: 14, weight: .medium)).foregroundColor(Theme.danger)
                .arrives()
        }
    }

    /// The ink button; while signing in it prints dots and reads `busyText` to VoiceOver.
    private func primaryButton(_ title: String, busyText: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(title, action: action)
            .buttonStyle(InkButtonStyle(enabled: enabled, busy: busy && enabled))
            .disabled(busy || !enabled)
            .busyAccessibility(busy, busyText)
    }

    // MARK: - Actions

    private func sendCode() {
        guard !busy, !email.trimmed.isEmpty else { return }
        Task {
            await run {
                try await app.signIn(email: email)
                code = ""
                resendAt = .now.addingTimeInterval(30)
                sent = true
            }
        }
    }

    private func resendCode() {
        Task {
            await run {
                try await app.signIn(email: email)
                code = ""
                resendAt = .now.addingTimeInterval(30)
            }
        }
    }

    private func verifyCode() {
        Task {
            await run { try await app.verifyCode(email: email, code: code) }
            if app.error != nil { code = "" }
        }
    }

    private func changeEmail() {
        sent = false
        code = ""
        app.error = nil
        focus = .email
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
