package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.recurring.RecurringCharge
import dev.whekin.whfin.data.recurring.detectRecurringCharges
import dev.whekin.whfin.data.recurring.recurringDue
import dev.whekin.whfin.data.recurring.recurringObservations
import dev.whekin.whfin.data.recurring.recurringOccurrences
import dev.whekin.whfin.data.income.IncomeExpectations
import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.ui.analytics.ordinaryExpenseDaily
import dev.whekin.whfin.ui.analytics.ownExpenseAmounts
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One complete reading: the ordinary rate, bills and payday always use the same ledger inputs. */
internal data class HomeCashForecast(val runway: HomeRunway?, val stillDue: List<RecurringCharge>)

internal fun cashForecast(
    spendableMinor: Long?,
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    merchants: List<MerchantEntity>,
    allocations: List<TransactionAllocationEntity>,
    incomeSources: List<IncomeSourceEntity>,
    today: LocalDate,
    zone: ZoneId,
): HomeCashForecast {
    val active = transactions.filter { !it.isVoided && it.day(zone) <= today }
    val currentMonth = YearMonth.from(today)
    val debtIds = allocations.filter { it.purpose == AllocationPurpose.LOAN || it.purpose == AllocationPurpose.REPAYMENT }
        .mapTo(mutableSetOf()) { it.transactionId }
    val systemCategories = categories.filter { it.isSystem }.mapTo(mutableSetOf()) { it.id }
    val activeSources = incomeSources.filter { IncomeExpectations.covers(it, currentMonth) }
    // An arbitrary credit/refund on the receiving account is not proof of salary. Only a unique
    // declared source with an exact amount/currency near its payday can settle the month here.
    val arrivedSourceMonths = IncomeExpectations.of(
        activeSources.filter { source -> activeSources.count {
            it.accountId == source.accountId && it.currency == source.currency
        } == 1 },
        active.filter { it.id !in debtIds && it.categoryId !in systemCategories },
        currentMonth, today, zone,
    ).filter { it.fulfilled }.mapTo(mutableSetOf()) { it.source.id to currentMonth }
    val through = paydayHorizon(nextPayday(incomeSources, today, arrivedSourceMonths), today)
    val amounts = ownExpenseAmounts(active, categories, allocations, zone)
    val observations = recurringObservations(active, merchants, zone, amounts)
    val recurringKeys = detectRecurringCharges(observations, today).mapTo(mutableSetOf()) { it.key }
    val recurringIds = observations.filter { it.key in recurringKeys }.mapTo(mutableSetOf()) { it.transactionId }
    val current = active.filter { YearMonth.from(it.day(zone)) == YearMonth.from(today) }
    val currentAmounts = current.filter { it.id in amounts && it.id !in recurringIds }.map { amounts[it.id] }
    val daily = if (currentAmounts.any { it == null }) null else
        ordinaryExpenseDaily(currentAmounts.filterNotNull(), today.dayOfMonth)
    return HomeCashForecast(
        runway = homeRunway(spendableMinor, daily, incomeSources, today,
            recurringOccurrences = recurringOccurrences(observations, today, through),
            arrivedSourceMonths = arrivedSourceMonths),
        stillDue = recurringDue(observations, today),
    )
}

private fun TransactionEntity.day(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()
