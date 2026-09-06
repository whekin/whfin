package dev.whekin.whfin.ui.analytics

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * A few months of invented money, shaped so the analytics screens can be read rather than admired.
 *
 * Every name and amount here is made up. The point of the shape is that the answer is not the one a
 * list of totals would give: rent is by far the largest category and explains none of the
 * difference, the two categories that do explain it are small ones that grew, one category fell,
 * and one stopped altogether. A screen that merely ranked spending would name rent; a screen that
 * explains a difference must not.
 *
 * The baselines are also deliberately indivisible by three, so the parts can only add up to the
 * whole if the units lost to integer division are handed out rather than dropped.
 */
internal object AnalyticsScenario {

    val zone: ZoneOffset = ZoneOffset.UTC

    private const val RENT = 1L
    private const val GROCERIES = 2L
    private const val EATING = 3L
    private const val HEALTH = 4L
    private const val BIKE = 5L
    private const val TRAVEL = 6L
    private const val SALARY = 7L

    val categories: List<CategoryEntity> = listOf(
        CategoryEntity(RENT, "Rent", kind = CategoryKind.EXPENSE, icon = "Home", color = 0xff8d7b5a.toInt()),
        CategoryEntity(GROCERIES, "Groceries", kind = CategoryKind.EXPENSE, icon = "ShoppingCart", color = 0xff4f725f.toInt()),
        CategoryEntity(EATING, "Eating out", kind = CategoryKind.EXPENSE, icon = "Restaurant", color = 0xffc96d4f.toInt()),
        CategoryEntity(HEALTH, "Health", kind = CategoryKind.EXPENSE, icon = "MedicalServices", color = 0xff9c5f6d.toInt()),
        CategoryEntity(BIKE, "Bike", kind = CategoryKind.EXPENSE, icon = "DirectionsBus", color = 0xff788a67.toInt()),
        CategoryEntity(TRAVEL, "Travel", kind = CategoryKind.EXPENSE, icon = "Flight", color = 0xff5c7f96.toInt()),
        CategoryEntity(SALARY, "Salary", kind = CategoryKind.INCOME, icon = "Payments", color = 0xff4f725f.toInt()),
    )

    val merchants: List<MerchantEntity> = listOf(
        MerchantEntity(1, "quiet dental", "Quiet Street Dental", HEALTH),
        MerchantEntity(2, "spoke workshop", "Spoke Workshop", BIKE),
        MerchantEntity(3, "juniper market", "Juniper Market", GROCERIES),
        MerchantEntity(4, "demo landlord", "Demo Landlord", RENT),
    )

    /** The month the scenario is about; May, June and July before it are the ordinary ones. */
    val selectedMonth: YearMonth = YearMonth.of(2026, 8)

    /** Everything of the selected month has happened by then, so it reads as a finished month. */
    val afterSelectedMonth: LocalDate = LocalDate.of(2026, 9, 4)

    /** Part-way through the selected month, where every base is cut to the same stretch of days. */
    val insideSelectedMonth: LocalDate = LocalDate.of(2026, 8, 10)

    enum class Shape {
        /** Three ordinary months, then one that costs more for two nameable reasons. */
        DEARER,

        /** The same history with a quiet month at the end: a difference can point downwards. */
        CHEAPER,

        /** Only one of the three preceding months was ever imported. */
        SHORT_HISTORY,

        /** Nothing before the selected month, so there is no ordinary level to speak of. */
        NO_HISTORY,

        /** Earlier months exist and hold income only: a real zero to compare against. */
        ZERO_BASELINE,

        /** No records at all. */
        NONE,
    }

