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
    const val MAX_CHUNKS = 100

    /** The window ending just before the earliest history already held. */
    fun chunkBefore(earliestKnown: LocalDate): CredoHistoryChunk {
        val to = earliestKnown.minusDays(1)
        return CredoHistoryChunk(from = to.minusMonths(CHUNK_MONTHS), to = to)
    }

    /** Only an explicitly narrowed export period is evidence of the end without an API extent.
     * Zero balances and empty years can occur in the middle of an account's life. */
    fun reachedBottom(requested: CredoHistoryChunk, statement: BankStatement): Boolean =
        statement.periodFrom?.isAfter(requested.from) == true
}
