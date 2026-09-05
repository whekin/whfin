package dev.whekin.whfin.data.income

import dev.whekin.whfin.data.db.IncomeSourceEntity
import dev.whekin.whfin.data.db.IncomeSourcePaymentEntity
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
    /** Credits confirmed as this source's pay within the month, in the account's own currency. */
    val received: List<TransactionEntity> = emptyList(),
    /**
     * The one currency [received] arrived in, or null when it was not unanimous.
     *
     * Kept separate from the declared currency because the two are different facts: an agreement can
     * be written in dollars and paid in USDT, and adding them together — or quietly treating one as
     * the other — is the kind of arithmetic that produces a confident wrong number.
     */
    val receivedCurrency: String? = null,
    /** The estimated payday is behind us and the declared amount is not fully here yet. */
    val awaiting: Boolean = false,
    /**
     * Of [received], the ones the owner confirmed by hand.
     *
     * The rest followed from a sender confirmed earlier, so they are un-counted by taking that
     * original answer back rather than by a second one that would contradict it.
     */
    val confirmedIds: Set<Long> = emptySet(),
    /**
     * Credits on the receiving account that nobody has confirmed as this source's pay.
     *
     * They are shown as a question, never counted. A refund, a friend settling up and the salary are
     * the same shape in a ledger, and an app that guesses between them tells the owner they have
     * been paid when they have not.
     */
    val candidates: List<TransactionEntity> = emptyList(),
    /**
     * Whether the answer came from a chain read that failed.
     *
     * Kept apart from "nothing arrived", because the two look identical in a total and mean opposite
     * things: one says you were not paid, the other says we could not look.
     */
    val unreadable: Boolean = false,
) {
    val receivedMinor: Long get() = received.sumOf { it.amountMinor }
    val receivedCount: Int get() = received.size
    val arrived: Boolean get() = received.isNotEmpty()

    /** True only while the declaration and the money are in the same currency and can be compared. */
    val comparable: Boolean get() = receivedCurrency == source.currency

    /**
     * Whether this month's pay is in.
     *
     * With comparable money it is the plain sum against the declaration. Without it — the agreement
     * in one currency, the payment in another — the amount cannot be judged at all, so a confirmed
     * payment settles the month rather than leaving the owner waiting forever for a comparison this
     * iteration cannot make. What it never does is invent a rate to reach a number.
     */
    val fulfilled: Boolean
        get() = if (comparable) receivedMinor >= source.amountMinor else arrived

    /** Only meaningful while the two sides are comparable; zero otherwise, and never displayed. */
    val remainingMinor: Long
        get() = if (comparable) (source.amountMinor - receivedMinor).coerceAtLeast(0) else 0L
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

    /**
     * Reads each source's month against the ledger.
     *
     * [payments] are the confirmations the owner made by hand. Everything else that landed on the
     * receiving account is offered back as a candidate, so the question stays visible instead of
     * being answered by arithmetic.
     */
    fun of(
        sources: List<IncomeSourceEntity>,
        transactions: List<TransactionEntity>,
        month: YearMonth,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        payments: List<IncomeSourcePaymentEntity> = emptyList(),
    ): List<IncomeExpectation> {
        val confirmedBySource = payments.groupBy({ it.incomeSourceId }, { it.transactionId })
        val byId = transactions.associateBy { it.id }
        return sources.filter { covers(it, month) }.map { source ->
            val confirmed = confirmedBySource[source.id].orEmpty().toSet()
            // A sender the owner has already vouched for once carries the next payment on its own.
            // Identity is the counterparty the ledger recorded — a merchant row for a bank credit,
            // the exact sending address for a wallet — never the amount, which changes every month.
            val senders = confirmed.mapNotNull { byId[it] }.senderKeys()
            val inMonth = transactions.filter { it.creditsSourceIn(source, month, zone) }
            val (received, candidates) = inMonth.partition { transaction ->
                transaction.id in confirmed || transaction.senderKey()?.let { it in senders } == true
            }
            val reading = IncomeExpectation(
                source = source,
                received = received.sortedByDescending(TransactionEntity::occurredAt),
                receivedCurrency = received.map(TransactionEntity::currency).distinct().singleOrNull(),
                confirmedIds = confirmed,
                candidates = candidates.sortedByDescending(TransactionEntity::occurredAt),
            )
            // Waiting, never "late": the owner declared an estimate, not a due date, so a payday
            // that has gone by with money still outstanding is a state, not an accusation. On the
            // day itself nothing is outstanding yet — the day is not over.
            reading.copy(awaiting = !reading.fulfilled && today > source.expectedPayday(month))
        }
    }

    /** Every credit that could belong to this source's month, confirmed or not. */
    private fun TransactionEntity.creditsSourceIn(
        declaration: IncomeSourceEntity,
        month: YearMonth,
        zone: ZoneId,
    ): Boolean {
        // The account decides which money could be this source's, not the declared currency: an
        // agreement in dollars paid in USDT is one arrangement, and filtering by the declaration
        // would silently offer nothing at all and leave the owner with a row that never moves.
        if (accountId != declaration.accountId) return false
        // Movements between the owner's own accounts, corrections and voided rows are never income:
        // the money was already counted when it first arrived.
        if (amountMinor <= 0 || isTransfer || transferGroupId != null) return false
        if (source == TxSource.ADJUSTMENT || isVoided) return false
        val day = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()
        if (day.toEpochDay() < declaration.startedOn) return false
        if (declaration.endedOn?.let { day.toEpochDay() > it } == true) return false
        return YearMonth.from(day) == month
    }

    private fun Iterable<TransactionEntity>.senderKeys(): Set<String> =
        mapNotNullTo(mutableSetOf()) { it.senderKey() }

    private fun TransactionEntity.senderKey(): String? =
        merchantId?.let { "merchant:$it" } ?: rawCounterparty?.takeIf(String::isNotBlank)?.let { "raw:$it" }
}
