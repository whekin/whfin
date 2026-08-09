package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.statement.StatementRow

/**
 * Decides whether a statement line is the same payment as something WHFIN already recorded.
 *
 * The two-layer model depends on this: an SMS is a draft that arrives in seconds, the statement is
 * the truth that arrives days later, and the same purchase must end up as one row. The merchant and
 * the purchase date carry the match; the amount only breaks ties, because a card paid abroad is
 * charged in another currency than the SMS announced and an exact-amount rule would split the pair
 * into two rows.
 */
internal object StatementReconciler {

    /**
     * The draft this statement line confirms, or null when nothing matches unambiguously.
     *
     * Ambiguity is deliberately left unresolved: two coffees at the same shop on the same day are
     * indistinguishable, and picking one at random would silently overwrite the wrong draft.
     */
    fun match(row: StatementRow, candidates: List<TransactionEntity>): TransactionEntity? {
        val wanted = MerchantNormalizer.normalize(row.merchantRaw ?: return null)
        if (wanted.isEmpty()) return null
        val sameMerchant = candidates.filter { candidate ->
            candidate.rawCounterparty?.let(MerchantNormalizer::normalize) == wanted
        }
        return sameMerchant.filter { it.amountMinor == row.amountMinor }.singleOrNull()
            ?: sameMerchant.singleOrNull()
    }
}
