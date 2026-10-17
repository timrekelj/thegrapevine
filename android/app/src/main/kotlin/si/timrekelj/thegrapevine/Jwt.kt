package si.timrekelj.thegrapevine

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.util.Base64

/** Reads the claims out of a Supabase JWT without verifying it (the server verifies). */
object Jwt {
    fun decode(token: String): JsonObject? {
        val parts = token.split('.')
        if (parts.size != 3) return null
        return try {
            val bytes = Base64.getUrlDecoder().decode(pad(parts[1]))
            SupabaseJson.decodeFromString<JsonObject>(bytes.decodeToString())
        } catch (_: Exception) {
            null
        }
    }

    fun isExpired(token: String, leewaySeconds: Long = 60): Boolean {
        val exp = decode(token)?.get("exp")?.jsonPrimitive?.content?.toDoubleOrNull() ?: return true
        val expiresAt = Instant.ofEpochMilli((exp * 1000).toLong())
        return expiresAt.isBefore(Instant.now().plusSeconds(leewaySeconds))
    }

    /** java.util.Base64 rejects unpadded input; JWTs strip the padding. */
    private fun pad(value: String): String {
        val remainder = value.length % 4
        return if (remainder == 0) value else value + "=".repeat(4 - remainder)
    }
}
