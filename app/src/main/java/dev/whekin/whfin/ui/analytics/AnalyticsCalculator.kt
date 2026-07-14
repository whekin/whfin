package dev.whekin.whfin.ui.analytics

import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import java.time.Instant
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
) {
    val deltaMinor: Long get() = incomeMinor - expenseMinor
}

private data class AnalyticsSlice(
    val transactionId: Long,
    val month: YearMonth,
    val currency: String,
    val amountMinor: Long,
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
            parts.asSequence().map { (amount, categoryId, currency) ->
                AnalyticsSlice(
                    transactionId = transaction.id,
                    month = month,
                    currency = currency,
                    amountMinor = amount,
                    categoryId = categoryId,
                    unaccounted = transaction.source == TxSource.ADJUSTMENT ||
                        categoryId?.let(categoryById::get)?.isSystem == true,
                    pending = transaction.status == TxStatus.PENDING,
                )
            }
        }
        .toList()

    val baseSlices = slices.filter { it.currency == BASE_CURRENCY && !it.unaccounted }
    val selectedBase = baseSlices.filter { it.month == selectedMonth }
    val income = selectedBase.sumOf { it.amountMinor.coerceAtLeast(0L) }
    val expenses = -selectedBase.sumOf { it.amountMinor.coerceAtMost(0L) }

    val rangeStart = selectedMonth.minusMonths((categoryRangeMonths - 1).toLong())
    val rangeExpenses = baseSlices.filter {
        it.amountMinor < 0L && it.month >= rangeStart && it.month <= selectedMonth
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
                expenseMinor = -values.sumOf { it.amountMinor },
            )
        }
        .sortedByDescending { it.expenseMinor }

    val trendValues = (1..12).map { monthNumber ->
        val month = YearMonth.of(selectedMonth.year, monthNumber)
        val expense = -baseSlices
            .filter {
                it.month == month && it.amountMinor < 0L && when (trendFilter) {
                    AnalyticsTrendFilter.All -> true
                    is AnalyticsTrendFilter.Category -> it.categoryId == trendFilter.categoryId
                }
            }
            .sumOf { it.amountMinor }
        AnalyticsMonthValue(month, expense)
    }
    val previousTrendExpense = -baseSlices
        .filter {
            it.month == selectedMonth.minusMonths(1) && it.amountMinor < 0L && when (trendFilter) {
                AnalyticsTrendFilter.All -> true
                is AnalyticsTrendFilter.Category -> it.categoryId == trendFilter.categoryId
            }
        }
        .sumOf { it.amountMinor }

    val selectedUnaccounted = slices.filter { it.month == selectedMonth && it.unaccounted }
    val otherCurrencies = slices
        .filter { it.month == selectedMonth && it.currency != BASE_CURRENCY && !it.unaccounted && it.amountMinor < 0L }
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
        unaccountedNetMinor = selectedUnaccounted.sumOf { it.amountMinor },
        otherCurrencyExpenses = otherCurrencies,
        pendingCount = slices.filter { it.month == selectedMonth && it.pending }.map { it.transactionId }.distinct().size,
        hasAnyTransactions = slices.isNotEmpty(),
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
