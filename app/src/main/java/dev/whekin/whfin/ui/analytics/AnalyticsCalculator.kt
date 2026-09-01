package dev.whekin.whfin.ui.analytics

import dev.whekin.whfin.data.categorization.CategoryTree
import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.isOpeningBalanceAnchor
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

internal sealed interface AnalyticsTrendFilter {
    data object All : AnalyticsTrendFilter
    data class Category(val categoryId: Long?) : AnalyticsTrendFilter
}

internal data class AnalyticsMonthValue(
    val month: YearMonth,
    val expenseMinor: Long,
)

internal data class AnalyticsCategoryValue(
    val categoryId: Long?,
    val name: String?,
    val icon: String?,
    val color: Int?,
    val expenseMinor: Long,
    val averageExpenseMinor: Long = 0L,
)

internal data class AnalyticsCurrencyValue(
    val currency: String,
    val expenseMinor: Long,
)

/**
 * Who the money inside the selected scope went to, and how many payments made that up.
 *
 * A category total hides two different facts that look identical from above: one large purchase and
 * a habit of sixty small ones. Neither is a property of a transaction, so no ordering of the ledger
 * can show them — they are properties of the counterparty. The row therefore carries both the sum
 * and the count, and the screen decides which of the two orders the list.
 *
 * A row the ledger never named keeps [merchantId] null rather than being dropped: forty payments
 * nobody can attribute is itself the answer, and hiding them would stop these rows adding up to the
 * total printed above them.
 */
internal data class AnalyticsMerchantValue(
    val merchantId: Long?,
    val name: String?,
    val expenseMinor: Long,
    val transactionCount: Int,
)

internal data class AnalyticsPace(
    val daysElapsed: Int,
    val daysTotal: Int,
    val projectedExpenseMinor: Long,
    val previousPeriodExpenseMinor: Long,
)

internal data class AnalyticsCategoryChange(
    val categoryId: Long?,
    val name: String?,
    val icon: String?,
    val color: Int?,
    val expenseMinor: Long,
    val previousExpenseMinor: Long,
    val projectedExpenseMinor: Long? = null,
) {
    val deltaMinor: Long get() = expenseMinor - previousExpenseMinor
}

internal data class AnalyticsData(
    val period: AnalyticsPeriod,
    val incomeMinor: Long,
    val expenseMinor: Long,
    /** Categories of the selected period, each with its average over the preceding periods. */
    val categoryValues: List<AnalyticsCategoryValue>,
    /** Counterparties of the selected period, scoped by the same filter the trend chart is showing. */
    val merchantValues: List<AnalyticsMerchantValue> = emptyList(),
    val spendingAverageMinor: Long = 0L,
    val trendFilter: AnalyticsTrendFilter,
    val trendFilterName: String?,
    val trendValues: List<AnalyticsMonthValue>,
    val previousTrendExpenseMinor: Long,
    val unaccountedNetMinor: Long,
    val otherCurrencyExpenses: List<AnalyticsCurrencyValue>,
    val pendingCount: Int,
    val hasAnyTransactions: Boolean,
    val pace: AnalyticsPace? = null,
    val categoryChanges: List<AnalyticsCategoryChange> = emptyList(),
    /** Currencies of the selected period whose day has no quote yet, so they are left out of totals. */
    val unvaluedCurrencies: Set<String> = emptySet(),
) {
    val deltaMinor: Long get() = incomeMinor - expenseMinor
    val selectedMonth: YearMonth get() = period.month
}

private data class AnalyticsSlice(
    val transactionId: Long,
    val month: YearMonth,
    val currency: String,
    val amountMinor: Long,
    /** Value in GEL booked at the rate of this row's own day; null while the day is unpriced. */
    val gelMinor: Long?,
    /** Who was paid, when the row was ever given a name. */
    val merchantId: Long?,
    val categoryId: Long?,
    /**
     * The category this row is *reported* under: its parent when it has one.
     *
     * Kept beside [categoryId] rather than replacing it, because the two answer different questions.
     * Totals and shares are asked of the group, so that a hobby split into parts, service and lifts
     * still reads as one line. Drilling into a row is asked of the leaf the user actually chose.
     */
    val groupId: Long?,
    val unaccounted: Boolean,
    val pending: Boolean,
)

private data class Funding(
    val amountMinor: Long,
    val currency: String,
)

