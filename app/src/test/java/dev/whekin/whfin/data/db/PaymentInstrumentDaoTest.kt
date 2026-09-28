package dev.whekin.whfin.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PaymentInstrumentDaoTest {
    private lateinit var db: WhfinDatabase
    private lateinit var account: AccountEntity

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val groupId = db.financialGroupDao().insert(
            FinancialGroupEntity(
                name = "Credo",
                type = FinancialGroupType.BANK,
                provider = "Credo",
            ),
        )
        val id = db.accountDao().insert(
            AccountEntity(
                name = "Everyday",
                type = AccountType.BANK,
                groupId = groupId,
                currency = "GEL",
            ),
        )
        account = requireNotNull(db.accountDao().byId(id))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `replacing an existing card updates its physical or virtual type`() = runBlocking {
        val dao = db.paymentInstrumentDao()
        dao.replaceForAccount(account, listOf("0001" to PaymentInstrumentType.PHYSICAL_CARD))

        dao.replaceForAccount(
            account,
            listOf("0001" to PaymentInstrumentType.VIRTUAL_CARD),
            primaryLast4 = "0001",
        )

        val card = dao.forAccount(account.id).single()
        assertEquals(PaymentInstrumentType.VIRTUAL_CARD, card.type)
        assertEquals(true, card.isPrimary)
    }

    @Test fun `discovered suffix is linked without guessing type or replacing an owner choice`() = runBlocking {
        val dao = db.paymentInstrumentDao()
        assertTrue(dao.linkDiscoveredForAccounts(listOf(account), "0001"))
        assertEquals(PaymentInstrumentType.UNCLASSIFIED_CARD, dao.forAccount(account.id).single().type)
        dao.replaceForAccount(account, listOf("0001" to PaymentInstrumentType.VIRTUAL_CARD), "0001")
        assertTrue(dao.linkDiscoveredForAccounts(listOf(account), "0001"))
        assertEquals(PaymentInstrumentType.VIRTUAL_CARD, dao.forAccount(account.id).single().type)
        assertTrue(dao.forAccount(account.id).single().isPrimary)

        val otherId = db.accountDao().insert(account.copy(id = 0, name = "Other"))
        val other = requireNotNull(db.accountDao().byId(otherId))
        assertFalse(dao.linkDiscoveredForAccounts(listOf(other), "0001"))
        assertTrue(dao.forAccount(other.id).isEmpty())
        assertTrue(runCatching { dao.replaceForAccount(other,
            listOf("0001" to PaymentInstrumentType.PHYSICAL_CARD)) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(dao.forAccount(other.id).isEmpty())
        assertEquals(PaymentInstrumentType.VIRTUAL_CARD, dao.forAccount(account.id).single().type)
    }
}
