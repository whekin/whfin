package dev.whekin.whfin.ui.analytics

import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.ui.feed.FeedItem
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsTransactionsFilterTest {
    private val food = CategoryEntity(1, "Food", kind = CategoryKind.EXPENSE, icon = "ShoppingCart", color = 0)
    private val transport = CategoryEntity(2, "Transport", kind = CategoryKind.EXPENSE, icon = "DirectionsBus", color = 0)
    private val unaccounted = CategoryEntity(
        3,
        "Unaccounted",
        kind = CategoryKind.EXPENSE,
        icon = "Category",
        color = 0,
        isSystem = true,
    )

    @Test
    fun categoryDrillDownMatchesAnalyticsMonthAndExpenseRules() {
        val items = listOf(
            item(1, LocalDate.of(2026, 6, 2), -1_000, "GEL", food),
            item(2, LocalDate.of(2026, 7, 2), -2_000, "GEL", food),
            item(3, LocalDate.of(2026, 6, 3), -3_000, "GEL", transport),
            item(4, LocalDate.of(2026, 6, 4), 4_000, "GEL", food),
            item(5, LocalDate.of(2026, 6, 5), -5_000, "GEL", food),
            item(6, LocalDate.of(2026, 6, 6), -600, "USD", food, fundedByGel = 1_700),
            item(7, LocalDate.of(2026, 6, 7), -7_000, "GEL", unaccounted, source = TxSource.ADJUSTMENT),
        )
        val allocations = listOf(
            TransactionAllocationEntity(
                transactionId = 5,
                amountMinor = -5_000,
                purpose = AllocationPurpose.LOAN,
            ),
        )
        val request = AnalyticsTransactionsRequest(
            month = YearMonth.of(2026, 6),
            categoryFilterEnabled = true,
            categoryId = food.id,
            filterName = food.name,
            expectedExpenseMinor = 2_700,
        )

        val result = filterAnalyticsTransactions(items, allocations, listOf(food, transport, unaccounted), request)

        assertEquals(listOf(6L, 1L), result.map { it.tx.id })
    }

    @Test
    fun nullCategoryMeansUncategorizedOnlyWhenCategoryFilterIsEnabled() {
        val uncategorized = item(
            id = 8,
            day = LocalDate.of(2026, 6, 8),
            amountMinor = -800,
            currency = "GEL",
            category = null,
        )
        val categorized = item(9, LocalDate.of(2026, 6, 9), -900, "GEL", food)
        val request = AnalyticsTransactionsRequest(
            month = YearMonth.of(2026, 6),
            categoryFilterEnabled = true,
            categoryId = null,
            filterName = "Uncategorized",
            expectedExpenseMinor = 800,
        )

        val result = filterAnalyticsTransactions(
            listOf(uncategorized, categorized),
            emptyList(),
            listOf(food),
            request,
        )

        assertEquals(listOf(8L), result.map { it.tx.id })
    }

    private fun item(
        id: Long,
        day: LocalDate,
        amountMinor: Long,
        currency: String,
        category: CategoryEntity?,
        fundedByGel: Long? = null,
        source: TxSource = TxSource.STATEMENT,
    ): FeedItem {
        val occurredAt = day.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        return FeedItem(
            tx = TransactionEntity(
                id = id,
                accountId = 1,
                amountMinor = amountMinor,
                currency = currency,
                occurredAt = occurredAt,
                categoryId = category?.id,
                status = TxStatus.CONFIRMED,
                source = source,
            ),
            merchant = null,
            category = category,
            account = null,
            cardHint = null,
            fundedByConversionMinor = fundedByGel,
            fundedByConversionCurrency = fundedByGel?.let { "GEL" },
            day = day,
        )
    }
}
