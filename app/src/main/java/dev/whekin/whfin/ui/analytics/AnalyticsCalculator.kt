package dev.whekin.whfin.ui.analytics

import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
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
)

internal data class AnalyticsCurrencyValue(
    val currency: String,
    val expenseMinor: Long,
)

internal data class AnalyticsPace(
    val daysElapsed: Int,
    val daysInMonth: Int,
    val projectedExpenseMinor: Long,
    val previousMonthExpenseMinor: Long,
)

internal data class AnalyticsCategoryChange(
    val categoryId: Long?,
    val name: String?,
    val icon: String?,
    val color: Int?,
    val expenseMinor: Long,
    val previousExpenseMinor: Long,
) {
    val deltaMinor: Long get() = expenseMinor - previousExpenseMinor
}

internal data class AnalyticsData(
    val selectedMonth: YearMonth,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val categoryRangeMonths: Int,
    val categoryExpenseMinor: Long,
    val categoryValues: List<AnalyticsCategoryValue>,
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
    /** Currencies of the selected month whose day has no quote yet, so they are left out of totals. */
    val unvaluedCurrencies: Set<String> = emptySet(),
) {
    val deltaMinor: Long get() = incomeMinor - expenseMinor
}

private data class AnalyticsSlice(
    val transactionId: Long,
    val month: YearMonth,
    val currency: String,
    val amountMinor: Long,
    /** Value in GEL booked at the rate of this row's own day; null while the day is unpriced. */
    val gelMinor: Long?,
    val categoryId: Long?,
    val unaccounted: Boolean,
    val pending: Boolean,
)

private data class Funding(
    val amountMinor: Long,
    val currency: String,
)

internal fun calculateAnalytics(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    allocations: List<TransactionAllocationEntity>,
    selectedMonth: YearMonth,
    categoryRangeMonths: Int,
    trendFilter: AnalyticsTrendFilter,
    zoneId: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zoneId),
): AnalyticsData {
    require(categoryRangeMonths in setOf(1, 3, 6, 12))
    val categoryById = categories.associateBy { it.id }
    val allocationsByTransaction = allocations.groupBy { it.transactionId }
    val fundingByPurchase = findConversionFunding(transactions, zoneId)
    val slices = transactions
        .asSequence()
        .filterNot { it.isTransfer || it.transferGroupId != null }
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
                scaleExpenseParts(includedParts, funding.amountMinor).map { (amount, categoryId) ->
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
                    categoryId = categoryId,
                    unaccounted = transaction.source == TxSource.ADJUSTMENT ||
                        categoryId?.let(categoryById::get)?.isSystem == true,
                    pending = transaction.status == TxStatus.PENDING,
                )
            }
        }
        .toList()

    // Anything with a booked lari value counts, whatever currency it was spent in.
    val baseSlices = slices.filter { it.gelMinor != null && !it.unaccounted }
    val unvaluedCurrencies = slices
        .filter { it.gelMinor == null && !it.unaccounted && it.month == selectedMonth }
        .map { it.currency }
        .toSortedSet()
    val selectedBase = baseSlices.filter { it.month == selectedMonth }
    val income = selectedBase.sumOf { it.gelMinor!!.coerceAtLeast(0L) }
    val expenses = -selectedBase.sumOf { it.gelMinor!!.coerceAtMost(0L) }
    val previousMonth = selectedMonth.minusMonths(1)
    val previousBase = baseSlices.filter { it.month == previousMonth }
    val previousExpenses = -previousBase.sumOf { it.gelMinor!!.coerceAtMost(0L) }
    val pace = if (selectedMonth == YearMonth.from(today)) {
        AnalyticsPace(
            daysElapsed = today.dayOfMonth,
            daysInMonth = selectedMonth.lengthOfMonth(),
            projectedExpenseMinor = expenses * selectedMonth.lengthOfMonth() / today.dayOfMonth,
            previousMonthExpenseMinor = previousExpenses,
        )
    } else {
        null
    }
    val currentCategoryExpenses = selectedBase
        .filter { it.gelMinor!! < 0L }
        .groupBy { it.categoryId }
        .mapValues { (_, values) -> -values.sumOf { it.gelMinor!! } }
    val previousCategoryExpenses = previousBase
        .filter { it.gelMinor!! < 0L }
        .groupBy { it.categoryId }
        .mapValues { (_, values) -> -values.sumOf { it.gelMinor!! } }
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
            )
        }
        .filter { it.deltaMinor != 0L }
        .sortedByDescending { abs(it.deltaMinor) }
        .take(3)

    val rangeStart = selectedMonth.minusMonths((categoryRangeMonths - 1).toLong())
    val rangeExpenses = baseSlices.filter {
        it.gelMinor!! < 0L && it.month >= rangeStart && it.month <= selectedMonth
    }
    val categoryValues = rangeExpenses
        .groupBy { it.categoryId }
        .map { (categoryId, values) ->
            val category = categoryId?.let(categoryById::get)
            AnalyticsCategoryValue(
                categoryId = categoryId,
                name = category?.name,
                icon = category?.icon,
                color = category?.color,
                expenseMinor = -values.sumOf { it.gelMinor!! },
            )
        }
        .sortedByDescending { it.expenseMinor }

    val trendValues = (1..12).map { monthNumber ->
        val month = YearMonth.of(selectedMonth.year, monthNumber)
        val expense = -baseSlices
            .filter {
                it.month == month && it.gelMinor!! < 0L && when (trendFilter) {
                    AnalyticsTrendFilter.All -> true
                    is AnalyticsTrendFilter.Category -> it.categoryId == trendFilter.categoryId
                }
            }
            .sumOf { it.gelMinor!! }
        AnalyticsMonthValue(month, expense)
    }
    val previousTrendExpense = -baseSlices
        .filter {
            it.month == selectedMonth.minusMonths(1) && it.gelMinor!! < 0L && when (trendFilter) {
                AnalyticsTrendFilter.All -> true
                is AnalyticsTrendFilter.Category -> it.categoryId == trendFilter.categoryId
            }
        }
        .sumOf { it.gelMinor!! }

    val selectedUnaccounted = slices.filter { it.month == selectedMonth && it.unaccounted }
    // Only what could not be valued stays a native amount; the rest is already in the totals above.
    val otherCurrencies = slices
        .filter { it.month == selectedMonth && it.gelMinor == null && !it.unaccounted && it.amountMinor < 0L }
        .groupBy { it.currency }
        .map { (currency, values) -> AnalyticsCurrencyValue(currency, -values.sumOf { it.amountMinor }) }
        .sortedBy { it.currency }

    return AnalyticsData(
        selectedMonth = selectedMonth,
        incomeMinor = income,
        expenseMinor = expenses,
        categoryRangeMonths = categoryRangeMonths,
        categoryExpenseMinor = categoryValues.sumOf { it.expenseMinor },
        categoryValues = categoryValues,
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
        pendingCount = slices.filter { it.month == selectedMonth && it.pending }.map { it.transactionId }.distinct().size,
        hasAnyTransactions = slices.isNotEmpty(),
        pace = pace,
        categoryChanges = categoryChanges,
    )
}

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
