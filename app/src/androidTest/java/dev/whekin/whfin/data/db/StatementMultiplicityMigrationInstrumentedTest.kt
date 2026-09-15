package dev.whekin.whfin.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StatementMultiplicityMigrationInstrumentedTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WhfinDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory())
    @Test fun upgradeRetainsMoneyAndImportHistoryWithoutInventingEvidence() {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        helper.createDatabase("multiplicity-migration.db", 7).use {
            it.execSQL("INSERT INTO accounts (id,name,type,currency,isArchived,sortOrder,fundRole) VALUES (1,'Synthetic cash','CASH','GEL',0,0,'AVAILABLE')")
            it.execSQL("INSERT INTO transactions (id,accountId,amountMinor,currency,occurredAt,status,source,isTransfer,isVoided,createdAt) VALUES (1,1,12345,'GEL',1,'MANUAL','MANUAL',0,0,1)")
            it.execSQL("INSERT INTO statement_imports (id,accountId,totalRows,inserted,duplicates,reconciled,importedAt) VALUES (1,1,1,1,0,0,1)")
        }
        helper.runMigrationsAndValidate("multiplicity-migration.db", 8, true, MIGRATION_7_8).use {
            it.query("SELECT amountMinor FROM transactions WHERE id=1").use { c -> assertTrue(c.moveToFirst()); assertEquals(12345L,c.getLong(0)) }
            it.query("SELECT totalRows,rowMultiplicity FROM statement_imports WHERE id=1").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(1,c.getInt(0)); assertTrue(c.isNull(1))
            }
        }
    }
}
