package dev.whekin.whfin.data.sms

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.importer.StatementImporter
import dev.whekin.whfin.data.statement.SyntheticTbcWorkbook
import dev.whekin.whfin.data.LedgerCalendar
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MultiBankSmsImportTest {
    private lateinit var db: WhfinDatabase
    private val queries = java.util.concurrent.CopyOnWriteArrayList<String>()
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WhfinDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback({ sql, _ -> queries += sql }, java.util.concurrent.Executor { it.run() }).build()
    }
    @After fun close() = db.close()
    private suspend fun bank(provider: String, iban: String, mapped: Boolean = true): AccountEntity {
        val group = db.financialGroupDao().insert(FinancialGroupEntity(name = provider, provider = provider, type = FinancialGroupType.BANK))
        val id = db.accountDao().insert(AccountEntity(name = provider, type = AccountType.BANK, groupId = group, iban = iban, currency = "GEL"))
        val account = requireNotNull(db.accountDao().byId(id))
        if (mapped) db.paymentInstrumentDao().linkForAccount(account, "0001", PaymentInstrumentType.PHYSICAL_CARD)
        return account
    }
    private val tbcPayment = "2.00 GEL (*0001) EXAMPLE BUS 08/09/26 22:22"
    private val credoPayment = "Payment: 2.00 GEL Card N ****0001 EXAMPLE BUS>Tbilisi GE Balance: 20.00 GEL 08/09/2026 22:22:00"
    @Test fun mappedCardLookupDoesNotRepeatForEveryInboxMessage() = runBlocking {
        bank("Credo", "GE00CD0000000000000001")
        queries.clear()
        assertEquals(0, SmsTransactionImporter(db).learnCardsFrom(List(100) { credoPayment }))
        assertEquals(1, queries.count { it.contains("si.last4") })
        assertTrue(db.transactionDao().allForIntegrity().isEmpty())
    }
    @Test fun missedCardEvidenceDoesNotHideALaterMatchingMessage() = runBlocking {
        val account = bank("Credo", "GE00CD0000000000000001", mapped = false)
        val time = LocalDate.of(2026, 9, 8).atStartOfDay(LedgerCalendar.zone).toInstant().toEpochMilli()
        db.transactionDao().insert(TransactionEntity(accountId = account.id, amountMinor = -200, currency = "GEL",
            occurredAt = time, rawCounterparty = "EXAMPLE BUS", source = TxSource.STATEMENT, status = TxStatus.CONFIRMED))
        val messages = listOf(credoPayment.replace("EXAMPLE BUS", "UNKNOWN SHOP"), credoPayment, credoPayment)
        assertEquals(1, SmsTransactionImporter(db).learnCardsFrom(messages))
        assertEquals(account.id, db.accountDao().byCardAndCurrency("0001", "GEL").single().id)
        assertEquals(PaymentInstrumentType.UNCLASSIFIED_CARD,
            db.paymentInstrumentDao().forAccount(account.id).single().type)
    }
    @Test fun sameCardSuffixInTwoBanksRoutesToTheSendingBank() = runBlocking {
        val credo = bank("Credo", "GE00CD0000000000000001")
        val tbc = bank("TBC", SyntheticTbcWorkbook.IBAN)
        val a = SmsTransactionImporter(db).import(credoPayment)
        val b = SmsTransactionImporter(db, BankSmsBank.TBC).import(tbcPayment)
        assertEquals(credo.id, db.transactionDao().byId(requireNotNull(a.transactionId))?.accountId)
        assertEquals(tbc.id, db.transactionDao().byId(requireNotNull(b.transactionId))?.accountId)
        assertEquals(SmsDiagnosticOutcome.DUPLICATE, SmsTransactionImporter(db, BankSmsBank.TBC).import(tbcPayment).outcome)
        assertEquals(2, db.transactionDao().allForIntegrity().size)
    }
    @Test fun manualResolutionCannotMapTbcToCredoAndDoesNotResolveOtherBanksQueue() = runBlocking {
        val credo = bank("Credo", "GE00CD0000000000000001", mapped = false)
        val tbc = bank("TBC", SyntheticTbcWorkbook.IBAN, mapped = false)
        val importer = SmsTransactionImporter(db)
        val c = importer.import(credoPayment)
        val t = SmsTransactionImporter(db, BankSmsBank.TBC).import(tbcPayment)
        assertEquals(SmsDiagnosticOutcome.ERROR, importer.resolveDiagnostic(requireNotNull(t.diagnosticId), credo.id).outcome)
        assertTrue(db.transactionDao().allForIntegrity().isEmpty())
        importer.resolveDiagnostic(requireNotNull(t.diagnosticId), tbc.id)
        assertNull(db.smsDiagnosticDao().byId(requireNotNull(c.diagnosticId))?.transactionId)
        assertEquals(tbc.id, db.transactionDao().allForIntegrity().single().accountId)
    }
    @Test fun statementBeforeSmsLearnsOnlyTbcCardAndNeverCredo() = runBlocking {
        val credo = bank("Credo", "GE00CD0000000000000001")
        StatementImporter(db).import(SyntheticTbcWorkbook.build().inputStream())
        val t = SmsTransactionImporter(db, BankSmsBank.TBC).import(tbcPayment)
        assertEquals(SmsDiagnosticOutcome.ATTACHED, t.outcome)
        val tx = db.transactionDao().byId(requireNotNull(t.transactionId))!!
        assertNotEquals(credo.id, tx.accountId)
        val matches = db.accountDao().byCardAndCurrency("0001", "GEL")
        assertEquals(2, matches.size)
    }
    @Test fun smsBeforeStatementReconcilesPurchaseAndRefundWithoutDoubleMoney() = runBlocking {
        bank("TBC", SyntheticTbcWorkbook.IBAN)
        val sms = SmsTransactionImporter(db, BankSmsBank.TBC)
        val purchase = sms.import(tbcPayment)
        val refund = sms.import("Deposit: 250.00 GEL (*0001) EXAMPLE REFUND 08/09/26 20:00")
        val result = StatementImporter(db).import(SyntheticTbcWorkbook.build().inputStream())
        assertEquals(2, result.reconciled)
        val transactions = db.transactionDao().allForIntegrity()
        assertEquals(4, transactions.size)
        assertEquals(20100L, transactions.sumOf { it.amountMinor })
        assertEquals(TxSource.STATEMENT, db.transactionDao().byId(requireNotNull(purchase.transactionId))?.source)
        assertEquals(TxSource.STATEMENT, db.transactionDao().byId(requireNotNull(refund.transactionId))?.source)
    }
    @Test fun identicalCredoStatementDoesNotAnswerTbcSms() = runBlocking {
        val credo = bank("Credo", "GE00CD0000000000000001")
        val time = LocalDate.of(2026, 9, 8).atStartOfDay(LedgerCalendar.zone).toInstant().toEpochMilli()
        db.transactionDao().insert(TransactionEntity(accountId = credo.id, amountMinor = -200, currency = "GEL",
            occurredAt = time, rawCounterparty = "EXAMPLE BUS", source = TxSource.STATEMENT, status = TxStatus.CONFIRMED))
        val tbc = SmsTransactionImporter(db, BankSmsBank.TBC).import(tbcPayment)
        assertEquals(SmsDiagnosticOutcome.NEEDS_CARD_MAPPING, tbc.outcome)
        assertNull(tbc.transactionId)
    }
    @Test fun tbcMobileRechargeUsesOnlyTheTbcSpendableLedger() = runBlocking {
        bank("Credo", "GE00CD0000000000000001")
        val tbcAccount = bank("TBC", SyntheticTbcWorkbook.IBAN)
        val sms = "Mobile Balance Recharge\n40.00GEL\nExample mobile account\nID:000000000\n12/09/2026"

        val result = SmsTransactionImporter(db, BankSmsBank.TBC).import(sms)

        assertEquals(SmsDiagnosticOutcome.IMPORTED, result.outcome)
        val transaction = requireNotNull(db.transactionDao().byId(requireNotNull(result.transactionId)))
        assertEquals(tbcAccount.id, transaction.accountId)
        assertEquals(-4000L, transaction.amountMinor)
        assertEquals("Example mobile account", transaction.rawCounterparty)
    }
    @Test fun refusedTbcTransferNoticeDoesNotInventARefundAmount() = runBlocking {
        val tbcAccount = bank("TBC", SyntheticTbcWorkbook.IBAN)
        val notice = "2.00 GEL you sent to 500000000 and the fee has been returned to your account as the recipient did not accept the payment."

        val result = SmsTransactionImporter(db, BankSmsBank.TBC).import(notice)

        assertEquals(SmsDiagnosticOutcome.IGNORED, result.outcome)
        assertTrue(db.transactionDao().allForIntegrity().none { it.accountId == tbcAccount.id })
    }
}
