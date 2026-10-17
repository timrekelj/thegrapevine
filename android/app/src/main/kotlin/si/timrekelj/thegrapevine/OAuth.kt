package si.timrekelj.thegrapevine

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

enum class AuthProvider(val value: String, val title: String) {
    Google("google", "Continue with Google"),
    Apple("apple", "Sign in with Apple"),
    X("x", "Continue with X"),
}

object OAuth {
    const val callback = "grapevine://auth/callback"

    fun verifier(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        ByteArray(32).also { SecureRandom().nextBytes(it) },
    )

    fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
    )

    fun authorizeURL(base: String, provider: AuthProvider, verifier: String): String {
        val query = listOf(
            "provider" to provider.value,
            "redirect_to" to callback,
            "code_challenge" to challenge(verifier),
            "code_challenge_method" to "s256",
        ).joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
        return "${base.trimEnd('/')}/auth/v1/authorize?$query"
    }

    fun isCallback(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "grapevine" && uri.host == "auth" && uri.path == "/callback"
    }.getOrDefault(false)

    fun code(value: String): String {
        require(isCallback(value)) { "Invalid sign-in callback." }
        val query = URI(value).rawQuery.orEmpty().split('&').associate {
            val parts = it.split('=', limit = 2)
            URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
        }
        require("error" !in query) { "Sign-in was cancelled or could not be completed. Please try again." }
        return requireNotNull(query["code"]?.takeIf { it.isNotBlank() }) { "Sign-in code is missing. Please try again." }
    }
}
