package dev.whekin.whfin.data.sms

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.FinancialGroupEntity
import dev.whekin.whfin.data.db.FinancialGroupType
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.db.SmsDiagnosticReason
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A transfer message names no card and no account, so two lari ledgers used to mean a question. It
 * does state the balance that was left, and only one ledger stood there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmsBalanceRoutingImporterTest {
    private lateinit var db: WhfinDatabase
    private lateinit var importer: SmsTransactionImporter
    private var everydayId = 0L
    private var secondId = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val groupId = db.financialGroupDao().insert(
            FinancialGroupEntity(
                name = "Credo",
                type = FinancialGroupType.BANK,
                provider = "Credo",
            ),
        )
        everydayId = db.accountDao().insert(
            AccountEntity(
                name = "Everyday GEL",
                type = AccountType.BANK,
                groupId = groupId,
                currency = "GEL",
                iban = "GE00CD0000000000000001",
            ),
        )
        secondId = db.accountDao().insert(
            AccountEntity(
                name = "Second GEL",
                type = AccountType.BANK,
                groupId = groupId,
                currency = "GEL",
                iban = "GE00CD0000000000000002",
            ),
        )
        importer = SmsTransactionImporter(db)
    }

    @After
    fun tearDown() = db.close()

    /** The bank's own last figure on each ledger — what a statement row or an earlier message leaves. */
    private suspend fun declare(accountId: Long, balanceMinor: Long, amountMinor: Long = -1_000) {
        db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                amountMinor = amountMinor,
                currency = "GEL",
                occurredAt = ANCHOR_AT,
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
                balanceAfterMinor = balanceMinor,
                externalKey = "anchor|$accountId",
                createdAt = ANCHOR_AT,
            ),
        )
    }

    @Test
    fun `the stated balance routes a transfer that names no account`() = runBlocking {
        declare(everydayId, balanceMinor = 50_000)
        declare(secondId, balanceMinor = 133_456)

        val result = importer.import(TRANSFER, RECEIVED_AT)

        assertEquals(SmsDiagnosticOutcome.IMPORTED, result.outcome)
        val transaction = requireNotNull(db.transactionDao().byId(requireNotNull(result.transactionId)))
        assertEquals(secondId, transaction.accountId)
        assertEquals(-10_000, transaction.amountMinor)
    }

    /** Rows written after the bank's figure move the ledger on; the arithmetic has to follow them. */
    @Test
    fun `activity recorded since the declared balance is taken into account`() = runBlocking {
        declare(everydayId, balanceMinor = 50_000)
        declare(secondId, balanceMinor = 200_000)
        db.transactionDao().insert(
            TransactionEntity(
                accountId = secondId,
                amountMinor = -66_544,
                currency = "GEL",
                occurredAt = ANCHOR_AT + 1_000,
                status = TxStatus.CONFIRMED,
                source = TxSource.MANUAL,
                externalKey = "manual|second",
                createdAt = ANCHOR_AT + 1_000,
            ),
        )

        val result = importer.import(TRANSFER, RECEIVED_AT)

        assertEquals(SmsDiagnosticOutcome.IMPORTED, result.outcome)
        assertEquals(
            secondId,
            requireNotNull(db.transactionDao().byId(requireNotNull(result.transactionId))).accountId,
        )
    }

    @Test
    fun `a balance no ledger stood at stays a question`() = runBlocking {
        declare(everydayId, balanceMinor = 50_000)
        declare(secondId, balanceMinor = 60_000)

        val result = importer.import(TRANSFER, RECEIVED_AT)

        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, result.outcome)
        assertEquals(SmsDiagnosticReason.MULTIPLE_ACCOUNTS, result.reason)
    }

    @Test
    fun `two ledgers that both reach it stay a question`() = runBlocking {
        declare(everydayId, balanceMinor = 133_456)
        declare(secondId, balanceMinor = 133_456)

        val result = importer.import(TRANSFER, RECEIVED_AT)

        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, result.outcome)
        assertEquals(SmsDiagnosticReason.MULTIPLE_ACCOUNTS, result.reason)
    }

    /** Without a figure from the bank behind it, our own sum would only assert that nothing is missing. */
    @Test
    fun `a ledger the bank never declared a balance for is not guessed`() = runBlocking {
        declare(everydayId, balanceMinor = 50_000)
        db.transactionDao().insert(
            TransactionEntity(
                accountId = secondId,
                amountMinor = 143_456,
                currency = "GEL",
                occurredAt = ANCHOR_AT,
                status = TxStatus.CONFIRMED,
                source = TxSource.MANUAL,
                externalKey = "manual|no-balance",
                createdAt = ANCHOR_AT,
            ),
        )

        val result = importer.import(TRANSFER, RECEIVED_AT)

        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, result.outcome)
    }

    /** Cash paid in at a desk is money arriving; a ledger recording it as a spend drifts twice over. */
    @Test
    fun `a cash deposit adds to the ledger`() = runBlocking {
        db.accountDao().archive(secondId)
        declare(everydayId, balanceMinor = 50_000)

        val result = importer.import(CASH_DEPOSIT, RECEIVED_AT)

        assertEquals(SmsDiagnosticOutcome.IMPORTED, result.outcome)
        val transaction = requireNotNull(db.transactionDao().byId(requireNotNull(result.transactionId)))
        assertEquals(20_000, transaction.amountMinor)
    }

    private companion object {
        const val RECEIVED_AT = 1_775_000_000_000
        const val ANCHOR_AT = 1_774_000_000_000

        /** 100.00 GEL out, 1234.56 GEL left — no card, no IBAN, nothing else to route by. */
        val TRANSFER = """
            Outgoing transfer
            Amount: 100.00 GEL;
            Balance: 1234.56 GEL
            Date:4/5/2026 10:43:19 PM
            Check details in MyCredo: https://mycredo.page.link/Pdkp
        """.trimIndent()

        val CASH_DEPOSIT = """
            Depositing funds to the account 4/5/2026 8:48:05 PM,
            Amount 200.00 GEL; Available Balance 700.00 GEL.
        """.trimIndent()
    }
}