    fun transactions(shape: Shape): List<TransactionEntity> {
        var id = 0L
        val rows = mutableListOf<TransactionEntity>()
        fun spend(month: Int, day: Int, minor: Long, category: Long, merchant: Long? = null) {
            if (minor == 0L) return
            rows += expense(++id, LocalDate.of(2026, month, day), minor, category, merchant)
        }
        fun earn(month: Int, day: Int, minor: Long) {
            rows += income(++id, LocalDate.of(2026, month, day), minor)
        }

        if (shape == Shape.NONE) return emptyList()

        val ordinary = when (shape) {
            Shape.NO_HISTORY -> emptyList()
            Shape.SHORT_HISTORY -> listOf(7)
            else -> listOf(5, 6, 7)
        }
        ordinary.forEach { month ->
            earn(month, 5, 420_000)
            if (shape == Shape.ZERO_BASELINE) return@forEach
            spend(month, 3, 125_000, RENT, merchant = 4)
            // Groceries land on three days, so a month total is not one payment.
            val grocery = when (month) {
                5 -> listOf(14_000L, 12_800L, 13_300L)
                6 -> listOf(13_100L, 12_400L, 13_200L)
                else -> listOf(14_234L, 13_500L, 13_500L)
            }
            grocery.forEachIndexed { index, minor -> spend(month, 6 + index * 7, minor, GROCERIES, merchant = 3) }
            spend(month, 9, if (month == 5) 30_000L else if (month == 6) 28_000L else 26_000L, EATING)
            spend(month, 21, if (month == 5) 4_500L else if (month == 7) 5_000L else 0L, HEALTH, merchant = 1)
            spend(month, 24, if (month == 6) 3_500L else 4_000L, BIKE, merchant = 2)
            // One trip, in one month: by August it is a category that stopped.
            if (month == 6) spend(month, 12, 62_000, TRAVEL)
        }

        earn(8, 5, 420_000)
        spend(8, 3, 125_000, RENT, merchant = 4)
        listOf(13_000L, 13_000L, 13_000L).forEachIndexed { index, minor ->
            spend(8, 6 + index * 7, minor, GROCERIES, merchant = 3)
        }
        if (shape == Shape.CHEAPER) {
            spend(8, 9, 8_000, EATING)
            spend(8, 21, 2_000, HEALTH, merchant = 1)
            spend(8, 24, 1_500, BIKE, merchant = 2)
            return rows
        }
        spend(8, 9, 12_000, EATING)
        // Two payments inside one category: the block names the category, never the reason.
        spend(8, 7, 42_000, HEALTH, merchant = 1)
        spend(8, 18, 13_000, HEALTH, merchant = 1)
        spend(8, 8, 34_000, BIKE, merchant = 2)
        return rows
    }

    private fun expense(
        id: Long,
        date: LocalDate,
        minor: Long,
        categoryId: Long,
        merchantId: Long?,
    ) = TransactionEntity(
        id = id,
        accountId = 1,
        amountMinor = -minor,
        currency = "GEL",
        gelValueMinor = -minor,
        occurredAt = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        merchantId = merchantId,
        categoryId = categoryId,
        status = TxStatus.CONFIRMED,
        source = TxSource.STATEMENT,
    )

    private fun income(id: Long, date: LocalDate, minor: Long) = TransactionEntity(
        id = id,
        accountId = 1,
        amountMinor = minor,
        currency = "GEL",
        gelValueMinor = minor,
        occurredAt = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        categoryId = SALARY,
        status = TxStatus.CONFIRMED,
        source = TxSource.STATEMENT,
    )

    /** The real calculation over the invented history — never hand-written view data. */
    fun analytics(
        shape: Shape,
        today: LocalDate = afterSelectedMonth,
        period: AnalyticsPeriod = AnalyticsPeriod.month(selectedMonth),
    ): AnalyticsData = calculateAnalytics(
        // No ledger row is dated after today, so a period still running is only as full as its
        // elapsed days — otherwise the current total would be read against a shorter baseline.
        transactions = transactions(shape).filter {
            it.occurredAt <= today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        },
        categories = categories,
        allocations = emptyList(),
        period = period,
        trendFilter = AnalyticsTrendFilter.All,
        zoneId = zone,
        today = today,
        merchants = merchants,
    )
}
