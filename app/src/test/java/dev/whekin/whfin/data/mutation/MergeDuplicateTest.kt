package dev.whekin.whfin.data.mutation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Folding a pair the two layers wrote down separately back into one operation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MergeDuplicateTest {

    private lateinit var db: WhfinDatabase
    private lateinit var mutations: TransactionMutationModule
    private var accountId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        mutations = TransactionMutationModule(db)
        accountId = db.accountDao().insert(
            AccountEntity(name = "Credo GEL", type = AccountType.BANK, currency = "GEL"),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun row(source: TxSource, key: String?) = db.transactionDao().insert(
        TransactionEntity(
            accountId = accountId,
            amountMinor = 694_800,
            currency = "GEL",
            occurredAt = 1_757_000_000_000,
            rawCounterparty = if (source == TxSource.SMS) "SHPS UNOTRON" else "შპს",
            status = if (source == TxSource.SMS) TxStatus.CONFIRMED else TxStatus.CONFIRMED,
            source = source,
            externalKey = key,
        ),
    )

    @Test
    fun theCopyStopsCountingAndNamesTheRowThatStays() = runBlocking {
        val statement = row(TxSource.STATEMENT, "statement|1")
        val message = row(TxSource.SMS, "sms|1")

        val report = mutations.mergeDuplicate(message, statement)

        assertEquals(1, report.changed)
        val folded = db.transactionDao().byId(message)!!
        assertTrue(folded.isVoided)
        assertEquals(statement, folded.mergedIntoTransactionId)
        // The key is released so a re-import can claim it, and no balancing adjustment is written:
        // this is one operation seen twice, not a bank that was wrong.
        assertEquals(null, folded.externalKey)
        assertFalse(db.transactionDao().byId(statement)!!.isVoided)
        assertEquals(0, db.transactionDao().activeCorrectionsFor(message).size)
    }

    @Test
    fun theStatementRowIsNeverTheOneRetired() = runBlocking {
        val statement = row(TxSource.STATEMENT, "statement|1")
        val message = row(TxSource.SMS, "sms|1")

        val report = mutations.mergeDuplicate(statement, message)

        assertEquals(0, report.changed)
        assertFalse(db.transactionDao().byId(statement)!!.isVoided)
    }

    @Test
    fun rowsOfDifferentMoneyAreNotFolded() = runBlocking {
        val statement = row(TxSource.STATEMENT, "statement|1")
        val message = db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                amountMinor = 694_801,
                currency = "GEL",
                occurredAt = 1_757_000_000_000,
                status = TxStatus.CONFIRMED,
                source = TxSource.SMS,
            ),
        )

        assertEquals(0, mutations.mergeDuplicate(message, statement).changed)
        assertFalse(db.transactionDao().byId(message)!!.isVoided)
    }
}
