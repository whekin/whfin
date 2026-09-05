package dev.whekin.whfin.ui.feed

import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.income.WeekendRule
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

    /** August is settled, so the reading looks forward to the September estimate. */
    private val augustPaid = setOf(1L to YearMonth.of(2026, 8))

    @Test
    fun `the single estimate drives the forecast`() {
        val runway = homeRunway(
            spendablePivotMinor = 55_000,
            ordinaryDailyMinor = 9_000,
            incomeSources = listOf(source(day = 5)),
            today = LocalDate.of(2026, 8, 28),
            arrivedSourceMonths = augustPaid,
        )!!

        // 5 September is a Saturday and this payer pays before the weekend: Friday the 4th.
        assertEquals(LocalDate.of(2026, 9, 4), runway.nextIncome?.expected)
        assertEquals(63_000L, runway.expectedExpenseMinor)
        assertEquals(8_000L, runway.shortfallMinor)
    }

    @Test
    fun `a weekday payday is not shifted by any rule`() {
        WeekendRule.entries.forEach { rule ->
            val payday = nextPayday(listOf(source(day = 4, rule = rule)), LocalDate.of(2026, 9, 1))!!

            assertEquals(LocalDate.of(2026, 9, 4), payday.usual)
            assertEquals(payday.usual, payday.expected)
            assertFalse(payday.weekendAdjusted)
        }
    }

    @Test
    fun `earlier pulls a saturday back one day and a sunday back two`() {
        val saturday = nextPayday(listOf(source(day = 5)), LocalDate.of(2026, 9, 1))!!
        val sunday = nextPayday(listOf(source(day = 6)), LocalDate.of(2026, 9, 1))!!

        assertEquals(LocalDate.of(2026, 9, 4), saturday.expected)
        assertEquals(LocalDate.of(2026, 9, 4), sunday.expected)
        assertTrue(saturday.weekendAdjusted)
        assertTrue(sunday.weekendAdjusted)
    }

    @Test
    fun `later pushes both weekend days to monday`() {
        val saturday = nextPayday(
            listOf(source(day = 5, rule = WeekendRule.LATER)), LocalDate.of(2026, 9, 1),
        )!!
        val sunday = nextPayday(
            listOf(source(day = 6, rule = WeekendRule.LATER)), LocalDate.of(2026, 9, 1),
        )!!

        assertEquals(LocalDate.of(2026, 9, 7), saturday.expected)
        assertEquals(LocalDate.of(2026, 9, 7), sunday.expected)
    }

    @Test
    fun `any day keeps the weekend date it was given`() {
        val payday = nextPayday(
            listOf(source(day = 6, rule = WeekendRule.ANY_DAY)), LocalDate.of(2026, 9, 1),
        )!!

        assertEquals(LocalDate.of(2026, 9, 6), payday.expected)
        assertFalse(payday.weekendAdjusted)
    }

    /** Pulling the estimate earlier can cross into the previous month; the date must survive it. */
    @Test
    fun `an earlier shift crosses the month boundary`() {
        // 1 November 2026 is a Sunday, so paying before the weekend lands on 30 October.
        val payday = nextPayday(
            listOf(source(day = 1)), LocalDate.of(2026, 10, 20), setOf(1L to YearMonth.of(2026, 10)),
        )!!

        assertEquals(LocalDate.of(2026, 11, 1), payday.usual)
        assertEquals(LocalDate.of(2026, 10, 30), payday.expected)
    }

    /** Pushing later can cross the other way, into the month after the usual one. */
    @Test
    fun `a later shift crosses into the next month`() {
        // 31 January 2027 is a Sunday; paying after the weekend lands on 1 February.
        val payday = nextPayday(
            listOf(source(day = 31, rule = WeekendRule.LATER)), LocalDate.of(2027, 1, 20),
        )!!

        assertEquals(LocalDate.of(2027, 1, 31), payday.usual)
        assertEquals(LocalDate.of(2027, 2, 1), payday.expected)
    }

    /** A day the month does not have is not an error in the declaration. */
    @Test
    fun `short months clamp the day they were given`() {
        fun expected(month: YearMonth) =
            nextPayday(listOf(source(day = 31)), month.atDay(1))!!

        // February 2027 ends on Sunday the 28th, pulled back to Friday the 26th.
        assertEquals(LocalDate.of(2027, 2, 28), expected(YearMonth.of(2027, 2)).usual)
        assertEquals(LocalDate.of(2027, 2, 26), expected(YearMonth.of(2027, 2)).expected)
        // 2028 is a leap year: the 29th exists and is a Tuesday.
        assertEquals(LocalDate.of(2028, 2, 29), expected(YearMonth.of(2028, 2)).expected)
        // April has 30 days; 30 April 2026 is a Thursday.
        assertEquals(LocalDate.of(2026, 4, 30), expected(YearMonth.of(2026, 4)).expected)
    }

    @Test
    fun `an arrived source skips its current month but keeps the next one`() {
        val payday = nextPayday(
            listOf(source(day = 5)),
            LocalDate.of(2026, 8, 7),
            setOf(1L to YearMonth.of(2026, 8)),
        )!!

        assertEquals(LocalDate.of(2026, 9, 4), payday.expected)
    }

    /**
     * A payday a few days late is still the payment being waited for. Rolling straight on to next
     * month would hide the one thing the owner actually wants to know.
     */
    @Test
    fun `a passed estimate stays the answer and says it is waiting`() {
        val payday = nextPayday(listOf(source(day = 5)), LocalDate.of(2026, 8, 12))!!

        assertEquals(LocalDate.of(2026, 8, 5), payday.expected)
        assertTrue(payday.passed)
    }

    /** Nothing is promised past a date that already went by: the next payday is not known yet. */
    @Test
    fun `a passed estimate forecasts no shortfall and draws no rule`() {
        val runway = homeRunway(
            spendablePivotMinor = 40_000,
            ordinaryDailyMinor = 10_000,
            incomeSources = listOf(source(day = 5)),
            today = LocalDate.of(2026, 8, 12),
        )!!

        assertTrue(runway.nextIncome!!.passed)
        assertNull(runway.expectedExpenseMinor)
        assertNull(runway.shortfallMinor)
        assertFalse(runway.shortOfIncome)
        assertEquals(4, runway.daysLeft)
        assertNull(runwayShape(runway, LocalDate.of(2026, 8, 12)))
    }

    /** Bills do not stop arriving because a payday is late, so they stay inside the horizon. */
    @Test
    fun `bills still count while a late payday is waited on`() {
        val bill = RecurringCharge("merchant:1", "Bill", 20_000, 20, LocalDate.of(2026, 7, 20))
        val runway = homeRunway(
            spendablePivotMinor = 100_000,
            ordinaryDailyMinor = 1_000,
            incomeSources = listOf(source(day = 5)),
            today = LocalDate.of(2026, 8, 12),
            recurringOccurrences = listOf(RecurringOccurrence(bill, LocalDate.of(2026, 8, 20))),
        )!!

        assertEquals(1, runway.recurringOccurrences.size)
        assertEquals(80, runway.daysLeft)
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
    fun `money that runs out before the payday is called short`() {
        val runway = homeRunway(
            spendablePivotMinor = 40_000,
            ordinaryDailyMinor = 10_000,
            incomeSources = listOf(source(day = 5)),
            today = today,
            arrivedSourceMonths = augustPaid,
        )

        assertNotNull(runway)
        assertEquals(4, runway!!.daysLeft)
        assertTrue(runway.shortOfIncome)
        assertEquals(LocalDate.of(2026, 9, 5), runway.nextIncome?.usual)
        assertEquals(LocalDate.of(2026, 9, 4), runway.nextIncome?.expected)
    }

    @Test
    fun `a regular large payment is charged on its due day instead of diluted into daily burn`() {
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
            incomeSources = listOf(source(day = 5)),
            recurringOccurrences = listOf(rent),
            today = LocalDate.of(2026, 8, 25),
            arrivedSourceMonths = augustPaid,
        )

        assertNotNull(runway)
        assertEquals(10_000L, runway!!.dailyBurnMinor)
        assertEquals(9, runway.daysLeft)
        assertEquals(70_000L, runway.shortfallMinor)
        assertEquals(listOf(rent), runway.recurringOccurrences)
    }

    @Test
    fun `a source that starts after its usual day does not promise a special first payment`() {
        val source = source(day = 5).copy(startedOn = LocalDate.of(2026, 9, 8).toEpochDay())

        val payday = nextPayday(listOf(source), LocalDate.of(2026, 9, 1))!!

        assertEquals(LocalDate.of(2026, 10, 5), payday.usual)
    }

    @Test
    fun `a source whose era ended does not promise a payday`() {
        val runway = homeRunway(
            spendablePivotMinor = 40_000,
            ordinaryDailyMinor = 10_000,
            incomeSources = listOf(source(day = 5, endedOn = LocalDate.of(2026, 6, 30).toEpochDay())),
            today = today,
        )

        assertNull(runway?.nextIncome)
        assertFalse(runway!!.shortOfIncome)
    }

    @Test fun `no deficit at the exact horizon cost and a deficit one minor unit below`() {
        fun reading(balance: Long) =
            homeRunway(balance, 10_000, listOf(source(day = 5)), today, arrivedSourceMonths = augustPaid)
        assertFalse(reading(100_000)!!.shortOfIncome)
        assertEquals(0L, reading(100_000)!!.remainingMinor)
        assertEquals(1L, reading(99_999)!!.shortfallMinor)
    }

    @Test fun `comfortable money answers payday without claiming an unbounded number of days`() {
        val result = homeRunway(2_000_000, 10_000, listOf(source(day = 5)), today,
            arrivedSourceMonths = augustPaid)!!
        assertFalse(result.shortOfIncome)
        assertEquals(1_900_000L, result.remainingMinor)
    }

    @Test fun `zero and negative balances are not silently comfortable`() {
        assertEquals(100_000L, homeRunway(0, 10_000, listOf(source(day = 5)), today,
            arrivedSourceMonths = augustPaid)!!.shortfallMinor)
        assertEquals(110_000L, homeRunway(-10_000, 10_000, listOf(source(day = 5)), today,
            arrivedSourceMonths = augustPaid)!!.shortfallMinor)
    }

    @Test fun `multiple same day bills and an overdue bill consume cash once each`() {
        val bill = RecurringCharge("merchant:1", "Bill", 5_000, 1, today.minusMonths(1))
        val result = homeRunway(30_000, 1_000, listOf(source(day = 5)), today,
            listOf(RecurringOccurrence(bill, today.minusDays(1)),
                RecurringOccurrence(bill, today.plusDays(1)),
                RecurringOccurrence(bill.copy(key = "merchant:2"), today.plusDays(1))),
            arrivedSourceMonths = augustPaid)!!
        assertEquals(25_000L, result.expectedExpenseMinor)
        assertEquals(5_000L, result.remainingMinor)
        assertEquals(15, result.daysLeft)
    }

    @Test fun `bill after payday is not an expense before payday`() {
        val bill = RecurringCharge("merchant:1", "Bill", 500_000, 20, today.minusMonths(1))
        val result = homeRunway(200_000, 10_000, listOf(source(day = 5)), today,
            listOf(RecurringOccurrence(bill, LocalDate.of(2026, 9, 20))),
            arrivedSourceMonths = augustPaid)!!
        assertEquals(100_000L, result.expectedExpenseMinor)
        assertTrue(result.recurringOccurrences.isEmpty())
    }

    @Test fun `overflow cannot produce a negative expense or a comfortable gap`() {
        val result = homeRunway(100, Long.MAX_VALUE, listOf(source(day = 5)), today,
            arrivedSourceMonths = augustPaid)!!
        assertTrue(result.shortOfIncome)
        assertTrue(result.expectedExpenseMinor!! > 0)
        assertTrue(result.shortfallMinor!! > 0)
    }

    @Test fun `zero daily rate can still forecast a bill without division by zero`() {
        val bill = RecurringCharge("merchant:1", "Bill", 500_000, 3, today.minusMonths(1))
        val result = homeRunway(100_000, 0, listOf(source(day = 5)), today,
            listOf(RecurringOccurrence(bill, today.plusDays(2))),
            arrivedSourceMonths = augustPaid)!!
        assertEquals(2, result.daysLeft)
        assertEquals(400_000L, result.shortfallMinor)
    }

    /**
     * The card draws one window: today at its start, and whichever comes last at its end. The
     * filled part is the money, so the mark on it is the day it runs out and the gap after it is
     * the shortfall the title names.
     */
    @Test
    fun theWindowRunsToWhicheverComesLastAndTheFilledPartIsTheMoney() {
        val today = LocalDate.of(2026, 8, 28)
        val runway = HomeRunway(
            daysLeft = 6,
            dailyBurnMinor = 7_200,
            nextIncome = NextPayday(
                usual = LocalDate.of(2026, 9, 5),
                expected = LocalDate.of(2026, 9, 4),
                weekendAdjusted = true,
            ),
            shortOfIncome = true,
            shortfallMinor = 5_608,
        )

        val shape = runwayShape(runway, today)!!

        // 7 days from today to the payday; the money covers six of them.
        assertEquals(6f / 7f, shape.fundedFraction, 0.001f)
        assertEquals(1f, shape.paydayFraction, 0.001f)
        assertEquals(LocalDate.of(2026, 9, 3), shape.runsOut)
    }

    @Test
    fun moneyOutlastingTheWindowFillsItAndKeepsThePaydayInside() {
        val today = LocalDate.of(2026, 8, 28)
        val runway = HomeRunway(
            daysLeft = 20,
            dailyBurnMinor = 7_200,
            nextIncome = NextPayday(
                usual = LocalDate.of(2026, 9, 7),
                expected = LocalDate.of(2026, 9, 7),
                weekendAdjusted = false,
            ),
            shortOfIncome = false,
        )

        val shape = runwayShape(runway, today)!!

        assertEquals(1f, shape.fundedFraction, 0.001f)
        assertEquals(10f / 20f, shape.paydayFraction, 0.001f)
    }

    /** Without a declared payday there is no window to divide, and the card keeps its sentence. */
    @Test
    fun noDeclaredPaydayLeavesNothingToDraw() {
        val runway = HomeRunway(daysLeft = 12, dailyBurnMinor = 7_200, nextIncome = null, shortOfIncome = false)

        assertEquals(null, runwayShape(runway, LocalDate.of(2026, 8, 28)))
    }

    private fun source(
        day: Int,
        rule: WeekendRule = WeekendRule.EARLIER,
        endedOn: Long? = null,
    ) = IncomeSourceEntity(
        id = 1,
        label = "Salary",
        amountMinor = 500_000,
        currency = "GEL",
        accountId = 1,
        expectedDayFrom = day,
        expectedDayTo = day,
        weekendRule = rule,
        startedOn = LocalDate.of(2025, 1, 1).toEpochDay(),
        endedOn = endedOn,
        createdAt = 0,
    )
}
