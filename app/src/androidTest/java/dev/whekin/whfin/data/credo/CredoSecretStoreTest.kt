package dev.whekin.whfin.data.credo

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import java.security.KeyStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredoSecretStoreTest {
    private lateinit var context: Context
    private lateinit var store: CredoSecretStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = CredoSecretStore(context)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun credentials_roundTripThroughAndroidKeystore_andCanBeForgotten() {
        store.save(CredoCredentials("private-user", "private-password"))

        assertTrue(store.hasCredentials())
        assertEquals("private-user", store.savedUsername())
        assertEquals(CredoCredentials("private-user", "private-password"), store.load())
        assertNull(
            context.getSharedPreferences(CredoSecretStore.PREFERENCES, Context.MODE_PRIVATE)
                .getString(CredoSecretStore.LEGACY_USERNAME, null),
        )

        val keyStore = KeyStore.getInstance(CredoSecretStore.ANDROID_KEYSTORE).apply { load(null) }
        val key = keyStore.getKey(CredoSecretStore.KEY_ALIAS, null) as SecretKey
        val keyInfo = SecretKeyFactory
            .getInstance(key.algorithm, CredoSecretStore.ANDROID_KEYSTORE)
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo
        assertEquals(CredoSecretStore.AES_KEY_BITS, keyInfo.keySize)

        store.clear()
        assertFalse(store.hasCredentials())
        assertEquals(null, store.load())
        assertFalse(keyStore.containsAlias(CredoSecretStore.KEY_ALIAS))
    }

    @Test
    fun legacyCredentials_areMigratedWithoutPlaintextUsername() {
        val legacyKey = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            CredoSecretStore.ANDROID_KEYSTORE,
        ).run {
            init(
                KeyGenParameterSpec.Builder(
                    CredoSecretStore.LEGACY_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
        val plaintext = JSONObject()
            .put("username", "legacy-user")
            .put("password", "legacy-password")
            .toString()
            .toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(CredoSecretStore.TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, legacyKey)
        }
        val encrypted = try {
            cipher.doFinal(plaintext)
        } finally {
            plaintext.fill(0)
        }
        val preferences = context.getSharedPreferences(CredoSecretStore.PREFERENCES, Context.MODE_PRIVATE)
        assertTrue(
            preferences.edit()
                .putString(CredoSecretStore.LEGACY_USERNAME, "legacy-user")
                .putString(CredoSecretStore.IV, cipher.iv.encode())
                .putString(CredoSecretStore.CIPHERTEXT, encrypted.encode())
                .commit(),
        )

        assertEquals(CredoCredentials("legacy-user", "legacy-password"), store.load())
        assertEquals(CredoSecretStore.CURRENT_KEY_VERSION, preferences.getInt(CredoSecretStore.KEY_VERSION, 0))
        assertFalse(preferences.contains(CredoSecretStore.LEGACY_USERNAME))

        val keyStore = KeyStore.getInstance(CredoSecretStore.ANDROID_KEYSTORE).apply { load(null) }
        assertFalse(keyStore.containsAlias(CredoSecretStore.LEGACY_KEY_ALIAS))
        assertTrue(keyStore.containsAlias(CredoSecretStore.KEY_ALIAS))
        assertEquals(CredoCredentials("legacy-user", "legacy-password"), store.load())
    }

    @Test
    fun corruptedAuthenticatedPayload_isForgottenInsteadOfCrashing() {
        store.save(CredoCredentials("private-user", "private-password"))
        val preferences = context.getSharedPreferences(CredoSecretStore.PREFERENCES, Context.MODE_PRIVATE)
        assertTrue(
            preferences.edit()
                .putString(CredoSecretStore.CIPHERTEXT, byteArrayOf(1, 2, 3, 4).encode())
                .commit(),
        )

        assertNull(store.load())
        assertFalse(store.hasCredentials())
        val keyStore = KeyStore.getInstance(CredoSecretStore.ANDROID_KEYSTORE).apply { load(null) }
        assertFalse(keyStore.containsAlias(CredoSecretStore.KEY_ALIAS))
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)
}
