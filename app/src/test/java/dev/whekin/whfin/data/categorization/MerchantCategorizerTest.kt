package dev.whekin.whfin.data.categorization

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MerchantCategorizerTest {
    private lateinit var db: WhfinDatabase
    private var accountId: Long = 0
    private var transportId: Long = 0
    private var bikeId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountId = db.accountDao().insert(AccountEntity(name = "Card", type = AccountType.BANK, currency = "GEL"))
        transportId = db.categoryDao().insert(expenseCategory("Transport", "DirectionsBus"))
        bikeId = db.categoryDao().insert(expenseCategory("Bike", "PedalBike"))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun newKnownMerchant_getsSafeCategoryImmediately() = runBlocking {
        val merchant = MerchantCategorizer.resolve(db, "Jet SHR>Tbilisi GE")

        assertEquals(transportId, merchant?.categoryId)
    }

    @Test
    fun newlyRecognizedExistingMerchant_backfillsHistory() = runBlocking {
        val merchantId = db.merchantDao().insert(MerchantEntity(normalizedKey = "bike24 gmbh", displayName = "Bike24 GmbH"))
        val txId = db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                amountMinor = -11_848,
                currency = "EUR",
                occurredAt = 1,
                merchantId = merchantId,
                rawCounterparty = "Bike24 GmbH",
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
            ),
        )
        assertNull(db.transactionDao().byId(txId)?.categoryId)

        val merchant = MerchantCategorizer.resolve(db, "Bike24 GmbH")

        assertEquals(bikeId, merchant?.categoryId)
        assertEquals(bikeId, db.transactionDao().byId(txId)?.categoryId)
    }

    private fun expenseCategory(name: String, icon: String) = CategoryEntity(
        name = name,
        kind = CategoryKind.EXPENSE,
        icon = icon,
        color = 0xff78906f.toInt(),
    )
}
