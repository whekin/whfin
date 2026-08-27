package dev.whekin.whfin.data.savings

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SavingsProjectionTest {
    private val today = LocalDate.of(2026, 8, 27)

    @Test fun `monthly plan grows reserve without any goal`() {
        val result = projectSavings(100_000, 10_000, today)
        assertEquals(13, result.points.size)
        assertEquals(100_000L, result.points.first().balanceMinor)
        assertEquals(220_000L, result.points.last().balanceMinor)
        assertEquals(today.plusYears(1), result.points.last().date)
        assertNull(result.goalReachedOn)
    }

    @Test fun `goal date rounds up contributions and changes live with pace`() {
        assertEquals(today.plusMonths(3), projectSavings(100, 40, today, 201).goalReachedOn)
        assertEquals(today.plusMonths(2), projectSavings(100, 80, today, 201).goalReachedOn)
        assertEquals(today, projectSavings(250, 40, today, 201).goalReachedOn)
        assertNull(projectSavings(100, 0, today, 201).goalReachedOn)
    }

    @Test fun `selected deadline and required pace use the same exact schedule`() {
        val target = today.plusMonths(3)
        val result = projectSavings(100, 20, today, 201, target)
        assertEquals(160L, result.balanceOnTargetDateMinor)
        assertEquals(34L, result.requiredMonthlyMinor)
        assertEquals(target, projectSavings(100, result.requiredMonthlyMinor!!, today, 201).goalReachedOn)
        assertEquals(120L, projectSavings(100, 20, today, 201, today.plusMonths(2).minusDays(1)).balanceOnTargetDateMinor)
        assertNull(projectSavings(100, 20, today, 201, today.plusDays(1)).requiredMonthlyMinor)
    }

    @Test fun `month end and leap years use anniversary dates without drift`() {
        val jan = LocalDate.of(2028, 1, 31)
        val result = projectSavings(0, 100, jan, 200, LocalDate.of(2028, 2, 29))
        assertEquals(100L, result.balanceOnTargetDateMinor)
        assertEquals(LocalDate.of(2028, 3, 31), result.goalReachedOn)
        assertEquals(0L, contributionCount(jan, LocalDate.of(2028, 2, 28)))
    }

    @Test fun `past deadline and overflowing money are not fabricated`() {
        assertNull(projectSavings(100, 20, today, 201, today.minusDays(1)).balanceOnTargetDateMinor)
        val result = projectSavings(Long.MAX_VALUE - 1, 100, today)
        assertTrue(result.exceedsMoneyRange)
        assertEquals(1, result.points.size)
        assertEquals(Long.MAX_VALUE - 1, result.points.first().balanceMinor)
    }
}
