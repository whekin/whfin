package dev.whekin.whfin.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Confirmations start empty, and that is the honest starting value.
 *
 * Nothing in an existing ledger records which credit was the pay — that answer only ever came from
 * the owner — so backfilling it would mean guessing exactly the thing the table exists to stop the
 * app from guessing. An upgraded install therefore asks its questions again, once.
 */
@RunWith(AndroidJUnit4::class)
class IncomeSourcePaymentMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WhfinDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration4To5_addsConfirmationsAndKeepsTheDeclaration() {
        helper.createDatabase(DATABASE_NAME, 4).apply {
            execSQL("INSERT INTO financial_groups VALUES (1, 'Credo', 'BANK', 'Credo', 0, 0)")
            execSQL(
                "INSERT INTO accounts (id, name, type, groupId, currency, iban, fundRole, " +
                    "bankProduct, isArchived, sortOrder) VALUES " +
                    "(1, 'Everyday', 'BANK', 1, 'GEL', 'GE00CD0000000000000001', 'AVAILABLE', " +
                    "'CURRENT_ACCOUNT', 0, 0)",
            )
            execSQL(
                "INSERT INTO income_sources (id, label, amountMinor, currency, accountId, " +
                    "expectedDayFrom, expectedDayTo, weekendRule, startedOn, endedOn, createdAt) " +
                    "VALUES (1, 'Salary', 270000, 'GEL', 1, 5, 5, 'LATER', 20500, NULL, 1780000000000)",
            )
            execSQL(
                "INSERT INTO transactions (id, accountId, amountMinor, currency, occurredAt, " +
                    "status, source, isTransfer, isVoided, createdAt) VALUES " +
                    "(1, 1, 270000, 'GEL', 1780000000000, 'CONFIRMED', 'STATEMENT', 0, 0, 1780000000000)",
            )
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 5, true, MIGRATION_4_5).use { migrated ->
            migrated.query("SELECT * FROM income_source_payments").use { cursor ->
                assertEquals(0, cursor.count)
            }
            migrated.query("SELECT weekendRule, amountMinor FROM income_sources").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("LATER", cursor.getString(0))
                assertEquals(270_000L, cursor.getLong(1))
            }
            migrated.query("SELECT amountMinor FROM transactions WHERE id = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(270_000L, cursor.getLong(0))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "income-source-payment-migration.db"
    }
}
