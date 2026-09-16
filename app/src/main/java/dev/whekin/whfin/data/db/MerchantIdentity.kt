package dev.whekin.whfin.data.db

import androidx.room.withTransaction
import dev.whekin.whfin.data.importer.GeorgianLatin
import dev.whekin.whfin.data.importer.GeorgianRomanization.isGeorgian

/**
 * One counterparty, however their bank spelled them.
 *
 * The alphabet of a name on a statement line does not belong to the statement: it is whatever the
 * counterparty's own bank holds on file. Credo registers its clients in Georgian and Bank of
 * Georgia and TBC in Latin, so one person holding accounts at two banks — or moving between them —
 * arrives under two names that share no character. A dictionary keyed by what the bank wrote
 * therefore learned each spelling separately: a category taught on one never reached the other, and
 * the same person was counted twice everywhere counterparties are listed.
 *
 * The key itself stays untouched. [dev.whekin.whfin.data.importer.MerchantNormalizer.normalize] is
 * what merchant memory is keyed by, and rewriting it would orphan every category the owner has
 * taught. Instead the shared skeleton becomes a second way in, stored in `merchant_aliases` — whose
 * unique `pattern` index then states the invariant in the schema itself: one skeleton, one
 * counterparty.
 */

/** One counterparty found under several keys: which row stays, and which retire into it. */
data class MerchantMerge(
    val survivor: MerchantEntity,
    val retired: List<MerchantEntity>,
    val skeleton: String,
)

/**
 * What a pass over the dictionary found.
 *
 * [ambiguous] skeletons are left exactly as they are, and deliberately carry no finding: two
 * spellings the owner has filed under different categories are evidence that the skeleton was too
 * coarse here, not evidence of a contradiction. Merging them would move money between categories on
 * a guess. Agreeing the two categories by hand is how the owner approves such a merge — the next
 * pass then has nothing to weigh and joins them.
 */
data class MerchantIdentityPlan(
    val merges: List<MerchantMerge>,
    val ambiguous: List<String>,
)

/**
 * Which dictionary entries are the same counterparty written in two alphabets.
 *
 * Only cross-script pairs are joined. Within one alphabet a bank is consistent, so two keys that
 * collapse to the same skeleton are far more likely two names than one name written twice: the
 * skeleton exists to drop the distinctions romanization cannot carry — `თ` and `ტ` both become `t`,
 * `კ` and `ქ` both become `k` — and dropping them inside Georgian would marry strangers.
 *
 * The survivor is the row carrying a category, because that is the owner's own teaching; failing
 * that the one with more operations behind it, because its name is the one already being read.
 */
fun planCounterpartySpellings(
    merchants: List<MerchantEntity>,
    usage: Map<Long, Int>,
): MerchantIdentityPlan {
    val merges = mutableListOf<MerchantMerge>()
    val ambiguous = mutableListOf<String>()
    merchants.groupBy { GeorgianLatin.skeleton(it.normalizedKey) }.forEach { (skeleton, group) ->
        if (skeleton.isBlank() || group.size < 2) return@forEach
        val georgian = group.count { isGeorgian(it.normalizedKey) }
        if (georgian == 0 || georgian == group.size) {
            ambiguous += skeleton
            return@forEach
        }
        if (group.mapNotNull { it.categoryId }.distinct().size > 1) {
            ambiguous += skeleton
            return@forEach
        }
        val survivor = group.sortedWith(
            compareByDescending<MerchantEntity> { it.categoryId != null }
                .thenByDescending { usage[it.id] ?: 0 }
                .thenBy { it.id },
        ).first()
        merges += MerchantMerge(survivor, group.filter { it.id != survivor.id }, skeleton)
    }
    return MerchantIdentityPlan(merges, ambiguous)
}

/**
 * Joins counterparties the bank wrote in two alphabets, and returns how many rows retired.
 *
 * Operations move to the survivor, and those of them still without a category inherit the
 * survivor's — the same rule [dev.whekin.whfin.data.db.TransactionDao.categorizeUnassignedForMerchant]
 * already applies when a merchant becomes recognizable, so nothing the owner set by hand is
 * overwritten. Every surviving key then gets its skeleton recorded, which is what stops the second
 * spelling from ever arriving as a second counterparty again.
 */
suspend fun WhfinDatabase.repairCounterpartySpellings(): Int {
    val merchants = merchantDao().all()
    if (merchants.isEmpty()) return 0
    val usage = merchantDao().usageCounts().associate { it.merchantId to it.transactionCount }
    val plan = planCounterpartySpellings(merchants, usage)
    val retiredIds = plan.merges.flatMap { merge -> merge.retired.map { it.id } }.toSet()
    withTransaction {
        plan.merges.forEach { merge ->
            merge.retired.forEach { retired ->
                transactionDao().repointMerchant(retired.id, merge.survivor.id)
                // The retired row's own aliases carry the same skeleton, so they would collide with
                // the survivor's on update; CASCADE drops them and the survivor records it below.
                merchantDao().deleteMerchant(retired.id)
            }
            merge.survivor.categoryId?.let { categoryId ->
                transactionDao().categorizeUnassignedForMerchant(merge.survivor.id, categoryId)
            }
        }
        merchants.asSequence()
            .filter { it.id !in retiredIds }
            .map { it to GeorgianLatin.skeleton(it.normalizedKey) }
            .filter { (_, skeleton) -> skeleton.isNotBlank() && skeleton !in plan.ambiguous }
            .forEach { (merchant, skeleton) ->
                merchantDao().insertAlias(
                    MerchantAliasEntity(merchantId = merchant.id, pattern = skeleton),
                )
            }
    }
    return retiredIds.size
}
