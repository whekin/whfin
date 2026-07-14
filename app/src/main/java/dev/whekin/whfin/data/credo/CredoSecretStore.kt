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

    fun savedUsername(): String? = preferences.getString(USERNAME, null)

    fun save(credentials: CredoCredentials) {
        val plaintext = JSONObject()
            .put("username", credentials.username)
            .put("password", credentials.credential)
            .toString()
            .toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            }
            val encrypted = cipher.doFinal(plaintext)
            check(
                preferences.edit()
                    .putString(USERNAME, credentials.username)
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
                doFinal(encrypted)
            }
        } catch (_: Exception) {
            clear()
            return null
        }
        return try {
            val json = JSONObject(plaintext.toString(Charsets.UTF_8))
            CredoCredentials(
                username = json.getString("username"),
                credential = json.getString("password"),
            )
        } finally {
            plaintext.fill(0)
        }
    }

    fun clear() {
        check(preferences.edit().clear().commit()) { "Could not clear Credo credentials" }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decode(): ByteArray? = runCatching { Base64.decode(this, Base64.NO_WRAP) }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "whfin_credo_credentials_aes_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val PREFERENCES = "whfin_credo_secrets"
        const val USERNAME = "username"
        const val IV = "iv"
        const val CIPHERTEXT = "ciphertext"
    }
}
