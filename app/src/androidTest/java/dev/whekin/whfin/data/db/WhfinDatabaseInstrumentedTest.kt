package dev.whekin.whfin.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhfinDatabaseInstrumentedTest {
    private lateinit var db: WhfinDatabase

    @Before
    fun createDatabase() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WhfinDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = db.close()

    @Test
    fun accountRoundTrip_usesRealSqlite() = runBlocking {
        db.accountDao().insert(AccountEntity(name = "Cash", type = AccountType.CASH, currency = "GEL"))
        val accounts = db.accountDao().allActive()
        assertEquals(1, accounts.size)
        assertEquals("GEL", accounts.single().currency)
    }
}
