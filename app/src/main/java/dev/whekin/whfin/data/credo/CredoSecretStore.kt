package dev.whekin.whfin.data.credo

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Device-local storage for the optional MyCredo password.
 *
 * The ciphertext lives in a dedicated SharedPreferences file which is not part of WHFIN's
 * Android backup allowlist or portable JSON backup. The AES key is non-exportable and belongs to
 * Android Keystore. WHFIN App Lock remains the product-level gate before this store is used.
 */
class CredoSecretStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun hasCredentials(): Boolean = preferences.contains(CIPHERTEXT) && preferences.contains(IV)

    /**
     * The username is intentionally read from the encrypted payload instead of being duplicated in
     * plaintext preferences.
     */
    fun savedUsername(): String? = load()?.username

    fun save(credentials: CredoCredentials) {
        val plaintext = JSONObject()
            .put("username", credentials.username)
            .put("password", credentials.credential)
            .toString()
            .toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                updateAAD(AAD)
            }
            val encrypted = cipher.doFinal(plaintext)
            check(
                preferences.edit()
                    .putString(IV, cipher.iv.encode())
                    .putString(CIPHERTEXT, encrypted.encode())
                    .commit(),
            ) { "Could not persist encrypted Credo credentials" }
        } finally {
            plaintext.fill(0)
        }
    }

    fun load(): CredoCredentials? {
        val iv = preferences.getString(IV, null)?.decode() ?: return null
        val encrypted = preferences.getString(CIPHERTEXT, null)?.decode() ?: return null
        val plaintext = try {
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
                updateAAD(AAD)
                doFinal(encrypted)
            }
        } catch (_: Exception) {
            clear()
            return null
        }
        val credentials = try {
            val json = JSONObject(plaintext.toString(Charsets.UTF_8))
            CredoCredentials(
                username = json.getString("username"),
                credential = json.getString("password"),
            )
        } catch (_: Exception) {
            clear()
            return null
        } finally {
            plaintext.fill(0)
        }
        return credentials
    }

    fun clear() {
        check(preferences.edit().clear().commit()) { "Could not clear Credo credentials" }
        deleteKey(KEY_ALIAS)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(AES_KEY_BITS)
                .build()
            init(spec)
            generateKey()
        }
    }

    private fun deleteKey(alias: String) {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply {
                load(null)
                if (containsAlias(alias)) deleteEntry(alias)
            }
        }
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decode(): ByteArray? = runCatching { Base64.decode(this, Base64.NO_WRAP) }.getOrNull()

    internal companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "whfin_credo_credentials_aes256_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_BITS = 256
        const val TAG_BITS = 128
        const val PREFERENCES = "whfin_credo_secrets"
        const val IV = "iv"
        const val CIPHERTEXT = "ciphertext"
        val AAD = "whfin:mycredo-credentials:aes256:v1".toByteArray(Charsets.UTF_8)
    }
}
