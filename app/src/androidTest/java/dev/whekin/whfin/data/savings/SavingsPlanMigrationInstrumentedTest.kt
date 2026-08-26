package dev.whekin.whfin.data.savings

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.whekin.whfin.data.db.MIGRATION_1_2
import dev.whekin.whfin.data.db.WhfinDatabase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavingsPlanMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WhfinDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private var db: WhfinDatabase? = null

    @After
    fun closeDatabase() {
        db?.close()
    }

    @Test
    fun migration1To2_preservesLedgerAndCreatesSavingsPlans() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL("INSERT INTO financial_groups VALUES (1, 'Credo', 'BANK', 'Credo', 0, 0)")
            execSQL(
                "INSERT INTO accounts VALUES " +
                    "(1, 'Daily', 'BANK', 1, 'GEL', 'GE00TEST', NULL, NULL, NULL, NULL, " +
                    "'AVAILABLE', 'CURRENT_ACCOUNT', 0, 0)",
            )
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 2, true, MIGRATION_1_2).use { migrated ->
            migrated.query("SELECT name FROM accounts WHERE id = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("Daily", cursor.getString(0))
            }
            migrated.query("SELECT COUNT(*) FROM savings_plans").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(0L, cursor.getLong(0))
            }
        }
    }

    @Test
    fun repository_versionsByMonth_withoutRewritingHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val opened = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java).build()
        db = opened
        val repository = SavingsPlanRepository(
            opened,
            Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC),
        )

        val augustId = repository.set(
            SavingsPlanDraft("gel", 100_000, goalMinor = 3_000_000, goalBy = LocalDate.parse("2027-12-31")),
            YearMonth.of(2026, 8),
        )
        assertEquals(
            augustId,
            repository.set(SavingsPlanDraft("GEL", 120_000), YearMonth.of(2026, 8)),
        )
        val septemberId = repository.set(
            SavingsPlanDraft("GEL", 150_000),
            YearMonth.of(2026, 9),
        )

        val history = opened.savingsPlanDao().allForIntegrity()
        assertEquals(2, history.size)
        assertEquals(120_000L, history[0].monthlyTargetMinor)
        assertEquals(LocalDate.parse("2026-08-31").toEpochDay(), history[0].endedOn)
        assertEquals(septemberId, history[1].id)
        assertEquals(150_000L, history[1].monthlyTargetMinor)

        repository.clear("GEL", YearMonth.of(2026, 9))
        assertNull(opened.savingsPlanDao().active("GEL"))
        assertEquals(1, opened.savingsPlanDao().allForIntegrity().size)
    }

    private companion object {
        const val DATABASE_NAME = "savings-plan-migration-test"
    }
}
