package si.timrekelj.thegrapevine

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * The Android analogue of the iOS Keychain: the master key lives in the Android
 * Keystore and the session is stored encrypted, never in plain preferences.
 * Opening the store is slow (Keystore + Tink), so it happens on first use, off the main thread.
 */
// shortcut: EncryptedSharedPreferences is deprecated but still works; move to a Keystore-backed AES-GCM file if it is removed.
@Suppress("DEPRECATION")
class SessionStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            open()
        } catch (_: Exception) {
            // The Keystore key is gone (restored or transferred data, cleared credentials): the stored
            // session can't be read anyway, so start over rather than fail on every launch.
            context.deleteSharedPreferences(FILE)
            open()
        }
    }

    private fun open(): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(session: Session) {
        prefs.edit { putString(KEY, SupabaseJson.encodeToString(session)) }
    }

    fun load(): Session? {
        val raw = prefs.getString(KEY, null) ?: return null
        return runCatching { SupabaseJson.decodeFromString<Session>(raw) }.getOrNull()
    }

    fun delete() {
        prefs.edit { remove(KEY).remove(VERIFIER) }
    }

    fun saveOAuthVerifier(verifier: String) {
        prefs.edit { putString(VERIFIER, verifier) }
    }

    fun takeOAuthVerifier(): String? {
        val verifier = prefs.getString(VERIFIER, null)
        prefs.edit { remove(VERIFIER) }
        return verifier
    }

    private companion object {
        const val FILE = "grapevine.session"
        const val KEY = "session"
        const val VERIFIER = "oauth_verifier"
    }
}
