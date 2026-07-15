package dev.whekin.whfin.data.drive

import android.content.Context
import dev.whekin.whfin.data.backup.WhfinBackupManager
import dev.whekin.whfin.data.backup.WhfinBackupMetadata
import dev.whekin.whfin.data.backup.WhfinBackupSummary
import dev.whekin.whfin.data.backup.WhfinEncryptedBackupEnvelope
import dev.whekin.whfin.data.db.WhfinDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DriveBackupMissingPassphraseException :
    Exception("A backup passphrase must be set before uploading to Google Drive.")

/**
 * Оркестрация бэкапа в скрытую appDataFolder: всегда шифрованный конверт,
 * поверх личной базы (не demo). Хранит не больше [KEEP_COPIES] последних копий.
 */
class DriveBackupManager(
    context: Context,
    private val database: WhfinDatabase,
    private val client: DriveBackupClient = DriveBackupClient(),
) {
    private val store = DriveBackupStore(context.applicationContext)
    private val backups = WhfinBackupManager(database)

    data class UploadResult(val fileName: String, val rowCount: Int, val sizeBytes: Int)

    suspend fun backupNow(accessToken: String, appVersion: String): UploadResult =
        withContext(Dispatchers.IO) {
            val passphrase = store.loadPassphrase() ?: throw DriveBackupMissingPassphraseException()
            val envelope = ByteArrayOutputStream()
            val summary = try {
                backups.exportEncrypted(
                    envelope,
                    WhfinBackupMetadata(
                        exportedAt = Instant.now(),
                        appVersion = appVersion,
                        primaryCurrency = "GEL",
                    ),
                    passphrase,
                )
            } finally {
                passphrase.fill(' ')
            }
            val bytes = envelope.toByteArray()
            val name = "whfin-backup-${FILE_STAMP.format(Instant.now())}." +
                WhfinEncryptedBackupEnvelope.FILE_EXTENSION
            client.upload(accessToken, name, bytes)
            rotate(accessToken)
            UploadResult(fileName = name, rowCount = summary.rowCount, sizeBytes = bytes.size)
        }

    suspend fun listBackups(accessToken: String): List<DriveBackupFile> =
        withContext(Dispatchers.IO) { client.list(accessToken) }

    suspend fun restore(accessToken: String, fileId: String, passphrase: CharArray): WhfinBackupSummary =
        withContext(Dispatchers.IO) {
            val envelope = client.download(accessToken, fileId)
            backups.restore(ByteArrayInputStream(envelope), passphrase)
        }

    private fun rotate(accessToken: String) {
        val obsolete = client.list(accessToken)
            .sortedByDescending { it.createdAt ?: Instant.EPOCH }
            .drop(KEEP_COPIES)
        obsolete.forEach { file ->
            // Ошибка удаления старой копии не должна ронять успешный бэкап.
            runCatching { client.delete(accessToken, file.id) }
        }
    }

    companion object {
        const val KEEP_COPIES = 10
        private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss").withZone(ZoneOffset.UTC)
    }
}
