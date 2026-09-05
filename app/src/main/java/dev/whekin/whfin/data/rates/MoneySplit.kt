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

/** One ledger and the money on it, already out of its storage form and into a plain amount. */
internal data class AccountReading(
    val account: AccountEntity,
    val amount: BigDecimal,
)

/**
 * The money, cut the few ways this app talks about it — from one reading, in one currency.
 *
 * Home and Accounts are two pages of the same pager and both say the word "available"; they used to
 * mean different things by it, because one converted foreign balances and the other summed lari
 * only. One swipe changed the answer by the size of the foreign money, and neither number said the
 * other existed. Every screen that says "available" now reads [available] and every screen that says
 * how much there is reads [total], so the words can disagree with each other only if the arithmetic
 * does.
 *
 * The parts add up on purpose: [available] + [reserve] + [heldBack] + watch-only chains = [total].
 * That is what lets a screen explain a residual instead of leaving one.
 */
internal data class MoneySplit(
    val total: ConvertedTotal,
    /** Positive balances only. */
    val assets: ConvertedTotal,
    /** Negative balances, kept signed so a screen prints what it is given. */
    val liabilities: ConvertedTotal,
    val available: ConvertedTotal,
    val reserve: ConvertedTotal,
    /** Money the owner calls available that a bank term still holds: inside [total], outside [available]. */
    val heldBack: ConvertedTotal,
    /** [available] in the pivot currency, so a daily rate divides it without a second conversion. */
    val availablePivotMinor: Long?,
)

/**
 * Whether a ledger holds money this app can treat as spendable.
 *
 * The fund role is the person's own statement about their money and is trusted as such. Two facts
 * are not opinions and are applied regardless: a watch-only chain ledger is read, never spent, and a
 * term deposit can only be spent by breaking its term, which is a decision and not a purchase.
 */
internal fun isSpendable(account: AccountEntity): Boolean =
    account.type != AccountType.CRYPTO &&
        account.fundRole == FundRole.AVAILABLE &&
        account.bankProduct != BankProduct.TERM_DEPOSIT

/** Available money a term keeps out of reach — the only reason [MoneySplit.available] is not the whole role. */
private fun isHeldByTerm(account: AccountEntity): Boolean =
    account.type != AccountType.CRYPTO &&
        account.fundRole == FundRole.AVAILABLE &&
        account.bankProduct == BankProduct.TERM_DEPOSIT

/** A chain observation carries uint256 base units, which is why it arrives as text. */
internal fun chainAmount(baseUnits: String, decimals: Int): BigDecimal? =
    runCatching { BigDecimal(baseUnits).movePointLeft(decimals) }.getOrNull()

internal fun moneySplit(
    readings: List<AccountReading>,
    rates: Map<String, ExchangeRate>,
    display: String,
): MoneySplit {
    val spendable = readings.filter { isSpendable(it.account) }
    // Chain money is read, not held in a role, and Accounts gives it a section of its own; letting
    // it into either column would put an unspendable number under a word that promises spending.
    val onHand = readings.filterNot { it.account.type == AccountType.CRYPTO }
    return MoneySplit(
        total = convertReadings(readings, rates, display),
        assets = convertReadings(readings, rates, display) { it.coerceAtLeast(BigDecimal.ZERO) },
        liabilities = convertReadings(readings, rates, display) { it.coerceAtMost(BigDecimal.ZERO) },
        available = convertReadings(spendable, rates, display),
        reserve = convertReadings(onHand.filter { it.account.fundRole == FundRole.RESERVE }, rates, display),
        heldBack = convertReadings(onHand.filter { isHeldByTerm(it.account) }, rates, display),
        availablePivotMinor = convertReadings(spendable, rates, PIVOT_CURRENCY).amount
            ?.movePointRight(2)
            ?.toLong(),
    )
}

/** Sum of [readings] in one currency, keeping what it could not convert. */
internal fun convertReadings(
    readings: List<AccountReading>,
    rates: Map<String, ExchangeRate>,
    display: String,
    part: (BigDecimal) -> BigDecimal = { it },
): ConvertedTotal {
    val amounts = mutableMapOf<String, BigDecimal>()
    readings.forEach { reading ->
        val amount = part(reading.amount)
        val code = reading.account.currency.uppercase()
        amounts[code] = (amounts[code] ?: BigDecimal.ZERO).add(amount)
    }
    return MoneyConverter.convert(amounts, display, rates)
}

/**
 * One reading of the money for every screen that shows a headline.
 *
 * Fiat ledgers are the sum of their transactions; watch-only chain ledgers are their last
 * observation, and one that was never read contributes nothing rather than a zero.
 */
internal class MoneySplitSource(
    private val db: WhfinDatabase,
    private val preferences: UiPreferences,
) {

    fun observe(): Flow<MoneySplit> = combine(
        db.accountDao().observeActive(),
        db.transactionDao().observeAccountBalances(),
        db.cryptoDao().observeBalances(),
        db.exchangeRateDao().observeAll(),
        preferences.displayCurrency,
    ) { accounts, balances, chainBalances, rateRows, display ->
        val ledgerTotals = balances.associate { it.accountId to it.totalMinor }
        val chainTotals = chainBalances.associateBy { it.accountId }
        val readings = accounts.mapNotNull { account ->
            val amount = if (account.type == AccountType.CRYPTO) {
                val observation = chainTotals[account.id] ?: return@mapNotNull null
                chainAmount(observation.baseUnits, observation.decimals) ?: return@mapNotNull null
            } else {
                BigDecimal(ledgerTotals[account.id] ?: 0L).movePointLeft(2)
            }
            AccountReading(account, amount)
        }
        moneySplit(readings, rateRows.map(::toRate).associateBy { it.code }, display)
    }
}
