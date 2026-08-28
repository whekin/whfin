package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.recurring.RecurringCharge
import dev.whekin.whfin.data.recurring.RecurringOccurrence
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRunwayTest {

    private val today = LocalDate.of(2026, 8, 25)

    @Test
    fun `runway divides spendable money by future ordinary spending`() {
        val runway = homeRunway(
            spendablePivotMinor = 120_000,
            ordinaryDailyMinor = 10_000,
            incomeSources = emptyList(),
            today = today,
        )

        assertNotNull(runway)
        assertEquals(12, runway!!.daysLeft)
        assertEquals(10_000L, runway.dailyBurnMinor)
        assertNull(runway.nextIncome)
        assertFalse(runway.shortOfIncome)
    }

    @Test
    fun `a month too young to project says nothing`() {
        assertNull(
            homeRunway(
                spendablePivotMinor = 120_000,
                ordinaryDailyMinor = null,
                incomeSources = emptyList(),
                today = today,
            ),
        )
    }

    @Test
    fun `missing spendable value stays unavailable`() {
        assertNull(
            homeRunway(
                spendablePivotMinor = null,
                ordinaryDailyMinor = 10_000,
                incomeSources = emptyList(),
                today = today,
            ),
        )
    }

    @Test
    fun `a comfortable runway stays quiet`() {
        assertNull(
            homeRunway(
                spendablePivotMinor = 2_000_000,
                ordinaryDailyMinor = 10_000,
                incomeSources = emptyList(),
                today = today,
            ),
        )
    }

    @Test
    fun `money that runs out before the declared window closes is called short`() {
        val runway = homeRunway(
            spendablePivotMinor = 40_000,
            ordinaryDailyMinor = 10_000,
            incomeSources = listOf(source(expectedDayFrom = 5, expectedDayTo = 10)),
            today = today,
        )

        assertNotNull(runway)
        assertEquals(4, runway!!.daysLeft)
        assertTrue(runway.shortOfIncome)
        assertEquals(LocalDate.of(2026, 9, 5), runway.nextIncome?.from)
        assertEquals(LocalDate.of(2026, 9, 10), runway.nextIncome?.to)
    }

    @Test
    fun `a regular large payment is charged on its due day instead of diluted into daily burn`() {
        val paydayToday = LocalDate.of(2026, 8, 25)
        val rent = RecurringOccurrence(
            charge = RecurringCharge(
                key = "iban:landlord",
                label = "Landlord",
                typicalMinor = 120_000,
                expectedDay = 3,
                lastSeen = LocalDate.of(2026, 8, 3),
            ),
            dueDate = LocalDate.of(2026, 9, 3),
        )

        val runway = homeRunway(
            spendablePivotMinor = 150_000,
            ordinaryDailyMinor = 10_000,
            incomeSources = listOf(source(expectedDayFrom = 5, expectedDayTo = 10)),
            recurringOccurrences = listOf(rent),
            today = paydayToday,
        )

        assertNotNull(runway)
        assertEquals(10_000L, runway!!.dailyBurnMinor)
        assertEquals(9, runway.daysLeft)
        assertEquals(130_000L, runway.shortfallMinor)
        assertEquals(listOf(rent), runway.recurringOccurrences)
    }

    @Test
    fun `an open window is the answer until it closes`() {
        val runway = homeRunway(
            spendablePivotMinor = 40_000,
            ordinaryDailyMinor = 10_000,
            incomeSources = listOf(source(expectedDayFrom = 20, expectedDayTo = 27)),
            today = today,
        )

        assertEquals(LocalDate.of(2026, 8, 20), runway?.nextIncome?.from)
        assertEquals(LocalDate.of(2026, 8, 27), runway?.nextIncome?.to)
    }

    @Test
    fun `a source whose era ended does not promise a payday`() {
        val runway = homeRunway(
            spendablePivotMinor = 40_000,
            ordinaryDailyMinor = 10_000,
            incomeSources = listOf(
                source(
                    expectedDayFrom = 5,
                    expectedDayTo = 10,
                    endedOn = LocalDate.of(2026, 6, 30).toEpochDay(),
                ),
            ),
            today = today,
        )

        assertNull(runway?.nextIncome)
        assertFalse(runway!!.shortOfIncome)
    }

    @Test
    fun `a month-end window is clamped to a day the month actually has`() {
        val runway = homeRunway(
            spendablePivotMinor = 40_000,
            ordinaryDailyMinor = 10_000,
            incomeSources = listOf(source(expectedDayFrom = 30, expectedDayTo = 31)),
            today = LocalDate.of(2026, 2, 25),
        )

        assertEquals(LocalDate.of(2026, 2, 28), runway?.nextIncome?.from)
        assertEquals(LocalDate.of(2026, 2, 28), runway?.nextIncome?.to)
    }

    @Test fun `no deficit at the exact horizon cost and a deficit one minor unit below`() {
        fun reading(balance: Long) = homeRunway(balance, 10_000,
            listOf(source(5, 10)), today)
        assertFalse(reading(160_000)!!.shortOfIncome)
        assertEquals(0L, reading(160_000)!!.remainingMinor)
        assertEquals(1L, reading(159_999)!!.shortfallMinor)
    }

    @Test fun `comfortable money answers payday without claiming an unbounded number of days`() {
        val result = homeRunway(2_000_000, 10_000, listOf(source(5, 10)), today)!!
        assertFalse(result.shortOfIncome)
        assertEquals(1_840_000L, result.remainingMinor)
    }

    @Test fun `zero and negative balances are not silently comfortable`() {
        assertEquals(160_000L, homeRunway(0, 10_000, listOf(source(5, 10)), today)!!.shortfallMinor)
        assertEquals(170_000L, homeRunway(-10_000, 10_000, listOf(source(5, 10)), today)!!.shortfallMinor)
    }

    @Test fun `multiple same day bills and an overdue bill consume cash once each`() {
        val bill = RecurringCharge("merchant:1", "Bill", 5_000, 1, today.minusMonths(1))
        val result = homeRunway(30_000, 1_000, listOf(source(5, 10)), today,
            listOf(RecurringOccurrence(bill, today.minusDays(1)),
                RecurringOccurrence(bill, today.plusDays(1)),
                RecurringOccurrence(bill.copy(key = "merchant:2"), today.plusDays(1))))!!
        assertEquals(31_000L, result.expectedExpenseMinor)
        assertEquals(1_000L, result.shortfallMinor)
        assertEquals(15, result.daysLeft)
    }

    @Test fun `bill after payday is not an expense before payday`() {
        val bill = RecurringCharge("merchant:1", "Bill", 500_000, 20, today.minusMonths(1))
        val result = homeRunway(200_000, 10_000, listOf(source(5, 10)), today,
            listOf(RecurringOccurrence(bill, LocalDate.of(2026, 9, 20))))!!
        assertEquals(160_000L, result.expectedExpenseMinor)
        assertTrue(result.recurringOccurrences.isEmpty())
    }

    @Test fun `overflow cannot produce a negative expense or a comfortable gap`() {
        val result = homeRunway(100, Long.MAX_VALUE, listOf(source(5, 10)), today)!!
        assertTrue(result.shortOfIncome)
        assertTrue(result.expectedExpenseMinor!! > 0)
        assertTrue(result.shortfallMinor!! > 0)
    }

    @Test fun `zero daily rate can still forecast a bill without division by zero`() {
        val bill = RecurringCharge("merchant:1", "Bill", 500_000, 3, today.minusMonths(1))
        val result = homeRunway(100_000, 0, listOf(source(5, 10)), today,
            listOf(RecurringOccurrence(bill, today.plusDays(2))))!!
        assertEquals(2, result.daysLeft)
        assertEquals(400_000L, result.shortfallMinor)
    }

    private fun source(
        expectedDayFrom: Int,
        expectedDayTo: Int,
        endedOn: Long? = null,
    ) = IncomeSourceEntity(
        id = 1,
        label = "Salary",
        amountMinor = 500_000,
        currency = "GEL",
        accountId = 1,
        expectedDayFrom = expectedDayFrom,
        expectedDayTo = expectedDayTo,
        startedOn = LocalDate.of(2025, 1, 1).toEpochDay(),
        endedOn = endedOn,
        createdAt = 0,
    )


}
