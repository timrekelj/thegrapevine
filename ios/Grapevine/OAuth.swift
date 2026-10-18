import Foundation
import CryptoKit

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

    static func verifier() -> String {
        UUID().uuidString.replacingOccurrences(of: "-", with: "") + UUID().uuidString.replacingOccurrences(of: "-", with: "")
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
        guard url.scheme == "grapevine", url.host == "auth", url.path == "/callback",
              let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems,
              !items.contains(where: { $0.name == "error" }),
              let code = items.first(where: { $0.name == "code" })?.value, !code.isEmpty else {
            throw APIError.network("Sign-in could not be completed. Please try again.")
        }
        return code
    }
}
