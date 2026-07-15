package dev.whekin.whfin.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.whekin.whfin.data.db.WhfinDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhfinBackupInstrumentedTest {
    private lateinit var source: WhfinDatabase
    private lateinit var target: WhfinDatabase

    @Before
    fun createDatabases() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        source = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java).build()
        target = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java).build()
    }

    @After
    fun closeDatabases() {
        source.close()
        target.close()
    }

    @Test
    fun schemaAllowlist_coversEveryCurrentRoomTableAndColumn() {
        val sqlite = source.openHelper.writableDatabase
        val actualTables = buildSet {
            sqlite.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table', 'android_metadata') ORDER BY name",
            ).use { cursor -> while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertEquals(WhfinBackupSchema.byName.keys + WhfinBackupSchema.excludedTables, actualTables)

        WhfinBackupSchema.tables.forEach { table ->
            val actualColumns = buildList {
                sqlite.query("PRAGMA table_info(`${table.name}`)").use { cursor ->
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
            }
            assertEquals("Columns changed for ${table.name}", table.columns, actualColumns)
        }
    }

    @Test
    fun exportRestore_roundTripsEveryTableDeterministically() = runBlocking {
        seedEveryTable(source)
        target.openHelper.writableDatabase.execSQL(
            "INSERT INTO `people` (`id`, `name`, `role`, `color`, `isArchived`) VALUES (99, 'Junk', NULL, 1, 0)",
        )

        val original = export(source)
        val summary = WhfinBackupManager(target).restore(ByteArrayInputStream(original))
        val restored = export(target)

        assertEquals(20, summary.rowCount)
        assertEquals(original.toString(Charsets.UTF_8), restored.toString(Charsets.UTF_8))
    }

    @Test
    fun restore_rejectsFutureFormatWithoutChangingCurrentData() = runBlocking {
        seedEveryTable(source)
        target.openHelper.writableDatabase.execSQL(
            "INSERT INTO `people` (`id`, `name`, `role`, `color`, `isArchived`) VALUES (99, 'Keep me', NULL, 1, 0)",
        )
        val future = export(source).toString(Charsets.UTF_8)
            .replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")

        assertThrows(WhfinBackupException::class.java) {
            runBlocking {
                WhfinBackupManager(target).restore(ByteArrayInputStream(future.toByteArray()))
            }
        }
        target.openHelper.writableDatabase.query("SELECT name FROM people WHERE id = 99").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals("Keep me", cursor.getString(0))
        }
    }

    @Test
    fun restore_rejectsMalformedJson() {
        assertThrows(WhfinBackupException::class.java) {
            runBlocking {
                WhfinBackupManager(target).restore(ByteArrayInputStream("{not-json".toByteArray()))
            }
        }
    }

    @Test
    fun restore_acceptsVersion2BackupAndClearsLocalDiagnostics() = runBlocking {
        seedEveryTable(source)
        target.openHelper.writableDatabase.execSQL(
            "INSERT INTO sms_diagnostics (externalKey, kind, outcome, receivedAt, updatedAt) " +
                "VALUES ('sms|local', 'UNRECOGNIZED', 'UNRECOGNIZED', 1, 1)",
        )
        val version2 = export(source).toString(Charsets.UTF_8)
            .replace("\"databaseVersion\": 4", "\"databaseVersion\": 2")
            .replace("        \"origin\": \"FILE\",\n", "")

        WhfinBackupManager(target).restore(ByteArrayInputStream(version2.toByteArray()))

        target.openHelper.writableDatabase.query("SELECT COUNT(*) FROM sms_diagnostics").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun restore_rejectsCurrentBackupMissingStatementOrigin() = runBlocking {
        seedEveryTable(source)
        val missingOrigin = export(source).toString(Charsets.UTF_8)
            .replace("        \"origin\": \"FILE\",\n", "")

        assertThrows(WhfinBackupException::class.java) {
            runBlocking {
                WhfinBackupManager(target).restore(ByteArrayInputStream(missingOrigin.toByteArray()))
            }
        }
        Unit
    }

    @Test
    fun demoFixture_restoresRichPublicScenario() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val summary = context.assets.open("whfin-demo-v4.json").use { input ->
            WhfinBackupManager(target).restore(input)
        }
        val sqlite = target.openHelper.writableDatabase

        assertEquals(4, summary.databaseVersion)
        check(summary.rowCount >= 200) { "Demo fixture became too small for representative UI states." }
        assertEquals(8, sqlite.longForQuery("SELECT COUNT(*) FROM accounts"))
        check(sqlite.longForQuery("SELECT COUNT(*) FROM transactions") >= 120)
        assertEquals(12, sqlite.longForQuery(
            "SELECT COUNT(DISTINCT strftime('%Y-%m', occurredAt / 1000, 'unixepoch')) " +
                "FROM transactions WHERE source != 'ADJUSTMENT'",
        ))
        assertEquals(2, sqlite.longForQuery("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING'"))
        assertEquals(15, sqlite.longForQuery("SELECT COUNT(*) FROM transfer_groups"))
        assertEquals(3, sqlite.longForQuery("SELECT COUNT(*) FROM debt_cases"))
        assertEquals(4, sqlite.longForQuery("SELECT COUNT(*) FROM statement_imports"))
        assertEquals(1, sqlite.longForQuery("SELECT COUNT(*) FROM reconciliation_issues WHERE state = 'OPEN'"))
        assertEquals(0, sqlite.longForQuery("SELECT COUNT(*) FROM accounts WHERE iban IS NOT NULL AND iban NOT LIKE 'GE00%'"))
        sqlite.query("PRAGMA foreign_key_check").use { cursor ->
            check(!cursor.moveToFirst()) { "Demo fixture contains broken foreign keys." }
        }

        val exported = export(target)
        val roundTrip = WhfinBackupCodec.read(ByteArrayInputStream(exported))
        assertEquals(summary.rowCount, roundTrip.summary.rowCount)
    }

    private suspend fun export(db: WhfinDatabase): ByteArray {
        val output = ByteArrayOutputStream()
        WhfinBackupManager(db).export(output, METADATA)
        return output.toByteArray()
    }

    private fun seedEveryTable(db: WhfinDatabase) {
        val sqlite = db.openHelper.writableDatabase
        sqlite.beginTransaction()
        try {
            sqlite.execSQL("INSERT INTO financial_groups VALUES (1, 'Credo', 'BANK', 'Credo', 0, 0)")
            sqlite.execSQL("INSERT INTO financial_groups VALUES (2, 'Wallet', 'WALLET', 'TrustWallet', 0, 1)")
            sqlite.execSQL("INSERT INTO wallet_addresses VALUES (1, 2, 'eip155:1', '0xabc', 'Main')")
            sqlite.execSQL("INSERT INTO crypto_assets VALUES (1, 'eip155:1', NULL, 'ETH', 'Ether', 18)")
            sqlite.execSQL("INSERT INTO accounts VALUES (1, 'Credo GEL', 'BANK', 1, 'GEL', 'GE01', NULL, NULL, NULL, NULL, 0, 0)")
            sqlite.execSQL("INSERT INTO accounts VALUES (2, 'ETH', 'CRYPTO', 2, 'ETH', NULL, 1, 1, NULL, NULL, 0, 1)")
            sqlite.execSQL("INSERT INTO payment_instruments VALUES (1, 1, 'PHYSICAL_CARD', '0001', 'Main card', 0)")
            sqlite.execSQL("INSERT INTO instrument_account_links VALUES (1, 1)")
            sqlite.execSQL("INSERT INTO transfer_groups VALUES (1, 'TRANSFER', 'Test transfer', 1000)")
            sqlite.execSQL("INSERT INTO statement_sources VALUES (1, 1, 'ACCOUNT', 1, NULL, 'GE01')")
            sqlite.execSQL("INSERT INTO categories VALUES (1, 'Groceries', NULL, 'EXPENSE', 'ShoppingCart', -123, 0, 0)")
            sqlite.execSQL("INSERT INTO merchants VALUES (1, 'nikora', 'Nikora', 1)")
            sqlite.execSQL("INSERT INTO merchant_aliases VALUES (1, 1, 'nikora trade')")
            sqlite.execSQL("INSERT INTO people VALUES (1, 'Alice', 'FRIEND', -456, 0)")
            sqlite.execSQL(
                "INSERT INTO transactions VALUES " +
                    "(1, 1, -1250, 'GEL', NULL, NULL, 2000, NULL, 1, 'NIKORA', NULL, 1, " +
                    "'Lunch', 'CONFIRMED', 'STATEMENT', 1, 1, 10000, 'tx-1', 3000)",
            )
            sqlite.execSQL("INSERT INTO transaction_allocations VALUES (1, 1, -1250, 1, 1, 'SHARED', 'Half')")
            sqlite.execSQL("INSERT INTO debt_cases VALUES (1, 1, 'THEY_OWE_ME', 1250, 'GEL', 2000, 'OPEN', NULL, 'Lunch')")
            sqlite.execSQL("INSERT INTO debt_events VALUES (1, 1, 'OPENED', NULL, NULL, NULL, NULL, 0, 0, 2000, NULL)")
            sqlite.execSQL("INSERT INTO statement_imports VALUES (1, 1, 1, 'statement.xlsx', 'FILE', 1, 31, 0, 10000, 1, 1, 0, 0, 0, 4000)")
            sqlite.execSQL("INSERT INTO reconciliation_issues VALUES (1, 1, 1, 1, 'OPEN', 5000)")
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.longForQuery(sql: String): Long =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private companion object {
        val METADATA = WhfinBackupMetadata(
            exportedAt = Instant.parse("2026-07-14T00:00:00Z"),
            appVersion = "0.1.0 (1)",
            primaryCurrency = "GEL",
        )
    }
}
