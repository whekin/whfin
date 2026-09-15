package dev.whekin.whfin.data.mutation

import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.importer.MerchantNormalizer

/**
 * Folds messages that stand beside a statement line of the same money back into one operation.
 *
 * Three bus fares of the same price on one day are three rides, and the ledger holds each of them
 * twice — once from the message, once from the statement. So a message claims a statement line **of
 * its own**: the choice between two indistinguishable lines does not matter, but the count does, and
 * folding every message into the first line would delete every ride but one. When the lines run out,
 * the remaining messages stay listed rather than being collapsed into something already spoken for.
 *
 * A line that names a different merchant is not a candidate: same amount on the same day is how the
 * check finds a suspect, not proof that two payments are the same payment.
 */
internal class DuplicateFolding(
    private val db: WhfinDatabase,
    private val mutations: TransactionMutationModule,
) {

    suspend fun fold(messageIds: List<Long>): Int {
        var merged = 0
        val claimed = mutableSetOf<Long>()
        for (id in messageIds) {
            val message = db.transactionDao().byId(id) ?: continue
            val day = LedgerCalendar.dayOf(message.occurredAt)
            val candidates = db.transactionDao().statementCandidates(
                message.accountId,
                LedgerCalendar.startOfDay(day),
                LedgerCalendar.startOfDay(day.plusDays(1)) - 1,
            ).filter { it.amountMinor == message.amountMinor && it.id !in claimed }
            val survivor = candidates.firstOrNull {
                MerchantNormalizer.equivalent(it.rawCounterparty, message.rawCounterparty)
            } ?: candidates.firstOrNull {
                it.rawCounterparty.isNullOrBlank() || message.rawCounterparty.isNullOrBlank()
            } ?: continue
            if (mutations.mergeDuplicate(id, survivor.id).changed > 0) {
                claimed += survivor.id
                merged++
            }
        }
        return merged
    }
}
