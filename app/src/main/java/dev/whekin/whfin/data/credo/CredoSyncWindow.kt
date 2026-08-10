package dev.whekin.whfin.data.credo

import dev.whekin.whfin.data.db.StatementImportEntity
import dev.whekin.whfin.data.importer.StatementCoverage
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * How far back one account's next sync reaches.
 *
 * A statement is downloaded and parsed whole, so asking for a year on every run when the last sync
 * was a week ago means re-reading a year of rows to insert nothing. The window follows what this
 * account is already known to cover instead.
 *
 * Deeper history is not this object's job. A routine sync never asks for more than the year the
 * bank's own web export uses; reaching further back is an explicit, separate action.
 */
object CredoSyncWindow {

    /** Asked for when nothing is known about an account, and the deepest a routine sync reaches. */
    const val FULL_MONTHS = 12L

    /**
     * Always re-read at least this much.
     *
     * A card payment reaches the statement days after the purchase, so a window that starts exactly
     * where coverage ended would keep missing the tail of every run. A month of overlap costs
     * nothing: repeated rows are recognised by `externalKey` and inserted once.
     */
    const val MINIMUM_MONTHS = 1L

    fun startFor(now: ZonedDateTime, imports: List<StatementImportEntity>): ZonedDateTime {
        // The untouched first sync keeps the exact instant window the bank's web export sends.
        val deepest = now.minusMonths(FULL_MONTHS)
        val coveredTo = imports.mapNotNull(StatementImportEntity::periodTo).maxOrNull()
            ?.let(LocalDate::ofEpochDay)
            ?: return deepest

        // A hole in the coverage is never filled by a window that begins after it.
        val earliestGap = StatementCoverage.gaps(imports).minOfOrNull { it.from }
        val reach = if (earliestGap != null && earliestGap < coveredTo) earliestGap else coveredTo

        val candidate = reach.atStartOfDay(now.zone)
        val minimum = now.minusMonths(MINIMUM_MONTHS)
        val start = if (candidate.isBefore(minimum)) candidate else minimum
        return if (start.isBefore(deepest)) deepest else start
    }
}
