import Foundation

enum APIError: Error, LocalizedError {
    case http(Int, String)
    case network(String)
    case decoding(String)
    case unauthorized

    var errorDescription: String? {
        switch self {
        case .http(let code, let body):
            if let data = body.data(using: .utf8), let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let message = (object["message"] ?? object["msg"] ?? object["error_description"] ?? object["error"]) as? String { return message }
            return "Server error (\(code))"
        case .network(let message): return message
        case .decoding(let message): return "Could not read the response: \(message)"
        case .unauthorized: return "Your session has expired. Please sign in again."
        }
    }
}

@MainActor
enum API {
    static var session: Session?
    private static var refreshTask: Task<Session, Error>?

    private static func url(for path: String, query: [URLQueryItem]) -> URL {
        var url = Config.supabaseURL
        url.append(path: path)
        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)!
        if !query.isEmpty { components.queryItems = query }
        return components.url!
    }

    private static func request(path: String, method: String, query: [URLQueryItem] = [], body: (any Encodable)? = nil, file: Data? = nil, contentType: String = "application/json", preferRepresentation: Bool = false, mergeDuplicates: Bool = false) async throws -> Data {
        if !path.hasPrefix("auth/"), let stored = session, JWT.isExpired(stored.accessToken) {
            if refreshTask == nil { refreshTask = Task { try await refreshSession(refreshToken: stored.refreshToken) } }
            defer { refreshTask = nil }
            let refreshed = try await refreshTask!.value
            guard session?.refreshToken == stored.refreshToken || session?.accessToken == refreshed.accessToken else { throw APIError.unauthorized }
            session = refreshed
            try Keychain.save(JSONEncoder().encode(refreshed))
        }
        var request = URLRequest(url: url(for: path, query: query))
        request.httpMethod = method
        request.setValue(Config.supabasePublishableKey, forHTTPHeaderField: "apikey")
        if let session {
            request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
        }
        let preferences = [preferRepresentation ? "return=representation" : nil,
                           mergeDuplicates ? "resolution=merge-duplicates" : nil].compactMap { $0 }
        if !preferences.isEmpty { request.setValue(preferences.joined(separator: ","), forHTTPHeaderField: "Prefer") }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONEncoder().encode(body)
        }
        if let file {
            request.setValue(contentType, forHTTPHeaderField: "Content-Type")
            request.httpBody = file
        }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIError.network("No response from the server") }
        if http.statusCode == 401 { throw APIError.unauthorized }
        if http.statusCode >= 500 { Telemetry.httpError(http.statusCode) }
        guard (200..<300).contains(http.statusCode) else {
            throw APIError.http(http.statusCode, String(data: data, encoding: .utf8) ?? "")
        }
        return data
    }

    private static func decode<T: Decodable>(_ type: T.Type, _ data: Data) throws -> T {
        do { return try JSONDecoder.supabase.decode(T.self, from: data) }
        catch { throw APIError.decoding(String(describing: error)) }
    }

    static func get<T: Decodable>(_ type: T.Type, path: String, query: [URLQueryItem] = []) async throws -> T {
        try decode(T.self, try await request(path: path, method: "GET", query: query))
    }

    static func getMany<T: Decodable>(_ type: T.Type, path: String, query: [URLQueryItem] = []) async throws -> [T] {
        try decode([T].self, try await request(path: path, method: "GET", query: query))
    }

    static func getOne<T: Decodable>(_ type: T.Type, path: String, query: [URLQueryItem] = []) async throws -> T? {
        let rows: [T] = try await getMany(type, path: path, query: query + [URLQueryItem(name: "limit", value: "1")])
        return rows.first
    }

    static func postRow<T: Decodable>(_ type: T.Type, path: String, body: any Encodable) async throws -> T {
        let data = try await request(path: path, method: "POST", body: body, preferRepresentation: true)
        let rows = try decode([T].self, data)
        guard let row = rows.first else { throw APIError.decoding("No row returned") }
        return row
    }

    static func upsertRow<T: Decodable>(_ type: T.Type, path: String, body: any Encodable) async throws -> T {
        let data = try await request(path: path, method: "POST", body: body, preferRepresentation: true, mergeDuplicates: true)
        let rows = try decode([T].self, data)
        guard let row = rows.first else { throw APIError.decoding("No row returned") }
        return row
    }

    static func patchRow<T: Decodable>(_ type: T.Type, path: String, query: [URLQueryItem], body: any Encodable) async throws -> T {
        let data = try await request(path: path, method: "PATCH", query: query, body: body, preferRepresentation: true)
        let rows = try decode([T].self, data)
        guard let row = rows.first else { throw APIError.decoding("No row returned") }
        return row
    }

    static func postVoid(path: String, body: any Encodable) async throws {
        _ = try await request(path: path, method: "POST", body: body)
    }

    static func postObject<T: Decodable>(_ type: T.Type, path: String, body: any Encodable) async throws -> T {
        try decode(T.self, try await request(path: path, method: "POST", body: body))
    }

    static func patchVoid(path: String, query: [URLQueryItem], body: any Encodable) async throws {
        _ = try await request(path: path, method: "PATCH", query: query, body: body)
    }

    static func delete(path: String, query: [URLQueryItem]) async throws {
        _ = try await request(path: path, method: "DELETE", query: query)
    }

    static func upload(_ attachment: Attachment, path: String) async throws {
        _ = try await request(path: "storage/v1/object/media/\(path)", method: "POST", file: attachment.data, contentType: attachment.contentType)
    }

    static func mediaURL(_ path: String) async throws -> URL {
        if let url = URL(string: path), ["http", "https"].contains(url.scheme ?? "") { return url }
        if path.hasPrefix("/seed/") { return Config.webURL.appending(path: path) }
        struct Signed: Decodable { let signedURL: String }
        let signed = try await postObject(Signed.self, path: "storage/v1/object/sign/media/\(path)", body: ["expiresIn": 3600])
        guard let url = URL(string: "storage/v1\(signed.signedURL)", relativeTo: Config.supabaseURL) else { throw APIError.network("Invalid media URL") }
        return url.absoluteURL
    }

    static func rpc<T: Decodable>(_ type: T.Type, name: String, body: any Encodable) async throws -> T {
        try decode(T.self, try await request(path: "rest/v1/rpc/\(name)", method: "POST", body: body))
    }

    // MARK: - Auth

    struct OtpBody: Encodable { let email: String }
    struct VerifyBody: Encodable { let email: String; let token: String; let type = "email" }

    /// Emails a one-time sign-in code (the email template shows only the code).
    static func signInWithOtp(email: String) async throws {
        _ = try await request(path: "auth/v1/otp", method: "POST", body: OtpBody(email: email.trimmed))
    }

    /// Exchanges the emailed code for a session.
    static func verifyOtp(email: String, code: String) async throws -> Session {
        session(from: try decode(TokenResponse.self,
            try await request(path: "auth/v1/verify", method: "POST", body: VerifyBody(email: email.trimmed, token: code.trimmed))))
    }

    struct RefreshBody: Encodable {
        let refresh_token: String
    }

    struct PKCEBody: Encodable { let auth_code: String; let code_verifier: String }

    static func exchangeCode(_ code: String, verifier: String) async throws -> Session {
        session(from: try decode(TokenResponse.self,
            try await request(path: "auth/v1/token", method: "POST", query: [URLQueryItem(name: "grant_type", value: "pkce")], body: PKCEBody(auth_code: code, code_verifier: verifier))))
    }

    struct AppleTokenBody: Encodable {
        let provider = "apple"
        let id_token: String
        let nonce: String
    }

    static func signInWithApple(idToken: String, nonce: String) async throws -> Session {
        session(from: try decode(TokenResponse.self,
            try await request(path: "auth/v1/token", method: "POST", query: [URLQueryItem(name: "grant_type", value: "id_token")],
                              body: AppleTokenBody(id_token: idToken, nonce: nonce))))
    }

    struct TokenResponse: Codable {
        let access_token: String
        let refresh_token: String
        let user: UserPayload
    }

    struct UserPayload: Codable {
        let id: UUID
        let email: String?
    }

    static func refreshSession(refreshToken: String) async throws -> Session {
        session(from: try decode(TokenResponse.self,
            try await request(path: "auth/v1/token", method: "POST", query: [URLQueryItem(name: "grant_type", value: "refresh_token")], body: RefreshBody(refresh_token: refreshToken))))
    }

    private static func session(from response: TokenResponse) -> Session {
        Session(accessToken: response.access_token, refreshToken: response.refresh_token,
                userId: response.user.id, email: response.user.email ?? "")
    }
}

enum JWT {
    static func decode(_ token: String) -> [String: Any]? {
        let parts = token.split(separator: ".")
        guard parts.count == 3 else { return nil }
        var payload = String(parts[1]).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        payload = payload.padding(toLength: ((payload.count + 3) / 4) * 4, withPad: "=", startingAt: 0)
        guard let data = Data(base64Encoded: payload),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
        return object
    }

    static func isExpired(_ token: String, leeway: TimeInterval = 60) -> Bool {
        guard let claims = decode(token), let exp = claims["exp"] as? TimeInterval else { return true }
        return Date(timeIntervalSince1970: exp).timeIntervalSinceNow < leeway
    }
}
