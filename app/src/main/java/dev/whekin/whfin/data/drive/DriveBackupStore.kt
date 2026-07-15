package dev.whekin.whfin.data.drive

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Локальное состояние Drive-бэкапа + passphrase для автоматической загрузки.
 *
 * Passphrase хранится как AES-GCM ciphertext под non-exportable Android Keystore key —
 * тот же паттерн, что CredoSecretStore. Файл prefs намеренно не входит ни в Android backup
 * allowlist, ни в portable JSON: при переносе на новое устройство пароль вводится заново.
 */
class DriveBackupStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean(ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(ENABLED, value).apply()
        }

    var lastSuccessAt: Long
        get() = preferences.getLong(LAST_SUCCESS_AT, 0L)
        set(value) {
            preferences.edit().putLong(LAST_SUCCESS_AT, value).apply()
        }

    /** Человекочитаемый код последней ошибки; null — последняя попытка успешна. */
    var lastError: String?
        get() = preferences.getString(LAST_ERROR, null)
        set(value) {
            preferences.edit().putString(LAST_ERROR, value).apply()
        }

    /** true, когда silent-авторизация перестала работать и нужен явный вход из UI. */
    var needsReauth: Boolean
        get() = preferences.getBoolean(NEEDS_REAUTH, false)
        set(value) {
            preferences.edit().putBoolean(NEEDS_REAUTH, value).apply()
        }

    fun hasPassphrase(): Boolean = preferences.contains(CIPHERTEXT) && preferences.contains(IV)

    fun savePassphrase(passphrase: CharArray) {
        val plaintext = String(passphrase).toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            }
            val encrypted = cipher.doFinal(plaintext)
            check(
                preferences.edit()
                    .putString(IV, cipher.iv.encode())
                    .putString(CIPHERTEXT, encrypted.encode())
                    .commit(),
            ) { "Could not persist encrypted Drive passphrase" }
        } finally {
            plaintext.fill(0)
        }
    }

    fun loadPassphrase(): CharArray? {
        val iv = preferences.getString(IV, null)?.decode() ?: return null
        val encrypted = preferences.getString(CIPHERTEXT, null)?.decode() ?: return null
        val plaintext = try {
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
                doFinal(encrypted)
            }
        } catch (_: Exception) {
            clearPassphrase()
            return null
        }
        return try {
            plaintext.toString(Charsets.UTF_8).toCharArray()
        } finally {
            plaintext.fill(0)
        }
    }

    fun clearPassphrase() {
        preferences.edit().remove(IV).remove(CIPHERTEXT).apply()
    }

    fun clearAll() {
        preferences.edit().clear().apply()
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
        const val KEY_ALIAS = "whfin_drive_passphrase_aes_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val PREFERENCES = "whfin_drive_backup"
        const val ENABLED = "enabled"
        const val LAST_SUCCESS_AT = "last_success_at"
        const val LAST_ERROR = "last_error"
        const val NEEDS_REAUTH = "needs_reauth"
        const val IV = "iv"
        const val CIPHERTEXT = "ciphertext"
    }
}
