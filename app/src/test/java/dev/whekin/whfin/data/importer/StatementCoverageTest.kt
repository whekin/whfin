package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.db.StatementImportEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatementCoverageTest {
    private fun item(from: String, to: String) = StatementImportEntity(
        accountId = 1,
        periodFrom = LocalDate.parse(from).toEpochDay(),
        periodTo = LocalDate.parse(to).toEpochDay(),
        openingBalanceMinor = null,
        closingBalanceMinor = null,
        totalRows = 0,
        inserted = 0,
        duplicates = 0,
        reconciled = 0,
        importedAt = 0,
    )

    @Test fun `adjacent and overlapping ranges have no gaps`() {
        assertEquals(emptyList<CoverageGap>(), StatementCoverage.gaps(listOf(
            item("2026-01-01", "2026-01-31"),
            item("2026-02-01", "2026-02-28"),
            item("2026-02-15", "2026-03-01"),
        )))
    }

    @Test fun `finds missing dates between imports`() {
        assertEquals(
            listOf(CoverageGap(LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-09"))),
            StatementCoverage.gaps(listOf(
                item("2026-01-01", "2026-01-31"),
                item("2026-02-10", "2026-03-01"),
            )),
        )
    }
}
