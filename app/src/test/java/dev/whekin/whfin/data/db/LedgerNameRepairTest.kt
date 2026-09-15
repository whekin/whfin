package dev.whekin.whfin.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** One IBAN, four currencies: the name of one of them is not the name of the others. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LedgerNameRepairTest {

    private lateinit var db: WhfinDatabase
    private var groupId: Long = 0
    private val iban = "GE00TB0000000000000001"

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WhfinDatabase::class.java)
            .allowMainThreadQueries().build()
        groupId = db.financialGroupDao().insert(
            FinancialGroupEntity(name = "TBC", type = FinancialGroupType.BANK, provider = "TBC"))
    }
    @After fun tearDown() = db.close()

    private suspend fun ledger(currency: String, name: String, accountIban: String? = iban) =
        db.accountDao().insert(AccountEntity(name = name, type = AccountType.BANK, groupId = groupId,
            currency = currency, iban = accountIban))

    @Test fun aNameGeneratedForAnotherCurrencyIsNotThisLedgersName() = runBlocking {
        val gel = ledger("GEL", "TBC GEL •0001")
        val usd = ledger("USD", "TBC GEL •0001")
        val eur = ledger("EUR", "TBC GEL •0001")

        assertEquals(2, db.repairCrossCurrencyLedgerNames())

        // The one it was generated for keeps it; the borrowed ones go back to being named by the
        // bank, the number and the product, which is what an empty name means on these screens.
        assertEquals("TBC GEL •0001", requireNotNull(db.accountDao().byId(gel)).name)
        assertEquals("", requireNotNull(db.accountDao().byId(usd)).name)
        assertEquals("", requireNotNull(db.accountDao().byId(eur)).name)
        assertEquals(0, db.repairCrossCurrencyLedgerNames())
    }

    @Test fun aNameItsOwnerWroteIsNeverTouched() = runBlocking {
        val travel = ledger("USD", "Travel")
        val safe = ledger("GEL", "My Safe", "GE00TB0000000000000069")
        val cash = db.accountDao().insert(AccountEntity(name = "Наличные", type = AccountType.CASH, currency = "GEL"))

        assertEquals(0, db.repairCrossCurrencyLedgerNames())

        assertEquals("Travel", requireNotNull(db.accountDao().byId(travel)).name)
        assertEquals("My Safe", requireNotNull(db.accountDao().byId(safe)).name)
        assertEquals("Наличные", requireNotNull(db.accountDao().byId(cash)).name)
    }

    @Test fun theGeneratedShapeIsRecognisedOnlyWithItsOwnAccountNumber() {
        assertTrue(isGeneratedLedgerName("TBC GEL •0001", iban))
        assertTrue(isGeneratedLedgerName("Credo USD 0001", iban))
        assertEquals("GEL", generatedNameCurrency("TBC GEL •0001", iban))
        // Another account's tail, a written name, and a name with no number are all somebody's words.
        assertFalse(isGeneratedLedgerName("TBC GEL •0009", iban))
        assertFalse(isGeneratedLedgerName("Travel GEL", iban))
        assertFalse(isGeneratedLedgerName("TBC GEL •0001", null))
        assertNull(generatedNameCurrency("Everyday", iban))
    }
}
