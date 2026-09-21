package dev.whekin.whfin.data.income

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class IncomeSourceRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), WhfinDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val repository = IncomeSourceRepository(db)
    @After fun close() = db.close()

    private suspend fun confirmedSource(): IncomeSourceEntity {
        val account = db.accountDao().insert(AccountEntity(name = "Salary", type = AccountType.CASH, currency = "GEL"))
        val source = IncomeSourceEntity(label = "Salary", amountMinor = 100_000, currency = "GEL",
            accountId = account, expectedDayFrom = 5, expectedDayTo = 5, startedOn = 20000, createdAt = 1)
        val saved = source.copy(id = repository.save(source))
        val transaction = db.transactionDao().insert(TransactionEntity(accountId = account, amountMinor = 100_000,
            currency = "GEL", occurredAt = 20010L * 86400000, status = TxStatus.CONFIRMED, source = TxSource.STATEMENT))
        db.incomeSourceDao().attach(IncomeSourcePaymentEntity(transaction, saved.id, 1))
        return saved
    }

    @Test fun `editing a declaration preserves confirmed payments`() = runBlocking {
        val source = confirmedSource()
        val payments = db.incomeSourceDao().payments()
        repository.save(source.copy(label = "Monthly salary", amountMinor = 120_000))
        assertEquals(payments, db.incomeSourceDao().payments())
        assertEquals(120_000L, db.incomeSourceDao().byId(source.id)!!.amountMinor)
    }

    @Test fun `moving pay to another account retains the previous eras confirmations`() = runBlocking {
        val source = confirmedSource()
        val payments = db.incomeSourceDao().payments()
        val nextAccount = db.accountDao().insert(AccountEntity(name = "New account", type = AccountType.CASH, currency = "GEL"))
        val next = repository.save(source.copy(accountId = nextAccount, startedOn = 20100))
        assertNotEquals(source.id, next)
        assertEquals(20099L, db.incomeSourceDao().byId(source.id)!!.endedOn)
        assertEquals(payments, db.incomeSourceDao().payments())
    }
}
