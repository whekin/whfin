package dev.whekin.whfin.data.importer

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.statement.SyntheticTbcWorkbook
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android XML/SQLite; run only on a disposable emulator. */
@RunWith(AndroidJUnit4::class)
class TbcStatementImportInstrumentedTest {
    @Test fun xlsxImportsAndReimportsOnAndroid() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = SyntheticTbcWorkbook.build()
        val db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java).build()
        try {
            val importer = StatementImporter(db)
            val first = importer.import(bytes.inputStream(), "synthetic-tbc.xlsx")
            assertEquals(4, first.inserted)
            assertEquals(20100L, db.transactionDao().allForIntegrity().sumOf { it.amountMinor })
            assertTrue(importer.preview(bytes.inputStream()).changesNothing)
            assertEquals(4, importer.import(bytes.inputStream()).duplicates)
            // Public synthetic fixture for the follow-up Android file-picker journey.
            java.io.File(context.cacheDir, "synthetic-tbc.xlsx").writeBytes(bytes)
        } finally { db.close() }
    }
}
