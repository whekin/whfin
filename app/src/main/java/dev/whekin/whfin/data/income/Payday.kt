package dev.whekin.whfin.data.income

import dev.whekin.whfin.data.db.IncomeSourceEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * What the payer does when the usual day is not a working one.
 *
 * Employers are consistent about this and people know which one theirs is, so it is one choice
 * rather than a date range the owner would have to keep correct. Public holidays are deliberately
 * absent: they need a per-country calendar the app does not have, and guessing one would move the
 * estimate on days it should not.
 */
enum class WeekendRule {
    /** Weekdays only, paid before the weekend. */
    EARLIER,

    /** Weekdays only, paid after the weekend. */
    LATER,

    /** The payer does not care which day of the week it is. */
    ANY_DAY,
}

/**
 * The single day this month's payment is estimated for.
 *
 * A weekend shifts the estimate and nothing else. It never decides whether an actual payment counts:
 * money that arrived is money that arrived, whichever day the calendar said.
 */
fun IncomeSourceEntity.expectedPayday(month: YearMonth): LocalDate {
    // A day beyond the month's length is not an error in the declaration — February simply has no
    // 31st — so the estimate lands on the last day the month does have.
    val day = month.atDay(expectedDayFrom.coerceIn(1, month.lengthOfMonth()))
    return when (weekendRule) {
        WeekendRule.EARLIER -> when (day.dayOfWeek) {
            DayOfWeek.SATURDAY -> day.minusDays(1)
            DayOfWeek.SUNDAY -> day.minusDays(2)
            else -> day
        }
        WeekendRule.LATER -> when (day.dayOfWeek) {
            DayOfWeek.SATURDAY -> day.plusDays(2)
            DayOfWeek.SUNDAY -> day.plusDays(1)
            else -> day
        }
        WeekendRule.ANY_DAY -> day
    }
}
