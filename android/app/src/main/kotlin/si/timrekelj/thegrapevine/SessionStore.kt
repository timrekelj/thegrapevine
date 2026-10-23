package si.timrekelj.thegrapevine

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Android analogue of the iOS Keychain: an AES-GCM key that never leaves the Android Keystore
 * encrypts the session, so the preferences file only ever holds ciphertext. Each value is bound to
 * its name, so one can't be swapped for another. The first use opens the Keystore, which is slow:
 * callers keep it off the main thread.
 */
class SessionStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        // The deprecated EncryptedSharedPreferences file this replaced; its session can't be read
        // without Tink, so that sign-in is simply asked for again.
        if (File(context.dataDir, "shared_prefs/$LEGACY_FILE.xml").exists()) {
            context.deleteSharedPreferences(LEGACY_FILE)
            runCatching { keyStore().deleteEntry(LEGACY_KEY) }
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    fun save(session: Session) = put(KEY, SupabaseJson.encodeToString(session))

    fun load(): Session? {
        val raw = get(KEY) ?: return null
        return runCatching { SupabaseJson.decodeFromString<Session>(raw) }.getOrNull()
    }

    fun delete() {
        prefs.edit { remove(KEY).remove(VERIFIER) }
    }

    fun saveOAuthVerifier(verifier: String) = put(VERIFIER, verifier)

    fun takeOAuthVerifier(): String? = get(VERIFIER).also { prefs.edit { remove(VERIFIER) } }

    private fun put(name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        cipher.updateAAD(name.toByteArray())
        val sealed = cipher.iv + cipher.doFinal(value.toByteArray())
        prefs.edit { putString(name, Base64.encodeToString(sealed, Base64.NO_WRAP)) }
    }

    private fun get(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        return try {
            val sealed = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            cipher.updateAAD(name.toByteArray())
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES).decodeToString()
        } catch (_: Exception) {
            // The key is gone (restored or transferred data, cleared credentials) or the value was
            // tampered with: it can't be read, so start over rather than fail on every launch.
            prefs.edit { remove(name) }
            null
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    /** Made once per install (two screens restoring at once must not each make one), then only looked up. */
    private val key: SecretKey by lazy {
        (keyStore().getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val FILE = "grapevine.secure"
        const val KEY = "session"
        const val VERIFIER = "oauth_verifier"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "grapevine.session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val LEGACY_FILE = "grapevine.session"
        const val LEGACY_KEY = "_androidx_security_master_key_"
    }
}