private fun analyticsSlices(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    allocations: List<TransactionAllocationEntity>,
    zoneId: ZoneId,
): List<AnalyticsSlice> {
    val categoryById = categories.associateBy { it.id }
    val tree = CategoryTree(categories)
    val allocationsByTransaction = allocations.groupBy { it.transactionId }
    val fundingByPurchase = findConversionFunding(transactions.filterNot { it.isVoided }, zoneId)
    return transactions
        .asSequence()
        .filterNot { it.isVoided || it.isOpeningBalanceAnchor() || it.isTransfer || it.transferGroupId != null }
        .flatMap { transaction ->
            val transactionAllocations = allocationsByTransaction[transaction.id].orEmpty()
            val includedParts = if (transactionAllocations.isEmpty()) {
                listOf(transaction.amountMinor to transaction.categoryId)
            } else {
                transactionAllocations
                    .filterNot { it.purpose == AllocationPurpose.LOAN || it.purpose == AllocationPurpose.REPAYMENT }
                    .map { it.amountMinor to (it.categoryId ?: transaction.categoryId) }
            }
            val funding = fundingByPurchase[transaction.id]
            val parts = if (funding != null && transaction.amountMinor < 0L && includedParts.isNotEmpty()) {
                val ownFundingMinor = BigDecimal(funding.amountMinor)
                    .multiply(BigDecimal(includedParts.sumOf { it.first }))
                    .divide(BigDecimal(transaction.amountMinor), 0, RoundingMode.HALF_UP).toLong()
                scaleExpenseParts(includedParts, ownFundingMinor).map { (amount, categoryId) ->
                    Triple(amount, categoryId, funding.currency)
                }
            } else {
                includedParts.map { (amount, categoryId) -> Triple(amount, categoryId, transaction.currency) }
            }
            val month = Instant.ofEpochMilli(transaction.occurredAt).atZone(zoneId).let {
                YearMonth.of(it.year, it.month)
            }
            // A split shares the booked value in the same proportion as the money.
            val gelForPart: (Long) -> Long? = when {
                transaction.currency == BASE_CURRENCY -> { part -> part }
                transaction.gelValueMinor == null || transaction.amountMinor == 0L -> { _ -> null }
                else -> { part ->
                    BigDecimal(part)
                        .multiply(BigDecimal(transaction.gelValueMinor))
                        .divide(BigDecimal(transaction.amountMinor), 0, RoundingMode.HALF_UP)
                        .toLong()
                }
            }
            parts.asSequence().map { (amount, categoryId, currency) ->
                AnalyticsSlice(
                    transactionId = transaction.id,
                    month = month,
                    currency = currency,
                    amountMinor = amount,
                    // The funded path already restated the purchase in the lari the bank charged.
                    gelMinor = if (currency == BASE_CURRENCY) amount else gelForPart(amount),
                    merchantId = transaction.merchantId,
                    categoryId = categoryId,
                    groupId = tree.rollupId(categoryId),
                    unaccounted = (transaction.source == TxSource.ADJUSTMENT &&
                        !transaction.isOpeningBalanceAnchor()) ||
                        categoryId?.let(categoryById::get)?.isSystem == true,
                    pending = transaction.status == TxStatus.PENDING,
                )
            }
        }
        .toList()
}

/**
 * Same own-expense valuation as Statistics, including splits and conversion-funded purchases.
 * Null means an expense has not been priced, not that it cost nothing.
 */
internal fun ownExpenseAmounts(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    allocations: List<TransactionAllocationEntity>,
    zoneId: ZoneId,
): Map<Long, Long?> {
    val priced = analyticsSlices(transactions, categories, allocations, zoneId)
        .filter { !it.unaccounted && it.amountMinor < 0L }
        .groupBy(AnalyticsSlice::transactionId)
        .mapValues { (_, slices) ->
            if (slices.any { it.gelMinor == null }) null else runCatching {
                Math.negateExact(slices.fold(0L) { sum, slice -> Math.addExact(sum, slice.gelMinor!!) })
            }.getOrNull()
        }
    // FX SMS stores a positive original amount while its signed ledger delta can still be zero
    // awaiting settlement. Missing valuation is never evidence of a free purchase.
    val awaitingSettlement = transactions.filter {
        !it.isVoided && !it.isTransfer && it.transferGroupId == null && it.source == TxSource.SMS &&
            it.amountMinor == 0L && (it.origAmountMinor ?: 0L) != 0L
    }.associate { it.id to null }
    return priced + awaitingSettlement
}

