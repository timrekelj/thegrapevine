package si.timrekelj.thegrapevine

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import okhttp3.Cache
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
class Api(
    private val http: HttpClient = defaultHttpClient(),
    /** Pictures only: no Supabase credentials are sent with it, and its responses may be cached on disk. */
    private val pictures: HttpClient = http,
) {

    var session: Session? = null

    /** Persisting a refreshed session is the app's job, not the client's. */
    var onSessionChanged: ((Session) -> Unit)? = null

    private val refreshMutex = Mutex()

    // ---------------------------------------------------------------- transport

    private suspend fun refreshIfNeeded(path: String) {
        // Token, OTP and verify are how a session is born or renewed. Other auth calls, such as
        // changing the email, still need a live access token.
        if (path.startsWith("auth/v1/token") || path.startsWith("auth/v1/otp") || path.startsWith("auth/v1/verify")) return
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
        mergeDuplicates: Boolean = false,
        /** Other calls turn a 401 into a session-expired error. Delete account keeps the body's `error`. */
        mapUnauthorized: Boolean = true,
    ): String {
        refreshIfNeeded(path)

        val response = http.request(Config.supabaseURL + path) {
            this.method = method
            if (query.isNotEmpty()) url { query.forEach { (name, value) -> parameters.append(name, value) } }
            header("apikey", Config.supabasePublishableKey)
            session?.let { header("Authorization", "Bearer ${it.accessToken}") }
            val preferences = listOfNotNull(
                "return=representation".takeIf { preferRepresentation },
                "resolution=merge-duplicates".takeIf { mergeDuplicates },
            )
            if (preferences.isNotEmpty()) header("Prefer", preferences.joinToString(","))
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
        if (status == 401 && mapUnauthorized) throw ApiError.Unauthorized
        if (status >= 500) Telemetry.httpError(status)
        if (status !in 200..299) throw ApiError.Http(status, text)
        return text
    }

    // ------------------------------------------------------------------ reads

    /** Whole front pages can be large; parse them off the main thread. */
    @PublishedApi
    internal suspend inline fun <reified T> decode(text: String): T =
        withContext(Dispatchers.Default) { SupabaseJson.decodeFromString<T>(text) }

    suspend inline fun <reified T> getMany(path: String, query: List<Pair<String, String>> = emptyList()): List<T> =
        decode(request(path = path, method = HttpMethod.Get, query = query))

    suspend inline fun <reified T> getOne(path: String, query: List<Pair<String, String>> = emptyList()): T? =
        getMany<T>(path, query + ("limit" to "1")).firstOrNull()

    // ------------------------------------------------------------------ writes

    suspend inline fun <reified T> postRow(path: String, body: String): T =
        decode<List<T>>(
            request(path = path, method = HttpMethod.Post, body = body, preferRepresentation = true),
        ).firstOrNull() ?: throw ApiError.Decoding("No row returned")

    suspend inline fun <reified T> upsertRow(path: String, body: String): T =
        decode<List<T>>(
            request(path = path, method = HttpMethod.Post, body = body, preferRepresentation = true, mergeDuplicates = true),
        ).firstOrNull() ?: throw ApiError.Decoding("No row returned")

    suspend inline fun <reified T> patchRow(path: String, query: List<Pair<String, String>>, body: String): T =
        decode<List<T>>(
            request(path = path, method = HttpMethod.Patch, query = query, body = body, preferRepresentation = true),
        ).firstOrNull() ?: throw ApiError.Decoding("No row returned")

    suspend inline fun <reified T> postObject(path: String, body: String): T =
        decode(request(path = path, method = HttpMethod.Post, body = body))

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
        decode(request(path = "rest/v1/rpc/$name", method = HttpMethod.Post, body = body))

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

    /**
     * Raw bytes for avatars and the halftone plates; Coil is deliberately not a dependency.
     * Image URLs come from scraped pages, so the size is capped rather than trusted.
     */
    suspend fun fetchBytes(url: String): ByteArray = pictures.prepareGet(url).execute { response ->
        if (!response.status.isSuccess()) throw ApiError.Http(response.status.value, "")
        val length = response.contentLength()
        if (length != null && length > MAX_PICTURE_BYTES) throw ApiError.Network("Picture too large")
        val bytes = response.bodyAsChannel().readRemaining(MAX_PICTURE_BYTES + 1L).readByteArray()
        if (bytes.size > MAX_PICTURE_BYTES) throw ApiError.Network("Picture too large")
        bytes
    }

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
    private data class PKCEBody(val auth_code: String, val code_verifier: String)

    suspend fun exchangeCode(code: String, verifier: String): Session = session(
        request(
            path = "auth/v1/token",
            method = HttpMethod.Post,
            query = listOf("grant_type" to "pkce"),
            body = encodeBody(PKCEBody(code, verifier)),
        ),
    )

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

    /**
     * Asks Supabase Auth to change the signed-in user's email.
     * With `double_confirm_changes` (the project's auth setting) the address stays the same until
     * both the current and the new inbox confirm. The returned [email] is the address in effect now;
     * [newEmail] is the pending one when Auth reports it.
     */
    suspend fun updateEmail(email: String): AuthEmailUpdate {
        val text = request(
            path = "auth/v1/user",
            method = HttpMethod.Put,
            body = encodeBody(EmailUpdateBody(email.trim())),
        )
        val user = try {
            SupabaseJson.decodeFromString<UserEmailPayload>(text)
        } catch (_: kotlinx.serialization.SerializationException) {
            throw ApiError.Decoding("Could not read the email change response.")
        }
        return AuthEmailUpdate(email = user.email, newEmail = user.new_email)
    }

    /**
     * Permanently deletes the signed-in user via `POST functions/v1/delete-account`.
     * [request] sends `apikey` (the project publishable key) and `Authorization: Bearer` of the
     * current access token, and this call sends no body. Success is `200 {"deleted": true}`.
     * A 4xx or 500 `{"error": "…"}` throws with that text and leaves the local session in place.
     */
    suspend fun deleteAccount() {
        val text = request(path = DELETE_ACCOUNT_FUNCTION, method = HttpMethod.Post, mapUnauthorized = false)
        if (!accountWasDeleted(text)) {
            throw ApiError.Decoding("Your account could not be deleted. Please try again.")
        }
    }

    @Serializable
    private data class EmailUpdateBody(val email: String)

    @Serializable
    private data class UserEmailPayload(val email: String? = null, val new_email: String? = null)

    private fun session(tokenJson: String): Session {
        val response: TokenResponse = SupabaseJson.decodeFromString(tokenJson)
        return Session(
            accessToken = response.access_token,
            refreshToken = response.refresh_token,
            userId = response.user.id,
            email = response.user.email.orEmpty(),
        )
    }

    companion object {
        /** `POST /functions/v1/delete-account`, caller's JWT, publishable key, no body. */
        const val DELETE_ACCOUNT_FUNCTION = "functions/v1/delete-account"

        private const val MAX_PICTURE_BYTES = 15 * 1024 * 1024

        fun defaultHttpClient(cache: Cache? = null): HttpClient = HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
            if (cache != null) engine { config { cache(cache) } }
        }
    }
}
