package dev.whekin.whfin.data.backup

import android.content.Context
import dev.whekin.whfin.data.db.WhfinDatabase
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The copy WHFIN takes of itself immediately before a restore replaces everything.
 *
 * Restoring is the one action that deletes every row the user owns, and the file being restored is
 * often the reason they doubt their data in the first place. Taking this snapshot first means a
 * wrong file costs a tap to undo instead of a year of history. It never leaves app-private storage:
 * it is written beside the database, so it carries exactly the same exposure the database already
 * has and no more — it is not an export, not shared, and not part of the Android backup allowlist.
 *
 * If the snapshot cannot be written the restore does not proceed. A destructive step without a way
 * back is precisely what this exists to prevent.
 */
class RestoreSafetyBackup(
    context: Context,
    private val appVersion: String,
    private val primaryCurrency: String = "GEL",
) {
    private val directory = File(context.filesDir, DIRECTORY_NAME)

    data class Snapshot(val file: File, val takenAt: Instant, val rowCount: Int)

    suspend fun capture(database: WhfinDatabase): Snapshot = withContext(Dispatchers.IO) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw WhfinBackupException("Could not prepare the safety copy directory.")
        }
        val takenAt = Instant.now()
        val target = File(directory, "$FILE_PREFIX${takenAt.toEpochMilli()}$FILE_SUFFIX")
        val summary = try {
            target.outputStream().use { output ->
                WhfinBackupManager(database).export(
                    output,
                    WhfinBackupMetadata(
                        exportedAt = takenAt,
                        appVersion = appVersion,
                        primaryCurrency = primaryCurrency,
                    ),
                )
            }
        } catch (error: Throwable) {
            // A half-written snapshot is worse than none: it would offer a way back that is not there.
            target.delete()
            throw WhfinBackupException("Could not write a safety copy before restoring.", error)
        }
        prune()
        Snapshot(target, takenAt, summary.rowCount)
    }

    /** The most recent snapshot, or null when no restore has been attempted on this device. */
    fun latest(): Snapshot? = snapshots().firstOrNull()

    private fun snapshots(): List<Snapshot> = directory.listFiles()
        .orEmpty()
        .mapNotNull { file ->
            val stamp = file.name.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX)
            if (!file.name.startsWith(FILE_PREFIX) || !file.name.endsWith(FILE_SUFFIX)) return@mapNotNull null
            val millis = stamp.toLongOrNull() ?: return@mapNotNull null
            Snapshot(file, Instant.ofEpochMilli(millis), rowCount = 0)
        }
        .sortedByDescending { it.takenAt }

    private fun prune() {
        snapshots().drop(KEEP).forEach { it.file.delete() }
    }

    private companion object {
        const val DIRECTORY_NAME = "restore-safety"
        const val FILE_PREFIX = "pre-restore-"
        const val FILE_SUFFIX = ".json"

        /**
         * Two: the copy from the restore being undone, and the one taken while undoing it. Keeping
         * more would quietly grow a pile of full ledgers on disk for no extra safety.
         */
        const val KEEP = 2
    }
}
