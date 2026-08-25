package dev.whekin.whfin.data.recurring

import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.isOpeningBalanceAnchor
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * One recorded expense, reduced to what recurrence detection needs.
 *
 * Amounts are in the pivot currency: a monthly obligation is compared against its own past, and a
 * row whose day has no quote yet cannot take part in that comparison without inventing a number.
 */
internal data class RecurringObservation(
    /** Stable identity of the payee — the merchant or the receiving account, never the spelling. */
    val key: String,
    val label: String,
    val day: LocalDate,
    /** Positive amount spent, in pivot minor units. */
    val amountMinor: Long,
)

/**
 * A payment that arrives every month whether or not anyone remembers it.
 *
 * Rent dominates a month and either already happened — making the month look expensive — or has not
 * happened yet, making the remaining days look affordable. Neither reading is true on its own, so the
 * obligation is named separately from what has been spent.
 */
internal data class RecurringCharge(
    val key: String,
    val label: String,
    /** The typical monthly amount: a median, so one unusual bill does not redefine the norm. */
    val typicalMinor: Long,
    /** Day of month the payment usually lands on. */
    val expectedDay: Int,
    val lastSeen: LocalDate,
)

/**
 * Turns ledger rows into observations, keeping only the person's own outgoing money.
 *
 * Transfers, debt movement and balance corrections are excluded for the same reason they are
 * excluded from a month's expenses: they move money the person already had.
 */
internal fun recurringObservations(
    transactions: List<TransactionEntity>,
    merchants: List<MerchantEntity>,
    zone: ZoneId,
): List<RecurringObservation> {
    val merchantById = merchants.associateBy { it.id }
    return transactions.mapNotNull { transaction ->
        if (transaction.isTransfer || transaction.transferGroupId != null) return@mapNotNull null
        if (transaction.isVoided || transaction.isOpeningBalanceAnchor()) return@mapNotNull null
        if (transaction.source == TxSource.ADJUSTMENT) return@mapNotNull null
        if (transaction.amountMinor >= 0L) return@mapNotNull null
        val pivotMinor = if (transaction.currency.equals(PIVOT, ignoreCase = true)) {
            transaction.amountMinor
        } else {
            transaction.gelValueMinor ?: return@mapNotNull null
        }
        val merchant = transaction.merchantId?.let(merchantById::get)
        val key = when {
            merchant != null -> "merchant:${merchant.id}"
            transaction.counterpartyIban != null -> "iban:${transaction.counterpartyIban}"
            else -> return@mapNotNull null
        }
        val label = merchant?.displayName
            ?: transaction.rawCounterparty?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        RecurringObservation(
            key = key,
            label = label,
            day = Instant.ofEpochMilli(transaction.occurredAt).atZone(zone).toLocalDate(),
            amountMinor = -pivotMinor,
        )
    }
}

/**
 * Finds the monthly obligations hiding in spending history.
 *
 * The test is deliberately strict, because a wrong recurrence would promise money that is not owed:
 * the payee must appear in most of the recent complete months, at most a couple of times in each —
 * a shop visited weekly is a habit, not a bill — and its monthly total must stay near its own median,
 * which is what separates rent and a subscription from a merchant that happens to be popular.
 */
internal fun detectRecurringCharges(
    observations: List<RecurringObservation>,
    today: LocalDate,
): List<RecurringCharge> {
    val completeMonths = (1..LOOKBACK_MONTHS).map { YearMonth.from(today).minusMonths(it.toLong()) }
    return observations
        .groupBy(RecurringObservation::key)
        .mapNotNull { (key, all) ->
            val recent = all.filter { YearMonth.from(it.day) in completeMonths }
            val byMonth = recent.groupBy { YearMonth.from(it.day) }
            if (byMonth.size < MIN_MONTHS) return@mapNotNull null
            if (byMonth.values.any { it.size > MAX_PER_MONTH }) return@mapNotNull null
            val totals = byMonth.values.map { month -> month.sumOf(RecurringObservation::amountMinor) }
            val typical = median(totals)
            if (typical <= 0L) return@mapNotNull null
            val steady = totals.all { total ->
                val drift = kotlin.math.abs(total - typical) * 100L / typical
                drift <= MAX_DRIFT_PERCENT
            }
            if (!steady) return@mapNotNull null
            RecurringCharge(
                key = key,
                // The latest spelling is the one the person just saw in their feed.
                label = all.maxBy { it.day }.label,
                typicalMinor = typical,
                expectedDay = median(recent.map { it.day.dayOfMonth.toLong() }).toInt(),
                lastSeen = all.maxOf { it.day },
            )
        }
        .sortedByDescending(RecurringCharge::typicalMinor)
}

/**
 * The obligations of the running month that have not been paid yet.
 *
 * A charge already recorded this month is finished business and says nothing about what is left. One
 * whose usual day has passed with nothing recorded still counts: either it is late or the statement
 * has not arrived, and both mean the money should still be treated as owed.
 */
internal fun recurringDue(
    observations: List<RecurringObservation>,
    today: LocalDate,
): List<RecurringCharge> {
    val month = YearMonth.from(today)
    val paidKeys = observations
        .filter { YearMonth.from(it.day) == month }
        .mapTo(mutableSetOf(), RecurringObservation::key)
    return detectRecurringCharges(observations, today).filterNot { it.key in paidKeys }
}

private fun median(values: List<Long>): Long {
    if (values.isEmpty()) return 0L
    val sorted = values.sorted()
    return sorted[sorted.lastIndex / 2]
}

private const val PIVOT = "GEL"
private const val LOOKBACK_MONTHS = 4
private const val MIN_MONTHS = 3
private const val MAX_PER_MONTH = 2
private const val MAX_DRIFT_PERCENT = 40L
