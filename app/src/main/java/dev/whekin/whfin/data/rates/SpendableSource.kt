package dev.whekin.whfin.data.rates

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.BankProduct
import dev.whekin.whfin.data.db.FundRole
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.preferences.UiPreferences
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Money that can be spent today, read in one chosen currency.
 *
 * Net worth answers what is owned; it never changes a decision on a Tuesday. This answers the
 * question the person actually has — how much is there to spend — and it is a different number on
 * purpose: everyday money is deliberately small in this app, because the rest sits in deposits.
 *
 * [pivotMinor] carries the same money in the pivot currency so it can be divided by a spending rate
 * without going through the display currency twice.
 */
internal data class SpendableMoney(
    val total: ConvertedTotal,
    val pivotMinor: Long?,
)

internal class SpendableSource(
    private val db: WhfinDatabase,
    private val preferences: UiPreferences,
) {

    fun observe(): Flow<SpendableMoney> = combine(
        db.accountDao().observeActive(),
        db.transactionDao().observeAccountBalances(),
        db.exchangeRateDao().observeAll(),
        preferences.displayCurrency,
    ) { accounts, balances, rateRows, display ->
        val ledgerTotals = balances.associate { it.accountId to it.totalMinor }
        val amounts = mutableMapOf<String, BigDecimal>()
        accounts.filter(::isSpendable).forEach { account ->
            val currency = account.currency.uppercase()
            val amount = BigDecimal(ledgerTotals[account.id] ?: 0L).movePointLeft(2)
            amounts[currency] = (amounts[currency] ?: BigDecimal.ZERO).add(amount)
        }
        val rates = rateRows.map(::toRate).associateBy { it.code }
        SpendableMoney(
            total = MoneyConverter.convert(amounts, display, rates),
            pivotMinor = MoneyConverter.convert(amounts, PIVOT_CURRENCY, rates).amount
                ?.movePointRight(2)
                ?.toLong(),
        )
    }
}

/**
 * Whether a ledger holds money this app can treat as spendable.
 *
 * The fund role is the person's own statement about their money and is trusted as such. Two facts
 * are not opinions and are applied regardless: a watch-only chain ledger is read, never spent, and a
 * term deposit can only be spent by breaking its term, which is a decision and not a purchase.
 */
private fun isSpendable(account: AccountEntity): Boolean =
    account.type != AccountType.CRYPTO &&
        account.fundRole == FundRole.AVAILABLE &&
        account.bankProduct != BankProduct.TERM_DEPOSIT