internal fun calculateAnalytics(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    allocations: List<TransactionAllocationEntity>,
    period: AnalyticsPeriod,
    trendFilter: AnalyticsTrendFilter,
    zoneId: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zoneId),
    merchants: List<MerchantEntity> = emptyList(),
): AnalyticsData {
    val categoryById = categories.associateBy { it.id }
    val slices = analyticsSlices(transactions, categories, allocations, zoneId)

    // Anything with a booked lari value counts, whatever currency it was spent in.
    val baseSlices = slices.filter { it.gelMinor != null && !it.unaccounted }
    val unvaluedCurrencies = slices
        .filter { it.gelMinor == null && !it.unaccounted && period.contains(it.month) }
        .map { it.currency }
        .toSortedSet()
    val selectedBase = baseSlices.filter { period.contains(it.month) }
    val income = selectedBase.sumOf { it.gelMinor!!.coerceAtLeast(0L) }
    val expenses = -selectedBase.sumOf { it.gelMinor!!.coerceAtMost(0L) }
    val previousPeriod = period.previous()
    val previousBase = baseSlices.filter { previousPeriod.contains(it.month) }
    val previousExpenses = -previousBase.sumOf { it.gelMinor!!.coerceAtMost(0L) }
    val comparisonPeriods = (1..period.comparisonPeriods).map(period::shiftedBack)
    val spendingAverage = comparisonPeriods.sumOf { comparison ->
        -baseSlices
            .filter { comparison.contains(it.month) }
            .sumOf { it.gelMinor!!.coerceAtMost(0L) }
    } / comparisonPeriods.size
    val pace = if (period.isCurrent(today)) {
        val daysElapsed = period.daysElapsed(today)
        val daysTotal = period.daysTotal(today)
        val projection = projectCurrentExpenses(
            selectedBase.filter { it.gelMinor!! < 0L },
            daysElapsed,
            daysTotal,
        )
        AnalyticsPace(
            daysElapsed = daysElapsed,
            daysTotal = daysTotal,
            projectedExpenseMinor = projection,
            previousPeriodExpenseMinor = previousExpenses,
        )
    } else {
        null
    }
    val currentCategoryExpenses = selectedBase
        .filter { it.gelMinor!! < 0L }
        .groupBy { it.groupId }
        .mapValues { (_, values) -> -values.sumOf { it.gelMinor!! } }
    val previousCategoryExpenses = previousBase
        .filter { it.gelMinor!! < 0L }
        .groupBy { it.groupId }
        .mapValues { (_, values) -> -values.sumOf { it.gelMinor!! } }
    val averageCategoryExpenses = baseSlices
        .filter { slice -> comparisonPeriods.any { it.contains(slice.month) } && slice.gelMinor!! < 0L }
        .groupBy { it.groupId }
        .mapValues { (_, values) -> -values.sumOf { it.gelMinor!! } / comparisonPeriods.size }
    // One list of categories, one window: the selected period, each row carrying its own baseline.
    // Statistics used to hold a second list over a rolling 1/3/6/12-month window, so the same screen
    // answered two different questions of time without saying which was which.
    val categoryValues = currentCategoryExpenses
        .map { (categoryId, expenseMinor) ->
            val category = categoryId?.let(categoryById::get)
            AnalyticsCategoryValue(
                categoryId = categoryId,
                name = category?.name,
                icon = category?.icon,
                color = category?.color,
                expenseMinor = expenseMinor,
                averageExpenseMinor = averageCategoryExpenses[categoryId] ?: 0L,
            )
        }
        .sortedByDescending { it.expenseMinor }
    val categoryChanges = currentCategoryExpenses
        .map { (categoryId, expenseMinor) ->
            val category = categoryId?.let(categoryById::get)
            AnalyticsCategoryChange(
                categoryId = categoryId,
                name = category?.name,
                icon = category?.icon,
                color = category?.color,
                expenseMinor = expenseMinor,
                previousExpenseMinor = previousCategoryExpenses[categoryId] ?: 0L,
                projectedExpenseMinor = pace?.let {
                    projectCurrentExpenses(
                        selectedBase.filter { slice ->
                            slice.gelMinor!! < 0L && slice.groupId == categoryId
                        },
                        it.daysElapsed,
                        it.daysTotal,
                    )
                },
            )
        }
        .filter { it.deltaMinor != 0L }
        .sortedByDescending { abs(it.deltaMinor) }
        .take(3)

    val matchesTrendFilter: (AnalyticsSlice) -> Boolean = { slice ->
        when (trendFilter) {
            AnalyticsTrendFilter.All -> true
            // Selecting a group follows its children in: a hobby's trend is the hobby, not one
            // of the buckets it was split into.
            is AnalyticsTrendFilter.Category ->
                slice.groupId == trendFilter.categoryId || slice.categoryId == trendFilter.categoryId
        }
    }
    // Scoped by the same filter the trend chart is showing, so the two blocks can never disagree
    // about which spending they are describing, and valued by the same rule as the category totals,
    // so these rows add up to the number printed above them.
    val merchantById = merchants.associateBy { it.id }
    val merchantValues = selectedBase
        .filter { it.gelMinor!! < 0L && matchesTrendFilter(it) }
        .groupBy(AnalyticsSlice::merchantId)
        .map { (merchantId, values) ->
            AnalyticsMerchantValue(
                merchantId = merchantId,
                name = merchantId?.let(merchantById::get)?.displayName,
                expenseMinor = -values.sumOf { it.gelMinor!! },
                // A split is several slices of one payment, and one payment is what was repeated.
                transactionCount = values.distinctBy(AnalyticsSlice::transactionId).size,
            )
        }
        .sortedByDescending { it.expenseMinor }

    // A fixed calendar year lets all twelve bars stay visible and makes 2024/2025 comparisons
    // spatially stable: January never changes position merely because another month was selected.
    val trendMonths = (1..12).map { YearMonth.of(period.year, it) }
    val trendValues = trendMonths.map { month ->
        val expense = -baseSlices
            .filter { it.month == month && it.gelMinor!! < 0L && matchesTrendFilter(it) }
            .sumOf { it.gelMinor!! }
        AnalyticsMonthValue(month, expense)
    }
    val previousTrendExpense = -baseSlices
        .filter { previousPeriod.contains(it.month) && it.gelMinor!! < 0L && matchesTrendFilter(it) }
        .sumOf { it.gelMinor!! }

    val selectedUnaccounted = slices.filter { period.contains(it.month) && it.unaccounted }
    // Only what could not be valued stays a native amount; the rest is already in the totals above.
    val otherCurrencies = slices
        .filter { period.contains(it.month) && it.gelMinor == null && !it.unaccounted && it.amountMinor < 0L }
        .groupBy { it.currency }
        .map { (currency, values) -> AnalyticsCurrencyValue(currency, -values.sumOf { it.amountMinor }) }
        .sortedBy { it.currency }

    return AnalyticsData(
        period = period,
        incomeMinor = income,
        expenseMinor = expenses,
        categoryValues = categoryValues,
        merchantValues = merchantValues,
        spendingAverageMinor = spendingAverage,
        trendFilter = trendFilter,
        trendFilterName = (trendFilter as? AnalyticsTrendFilter.Category)
            ?.categoryId
            ?.let(categoryById::get)
            ?.name,
        trendValues = trendValues,
        previousTrendExpenseMinor = previousTrendExpense,
        unaccountedNetMinor = selectedUnaccounted.sumOf { it.gelMinor ?: 0L },
        otherCurrencyExpenses = otherCurrencies,
        unvaluedCurrencies = unvaluedCurrencies,
        pendingCount = slices.filter { period.contains(it.month) && it.pending }
            .map { it.transactionId }
            .distinct()
            .size,
        hasAnyTransactions = slices.isNotEmpty(),
        pace = pace,
        categoryChanges = categoryChanges,
    )
}

