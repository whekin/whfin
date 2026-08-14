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

    private suspend fun expense(
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
        val fee = expense(note = "გადარიცხვის საკომისიო")

        val result = CategoryMaintenance.run(db)

        assertEquals(bankFeesId, categoryOf(fee))
        assertEquals(1, result.operationsMatched)
    }

    @Test
    fun cardPaymentNote_isNeverMistakenForAnOperationLabel() = runBlocking {
        val payment = expense(note = "გადახდა - EXAMPLE SHOP 7.14 GEL 09.07.2025")

        CategoryMaintenance.run(db)

        assertNull(categoryOf(payment))
    }

    @Test
    fun aCategoryTheUserChose_isNeverRevisited() = runBlocking {
        val fee = expense(note = "გადარიცხვის საკომისიო")
        db.transactionDao().categorizeIfUnassigned(fee, rentId)

        CategoryMaintenance.run(db)

        assertEquals(rentId, categoryOf(fee))
    }

    @Test
    fun merchantRecognizedByAnAppUpdate_fixesItsOwnHistory() = runBlocking {
        val merchantId = db.merchantDao().insert(
            MerchantEntity(normalizedKey = "jetshr", displayName = "jetshr"),
        )
        val first = expense(merchantId = merchantId)
        val second = expense(merchantId = merchantId)

        val result = CategoryMaintenance.run(db)

        assertEquals(transportId, categoryOf(first))
        assertEquals(transportId, categoryOf(second))
        assertEquals(transportId, db.merchantDao().byKey("jetshr")?.categoryId)
        assertEquals(1, result.merchantsMatched)
    }

    @Test
    fun namedRecipient_catchesEverySpellingOfTheSameAccount() = runBlocking {
        val iban = "GE00WH0000000000000042"
        val early = expense(iban = iban)
        val late = expense(iban = iban)
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
        val transfer = expense(iban = iban)
        db.counterpartyRuleDao().upsert(
            CounterpartyRuleEntity(iban = iban, displayName = "Example Person", createdAt = 1_000),
        )

        CategoryMaintenance.run(db)

        assertNull(categoryOf(transfer))
    }

    @Test
    fun aTransferIsAskedAboutByRecipient_notOnceMoreByNameSpelling() = runBlocking {
        val merchantId = db.merchantDao().insert(
            MerchantEntity(normalizedKey = "example person", displayName = "Example Person"),
        )
        expense(
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
