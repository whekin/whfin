package dev.whekin.whfin.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The deposit number arrives beside the money already recorded, not instead of it.
 *
 * Two nullable columns is the whole change, and null is the honest starting value: no notice has been
 * placed by hand yet, so no deposit claims a number. What has to survive is everything else — this is
 * the first schema change made while somebody's real ledger is on the other side of it.
 */
@RunWith(AndroidJUnit4::class)
class DepositNumberMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WhfinDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration2To3_addsDepositNumberAndKeepsTheLedger() {
        helper.createDatabase(DATABASE_NAME, 2).apply {
            execSQL("INSERT INTO financial_groups VALUES (1, 'Credo', 'BANK', 'Credo', 0, 0)")
            execSQL(
                "INSERT INTO accounts VALUES " +
                    "(1, 'Everyday', 'BANK', 1, 'GEL', 'GE00CD0000000000000001', NULL, NULL, NULL, " +
                    "NULL, 'AVAILABLE', 'CURRENT_ACCOUNT', 0, 0)",
            )
            execSQL(
                "INSERT INTO accounts VALUES " +
                    "(2, 'Demand deposit', 'BANK', 1, 'GEL', 'GE00CD0000000000000002', NULL, NULL, " +
                    "NULL, NULL, 'AVAILABLE', 'DEMAND_DEPOSIT', 0, 1)",
            )
            execSQL(
                "INSERT INTO transactions (id, accountId, amountMinor, currency, occurredAt, " +
                    "status, source, isTransfer, isVoided, createdAt) VALUES " +
                    "(1, 2, 531, 'GEL', 1780000000000, 'CONFIRMED', 'SMS', 0, 0, 1780000000000)",
            )
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 3, true, MIGRATION_2_3).use { migrated ->
            migrated.query(
                "SELECT name, bankProduct, fundRole, depositNumber FROM accounts ORDER BY id",
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("Everyday", cursor.getString(0))
                assertTrue(cursor.isNull(3))
                check(cursor.moveToNext())
                // Available by role and a deposit by product: the pair that has to survive intact,
                // because routing interest reads the product and never the role.
                assertEquals("Demand deposit", cursor.getString(0))
                assertEquals("DEMAND_DEPOSIT", cursor.getString(1))
                assertEquals("AVAILABLE", cursor.getString(2))
                assertTrue(cursor.isNull(3))
            }
            migrated.query("SELECT amountMinor, accountId, depositNumber FROM sms_diagnostics").use { cursor ->
                assertEquals(0, cursor.count)
            }
            migrated.query("SELECT amountMinor FROM transactions WHERE id = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(531L, cursor.getLong(0))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "deposit-number-migration.db"
    }
}
