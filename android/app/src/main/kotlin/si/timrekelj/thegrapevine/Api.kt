package si.timrekelj.thegrapevine

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed class ApiError(message: String) : Exception(message) {
    class Http(val code: Int, val body: String) : ApiError(httpMessage(code, body))
    class Network(message: String) : ApiError(message)
    class Decoding(message: String) : ApiError(message)
    object Unauthorized : ApiError("Your session has expired. Please sign in again.")
}

/** Supabase reports failures as `{"message"|"msg"|"error_description"|"error": "…"}` (Auth uses `msg`). */
private fun httpMessage(code: Int, body: String): String {
    val fromBody = runCatching {
        val obj = SupabaseJson.parseToJsonElement(body).jsonObject
        (obj["message"] ?: obj["msg"] ?: obj["error_description"] ?: obj["error"])?.jsonPrimitive?.content
    }.getOrNull()
    return fromBody ?: "Server error ($code)"
}

inline fun <reified T> encodeBody(value: T): String = SupabaseJson.encodeToString(value)

/**
 * A direct REST client against Supabase, ported from `ios/Grapevine/API.swift`.
 * All orchestration (submission, clustering, summaries) lives in Edge Functions,
 * so there is nothing else to talk to.
 */
class Api(private val http: HttpClient = defaultHttpClient()) {

    var session: Session? = null

    /** Persisting a refreshed session is the app's job, not the client's. */
    var onSessionChanged: ((Session) -> Unit)? = null

    private val refreshMutex = Mutex()

    // ---------------------------------------------------------------- transport

    private suspend fun refreshIfNeeded(path: String) {
        if (path.startsWith("auth/")) return
        val stored = session ?: return
        if (!Jwt.isExpired(stored.accessToken)) return
        refreshMutex.withLock {
            val current = session ?: return
            if (!Jwt.isExpired(current.accessToken)) return
            val fresh = refreshSession(current.refreshToken)
            session = fresh
            onSessionChanged?.invoke(fresh)
        }
    }

    suspend fun request(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        query: List<Pair<String, String>> = emptyList(),
        body: String? = null,
        file: ByteArray? = null,
        fileContentType: String = "application/octet-stream",
        preferRepresentation: Boolean = false,
    ): String {
        refreshIfNeeded(path)

        val response = http.request(Config.supabaseURL + path) {
            this.method = method
            if (query.isNotEmpty()) url { query.forEach { (name, value) -> parameters.append(name, value) } }
            header("apikey", Config.supabasePublishableKey)
            session?.let { header("Authorization", "Bearer ${it.accessToken}") }
            if (preferRepresentation) header("Prefer", "return=representation")
            if (body != null) {
                setBody(body)
                contentType(ContentType.Application.Json)
            } else if (file != null) {
                setBody(file)
                contentType(ContentType.parse(fileContentType))
            }
        }

        val text = response.bodyAsText()
        val status = response.status.value
        if (status == 401) throw ApiError.Unauthorized
        if (status !in 200..299) throw ApiError.Http(status, text)
        return text
    }

    // ------------------------------------------------------------------ reads

    suspend inline fun <reified T> getMany(path: String, query: List<Pair<String, String>> = emptyList()): List<T> =
        SupabaseJson.decodeFromString(request(path = path, method = HttpMethod.Get, query = query))

    suspend inline fun <reified T> getOne(path: String, query: List<Pair<String, String>> = emptyList()): T? =
        getMany<T>(path, query + ("limit" to "1")).firstOrNull()

    // ------------------------------------------------------------------ writes

    suspend inline fun <reified T> postRow(path: String, body: String): T =
        SupabaseJson.decodeFromString<List<T>>(
            request(path = path, method = HttpMethod.Post, body = body, preferRepresentation = true),
        ).firstOrNull() ?: throw ApiError.Decoding("No row returned")

    suspend inline fun <reified T> patchRow(path: String, query: List<Pair<String, String>>, body: String): T =
        SupabaseJson.decodeFromString<List<T>>(
            request(path = path, method = HttpMethod.Patch, query = query, body = body, preferRepresentation = true),
        ).firstOrNull() ?: throw ApiError.Decoding("No row returned")

    suspend inline fun <reified T> postObject(path: String, body: String): T =
        SupabaseJson.decodeFromString(request(path = path, method = HttpMethod.Post, body = body))

    suspend fun postVoid(path: String, body: String) {
        request(path = path, method = HttpMethod.Post, body = body)
    }

    suspend fun patchVoid(path: String, query: List<Pair<String, String>>, body: String) {
        request(path = path, method = HttpMethod.Patch, query = query, body = body)
    }

    suspend fun delete(path: String, query: List<Pair<String, String>>) {
        request(path = path, method = HttpMethod.Delete, query = query)
    }

    suspend inline fun <reified T> rpc(name: String, body: String): T =
        SupabaseJson.decodeFromString(request(path = "rest/v1/rpc/$name", method = HttpMethod.Post, body = body))

    // ------------------------------------------------------------------- media

    suspend fun upload(attachment: Attachment, path: String) {
        request(
            path = "storage/v1/object/media/$path",
            method = HttpMethod.Post,
            file = attachment.data,
            fileContentType = attachment.contentType,
        )
    }

    /** A short-lived signed URL, or the original URL when it is already absolute. */
    suspend fun mediaURL(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        if (path.startsWith("/seed/")) return Config.webURL + path
        val signed: SignedUrl = postObject(
            path = "storage/v1/object/sign/media/$path",
            body = encodeBody(SignedUrlBody(expiresIn = 3600)),
        )
        return Config.supabaseURL + "storage/v1" + signed.signedURL
    }

    /** Raw bytes for the halftone plates; Coil is deliberately not a dependency. */
    suspend fun fetchBytes(url: String): ByteArray = http.get(url).body()

    // -------------------------------------------------------------------- auth

    @Serializable
    private data class SignedUrlBody(val expiresIn: Int)

    @Serializable
    private data class SignedUrl(val signedURL: String)

    /** Emails a one-time sign-in code (the email template shows only the code). */
    suspend fun signInWithOtp(email: String) {
        request(path = "auth/v1/otp", method = HttpMethod.Post, body = encodeBody(OtpBody(email.trim())))
    }

    /** Exchanges the emailed code for a session. */
    suspend fun verifyOtp(email: String, code: String): Session =
        session(
            request(
                path = "auth/v1/verify",
                method = HttpMethod.Post,
                body = encodeBody(VerifyBody(email = email.trim(), token = code.trim())),
            ),
        )

    @Serializable
    private data class OtpBody(val email: String)

    @Serializable
    private data class VerifyBody(val email: String, val token: String, val type: String = "email")

    @Serializable
    private data class RefreshBody(val refresh_token: String)

    @Serializable
    private data class TokenResponse(val access_token: String, val refresh_token: String, val user: UserPayload)

    @Serializable
    private data class UserPayload(val id: String, val email: String? = null)

    suspend fun refreshSession(refreshToken: String): Session =
        session(
            request(
                path = "auth/v1/token",
                method = HttpMethod.Post,
                query = listOf("grant_type" to "refresh_token"),
                body = encodeBody(RefreshBody(refreshToken)),
            ),
        )

    private fun session(tokenJson: String): Session {
        val response: TokenResponse = SupabaseJson.decodeFromString(tokenJson)
        return Session(
            accessToken = response.access_token,
            refreshToken = response.refresh_token,
            userId = response.user.id,
            email = response.user.email.orEmpty(),
        )
    }

    private companion object {
        fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
        }
    }
}
