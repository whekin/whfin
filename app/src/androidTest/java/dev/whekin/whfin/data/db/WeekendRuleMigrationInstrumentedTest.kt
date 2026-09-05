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
 * A declaration written under the old mandatory date range keeps every number it had.
 *
 * The range is gone from the product, not from the file: `expectedDayTo` stays where it was so an
 * older backup still restores, and the payday now reads from `expectedDayFrom` alone. `EARLIER` is
 * the default because the old code shifted a weekend payday to the *next* weekday — so this
 * migration deliberately changes what an untouched declaration estimates. It is the safe direction:
 * an estimate that lands a day early is money the owner already has, and one that lands late is
 * money they planned around and do not.
 */
@RunWith(AndroidJUnit4::class)
class WeekendRuleMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WhfinDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration3To4_addsTheWeekendRuleAndKeepsTheDeclaration() {
        helper.createDatabase(DATABASE_NAME, 3).apply {
            execSQL("INSERT INTO financial_groups VALUES (1, 'Credo', 'BANK', 'Credo', 0, 0)")
            execSQL(
                "INSERT INTO accounts (id, name, type, groupId, currency, iban, fundRole, " +
                    "bankProduct, isArchived, sortOrder) VALUES " +
                    "(1, 'Everyday', 'BANK', 1, 'GEL', 'GE00CD0000000000000001', 'AVAILABLE', " +
                    "'CURRENT_ACCOUNT', 0, 0)",
            )
            execSQL(
                "INSERT INTO income_sources (id, label, amountMinor, currency, accountId, " +
                    "expectedDayFrom, expectedDayTo, startedOn, endedOn, createdAt) VALUES " +
                    "(1, 'Salary', 270000, 'USDT', 1, 5, 10, 20500, NULL, 1780000000000)",
            )
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 4, true, MIGRATION_3_4).use { migrated ->
            migrated.query(
                "SELECT label, amountMinor, currency, accountId, expectedDayFrom, expectedDayTo, " +
                    "weekendRule, startedOn FROM income_sources",
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("Salary", cursor.getString(0))
                assertEquals(270_000L, cursor.getLong(1))
                assertEquals("USDT", cursor.getString(2))
                assertEquals(1L, cursor.getLong(3))
                assertEquals(5, cursor.getInt(4))
                // The old outer bound is kept as written; nothing reads it any more.
                assertEquals(10, cursor.getInt(5))
                assertEquals("EARLIER", cursor.getString(6))
                assertEquals(20_500L, cursor.getLong(7))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "weekend-rule-migration.db"
    }
}
