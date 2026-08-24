package dev.whekin.whfin.ui.analytics

import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.isOpeningBalanceAnchor
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsCalculatorTest {
    private val zone = ZoneId.of("UTC")
    private val food = CategoryEntity(1, "Food", kind = CategoryKind.EXPENSE, icon = "ShoppingCart", color = 0)
    private val transport = CategoryEntity(2, "Transport", kind = CategoryKind.EXPENSE, icon = "DirectionsBus", color = 0)
    private val unaccounted = CategoryEntity(3, "Unaccounted", kind = CategoryKind.EXPENSE, icon = "Category", color = 0, isSystem = true)

    @Test
    fun openingBalanceIsBalanceOnly_andNormalAdjustmentRemainsUnaccounted() {
        val opening = tx(
            id = 1,
            amount = 30_000,
            currency = "GEL",
            date = LocalDate.of(2026, 7, 1),
            categoryId = unaccounted.id,
            source = TxSource.ADJUSTMENT,
            isTransfer = true,
        )
        val adjustment = tx(
            id = 2,
            amount = -500,
            currency = "GEL",
            date = LocalDate.of(2026, 7, 2),
            categoryId = unaccounted.id,
            source = TxSource.ADJUSTMENT,
        )

        assertTrue(opening.isOpeningBalanceAnchor())
        val openingData = calculateAnalytics(
            transactions = listOf(opening),
            categories = listOf(unaccounted),
            allocations = emptyList(),
            period = AnalyticsPeriod.month(YearMonth.of(2026, 7)),
            trendFilter = AnalyticsTrendFilter.All,
            zoneId = zone,
        )
        assertEquals(0L, openingData.incomeMinor)
        assertEquals(0L, openingData.expenseMinor)
        assertEquals(0L, openingData.unaccountedNetMinor)

        val data = calculateAnalytics(
            transactions = listOf(opening, adjustment),
            categories = listOf(unaccounted),
            allocations = emptyList(),
            period = AnalyticsPeriod.month(YearMonth.of(2026, 7)),
            trendFilter = AnalyticsTrendFilter.All,
            zoneId = zone,
        )

        assertEquals(0L, data.incomeMinor)
        assertEquals(0L, data.expenseMinor)
        assertEquals(-500L, data.unaccountedNetMinor)
    }

    @Test
    fun excludesTransfersDebtsAndAdjustmentsFromMonthTotals() {
        val transactions = listOf(
            tx(1, -10_000, "GEL", LocalDate.of(2026, 7, 2), categoryId = food.id, status = TxStatus.PENDING),
            tx(2, 50_000, "GEL", LocalDate.of(2026, 7, 3)),
            tx(3, -20_000, "GEL", LocalDate.of(2026, 7, 4), isTransfer = true),
            tx(4, 100_000, "GEL", LocalDate.of(2026, 7, 5), categoryId = unaccounted.id, source = TxSource.ADJUSTMENT),
            tx(5, -3_000, "GEL", LocalDate.of(2026, 7, 6), categoryId = food.id),
        )
        val data = calculateAnalytics(
            transactions,
            listOf(food, unaccounted),
            listOf(TransactionAllocationEntity(transactionId = 5, amountMinor = -3_000, purpose = AllocationPurpose.LOAN)),
            AnalyticsPeriod.month(YearMonth.of(2026, 7)),
            AnalyticsTrendFilter.All,
            zone,
        )

        assertEquals(50_000L, data.incomeMinor)
        assertEquals(10_000L, data.expenseMinor)
        assertEquals(100_000L, data.unaccountedNetMinor)
        assertEquals(1, data.pendingCount)
        assertEquals(listOf(food.id), data.categoryValues.map { it.categoryId })
    }

    @Test
    fun categoriesCoverTheSelectedPeriodAndCarryTheirOwnBaseline() {
        val transactions = listOf(
            tx(1, -1_000, "GEL", LocalDate.of(2026, 1, 5), categoryId = food.id),
            tx(2, -2_000, "GEL", LocalDate.of(2026, 2, 5), categoryId = food.id),
            tx(3, -3_000, "GEL", LocalDate.of(2026, 3, 5), categoryId = food.id),
            tx(4, -1_500, "GEL", LocalDate.of(2026, 3, 8), categoryId = transport.id),
        )
        val data = calculateAnalytics(
            transactions,
            listOf(food, transport),
            emptyList(),
            AnalyticsPeriod.month(YearMonth.of(2026, 3)),
            AnalyticsTrendFilter.Category(food.id),
            zone,
        )

        // Only March: the categories of a screen must answer the same period as its own total.
        assertEquals(4_500L, data.categoryValues.sumOf { it.expenseMinor })
        assertEquals(YearMonth.of(2026, 1), data.trendValues.first().month)
        assertEquals(YearMonth.of(2026, 12), data.trendValues.last().month)
        assertEquals(listOf(1_000L, 2_000L, 3_000L), data.trendValues.take(3).map { it.expenseMinor })
        assertTrue(data.trendValues.drop(3).all { it.expenseMinor == 0L })
        assertEquals(1_000L, data.spendingAverageMinor)
        assertEquals(
            3_000L,
            data.categoryValues.single { it.categoryId == food.id }.expenseMinor,
        )
        assertEquals(
            1_000L,
            data.categoryValues.single { it.categoryId == food.id }.averageExpenseMinor,
        )
    }

    @Test
    fun attributesLinkedCurrencyConversionToPurchaseCategoryInGel() {
        val transactions = listOf(
            tx(1, -10_526, "GEL", LocalDate.of(2026, 7, 10), accountId = 1, transferGroupId = 10, isTransfer = true),
            tx(2, 4_000, "USD", LocalDate.of(2026, 7, 10), accountId = 2, transferGroupId = 10, isTransfer = true),
            tx(3, -3_900, "USD", LocalDate.of(2026, 7, 10), accountId = 2, categoryId = food.id),
        )
        val data = calculateAnalytics(
            transactions,
            listOf(food),
            emptyList(),
            AnalyticsPeriod.month(YearMonth.of(2026, 7)),
            AnalyticsTrendFilter.All,
            zone,
        )

        assertEquals(10_526L, data.expenseMinor)
        assertEquals(10_526L, data.categoryValues.single().expenseMinor)
        assertTrue(data.otherCurrencyExpenses.isEmpty())
    }

    @Test
    fun keepsUnconvertedForeignExpenseSeparate() {
        val data = calculateAnalytics(
            listOf(tx(1, -2_360, "USD", LocalDate.of(2026, 7, 10), categoryId = food.id)),
            listOf(food),
            emptyList(),
            AnalyticsPeriod.month(YearMonth.of(2026, 7)),
            AnalyticsTrendFilter.All,
            zone,
        )

        assertEquals(0L, data.expenseMinor)
        assertEquals(listOf(AnalyticsCurrencyValue("USD", 2_360)), data.otherCurrencyExpenses)
    }

    @Test
    fun projectsCurrentMonthPaceAndFindsLargestCategoryDrivers() {
        val transactions = listOf(
            tx(1, -20_000, "GEL", LocalDate.of(2026, 6, 4), categoryId = food.id),
            tx(2, -10_000, "GEL", LocalDate.of(2026, 6, 8), categoryId = transport.id),
            tx(3, -10_000, "GEL", LocalDate.of(2026, 7, 3), categoryId = food.id),
            tx(4, -30_000, "GEL", LocalDate.of(2026, 7, 8), categoryId = transport.id),
        )
        val data = calculateAnalytics(
            transactions = transactions,
            categories = listOf(food, transport),
            allocations = emptyList(),
            period = AnalyticsPeriod.month(YearMonth.of(2026, 7)),
            trendFilter = AnalyticsTrendFilter.All,
            zoneId = zone,
            today = LocalDate.of(2026, 7, 10),
        )

        assertEquals(
            AnalyticsPace(
                daysElapsed = 10,
                daysTotal = 31,
                projectedExpenseMinor = 124_000,
                previousPeriodExpenseMinor = 30_000,
            ),
            data.pace,
        )
        assertEquals(
            listOf(
                AnalyticsCategoryChange(
                    categoryId = transport.id,
                    name = transport.name,
                    icon = transport.icon,
                    color = transport.color,
                    expenseMinor = 30_000,
                    previousExpenseMinor = 10_000,
                    projectedExpenseMinor = 93_000,
                ),
                AnalyticsCategoryChange(
                    categoryId = food.id,
                    name = food.name,
                    icon = food.icon,
                    color = food.color,
                    expenseMinor = 10_000,
                    previousExpenseMinor = 20_000,
                    projectedExpenseMinor = 31_000,
                ),
            ),
            data.categoryChanges,
        )
    }

    @Test
    fun oneLargePurchaseIsCountedOnceInsteadOfRepeatedForEveryRemainingDay() {
        val transactions = buildList {
            (1L..10L).forEach { day ->
                add(tx(day, -10_000, "GEL", LocalDate.of(2026, 7, day.toInt()), categoryId = food.id))
            }
            add(tx(11, -300_000, "GEL", LocalDate.of(2026, 7, 10), categoryId = food.id))
        }

        val data = calculateAnalytics(
            transactions = transactions,
            categories = listOf(food),
            allocations = emptyList(),
            period = AnalyticsPeriod.month(YearMonth.of(2026, 7)),
            trendFilter = AnalyticsTrendFilter.All,
            zoneId = zone,
            today = LocalDate.of(2026, 7, 10),
        )

        // 4,000 GEL already spent + the ordinary 100 GEL/day for the 21 remaining days.
        assertEquals(610_000L, data.pace?.projectedExpenseMinor)
        assertEquals(610_000L, data.categoryChanges.single().projectedExpenseMinor)
    }

    @Test
    fun doesNotProjectHistoricalMonths() {
        val data = calculateAnalytics(
            transactions = listOf(tx(1, -10_000, "GEL", LocalDate.of(2026, 6, 3), categoryId = food.id)),
            categories = listOf(food),
            allocations = emptyList(),
            period = AnalyticsPeriod.month(YearMonth.of(2026, 6)),
            trendFilter = AnalyticsTrendFilter.All,
            zoneId = zone,
            today = LocalDate.of(2026, 7, 10),
        )

        assertEquals(null, data.pace)
    }

    /**
     * A hobby split into buckets is still one hobby on the statistics screen. The split exists so
     * that "I bought a fork" and "I rode more" stop cancelling each other out inside one number,
     * not so that the hobby disappears from the list into three small slices.
     */
    @Test
    fun aGroupIsOneLineOnTheList_andItsTrendFollowsEveryBucketIn() {
        val bike = CategoryEntity(10, "Bike", kind = CategoryKind.EXPENSE, icon = "PedalBike", color = 0)
        val parts = bike.copy(id = 11, name = "Parts", parentId = bike.id)
        val lifts = bike.copy(id = 12, name = "Lifts", parentId = bike.id)
        val categories = listOf(food, bike, parts, lifts)
        val transactions = listOf(
            tx(1, -132_000, "GEL", LocalDate.of(2026, 7, 4), categoryId = parts.id),
            tx(2, -1_500, "GEL", LocalDate.of(2026, 7, 5), categoryId = lifts.id),
            tx(3, -3_000, "GEL", LocalDate.of(2026, 7, 6), categoryId = bike.id),
            tx(4, -20_000, "GEL", LocalDate.of(2026, 7, 7), categoryId = food.id),
        )

        val grouped = calculateAnalytics(
            transactions,
            categories,
            emptyList(),
            AnalyticsPeriod.month(YearMonth.of(2026, 7)),
            AnalyticsTrendFilter.All,
            zone,
        )

        // One row for the whole hobby, holding its own rows and both buckets.
        assertEquals(
            listOf("Bike" to 136_500L, "Food" to 20_000L),
            grouped.categoryValues.map { it.name to it.expenseMinor },
        )

        val trend = calculateAnalytics(
            transactions,
            categories,
            emptyList(),
            AnalyticsPeriod.month(YearMonth.of(2026, 7)),
            AnalyticsTrendFilter.Category(bike.id),
            zone,
        )

        assertEquals(
            136_500L,
            trend.trendValues.single { it.month == YearMonth.of(2026, 7) }.expenseMinor,
        )
    }

    /** Grouping changes how a total is reported, never what the money adds up to. */
    @Test
    fun groupingLeavesTheTotalUntouched() {
        val bike = CategoryEntity(10, "Bike", kind = CategoryKind.EXPENSE, icon = "PedalBike", color = 0)
        val parts = bike.copy(id = 11, name = "Parts", parentId = bike.id)
        val transactions = listOf(
            tx(1, -132_000, "GEL", LocalDate.of(2026, 7, 4), categoryId = parts.id),
            tx(2, -20_000, "GEL", LocalDate.of(2026, 7, 7), categoryId = food.id),
        )
        val flat = calculateAnalytics(
            transactions,
            listOf(food, bike, parts.copy(parentId = null)),
            emptyList(),
            AnalyticsPeriod.month(YearMonth.of(2026, 7)),
            AnalyticsTrendFilter.All,
            zone,
        )
        val nested = calculateAnalytics(
            transactions,
            listOf(food, bike, parts),
            emptyList(),
            AnalyticsPeriod.month(YearMonth.of(2026, 7)),
            AnalyticsTrendFilter.All,
            zone,
        )

        assertEquals(flat.expenseMinor, nested.expenseMinor)
        assertEquals(
            flat.categoryValues.sumOf { it.expenseMinor },
            nested.categoryValues.sumOf { it.expenseMinor },
        )
    }

    private fun tx(
        id: Long,
        amount: Long,
        currency: String,
        date: LocalDate,
        accountId: Long = 1,
        categoryId: Long? = null,
        transferGroupId: Long? = null,
        isTransfer: Boolean = false,
        status: TxStatus = TxStatus.CONFIRMED,
        source: TxSource = TxSource.STATEMENT,
        gelValueMinor: Long? = null,
    ) = TransactionEntity(
        id = id,
        accountId = accountId,
        amountMinor = amount,
        currency = currency,
        occurredAt = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        categoryId = categoryId,
        status = status,
        source = source,
        transferGroupId = transferGroupId,
        isTransfer = isTransfer,
        gelValueMinor = gelValueMinor,
    )

    @Test
    fun aForeignExpenseCountsOnceItsOwnDayHasBeenPriced() {
        val transactions = listOf(
            tx(1, -10_000, "GEL", LocalDate.of(2026, 7, 2), categoryId = food.id),
            tx(2, -2_000, "USD", LocalDate.of(2026, 7, 3), categoryId = food.id, gelValueMinor = -5_400),
        )

        val data = calculateAnalytics(
            transactions, listOf(food), emptyList(), AnalyticsPeriod.month(YearMonth.of(2026, 7)),
            AnalyticsTrendFilter.All, zone,
        )

        assertEquals(15_400L, data.expenseMinor)
        assertEquals(15_400L, data.categoryValues.single { it.categoryId == food.id }.expenseMinor)
        assertTrue(data.unvaluedCurrencies.isEmpty())
        assertTrue("a valued row is no longer a leftover native amount", data.otherCurrencyExpenses.isEmpty())
    }

    @Test
    fun anUnpricedForeignExpenseStaysOutOfTheTotalAndIsNamed() {
        val transactions = listOf(
            tx(1, -10_000, "GEL", LocalDate.of(2026, 7, 2), categoryId = food.id),
            tx(2, -2_000, "USD", LocalDate.of(2026, 7, 3), categoryId = food.id),
        )

        val data = calculateAnalytics(
            transactions, listOf(food), emptyList(), AnalyticsPeriod.month(YearMonth.of(2026, 7)),
            AnalyticsTrendFilter.All, zone,
        )

        // Never a guess and never a zero: the lari total is honest and the gap is named.
        assertEquals(10_000L, data.expenseMinor)
        assertEquals(setOf("USD"), data.unvaluedCurrencies)
        assertEquals(listOf("USD"), data.otherCurrencyExpenses.map { it.currency })
        assertEquals(2_000L, data.otherCurrencyExpenses.single().expenseMinor)
    }

    @Test
    fun aSplitSharesTheBookedValueInTheSameProportionAsTheMoney() {
        val transactions = listOf(
            tx(1, -3_000, "USD", LocalDate.of(2026, 7, 3), categoryId = food.id, gelValueMinor = -9_000),
        )
        val allocations = listOf(
            TransactionAllocationEntity(
                id = 1, transactionId = 1, amountMinor = -1_000,
                categoryId = food.id, purpose = AllocationPurpose.PERSONAL,
            ),
            TransactionAllocationEntity(
                id = 2, transactionId = 1, amountMinor = -2_000,
                categoryId = transport.id, purpose = AllocationPurpose.PERSONAL,
            ),
        )

        val data = calculateAnalytics(
            transactions, listOf(food, transport), allocations, AnalyticsPeriod.month(YearMonth.of(2026, 7)),
            AnalyticsTrendFilter.All, zone,
        )

        assertEquals(9_000L, data.expenseMinor)
        assertEquals(3_000L, data.categoryValues.single { it.categoryId == food.id }.expenseMinor)
        assertEquals(6_000L, data.categoryValues.single { it.categoryId == transport.id }.expenseMinor)
    }

    @Test
    fun aYearTotalsItsOwnTwelveMonthsAndChartsThemInOrder() {
        val transactions = listOf(
            tx(1, -5_000, "GEL", LocalDate.of(2025, 11, 4), categoryId = food.id),
            tx(2, -1_000, "GEL", LocalDate.of(2026, 1, 5), categoryId = food.id),
            tx(3, -2_000, "GEL", LocalDate.of(2026, 6, 5), categoryId = transport.id),
            tx(4, 40_000, "GEL", LocalDate.of(2026, 6, 6)),
            tx(5, -3_000, "GEL", LocalDate.of(2026, 12, 30), categoryId = food.id),
            tx(6, -9_000, "GEL", LocalDate.of(2027, 1, 2), categoryId = food.id),
        )

        val data = calculateAnalytics(
            transactions = transactions,
            categories = listOf(food, transport),
            allocations = emptyList(),
            period = AnalyticsPeriod.year(YearMonth.of(2026, 6)),
            trendFilter = AnalyticsTrendFilter.All,
            zoneId = zone,
            today = LocalDate.of(2027, 3, 4),
        )

        assertEquals(6_000L, data.expenseMinor)
        assertEquals(40_000L, data.incomeMinor)
        // The neighbouring years belong to their own periods, never to this one.
        assertEquals(12, data.trendValues.size)
        assertEquals(YearMonth.of(2026, 1), data.trendValues.first().month)
        assertEquals(YearMonth.of(2026, 12), data.trendValues.last().month)
        assertEquals(listOf(1_000L, 2_000L, 3_000L), data.trendValues.map { it.expenseMinor }.filter { it > 0L })
        assertEquals(5_000L, data.previousTrendExpenseMinor)
        assertEquals(5_000L, data.spendingAverageMinor)
        // Categories follow the period, so a year's categories add up to the year's expenses.
        assertEquals(data.expenseMinor, data.categoryValues.sumOf { it.expenseMinor })
    }

    @Test
    fun theRunningYearProjectsFromTheDayOfYearAndAFinishedYearDoesNot() {
        val transactions = listOf(
            tx(1, -60_000, "GEL", LocalDate.of(2025, 5, 5), categoryId = food.id),
            tx(2, -10_000, "GEL", LocalDate.of(2026, 1, 10), categoryId = food.id),
            tx(3, -10_000, "GEL", LocalDate.of(2026, 2, 10), categoryId = food.id),
        )

        val running = calculateAnalytics(
            transactions = transactions,
            categories = listOf(food),
            allocations = emptyList(),
            period = AnalyticsPeriod.year(YearMonth.of(2026, 3)),
            trendFilter = AnalyticsTrendFilter.All,
            zoneId = zone,
            today = LocalDate.of(2026, 3, 22), // day 81 of 365
        )

        assertEquals(81, running.pace?.daysElapsed)
        assertEquals(365, running.pace?.daysTotal)
        assertEquals(20_000L * 365 / 81, running.pace?.projectedExpenseMinor)
        assertEquals(60_000L, running.pace?.previousPeriodExpenseMinor)

        val finished = calculateAnalytics(
            transactions = transactions,
            categories = listOf(food),
            allocations = emptyList(),
            period = AnalyticsPeriod.year(YearMonth.of(2025, 5)),
            trendFilter = AnalyticsTrendFilter.All,
            zoneId = zone,
            today = LocalDate.of(2026, 3, 22),
        )

        assertEquals(null, finished.pace)
    }
}
