package dev.whekin.whfin.ui.analytics

import java.time.YearMonth
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.whekin.whfin.data.db.AllocationPurpose
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.TransactionAllocationEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import java.time.LocalDate

/**
 * "Why did this period cost more?" — answered by arithmetic that adds up.
 *
 * The screen states one difference and then attributes it. That only means anything if the parts
 * sum to the whole, if a category that stopped counts as much as one that started, and if the base
 * is the periods that were actually recorded rather than the number three.
 */
class AnalyticsDifferenceTest {

    private val month = AnalyticsPeriod.month(AnalyticsScenario.selectedMonth)

    @Test
    fun `the contributions add up to the stated difference`() {
        val data = AnalyticsScenario.analytics(AnalyticsScenario.Shape.DEARER)

        assertEquals(265_000L, data.expenseMinor)
        assertEquals(220_678L, data.baseline.expenseMinor)
        assertEquals(3, data.baseline.periods.size)
        assertTrue(data.baseline.isComplete)
        assertEquals(
            data.expenseMinor - data.baseline.expenseMinor,
            data.categoryChanges.sumOf { it.deltaMinor },
        )
        assertEquals(44_322L, data.categoryChanges.sumOf { it.deltaMinor })
    }

    @Test
    fun `rounding a baseline between categories loses nothing`() {
        val data = AnalyticsScenario.analytics(AnalyticsScenario.Shape.DEARER)

        // Plain integer division of each category's three-month total comes to 220,676 — two units
        // short of the 220,678 stated above the rows. The two units go to the largest remainders
        // rather than being dropped, which is the only reason the contributions can add up at all.
        assertEquals(3_167L, data.categoryChanges.single { it.name == "Health" }.typicalExpenseMinor)
        assertEquals(20_667L, data.categoryChanges.single { it.name == "Travel" }.typicalExpenseMinor)
        // Every category of the selected period carries the same share the contributions used.
        val shares = data.categoryValues.sumOf { it.averageExpenseMinor } +
            data.categoryChanges.filter { change ->
                data.categoryValues.none { it.categoryId == change.categoryId }
            }.sumOf { it.typicalExpenseMinor }
        assertEquals(data.baseline.expenseMinor, shares)
    }

    @Test
    fun `the biggest category is not the answer when it did not move`() {
        val data = AnalyticsScenario.analytics(AnalyticsScenario.Shape.DEARER)

        // Rent is the largest expense of the month by a distance and explains none of the change.
        assertEquals("Rent", data.categoryValues.first().name)
        assertFalse(data.categoryChanges.any { it.name == "Rent" })
        assertEquals(
            listOf("Health" to 51_833L, "Bike" to 30_167L, "Travel" to -20_667L),
            data.categoryChanges.take(3).map { it.name to it.deltaMinor },
        )
    }

    @Test
    fun `a category that stopped is part of the answer`() {
        val data = AnalyticsScenario.analytics(AnalyticsScenario.Shape.DEARER)
        val travel = data.categoryChanges.single { it.name == "Travel" }

        assertEquals(0L, travel.expenseMinor)
        assertEquals(20_667L, travel.typicalExpenseMinor)
        assertEquals(-20_667L, travel.deltaMinor)
    }

    @Test
    fun `a quieter month states a difference pointing the other way`() {
        val data = AnalyticsScenario.analytics(AnalyticsScenario.Shape.CHEAPER)

        val difference = data.expenseMinor - data.baseline.expenseMinor
        assertTrue(difference < 0L)
        assertEquals(difference, data.categoryChanges.sumOf { it.deltaMinor })
    }

    @Test
    fun `a running month compares the same stretch of every base`() {
        val data = AnalyticsScenario.analytics(
            AnalyticsScenario.Shape.DEARER,
            today = AnalyticsScenario.insideSelectedMonth,
        )

        assertEquals(10, data.comparisonDays)
        assertEquals(226_000L, data.expenseMinor)
        assertEquals(166_778L, data.baseline.expenseMinor)
        assertEquals(59_222L, data.categoryChanges.sumOf { it.deltaMinor })
        assertEquals(
            data.expenseMinor - data.baseline.expenseMinor,
            data.categoryChanges.sumOf { it.deltaMinor },
        )
    }

    @Test
    fun `only the months that were recorded enter the ordinary level`() {
        val data = AnalyticsScenario.analytics(AnalyticsScenario.Shape.SHORT_HISTORY)

        // July alone: dividing its spending by three would report an ordinary life as a surge.
        assertEquals(listOf(YearMonth.of(2026, 7)), data.baseline.periods.map { it.month })
        assertEquals(3, data.baseline.requestedPeriods)
        assertFalse(data.baseline.isComplete)
        assertEquals(201_234L, data.baseline.expenseMinor)
        assertEquals(
            data.expenseMinor - data.baseline.expenseMinor,
            data.categoryChanges.sumOf { it.deltaMinor },
        )
    }