/**
 * Projects ordinary spending while counting an already-booked large purchase only once.
 *
 * A straight month/day multiplier effectively buys a phone, pays annual insurance, or books a trip
 * again on every remaining day. With enough observations to establish a median transaction, amounts
 * far above it are treated as already-realised one-offs; only the ordinary part continues at the
 * elapsed-month rate. Small samples keep the transparent linear projection instead of inventing an
 * outlier threshold from too little evidence.
 */
private fun projectCurrentExpenses(
    expenseSlices: List<AnalyticsSlice>,
    daysElapsed: Int,
    daysInMonth: Int,
): Long {
    val byTransaction = expenseSlices
        .groupBy(AnalyticsSlice::transactionId)
        .values
        .map { slices -> -slices.sumOf { it.gelMinor!! } }
        .filter { it > 0L }
    val actual = byTransaction.sum()
    if (byTransaction.size < MIN_EXPENSES_FOR_ROBUST_PROJECTION) {
        return actual * daysInMonth / daysElapsed.coerceAtLeast(1)
    }

    val ordinary = ordinaryExpenseTotal(byTransaction)
    val oneOffs = actual - ordinary
    return oneOffs + ordinary * daysInMonth / daysElapsed.coerceAtLeast(1)
}

/** No assertion about a spending habit from fewer than five non-recurring observations. */
internal fun ordinaryExpenseDaily(amounts: List<Long>, daysElapsed: Int): Long? {
    if (daysElapsed < 5 || amounts.size < MIN_EXPENSES_FOR_ROBUST_PROJECTION) return null
    return ordinaryExpenseTotal(amounts) / daysElapsed
}

