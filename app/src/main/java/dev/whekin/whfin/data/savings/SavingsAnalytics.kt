package dev.whekin.whfin.data.savings

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.FundRole
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * The reserve snapshot and controlled saving flow for one calendar month.
 *
 * [reserveBalanceMinor] is the end-of-month balance of all reserve ledgers in the requested
 * currency. [paceMinor] is the amount of controlled saving completed in the month: a positive
 * value means that more money was moved into reserve than out of it, while a negative value means
 * that reserve was spent or released. Interest, income, opening anchors, and adjustments affect
 * the balance but intentionally do not satisfy the saving pace.
 */
data class SavingsMonth(
    val month: YearMonth,
    val reserveBalanceMinor: Long,
    val paceMinor: Long,
)

/** A rolling view of [SavingsMonth.paceMinor], retaining the month it describes. */
data class SavingsRollingAverage(
    val month: YearMonth,
    val averagePaceMinor: Long,
    val monthsIncluded: Int,
)

/** Pure, already-bucketed savings data ready for a view model or another presentation layer. */
data class SavingsAnalytics(
    val currency: String,
    val months: List<SavingsMonth>,
) {
    /** The latest requested end-of-month reserve balance, or zero for an empty range. */
    val endingReserveBalanceMinor: Long
        get() = months.lastOrNull()?.reserveBalanceMinor ?: 0L

    /** The rolling average of controlled pace over the current and preceding two buckets. */
    fun rollingThreeMonthAverage(): List<SavingsRollingAverage> =
        rollingThreeMonthAverage(months)
}

/**
 * Calculates reserve balance and controlled saving pace for [currency] between [fromMonth] and
 * [throughMonth], inclusive.
 *
 * The range is bucketed using [occurredAt] interpreted in [zoneId]. Transactions before
 * [fromMonth] are still applied to the first balance bucket, so a chart of a recent year starts
 * at the real reserve balance rather than at zero. Transactions after [throughMonth] are ignored.
 * Account fund roles are intentionally read from the current [AccountEntity] snapshot: a role
 * change is a present classification, not historical account metadata.
 */
fun calculateSavingsAnalytics(
    accounts: Collection<AccountEntity>,
    transactions: Collection<TransactionEntity>,
    currency: String,
    fromMonth: YearMonth,
    throughMonth: YearMonth,
    zoneId: ZoneId = ZoneId.systemDefault(),
): SavingsAnalytics {
    require(!fromMonth.isAfter(throughMonth)) {
        "fromMonth must not be after throughMonth"
    }

    val months = consecutiveMonths(fromMonth, throughMonth)
    val accountById = accounts.associateBy { it.id }
    val eligibleAccounts = accountById.values.filter { it.type != AccountType.CRYPTO }
    val eligibleAccountIds = eligibleAccounts.asSequence().map { it.id }.toSet()
    val reserveAccountIds = eligibleAccounts
        .asSequence()
        .filter { it.fundRole == FundRole.RESERVE && it.currency == currency }
        .map { it.id }
        .toSet()

    if (reserveAccountIds.isEmpty()) {
        return SavingsAnalytics(
            currency = currency,
            months = months.map { month -> SavingsMonth(month, 0L, 0L) },
        )
    }

    // Group classification must see all fiat currencies: a GEL available ledger can fund a USD
    // reserve leg in a conversion group. Only the reserve legs in the requested currency are
    // later summed into this analytics series.
    val activeForTransferClassification = transactions.asSequence()
        .filter { !it.isVoided && it.source != TxSource.CRYPTO }
        .filter { it.source != TxSource.ADJUSTMENT }
        .filter { it.accountId in eligibleAccountIds }
        .filter { it.transferGroupId != null }
        .toList()
    val groupsWithAvailableLedger = activeForTransferClassification
        .groupBy { it.transferGroupId!! }
        .mapValues { (_, groupTransactions) ->
            groupTransactions.any { it.amountMinor < 0L } &&
                groupTransactions.any { it.amountMinor > 0L } &&
                groupTransactions.any { transaction ->
                accountById[transaction.accountId]?.fundRole == FundRole.AVAILABLE
            }
        }

    val rows = transactions.asSequence()
        .filter { !it.isVoided && it.source != TxSource.CRYPTO }
        .filter { it.accountId in reserveAccountIds }
        .filter { it.currency == currency }
        .map { transaction ->
            val month = transaction.occurredAt
                .asYearMonth(zoneId)
            val paceMinor = controlledPaceContribution(
                transaction = transaction,
                hasAvailableLedgerInGroup = transaction.transferGroupId
                    ?.let(groupsWithAvailableLedger::get) == true,
            )
            SavingsRow(month = month, balanceMinor = transaction.amountMinor, paceMinor = paceMinor)
        }
        .toList()

    val balanceBeforeRange = rows
        .asSequence()
        .filter { it.month.isBefore(fromMonth) }
        .sumOf { it.balanceMinor }

    val balanceByMonth = rows
        .asSequence()
        .filter { it.month >= fromMonth && it.month <= throughMonth }
        .groupBy { it.month }
        .mapValues { (_, monthRows) -> monthRows.sumOf { it.balanceMinor } }
    val paceByMonth = rows
        .asSequence()
        .filter { it.month >= fromMonth && it.month <= throughMonth }
        .groupBy { it.month }
        .mapValues { (_, monthRows) -> monthRows.sumOf { it.paceMinor } }

    var runningBalance = balanceBeforeRange
    val buckets = months.map { month ->
        runningBalance += balanceByMonth[month] ?: 0L
        SavingsMonth(
            month = month,
            reserveBalanceMinor = runningBalance,
            paceMinor = paceByMonth[month] ?: 0L,
        )
    }

    return SavingsAnalytics(currency = currency, months = buckets)
}

