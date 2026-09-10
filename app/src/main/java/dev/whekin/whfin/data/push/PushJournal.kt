package dev.whekin.whfin.data.push

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Bounded, local diagnostic drawer, excluded from both Android and portable backup. */
class PushJournal(context: Context, private val now: () -> Long = System::currentTimeMillis, private val limit: Int = LIMIT) {
    init { require(limit in 1..LIMIT) }
    private val file = AtomicFile(File(context.noBackupFilesDir, "tbc-push-journal.bin"))
    data class Entry(val id: String, val capturedAt: Long, val push: BankPush, val outcome: String, val diagnosticId: Long? = null) {
        override fun toString() = "PushJournal.Entry(redacted)"
        fun json() = JSONObject().put("id", id).put("capturedAt", capturedAt).put("notification", push.json())
            .put("outcome", outcome).put("diagnosticId", diagnosticId ?: JSONObject.NULL)
        companion object {
            fun fromJson(o: JSONObject) = Entry(o.getString("id"), o.getLong("capturedAt"), BankPush.fromJson(o.getJSONObject("notification")),
                o.getString("outcome"), o.optLong("diagnosticId").takeIf { it > 0 })
        }
    }
    fun entries(): List<Entry> = synchronized(lock) {
        val all = read()
        val kept = all.filter { it.capturedAt >= now() - RETENTION }.takeLast(limit)
        if (kept != all) write(kept)
        kept.reversed()
    }
    fun record(push: BankPush, outcome: String, diagnosticId: Long? = null): Entry = synchronized(lock) {
        require(push.packageName == TbcPush.PACKAGE && !TbcPush.sensitive(push))
        val id = TbcPush.hash(push.json().toString())
        val all = entries().reversed()
        val entry = Entry(id, all.firstOrNull { it.id == id }?.capturedAt ?: now(), push, outcome, diagnosticId)
        write((all.filterNot { it.id == id } + entry).takeLast(limit))
        entry
    }
    fun clear() = synchronized(lock) { file.delete() }
    private fun read(): List<Entry> {
        if (!file.baseFile.exists()) return emptyList()
        val bytes = file.readFully()
        require(bytes.size in 29..MAX_BYTES)
        val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            updateAAD(ALIAS.toByteArray()); doFinal(bytes.copyOfRange(12, bytes.size))
        }
        return try { JSONArray(plain.toString(Charsets.UTF_8)).let { a -> (0 until a.length()).map { Entry.fromJson(a.getJSONObject(it)) } } }
        finally { plain.fill(0) }
    }
    private fun write(entries: List<Entry>) {
        var kept = entries
        var plain = JSONArray(kept.map { it.json() }).toString().toByteArray()
        while (plain.size >= MAX_BYTES - 100 && kept.size > 1) {
            plain.fill(0); kept = kept.drop(1)
            plain = JSONArray(kept.map { it.json() }).toString().toByteArray()
        }
        require(plain.size < MAX_BYTES - 100)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(ALIAS.toByteArray()) }
            val encrypted = cipher.doFinal(plain)
            val output = file.startWrite()
            try { output.write(cipher.iv); output.write(encrypted); file.finishWrite(output) }
            catch (e: Exception) { file.failWrite(output); throw e }
        } finally { plain.fill(0) }
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            generateKey()
        }
    }
    companion object {
        private val lock = Any()
        private const val ALIAS = "whfin_tbc_push_journal_v1"
        const val LIMIT = 300
        const val RETENTION = 30L * 24 * 60 * 60 * 1000
        private const val MAX_BYTES = 8 * 1024 * 1024
    }
}
