package dev.whekin.whfin.widget

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.mutation.ExpenseBeneficiary
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickExpenseWriterTest {
    private lateinit var db: WhfinDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhfinDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()
    @Test fun widgetCreatesCashExpenseAndBeneficiaryInOneSave() = runBlocking {
        val id = writeQuickExpense(db, 1250, "GEL", null, "Coffee", null, ExpenseBeneficiary(newPersonName = "Mira"))
        val tx = requireNotNull(db.transactionDao().byId(id))
        assertEquals(-1250L, tx.amountMinor)
        assertEquals("Coffee", tx.note)
        assertEquals(AccountType.CASH, db.accountDao().byId(tx.accountId)?.type)
        val share = db.transactionAllocationDao().allForIntegrity().single()
        assertEquals(-1250L, share.amountMinor)
        assertEquals(AllocationPurpose.GIFT, share.purpose)
        assertEquals("Mira", db.personDao().byId(requireNotNull(share.personId))?.name)
    }
    @Test fun failedRecipientDoesNotLeaveAnEmptyCashAccount() = runBlocking {
        assertTrue(runCatching { writeQuickExpense(db, 1250, "GEL", null, null, null, ExpenseBeneficiary(personId = 999)) }.isFailure)
        assertTrue(db.accountDao().allActive().isEmpty())
        assertTrue(db.transactionDao().allForIntegrity().isEmpty())
    }
    @Test fun selectedSourceIsPreservedWithHalfShare() = runBlocking {
        val account = db.accountDao().insert(AccountEntity(name = "Everyday", currency = "GEL", type = AccountType.BANK))
        val id = writeQuickExpense(db, 1501, "GEL", account, null, null, ExpenseBeneficiary(newPersonName = "Mira", shareMinor = 750))
        assertEquals(account, db.transactionDao().byId(id)?.accountId)
        assertEquals(1, db.accountDao().allActive().size)
        assertEquals(listOf(-751L, -750L), db.transactionAllocationDao().allForIntegrity().map { it.amountMinor }.sorted())
    }
}
