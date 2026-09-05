package dev.whekin.whfin.data.rates

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.data.db.FundRole
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneySplitTest {

    @Test
    fun `available carries foreign money at its rate, not only lari`() {
        // The defect this exists for: Home converted the foreign ledgers into "available to spend"
        // and Accounts summed lari only, so one swipe between two pages of the same pager changed
        // the answer by the size of the foreign balances, and neither page said the other existed.
        val split = moneySplit(
            listOf(
                reading(1, "GEL", "2370.72"),
                reading(2, "USD", "79.10"),
                reading(3, "EUR", "169.30"),
            ),
            rates,
            "GEL",
        )

        assertEquals(BigDecimal("3109.12"), split.available.amount)
        assertTrue(split.available.missing.isEmpty())
    }

    @Test
    fun `a term the owner calls available holds the money without hiding it`() {
        val split = moneySplit(
            listOf(
                reading(1, "GEL", "500.00"),
                reading(2, "GEL", "10000.00", product = BankProduct.TERM_DEPOSIT),
            ),
            rates,
            "GEL",
        )

        assertEquals(BigDecimal("500.00"), split.available.amount)
        assertEquals(BigDecimal("10000.00"), split.heldBack.amount)
        assertEquals(BigDecimal("10500.00"), split.total.amount)
    }

    @Test
    fun `available reserve held back and chain add up to the total`() {
        val readings = listOf(
            reading(1, "GEL", "500.00"),
            reading(2, "USD", "100.00"),
            reading(3, "GEL", "8000.00", role = FundRole.RESERVE),
            reading(4, "GEL", "2000.00", product = BankProduct.TERM_DEPOSIT),
            reading(5, "USDT", "50.00", type = AccountType.CRYPTO),
        )

        val split = moneySplit(readings, rates, "GEL")
        val chain = convertReadings(readings.filter { it.account.type == AccountType.CRYPTO }, rates, "GEL")

        assertEquals(
            split.total.amount,
            listOf(split.available, split.reserve, split.heldBack, chain)
                .fold(BigDecimal.ZERO) { sum, part -> sum.add(part.amount) },
        )
    }

    @Test
    fun `a currency with no quote is named rather than counted as zero`() {
        val split = moneySplit(
            listOf(reading(1, "GEL", "100.00"), reading(2, "AMD", "40000.00")),
            rates,
            "GEL",
        )

        assertEquals(BigDecimal("100.00"), split.available.amount)
        assertEquals(setOf("AMD"), split.available.missing)
        assertEquals(setOf("AMD"), split.total.missing)
    }

    @Test
    fun `assets and liabilities split the same reading by sign`() {
        val split = moneySplit(
            listOf(reading(1, "GEL", "1000.00"), reading(2, "GEL", "-250.00")),
            rates,
            "GEL",
        )

        assertEquals(BigDecimal("1000.00"), split.assets.amount)
        assertEquals(BigDecimal("-250.00"), split.liabilities.amount)
        assertEquals(BigDecimal("750.00"), split.total.amount)
    }

    private val rates = mapOf(
        "USD" to ExchangeRate("USD", BigDecimal("2.70"), observedAt = 1_000),
        "EUR" to ExchangeRate("EUR", BigDecimal("3.10"), observedAt = 1_000),
        "USDT" to ExchangeRate("USDT", BigDecimal("2.70"), observedAt = 1_000),
    )

    private fun reading(
        id: Long,
        currency: String,
        amount: String,
        role: FundRole = FundRole.AVAILABLE,
        product: BankProduct? = null,
        type: AccountType = AccountType.BANK,
    ) = AccountReading(
        AccountEntity(
            id = id,
            name = "Ledger $id",
            type = type,
            currency = currency,
            fundRole = role,
            bankProduct = product,
        ),
        BigDecimal(amount),
    )
}
