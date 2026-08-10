package dev.whekin.whfin.data.credo

import dev.whekin.whfin.data.db.StatementImportEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CredoSyncWindowTest {

    private val zone = ZoneId.of("Asia/Tbilisi")
    private val now = ZonedDateTime.of(2026, 7, 14, 22, 21, 31, 555_000_000, zone)

    @Test
    fun `an account nothing is known about is asked for the full year`() {
        val start = CredoSyncWindow.startFor(now, emptyList())

        // The exact instant window the bank's own web export sends, down to the millisecond.
        assertEquals(now.minusMonths(12), start)
    }

    @Test
    fun `imports without a period say nothing about coverage`() {
        val start = CredoSyncWindow.startFor(now, listOf(import(from = null, to = null)))

        assertEquals(now.minusMonths(12), start)
    }

    @Test
    fun `an account synced a week ago is still re-read for a month`() {
        val start = CredoSyncWindow.startFor(
            now,
            listOf(import(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 7))),
        )

        // Not 7 July: the bank posts a card payment days after the purchase.
        assertEquals(now.minusMonths(1), start)
    }

    @Test
    fun `coverage older than a month decides the window itself`() {
        val start = CredoSyncWindow.startFor(
            now,
            listOf(import(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 20))),
        )

        assertEquals(LocalDate.of(2026, 3, 20).atStartOfDay(zone), start)
    }

    @Test
    fun `a routine sync never digs deeper than the year the web export uses`() {
        val start = CredoSyncWindow.startFor(
            now,
            listOf(import(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1))),
        )

        assertEquals(now.minusMonths(12), start)
    }

    @Test
    fun `a hole in the coverage pulls the window back to its start`() {
        val start = CredoSyncWindow.startFor(
            now,
            listOf(
                import(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28)),
                // March and April were never imported.
                import(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 7, 13)),
            ),
        )

        assertEquals(LocalDate.of(2026, 3, 1).atStartOfDay(zone), start)
    }

    @Test
    fun `a gap already older than the year is not chased by a routine sync`() {
        val start = CredoSyncWindow.startFor(
            now,
            listOf(
                import(LocalDate.of(2019, 1, 1), LocalDate.of(2019, 2, 28)),
                import(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 7, 13)),
            ),
        )

        assertEquals(now.minusMonths(12), start)
    }

    private fun import(from: LocalDate?, to: LocalDate?) = StatementImportEntity(
        accountId = 1,
        periodFrom = from?.toEpochDay(),
        periodTo = to?.toEpochDay(),
        openingBalanceMinor = null,
        closingBalanceMinor = null,
        totalRows = 1,
        inserted = 1,
        duplicates = 0,
        reconciled = 0,
        reviewCount = 0,
        importedAt = 0,
    )
}
