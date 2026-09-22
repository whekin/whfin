package dev.whekin.whfin.data.sms

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.data.db.FinancialGroupEntity
import dev.whekin.whfin.data.db.FinancialGroupType
import dev.whekin.whfin.data.db.FundRole
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.db.SmsDiagnosticReason
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.StatementImportEntity
import dev.whekin.whfin.data.db.StatementImportOrigin
import dev.whekin.whfin.data.LedgerCalendar
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A bill is paid out of money that can be spent.
 *
 * Which accounts are deposits has already been stated by their bank product, so a deposit is not a
 * possible answer to "where did this payment come from" — and listing one asks the owner to answer
 * again, in a list long enough to hide the rows that could be right. This is the mirror of the rule
 * interest already follows in the other direction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmsBillRoutingTest {
    private lateinit var db: WhfinDatabase
    private lateinit var importer: SmsTransactionImporter
    private var groupId = 0L

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            WhfinDatabase::class.java,
        ).allowMainThreadQueries().build()
        groupId = db.financialGroupDao().insert(
            FinancialGroupEntity(name = "Credo", type = FinancialGroupType.BANK, provider = "Credo"),
        )
        importer = SmsTransactionImporter(db)
    }

    @After fun tearDown() = db.close()

    private suspend fun current(tail: String) = db.accountDao().insert(
        AccountEntity(
            name = "Everyday GEL",
            type = AccountType.BANK,
            groupId = groupId,
            currency = "GEL",
            iban = "GE00CD000000000000000$tail",
            bankProduct = BankProduct.CURRENT_ACCOUNT,
        ),
    )

    /** Available by fund role and a deposit by product: the case reading the role would get wrong. */
    private suspend fun demandDeposit() = db.accountDao().insert(
        AccountEntity(
            name = "Demand deposit GEL",
            type = AccountType.BANK,
            groupId = groupId,
            currency = "GEL",
            iban = "GE00CD0000000000000002",
            fundRole = FundRole.AVAILABLE,
            bankProduct = BankProduct.DEMAND_DEPOSIT,
        ),
    )

    private suspend fun termDeposit() = db.accountDao().insert(
        AccountEntity(
            name = "Term deposit GEL",
            type = AccountType.BANK,
            groupId = groupId,
            currency = "GEL",
            iban = "GE00CD0000000000000003",
            fundRole = FundRole.RESERVE,
            bankProduct = BankProduct.TERM_DEPOSIT,
        ),
    )

    @Test fun theOnlyAccountItCouldHaveComeFromNeedsNoQuestion() = runBlocking {
        val everyday = current("1")
        demandDeposit()
        termDeposit()

        val result = importer.import(bill())

        assertEquals(SmsDiagnosticOutcome.IMPORTED, result.outcome)
        val transaction = db.transactionDao().byId(result.transactionId!!)!!
        assertEquals(everyday, transaction.accountId)
        assertEquals(-4560L, transaction.amountMinor)
    }

    @Test fun twoSpendableAccountsAreAQuestionAboutThoseTwo() = runBlocking {
        current("1")
        current("4")
        demandDeposit()

        val result = importer.import(bill())

        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, result.outcome)
        assertEquals(SmsDiagnosticReason.MULTIPLE_ACCOUNTS, result.reason)
    }

    /**
     * Nothing to pay from is its own answer: offering the deposits would be offering the only rows
     * that cannot be right.
     */
    @Test fun depositsAloneAreNotAnAnswer() = runBlocking {
        demandDeposit()
        termDeposit()

        val result = importer.import(bill())

        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, result.outcome)
        assertEquals(SmsDiagnosticReason.NO_ACCOUNT, result.reason)
    }

    @Test fun silknetBillAttachesToItsStatementAmongTwoSameAmountDays() = runBlocking {
        val everyday = current("1")
        current("4")
        val day = LocalDate.of(2026, 8, 24)
        val at = day.atTime(15, 24).atZone(LedgerCalendar.zone).toInstant().toEpochMilli()
        val silknet = db.transactionDao().insert(TransactionEntity(
            accountId = everyday, amountMinor = -100, currency = "GEL",
            occurredAt = day.atStartOfDay(LedgerCalendar.zone).toInstant().toEpochMilli(),
            rawCounterparty = "სილქნეტი - ინტერნეტი", source = TxSource.STATEMENT,
            status = TxStatus.CONFIRMED,
        ))
        db.transactionDao().insert(TransactionEntity(
            accountId = everyday, amountMinor = -100, currency = "GEL",
            occurredAt = day.plusDays(1).atStartOfDay(LedgerCalendar.zone).toInstant().toEpochMilli(),
            rawCounterparty = "OTHER PROVIDER", source = TxSource.STATEMENT,
            status = TxStatus.CONFIRMED,
        ))
        db.statementImportDao().insert(StatementImportEntity(
            accountId = everyday, sourceId = null, fileName = "statement.xlsx",
            origin = StatementImportOrigin.CREDO_SYNC,
            periodFrom = day.minusDays(10).toEpochDay(), periodTo = day.plusDays(10).toEpochDay(),
            openingBalanceMinor = 0, closingBalanceMinor = -200, totalRows = 2,
            inserted = 2, duplicates = 0, reconciled = 0, reviewCount = 0, importedAt = at - 60_000,
        ))
        val sms = """
            Service/utility payment
            Amount: 1.00 GEL;
            Service: Silknet, ID: 000000000
            Balance: 263.39 GEL
            Date: äDateñ;
        """.trimIndent()

        val result = importer.import(sms, at)

        assertEquals(SmsDiagnosticOutcome.ATTACHED, result.outcome)
        assertEquals(silknet, result.transactionId)
        assertEquals(2, db.transactionDao().allForIntegrity().size)
    }

    @Test fun choosingAccountCannotCreateABillInsideAStatementCoveredPeriod() = runBlocking {
        val everyday = current("1")
        current("4")
        val day = LocalDate.of(2026, 8, 24)
        val receivedAt = day.atTime(15, 24).atZone(LedgerCalendar.zone).toInstant().toEpochMilli()
        db.statementImportDao().insert(StatementImportEntity(
            accountId = everyday, sourceId = null, fileName = "statement.xlsx",
            origin = StatementImportOrigin.CREDO_SYNC,
            periodFrom = day.minusDays(10).toEpochDay(), periodTo = day.plusDays(10).toEpochDay(),
            openingBalanceMinor = 0, closingBalanceMinor = 0, totalRows = 0,
            inserted = 0, duplicates = 0, reconciled = 0, reviewCount = 0, importedAt = receivedAt - 60_000,
        ))
        val unresolved = importer.import(bill(), receivedAt)
        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, unresolved.outcome)

        val resolved = importer.resolveDiagnostic(requireNotNull(unresolved.diagnosticId), everyday)

        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, resolved.outcome)
        assertEquals(SmsDiagnosticReason.STATEMENT_COVERS_PERIOD, resolved.reason)
        assertEquals(0, db.transactionDao().allForIntegrity().size)
    }

    private fun bill(amount: String = "45.60", balance: String = "210.15") = """
        Service/utility payment
        Amount: $amount GEL;
        Service: Example Utility, ID: MED 000000
        Balance: $balance GEL
        Date: äDateñ;
        Check details in MyCredo: https://mycredo.page.link/Pdk
    """.trimIndent()
}
