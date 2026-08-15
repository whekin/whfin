package dev.whekin.whfin.data.categorization

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.CounterpartyRuleEntity
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Rules arriving after the history they describe. Every case here is a row that was already in the
 * ledger, uncategorized, when the rule that explains it was added.
 */
@RunWith(AndroidJUnit4::class)
class CategoryMaintenanceInstrumentedTest {

    private lateinit var db: WhfinDatabase
    private var accountId = 0L
    private var bankFeesId = 0L
    private var transportId = 0L
    private var rentId = 0L

    @Before
    fun createDatabase() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WhfinDatabase::class.java,
        ).allowMainThreadQueries().build()
        accountId = db.accountDao().insert(
            AccountEntity(name = "Everyday", type = AccountType.BANK, currency = "GEL"),
        )
        bankFeesId = category("Bank fees", "AccountBalance")
        transportId = category("Transport", "DirectionsBus")
        rentId = category("Rent", "Home")
    }

    @After
    fun closeDatabase() = db.close()

    private suspend fun category(name: String, icon: String): Long = db.categoryDao().insert(
        CategoryEntity(name = name, kind = CategoryKind.EXPENSE, icon = icon, color = 0),
    )

    private suspend fun entry(
        note: String? = null,
        merchantId: Long? = null,
        iban: String? = null,
        rawCounterparty: String? = null,
        amountMinor: Long = -1500,
        source: TxSource = TxSource.STATEMENT,
    ): Long = db.transactionDao().insert(
        TransactionEntity(
            accountId = accountId,
            amountMinor = amountMinor,
            currency = "GEL",
            occurredAt = 1_000,
            postedAt = 1_000,
            merchantId = merchantId,
            rawCounterparty = rawCounterparty,
            counterpartyIban = iban,
            note = note,
            status = TxStatus.CONFIRMED,
            source = source,
            createdAt = 1_000,
        ),
    )

    private suspend fun categoryOf(id: Long): Long? = db.transactionDao().byId(id)?.categoryId

    @Test
    fun feeAlreadyImported_getsItsCategoryFromTheLabelTheBankLeftInTheNote() = runBlocking {
        val fee = entry(note = "გადარიცხვის საკომისიო")

        val result = CategoryMaintenance.run(db)

        assertEquals(bankFeesId, categoryOf(fee))
        assertEquals(1, result.operationsMatched)
    }

    @Test
    fun cardPaymentNote_isNeverMistakenForAnOperationLabel() = runBlocking {
        val payment = entry(note = "გადახდა - EXAMPLE SHOP 7.14 GEL 09.07.2025")

        CategoryMaintenance.run(db)

        assertNull(categoryOf(payment))
    }

    @Test
    fun aCategoryTheUserChose_isNeverRevisited() = runBlocking {
        val fee = entry(note = "გადარიცხვის საკომისიო")
        db.transactionDao().categorizeIfUnassigned(fee, rentId)

        CategoryMaintenance.run(db)

        assertEquals(rentId, categoryOf(fee))
    }

    @Test
    fun merchantRecognizedByAnAppUpdate_fixesItsOwnHistory() = runBlocking {
        val merchantId = db.merchantDao().insert(
            MerchantEntity(normalizedKey = "jetshr", displayName = "jetshr"),
        )
        val first = entry(merchantId = merchantId)
        val second = entry(merchantId = merchantId)

        val result = CategoryMaintenance.run(db)

        assertEquals(transportId, categoryOf(first))
        assertEquals(transportId, categoryOf(second))
        assertEquals(transportId, db.merchantDao().byKey("jetshr")?.categoryId)
        assertEquals(1, result.merchantsMatched)
    }

    @Test
    fun namedRecipient_catchesEverySpellingOfTheSameAccount() = runBlocking {
        val iban = "GE00WH0000000000000042"
        val early = entry(iban = iban)
        val late = entry(iban = iban)
        db.counterpartyRuleDao().upsert(
            CounterpartyRuleEntity(
                iban = iban,
                displayName = "Example Person",
                categoryId = rentId,
                createdAt = 1_000,
            ),
        )

        CategoryMaintenance.run(db)

        assertEquals(rentId, categoryOf(early))
        assertEquals(rentId, categoryOf(late))
    }

    @Test
    fun recipientWithoutACategory_leavesTheirTransfersAlone() = runBlocking {
        val iban = "GE00WH0000000000000043"
        val transfer = entry(iban = iban)
        db.counterpartyRuleDao().upsert(
            CounterpartyRuleEntity(iban = iban, displayName = "Example Person", createdAt = 1_000),
        )

        CategoryMaintenance.run(db)

        assertNull(categoryOf(transfer))
    }

    @Test
    fun depositInterestAlreadyImported_landsInIncomeWithoutBeingAsked() = runBlocking {
        val interestId = db.categoryDao().insert(
            CategoryEntity(name = "Interest", kind = CategoryKind.INCOME, icon = "Percent", color = 0),
        )
        val paid = entry(note = "საპროცენტო სარგებლის გადახდა", amountMinor = 597)

        CategoryMaintenance.run(db)

        assertEquals(interestId, categoryOf(paid))
    }

    /**
     * Credo names the destination ledger on a cash deposit, and that ledger is the user's own. Asked
     * about as a counterparty it would invite a rule about themselves.
     */
    @Test
    fun anOwnAccountIsNeverOfferedAsACounterparty() = runBlocking {
        val ownIban = "GE00WH0000000000000099"
        db.accountDao().insert(
            AccountEntity(name = "Credo GEL", type = AccountType.BANK, currency = "GEL", iban = ownIban),
        )
        entry(iban = ownIban, rawCounterparty = "Example Owner", amountMinor = 230_000)
        entry(iban = "GE00WH0000000000000100", rawCounterparty = "Example Payer", amountMinor = 700_000)

        val senders = db.transactionDao().observeUncategorizedIncomeCounterparties().first()

        assertEquals(listOf("GE00WH0000000000000100"), senders.map { it.iban })
    }

    @Test
    fun incomeAndSpendingAreCountedApart() = runBlocking {
        entry(rawCounterparty = "Example Shop")
        entry(iban = "GE00WH0000000000000101", rawCounterparty = "Example Payer", amountMinor = 700_000)

        val spending = db.transactionDao().observeCategoryCoverage().first()
        val income = db.transactionDao().observeIncomeCoverage().first()

        assertEquals(1, spending.totalExpenses)
        assertEquals(1, income.totalExpenses)
        assertEquals(0, income.categorizedExpenses)
    }

    @Test
    fun aTransferIsAskedAboutByRecipient_notOnceMoreByNameSpelling() = runBlocking {
        val merchantId = db.merchantDao().insert(
            MerchantEntity(normalizedKey = "example person", displayName = "Example Person"),
        )
        entry(
            merchantId = merchantId,
            iban = "GE00WH0000000000000044",
            rawCounterparty = "Example Person",
        )

        val merchants = db.transactionDao().observeUncategorizedMerchants().first()
        val counterparties = db.transactionDao().observeUncategorizedCounterparties().first()

        assertEquals(emptyList<Long>(), merchants.map { it.merchantId })
        assertEquals(1, counterparties.size)
        assertEquals("GE00WH0000000000000044", counterparties.single().iban)
        assertEquals("Example Person", counterparties.single().displayName)
    }
}
