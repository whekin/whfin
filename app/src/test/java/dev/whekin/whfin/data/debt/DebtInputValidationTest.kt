package dev.whekin.whfin.data.debt

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
class DebtInputValidationTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhfinDatabase::class.java)
        .allowMainThreadQueries().build()
    private val repository = DebtRepository(db)
    @After fun close() = db.close()

    @Test fun `negative repayment cannot reverse the money movement and close a debt`() = runBlocking {
        val account = db.accountDao().insert(AccountEntity(name = "Cash", type = AccountType.CASH, currency = "GEL"))
        val debt = repository.open(NewDebt(personName = "Example", direction = DebtDirection.THEY_OWE_ME,
            amountMinor = 1000, currency = "GEL", occurredAt = 1))
        val result = runCatching { repository.settle(DebtSettlement(debt, -500, "GEL", account, occurredAt = 2)) }
        assertTrue(result.isFailure)
        assertEquals(DebtStatus.OPEN, db.debtDao().caseById(debt)!!.status)
        assertEquals(1, db.debtDao().eventsForCase(debt).size)
        db.openHelper.readableDatabase.query("SELECT count(*) FROM transactions").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }

    @Test fun `a movement in another currency is rejected before any money is written`() = runBlocking {
        val account = db.accountDao().insert(AccountEntity(name = "Dollar cash", type = AccountType.CASH, currency = "USD"))
        val result = runCatching { repository.open(NewDebt(personName = "Example", direction = DebtDirection.THEY_OWE_ME,
            amountMinor = 1000, currency = "GEL", accountId = account, occurredAt = 1)) }
        assertTrue(result.isFailure)
        db.openHelper.readableDatabase.query("SELECT count(*) FROM people").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }

    @Test fun `invalid partial credit does not silently become zero or full repayment`() = runBlocking {
        val debt = repository.open(NewDebt(personName = "Example", direction = DebtDirection.THEY_OWE_ME,
            amountMinor = 1000, currency = "GEL", occurredAt = 1))
        for (credit in listOf(-1L, 0L, 1001L)) {
            assertTrue(runCatching { repository.settle(DebtSettlement(debtCaseId = debt,
                debtValueMinor = credit, close = false, occurredAt = 2)) }.isFailure)
        }
        assertEquals(1, db.debtDao().eventsForCase(debt).size)
    }
}
