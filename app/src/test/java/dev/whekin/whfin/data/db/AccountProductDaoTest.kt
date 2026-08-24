package dev.whekin.whfin.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountProductDaoTest {
    private lateinit var db: WhfinDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            WhfinDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun productEditAppliesToEveryCurrencyLedgerOfTheIbanContainer() = runBlocking {
        val groupId = db.financialGroupDao().insert(
            FinancialGroupEntity(
                name = "Credo",
                type = FinancialGroupType.BANK,
                provider = "Credo",
            ),
        )
        db.accountDao().insert(
            AccountEntity(
                name = "Credo",
                type = AccountType.BANK,
                groupId = groupId,
                currency = "GEL",
                iban = "GE00CONTAINER",
                fundRole = FundRole.AVAILABLE,
            ),
        )
        db.accountDao().insert(
            AccountEntity(
                name = "Credo",
                type = AccountType.BANK,
                groupId = groupId,
                currency = "USD",
                iban = "GE00CONTAINER",
                fundRole = FundRole.RESERVE,
            ),
        )

        db.accountDao().updateIbanContainer(
            groupId = groupId,
            iban = "GE00CONTAINER",
            name = "Rainy day",
            fundRole = FundRole.AVAILABLE,
            bankProduct = BankProduct.TERM_DEPOSIT,
        )

        assertEquals(
            listOf(BankProduct.TERM_DEPOSIT, BankProduct.TERM_DEPOSIT),
            db.accountDao().byGroup(groupId).map { it.bankProduct },
        )
        assertEquals(
            listOf(FundRole.AVAILABLE, FundRole.AVAILABLE),
            db.accountDao().byGroup(groupId).map { it.fundRole },
        )
    }

    @Test
    fun bankDetailsProductEditPreservesEachLedgerFundRole() = runBlocking {
        val groupId = db.financialGroupDao().insert(
            FinancialGroupEntity(
                name = "Credo",
                type = FinancialGroupType.BANK,
                provider = "Credo",
            ),
        )
        db.accountDao().insert(
            AccountEntity(
                name = "Credo",
                type = AccountType.BANK,
                groupId = groupId,
                currency = "GEL",
                iban = "GE00BANKDETAILS",
                fundRole = FundRole.AVAILABLE,
            ),
        )
        db.accountDao().insert(
            AccountEntity(
                name = "Credo",
                type = AccountType.BANK,
                groupId = groupId,
                currency = "USD",
                iban = "GE00BANKDETAILS",
                fundRole = FundRole.RESERVE,
            ),
        )

        db.accountDao().updateIbanBankProduct(
            groupId = groupId,
            iban = "GE00BANKDETAILS",
            bankProduct = BankProduct.TERM_DEPOSIT,
        )

        assertEquals(
            listOf(BankProduct.TERM_DEPOSIT, BankProduct.TERM_DEPOSIT),
            db.accountDao().byGroup(groupId).map { it.bankProduct },
        )
        assertEquals(
            listOf(FundRole.AVAILABLE, FundRole.RESERVE),
            db.accountDao().byGroup(groupId).map { it.fundRole },
        )
    }
}
