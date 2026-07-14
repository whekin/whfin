package dev.whekin.whfin.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

sealed interface PinVerificationResult {
    data object Success : PinVerificationResult
    data class Invalid(val attemptsRemaining: Int) : PinVerificationResult
    data class Locked(val retryAfterMillis: Long) : PinVerificationResult
}

class AppLockPinStore(
    context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun hasPin(): Boolean = preferences.contains(SALT) && preferences.contains(DIGEST)

    fun setPin(pin: CharArray) {
        require(pin.size == PIN_LENGTH && pin.all(Char::isDigit))
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val digest = try {
            digest(pin, salt)
        } finally {
            pin.fill('\u0000')
        }
        check(preferences.edit()
            .putString(SALT, salt.encode())
            .putString(DIGEST, digest.encode())
            .remove(FAILED_ATTEMPTS)
            .remove(LOCKED_UNTIL)
            .commit()) { "Could not persist the WHFIN App Lock verifier" }
    }

    fun verify(pin: CharArray): PinVerificationResult {
        val now = nowMillis()
        val lockedUntil = preferences.getLong(LOCKED_UNTIL, 0L)
        if (lockedUntil > now) {
            pin.fill('\u0000')
            return PinVerificationResult.Locked(lockedUntil - now)
        }
        val salt = preferences.getString(SALT, null)?.decode()
        val expected = preferences.getString(DIGEST, null)?.decode()
        if (salt == null || expected == null) {
            pin.fill('\u0000')
            return PinVerificationResult.Invalid(MAX_ATTEMPTS)
        }
        val actual = try {
            digest(pin, salt)
        } finally {
            pin.fill('\u0000')
        }
        if (MessageDigest.isEqual(expected, actual)) {
            preferences.edit().remove(FAILED_ATTEMPTS).remove(LOCKED_UNTIL).apply()
            return PinVerificationResult.Success
        }
        val failures = preferences.getInt(FAILED_ATTEMPTS, 0) + 1
        return if (failures >= MAX_ATTEMPTS) {
            preferences.edit()
                .putInt(FAILED_ATTEMPTS, 0)
                .putLong(LOCKED_UNTIL, now + LOCKOUT_MILLIS)
                .apply()
            PinVerificationResult.Locked(LOCKOUT_MILLIS)
        } else {
            preferences.edit().putInt(FAILED_ATTEMPTS, failures).apply()
            PinVerificationResult.Invalid(MAX_ATTEMPTS - failures)
        }
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun digest(pin: CharArray, salt: ByteArray): ByteArray {
        val key = getOrCreateKey()
        val mac = Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256).apply { init(key) }
        mac.update(salt)
        val bytes = ByteArray(pin.size) { index -> pin[index].code.toByte() }
        return try {
            mac.doFinal(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                ).build(),
            )
            generateKey()
        }
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decode(): ByteArray? = runCatching { Base64.decode(this, Base64.NO_WRAP) }.getOrNull()

    companion object {
        const val PIN_LENGTH = 4
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_MILLIS = 30_000L
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "whfin_app_lock_hmac_v1"
        private const val PREFERENCES = "whfin_app_lock"
        private const val SALT = "salt"
        private const val DIGEST = "digest"
        private const val FAILED_ATTEMPTS = "failed_attempts"
        private const val LOCKED_UNTIL = "locked_until"
    }
}
