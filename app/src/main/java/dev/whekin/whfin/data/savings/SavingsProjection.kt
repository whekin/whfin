package dev.whekin.whfin.data.savings

import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class SavingsProjectionPoint(val date: LocalDate, val balanceMinor: Long)

data class SavingsProjection(
    val points: List<SavingsProjectionPoint>,
    val goalReachedOn: LocalDate?,
    val balanceOnTargetDateMinor: Long?,
    val requiredMonthlyMinor: Long?,
    val exceedsMoneyRange: Boolean,
)

/**
 * A scenario, never a scheduled bank operation. The current balance is the anchor, with the first
 * equal contribution one month from [today]. No current-month contribution is counted twice and
 * no interest, market return or withdrawal is invented. All calculations retain exact minor units.
 */
fun projectSavings(
    balanceMinor: Long,
    monthlyMinor: Long,
    today: LocalDate,
    goalMinor: Long? = null,
    targetDate: LocalDate? = null,
    horizonMonths: Int = 12,
): SavingsProjection {
    require(monthlyMinor >= 0)
    require(horizonMonths in 1..360)
    val balance = BigInteger.valueOf(balanceMinor)
    val monthly = BigInteger.valueOf(monthlyMinor)
    val gap = goalMinor?.let { (BigInteger.valueOf(it) - balance).max(BigInteger.ZERO) }
    fun amountAfter(months: Long): Long? = (balance + monthly * BigInteger.valueOf(months)).toMoneyOrNull()
    val points = buildList {
        add(SavingsProjectionPoint(today, balanceMinor))
        for (month in 1..horizonMonths) {
            val amount = amountAfter(month.toLong()) ?: break
            val date = runCatching { today.plusMonths(month.toLong()) }.getOrNull() ?: break
            add(SavingsProjectionPoint(date, amount))
        }
    }
    val targetMonths = targetDate?.takeIf { it >= today }?.let { contributionCount(today, it) }
    val reachedOn = when {
        gap == null -> null
        gap == BigInteger.ZERO -> today
        monthlyMinor == 0L -> null
        else -> runCatching { today.plusMonths(gap.ceilDivide(monthly).longValueExact()) }.getOrNull()
    }
    return SavingsProjection(
        points = points,
        goalReachedOn = reachedOn,
        balanceOnTargetDateMinor = targetMonths?.let(::amountAfter),
        requiredMonthlyMinor = when {
            gap == null || targetMonths == null -> null
            gap == BigInteger.ZERO -> 0L
            targetMonths == 0L -> null
            else -> gap.ceilDivide(BigInteger.valueOf(targetMonths)).toMoneyOrNull()
        },
        exceedsMoneyRange = points.size != horizonMonths + 1 ||
            (targetMonths != null && amountAfter(targetMonths) == null),
    )
}

/** Unlike ChronoUnit.MONTHS on LocalDate, Jan 31 → Feb 28 is one contribution, not zero. */
internal fun contributionCount(today: LocalDate, date: LocalDate): Long {
    if (date < today) return 0
    val months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(date))
    return if (today.plusMonths(months) > date) (months - 1).coerceAtLeast(0) else months
}

private fun BigInteger.ceilDivide(divisor: BigInteger): BigInteger =
    (this + divisor - BigInteger.ONE) / divisor

private fun BigInteger.toMoneyOrNull(): Long? = runCatching { longValueExact() }.getOrNull()
