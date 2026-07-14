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
        helper.runMigrationsAndValidate(TEST_DB_ALL, 4, true, *ALL_MIGRATIONS).close()
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

    private companion object {
        const val TEST_DB = "whfin-migration-1-2"
        const val TEST_DB_ALL = "whfin-migration-all"
        const val TEST_DB_2_3 = "whfin-migration-2-3"
        const val TEST_DB_3_4 = "whfin-migration-3-4"
    }
}
