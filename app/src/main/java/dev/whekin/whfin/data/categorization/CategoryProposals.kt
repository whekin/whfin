package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.MerchantEntity

/**
 * Categories a ledger has already earned.
 *
 * After a bank sync WHFIN knows who the money went to, so it can stop guessing what someone's life
 * looks like and read it instead: a category is offered when recognized merchants in the history
 * would fill it, and the number of transactions behind it is shown so the offer can be judged rather
 * than trusted. Everything else stays absent — an empty category is not neutral, it costs a line in
 * every picker and every monthly list forever.
 *
 * Nothing is created here. The proposal is a question; only the user's answer writes anything.
 */
object CategoryProposals {

    data class Proposal(
        val definition: CategoryCatalog.Definition,
        /** Transactions whose merchant a local rule already maps to this category. */
        val transactionCount: Int,
    )

    /**
     * @param usageByMerchantId how many transactions each merchant accounts for.
     */
    fun from(
        merchants: List<MerchantEntity>,
        usageByMerchantId: Map<Long, Int>,
        existing: List<CategoryEntity>,
        minimumTransactions: Int = 1,
    ): List<Proposal> {
        val present = existing.map { it.icon to it.kind }.toSet()
        val evidence = mutableMapOf<Pair<String, CategoryKind>, Int>()

        merchants.forEach { merchant ->
            val icon = GeorgiaMerchantPreset.iconFor(merchant.normalizedKey) ?: return@forEach
            val key = icon to CategoryKind.EXPENSE
            if (key in present) return@forEach
            evidence[key] = (evidence[key] ?: 0) + (usageByMerchantId[merchant.id] ?: 0)
        }

        return evidence
            .filter { (_, count) -> count >= minimumTransactions }
            .mapNotNull { (key, count) ->
                val (icon, kind) = key
                CategoryCatalog.byIcon(icon, kind)?.let { Proposal(it, count) }
            }
            .sortedWith(compareByDescending<Proposal> { it.transactionCount }.thenBy { it.definition.en })
    }

    /**
     * Categories that exist but nothing has ever been filed under.
     *
     * Offered for removal, never removed: a category kept empty on purpose is a plan, and the ledger
     * cannot tell that apart from one that was seeded and forgotten.
     */
    fun unused(existing: List<CategoryEntity>, usageByCategoryId: Map<Long, Int>): List<CategoryEntity> =
        existing.filter { !it.isSystem && (usageByCategoryId[it.id] ?: 0) == 0 }
}
