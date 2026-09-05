package dev.whekin.whfin.ui.accounts

import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.rates.AccountReading
import dev.whekin.whfin.data.rates.ExchangeRate
import dev.whekin.whfin.data.rates.MoneySplit
import dev.whekin.whfin.data.rates.chainAmount
import dev.whekin.whfin.data.rates.convertReadings
import dev.whekin.whfin.data.rates.moneySplit
import java.math.BigDecimal

/**
 * The money on one ledger, or nothing at all.
 *
 * A watch-only ledger that was never read has no balance rather than a zero one: printing `0.00` for
 * money that is plainly on the chain is a claim, and this app does not make it.
 */
internal fun AccountWithBalance.reading(): AccountReading? = when (account.type) {
    AccountType.CRYPTO -> onChain?.let { chainAmount(it.baseUnits, it.decimals) }
    else -> BigDecimal(balanceMinor).movePointLeft(2)
}?.let { AccountReading(account, it) }

internal fun accountReadings(rows: List<AccountWithBalance>): List<AccountReading> = rows.mapNotNull { it.reading() }

/** One source of money and what it holds, converted into the currency the screen is read in. */
internal data class AccountSourceShare(
    val name: String,
    val amount: BigDecimal,
)

/** A balance left in the currency it is actually held in. */
internal data class NativeCurrencyBalance(
    val currency: String,
    val amount: BigDecimal,
)

internal data class AccountOverviewData(
    val split: MoneySplit,
    val sources: List<AccountSourceShare>,
    val nativeCurrencies: List<NativeCurrencyBalance>,
)

/**
 * Everything the overview screen shows, from the same split the headline above it is cut from.
 *
 * This screen exists to explain the number on Accounts, so it has to start from that number. It used
 * to start from a different one — lari only, watch-only ledgers dropped — and the two screens then
 * disagreed by the size of the foreign and chain money without either of them saying so.
 */
internal fun accountOverviewData(
    rows: List<AccountWithBalance>,
    rates: Map<String, ExchangeRate>,
    display: String,
): AccountOverviewData {
    val readings = accountReadings(rows)
    val byAccount = readings.associateBy { it.account.id }
    val sources = rows
        .mapNotNull { row -> byAccount[row.account.id]?.let { row to it } }
        .filter { (_, reading) -> reading.amount.signum() > 0 }
        .groupBy { (row, _) -> row.groupName ?: row.account.name }
        .map { (name, entries) ->
            AccountSourceShare(
                name = name,
                amount = convertReadings(entries.map { it.second }, rates, display).amount ?: BigDecimal.ZERO,
            )
        }
        .filter { it.amount.signum() > 0 }
        .sortedByDescending { it.amount }
    val native = readings
        .filterNot { it.account.currency.equals(display, ignoreCase = true) }
        .groupBy { it.account.currency.uppercase() }
        .map { (currency, entries) ->
            NativeCurrencyBalance(currency, entries.fold(BigDecimal.ZERO) { sum, it -> sum.add(it.amount) })
        }
        .filter { it.amount.signum() != 0 }
        .sortedBy { it.currency }
    return AccountOverviewData(
        split = moneySplit(readings, rates, display),
        sources = sources,
        nativeCurrencies = native,
    )
}
