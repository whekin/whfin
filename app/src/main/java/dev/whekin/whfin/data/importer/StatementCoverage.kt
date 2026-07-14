package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.db.StatementImportEntity
import java.time.LocalDate

data class CoverageGap(val from: LocalDate, val to: LocalDate)

object StatementCoverage {
    fun gaps(imports: List<StatementImportEntity>): List<CoverageGap> {
        val ranges = imports.mapNotNull { item ->
            val from = item.periodFrom?.let(LocalDate::ofEpochDay) ?: return@mapNotNull null
            val to = item.periodTo?.let(LocalDate::ofEpochDay) ?: return@mapNotNull null
            from to to
        }.sortedBy { it.first }
        if (ranges.size < 2) return emptyList()

        val result = mutableListOf<CoverageGap>()
        var coveredTo = ranges.first().second
        for ((from, to) in ranges.drop(1)) {
            if (from > coveredTo.plusDays(1)) {
                result += CoverageGap(coveredTo.plusDays(1), from.minusDays(1))
            }
            if (to > coveredTo) coveredTo = to
        }
        return result
    }
}
