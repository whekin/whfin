package dev.whekin.whfin.data.savings

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.FundRole
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class SavingsAnalyticsTest {

    private val utc = ZoneOffset.UTC
    private val reserve = account(1, "Reserve", FundRole.RESERVE)
    private val available = account(2, "Everyday", FundRole.AVAILABLE)

    @Test
    fun `balance includes every active reserve row but pace only counts controlled movements`() {
        val cryptoReserve = account(3, "Crypto reserve", FundRole.RESERVE, AccountType.CRYPTO, "USDT")
        val transactions = listOf(
            // Balance-only opening anchor.
            tx(1, 100_000, LocalDate.of(2026, 1, 1), source = TxSource.ADJUSTMENT, isTransfer = true),
            // Interest is balance, not controlled saving.
            tx(2, 500, LocalDate.of(2026, 1, 31)),
            // Direct reserve spending reduces both balance and controlled pace.
            tx(3, -10_000, LocalDate.of(2026, 2, 2)),
            // Available -> reserve and reserve -> available are controlled flows.
            tx(4, 25_000, LocalDate.of(2026, 2, 5), transferGroupId = 10),
            tx(5, -25_000, LocalDate.of(2026, 2, 5), accountId = available.id, transferGroupId = 10),
            tx(6, -5_000, LocalDate.of(2026, 2, 10), transferGroupId = 11),
            tx(7, 5_000, LocalDate.of(2026, 2, 10), accountId = available.id, transferGroupId = 11),
            // Available rows never affect reserve balance.
            tx(8, -99_000, LocalDate.of(2026, 2, 15), accountId = available.id),
            // Crypto and voided rows are absent from both outputs.
            tx(9, 9_000, LocalDate.of(2026, 2, 16), accountId = cryptoReserve.id, currency = "USDT"),
            tx(10, 90_000, LocalDate.of(2026, 2, 17), isVoided = true),
        )

        val result = calculateSavingsAnalytics(
            accounts = listOf(reserve, available, cryptoReserve),
            transactions = transactions,
            currency = "GEL",
            fromMonth = YearMonth.of(2026, 1),
            throughMonth = YearMonth.of(2026, 3),
            zoneId = utc,
        )

        assertEquals(
            listOf(100_500L, 110_500L, 110_500L),
            result.months.map { it.reserveBalanceMinor },
        )
        assertEquals(listOf(0L, 10_000L, 0L), result.months.map { it.paceMinor })
    }

    @Test
    fun `pace uses reserve-side net once and ignores same-role groups and adjustments`() {
        val secondReserve = account(4, "Term reserve", FundRole.RESERVE)
        val secondAvailable = account(5, "Wallet", FundRole.AVAILABLE)
        val transactions = listOf(
            // One available debit funds two reserve legs: count +100_000 once, not twice.
            tx(1, -100_000, LocalDate.of(2026, 6, 1), accountId = available.id, transferGroupId = 20),
            tx(2, 70_000, LocalDate.of(2026, 6, 1), transferGroupId = 20),
            tx(3, 30_000, LocalDate.of(2026, 6, 1), accountId = secondReserve.id, transferGroupId = 20),
            // Reserve -> available is negative controlled pace.
            tx(4, -40_000, LocalDate.of(2026, 6, 2), transferGroupId = 21),
            tx(5, 40_000, LocalDate.of(2026, 6, 2), accountId = available.id, transferGroupId = 21),
            // Reserve <-> reserve and available <-> available groups are zero.
            tx(6, 50_000, LocalDate.of(2026, 6, 3), transferGroupId = 22),
            tx(7, -50_000, LocalDate.of(2026, 6, 3), accountId = secondReserve.id, transferGroupId = 22),
            tx(8, -20_000, LocalDate.of(2026, 6, 4), accountId = available.id, transferGroupId = 23),
            tx(9, 20_000, LocalDate.of(2026, 6, 4), accountId = secondAvailable.id, transferGroupId = 23),
            // Only ordinary reserve spending affects pace among non-transfers.
            tx(10, -25_000, LocalDate.of(2026, 6, 5)),
            tx(11, 100_000, LocalDate.of(2026, 6, 6)),
            tx(12, 7_000, LocalDate.of(2026, 6, 7), source = TxSource.ADJUSTMENT),
            tx(13, -8_000, LocalDate.of(2026, 6, 8), source = TxSource.ADJUSTMENT),
            // Even if an adjustment is accidentally grouped, it remains balance-only.
            tx(14, 9_000, LocalDate.of(2026, 6, 9), source = TxSource.ADJUSTMENT, transferGroupId = 24),
            tx(15, -9_000, LocalDate.of(2026, 6, 9), accountId = available.id, transferGroupId = 24),
        )

        val result = calculateSavingsAnalytics(
            accounts = listOf(reserve, available, secondReserve, secondAvailable),
            transactions = transactions,
            currency = "GEL",
            fromMonth = YearMonth.of(2026, 6),
            throughMonth = YearMonth.of(2026, 6),
            zoneId = utc,
        )

        assertEquals(35_000L, result.months.single().paceMinor)
    }

    @Test
    fun `buckets are consecutive across year boundary and preserve starting balance`() {
        val transactions = listOf(
            tx(1, 100_000, LocalDate.of(2025, 10, 31), source = TxSource.ADJUSTMENT),
            tx(2, -10_000, LocalDate.of(2025, 12, 15)),
            tx(3, 20_000, LocalDate.of(2026, 1, 5), accountId = reserve.id, transferGroupId = 30),
            tx(4, -20_000, LocalDate.of(2026, 1, 5), accountId = available.id, transferGroupId = 30),
        )

        val result = calculateSavingsAnalytics(
            accounts = listOf(reserve, available),
            transactions = transactions,
            currency = "GEL",
            fromMonth = YearMonth.of(2025, 11),
            throughMonth = YearMonth.of(2026, 2),
            zoneId = utc,
        )

        assertEquals(
            listOf(
                YearMonth.of(2025, 11),
                YearMonth.of(2025, 12),
                YearMonth.of(2026, 1),
                YearMonth.of(2026, 2),
            ),
            result.months.map { it.month },
        )
        assertEquals(listOf(100_000L, 90_000L, 110_000L, 110_000L), result.months.map { it.reserveBalanceMinor })
        assertEquals(listOf(0L, -10_000L, 20_000L, 0L), result.months.map { it.paceMinor })
    }

    @Test
    fun `zone controls which month receives a transaction near midnight`() {
        val nearMidnightUtc = Instant.parse("2025-12-31T21:30:00Z").toEpochMilli()
        val transaction = tx(1, -1_000, LocalDate.of(2026, 1, 1)).copy(occurredAt = nearMidnightUtc)

        val result = calculateSavingsAnalytics(
            accounts = listOf(reserve),
            transactions = listOf(transaction),
            currency = "GEL",
            fromMonth = YearMonth.of(2025, 12),
            throughMonth = YearMonth.of(2026, 1),
            zoneId = ZoneId.of("Asia/Tbilisi"),
        )

        assertEquals(listOf(0L, -1_000L), result.months.map { it.paceMinor })
        assertEquals(listOf(0L, -1_000L), result.months.map { it.reserveBalanceMinor })
    }

    @Test
    fun `voided rows do not fund a transfer group or change balance`() {
        val transactions = listOf(
            tx(1, 100_000, LocalDate.of(2026, 7, 1), isVoided = true),
            // The only available peer is voided, so this active reserve leg is not proven saving.
            tx(2, 50_000, LocalDate.of(2026, 7, 2), transferGroupId = 40),
            tx(3, -50_000, LocalDate.of(2026, 7, 2), accountId = available.id, transferGroupId = 40, isVoided = true),
        )

        val result = calculateSavingsAnalytics(
            accounts = listOf(reserve, available),
            transactions = transactions,
            currency = "GEL",
            fromMonth = YearMonth.of(2026, 7),
            throughMonth = YearMonth.of(2026, 7),
            zoneId = utc,
        )

        assertEquals(50_000L, result.months.single().reserveBalanceMinor)
        assertEquals(0L, result.months.single().paceMinor)
    }

    @Test
    fun `rolling average includes zero months and uses at most three trailing buckets`() {
        val months = listOf(
            SavingsMonth(YearMonth.of(2026, 1), reserveBalanceMinor = 0L, paceMinor = 90L),
            SavingsMonth(YearMonth.of(2026, 2), reserveBalanceMinor = 0L, paceMinor = 30L),
            SavingsMonth(YearMonth.of(2026, 3), reserveBalanceMinor = 0L, paceMinor = 0L),
            SavingsMonth(YearMonth.of(2026, 4), reserveBalanceMinor = 0L, paceMinor = 60L),
        )

        val averages = rollingThreeMonthAverage(months)

        assertEquals(listOf(90L, 60L, 40L, 30L), averages.map { it.averagePaceMinor })
        assertEquals(listOf(1, 2, 3, 3), averages.map { it.monthsIncluded })
        assertEquals(averages, SavingsAnalytics("GEL", months).rollingThreeMonthAverage())
    }

    private fun account(
        id: Long,
        name: String,
        fundRole: FundRole,
        type: AccountType = AccountType.BANK,
        currency: String = "GEL",
    ) = AccountEntity(
        id = id,
        name = name,
        type = type,
        currency = currency,
        fundRole = fundRole,
    )

    private fun tx(
        id: Long,
        amountMinor: Long,
        day: LocalDate,
        accountId: Long = reserve.id,
        currency: String = "GEL",
        source: TxSource = TxSource.STATEMENT,
        transferGroupId: Long? = null,
        isTransfer: Boolean = transferGroupId != null,
        isVoided: Boolean = false,
    ) = TransactionEntity(
        id = id,
        accountId = accountId,
        amountMinor = amountMinor,
        currency = currency,
        occurredAt = day.atStartOfDay(utc).toInstant().toEpochMilli(),
        status = TxStatus.CONFIRMED,
        source = source,
        transferGroupId = transferGroupId,
        isTransfer = isTransfer,
        isVoided = isVoided,
    )
}
