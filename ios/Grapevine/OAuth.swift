import Foundation
import CryptoKit
import Security

enum AuthProvider: String, CaseIterable {
    case google, apple, x

    var title: String {
        switch self {
        case .google: "Continue with Google"
        case .apple: "Sign in with Apple"
        case .x: "Continue with X"
        }
    }
}

enum OAuth {
    static let callback = "grapevine://auth/callback"

    /// A PKCE code verifier (and Apple nonce): 32 bytes from the system's secure random generator, as 64 hex digits.
    static func verifier() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        if SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) != errSecSuccess {
            bytes = (0..<32).map { _ in UInt8.random(in: .min ... .max) }
        }
        return bytes.map { String(format: "%02x", $0) }.joined()
    }

    static func nonceHash(_ nonce: String) -> String {
        SHA256.hash(data: Data(nonce.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    static func challenge(_ verifier: String) -> String {
        Data(SHA256.hash(data: Data(verifier.utf8))).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }

    static func authorizeURL(provider: AuthProvider, verifier: String) -> URL {
        var url = URLComponents(url: Config.supabaseURL.appending(path: "auth/v1/authorize"), resolvingAgainstBaseURL: false)!
        url.queryItems = [
            URLQueryItem(name: "provider", value: provider.rawValue),
            URLQueryItem(name: "redirect_to", value: callback),
            URLQueryItem(name: "code_challenge", value: challenge(verifier)),
            URLQueryItem(name: "code_challenge_method", value: "s256"),
        ]
        return url.url!
    }

    static func code(from url: URL) throws -> String {
        let failure = APIError.network("Sign-in could not be completed. Please try again.")
        guard url.scheme == "grapevine", url.host == "auth", url.path == "/callback",
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { throw failure }
        // Supabase can return provider errors in either the query or fragment.
        let items = (components.queryItems ?? []) + (URLComponents(string: "?" + (components.fragment ?? ""))?.queryItems ?? [])
        if items.contains(where: { $0.name == "error" || $0.name == "error_code" }) {
            let message = items.first(where: { $0.name == "error_description" })?.value
                ?? items.first(where: { $0.name == "error" })?.value
            throw APIError.network(message.flatMap { $0.isEmpty ? nil : $0 } ?? "Sign-in was rejected. Please try again.")
        }
        guard let code = components.queryItems?.first(where: { $0.name == "code" })?.value, !code.isEmpty else { throw failure }
        return code
    }
}
