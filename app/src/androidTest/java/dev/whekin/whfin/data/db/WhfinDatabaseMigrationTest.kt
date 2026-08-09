package dev.whekin.whfin.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhfinDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WhfinDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesExistingRowsAndCreatesDebtTables() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO `people` (`id`, `name`, `role`, `color`, `isArchived`) " +
                    "VALUES (7, 'Alice', 'FRIEND', 4283453520, 0)",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).apply {
            query("SELECT `name`, `role`, `isArchived` FROM `people` WHERE `id` = 7").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("Alice", cursor.getString(0))
                assertEquals("FRIEND", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
            }

            execSQL(
                "INSERT INTO `debt_cases` " +
                    "(`id`, `personId`, `direction`, `originalAmountMinor`, `currency`, `openedAt`, `status`, `closedAt`, `note`) " +
                    "VALUES (11, 7, 'THEY_OWE_ME', 12500, 'GEL', 1000, 'OPEN', NULL, 'Lunch')",
            )
            execSQL(
                "INSERT INTO `debt_events` " +
                    "(`id`, `debtCaseId`, `kind`, `actualAmountMinor`, `actualCurrency`, `accountId`, `transactionId`, `debtValueMinor`, `closesCase`, `occurredAt`, `note`) " +
                    "VALUES (12, 11, 'OPENED', NULL, NULL, NULL, NULL, 0, 0, 1000, NULL)",
            )
            query("SELECT COUNT(*) FROM `debt_events` WHERE `debtCaseId` = 11").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrateAllFromEarliestSchema_matchesCurrentRoomSchema() {
        helper.createDatabase(TEST_DB_ALL, 1).close()
        helper.runMigrationsAndValidate(TEST_DB_ALL, WHFIN_DATABASE_VERSION, true, *ALL_MIGRATIONS).close()
    }

    @Test
    fun migrate2To3_preservesLedgerAndCreatesSmsDiagnostics() {
        helper.createDatabase(TEST_DB_2_3, 2).apply {
            execSQL("INSERT INTO `financial_groups` VALUES (1, 'Example bank', 'BANK', 'Example', 0, 0)")
            execSQL("INSERT INTO `accounts` VALUES (1, 'Example GEL', 'BANK', 1, 'GEL', NULL, NULL, NULL, NULL, NULL, 0, 0)")
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_2_3, 3, true, MIGRATION_2_3).apply {
            query("SELECT `name` FROM `accounts` WHERE `id` = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("Example GEL", cursor.getString(0))
            }
            execSQL(
                "INSERT INTO `sms_diagnostics` " +
                    "(`externalKey`, `kind`, `outcome`, `receivedAt`, `updatedAt`) " +
                    "VALUES ('sms|example', 'CARD_PAYMENT', 'NEEDS_CARD_MAPPING', 1000, 1000)",
            )
            query("SELECT COUNT(*) FROM `sms_diagnostics`").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrate3To4_preservesImportsAndBackfillsOrigin() {
        helper.createDatabase(TEST_DB_3_4, 3).apply {
            execSQL("INSERT INTO `financial_groups` VALUES (1, 'Credo', 'BANK', 'Credo', 0, 0)")
            execSQL("INSERT INTO `accounts` VALUES (1, 'Everyday', 'BANK', 1, 'GEL', 'GE00CD1', NULL, NULL, NULL, NULL, 0, 0)")
            execSQL(
                "INSERT INTO `statement_imports` " +
                    "(`id`, `accountId`, `sourceId`, `fileName`, `periodFrom`, `periodTo`, `openingBalanceMinor`, " +
                    "`closingBalanceMinor`, `totalRows`, `inserted`, `duplicates`, `reconciled`, `reviewCount`, `importedAt`) " +
                    "VALUES (1, 1, NULL, 'MYCREDO_GE00CD1_GEL_STATEMENT_2026_07_14.xlsx', NULL, NULL, NULL, NULL, 4, 0, 4, 0, 0, 1000)",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_3_4, 4, true, MIGRATION_3_4).apply {
            query("SELECT `origin`, `fileName` FROM `statement_imports` WHERE `id` = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("CREDO_SYNC", cursor.getString(0))
                assertEquals("MYCREDO_GE00CD1_GEL_STATEMENT_2026_07_14.xlsx", cursor.getString(1))
            }
            close()
        }
    }

    @Test
    fun migrate4To5_keepsTheLedgerAndAddsAnEmptyBalanceSnapshot() {
        helper.createDatabase(TEST_DB_4_5, 4).apply {
            execSQL("INSERT INTO `financial_groups` VALUES (1, 'Wallet', 'WALLET', 'tron:mainnet', 0, 0)")
            execSQL("INSERT INTO `accounts` VALUES (1, 'Wallet', 'CRYPTO', 1, 'TRX', NULL, NULL, NULL, NULL, NULL, 0, 0)")
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_4_5, 5, true, MIGRATION_4_5).apply {
            query("SELECT `name` FROM `accounts` WHERE `id` = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("Wallet", cursor.getString(0))
            }
            // Never refreshed is the honest starting state, so the new table starts empty.
            query("SELECT COUNT(*) FROM `crypto_balances`").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            execSQL(
                "INSERT INTO `crypto_balances` (`accountId`, `baseUnits`, `decimals`, `observedAt`, `source`) " +
                    "VALUES (1, '1500000', 6, 1000, 'example.test')",
            )
            query("SELECT `baseUnits` FROM `crypto_balances` WHERE `accountId` = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("1500000", cursor.getString(0))
            }
            close()
        }
    }

    @Test
    fun migrate5To6_addsAnEmptyQuoteSnapshot() {
        helper.createDatabase(TEST_DB_5_6, 5).close()

        helper.runMigrationsAndValidate(TEST_DB_5_6, 6, true, MIGRATION_5_6).apply {
            // No quote yet means "show native amounts", never "everything is worth zero".
            query("SELECT COUNT(*) FROM `exchange_rates`").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            execSQL(
                "INSERT INTO `exchange_rates` (`code`, `gelPerUnit`, `observedAt`, `validOn`, `source`) " +
                    "VALUES ('USD', '2.6266', 1000, '2026-07-31', 'example.test')",
            )
            query("SELECT `gelPerUnit` FROM `exchange_rates` WHERE `code` = 'USD'").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("2.6266", cursor.getString(0))
            }
            close()
        }
    }

    @Test
    fun migrate6To7_keepsRowsAndLeavesForeignValuesUnbooked() {
        helper.createDatabase(TEST_DB_6_7, 6).apply {
            execSQL("INSERT INTO `accounts` VALUES (1, 'Everyday', 'BANK', NULL, 'USD', NULL, NULL, NULL, NULL, NULL, 0, 0)")
            execSQL(
                "INSERT INTO `transactions` (`id`, `accountId`, `amountMinor`, `currency`, `occurredAt`, " +
                    "`status`, `source`, `isTransfer`, `createdAt`) " +
                    "VALUES (1, 1, -2500, 'USD', 1000, 'CONFIRMED', 'STATEMENT', 0, 1000)",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_6_7, 7, true, MIGRATION_6_7).apply {
            // Back-valuing at today's rate would make every past month drift, so the column starts empty.
            query("SELECT `amountMinor`, `gelValueMinor`, `gelRateOn` FROM `transactions` WHERE `id` = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(-2500, cursor.getInt(0))
                check(cursor.isNull(1))
                check(cursor.isNull(2))
            }
            execSQL(
                "INSERT INTO `exchange_rate_history` (`code`, `onDate`, `gelPerUnit`, `validOn`, `observedAt`) " +
                    "VALUES ('USD', '2026-03-14', '2.70', '2026-03-14', 5000)",
            )
            query("SELECT COUNT(*) FROM `exchange_rate_history`").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            close()
        }
    }

    @Test
    fun migrate9To10_marksCorrectionsOfRestoredRowsAsRevoked() {
        helper.createDatabase(TEST_DB_9_10, 9).apply {
            execSQL("INSERT INTO `accounts` VALUES (1, 'Everyday', 'BANK', NULL, 'GEL', NULL, NULL, NULL, NULL, NULL, 0, 0)")
            // A row the user corrected and never took back.
            execSQL(
                "INSERT INTO `transactions` (`id`, `accountId`, `amountMinor`, `currency`, `occurredAt`, " +
                    "`status`, `source`, `isTransfer`, `isVoided`, `createdAt`) " +
                    "VALUES (1, 1, -2500, 'GEL', 1000, 'CONFIRMED', 'STATEMENT', 0, 1, 1000)",
            )
            execSQL(
                "INSERT INTO `transactions` (`id`, `accountId`, `amountMinor`, `currency`, `occurredAt`, " +
                    "`status`, `source`, `isTransfer`, `isVoided`, `correctionOfTransactionId`, `createdAt`) " +
                    "VALUES (2, 1, 2500, 'GEL', 1000, 'MANUAL', 'ADJUSTMENT', 1, 1, 1, 2000)",
            )
            // A row an older build restored: it is active again, but its correction still looks live.
            execSQL(
                "INSERT INTO `transactions` (`id`, `accountId`, `amountMinor`, `currency`, `occurredAt`, " +
                    "`status`, `source`, `isTransfer`, `isVoided`, `createdAt`) " +
                    "VALUES (3, 1, -700, 'GEL', 1000, 'CONFIRMED', 'STATEMENT', 0, 0, 3000)",
            )
            execSQL(
                "INSERT INTO `transactions` (`id`, `accountId`, `amountMinor`, `currency`, `occurredAt`, " +
                    "`status`, `source`, `isTransfer`, `isVoided`, `correctionOfTransactionId`, `createdAt`) " +
                    "VALUES (4, 1, 700, 'GEL', 1000, 'MANUAL', 'ADJUSTMENT', 1, 1, 3, 4000)",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_9_10, 10, true, MIGRATION_9_10).apply {
            query("SELECT `correctionRevokedAt` FROM `transactions` WHERE `id` = 2").use { cursor ->
                check(cursor.moveToFirst())
                check(cursor.isNull(0)) { "A correction of a still-voided row stays active" }
            }
            query("SELECT `correctionRevokedAt` FROM `transactions` WHERE `id` = 4").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(4000, cursor.getLong(0))
            }
            close()
        }
    }

    private companion object {
        const val TEST_DB = "whfin-migration-1-2"
        const val TEST_DB_ALL = "whfin-migration-all"
        const val TEST_DB_2_3 = "whfin-migration-2-3"
        const val TEST_DB_3_4 = "whfin-migration-3-4"
        const val TEST_DB_4_5 = "whfin-migration-4-5"
        const val TEST_DB_5_6 = "whfin-migration-5-6"
        const val TEST_DB_6_7 = "whfin-migration-6-7"
        const val TEST_DB_9_10 = "whfin-migration-9-10"
    }
}