/**
 * Returns a three-month trailing average for every supplied bucket.
 *
 * The first one or two buckets use the available history rather than returning null. Missing
 * months should be represented by zero-valued [SavingsMonth] buckets from
 * [calculateSavingsAnalytics], so they correctly participate in the average.
 */
fun rollingThreeMonthAverage(months: List<SavingsMonth>): List<SavingsRollingAverage> =
    months.indices.map { index ->
        val firstIndex = (index - 2).coerceAtLeast(0)
        val window = months.subList(firstIndex, index + 1)
        SavingsRollingAverage(
            month = months[index].month,
            averagePaceMinor = window.sumOf { it.paceMinor } / window.size,
            monthsIncluded = window.size,
        )
    }

private data class SavingsRow(
    val month: YearMonth,
    val balanceMinor: Long,
    val paceMinor: Long,
)

private fun controlledPaceContribution(
    transaction: TransactionEntity,
    hasAvailableLedgerInGroup: Boolean,
): Long = when {
    // Adjustments (including opening anchors) explain a balance; they are not saving behavior. This
    // stays ahead of the transfer branch so even a malformed/grouped adjustment cannot satisfy the
    // pace.
    transaction.source == TxSource.ADJUSTMENT -> 0L

    // A grouped movement is counted from the reserve side only. Summing reserve rows once per row
    // is equivalent to taking the group net and avoids double-counting multi-leg reserve groups.
    transaction.transferGroupId != null ->
        if (hasAvailableLedgerInGroup) transaction.amountMinor else 0L

    // Legacy one-leg transfer rows have no evidence of the other fund role, so they cannot prove a
    // controlled saving action. They still remain part of the reserve balance above.
    transaction.isTransfer -> 0L

    // A regular debit directly from reserve is money taken back out of the reserve. Positive
    // ordinary rows (salary, interest, cash deposit) affect balance only.
    transaction.amountMinor < 0L -> transaction.amountMinor

    else -> 0L
}

private fun consecutiveMonths(fromMonth: YearMonth, throughMonth: YearMonth): List<YearMonth> =
    buildList {
        var month = fromMonth
        while (true) {
            add(month)
            if (month == throughMonth) break
            month = month.plusMonths(1)
        }
    }

private fun Long.asYearMonth(zoneId: ZoneId): YearMonth {
    val localDate = Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
    return YearMonth.of(localDate.year, localDate.month)
}
