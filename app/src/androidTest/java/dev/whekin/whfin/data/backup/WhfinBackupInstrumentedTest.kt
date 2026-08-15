package dev.whekin.whfin.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whekin.whfin.data.demo.DemoDataInstaller
import dev.whekin.whfin.data.db.WHFIN_DATABASE_VERSION
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.integrity.DataIntegrityChecker
import dev.whekin.whfin.data.mutation.TransactionMutationModule
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.ZoneOffset
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

        assertEquals(26, summary.rowCount)
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
    fun restore_rejectsBackupFromAnotherDatabaseContractWithoutChangingCurrentData() = runBlocking {
        seedEveryTable(source)
        target.openHelper.writableDatabase.execSQL(
            "INSERT INTO `people` (`id`, `name`, `role`, `color`, `isArchived`) " +
                "VALUES (99, 'Keep me', NULL, 1, 0)",
        )
        val otherContract = export(source).toString(Charsets.UTF_8)
            .replace(
                "\"databaseVersion\": $WHFIN_DATABASE_VERSION",
                "\"databaseVersion\": ${WHFIN_DATABASE_VERSION + 1}",
            )

        assertThrows(WhfinBackupException::class.java) {
            runBlocking {
                WhfinBackupManager(target).restore(ByteArrayInputStream(otherContract.toByteArray()))
            }
        }
        target.openHelper.writableDatabase.query("SELECT name FROM people WHERE id = 99").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals("Keep me", cursor.getString(0))
        }
    }

    @Test
    fun restore_acceptsVersion1CardsWithoutPrimaryFlag() = runBlocking {
        seedEveryTable(source)
        val version1 = export(source).toString(Charsets.UTF_8)
            .replace("\"databaseVersion\": $WHFIN_DATABASE_VERSION", "\"databaseVersion\": 1")
            .replace("        \"isPrimary\": 0,\n", "")

        WhfinBackupManager(target).restore(ByteArrayInputStream(version1.toByteArray()))

        val card = target.paymentInstrumentDao().forAccount(1).single()
        assertEquals("0001", card.last4)
        assertEquals(false, card.isPrimary)
    }

    /**
     * A copy taken before recipient rules existed is still a valid copy: nothing had been taught
     * yet. Refusing it would retire the user's own safety files the moment a feature is added.
     */
    @Test
    fun restore_acceptsABackupTakenBeforeRecipientRulesExisted() = runBlocking {
        seedEveryTable(source)
        val exported = export(source).toString(Charsets.UTF_8)
        val withoutRules = exported.replace(
            Regex(""",\s*"counterparty_rules": \[[^]]*]"""),
            "",
        )
        assertEquals(false, withoutRules.contains("counterparty_rules"))

        WhfinBackupManager(target).restore(ByteArrayInputStream(withoutRules.toByteArray()))

        assertEquals(emptyList<Long>(), target.counterpartyRuleDao().all().map { it.id })
        assertEquals("Alice", target.personDao().byId(1)?.name)
    }

    /** A table that carries money is never optional: an absent one would restore a broken ledger. */
    @Test
    fun restore_stillRejectsABackupMissingATableThatCarriesMoney() = runBlocking {
        seedEveryTable(source)
        val withoutAllocations = export(source).toString(Charsets.UTF_8).replace(
            Regex(""",\s*"transaction_allocations": \[[^]]*]"""),
            "",
        )

        assertThrows(WhfinBackupException::class.java) {
            runBlocking {
                WhfinBackupManager(target).restore(ByteArrayInputStream(withoutAllocations.toByteArray()))
            }
        }
        Unit
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
    fun restore_rejectsAnUnknownEnumValueBeforeTouchingTheLedger() = runBlocking {
        seedEveryTable(source)
        seedEveryTable(target)
        val broken = export(source).toString(Charsets.UTF_8)
            .replace("\"direction\": \"THEY_OWE_ME\"", "\"direction\": \"I_OWE\"")

        assertThrows(WhfinBackupException::class.java) {
            runBlocking { WhfinBackupManager(target).restore(ByteArrayInputStream(broken.toByteArray())) }
        }
        // Room would only fail later, while observing a query, so the file must be refused up front.
        assertEquals(1, target.openHelper.writableDatabase.longForQuery("SELECT COUNT(*) FROM debt_cases"))
        Unit
    }

    @Test
    fun demoFixture_restoresRichPublicScenario() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val summary = context.assets.open(DemoDataInstaller.ASSET_NAME).use { input ->
            WhfinBackupManager(target).restore(input)
        }
        val sqlite = target.openHelper.writableDatabase

        assertEquals(WHFIN_DATABASE_VERSION, summary.databaseVersion)
        check(summary.rowCount >= 300) { "Demo fixture became too small for representative UI states." }
        assertEquals(10, sqlite.longForQuery("SELECT COUNT(*) FROM accounts"))
        check(sqlite.longForQuery("SELECT COUNT(*) FROM transactions") >= 250)
        assertEquals(12, sqlite.longForQuery(
            "SELECT COUNT(DISTINCT strftime('%Y-%m', occurredAt / 1000, 'unixepoch')) " +
                "FROM transactions WHERE source != 'ADJUSTMENT'",
        ))
        assertEquals(2, sqlite.longForQuery("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING'"))
        // Money leaves the deposit before it can be spent, so the everyday ledgers stay thin.
        check(
            sqlite.longForQuery("SELECT SUM(amountMinor) FROM transactions WHERE accountId = 3") >
                10 * sqlite.longForQuery("SELECT SUM(amountMinor) FROM transactions WHERE accountId = 1"),
        ) { "Demo deposits should hold far more than the card ledger." }
        // Foreign money must be visible both as a bank conversion and as an FX card charge.
        check(sqlite.longForQuery("SELECT COUNT(*) FROM transfer_groups WHERE type = 'CONVERSION'") >= 4)
        check(sqlite.longForQuery("SELECT COUNT(*) FROM transactions WHERE origCurrency IS NOT NULL") >= 12)
        assertEquals(3, sqlite.longForQuery("SELECT COUNT(DISTINCT currency) FROM transactions WHERE currency != 'GEL'") + 1)
        // The same ticker on two chains stays two different assets.
        assertEquals(3, sqlite.longForQuery("SELECT COUNT(*) FROM accounts WHERE type = 'CRYPTO'"))
        assertEquals(2, sqlite.longForQuery("SELECT COUNT(*) FROM crypto_assets WHERE symbol = 'USDT'"))
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

    @Test
    fun demoInstaller_shiftsFixtureWithoutTouchingUserDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        source.openHelper.writableDatabase.execSQL(
            "INSERT INTO people (id, name, role, color, isArchived) VALUES (999, 'Keep me', NULL, 1, 0)",
        )
        val fixedClock = Clock.fixed(Instant.parse("2027-08-20T12:00:00Z"), ZoneOffset.UTC)

        DemoDataInstaller(context, target, fixedClock).install()

        assertEquals(1, source.openHelper.writableDatabase.longForQuery("SELECT COUNT(*) FROM people WHERE id = 999"))
        assertEquals(0, target.openHelper.writableDatabase.longForQuery("SELECT COUNT(*) FROM people WHERE id = 999"))
        val sqlite = target.openHelper.writableDatabase
        check(sqlite.longForQuery("SELECT COUNT(*) FROM transactions") >= 250)
        // The demo has to read as current: its newest row lands in the days before "today".
        val latest = Instant.ofEpochMilli(sqlite.longForQuery("SELECT MAX(occurredAt) FROM transactions"))
            .atZone(ZoneOffset.UTC).toLocalDate()
        val today = LocalDate.parse("2027-08-20")
        check(!latest.isAfter(today) && ChronoUnit.DAYS.between(latest, today) <= 7) {
            "Demo fixture drifted away from the installation date: $latest"
        }
        // Chain balances cannot travel in a backup, so the installer seeds them itself.
        assertEquals(3, sqlite.longForQuery("SELECT COUNT(*) FROM crypto_balances"))
        assertEquals(0, sqlite.longForQuery("SELECT COUNT(*) FROM crypto_balances WHERE baseUnits = '0'"))
    }

    /**
     * The copy a person actually keeps is the encrypted one, and what they need back is not just
     * rows but which of those rows currently count. A correction hides its source from every active
     * projection while both stay in the file, so a restore that quietly reactivates either would
     * bring back money the user already decided was wrong.
     */
    @Test
    fun encryptedRoundTrip_keepsCorrectedRowsCorrectedAndRestoredRowsRestored() = runBlocking {
        seedEveryTable(source)
        val mutations = TransactionMutationModule(source)
        val corrected = statementRow(source, id = 20, amountMinor = -5_000, key = "tx-corrected")
        val restored = statementRow(source, id = 21, amountMinor = -7_000, key = "tx-restored")
        mutations.voidTransaction(corrected, reason = "Refunded")
        mutations.voidTransaction(restored)
        mutations.restoreTransaction(restored)
        val balanceBefore = source.transactionDao().sumByAccount(1)

        val encrypted = ByteArrayOutputStream()
        WhfinBackupManager(source).exportEncrypted(encrypted, METADATA, PASSPHRASE.copyOf())
        WhfinBackupManager(target).restore(
            ByteArrayInputStream(encrypted.toByteArray()),
            PASSPHRASE.copyOf(),
        )

        // Byte equality of the plain exports proves every column survived, provenance included.
        assertEquals(
            export(source).toString(Charsets.UTF_8),
            export(target).toString(Charsets.UTF_8),
        )
        assertEquals(balanceBefore, target.transactionDao().sumByAccount(1))
        assertEquals(true, target.transactionDao().byId(corrected)?.isVoided)
        assertEquals(false, target.transactionDao().byId(restored)?.isVoided)
        assertEquals(1, target.transactionDao().activeCorrectionsFor(corrected).size)
        // The withdrawn correction is still filed, it just no longer claims anything.
        assertEquals(0, target.transactionDao().activeCorrectionsFor(restored).size)
        assertEquals(1, target.transactionDao().correctionsFor(restored).size)
        // Audit rows never re-enter a balance, whichever side they ended on.
        val auditRows = target.transactionDao().allForIntegrity()
            .filter { it.correctionOfTransactionId != null }
        assertEquals(2, auditRows.size)
        assertEquals(emptyList<Long>(), auditRows.filterNot { it.isVoided }.map { it.id })
        val report = DataIntegrityChecker(target).run()
        assertEquals(report.issues.joinToString { "${it.code}:${it.entity}#${it.entityId}" }, true, report.isHealthy)
    }

    @Test
    fun restore_rejectsABackupFromANewerDatabaseWithoutTouchingCurrentData() = runBlocking {
        seedEveryTable(source)
        seedEveryTable(target)
        val newer = export(source).toString(Charsets.UTF_8)
            .replace(
                "\"databaseVersion\": $WHFIN_DATABASE_VERSION",
                "\"databaseVersion\": ${WHFIN_DATABASE_VERSION + 1}",
            )

        assertThrows(WhfinBackupException::class.java) {
            runBlocking { WhfinBackupManager(target).restore(ByteArrayInputStream(newer.toByteArray())) }
        }
        // An unreadable future file must never be treated as "restore what you can".
        assertEquals(1, target.openHelper.writableDatabase.longForQuery("SELECT COUNT(*) FROM accounts WHERE id = 1"))
        Unit
    }

    private suspend fun statementRow(db: WhfinDatabase, id: Long, amountMinor: Long, key: String): Long {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO transactions (id, accountId, amountMinor, currency, occurredAt, status, " +
                "source, isTransfer, externalKey, createdAt) VALUES " +
                "($id, 1, $amountMinor, 'GEL', 2000, 'CONFIRMED', 'STATEMENT', 0, '$key', 3000)",
        )
        return id
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
            sqlite.execSQL(
                "INSERT INTO accounts VALUES " +
                    "(1, 'Credo GEL', 'BANK', 1, 'GEL', 'GE01', NULL, NULL, NULL, NULL, " +
                    "'AVAILABLE', 'CURRENT_ACCOUNT', 0, 0)",
            )
            sqlite.execSQL(
                "INSERT INTO accounts VALUES " +
                    "(2, 'ETH', 'CRYPTO', 2, 'ETH', NULL, 1, 1, NULL, NULL, " +
                    "'AVAILABLE', NULL, 0, 1)",
            )
            sqlite.execSQL(
                "INSERT INTO accounts VALUES " +
                    "(3, 'Credo reserve', 'SAVINGS', 1, 'GEL', 'GE02', NULL, NULL, NULL, " +
                    "'FLEXIBLE_RESERVE', 'RESERVE', NULL, 0, 2)",
            )
            sqlite.execSQL("INSERT INTO payment_instruments VALUES (1, 1, 'PHYSICAL_CARD', '0001', 'Main card', 0, 0)")
            sqlite.execSQL("INSERT INTO instrument_account_links VALUES (1, 1)")
            sqlite.execSQL("INSERT INTO transfer_groups VALUES (1, 'TRANSFER', 'Test transfer', 1000)")
            sqlite.execSQL("INSERT INTO statement_sources VALUES (1, 1, 'ACCOUNT', 1, NULL, 'GE01')")
            sqlite.execSQL("INSERT INTO categories VALUES (1, 'Groceries', NULL, 'EXPENSE', 'ShoppingCart', -123, 0, 0)")
            sqlite.execSQL("INSERT INTO merchants VALUES (1, 'nikora', 'Nikora', 1)")
            sqlite.execSQL("INSERT INTO merchant_aliases VALUES (1, 1, 'nikora trade')")
            sqlite.execSQL("INSERT INTO people VALUES (1, 'Alice', 'FRIEND', -456, 0)")
            sqlite.execSQL(
                "INSERT INTO counterparty_rules VALUES (1, 'GE00WH0000000000000042', 'Alice', 1, 1, 6000)",
            )
            sqlite.execSQL(
                "INSERT INTO income_sources VALUES (1, 'Salary', 270000, 'USDT', 1, 5, 10, 20000, NULL, 6000)",
            )
            // Named columns on purpose: a positional insert breaks on every new column.
            sqlite.execSQL(
                "INSERT INTO transactions (" +
                    "id, accountId, amountMinor, currency, occurredAt, merchantId, rawCounterparty, " +
                    "categoryId, note, status, source, transferGroupId, isTransfer, " +
                    "balanceAfterMinor, externalKey, gelValueMinor, gelRateOn, createdAt" +
                    ") VALUES (" +
                    "1, 1, -1250, 'GEL', 2000, 1, 'NIKORA', 1, 'Lunch', 'CONFIRMED', 'STATEMENT', " +
                    "1, 1, 10000, 'tx-1', -3400, '2026-03-14', 3000)",
            )
            // Both legs, because a transfer group with one leg is the corruption the integrity
            // checks look for — a fixture must not ship the shape it is meant to catch.
            sqlite.execSQL(
                "INSERT INTO transactions (" +
                    "id, accountId, amountMinor, currency, occurredAt, status, source, " +
                    "transferGroupId, isTransfer, externalKey, createdAt" +
                    ") VALUES (" +
                    "2, 3, 1250, 'GEL', 2000, 'CONFIRMED', 'STATEMENT', 1, 1, 'tx-2', 3000)",
            )
            // A split belongs to a purchase, never to a leg of a transfer between own accounts.
            sqlite.execSQL(
                "INSERT INTO transactions (" +
                    "id, accountId, amountMinor, currency, occurredAt, merchantId, rawCounterparty, " +
                    "categoryId, status, source, isTransfer, externalKey, createdAt" +
                    ") VALUES (" +
                    "3, 1, -2400, 'GEL', 2500, 1, 'NIKORA', 1, 'CONFIRMED', 'STATEMENT', 0, 'tx-3', 3500)",
            )
            sqlite.execSQL("INSERT INTO transaction_allocations VALUES (1, 3, -1200, 1, 1, 'SHARED', 'Half')")
            sqlite.execSQL("INSERT INTO transaction_allocations VALUES (2, 3, -1200, 1, NULL, 'PERSONAL', NULL)")
            sqlite.execSQL("INSERT INTO debt_cases VALUES (1, 1, 'THEY_OWE_ME', 1250, 'GEL', 2000, 'OPEN', NULL, 'Lunch')")
            sqlite.execSQL(
                "INSERT INTO debt_events (id, debtCaseId, kind, actualAmountMinor, actualCurrency, " +
                    "accountId, transactionId, debtValueMinor, closesCase, occurredAt, note) " +
                    "VALUES (1, 1, 'OPENED', NULL, NULL, NULL, NULL, 0, 0, 2000, NULL)",
            )
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
        val PASSPHRASE = "correct horse battery staple".toCharArray()
    }
}
