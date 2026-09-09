package dev.whekin.whfin.data.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BankCredentialStoreTest {
    @Test fun bankIsolationRecreationAndNoPlaintextCredentials() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = EncryptedBankCredentialStore(context, "banktesta")
        val second = EncryptedBankCredentialStore(context, "banktestb")
        val value = BankCredentials("example_user", "example_password")
        try {
            first.save(value); second.save(BankCredentials("another_user", "another_password"))
            assertEquals(value, EncryptedBankCredentialStore(context, "banktesta").load())
            val prefs = context.getSharedPreferences("whfin_banktesta_secrets", Context.MODE_PRIVATE)
            assertFalse(prefs.all.values.joinToString().contains(value.username))
            assertFalse(prefs.all.values.joinToString().contains(value.credential))
            val copied = context.getSharedPreferences("whfin_banktestb_secrets", Context.MODE_PRIVATE)
            copied.edit().putString("iv", prefs.getString("iv", null)).putString("ciphertext", prefs.getString("ciphertext", null)).commit()
            assertNull(second.load())
            assertEquals(value, first.load())
            assertEquals("BankCredentials(redacted)", value.toString())
        } finally { first.clear(); second.clear() }
    }
    @Test fun preexistingCredoCiphertextUsesTheSameAliasAadAndPayload() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = EncryptedBankCredentialStore(context, "credo")
        store.clear()
        try {
            val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder("whfin_credo_credentials_aes256_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
                generateKey()
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key)
                updateAAD("whfin:mycredo-credentials:aes256:v1".toByteArray())
            }
            val payload = JSONObject().put("username", "legacy_user").put("password", "legacy_password").toString().toByteArray()
            val encrypted = cipher.doFinal(payload)
            context.getSharedPreferences("whfin_credo_secrets", Context.MODE_PRIVATE).edit()
                .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()
            assertEquals(BankCredentials("legacy_user", "legacy_password"), store.load())
        } finally { store.clear() }
    }
}
