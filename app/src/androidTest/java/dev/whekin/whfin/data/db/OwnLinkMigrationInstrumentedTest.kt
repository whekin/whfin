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
 * The hand-made link keeps every row it held; only its name changes.
 *
 * A stored constant is the one place a rename cannot be cosmetic: Room throws on an unknown value
 * while observing a query, long after any migration returned, so a group left spelled the old way
 * would be an app that crashes on open. Groups the app derives for itself are not touched — they
 * mean something different and are rebuilt from statements, not from a decision somebody made.
 */
@RunWith(AndroidJUnit4::class)
class OwnLinkMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WhfinDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration5To6_renamesTheHandMadeLinkAndLeavesDerivedGroupsAlone() {
        helper.createDatabase(DATABASE_NAME, 5).apply {
            execSQL("INSERT INTO transfer_groups (id, type, createdAt) VALUES (1, 'CRYPTO_BRIDGE', 100)")
            execSQL("INSERT INTO transfer_groups (id, type, createdAt) VALUES (2, 'CONVERSION', 200)")
            execSQL("INSERT INTO transfer_groups (id, type, createdAt) VALUES (3, 'TRANSFER', 300)")
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 6, true, MIGRATION_5_6).use { migrated ->
            migrated.query("SELECT id, type FROM transfer_groups ORDER BY id").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("OWN_LINK", cursor.getString(1))
                check(cursor.moveToNext())
                assertEquals("CONVERSION", cursor.getString(1))
                check(cursor.moveToNext())
                assertEquals("TRANSFER", cursor.getString(1))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "own-link-migration.db"
    }
}
