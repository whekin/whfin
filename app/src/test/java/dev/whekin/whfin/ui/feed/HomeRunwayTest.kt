package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.recurring.RecurringCharge
import dev.whekin.whfin.data.recurring.RecurringOccurrence
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRunwayTest {

    private val today = LocalDate.of(2026, 8, 25)

    @Test
    fun `normal payday drives the main forecast while the latest day stays a fallback`() {
        val runway = homeRunway(
            spendablePivotMinor = 80_000,
            ordinaryDailyMinor = 9_000,
            incomeSources = listOf(source(expectedDayFrom = 5, expectedDayTo = 10)),
            today = LocalDate.of(2026, 8, 28),
        )!!

        // The nominal 5 September is Saturday, so Monday the 7th is the conservative normal case.
        assertEquals(LocalDate.of(2026, 9, 7), runway.nextIncome?.expected)
        assertEquals(90_000L, runway.expectedExpenseMinor)
        assertEquals(10_000L, runway.shortfallMinor)
        assertEquals(117_000L, runway.deadlineExpectedExpenseMinor)
        assertEquals(37_000L, runway.deadlineShortfallMinor)
    }

    @Test
    fun `weekday usual payday is not shifted`() {
        val window = nextIncomeWindow(
            listOf(source(expectedDayFrom = 4, expectedDayTo = 10)),
            LocalDate.of(2026, 8, 28),
        )!!

        assertEquals(LocalDate.of(2026, 9, 4), window.usual)
        assertEquals(window.usual, window.expected)
        assertFalse(window.weekendAdjusted)
    }

    @Test
    fun `sunday usual payday moves to monday`() {
        val window = nextIncomeWindow(
            listOf(source(expectedDayFrom = 6, expectedDayTo = 10)),
            LocalDate.of(2026, 8, 28),
        )!!

        assertEquals(LocalDate.of(2026, 9, 6), window.usual)
        assertEquals(LocalDate.of(2026, 9, 7), window.expected)
        assertTrue(window.weekendAdjusted)
    }

    @Test
    fun `weekend adjustment never promises money after the declared deadline`() {
        val window = nextIncomeWindow(
            listOf(source(expectedDayFrom = 5, expectedDayTo = 6)),
            LocalDate.of(2026, 8, 28),
        )!!

        assertEquals(LocalDate.of(2026, 9, 6), window.expected)
        assertEquals(window.deadline, window.expected)
        assertFalse(window.weekendAdjusted)
        assertTrue(window.usingDeadline)
    }

    @Test
    fun `an arrived source skips its current window but keeps next month`() {
        val august = YearMonth.of(2026, 8)
        val window = nextIncomeWindow(
            listOf(source(expectedDayFrom = 5, expectedDayTo = 10)),
            LocalDate.of(2026, 8, 7),
            setOf(1L to august),
        )!!

        assertEquals(LocalDate.of(2026, 9, 5), window.usual)
        assertEquals(LocalDate.of(2026, 9, 7), window.expected)
    }

    @Test
    fun `a source that starts after its usual day does not promise a special first payment`() {
        val source = source(expectedDayFrom = 5, expectedDayTo = 10).copy(
            startedOn = LocalDate.of(2026, 9, 8).toEpochDay(),
        )

        val window = nextIncomeWindow(listOf(source), LocalDate.of(2026, 9, 1))!!

        assertEquals(LocalDate.of(2026, 10, 5), window.usual)
    }

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
        assertEquals(LocalDate.of(2026, 9, 5), runway.nextIncome?.usual)
        assertEquals(LocalDate.of(2026, 9, 7), runway.nextIncome?.expected)
        assertEquals(LocalDate.of(2026, 9, 10), runway.nextIncome?.deadline)
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
        assertEquals(100_000L, runway.shortfallMinor)
        assertEquals(130_000L, runway.deadlineShortfallMinor)
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

        assertEquals(LocalDate.of(2026, 8, 27), runway?.nextIncome?.expected)
        assertTrue(runway?.nextIncome?.usingDeadline == true)
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

        assertEquals(LocalDate.of(2026, 2, 28), runway?.nextIncome?.usual)
        assertEquals(LocalDate.of(2026, 2, 28), runway?.nextIncome?.expected)
        assertEquals(LocalDate.of(2026, 2, 28), runway?.nextIncome?.deadline)
    }

    @Test fun `no deficit at the exact horizon cost and a deficit one minor unit below`() {
        fun reading(balance: Long) = homeRunway(balance, 10_000,
            listOf(source(5, 10)), today)
        assertFalse(reading(130_000)!!.shortOfIncome)
        assertEquals(0L, reading(130_000)!!.remainingMinor)
        assertEquals(1L, reading(129_999)!!.shortfallMinor)
        assertEquals(30_000L, reading(130_000)!!.deadlineShortfallMinor)
    }

    @Test fun `comfortable money answers payday without claiming an unbounded number of days`() {
        val result = homeRunway(2_000_000, 10_000, listOf(source(5, 10)), today)!!
        assertFalse(result.shortOfIncome)
        assertEquals(1_870_000L, result.remainingMinor)
        assertEquals(1_840_000L, result.deadlineRemainingMinor)
    }

    @Test fun `zero and negative balances are not silently comfortable`() {
        assertEquals(130_000L, homeRunway(0, 10_000, listOf(source(5, 10)), today)!!.shortfallMinor)
        assertEquals(140_000L, homeRunway(-10_000, 10_000, listOf(source(5, 10)), today)!!.shortfallMinor)
    }

    @Test fun `multiple same day bills and an overdue bill consume cash once each`() {
        val bill = RecurringCharge("merchant:1", "Bill", 5_000, 1, today.minusMonths(1))
        val result = homeRunway(30_000, 1_000, listOf(source(5, 10)), today,
            listOf(RecurringOccurrence(bill, today.minusDays(1)),
                RecurringOccurrence(bill, today.plusDays(1)),
                RecurringOccurrence(bill.copy(key = "merchant:2"), today.plusDays(1))))!!
        assertEquals(28_000L, result.expectedExpenseMinor)
        assertEquals(2_000L, result.remainingMinor)
        assertEquals(1_000L, result.deadlineShortfallMinor)
        assertEquals(15, result.daysLeft)
    }

    @Test fun `bill after payday is not an expense before payday`() {
        val bill = RecurringCharge("merchant:1", "Bill", 500_000, 20, today.minusMonths(1))
        val result = homeRunway(200_000, 10_000, listOf(source(5, 10)), today,
            listOf(RecurringOccurrence(bill, LocalDate.of(2026, 9, 20))))!!
        assertEquals(130_000L, result.expectedExpenseMinor)
        assertEquals(160_000L, result.deadlineExpectedExpenseMinor)
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
