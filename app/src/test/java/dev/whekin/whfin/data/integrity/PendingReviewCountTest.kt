package dev.whekin.whfin.data.integrity

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PendingReviewCountTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhfinDatabase::class.java)
        .allowMainThreadQueries().build()
    @After fun close() = db.close()

    @Test fun `bank holds await the bank and are not drafts needing the owners confirmation`() = runBlocking {
        val account = db.accountDao().insert(AccountEntity(name = "Account", type = AccountType.BANK, currency = "GEL"))
        val pending = TransactionEntity(accountId = account, amountMinor = -100, currency = "GEL",
            occurredAt = 1, status = TxStatus.PENDING, source = TxSource.MANUAL)
        db.transactionDao().insert(pending)
        db.transactionDao().insert(pending.copy(source = TxSource.BANK_HOLD))
        db.transactionDao().insert(pending.copy(source = TxSource.SMS, isVoided = true))
        assertEquals(1, db.transactionDao().pendingReviewCount())
    }
}
