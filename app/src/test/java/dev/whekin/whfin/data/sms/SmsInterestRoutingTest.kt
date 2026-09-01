package dev.whekin.whfin.data.sms

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.FinancialGroupEntity
import dev.whekin.whfin.data.db.FinancialGroupType
import dev.whekin.whfin.data.db.FundRole
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.db.SmsDiagnosticReason
import dev.whekin.whfin.data.db.WhfinDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Interest is paid on a deposit, and which accounts are deposits is already stated.
 *
 * The stated balance cannot answer it on its own: reaching that figure means adding up everything the
 * ledger recorded since the bank last declared one, so the deposit its owner constantly transfers out
 * of is exactly where the arithmetic misses. The number the notice prints does not depend on the
 * ledger being complete.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmsInterestRoutingTest {
    private lateinit var db: WhfinDatabase
    private lateinit var importer: SmsTransactionImporter
    private var groupId = 0L
    private var currentId = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        groupId = db.financialGroupDao().insert(
            FinancialGroupEntity(name = "Credo", type = FinancialGroupType.BANK, provider = "Credo"),
        )
        // Always present, and never an answer to interest.
        currentId = db.accountDao().insert(
            AccountEntity(
                name = "Everyday GEL",
                type = AccountType.BANK,
                groupId = groupId,
                currency = "GEL",
                iban = "GE00CD0000000000000001",
                bankProduct = BankProduct.CURRENT_ACCOUNT,
            ),
        )
        importer = SmsTransactionImporter(db)
    }

    /**
     * A deposit paying interest on each day's balance, spent from constantly: available by fund role
     * and a deposit by bank product. Reading "is this a deposit" off the role would miss exactly this.
     */
    private suspend fun demandDeposit(): Long = db.accountDao().insert(
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

    private suspend fun termDeposit(): Long = db.accountDao().insert(
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

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun theOnlyDepositOfThatCurrencyNeedsNoQuestion() = runBlocking {
        val demandId = demandDeposit()

        val result = importer.import(interest(number = "10000002"))

        assertEquals(SmsDiagnosticOutcome.IMPORTED, result.outcome)
        val transaction = db.transactionDao().byId(result.transactionId!!)!!
        assertEquals(demandId, transaction.accountId)
        // Money arriving, not leaving.
        assertEquals(531L, transaction.amountMinor)
    }

    @Test
    fun aSecondDepositMakesItAQuestionAboutDepositsAlone() = runBlocking {
        demandDeposit()
        termDeposit()

        val result = importer.import(interest(number = "10000002"))

        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, result.outcome)
        assertEquals(SmsDiagnosticReason.MULTIPLE_ACCOUNTS, result.reason)
        val diagnostic = db.smsDiagnosticDao().byId(result.diagnosticId!!)!!
        // The question has to be able to say which deposit it is about.
        assertEquals("10000002", diagnostic.depositNumber)
        assertNull(diagnostic.transactionId)
    }

    @Test
    fun answeringOnceTeachesTheNumberAndTheNextNoticeRoutesItself() = runBlocking {
        val demandId = demandDeposit()
        termDeposit()

        val asked = importer.import(interest(number = "10000002"))
        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, asked.outcome)

        val resolved = importer.resolveDiagnostic(asked.diagnosticId!!, demandId)

        assertEquals(SmsDiagnosticOutcome.IMPORTED, resolved.outcome)
        assertEquals(demandId, db.transactionDao().byId(resolved.transactionId!!)!!.accountId)
        assertEquals("10000002", db.accountDao().byId(demandId)!!.depositNumber)

        // The same deposit, a later month: identity answers it, and no ledger arithmetic is needed.
        val next = importer.import(interest(number = "10000002", amount = "6.02", day = "03/05/2026"))

        assertEquals(SmsDiagnosticOutcome.IMPORTED, next.outcome)
        assertEquals(demandId, db.transactionDao().byId(next.transactionId!!)!!.accountId)
    }

    @Test
    fun aNoticeAboutTheOtherDepositIsStillAQuestion() = runBlocking {
        val demandId = demandDeposit()
        val termId = termDeposit()

        val asked = importer.import(interest(number = "10000002"))
        importer.resolveDiagnostic(asked.diagnosticId!!, demandId)

        val other = importer.import(interest(number = "10000009", amount = "1.20", day = "03/05/2026"))

        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, other.outcome)
        // The learnt number belongs to one deposit only; it must not answer for its neighbour.
        assertNull(db.accountDao().byId(termId)!!.depositNumber)
    }

    @Test
    fun oneAnswerAlsoPlacesTheNoticesAlreadyWaitingOnThatDeposit() = runBlocking {
        demandDeposit()
        val termId = termDeposit()

        val first = importer.import(interest(number = "10000002", amount = "5.31", day = "03/04/2026"))
        val second = importer.import(interest(number = "10000002", amount = "6.02", day = "03/05/2026"))
        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, first.outcome)
        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, second.outcome)

        importer.resolveDiagnostic(second.diagnosticId!!, termId)

        // Both were about the same deposit, so answering either answers both.
        listOf(first, second).forEach { asked ->
            val diagnostic = db.smsDiagnosticDao().byId(asked.diagnosticId!!)!!
            assertEquals(SmsDiagnosticOutcome.IMPORTED, diagnostic.outcome)
            assertNotNull(diagnostic.transactionId)
            assertEquals(termId, db.transactionDao().byId(diagnostic.transactionId!!)!!.accountId)
        }
    }

    @Test
    fun noDepositAtAllSaysSoRatherThanOfferingTheCurrentAccounts() = runBlocking {
        val result = importer.import(interest(number = "10000002"))

        assertEquals(SmsDiagnosticOutcome.CHOOSE_ACCOUNT, result.outcome)
        // A current account is not an answer to interest, so its presence is not "several accounts".
        assertEquals(SmsDiagnosticReason.NO_ACCOUNT, result.reason)
        assertNotNull(db.accountDao().byId(currentId))
    }

    @Test
    fun interestTakesItsCategoryFromWhatTheBankSaidTheOperationIs() = runBlocking {
        demandDeposit()
        val percent = db.categoryDao().insert(
            CategoryEntity(name = "Percent", kind = CategoryKind.INCOME, icon = "Percent", color = 0),
        )

        val result = importer.import(interest(number = "10000002"))

        // The statement importer files the identical row this way; which door it came through is not
        // something the ledger should record.
        assertEquals(percent, db.transactionDao().byId(result.transactionId!!)!!.categoryId)
    }

    @Test
    fun withoutThatCategoryTheRowStaysBlankRatherThanInventingOne() = runBlocking {
        demandDeposit()

        val result = importer.import(interest(number = "10000002"))

        // Categories are offered from the evidence of rows like this, never seeded behind the owner:
        // one they deleted must not come back on its own.
        assertNull(db.transactionDao().byId(result.transactionId!!)!!.categoryId)
    }

    private fun interest(
        number: String,
        amount: String = "5.31",
        day: String = "03/04/2026",
        balance: String = "640.28",
    ) = """
        Accrued interest on your $number deposit, $day,
        amount $amount GEL; Available Balance: $balance GEL.
    """.trimIndent()
}
