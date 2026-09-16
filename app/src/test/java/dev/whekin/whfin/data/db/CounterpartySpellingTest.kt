package dev.whekin.whfin.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.categorization.MerchantCategorizer
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

    private suspend fun payment(merchantId: Long, categoryId: Long? = null): Long =
        db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                amountMinor = -1000,
                currency = "GEL",
                occurredAt = 1_700_000_000_000,
                merchantId = merchantId,
                categoryId = categoryId,
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
            ),
        )

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
