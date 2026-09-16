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

    private fun bill(amount: String = "45.60", balance: String = "210.15") = """
        Service/utility payment
        Amount: $amount GEL;
        Service: Example Utility, ID: MED 000000
        Balance: $balance GEL
        Date: äDateñ;
        Check details in MyCredo: https://mycredo.page.link/Pdk
    """.trimIndent()
}
