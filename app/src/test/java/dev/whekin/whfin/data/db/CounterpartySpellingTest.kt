package dev.whekin.whfin.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.categorization.MerchantCategorizer
import dev.whekin.whfin.ui.settings.CounterpartyName
import dev.whekin.whfin.ui.settings.survivingName
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One counterparty, two alphabets — because the name belongs to their bank, not to the statement.
 *
 * All names here are synthetic: common Georgian words and given names, chosen for the letters they
 * exercise rather than copied from anyone's ledger.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CounterpartySpellingTest {

    private lateinit var db: WhfinDatabase
    private var accountId: Long = 0
    private var rentId: Long = 0
    private var groceriesId: Long = 0

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            WhfinDatabase::class.java,
        ).allowMainThreadQueries().build()
        accountId = db.accountDao().insert(
            AccountEntity(name = "Account", type = AccountType.BANK, currency = "GEL"),
        )
        rentId = db.categoryDao().insert(
            CategoryEntity(name = "Rent", kind = CategoryKind.EXPENSE, icon = "Home", color = 1),
        )
        groceriesId = db.categoryDao().insert(
            CategoryEntity(name = "Groceries", kind = CategoryKind.EXPENSE, icon = "ShoppingCart", color = 2),
        )
    }

    @After fun tearDown() = db.close()

    private suspend fun merchant(key: String, categoryId: Long? = null): Long =
        db.merchantDao().insert(
            MerchantEntity(normalizedKey = key, displayName = key, categoryId = categoryId),
        )

    private suspend fun payment(
        merchantId: Long,
        categoryId: Long? = null,
        counterpartyIban: String? = null,
    ): Long = db.transactionDao().insert(
        TransactionEntity(
            accountId = accountId,
            amountMinor = -1000,
            currency = "GEL",
            occurredAt = 1_700_000_000_000,
            merchantId = merchantId,
            categoryId = categoryId,
            counterpartyIban = counterpartyIban,
            status = TxStatus.CONFIRMED,
            source = TxSource.STATEMENT,
        ),
    )

    /** Synthetic counterparty accounts: checksum 00, as every committed fixture must use. */
    private val theirBank = "GE00TB0000000000000123"
    private val theirOtherBank = "GE00BG0000000000000456"

    @Test fun twoSpellingsPaidIntoOneAccountAreOneCounterparty() = runBlocking {
        // The same landlord, abbreviated by one bank and punctuated differently on another line:
        // no shared skeleton, no shared alphabet difference — only the account proves it.
        val dotted = merchant("magda.k")
        val spaced = merchant("magda k")
        payment(dotted, counterpartyIban = theirBank)
        repeat(3) { payment(spaced, counterpartyIban = theirBank) }

        assertEquals(1, db.repairCounterpartySpellings())

        assertEquals(spaced, db.merchantDao().all().single().id)
    }

    @Test fun aRetiredSpellingKeepsAnsweringForTheCounterparty() = runBlocking {
        val dotted = merchant("magda.k")
        val spaced = merchant("magda k")
        payment(dotted, counterpartyIban = theirBank)
        repeat(3) { payment(spaced, counterpartyIban = theirBank) }
        db.repairCounterpartySpellings()

        // The next statement writes the retired spelling again; it must not start a second name.
        val resolved = MerchantCategorizer.resolve(db, "magda.k")

        assertEquals(spaced, resolved?.id)
        assertEquals(1, db.merchantDao().all().size)
    }

    @Test fun accountsJoinNamesThroughOneAnother() = runBlocking {
        // One person with two banks pulls both of their spellings together.
        val georgian = merchant("გიორგი ხარაძე")
        val both = merchant("g. kharadze")
        val other = merchant("kharadze giorgi")
        payment(georgian, counterpartyIban = theirBank)
        payment(both, counterpartyIban = theirBank)
        payment(both, counterpartyIban = theirOtherBank)
        payment(other, counterpartyIban = theirOtherBank)

        assertEquals(2, db.repairCounterpartySpellings())

        assertEquals(1, db.merchantDao().all().size)
    }

    @Test fun aNameStandingOverManyAccountsIsNotEvidence() = runBlocking {
        val label = merchant("transfer")
        val first = merchant("first payee")
        val second = merchant("second payee")
        listOf("1", "2", "3", "4", "5").forEach { tail ->
            payment(label, counterpartyIban = "GE00TB000000000000000$tail")
        }
        payment(first, counterpartyIban = "GE00TB0000000000000001")
        payment(second, counterpartyIban = "GE00TB0000000000000002")

        assertEquals(0, db.repairCounterpartySpellings())

        assertEquals(3, db.merchantDao().all().size)
    }

    @Test fun oneAccountDoesNotOverrideTwoDifferentCategories() = runBlocking {
        val first = merchant("magda.k", rentId)
        val second = merchant("magda k", groceriesId)
        payment(first, counterpartyIban = theirBank)
        payment(second, counterpartyIban = theirBank)

        assertEquals(0, db.repairCounterpartySpellings())

        assertEquals(2, db.merchantDao().all().size)
    }

    @Test fun anAbbreviationAndAFullNameNeedTheOwner() = runBlocking {
        // One bank prints "Magda K", another the full name in Georgian, and the transfer names no
        // account at all. Nothing here is proof, so the pass leaves both alone.
        val short = merchant("magda k")
        val full = merchant("მაგდა ხარაძე")
        payment(short)
        payment(full)

        assertEquals(0, db.repairCounterpartySpellings())
        assertEquals(2, db.merchantDao().all().size)

        assertTrue(db.mergeCounterparties(short, full))

        val left = db.merchantDao().all().single()
        assertEquals(short, left.id)
        // And the full name, when the next TBC statement writes it again, finds them.
        assertEquals(short, MerchantCategorizer.resolve(db, "მაგდა ხარაძე")?.id)
    }

    /**
     * The dialog names the surviving name before anything is written, so its rule and the merge's
     * rule have to be the same one — checked here on all three tie-breaks in turn.
     */
    @Test fun theScreenPredictsTheNameThatWillSurvive() = runBlocking {
        suspend fun check(first: Pair<String, Long?>, second: Pair<String, Long?>, uses: Pair<Int, Int>) {
            db.merchantDao().all().forEach { db.merchantDao().deleteMerchant(it.id) }
            val a = merchant(first.first, first.second)
            val b = merchant(second.first, second.second)
            repeat(uses.first) { payment(a) }
            repeat(uses.second) { payment(b) }
            val usage = db.merchantDao().usageCounts().associate { it.merchantId to it.transactionCount }
            val byId = db.merchantDao().all().associateBy { it.id }
            fun view(id: Long) = CounterpartyName(
                merchantId = id,
                displayName = byId.getValue(id).normalizedKey,
                transactionCount = usage[id] ?: 0,
                categoryName = byId.getValue(id).categoryId?.let { "Rent" },
            )
            val predicted = survivingName(view(a), view(b)).merchantId
            db.mergeCounterparties(a, b)
            assertEquals(predicted, db.merchantDao().all().single().id)
        }

        check("taught" to rentId, "busier" to null, 1 to 9)
        check("quiet" to null, "busier" to null, 1 to 9)
        check("first" to null, "second" to null, 3 to 3)
    }

    @Test fun joiningByHandRefusesToJoinSomethingWithItself() = runBlocking {
        val only = merchant("magda k")

        assertFalse(db.mergeCounterparties(only, only))
        assertFalse(db.mergeCounterparties(only, only + 999))
    }

    @Test fun twoAlphabetsBecomeOneCounterparty() = runBlocking {
        val latin = merchant("giorgi kharadze", rentId)
        val georgian = merchant("გიორგი ხარაძე")
        val known = payment(latin)
        val orphan = payment(georgian)
        val alreadyFiled = payment(georgian, groceriesId)

        assertEquals(1, db.repairCounterpartySpellings())

        assertNull(db.merchantDao().all().firstOrNull { it.id == georgian })
        assertEquals(latin, db.transactionDao().byId(known)?.merchantId)
        // The operations move to the counterparty they turned out to be, and the ones with no
        // category of their own inherit what the owner already taught about them.
        assertEquals(latin, db.transactionDao().byId(orphan)?.merchantId)
        assertEquals(rentId, db.transactionDao().byId(orphan)?.categoryId)
        // A category set by hand is never overwritten by the merge.
        assertEquals(groceriesId, db.transactionDao().byId(alreadyFiled)?.categoryId)
    }

    @Test fun theSecondSpellingNoLongerArrivesAsASecondCounterparty() = runBlocking {
        val latin = merchant("giorgi kharadze", rentId)
        db.repairCounterpartySpellings()

        val resolved = MerchantCategorizer.resolve(db, "გიორგი ხარაძე")

        assertEquals(latin, resolved?.id)
        assertEquals(1, db.merchantDao().all().size)
    }

    @Test fun aNameSeenOnlyOnceStillLearnsItsSkeleton() = runBlocking {
        val first = MerchantCategorizer.resolve(db, "შპს ცისკარი")

        val second = MerchantCategorizer.resolve(db, "SHPS TSISKARI")

        assertEquals(first?.id, second?.id)
        assertEquals(1, db.merchantDao().all().size)
    }

    @Test fun twoGeorgianNamesThatDifferOnlyByAspirationStayTwoNames() = runBlocking {
        merchant("თამარი")
        merchant("ტამარი")

        assertEquals(0, db.repairCounterpartySpellings())

        assertEquals(2, db.merchantDao().all().size)
        // Neither claims the shared skeleton, so a later spelling cannot attach to whichever one
        // happened to be written first.
        assertNull(db.merchantDao().byAlias("tamari"))
    }

    @Test fun spellingsFiledUnderDifferentCategoriesAreLeftAlone() = runBlocking {
        merchant("giorgi kharadze", rentId)
        merchant("გიორგი ხარაძე", groceriesId)

        assertEquals(0, db.repairCounterpartySpellings())

        assertEquals(2, db.merchantDao().all().size)
    }

    @Test fun theTaughtNameSurvivesEvenWhenTheOtherHasMoreOperations() = runBlocking {
        val taught = merchant("გიორგი ხარაძე", rentId)
        val busier = merchant("giorgi kharadze")
        repeat(3) { payment(busier) }

        db.repairCounterpartySpellings()

        val left = db.merchantDao().all().single()
        assertEquals(taught, left.id)
        assertEquals(rentId, left.categoryId)
    }

    @Test fun aSecondPassHasNothingLeftToDo() = runBlocking {
        merchant("giorgi kharadze", rentId)
        merchant("გიორგი ხარაძე")

        assertEquals(1, db.repairCounterpartySpellings())
        assertEquals(0, db.repairCounterpartySpellings())
    }
}
