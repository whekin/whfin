package dev.whekin.whfin.ui.accounts

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.data.db.FundRole
import dev.whekin.whfin.data.rates.ExchangeRate
import dev.whekin.whfin.data.rates.moneySplit
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountOverviewTest {
    @Test
    fun `deposit product can remain available while an ordinary ledger can be reserve`() {
        val accounts = listOf(
            account(1, "Daily deposit", AccountType.SAVINGS, "GEL", 5_000),
            account(2, "Rainy day", AccountType.BANK, "GEL", 3_000, fundRole = FundRole.RESERVE),
        )

        val overview = accountOverviewData(accounts, rates, "GEL")

        assertEquals(BigDecimal("50.00"), overview.split.available.amount)
        assertEquals(BigDecimal("30.00"), overview.split.reserve.amount)
    }

    @Test
    fun `the overview opens with the number the accounts headline shows`() {
        // This screen exists to explain that headline. It used to start from a different reading —
        // lari only, watch-only ledgers dropped — so the two screens disagreed by the size of the
        // foreign and chain money with nothing on either of them saying so.
        val accounts = listOf(
            account(1, "Credo", AccountType.BANK, "GEL", 10_000, group = "Credo"),
            account(2, "Credo USD", AccountType.BANK, "USD", 50_000, group = "Credo"),
        )

        val overview = accountOverviewData(accounts, rates, "GEL")

        assertEquals(moneySplit(accountReadings(accounts), rates, "GEL").total, overview.split.total)
        assertEquals(BigDecimal("1450.00"), overview.split.total.amount)
    }

    @Test
    fun `sources add up to assets and are read in one currency`() {
        val accounts = listOf(
            account(1, "Credo", AccountType.BANK, "GEL", 10_000, group = "Credo"),
            account(2, "Overdraft", AccountType.BANK, "GEL", -2_000, group = "Credo"),
            account(3, "Reserve", AccountType.SAVINGS, "GEL", 3_000, fundRole = FundRole.RESERVE),
            account(4, "Credo USD", AccountType.BANK, "USD", 500, group = "Credo"),
        )

        val overview = accountOverviewData(accounts, rates, "GEL")

        assertEquals(
            overview.split.assets.amount,
            overview.sources.fold(BigDecimal.ZERO) { sum, share -> sum.add(share.amount) },
        )
        assertEquals(listOf("Credo", "Reserve"), overview.sources.map { it.name })
        assertEquals(BigDecimal("-20.00"), overview.split.liabilities.amount)
    }

    @Test
    fun `native balances name every currency other than the one being read`() {
        val accounts = listOf(
            account(1, "Credo", AccountType.BANK, "GEL", 10_000),
            account(2, "Credo USD", AccountType.BANK, "USD", 500),
            account(3, "Cash EUR", AccountType.CASH, "EUR", 0),
        )

        val overview = accountOverviewData(accounts, rates, "GEL")

        assertEquals(listOf(NativeCurrencyBalance("USD", BigDecimal("5.00"))), overview.nativeCurrencies)
    }

    @Test
    fun `a watch-only ledger that was never read contributes nothing`() {
        val chain = account(9, "USDT", AccountType.CRYPTO, "USDT", 0)

        assertEquals(null, chain.reading())
    }

    private val rates = mapOf(
        "USD" to ExchangeRate("USD", BigDecimal("2.70"), observedAt = 1_000),
        "EUR" to ExchangeRate("EUR", BigDecimal("3.10"), observedAt = 1_000),
    )

    private fun account(
        id: Long,
        name: String,
        type: AccountType,
        currency: String,
        balanceMinor: Long,
        group: String? = null,
        fundRole: FundRole = FundRole.AVAILABLE,
        bankProduct: BankProduct? = null,
    ) = AccountWithBalance(
        account = AccountEntity(
            id = id,
            name = name,
            type = type,
            currency = currency,
            fundRole = fundRole,
            bankProduct = bankProduct,
        ),
        balanceMinor = balanceMinor,
        cardMasks = emptyList(),
        groupName = group,
    )
}
