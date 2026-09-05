package dev.whekin.whfin.data.income

import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * What a declared source said would arrive this month, next to what did.
 *
 * The declaration is never trusted on its own. A single number typed once goes stale silently, and a
 * silently stale number is the worst thing a money app can show, so the answer always carries both
 * sides: what was declared and what the ledger actually recorded. A gap between them is information —
 * the rate moved, the payment is late, the job changed — not an error to hide.
 *
 * Nothing here writes to the ledger. Declaring where money enters explains rows; it never creates
 * them.
 */
data class IncomeExpectation(
    val source: IncomeSourceEntity,
    /** Sum actually received on the declared account within the month, in that account's currency. */
    val receivedMinor: Long,
    val receivedCount: Int,
    /** The estimated payday is behind us and the declared amount is not fully here yet. */
    val awaiting: Boolean,
    /**
     * Whether the answer came from a chain read that failed.
     *
     * Kept apart from "nothing arrived", because the two look identical in a total and mean opposite
     * things: one says you were not paid, the other says we could not look.
     */
    val unreadable: Boolean = false,
) {
    val arrived: Boolean get() = receivedCount > 0
    val fulfilled: Boolean get() = receivedMinor >= source.amountMinor
    val remainingMinor: Long get() = (source.amountMinor - receivedMinor).coerceAtLeast(0)
}

object IncomeExpectations {

    /**
     * A source describes a month when it had already started by the month's end and had not ended
     * before its start: the eras of a working life meet at a boundary, and neither side should claim
     * the same month twice.
     */
    fun covers(source: IncomeSourceEntity, month: YearMonth): Boolean {
        val started = LocalDate.ofEpochDay(source.startedOn)
        if (started > month.atEndOfMonth()) return false
        val ended = source.endedOn?.let(LocalDate::ofEpochDay) ?: return true
        return ended >= month.atDay(1)
    }

    fun of(
        sources: List<IncomeSourceEntity>,
        transactions: List<TransactionEntity>,
        month: YearMonth,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<IncomeExpectation> = sources.filter { covers(it, month) }.map { source ->
        val received = transactions.filter { transaction ->
            transaction.accountId == source.accountId &&
                transaction.currency == source.currency &&
                transaction.amountMinor > 0 &&
                !transaction.isTransfer &&
                transaction.transferGroupId == null &&
                transaction.source != TxSource.ADJUSTMENT &&
                !transaction.isVoided &&
                Instant.ofEpochMilli(transaction.occurredAt).atZone(zone).toLocalDate().toEpochDay() >= source.startedOn &&
                (source.endedOn == null ||
                    Instant.ofEpochMilli(transaction.occurredAt).atZone(zone).toLocalDate().toEpochDay() <= source.endedOn) &&
                YearMonth.from(
                    Instant.ofEpochMilli(transaction.occurredAt).atZone(zone).toLocalDate(),
                ) == month
        }
        IncomeExpectation(
            source = source,
            receivedMinor = received.sumOf { it.amountMinor },
            receivedCount = received.size,
            // Waiting, never "late": the owner declared an estimate, not a due date, so a payday
            // that has gone by with money still outstanding is a state, not an accusation.
            awaiting = received.sumOf { it.amountMinor } < source.amountMinor &&
                today > source.expectedPayday(month),
        )
    }

}
