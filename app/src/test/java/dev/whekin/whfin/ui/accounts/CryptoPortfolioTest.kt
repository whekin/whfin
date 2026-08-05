package dev.whekin.whfin.ui.accounts

import dev.whekin.whfin.data.crypto.CryptoNetwork
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.rates.ExchangeRate
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading several wallets as one portfolio: one pile per ticker, an unread address that is not a
 * zero, and a subtotal that only claims what it could actually price.
 */
class CryptoPortfolioTest {

    private var nextId = 1L

    private fun holding(
        symbol: String,
        network: CryptoNetwork,
        address: String,
        walletName: String,
        baseUnits: String?,
        decimals: Int = 6,
        observedAt: Long = 1_000L,
    ) = AccountWithBalance(
        account = AccountEntity(
            id = nextId++,
            name = walletName,
            type = AccountType.CRYPTO,
            currency = symbol,
            walletAddressId = 1,
            cryptoAssetId = 1,
        ),
        balanceMinor = 0,
        cardMasks = emptyList(),
        address = address,
        chainId = network.chainId,
        groupName = walletName,
        onChain = baseUnits?.let { OnChainBalance(it, decimals, observedAt) },
    )

    private val rates = mapOf(
        "USDT" to ExchangeRate("USDT", BigDecimal("2.70"), observedAt = 500L),
        "TRX" to ExchangeRate("TRX", BigDecimal("0.66"), observedAt = 500L),
        "USD" to ExchangeRate("USD", BigDecimal("2.70"), observedAt = 500L),
    )

    @Test
    fun theSameTickerInTwoWalletsIsOnePile() {
        val portfolio = buildCryptoPortfolio(
            listOf(
                holding("USDT", CryptoNetwork.TRON, TRON, "Tron daily", "1200000000"),
                holding("USDT", CryptoNetwork.ETHEREUM, EVM, "Cold", "634260000"),
            ),
            rates,
            "GEL",
        )

        val usdt = portfolio.assets.single()
        assertEquals("USDT", usdt.symbol)
        assertEquals(BigInteger("1834260000"), usdt.baseUnits)
        assertEquals(2, usdt.holdings.size)
        assertEquals(2, usdt.walletCount)
        // 1834.26 USDT × 2.70 GEL
        assertEquals(BigDecimal("4952.50"), usdt.converted)
        assertEquals(BigDecimal("4952.50"), portfolio.total?.amount)
        assertTrue(portfolio.total?.missing.orEmpty().isEmpty())
    }

    @Test
    fun eachTickerKeepsItsOwnRowAndTheBiggestPileLeads() {
        val portfolio = buildCryptoPortfolio(
            listOf(
                holding("TRX", CryptoNetwork.TRON, TRON, "Tron daily", "512400000"),
                holding("USDT", CryptoNetwork.TRON, TRON, "Tron daily", "1200000000"),
            ),
            rates,
            "GEL",
        )

        assertEquals(listOf("USDT", "TRX"), portfolio.assets.map { it.symbol })
        // 1200 × 2.70 + 512.4 × 0.66
        assertEquals(BigDecimal("3578.18"), portfolio.total?.amount)
    }

    @Test
    fun anAddressNobodyHasReadIsNotAZero() {
        val portfolio = buildCryptoPortfolio(
            listOf(holding("USDT", CryptoNetwork.TRON, TRON, "New wallet", null)),
            rates,
            "GEL",
        )

        val usdt = portfolio.assets.single()
        assertNull(usdt.baseUnits)
        assertNull(usdt.converted)
        assertEquals(1, usdt.unreadCount)
        assertNull(portfolio.lastObservedAt)
        // Nothing readable means nothing to total, not a confident zero.
        assertNull(portfolio.total)
    }

    @Test
    fun anUnpricedAssetIsNamedInsteadOfSilentlyDropped() {
        val portfolio = buildCryptoPortfolio(
            listOf(
                holding("USDT", CryptoNetwork.TRON, TRON, "Tron daily", "1000000"),
                holding("TRX", CryptoNetwork.TRON, TRON, "Tron daily", "1000000"),
            ),
            rates - "TRX",
            "GEL",
        )

        assertEquals(setOf("TRX"), portfolio.total?.missing)
        assertEquals(BigDecimal("2.70"), portfolio.total?.amount)
        assertNull(portfolio.assets.single { it.symbol == "TRX" }.converted)
    }

    @Test
    fun tickersThatDisagreeAboutDecimalsAreNeverSummed() {
        val portfolio = buildCryptoPortfolio(
            listOf(
                holding("USDT", CryptoNetwork.TRON, TRON, "Tron", "1000000", decimals = 6),
                holding("USDT", CryptoNetwork.ETHEREUM, EVM, "Odd chain", "1000000000000000000", decimals = 18),
            ),
            rates,
            "GEL",
        )

        assertEquals(2, portfolio.assets.size)
        assertEquals(listOf(6, 18), portfolio.assets.map { it.decimals }.sorted())
        // Both are one USDT, so the pile is two — not a trillion.
        assertEquals(BigDecimal("5.40"), portfolio.total?.amount)
    }

    @Test
    fun theSubtotalFollowsTheChosenDisplayCurrency() {
        val portfolio = buildCryptoPortfolio(
            listOf(holding("USDT", CryptoNetwork.TRON, TRON, "Tron", "100000000")),
            rates,
            "usd",
        )

        assertEquals("USD", portfolio.displayCurrency)
        assertEquals(BigDecimal("100.00"), portfolio.total?.amount)
    }

    @Test
    fun fiatLedgersAreNotPartOfTheChainReading() {
        val cash = AccountWithBalance(
            AccountEntity(id = 99, name = "Cash", type = AccountType.CASH, currency = "GEL"),
            50_000,
            emptyList(),
        )

        val portfolio = buildCryptoPortfolio(listOf(cash), rates, "GEL")

        assertTrue(portfolio.isEmpty)
        assertNull(portfolio.total)
    }

    private companion object {
        const val TRON = "T9yD14Nj9j7xAB4dbGeiX9h8unkKHxuWwb"
        const val EVM = "0x00112233445566778899aabbccddeeff00112233"
    }
}
