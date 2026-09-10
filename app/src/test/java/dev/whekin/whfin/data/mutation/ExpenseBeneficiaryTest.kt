package dev.whekin.whfin.data.mutation

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExpenseBeneficiaryTest {
    private lateinit var db: WhfinDatabase
    private lateinit var module: TransactionMutationModule
    private var cash = 0L
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhfinDatabase::class.java).allowMainThreadQueries().build()
        module = TransactionMutationModule(db)
        cash = db.accountDao().insert(AccountEntity(name = "Cash", currency = "GEL", type = AccountType.CASH))
    }
    @After fun close() = db.close()
    private fun expense(amount: Long = -1501) = ManualMutation(cash, amount, occurredAt = 1000)

    @Test fun giftAndNewPersonAreSavedAsOneExpenseWithoutDebt() = runBlocking {
        val id = module.createManual(expense(), beneficiary = ExpenseBeneficiary(newPersonName = "Mira"))
        val shares = db.transactionAllocationDao().allForIntegrity().filter { it.transactionId == id }
        assertEquals(1, shares.size)
        assertEquals(-1501L, shares.single().amountMinor)
        assertEquals(AllocationPurpose.GIFT, shares.single().purpose)
        assertEquals("Mira", db.personDao().byId(requireNotNull(shares.single().personId))?.name)
        assertEquals(-1501L, db.transactionDao().sumByAccount(cash))
        assertFalse(requireNotNull(db.transactionDao().byId(id)).isTransfer)
    }
    @Test fun sharedExpensePreservesTheOddMinorUnitOnSelf() = runBlocking {
        val person = db.personDao().insert(PersonEntity(name = "Mira", color = 0))
        module.createManual(expense(), beneficiary = ExpenseBeneficiary(personId = person, shareMinor = 750))
        val shares = db.transactionAllocationDao().allForIntegrity()
        assertEquals(-750L, shares.single { it.personId == person }.amountMinor)
        assertEquals(AllocationPurpose.SHARED, shares.single { it.personId == person }.purpose)
        assertEquals(-751L, shares.single { it.personId == null }.amountMinor)
        assertEquals(-1501L, shares.sumOf { it.amountMinor })
    }
    @Test fun failureRollsBackTheNewPersonToo() = runBlocking {
        assertTrue(runCatching { module.createManual(expense().copy(accountId = 999), beneficiary = ExpenseBeneficiary(newPersonName = "Mira")) }.isFailure)
        assertTrue(db.transactionDao().allForIntegrity().isEmpty())
        assertEquals(0, db.openHelper.readableDatabase.query("SELECT count(*) FROM people").use { it.moveToFirst(); it.getInt(0) })
    }
    @Test fun invalidSharesAndIncomeCannotCreateBeneficiaries() = runBlocking {
        listOf(0L, 1502L).forEach { amount ->
            assertTrue(runCatching { module.createManual(expense(), beneficiary = ExpenseBeneficiary(newPersonName = "Mira", shareMinor = amount)) }.isFailure)
        }
        assertTrue(runCatching { module.createManual(expense(1501), beneficiary = ExpenseBeneficiary(newPersonName = "Mira")) }.isFailure)
        assertTrue(db.transactionDao().allForIntegrity().isEmpty())
    }
}