private fun ordinaryExpenseTotal(amounts: List<Long>): Long {
    val sorted = amounts.sorted()
    val median = sorted[sorted.lastIndex / 2]
    val threshold = maxOf(
        median.coerceAtMost(Long.MAX_VALUE / ONE_OFF_MEDIAN_MULTIPLIER) * ONE_OFF_MEDIAN_MULTIPLIER,
        MIN_ONE_OFF_MINOR,
    )
    return amounts.filter { it <= threshold }.sum()
}

private const val MIN_EXPENSES_FOR_ROBUST_PROJECTION = 5
private const val ONE_OFF_MEDIAN_MULTIPLIER = 5L
private const val MIN_ONE_OFF_MINOR = 50_000L

private fun scaleExpenseParts(parts: List<Pair<Long, Long?>>, fundedAmountMinor: Long): List<Pair<Long, Long?>> {
    val weights = parts.map { abs(it.first) }
    val totalWeight = weights.sum().coerceAtLeast(1L)
    var remaining = fundedAmountMinor
    return parts.mapIndexed { index, (_, categoryId) ->
        val value = if (index == parts.lastIndex) remaining else fundedAmountMinor * weights[index] / totalWeight
        remaining -= value
        -value to categoryId
    }
}

private fun findConversionFunding(
    transactions: List<TransactionEntity>,
    zoneId: ZoneId,
): Map<Long, Funding> {
    val groups = transactions.filter { it.transferGroupId != null }.groupBy { it.transferGroupId }
    val purchases = transactions.filter {
        !it.isTransfer && it.transferGroupId == null && it.amountMinor < 0L
    }
    val usedPurchases = mutableSetOf<Long>()
    val result = mutableMapOf<Long, Funding>()
    groups.values.forEach { legs ->
        val outgoing = legs.firstOrNull { it.amountMinor < 0L && it.currency == BASE_CURRENCY } ?: return@forEach
        val incoming = legs.firstOrNull { it.amountMinor > 0L && it.currency != outgoing.currency } ?: return@forEach
        val received = incoming.amountMinor
        val conversionDay = Instant.ofEpochMilli(outgoing.occurredAt).atZone(zoneId).toLocalDate()
        val candidate = purchases
            .asSequence()
            .filter { purchase ->
                val spent = -purchase.amountMinor
                val leftover = received - spent
                val purchaseDay = Instant.ofEpochMilli(purchase.occurredAt).atZone(zoneId).toLocalDate()
                purchase.id !in usedPurchases &&
                    purchase.accountId == incoming.accountId &&
                    purchase.currency == incoming.currency &&
                    leftover >= 0L &&
                    leftover <= maxOf(received / 20L, 100L) &&
                    abs(ChronoUnit.DAYS.between(purchaseDay, conversionDay)) <= 1L
            }
            .minByOrNull { abs((-it.amountMinor) - received) }
            ?: return@forEach
        usedPurchases += candidate.id
        result[candidate.id] = Funding(abs(outgoing.amountMinor), outgoing.currency)
    }
    return result
}

private const val BASE_CURRENCY = "GEL"
