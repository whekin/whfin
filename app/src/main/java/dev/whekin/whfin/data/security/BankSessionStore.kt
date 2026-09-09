package dev.whekin.whfin.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Opaque bank session only; never a password. Caller owns the App Lock gate and opt-in. */
interface BankSessionStore {
    fun hasSaved(): Boolean
    fun load(): String?
    fun save(value: String)
    fun clear()
}
class EncryptedBankSessionStore(context: Context, bank: String) : BankSessionStore {
    init { require(bank.matches(Regex("[a-z]+"))) }
    private val alias = "whfin_${bank}_session_v1"
    private val file = AtomicFile(File(context.noBackupFilesDir, "$alias.bin"))
    private val aad = alias.toByteArray(Charsets.UTF_8)
    override fun hasSaved() = file.baseFile.exists()
    override fun save(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key()); updateAAD(aad)
            }
            val encrypted = cipher.doFinal(bytes)
            val output = file.startWrite()
            try {
                output.write(cipher.iv); output.write(encrypted); file.finishWrite(output)
            } catch (error: Exception) { file.failWrite(output); throw error }
        } finally { bytes.fill(0) }
    }
    override fun load(): String? {
        if (!hasSaved()) return null
        return try {
            val bytes = file.readFully()
            require(bytes.size in 29..65536)
            val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                updateAAD(aad); doFinal(bytes.copyOfRange(12, bytes.size))
            }
            try { plain.toString(Charsets.UTF_8) } finally { plain.fill(0) }
        } catch (_: Exception) { clear(); null }
    }
    override fun clear() {
        file.delete()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }
}
