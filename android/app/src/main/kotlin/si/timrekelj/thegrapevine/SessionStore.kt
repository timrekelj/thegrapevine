package si.timrekelj.thegrapevine

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * The Android analogue of the iOS Keychain: the master key lives in the Android
 * Keystore and the session is stored encrypted, never in plain preferences.
 */
// shortcut: EncryptedSharedPreferences is deprecated but still works; move to a Keystore-backed AES-GCM file if it is removed.
@Suppress("DEPRECATION")
class SessionStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "grapevine.session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(session: Session) {
        prefs.edit().putString(KEY, SupabaseJson.encodeToString(session)).apply()
    }

    fun load(): Session? {
        val raw = prefs.getString(KEY, null) ?: return null
        return runCatching { SupabaseJson.decodeFromString<Session>(raw) }.getOrNull()
    }

    fun delete() {
        prefs.edit().remove(KEY).remove("oauth_verifier").apply()
    }

    fun saveOAuthVerifier(verifier: String) {
        prefs.edit().putString("oauth_verifier", verifier).apply()
    }

    fun takeOAuthVerifier(): String? {
        val verifier = prefs.getString("oauth_verifier", null)
        prefs.edit().remove("oauth_verifier").apply()
        return verifier
    }

    private companion object {
        const val KEY = "session"
    }
}
