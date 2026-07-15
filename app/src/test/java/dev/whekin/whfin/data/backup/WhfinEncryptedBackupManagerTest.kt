package dev.whekin.whfin.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WhfinEncryptedBackupManagerTest {
    private lateinit var db: WhfinDatabase
    private lateinit var manager: WhfinBackupManager
    private var txId: Long = 0

    private val metadata = WhfinBackupMetadata(
        exportedAt = Instant.parse("2026-07-15T12:00:00Z"),
        appVersion = "test",
        primaryCurrency = "GEL",
    )

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        manager = WhfinBackupManager(db)
        val accountId = db.accountDao().insert(
            AccountEntity(name = "Cash", type = AccountType.CASH, currency = "GEL"),
        )
        val categoryId = db.categoryDao().insert(
            CategoryEntity(name = "Groceries", kind = CategoryKind.EXPENSE, icon = "ShoppingCart", color = 1),
        )
        txId = db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId, amountMinor = -1234, currency = "GEL",
                occurredAt = 1_700_000_000_000, categoryId = categoryId,
                status = TxStatus.MANUAL, source = TxSource.MANUAL, createdAt = 1,
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun encryptedRoundtripRestoresEveryRow() = runBlocking {
        val output = ByteArrayOutputStream()
        val exported = manager.exportEncrypted(output, metadata, "battery staple".toCharArray())
        val envelope = output.toByteArray()

        assertTrue(manager.isEncrypted(ByteArrayInputStream(envelope)))

        db.transactionDao().delete(txId)
        assertEquals(null, db.transactionDao().byId(txId))

        val restored = manager.restore(ByteArrayInputStream(envelope), "battery staple".toCharArray())
        assertEquals(exported.rowCount, restored.rowCount)
        assertEquals(-1234L, db.transactionDao().byId(txId)?.amountMinor)
    }

    @Test
    fun wrongPassphraseLeavesDataUntouched() = runBlocking {
        val output = ByteArrayOutputStream()
        manager.exportEncrypted(output, metadata, "battery staple".toCharArray())

        assertThrows(WhfinBackupPassphraseException::class.java) {
            runBlocking {
                manager.restore(ByteArrayInputStream(output.toByteArray()), "wrong pass".toCharArray())
            }
        }
        assertEquals(-1234L, db.transactionDao().byId(txId)?.amountMinor)
    }

    @Test
    fun encryptedRestoreWithoutPassphraseAsksForIt() {
        val output = ByteArrayOutputStream()
        runBlocking { manager.exportEncrypted(output, metadata, "battery staple".toCharArray()) }

        assertThrows(WhfinBackupPassphraseException::class.java) {
            runBlocking { manager.restore(ByteArrayInputStream(output.toByteArray())) }
        }
    }

    @Test
    fun plainExportStillRestoresWithoutPassphrase() = runBlocking {
        val output = ByteArrayOutputStream()
        val exported = manager.export(output, metadata)
        val json = output.toByteArray()

        assertEquals(false, manager.isEncrypted(ByteArrayInputStream(json)))
        val restored = manager.restore(ByteArrayInputStream(json))
        assertEquals(exported.rowCount, restored.rowCount)
    }
}