    @Test
    fun `without any earlier record there is no ordinary level to state`() {
        val data = AnalyticsScenario.analytics(AnalyticsScenario.Shape.NO_HISTORY)

        assertFalse(data.baseline.isKnown)
        assertEquals(0, data.baseline.periods.size)
        assertEquals(0L, data.baseline.expenseMinor)
        assertEquals(null, data.pace)
    }

    @Test
    fun `a recorded month without spending is a real zero, not a missing one`() {
        val data = AnalyticsScenario.analytics(AnalyticsScenario.Shape.ZERO_BASELINE)

        assertTrue(data.baseline.isKnown)
        assertTrue(data.baseline.isComplete)
        assertEquals(0L, data.baseline.expenseMinor)
        // The whole month is the difference, and every category of it is its own contribution.
        assertEquals(data.expenseMinor, data.categoryChanges.sumOf { it.deltaMinor })
        assertTrue(data.categoryChanges.all { it.typicalExpenseMinor == 0L })
    }

    @Test
    fun `an empty workspace reports nothing rather than zero`() {
        val data = AnalyticsScenario.analytics(AnalyticsScenario.Shape.NONE)

        assertFalse(data.hasAnyTransactions)
        assertFalse(data.baseline.isKnown)
        assertTrue(data.categoryChanges.isEmpty())
    }

    @Test
    fun `contributions are ranked by the money they moved`() {
        val data = AnalyticsScenario.analytics(AnalyticsScenario.Shape.DEARER)

        val magnitudes = data.categoryChanges.map { abs(it.deltaMinor) }
        assertEquals(magnitudes.sortedDescending(), magnitudes)
        assertEquals(month, data.period)
    }

    @Test
    fun `movements that are not spending never become a cause`() {
        val plain = AnalyticsScenario.analytics(AnalyticsScenario.Shape.DEARER)
        val noise = AnalyticsScenario.transactions(AnalyticsScenario.Shape.DEARER) + listOf(
            // Both legs of an own transfer, a balance adjustment, and money lent to someone.
            leg(9_001, LocalDate.of(2026, 8, 12), -80_000, transferGroupId = 900),
            leg(9_002, LocalDate.of(2026, 8, 12), 80_000, transferGroupId = 900),
            adjustment(9_003, LocalDate.of(2026, 8, 13), -5_000),
            spend(9_004, LocalDate.of(2026, 8, 14), 30_000, categoryId = 3),
        )
        val allocations = listOf(
            TransactionAllocationEntity(
                id = 1,
                transactionId = 9_004,
                personId = 1,
                amountMinor = -30_000,
                purpose = AllocationPurpose.LOAN,
                categoryId = 3,
            ),
        )

        val data = calculateAnalytics(
            transactions = noise,
            categories = AnalyticsScenario.categories + systemCategory,
            allocations = allocations,
            period = AnalyticsPeriod.month(AnalyticsScenario.selectedMonth),
            trendFilter = AnalyticsTrendFilter.All,
            zoneId = AnalyticsScenario.zone,
            today = AnalyticsScenario.afterSelectedMonth,
            merchants = AnalyticsScenario.merchants,
        )

        // A transfer is not spending, an adjustment belongs to the system category, and money lent
        // is still owed: none of the three may explain why a month cost more.
        assertEquals(plain.expenseMinor, data.expenseMinor)
        assertEquals(
            plain.categoryChanges.map { it.name to it.deltaMinor },
            data.categoryChanges.map { it.name to it.deltaMinor },
        )
    }

    private val systemCategory = CategoryEntity(
        id = 99, name = "Unaccounted", kind = CategoryKind.EXPENSE, icon = "Category", color = 0,
        isSystem = true,
    )

    private fun spend(id: Long, day: LocalDate, minor: Long, categoryId: Long) = row(
        id, day, -minor, categoryId = categoryId,
    )

    private fun leg(id: Long, day: LocalDate, minor: Long, transferGroupId: Long) = row(
        id, day, minor, categoryId = null, transferGroupId = transferGroupId, isTransfer = true,
    )

    private fun adjustment(id: Long, day: LocalDate, minor: Long) = row(
        id, day, minor, categoryId = 99, source = TxSource.ADJUSTMENT,
    )

    private fun row(
        id: Long,
        day: LocalDate,
        minor: Long,
        categoryId: Long?,
        transferGroupId: Long? = null,
        isTransfer: Boolean = false,
        source: TxSource = TxSource.STATEMENT,
    ) = TransactionEntity(
        id = id,
        accountId = 1,
        amountMinor = minor,
        currency = "GEL",
        gelValueMinor = minor,
        occurredAt = day.atStartOfDay(AnalyticsScenario.zone).toInstant().toEpochMilli(),
        categoryId = categoryId,
        transferGroupId = transferGroupId,
        isTransfer = isTransfer,
        status = TxStatus.CONFIRMED,
        source = source,
    )
}
