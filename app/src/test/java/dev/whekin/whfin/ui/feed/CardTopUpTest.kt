package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.data.db.FundRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardTopUpTest {

    private val card = account(id = 1, name = "Everyday", groupId = 10)

    @Test
    fun `money in the same bank is proposed first`() {
        val accounts = listOf(
            card,
            account(id = 2, name = "Reserve", groupId = 10, fundRole = FundRole.RESERVE),
            account(id = 3, name = "Cash", type = AccountType.CASH, groupId = null),
        )
        val balances = mapOf(1L to 5_000L, 2L to 200_000L, 3L to 900_000L)

        assertEquals(2L, cardTopUpSource(accounts, balances, card)?.id)
    }

    @Test
    fun `an empty ledger is never proposed`() {
        val accounts = listOf(
            card,
            account(id = 2, name = "Reserve", groupId = 10, fundRole = FundRole.RESERVE),
            account(id = 3, name = "Cash", type = AccountType.CASH, groupId = null),
        )
        val balances = mapOf(1L to 5_000L, 2L to 0L, 3L to 90_000L)

        assertEquals(3L, cardTopUpSource(accounts, balances, card)?.id)
    }

    @Test
    fun `another currency cannot fund this card`() {
        val accounts = listOf(
            card,
            account(id = 2, name = "USD", groupId = 10, currency = "USD"),
        )
        val balances = mapOf(2L to 500_000L)

        assertNull(cardTopUpSource(accounts, balances, card))
    }

    @Test
    fun `a watch-only wallet is not a source`() {
        val accounts = listOf(
            card,
            account(id = 2, name = "USDT", type = AccountType.CRYPTO, groupId = 20, currency = "GEL"),
        )
        val balances = mapOf(2L to 500_000L)

        assertNull(cardTopUpSource(accounts, balances, card))
    }

    @Test
    fun `a term deposit is the last resort, not the first idea`() {
        val accounts = listOf(
            card,
            account(
                id = 2,
                name = "Term deposit",
                groupId = 10,
                fundRole = FundRole.RESERVE,
                bankProduct = BankProduct.TERM_DEPOSIT,
            ),
            account(id = 3, name = "Everyday", groupId = 10, bankProduct = BankProduct.CURRENT_ACCOUNT),
        )
        val balances = mapOf(2L to 900_000L, 3L to 50_000L)

        assertEquals(3L, cardTopUpSource(accounts, balances, card)?.id)
    }

    @Test
    fun `a term deposit is still offered when nothing else holds money`() {
        val accounts = listOf(
            card,
            account(
                id = 2,
                name = "Term deposit",
                groupId = 10,
                bankProduct = BankProduct.TERM_DEPOSIT,
            ),
            account(id = 3, name = "Everyday", groupId = 10, bankProduct = BankProduct.CURRENT_ACCOUNT),
        )
        val balances = mapOf(2L to 900_000L, 3L to 0L)

        assertEquals(2L, cardTopUpSource(accounts, balances, card)?.id)
    }

    @Test
    fun `outside the bank the fullest ledger wins`() {
        val accounts = listOf(
            card,
            account(id = 2, name = "Cash", type = AccountType.CASH, groupId = null),
            account(
                id = 3,
                name = "Other bank",
                groupId = 30,
                bankProduct = BankProduct.CURRENT_ACCOUNT,
            ),
        )
        val balances = mapOf(2L to 40_000L, 3L to 300_000L)

        assertEquals(3L, cardTopUpSource(accounts, balances, card)?.id)
    }

    private fun account(
        id: Long,
        name: String,
        type: AccountType = AccountType.BANK,
        currency: String = "GEL",
        groupId: Long? = null,
        fundRole: FundRole = FundRole.AVAILABLE,
        bankProduct: BankProduct? = null,
    ) = AccountEntity(
        id = id,
        name = name,
        type = type,
        currency = currency,
        groupId = groupId,
        fundRole = fundRole,
        bankProduct = bankProduct,
    )
}
