package dev.whekin.whfin.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.PersonEntity
import dev.whekin.whfin.data.db.WhfinDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Restoring is the one action that deletes everything the user owns. These tests hold it to the
 * promise that it can always be taken back.
 */
@RunWith(AndroidJUnit4::class)
class RestoreSafetyBackupInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: WhfinDatabase
    private lateinit var safety: RestoreSafetyBackup

    private val directory get() = File(context.filesDir, "restore-safety")

    private val metadata = WhfinBackupMetadata(
        exportedAt = Instant.parse("2026-08-09T10:00:00Z"),
        appVersion = "test",
        primaryCurrency = "GEL",
    )

    @Before
    fun setUp() {
        directory.deleteRecursively()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java).build()
        safety = RestoreSafetyBackup(context, appVersion = "test")
    }

    @After
    fun tearDown() {
        db.close()
        directory.deleteRecursively()
    }

    private suspend fun person(name: String) =
        db.personDao().insert(PersonEntity(name = name, color = 1))

    private suspend fun names(): List<String> =
        db.openHelper.writableDatabase.query("SELECT name FROM people ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    @Test
    fun aWrongRestore_canBeTakenBackFromTheCopyItTriggered() = runBlocking {
        person("Keep me")
        val foreign = ByteArrayOutputStream().also { output ->
            val other = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java).build()
            other.personDao().insert(PersonEntity(name = "Someone else", color = 2))
            WhfinBackupManager(other).export(output, metadata)
            other.close()
        }.toByteArray()

        WhfinBackupManager(db, safety).restore(ByteArrayInputStream(foreign))
        assertEquals(listOf("Someone else"), names())

        val snapshot = requireNotNull(safety.latest()) { "the restore must leave a way back" }
        WhfinBackupManager(db, safety).restore(snapshot.file.inputStream())

        assertEquals(listOf("Keep me"), names())
    }

    @Test
    fun anUnreadableFile_costsNeitherTheLedgerNorASnapshot() = runBlocking {
        person("Keep me")

        assertThrows(WhfinBackupException::class.java) {
            runBlocking {
                WhfinBackupManager(db, safety).restore(ByteArrayInputStream("{nope".toByteArray()))
            }
        }

        assertEquals(listOf("Keep me"), names())
        // The file never got far enough to threaten anything, so no copy was spent on it.
        assertNull(safety.latest())
    }

    @Test
    fun whenNoSnapshotCanBeWritten_theRestoreDoesNotHappen() = runBlocking {
        person("Keep me")
        val backup = ByteArrayOutputStream().also { output ->
            val other = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java).build()
            other.accountDao().insert(AccountEntity(name = "Cash", type = AccountType.CASH, currency = "GEL"))
            WhfinBackupManager(other).export(output, metadata)
            other.close()
        }.toByteArray()
        // A plain file where the directory has to be: writing the snapshot cannot succeed.
        directory.parentFile?.mkdirs()
        directory.writeText("in the way")

        assertThrows(WhfinBackupException::class.java) {
            runBlocking { WhfinBackupManager(db, safety).restore(ByteArrayInputStream(backup)) }
        }

        assertEquals(listOf("Keep me"), names())
        directory.delete()
        Unit
    }

    @Test
    fun onlyTheTwoMostRecentCopiesAreKept() = runBlocking {
        person("Keep me")
        repeat(3) { safety.capture(db) }

        val files = directory.listFiles().orEmpty().filter { it.name.startsWith("pre-restore-") }
        assertEquals(2, files.size)
        assertTrue(files.all { it.length() > 0 })
    }
}
