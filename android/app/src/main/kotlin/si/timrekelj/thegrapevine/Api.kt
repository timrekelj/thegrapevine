package si.timrekelj.thegrapevine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

enum class HttpMethod { Get, Post, Put, Patch, Delete }

/**
 * A direct REST client against Supabase, ported from `ios/Grapevine/API.swift`.
 * All orchestration (submission, clustering, summaries) lives in Edge Functions,
 * so there is nothing else to talk to.
 */
class Api(
    private val http: OkHttpClient = defaultHttpClient(),
    /** Pictures only: no Supabase credentials are sent with it, and its responses may be cached on disk. */
    private val pictures: OkHttpClient = http,
) {

    /** Read by every request, written by sign-in and refreshes on other coroutines. */
    @Volatile
    var session: Session? = null
        set(value) {
            if (value == null) signedURLs.clear()
            field = value
        }

    /** Persisting a refreshed session is the app's job, not the client's. */
    @Volatile
    var onSessionChanged: ((Session) -> Unit)? = null

    private val refreshMutex = Mutex()

    /** Signed media URLs by storage path, with when to stop handing them out; forgotten on sign-out. */
    private val signedURLs = ConcurrentHashMap<String, Pair<String, Long>>()

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

    /**
     * Renews a session read back from storage. Refresh tokens are single-use, so this goes through the
     * same lock as [refreshIfNeeded]: two screens restoring at once (the app and its share sheet) share
     * one refresh instead of the second spending a token the first already rotated.
     */
    suspend fun restore(stored: Session): Session = refreshMutex.withLock {
        session?.takeIf { it.userId == stored.userId && !Jwt.isExpired(it.accessToken) }
            ?: refreshSession(stored.refreshToken)
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

        val url = (Config.supabaseURL + path).toHttpUrl().newBuilder()
            .apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()
        val content = when {
            body != null -> body.toRequestBody(JSON)
            file != null -> file.toRequestBody(fileContentType.toMediaTypeOrNull())
            method == HttpMethod.Get || method == HttpMethod.Delete -> null
            else -> ByteArray(0).toRequestBody()
        }
        val request = Request.Builder().url(url)
            .method(method.name.uppercase(), content)
            .header("apikey", Config.supabasePublishableKey)
            .apply {
                session?.let { header("Authorization", "Bearer ${it.accessToken}") }
                val preferences = listOfNotNull(
                    "return=representation".takeIf { preferRepresentation },
                    "resolution=merge-duplicates".takeIf { mergeDuplicates },
                )
                if (preferences.isNotEmpty()) header("Prefer", preferences.joinToString(","))
            }
            .build()

        val (status, text) = http.fetch(request) { it.code to it.body.string() }
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

    /** Story media goes to `media`; screenshots for reports to `feedback`. */
    suspend fun upload(attachment: Attachment, path: String, bucket: String = "media") {
        request(
            path = "storage/v1/object/$bucket/$path",
            method = HttpMethod.Post,
            file = attachment.data,
            fileContentType = attachment.contentType,
        )
    }

    /**
     * A short-lived signed URL, or the original URL when it is already absolute. Signed URLs are
     * reused while they have a while left, so a photo scrolled away and back keeps the same URL and
     * comes from the picture caches instead of being signed, downloaded and decoded again.
     */
    suspend fun mediaURL(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        if (path.startsWith("/seed/")) return Config.webURL + path
        val now = System.currentTimeMillis()
        signedURLs[path]?.let { (url, until) -> if (now < until) return url }
        val signed: SignedUrl = postObject(
            path = "storage/v1/object/sign/media/$path",
            body = encodeBody(SignedUrlBody(expiresIn = SIGNED_URL_SECONDS)),
        )
        val url = Config.supabaseURL + "storage/v1" + signed.signedURL
        // Handed out for most of its life only, so whoever gets it last still has time to load it.
        signedURLs[path] = url to now + SIGNED_URL_SECONDS * 1000L * 3 / 4
        return url
    }

    /**
     * Raw bytes for the photos on stories and in messages; Coil is deliberately not a dependency.
     * Image URLs come from scraped pages, so the size is capped rather than trusted.
     */
    suspend fun fetchBytes(url: String): ByteArray {
        val request = runCatching { Request.Builder().url(url).build() }.getOrNull()
            ?: throw ApiError.Network("Not a picture link")
        return pictures.fetch(request) { response ->
            if (!response.isSuccessful) throw ApiError.Http(response.code, "")
            val body = response.body
            if (body.contentLength() > MAX_PICTURE_BYTES) throw ApiError.Network("Picture too large")
            val source = body.source()
            if (source.request(MAX_PICTURE_BYTES + 1L)) throw ApiError.Network("Picture too large")
            source.readByteArray()
        }
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

    /** A refresh token Auth won't take (revoked, already rotated, user gone) means the session is over. */
    suspend fun refreshSession(refreshToken: String): Session {
        val text = try {
            request(
                path = "auth/v1/token",
                method = HttpMethod.Post,
                query = listOf("grant_type" to "refresh_token"),
                body = encodeBody(RefreshBody(refreshToken)),
            )
        } catch (e: ApiError.Http) {
            throw if (e.code == 400 || e.code == 403) ApiError.Unauthorized else e
        }
        return session(text)
    }

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
        private const val SIGNED_URL_SECONDS = 3600
        private val JSON = "application/json".toMediaType()

        /**
         * No limit on a whole call, so a large video upload on a slow line isn't cut off; a connection
         * that goes quiet for [IDLE_SECONDS] is. A story's reads, its photos' signed URLs and the
         * photos themselves all go to the Supabase host at once, so more than OkHttp's default five
         * may be in flight there (HTTP/2 carries them on one connection).
         */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = 16 })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(IDLE_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(IDLE_SECONDS, TimeUnit.SECONDS)
            .build()

        private const val IDLE_SECONDS = 30L

        /**
         * Runs [request] without blocking the caller, and reads the response with [read] on OkHttp's
         * thread. Cancelling the coroutine cancels the call. A dropped or unreachable connection
         * becomes one readable [ApiError.Network].
         */
        private suspend fun <T> OkHttpClient.fetch(request: Request, read: (Response) -> T): T =
            suspendCancellableCoroutine { continuation ->
                val call = newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(offline(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        runCatching { response.use(read) }
                            .onSuccess { continuation.resume(it) }
                            .onFailure { continuation.resumeWithException(if (it is IOException) offline(it) else it) }
                    }
                })
            }

        private fun offline(e: IOException) = ApiError.Network(
            if (e is InterruptedIOException) "The connection timed out. Please try again."
            else "Could not reach The Grapevine. Check your connection and try again.",
        )
    }
}
