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
    val through = nextIncomeWindow(incomeSources, today)?.to ?: today.plusDays(45)
    val active = transactions.filter { !it.isVoided && it.day(zone) <= today }
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
            recurringOccurrences(observations, today, through)),
        stillDue = recurringDue(observations, today),
    )
}

private fun TransactionEntity.day(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()
