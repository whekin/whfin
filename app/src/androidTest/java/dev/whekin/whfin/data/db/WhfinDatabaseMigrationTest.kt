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
        helper.runMigrationsAndValidate(TEST_DB_ALL, 2, true, *ALL_MIGRATIONS).close()
    }

    private companion object {
        const val TEST_DB = "whfin-migration-1-2"
        const val TEST_DB_ALL = "whfin-migration-all"
    }
}
