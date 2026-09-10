package dev.whekin.whfin.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BankHoldMigrationInstrumentedTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WhfinDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory())
    @Test fun migrationPreservesExistingLedgerAndAddsOnlyAnEmptyHoldTable() {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        helper.createDatabase("hold-migration.db", 6).use {
            it.execSQL("INSERT INTO accounts (id,name,type,currency,isArchived,sortOrder,fundRole) VALUES (1,'Synthetic cash','CASH','GEL',0,0,'AVAILABLE')")
            it.execSQL("INSERT INTO transactions (id,accountId,amountMinor,currency,occurredAt,status,source,isTransfer,isVoided,createdAt) VALUES (1,1,12345,'GEL',1,'MANUAL','MANUAL',0,0,1)")
        }
        helper.runMigrationsAndValidate("hold-migration.db", 7, true, MIGRATION_6_7).use {
            it.query("SELECT amountMinor FROM transactions WHERE id=1").use { c -> assertTrue(c.moveToFirst()); assertEquals(12345L,c.getLong(0)) }
            it.query("SELECT COUNT(*) FROM bank_holds").use { c -> assertTrue(c.moveToFirst()); assertEquals(0,c.getInt(0)) }
        }
    }
}
