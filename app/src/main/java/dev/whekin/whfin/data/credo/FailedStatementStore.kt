package dev.whekin.whfin.data.credo

import android.content.Context
import java.io.File
import java.io.OutputStream
import org.json.JSONObject

/**
 * The exact statement bytes that were downloaded and then refused, kept until the user asks for them.
 *
 * A statement that fails our own rules can only be diagnosed from the file itself, and the failure is
 * seen minutes or hours after the run: the screen is left, the process dies, a retry starts. Holding
 * the bytes in process memory lost them to all three, so the one action that could explain the
 * failure silently did nothing.
 *
 * This is a diagnostic drawer, not an export. It lives in app-private storage beside the database,
 * carries the same exposure the database already has, and is absent from the Android backup
 * allowlist and from every JSON backup. Nothing here is uploaded, and it is bounded: expired and
 * surplus entries are dropped, because bank statements are not something to accumulate on disk.
 */
class FailedStatementStore(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val directory = File(context.filesDir, DIRECTORY_NAME)

    /**
     * One kept failure. The label is the masked one already shown on screen and the window is dates
     * only: no amounts, no full account number.
     */
    data class Entry(
        val token: String,
        val accountLabel: String,
        val fileName: String,
        val errorCode: String?,
        val detail: String?,
        val askedFrom: String?,
        val askedTo: String?,
        val savedAt: Long,
    )

    fun save(
        accountLabel: String,
        fileName: String,
        errorCode: String?,
        detail: String?,
        askedFrom: String?,
        askedTo: String?,
        bytes: ByteArray,
    ): Entry? {
        if (!directory.exists() && !directory.mkdirs()) return null
        // The newest failure of an account replaces its predecessor: the same statement failing again
        // is the same evidence, and keeping every attempt would fill the drawer with copies.
        forget(accountLabel)
        val savedAt = now()
        val entry = Entry(
            token = "$savedAt-${accountLabel.hashCode().toUInt().toString(16)}",
            accountLabel = accountLabel,
            fileName = fileName,
            errorCode = errorCode,
            detail = detail,
            askedFrom = askedFrom,
            askedTo = askedTo,
            savedAt = savedAt,
        )
        return runCatching {
            File(directory, entry.token + STATEMENT_SUFFIX).writeBytes(bytes)
            File(directory, entry.token + METADATA_SUFFIX).writeText(entry.toJson())
            prune()
            entry
        }.getOrElse {
            // Half of a kept failure is not evidence; it would only promise a file that cannot open.
            remove(entry.token)
            null
        }
    }

    /** Kept failures, newest first. Expired and surplus entries are dropped as they are read. */
    fun entries(): List<Entry> {
        prune()
        return storedEntries()
    }

    fun copyTo(token: String, output: OutputStream): Boolean {
        val statement = File(directory, token + STATEMENT_SUFFIX)
        if (!statement.isFile) return false
        return runCatching { statement.inputStream().use { it.copyTo(output) } }.isSuccess
    }

    /** Called when an account's statement finally imports: the old evidence describes nothing now. */
    fun forget(accountLabel: String) {
        storedEntries()
            .filter { it.accountLabel == accountLabel }
            .forEach { remove(it.token) }
    }

    fun clear() {
        directory.listFiles().orEmpty().forEach { it.delete() }
    }

    private fun storedEntries(): List<Entry> = directory.listFiles()
        .orEmpty()
        .filter { it.name.endsWith(METADATA_SUFFIX) }
        .mapNotNull { file -> runCatching { entryFrom(JSONObject(file.readText())) }.getOrNull() }
        .filter { File(directory, it.token + STATEMENT_SUFFIX).isFile }
        .sortedByDescending(Entry::savedAt)

    private fun prune() {
        val expiredBefore = now() - MAX_AGE_MILLIS
        val stored = storedEntries()
        stored.filter { it.savedAt < expiredBefore }.forEach { remove(it.token) }
        stored.filter { it.savedAt >= expiredBefore }.drop(KEEP).forEach { remove(it.token) }
    }

    private fun remove(token: String) {
        File(directory, token + STATEMENT_SUFFIX).delete()
        File(directory, token + METADATA_SUFFIX).delete()
    }

    private fun Entry.toJson(): String = JSONObject()
        .put("token", token)
        .put("accountLabel", accountLabel)
        .put("fileName", fileName)
        .put("errorCode", errorCode)
        .put("detail", detail)
        .put("askedFrom", askedFrom)
        .put("askedTo", askedTo)
        .put("savedAt", savedAt)
        .toString()

    private fun entryFrom(json: JSONObject) = Entry(
        token = json.getString("token"),
        accountLabel = json.getString("accountLabel"),
        fileName = json.getString("fileName"),
        errorCode = json.optionalString("errorCode"),
        detail = json.optionalString("detail"),
        askedFrom = json.optionalString("askedFrom"),
        askedTo = json.optionalString("askedTo"),
        savedAt = json.getLong("savedAt"),
    )

    /** `optString` turns a JSON null into the text "null"; an absent field must stay absent. */
    private fun JSONObject.optionalString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)

    private companion object {
        const val DIRECTORY_NAME = "failed-statements"
        const val STATEMENT_SUFFIX = ".xlsx"
        const val METADATA_SUFFIX = ".json"

        /** Enough to hold one bad run across every ledger of a bank without becoming an archive. */
        const val KEEP = 8
        const val MAX_AGE_MILLIS = 14L * 24 * 60 * 60 * 1000
    }
}
