package dev.whekin.whfin.data.rates

import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.preferences.UiPreferences
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Everything the person owns, read in one chosen currency.
 *
 * Fiat ledgers are the sum of their transactions; watch-only chain ledgers are their last
 * observation, and one that was never read contributes nothing rather than a zero. The result keeps
 * track of what it could not convert so the headline never claims to be the whole picture.
 */
internal class NetWorthSource(
    private val db: WhfinDatabase,
    private val preferences: UiPreferences,
) {

    fun observe(): Flow<ConvertedTotal> = combine(
        db.accountDao().observeActive(),
        db.transactionDao().observeAccountBalances(),
        db.cryptoDao().observeBalances(),
        db.exchangeRateDao().observeAll(),
        preferences.displayCurrency,
    ) { accounts, balances, chainBalances, rateRows, display ->
        val ledgerTotals = balances.associate { it.accountId to it.totalMinor }
        val chainTotals = chainBalances.associateBy { it.accountId }
        val amounts = mutableMapOf<String, BigDecimal>()

        accounts.forEach { account ->
            val currency = account.currency.uppercase()
            val amount = if (account.type == AccountType.CRYPTO) {
                val observation = chainTotals[account.id] ?: return@forEach
                runCatching {
                    BigDecimal(observation.baseUnits).movePointLeft(observation.decimals)
                }.getOrNull() ?: return@forEach
            } else {
                BigDecimal(ledgerTotals[account.id] ?: 0L).movePointLeft(2)
            }
            amounts[currency] = (amounts[currency] ?: BigDecimal.ZERO).add(amount)
        }

        MoneyConverter.convert(amounts, display, rateRows.map(::toRate).associateBy { it.code })
    }
}
