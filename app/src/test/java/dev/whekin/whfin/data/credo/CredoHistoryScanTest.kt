package dev.whekin.whfin.data.credo

import dev.whekin.whfin.data.statement.BankProfile
import dev.whekin.whfin.data.statement.BankStatement
import dev.whekin.whfin.data.statement.StatementOperation
import dev.whekin.whfin.data.statement.StatementRow
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredoHistoryScanTest {

    private val requested = CredoHistoryChunk(
        from = LocalDate.of(2024, 1, 1),
        to = LocalDate.of(2024, 12, 31),
    )

    @Test
    fun `the next chunk ends the day before history already held`() {
        val chunk = CredoHistoryScan.chunkBefore(LocalDate.of(2026, 3, 1))

        assertEquals(LocalDate.of(2026, 2, 28), chunk.to)
        assertEquals(LocalDate.of(2025, 2, 28), chunk.from)
    }

    @Test
    fun `chunks abut so the walk leaves no hole behind it`() {
        val first = CredoHistoryScan.chunkBefore(LocalDate.of(2026, 3, 1))
        val second = CredoHistoryScan.chunkBefore(first.from)

        assertEquals(first.from.minusDays(1), second.to)
    }

    @Test
    fun `a narrowed period means the bank has nothing earlier`() {
        val clamped = statement(from = LocalDate.of(2024, 6, 1), opening = 5_000, closing = 4_000, rows = 3)

        assertTrue(CredoHistoryScan.reachedBottom(requested, clamped))
    }

    @Test
    fun `a chunk that opens at zero is where the ledger starts`() {
        val first = statement(from = requested.from, opening = 0, closing = 4_000, rows = 3)

        assertTrue(CredoHistoryScan.reachedBottom(requested, first))
    }

    @Test
    fun `a full chunk that opens with money keeps the walk going`() {
        val middle = statement(from = requested.from, opening = 9_000, closing = 4_000, rows = 3)

        assertFalse(CredoHistoryScan.reachedBottom(requested, middle))
    }

    @Test
    fun `a quiet year holding money is not the bottom`() {
        // The account existed and was simply untouched: stopping here would cut the history short.
        val dormant = statement(from = requested.from, opening = 4_000, closing = 4_000, rows = 0)

        assertFalse(CredoHistoryScan.reachedBottom(requested, dormant))
    }

    @Test
    fun `an empty year at zero throughout means there was no account yet`() {
        val before = statement(from = requested.from, opening = 0, closing = 0, rows = 0)

        assertTrue(CredoHistoryScan.reachedBottom(requested, before))
    }

    @Test
    fun `an unknown period is not mistaken for a narrowed one`() {
        val noPeriod = statement(from = null, opening = 9_000, closing = 4_000, rows = 3)

        assertFalse(CredoHistoryScan.reachedBottom(requested, noPeriod))
    }

    private fun statement(
        from: LocalDate?,
        opening: Long?,
        closing: Long?,
        rows: Int,
    ) = BankStatement(
        bank = BankProfile(provider = "Credo", displayName = "Credo"),
        accountIban = "GE00WH0000000000000000",
        currency = "GEL",
        periodFrom = from,
        periodTo = requested.to,
        openingBalanceMinor = opening,
        closingBalanceMinor = closing,
        rows = List(rows) {
            StatementRow(
                postedDate = requested.to,
                operation = StatementOperation.CARD_PAYMENT,
                operationRaw = "card",
                amountMinor = -100,
                balanceAfterMinor = null,
                description = "row",
                beneficiaryName = null,
                beneficiaryAccount = null,
            )
        },
    )
}
