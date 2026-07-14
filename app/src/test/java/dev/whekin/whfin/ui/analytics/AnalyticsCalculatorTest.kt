package dev.whekin.whfin.ui.analytics

import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
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
            YearMonth.of(2026, 7),
            1,
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
    fun aggregatesCategoryRangeAndBuildsSelectedCategoryYearTrend() {
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
            YearMonth.of(2026, 3),
            3,
            AnalyticsTrendFilter.Category(food.id),
            zone,
        )

        assertEquals(7_500L, data.categoryExpenseMinor)
        assertEquals(6_000L, data.categoryValues.first { it.categoryId == food.id }.expenseMinor)
        assertEquals(listOf(1_000L, 2_000L, 3_000L), data.trendValues.take(3).map { it.expenseMinor })
        assertTrue(data.trendValues.drop(3).all { it.expenseMinor == 0L })
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
            YearMonth.of(2026, 7),
            1,
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
            YearMonth.of(2026, 7),
            1,
            AnalyticsTrendFilter.All,
            zone,
        )

        assertEquals(0L, data.expenseMinor)
        assertEquals(listOf(AnalyticsCurrencyValue("USD", 2_360)), data.otherCurrencyExpenses)
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
    )
}
