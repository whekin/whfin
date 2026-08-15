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
    fun migration1To2_preservesCardsAndDefaultsPrimaryToFalse() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO financial_groups (id, name, type, provider, isArchived, sortOrder) " +
                    "VALUES (1, 'Credo', 'BANK', 'Credo', 0, 0)",
            )
            execSQL(
                "INSERT INTO payment_instruments (id, groupId, type, last4, label, isArchived) " +
                    "VALUES (1, 1, 'PHYSICAL_CARD', '0001', 'Everyday', 0)",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { database ->
            database.query(
                "SELECT last4, isPrimary FROM payment_instruments WHERE id = 1",
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("0001", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
        }
    }

    @Test
    fun migration2To3_addsEmptyCounterpartyRulesAndKeepsLedger() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO accounts (id, name, type, currency, isArchived, sortOrder) " +
                    "VALUES (1, 'Everyday', 'BANK', 'GEL', 0, 0)",
            )
            execSQL(
                "INSERT INTO transactions (id, accountId, amountMinor, currency, occurredAt, " +
                    "postedAt, status, source, isTransfer, isVoided, createdAt) " +
                    "VALUES (1, 1, -1500, 'GEL', 100, 100, 'CONFIRMED', 'STATEMENT', 0, 0, 100)",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3).use { database ->
            database.query("SELECT COUNT(*) FROM counterparty_rules").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT amountMinor FROM transactions WHERE id = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(-1500, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migration3To4_addsEmptyIncomeSourcesAndKeepsRules() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                "INSERT INTO counterparty_rules (id, iban, displayName, categoryId, personId, createdAt) " +
                    "VALUES (1, 'GE00WH0000000000000042', 'Example Person', NULL, NULL, 100)",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4).use { database ->
            database.query("SELECT COUNT(*) FROM income_sources").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT displayName FROM counterparty_rules WHERE id = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("Example Person", cursor.getString(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "whfin-migration-test"
    }
}
