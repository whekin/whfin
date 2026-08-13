package dev.whekin.whfin.data.integrity

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Voiding has explicit causes and the check knows each one. A merged duplicate is not a correction —
 * there is no separate operation to undo — but it still owes an explanation, and the row it points
 * at has to be one that still counts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MergedTransactionIntegrityTest {
    private lateinit var db: WhfinDatabase
    private var accountId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountId = db.accountDao().insert(
            AccountEntity(name = "Everyday", type = AccountType.BANK, currency = "GEL"),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun row(
        amountMinor: Long = -2_500,
        voided: Boolean = false,
        externalKey: String? = null,
        mergedInto: Long? = null,
    ): Long = db.transactionDao().insert(
        TransactionEntity(
            accountId = accountId,
            amountMinor = amountMinor,
            currency = "GEL",
            occurredAt = 1_000,
            status = TxStatus.CONFIRMED,
            source = TxSource.STATEMENT,
            externalKey = externalKey,
            isVoided = voided,
            mergedIntoTransactionId = mergedInto,
            createdAt = 1,
        ),
    )

    @Test
    fun `a merged copy that names its survivor is healthy`() = runBlocking {
        val survivor = row(externalKey = "stmt|1")
        row(voided = true, mergedInto = survivor)

        val report = DataIntegrityChecker(db).run()

        assertTrue(report.issues.joinToString { it.code }, report.isHealthy)
    }

    @Test
    fun `a voided import with no reason at all is still reported`() = runBlocking {
        row(voided = true)

        val report = DataIntegrityChecker(db).run()

        assertEquals(listOf("missing_transaction_correction"), report.issues.map { it.code })
    }

    @Test
    fun `a canceled sms row retains money provenance and is healthy`() = runBlocking {
        db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                amountMinor = -2_500,
                currency = "GEL",
                occurredAt = 1_000,
                status = TxStatus.CONFIRMED,
                source = TxSource.SMS,
                externalKey = "sms|payment",
                isVoided = true,
                canceledBySmsExternalKey = "sms|cancellation",
                createdAt = 1,
            ),
        )

        assertTrue(DataIntegrityChecker(db).run().isHealthy)
    }

    @Test
    fun `an sms row standing beside its statement twin is one purchase counted twice`() = runBlocking {
        db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                amountMinor = -2_500,
                currency = "GEL",
                occurredAt = 1_000,
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
                externalKey = "stmt|1",
                createdAt = 1,
            ),
        )
        db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                amountMinor = -2_500,
                currency = "GEL",
                occurredAt = 2_000,
                status = TxStatus.CONFIRMED,
                source = TxSource.SMS,
                externalKey = "sms|1",
                createdAt = 2,
            ),
        )

        val codes = DataIntegrityChecker(db).run().issues.map { it.code }

        assertEquals(listOf("duplicate_statement_row"), codes)
    }

    @Test
    fun `the same money on different days is not a duplicate`() = runBlocking {
        val day = 24L * 60 * 60 * 1000
        db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                amountMinor = -2_500,
                currency = "GEL",
                occurredAt = day,
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
                externalKey = "stmt|1",
                createdAt = 1,
            ),
        )
        db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                amountMinor = -2_500,
                currency = "GEL",
                occurredAt = day * 5,
                status = TxStatus.CONFIRMED,
                source = TxSource.SMS,
                externalKey = "sms|1",
                createdAt = 2,
            ),
        )

        assertTrue(DataIntegrityChecker(db).run().isHealthy)
    }

    @Test
    fun `a merge pointing at a retired row means the money disappeared`() = runBlocking {
        val retired = row(voided = true, externalKey = "stmt|1", mergedInto = null)
        row(voided = true, mergedInto = retired)

        val codes = DataIntegrityChecker(db).run().issues.map { it.code }

        assertTrue(codes.toString(), "orphan_merged_transaction" in codes)
    }

    @Test
    fun `a merged copy that still counts is a duplicate in the balance`() = runBlocking {
        val survivor = row(externalKey = "stmt|1")
        row(mergedInto = survivor)

        val codes = DataIntegrityChecker(db).run().issues.map { it.code }

        assertTrue(codes.toString(), "active_merged_transaction" in codes)
    }
}
