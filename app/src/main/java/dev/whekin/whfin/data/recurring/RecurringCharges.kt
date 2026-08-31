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
    val transactionId: Long = 0,
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

/** One expected occurrence of a proven monthly charge inside a future cash horizon. */
internal data class RecurringOccurrence(
    val charge: RecurringCharge,
    /** The charge's usual calendar date; an overdue occurrence keeps its original date. */
    val dueDate: LocalDate,
    val amountMinor: Long = charge.typicalMinor,
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
    ownExpenseMinor: Map<Long, Long?>? = null,
): List<RecurringObservation> {
    val merchantById = merchants.associateBy { it.id }
    return transactions.mapNotNull { transaction ->
        if (transaction.isTransfer || transaction.transferGroupId != null) return@mapNotNull null
        if (transaction.isVoided || transaction.isOpeningBalanceAnchor()) return@mapNotNull null
        if (transaction.source == TxSource.ADJUSTMENT) return@mapNotNull null
        if (transaction.amountMinor >= 0L) return@mapNotNull null
        val expenseMinor = if (ownExpenseMinor != null) {
            ownExpenseMinor[transaction.id] ?: return@mapNotNull null
        } else if (transaction.currency.equals(PIVOT, ignoreCase = true)) {
            -transaction.amountMinor
        } else {
            -(transaction.gelValueMinor ?: return@mapNotNull null)
        }
        if (expenseMinor <= 0L) return@mapNotNull null
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
            amountMinor = expenseMinor,
            transactionId = transaction.id,
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
 *
 * The last test is the date. A bill lands on its day: rent on the third, a subscription on the day it
 * was taken out, a utility within the same handful of days each month. A shop is visited whenever
 * there is a reason to, and three visits that happen to be a month apart for similar sums are a
 * coincidence, not an obligation — a hobby shop billed the owner for a purchase they had not planned
 * to make until the rule stopped believing scattered dates.
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
            // The date a month's payment lands on, taken from its first payment: a bill that is
            // settled in two parts is dated by the day it fell due, not by the day it was finished.
            val landings = byMonth.values.map { month -> month.minOf(RecurringObservation::day).dayOfMonth }
            val expectedDay = median(landings.map(Int::toLong)).toInt()
            if (landings.any { dayDistance(it, expectedDay) > MAX_DAY_DRIFT }) return@mapNotNull null
            RecurringCharge(
                key = key,
                // The latest spelling is the one the person just saw in their feed.
                label = all.maxBy { it.day }.label,
                typicalMinor = typical,
                expectedDay = expectedDay,
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
): List<RecurringCharge> = recurringOccurrences(
    observations = observations,
    today = today,
    through = YearMonth.from(today).atEndOfMonth(),
).map { it.charge.copy(typicalMinor = it.amountMinor) }

/**
 * Schedules proven monthly charges between now and a cash-planning horizon.
 *
 * A sufficient payment in a calendar month settles that month's occurrence. An unpaid current
 * occurrence remains visible after its usual day, while later months are included only when their
 * expected date falls inside [through]. This lets Home look across a month boundary to payday
 * without turning a recurring rent payment into an everyday spending rate.
 */
internal fun recurringOccurrences(
    observations: List<RecurringObservation>,
    today: LocalDate,
    through: LocalDate,
): List<RecurringOccurrence> {
    if (through < today) return emptyList()
    val firstMonth = YearMonth.from(today)
    val lastMonth = YearMonth.from(through)
    val paidByMonth = observations.filter { it.day <= today }
        .groupBy { YearMonth.from(it.day) }
        .mapValues { (_, rows) ->
            rows.groupBy(RecurringObservation::key)
                .mapValues { (_, payments) -> payments.sumOf(RecurringObservation::amountMinor) }
        }
    val charges = detectRecurringCharges(observations, today)
    return generateSequence(firstMonth) { month -> month.plusMonths(1) }
        .takeWhile { it <= lastMonth }
        .flatMap { month ->
            val paidAmounts = paidByMonth[month].orEmpty()
            charges.asSequence().mapNotNull { charge ->
                val paid = paidAmounts[charge.key] ?: 0L
                // The detector already allows 40% monthly variation. A token/partial payment is
                // not evidence that a whole bill was settled; reserve the remaining typical sum.
                if (paid >= charge.typicalMinor - charge.typicalMinor * MAX_DRIFT_PERCENT / 100L) {
                    return@mapNotNull null
                }
                val dueDate = month.atDay(charge.expectedDay.coerceAtMost(month.lengthOfMonth()))
                if (dueDate > through) null else RecurringOccurrence(charge, dueDate, charge.typicalMinor - paid)
            }
        }
        .sortedWith(compareBy(RecurringOccurrence::dueDate).thenByDescending { it.charge.typicalMinor })
        .toList()
}

private fun median(values: List<Long>): Long {
    if (values.isEmpty()) return 0L
    val sorted = values.sorted()
    return sorted[sorted.lastIndex / 2]
}

/**
 * How far apart two days of the month are, counting the turn of the month as a single step: a bill
 * on the 31st and one on the 1st are neighbours, not thirty days apart.
 */
private fun dayDistance(left: Int, right: Int): Int {
    val direct = kotlin.math.abs(left - right)
    return minOf(direct, DAYS_IN_MONTH - direct)
}

private const val PIVOT = "GEL"
private const val LOOKBACK_MONTHS = 4
private const val MIN_MONTHS = 3
private const val MAX_PER_MONTH = 2
private const val MAX_DRIFT_PERCENT = 40L
private const val DAYS_IN_MONTH = 31
/** A window of about nine days: wide enough for a bill that waits for a weekday, narrow for a shop. */
private const val MAX_DAY_DRIFT = 4
