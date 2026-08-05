package dev.whekin.whfin.ui.accounts

import dev.whekin.whfin.data.crypto.CryptoNetwork
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.rates.ConvertedTotal
import dev.whekin.whfin.data.rates.ExchangeRate
import dev.whekin.whfin.data.rates.MoneyConverter
import java.math.BigDecimal
import java.math.BigInteger

/** One asset ledger of one address: the smallest thing a chain actually answers about. */
data class CryptoHolding(
    val accountId: Long,
    val walletName: String,
    val networkName: String?,
    val address: String?,
    val symbol: String,
    val decimals: Int,
    /** Exact base units of the last observation; null means never read, which is not zero. */
    val baseUnits: BigInteger?,
    val observedAt: Long?,
    val converted: BigDecimal?,
)

/**
 * The same ticker across every wallet.
 *
 * USDT-ERC20 and USDT-TRC20 stay separate balances in storage; a person still owns one pile of USDT,
 * so the reading is summed here and the chains show up in [holdings]. Two chains that disagree about
 * decimals are never summed — that would silently move the point.
 */
data class CryptoAssetGroup(
    val symbol: String,
    val decimals: Int,
    val baseUnits: BigInteger?,
    val converted: BigDecimal?,
    val holdings: List<CryptoHolding>,
) {
    val unreadCount: Int get() = holdings.count { it.baseUnits == null }
    val walletCount: Int get() = holdings.map { it.address ?: it.walletName }.distinct().size
}

data class CryptoPortfolio(
    val assets: List<CryptoAssetGroup>,
    /** Everything on-chain, in the currency the person chose to read totals in. */
    val total: ConvertedTotal?,
    val lastObservedAt: Long?,
    /** Currency every [CryptoAssetGroup.converted] and [CryptoHolding.converted] is stated in. */
    val displayCurrency: String,
) {
    val isEmpty: Boolean get() = assets.isEmpty()
    val unreadCount: Int get() = assets.sumOf { it.unreadCount }
}

/**
 * Builds the crypto reading out of ledger rows and the quotes at hand.
 *
 * Pure on purpose: rounding, aggregation, and "never read is not zero" are the parts worth pinning
 * down in a test, and none of them need a database or a network.
 */
fun buildCryptoPortfolio(
    accounts: List<AccountWithBalance>,
    rates: Map<String, ExchangeRate>,
    displayCurrency: String,
): CryptoPortfolio {
    val holdings = accounts.filter { it.account.type == AccountType.CRYPTO }.map { row ->
        val observation = row.onChain
        val baseUnits = observation?.baseUnits?.let { runCatching { BigInteger(it) }.getOrNull() }
        val decimals = observation?.decimals ?: 0
        CryptoHolding(
            accountId = row.account.id,
            walletName = row.groupName ?: row.account.name,
            networkName = row.chainId?.let { CryptoNetwork.byChainId(it)?.displayName },
            address = row.address,
            symbol = row.account.currency.uppercase(),
            decimals = decimals,
            baseUnits = baseUnits,
            observedAt = observation?.observedAt,
            converted = baseUnits?.let { convert(it, decimals, row.account.currency, rates, displayCurrency) },
        )
    }
    val display = displayCurrency.uppercase()
    if (holdings.isEmpty()) return CryptoPortfolio(emptyList(), null, null, display)

    val amounts = mutableMapOf<String, BigDecimal>()
    val assets = holdings.groupBy { it.symbol }.flatMap { (symbol, rows) ->
        // Decimals can only differ when two chains disagree about the same ticker. Then each width
        // stays its own row: summing them would move the point on somebody's balance.
        val widths = rows.filter { it.baseUnits != null }.map { it.decimals }.distinct()
        val buckets = when {
            widths.size <= 1 -> listOf((widths.firstOrNull() ?: 0) to rows)
            else -> widths.sorted().mapIndexed { index, width ->
                val read = rows.filter { it.baseUnits != null && it.decimals == width }
                width to if (index == 0) read + rows.filter { it.baseUnits == null } else read
            }
        }
        buckets.map { (decimals, bucketRows) ->
            val sum = bucketRows.mapNotNull { it.baseUnits }
                .takeIf { it.isNotEmpty() }
                ?.fold(BigInteger.ZERO, BigInteger::add)
            if (sum != null) {
                val scaled = BigDecimal(sum).movePointLeft(decimals)
                amounts[symbol] = (amounts[symbol] ?: BigDecimal.ZERO).add(scaled)
            }
            CryptoAssetGroup(
                symbol = symbol,
                decimals = decimals,
                baseUnits = sum,
                converted = sum?.let { convert(it, decimals, symbol, rates, displayCurrency) },
                holdings = bucketRows.sortedWith(
                    compareByDescending<CryptoHolding> { it.baseUnits ?: BigInteger.ZERO }
                        .thenBy { it.walletName },
                ),
            )
        }
    }.sortedWith(
        compareByDescending<CryptoAssetGroup> { it.converted ?: BigDecimal.ZERO }.thenBy { it.symbol },
    )

    val total = MoneyConverter.convert(amounts, display, rates).takeIf { amounts.isNotEmpty() }
    return CryptoPortfolio(
        assets = assets,
        total = total,
        lastObservedAt = holdings.mapNotNull { it.observedAt }.maxOrNull(),
        displayCurrency = display,
    )
}

private fun convert(
    baseUnits: BigInteger,
    decimals: Int,
    symbol: String,
    rates: Map<String, ExchangeRate>,
    displayCurrency: String,
): BigDecimal? = MoneyConverter.convert(
    mapOf(symbol.uppercase() to BigDecimal(baseUnits).movePointLeft(decimals)),
    displayCurrency,
    rates,
).takeIf { it.missing.isEmpty() }?.amount
