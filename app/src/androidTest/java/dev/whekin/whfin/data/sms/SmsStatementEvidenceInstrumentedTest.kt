package dev.whekin.whfin.data.sms

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.FinancialGroupEntity
import dev.whekin.whfin.data.db.FinancialGroupType
import dev.whekin.whfin.data.db.PaymentInstrumentType
import dev.whekin.whfin.data.db.SmsDiagnosticReason
import dev.whekin.whfin.data.db.StatementImportEntity
import dev.whekin.whfin.data.db.StatementImportOrigin
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A bank connection fills every ledger before the phone's inbox is read, so the common case is a
 * message whose money is already on file. These cover that order: the statement answers "which
 * account", the message stops being a question, and the card it names is learned once.
 */
@RunWith(AndroidJUnit4::class)
class SmsStatementEvidenceInstrumentedTest {
    private lateinit var db: WhfinDatabase
    private lateinit var importer: SmsTransactionImporter
    private var groupId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java).build()
        groupId = db.financialGroupDao().insert(
            FinancialGroupEntity(name = "Credo", type = FinancialGroupType.BANK, provider = "Credo"),
        )
        importer = SmsTransactionImporter(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun ledger(name: String, currency: String, iban: String) = db.accountDao().insert(
        AccountEntity(
            name = name,
            type = AccountType.BANK,
            groupId = groupId,
            currency = currency,
            iban = iban,
        ),
    )

    private suspend fun statementRow(
        accountId: Long,
        amountMinor: Long,
        currency: String = "GEL",
        merchant: String? = "EXAMPLE MARKET",
        at: LocalDateTime = LocalDateTime.of(2026, 4, 3, 0, 0),
        balanceAfterMinor: Long? = null,
        isTransfer: Boolean = false,
    ) = db.transactionDao().insert(
        TransactionEntity(
            accountId = accountId,
            amountMinor = amountMinor,
            currency = currency,
            occurredAt = at.atZone(ZONE).toInstant().toEpochMilli(),
            rawCounterparty = merchant,
            status = TxStatus.CONFIRMED,
            source = TxSource.STATEMENT,
            isTransfer = isTransfer,
            balanceAfterMinor = balanceAfterMinor,
            externalKey = "statement-$accountId-$amountMinor-${at.toLocalTime()}",
            createdAt = 0,
        ),
    )

    @Test
    fun aCardPaymentTheStatementAlreadyHoldsIsAttachedInsteadOfAsked() = runBlocking {
        ledger("Everyday GEL", "GEL", IBAN_ONE)
        val savings = ledger("Second GEL", "GEL", IBAN_TWO)
        val statementId = statementRow(savings, amountMinor = -1_234, balanceAfterMinor = 56_789)

        val result = importer.import(CARD_PAYMENT, RECEIVED_AT)

        assertEquals(SmsDiagnosticOutcome.ATTACHED, result.outcome)
        assertEquals(statementId, result.transactionId)
        // Bank truth stays one row; the message became evidence, not a second draft of it.
        assertEquals(1, transactionCount())
        assertTrue(db.smsDiagnosticDao().observeUnrouted().first().isEmpty())
    }

    @Test
    fun oneMatchedPurchaseTeachesTheCardItsLedger() = runBlocking {
        ledger("Everyday GEL", "GEL", IBAN_ONE)
        val second = ledger("Second GEL", "GEL", IBAN_TWO)
        statementRow(second, amountMinor = -1_234, balanceAfterMinor = 56_789)

        importer.import(CARD_PAYMENT, RECEIVED_AT)

        assertEquals(second, db.accountDao().byCardAndCurrency("0001", "GEL").single().id)
        // The next message from the same card no longer needs a statement or a question.
        val later = importer.import(SECOND_CARD_PAYMENT, RECEIVED_AT + 1_000)
        assertEquals(SmsDiagnosticOutcome.IMPORTED, later.outcome)
        assertEquals(second, db.transactionDao().byId(requireNotNull(later.transactionId))?.accountId)
    }

    /**
     * The first bank connection: a year of statements lands, and the phone's inbox is read only
     * afterwards. The messages are already old news — every one of them is in the statements — but
     * they are the only place the card number appears, so they still have identity to give.
     */
    @Test
    fun theInboxNamesACardTheStatementsNeverPrint() = runBlocking {
        ledger("Everyday GEL", "GEL", IBAN_ONE)
        val second = ledger("Second GEL", "GEL", IBAN_TWO)
        statementRow(second, amountMinor = -1_234, balanceAfterMinor = 56_789)

        val linked = importer.learnCardsFrom(listOf(CARD_PAYMENT))

        assertEquals(1, linked)
        assertEquals(second, db.accountDao().byCardAndCurrency("0001", "GEL").single().id)
        // Reading the inbox is not importing it: the statement stays the only row.
        assertEquals(1, transactionCount())
    }

    /** A card already pointing at a ledger of that currency has nothing left to learn. */
    @Test
    fun aCardAlreadyLinked_isNotReExaminedAgainstTheStatements() = runBlocking {
        val second = ledger("Second GEL", "GEL", IBAN_TWO)
        statementRow(second, amountMinor = -1_234, balanceAfterMinor = 56_789)
        importer.learnCardsFrom(listOf(CARD_PAYMENT))

        val again = importer.learnCardsFrom(listOf(CARD_PAYMENT))

        assertEquals(0, again)
    }

    /**
     * Ambiguity is not identity. Two ledgers could equally have paid, so the card stays unlinked
     * rather than being bound to a guess that every future message would then inherit.
     */
    @Test
    fun anInboxMessageThatCouldBeEitherLedger_teachesNothing() = runBlocking {
        val first = ledger("Everyday GEL", "GEL", IBAN_ONE)
        val second = ledger("Second GEL", "GEL", IBAN_TWO)
        statementRow(first, amountMinor = -1_234, balanceAfterMinor = null)
        statementRow(second, amountMinor = -1_234, balanceAfterMinor = null)

        val linked = importer.learnCardsFrom(listOf(CARD_PAYMENT))

        assertEquals(0, linked)
        assertTrue(db.accountDao().byCardAndCurrency("0001", "GEL").isEmpty())
    }

    @Test
    fun theStatedBalanceDecidesBetweenTwoIdenticalRows() = runBlocking {
        val first = ledger("Everyday GEL", "GEL", IBAN_ONE)
        val second = ledger("Second GEL", "GEL", IBAN_TWO)
        statementRow(first, amountMinor = -1_234, balanceAfterMinor = 10_000)
        val expected = statementRow(second, amountMinor = -1_234, balanceAfterMinor = 56_789)

        val result = importer.import(CARD_PAYMENT, RECEIVED_AT)

        assertEquals(SmsDiagnosticOutcome.ATTACHED, result.outcome)
        assertEquals(expected, result.transactionId)
    }

    @Test
    fun twoIndistinguishableRowsAreLeftForTheUser() = runBlocking {
        val first = ledger("Everyday GEL", "GEL", IBAN_ONE)
        val second = ledger("Second GEL", "GEL", IBAN_TWO)
        statementRow(first, amountMinor = -1_234, balanceAfterMinor = null)
        statementRow(second, amountMinor = -1_234, balanceAfterMinor = null)

        val result = importer.import(CARD_PAYMENT, RECEIVED_AT)

        assertEquals(SmsDiagnosticOutcome.NEEDS_CARD_MAPPING, result.outcome)
        assertTrue(db.accountDao().byCardAndCurrency("0001", "GEL").isEmpty())
    }

    @Test
    fun aRowAnotherMessageAlreadyExplainsIsNotTakenTwice() = runBlocking {
        val only = ledger("Everyday GEL", "GEL", IBAN_ONE)
        statementRow(only, amountMinor = -1_234, balanceAfterMinor = 56_789)

        val first = importer.import(CARD_PAYMENT, RECEIVED_AT)
        // Same amount, same shop, a minute later: a second charge the statement has not reached yet.
        // It belongs in the ledger the first one just identified — but as its own row, never filed
        // onto the row that already explains the first.
        val second = importer.import(REPEATED_CARD_PAYMENT, RECEIVED_AT + 60_000)

        assertEquals(SmsDiagnosticOutcome.ATTACHED, first.outcome)
        assertEquals(SmsDiagnosticOutcome.IMPORTED, second.outcome)
        assertNotEquals(first.transactionId, second.transactionId)
        assertEquals(2, transactionCount())
        assertEquals(only, db.transactionDao().byId(requireNotNull(second.transactionId))?.accountId)
    }

    @Test
    fun anOwnTransferTheStatementAlreadyHoldsDoesNotWriteASecondPair() = runBlocking {
        val from = ledger("Everyday GEL", "GEL", IBAN_ONE)
        val to = ledger("Second GEL", "GEL", IBAN_TWO)
        val at = LocalDateTime.of(2026, 4, 5, 0, 0)
        val sent = statementRow(from, amountMinor = -20_000, merchant = null, at = at, isTransfer = true)
        statementRow(to, amountMinor = 20_000, merchant = null, at = at, isTransfer = true)

        // Both IBANs are known, so this routes cleanly — and used to write a second pair of legs on
        // top of the transfer the statement had filed, doubling both balances.
        val result = importer.import(OWN_TRANSFER, RECEIVED_AT)

        assertEquals(SmsDiagnosticOutcome.ATTACHED, result.outcome)
        assertEquals(sent, result.transactionId)
        assertEquals(2, transactionCount())
    }

    @Test
    fun aMessageInsideACoveredPeriodIsNeverWrittenToTheLedger() = runBlocking {
        val account = ledger("Everyday GEL", "GEL", IBAN_ONE)
        db.paymentInstrumentDao().linkForAccount(
            requireNotNull(db.accountDao().byId(account)),
            "0001",
            PaymentInstrumentType.PHYSICAL_CARD,
        )
        db.statementImportDao().insert(
            StatementImportEntity(
                accountId = account,
                sourceId = null,
                fileName = "statement.xlsx",
                origin = StatementImportOrigin.CREDO_SYNC,
                periodFrom = LocalDate.of(2026, 4, 1).toEpochDay(),
                periodTo = LocalDate.of(2026, 4, 30).toEpochDay(),
                openingBalanceMinor = 0,
                closingBalanceMinor = 0,
                totalRows = 0,
                inserted = 0,
                duplicates = 0,
                reconciled = 0,
                reviewCount = 0,
                importedAt = 0,
            ),
        )

        // The card names its ledger, but the statement covers that day and holds no such row: the
        // bank printed it differently, or we failed to recognise it. A second row for one purchase
        // is worse than a question that the next import can answer.
        val result = importer.import(CARD_PAYMENT, RECEIVED_AT)

        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, result.outcome)
        assertEquals(SmsDiagnosticReason.STATEMENT_COVERS_PERIOD, result.reason)
        assertEquals(0, transactionCount())
    }

    @Test
    fun aMerchantThatOnlyTheMessageSpellsOutStillMatches() = runBlocking {
        val account = ledger("Everyday GEL", "GEL", IBAN_ONE)
        val statementId = statementRow(
            account,
            amountMinor = -1_234,
            merchant = "ANTHROPIC",
            balanceAfterMinor = 56_789,
        )

        val result = importer.import(LONG_MERCHANT_CARD_PAYMENT, RECEIVED_AT)

        assertEquals(SmsDiagnosticOutcome.ATTACHED, result.outcome)
        assertEquals(statementId, result.transactionId)
    }

    @Test
    fun aConversionIsAttachedOnlyWhenBothLegsAreOnFile() = runBlocking {
        val gel = ledger("Everyday GEL", "GEL", IBAN_ONE)
        ledger("Second GEL", "GEL", IBAN_TWO)
        val usd = ledger("Everyday USD", "USD", IBAN_ONE)
        ledger("Second USD", "USD", IBAN_TWO)
        val sold = statementRow(
            gel,
            amountMinor = -5_000,
            merchant = null,
            at = LocalDateTime.of(2026, 4, 5, 0, 0),
            isTransfer = true,
        )

        val withoutDestination = importer.import(CURRENCY_EXCHANGE, RECEIVED_AT)
        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, withoutDestination.outcome)

        statementRow(
            usd,
            amountMinor = 1_800,
            currency = "USD",
            merchant = null,
            at = LocalDateTime.of(2026, 4, 5, 0, 0),
            isTransfer = true,
        )
        val result = importer.import(CURRENCY_EXCHANGE, RECEIVED_AT)

        assertEquals(SmsDiagnosticOutcome.ATTACHED, result.outcome)
        assertEquals(sold, result.transactionId)
    }

    @Test
    fun aStatementImportedLaterAnswersMessagesThatWereAlreadyWaiting() = runBlocking {
        ledger("Everyday GEL", "GEL", IBAN_ONE)
        val second = ledger("Second GEL", "GEL", IBAN_TWO)

        val asked = importer.import(CARD_PAYMENT, RECEIVED_AT)
        assertEquals(SmsDiagnosticOutcome.NEEDS_CARD_MAPPING, asked.outcome)

        val statementId = statementRow(second, amountMinor = -1_234, balanceAfterMinor = 56_789)
        assertEquals(1, importer.attachUnroutedToStatements())

        val diagnostic = db.smsDiagnosticDao().byId(requireNotNull(asked.diagnosticId))
        assertEquals(SmsDiagnosticOutcome.ATTACHED, diagnostic?.outcome)
        assertEquals(statementId, diagnostic?.transactionId)
        assertEquals(second, db.accountDao().byCardAndCurrency("0001", "GEL").single().id)
    }

    private fun transactionCount(): Int = db.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM transactions")
        .use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Tbilisi")
        const val RECEIVED_AT = 1_775_000_000_000
        const val IBAN_ONE = "GE00CD0000000000000001"
        const val IBAN_TWO = "GE00CD0000000000000002"
        val CARD_PAYMENT = """
            Payment: 12.34 GEL
            Card N ****0001
            EXAMPLE MARKET>Tbilisi                 GE
            Balance: 567.89 GEL
            03/04/2026 20:48:05
            Details: https://mycredo.page.link/Pdk
        """.trimIndent()
        val REPEATED_CARD_PAYMENT = """
            Payment: 12.34 GEL
            Card N ****0001
            EXAMPLE MARKET>Tbilisi                 GE
            Balance: 555.55 GEL
            03/04/2026 20:49:05
            Details: https://mycredo.page.link/Pdk
        """.trimIndent()
        val SECOND_CARD_PAYMENT = """
            Payment: 21.98 GEL
            Card N ****0001
            EXAMPLE PHARMACY>Tbilisi               GE
            Balance: 545.91 GEL
            03/04/2026 21:03:05
            Details: https://mycredo.page.link/Pdk
        """.trimIndent()
        val OWN_TRANSFER = """
            Transfer between accounts
            Amount: 200.00 GEL;
            From: $IBAN_ONE
            To: $IBAN_TWO
            Balance: 1334.56 GEL
            Date: 4/5/2026 10:43:03 PM
            Check details in MyCredo: https://mycredo.page.link/Pdkp
        """.trimIndent()
        val LONG_MERCHANT_CARD_PAYMENT = """
            Payment: 12.34 GEL
            Card N ****0001
            ANTHROPIC* CLAUDE.AI>Tbilisi           GE
            Balance: 567.89 GEL
            03/04/2026 20:48:05
            Details: https://mycredo.page.link/Pdk
        """.trimIndent()
        val CURRENCY_EXCHANGE = """
            Currency exchange
            Amount: 50.00 GEL
            Received amount: 18.00 USD
            Balance: 18.00 USD
            Date:4/5/2026 9:36:59 PM;
            Check details in MyCredo: https://mycredo.page.link/Pdkp
        """.trimIndent()
    }
}
