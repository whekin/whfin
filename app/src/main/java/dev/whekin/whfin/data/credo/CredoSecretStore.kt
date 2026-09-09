package dev.whekin.whfin.data.credo

import android.content.Context
import dev.whekin.whfin.data.security.EncryptedBankCredentialStore

/** Compatibility facade over the common bank store; existing Credo ciphertext stays readable. */
class CredoSecretStore(context: Context) {
    private val store = EncryptedBankCredentialStore(context, "credo")
    fun hasCredentials() = store.hasCredentials()
    fun savedUsername() = store.savedUsername()
    fun save(credentials: CredoCredentials) = store.save(credentials)
    fun load(): CredoCredentials? = store.load()
    fun clear() = store.clear()

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
