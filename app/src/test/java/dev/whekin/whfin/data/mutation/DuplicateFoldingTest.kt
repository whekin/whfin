package dev.whekin.whfin.data.mutation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Repeated fares of the same price on one day are repeated payments, not one payment repeated. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DuplicateFoldingTest {

    private lateinit var db: WhfinDatabase
    private lateinit var folding: DuplicateFolding
    private var accountId: Long = 0
    private val day = LocalDate.of(2026, 9, 13)

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WhfinDatabase::class.java)
            .allowMainThreadQueries().build()
        folding = DuplicateFolding(db, TransactionMutationModule(db))
        accountId = db.accountDao().insert(AccountEntity(name = "TBC GEL", type = AccountType.BANK, currency = "GEL"))
    }
    @After fun tearDown() = db.close()

    private suspend fun ride(source: TxSource, merchant: String? = "TBCTPBUS", minute: Int = 0) =
        db.transactionDao().insert(TransactionEntity(
            accountId = accountId, amountMinor = -100, currency = "GEL",
            occurredAt = day.atStartOfDay(LedgerCalendar.zone).toInstant().toEpochMilli() + minute * 60_000L,
            rawCounterparty = merchant, status = TxStatus.CONFIRMED, source = source,
            externalKey = if (source == TxSource.STATEMENT) "stmt|$minute" else "sms|$minute"))

    private suspend fun active() = db.transactionDao().allForIntegrity().filterNot { it.isVoided }

    @Test fun threeRidesWrittenDownTwiceStayThreeRides() = runBlocking {
        val messages = (0 until 3).map { ride(TxSource.SMS, minute = it) }
        repeat(3) { ride(TxSource.STATEMENT, minute = it) }

        assertEquals(3, folding.fold(messages))

        val remaining = active()
        assertEquals(3, remaining.size)
        assertTrue(remaining.all { it.source == TxSource.STATEMENT })
        assertEquals(-300L, remaining.sumOf { it.amountMinor })
        // Each message named a line of its own; three pointing at one line would be two rides gone.
        assertEquals(3, messages.mapNotNull { db.transactionDao().byId(it)?.mergedIntoTransactionId }.distinct().size)
    }

    @Test fun threeMessagesAndOneStatementLineLoseNothingButTheOneRealCopy() = runBlocking {
        val messages = (0 until 3).map { ride(TxSource.SMS, minute = it) }
        ride(TxSource.STATEMENT, minute = 0)

        // Only one ride is described twice; the other two are rides the statement has not printed
        // yet, and folding them into that single line would erase two lari of real spending.
        assertEquals(1, folding.fold(messages))

        val remaining = active()
        assertEquals(3, remaining.size)
        assertEquals(-300L, remaining.sumOf { it.amountMinor })
        assertEquals(1, remaining.count { it.source == TxSource.STATEMENT })
    }

    @Test fun aLineNamingAnotherMerchantIsNotAPlaceToFoldInto() = runBlocking {
        val message = ride(TxSource.SMS)
        ride(TxSource.STATEMENT, merchant = "WATER SUPPLY")

        assertEquals(0, folding.fold(listOf(message)))
        assertEquals(2, active().size)
    }

    @Test fun anUnnamedLineStillAbsorbsItsMessage() = runBlocking {
        val message = ride(TxSource.SMS)
        ride(TxSource.STATEMENT, merchant = null)

        assertEquals(1, folding.fold(listOf(message)))
        assertEquals(1, active().size)
    }

    @Test fun oldUnnamedSilknetMessageCanFoldIntoNamedStatement() = runBlocking {
        val message = ride(TxSource.SMS, merchant = null)
        val statement = ride(TxSource.STATEMENT, merchant = "სილქნეტი - ინტერნეტი")

        assertEquals(1, folding.fold(listOf(message)))
        assertEquals(statement, db.transactionDao().byId(message)?.mergedIntoTransactionId)
        assertEquals(1, active().size)
    }

    @Test fun aLineThatAlreadyAbsorbedACopyRefusesTheNextOne() = runBlocking {
        val first = ride(TxSource.SMS, minute = 0)
        val second = ride(TxSource.SMS, minute = 1)
        val line = ride(TxSource.STATEMENT, minute = 0)
        val mutations = TransactionMutationModule(db)

        assertEquals(1, mutations.mergeDuplicate(first, line).changed)
        assertEquals(0, mutations.mergeDuplicate(second, line).changed)
        assertFalse(db.transactionDao().byId(second)!!.isVoided)
    }
}
