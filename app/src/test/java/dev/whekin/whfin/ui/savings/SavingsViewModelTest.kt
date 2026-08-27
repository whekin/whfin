package dev.whekin.whfin.ui.savings

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.FundRole
import dev.whekin.whfin.data.db.SavingsPlanEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class SavingsViewModelTest {
    private val zone = ZoneOffset.UTC
    private val reserve = AccountEntity(
        id = 1,
        name = "Reserve",
        type = AccountType.BANK,
        currency = "GEL",
        fundRole = FundRole.RESERVE,
    )
    private val available = reserve.copy(id = 2, name = "Everyday", fundRole = FundRole.AVAILABLE)

    @Test
    fun `history starts with evidence and other currencies stay separate`() {
        val usd = reserve.copy(id = 3, currency = "USD")
        val data = buildSavingsScreenData(
            accounts = listOf(reserve, available, usd),
            transactions = transfer(1, YearMonth.of(2026, 7), 90_000, 10) +
                tx(99, usd.id, 500_000, YearMonth.of(2026, 7), 11).copy(currency = "USD"),
            plans = emptyList(), today = LocalDate.of(2026, 8, 27), zone = zone,
        )
        assertEquals(listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 8)), data.months.map { it.month })
        assertEquals(90_000L, data.currentReserveMinor)
        assertEquals(listOf("GEL", "USD"), data.availableCurrencies)
    }

    @Test
    fun `plan revisions judge each complete month by the target that covered it`() {
        val data = buildSavingsScreenData(
            accounts = listOf(reserve, available),
            transactions = listOf(
                transfer(1, YearMonth.of(2026, 5), 90_000, 10),
                transfer(2, YearMonth.of(2026, 6), 120_000, 11),
                transfer(3, YearMonth.of(2026, 7), 140_000, 12),
            ).flatMap { it },
            plans = listOf(
                plan(1, 100_000, YearMonth.of(2026, 5), YearMonth.of(2026, 6)),
                plan(2, 150_000, YearMonth.of(2026, 7), null),
            ),
            today = LocalDate.of(2026, 8, 27),
            zone = zone,
        )

        assertEquals(1, data.monthsOnPace)
        assertEquals(3, data.evaluatedMonths)
        assertEquals(150_000L, data.currentPlan?.monthlyTargetMinor)
        assertEquals(116_666L, data.rollingThreeMonthMinor)
    }

    private fun transfer(id: Long, month: YearMonth, amount: Long, group: Long) = listOf(
        tx(id * 2, reserve.id, amount, month, group),
        tx(id * 2 + 1, available.id, -amount, month, group),
    )

    private fun tx(id: Long, accountId: Long, amount: Long, month: YearMonth, group: Long) = TransactionEntity(
        id = id,
        accountId = accountId,
        amountMinor = amount,
        currency = "GEL",
        occurredAt = month.atDay(5).atStartOfDay(zone).toInstant().toEpochMilli(),
        status = TxStatus.CONFIRMED,
        source = TxSource.STATEMENT,
        transferGroupId = group,
        isTransfer = true,
    )

    private fun plan(id: Long, target: Long, from: YearMonth, through: YearMonth?) = SavingsPlanEntity(
        id = id,
        currency = "GEL",
        monthlyTargetMinor = target,
        startedOn = from.atDay(1).toEpochDay(),
        endedOn = through?.atEndOfMonth()?.toEpochDay(),
        createdAt = 0,
    )
}
