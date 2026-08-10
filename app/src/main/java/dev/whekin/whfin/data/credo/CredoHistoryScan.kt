package dev.whekin.whfin.data.credo

import dev.whekin.whfin.data.statement.BankStatement
import java.time.LocalDate

/** One window of history to ask the bank for, both ends inclusive. */
data class CredoHistoryChunk(val from: LocalDate, val to: LocalDate)

/**
 * Walking an account's history backwards, a year at a time.
 *
 * One huge range is the obvious alternative and a worse one: the whole workbook is held in memory
 * while it is unzipped and parsed, the request stops looking like the one the bank's own web export
 * sends, and a single failure costs the entire history. A year per request keeps the shape the site
 * uses, bounds memory to what a routine sync already handles, and lets each chunk validate its own
 * balance chain.
 *
 * Nobody tells us where an account begins — the bank's account list carries no opening date — so the
 * bottom is recognised from the statements themselves.
 */
object CredoHistoryScan {

    /** A statement's own period is a year, so history is asked for in the same unit. */
    const val CHUNK_MONTHS = 12L

    /** Not a stop signal but a guard: a protocol change must not turn this into an endless loop. */
    const val MAX_CHUNKS = 10

    /** The window ending just before the earliest history already held. */
    fun chunkBefore(earliestKnown: LocalDate): CredoHistoryChunk {
        val to = earliestKnown.minusDays(1)
        return CredoHistoryChunk(from = to.minusMonths(CHUNK_MONTHS), to = to)
    }

    /**
     * Whether the bank has nothing older than what this chunk returned.
     *
     * An empty chunk on its own means nothing: an account can sit untouched for a year with money on
     * it, and stopping there would cut the history short. What does end the walk:
     *
     * - the bank narrowed the period we asked for — it has nothing earlier to give;
     * - the chunk has rows and opens at zero — the ledger starts here;
     * - the chunk is empty and stands at zero throughout — there was no account yet.
     *
     * The last one can in principle stop one chunk early, on an account that was emptied to exactly
     * zero and then left alone for a full year. A second run starts from the new earliest coverage
     * and reaches past it, so the history is not lost, only deferred.
     */
    fun reachedBottom(requested: CredoHistoryChunk, statement: BankStatement): Boolean {
        statement.periodFrom?.let { if (it.isAfter(requested.from)) return true }
        if (statement.rows.isNotEmpty()) return statement.openingBalanceMinor == 0L
        return statement.openingBalanceMinor == 0L && statement.closingBalanceMinor == 0L
    }
}
