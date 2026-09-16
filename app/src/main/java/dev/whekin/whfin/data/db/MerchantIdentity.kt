package dev.whekin.whfin.data.db

import androidx.room.withTransaction
import dev.whekin.whfin.data.importer.GeorgianLatin
import dev.whekin.whfin.data.importer.GeorgianRomanization.isGeorgian

/**
 * One counterparty, however their bank wrote them down.
 *
 * A name on a statement line is not an identity. The bank that holds the counterparty's account
 * decides how it is printed, and the ways it varies have no end: two alphabets, because Credo
 * registers its clients in Georgian while TBC and Bank of Georgia use Latin; two romanizations of
 * the same Georgian name; the surname first or last; a legal form or a tax number glued on; an
 * abbreviation on one bank and the full name on another; and, on a transfer, sometimes whatever the
 * sender typed into the form. A dictionary keyed by what the bank wrote learns each of these
 * separately, so a category taught on one never reaches the others and one person is counted many
 * times wherever counterparties are listed.
 *
 * Two kinds of proof are accepted here, and nothing else.
 *
 * The counterparty's **account** is the strong one: an IBAN belongs to one payee, so two rows paid
 * into the same account are the same payee no matter how differently they are spelled. It is the
 * same principle `counterparty_rules` already uses to answer a category by the recipient's account
 * rather than by the writing of their name.
 *
 * The shared **skeleton across alphabets** is the weak one, for rows that carry no account at all.
 *
 * The key itself is never rewritten: [dev.whekin.whfin.data.importer.MerchantNormalizer.normalize]
 * is what merchant memory is keyed by, and coarsening it would orphan every category the owner has
 * taught. Instead every retired spelling is recorded in `merchant_aliases`, whose unique `pattern`
 * index states the invariant in the schema: one spelling, one counterparty.
 */

/**
 * How many counterparty accounts one name may answer for before it stops being evidence.
 *
 * A person with accounts at two banks is ordinary; the owner's ledger has five such names and none
 * wider. A name standing over many accounts is not a person but a label — and since accounts are
 * joined transitively through the names on them, one such label would marry everybody it ever paid.
 */
private const val MAX_ACCOUNTS_PER_NAME = 4

/** One counterparty found under several keys: which row stays, and which retire into it. */
data class MerchantMerge(
    val survivor: MerchantEntity,
    val retired: List<MerchantEntity>,
)

/**
 * What a pass over the dictionary found.
 *
 * A group whose rows carry different categories is left exactly as it is and deliberately raises no
 * finding. Even proven by account, merging it would move money between categories on WHFIN's
 * initiative: the owner filed those rows apart, and only they know whether that was a mistake.
 * Agreeing the two categories by hand is how such a merge gets approved — the next pass then has
 * nothing to weigh and joins them.
 */
data class MerchantIdentityPlan(val merges: List<MerchantMerge>)

/**
 * Which dictionary entries are the same counterparty.
 *
 * Accounts join names transitively, which is what lets a person with two banks pull both of their
 * spellings together, and is also why [MAX_ACCOUNTS_PER_NAME] exists.
 *
 * The skeleton joins only across alphabets. Within one alphabet a bank is consistent, so two keys
 * that collapse onto the same skeleton are far more likely two names than one name written twice:
 * the skeleton drops what romanization cannot carry — `თ` and `ტ` both become `t`, `კ` and `ქ` both
 * become `k` — and dropping that inside Georgian would marry strangers.
 *
 * The survivor is the row carrying a category, because that is the owner's own teaching; failing
 * that the one with more operations behind it, because its name is the one already being read.
 */
fun planCounterpartySpellings(
    merchants: List<MerchantEntity>,
    usage: Map<Long, Int>,
    accounts: Map<Long, Set<String>>,
): MerchantIdentityPlan {
    val union = DisjointNames(merchants.map { it.id })

    accounts.asSequence()
        .filter { (_, ibans) -> ibans.size <= MAX_ACCOUNTS_PER_NAME }
        .flatMap { (merchantId, ibans) -> ibans.map { it to merchantId } }
        .groupBy({ it.first }, { it.second })
        .values
        .forEach { sharing -> sharing.zipWithNext().forEach { (a, b) -> union.join(a, b) } }

    merchants.groupBy { GeorgianLatin.skeleton(it.normalizedKey) }
        .forEach { (skeleton, group) ->
            if (skeleton.isBlank() || group.size < 2) return@forEach
            val georgian = group.count { isGeorgian(it.normalizedKey) }
            if (georgian == 0 || georgian == group.size) return@forEach
            group.zipWithNext().forEach { (a, b) -> union.join(a.id, b.id) }
        }

    val byId = merchants.associateBy { it.id }
    val merges = union.groups().mapNotNull { ids ->
        if (ids.size < 2) return@mapNotNull null
        val group = ids.mapNotNull(byId::get)
        if (group.mapNotNull { it.categoryId }.distinct().size > 1) return@mapNotNull null
        val survivor = group.sortedWith(
            compareByDescending<MerchantEntity> { it.categoryId != null }
                .thenByDescending { usage[it.id] ?: 0 }
                .thenBy { it.id },
        ).first()
        MerchantMerge(survivor, group.filter { it.id != survivor.id })
    }
    return MerchantIdentityPlan(merges)
}

/**
 * Joins counterparties their banks wrote down differently, and returns how many rows retired.
 *
 * Operations move to the survivor, and those of them still without a category inherit the
 * survivor's — the same rule [TransactionDao.categorizeUnassignedForMerchant] already applies when a
 * merchant becomes recognizable, so nothing the owner set by hand is overwritten.
 */
suspend fun WhfinDatabase.repairCounterpartySpellings(): Int {
    val merchants = merchantDao().all()
    if (merchants.isEmpty()) return 0
    val usage = merchantDao().usageCounts().associate { it.merchantId to it.transactionCount }
    val accounts = merchantDao().counterpartyAccounts()
        .groupBy({ it.merchantId }, { it.counterpartyIban.filterNot(Char::isWhitespace).uppercase() })
        .mapValues { (_, ibans) -> ibans.toSet() }
    val plan = planCounterpartySpellings(merchants, usage, accounts)
    if (plan.merges.isEmpty()) {
        withTransaction { recordSpellings(merchants, emptyMap()) }
        return 0
    }
    val survivorOf = plan.merges.flatMap { merge ->
        merge.retired.map { it.id to merge.survivor.id }
    }.toMap()
    withTransaction {
        plan.merges.forEach { merge -> applyMerge(merge) }
        recordSpellings(merchants, survivorOf)
    }
    return survivorOf.size
}

/**
 * Joins two counterparties on the owner's word alone.
 *
 * Some spellings carry no proof at all — a transfer that names no account, an abbreviation on one
 * bank against the full name on another — and no rule can join those without guessing. This is the
 * answer for them, and equally for a pair the automatic pass refused because their categories
 * disagreed: here the disagreement is being resolved, not ignored.
 */
suspend fun WhfinDatabase.mergeCounterparties(firstId: Long, secondId: Long): Boolean {
    if (firstId == secondId) return false
    val merchants = merchantDao().all()
    val pair = merchants.filter { it.id == firstId || it.id == secondId }
    if (pair.size != 2) return false
    val usage = merchantDao().usageCounts().associate { it.merchantId to it.transactionCount }
    val survivor = pair.sortedWith(
        compareByDescending<MerchantEntity> { it.categoryId != null }
            .thenByDescending { usage[it.id] ?: 0 }
            .thenBy { it.id },
    ).first()
    val merge = MerchantMerge(survivor, pair.filter { it.id != survivor.id })
    withTransaction {
        applyMerge(merge)
        recordSpellings(merchants, merge.retired.associate { it.id to survivor.id })
    }
    return true
}

/** Which of two counterparties keeps its name when they are joined, so the screen can say so. */
fun survivingCounterparty(first: MerchantEntity, second: MerchantEntity, usage: Map<Long, Int>) =
    listOf(first, second).sortedWith(
        compareByDescending<MerchantEntity> { it.categoryId != null }
            .thenByDescending { usage[it.id] ?: 0 }
            .thenBy { it.id },
    ).first()

private suspend fun WhfinDatabase.applyMerge(merge: MerchantMerge) {
    merge.retired.forEach { retired ->
        transactionDao().repointMerchant(retired.id, merge.survivor.id)
        // The retired row's aliases would collide with the survivor's on update; CASCADE drops them
        // and every spelling is written back below from the merchant list read before the merge.
        merchantDao().deleteMerchant(retired.id)
    }
    merge.survivor.categoryId?.let { categoryId ->
        transactionDao().categorizeUnassignedForMerchant(merge.survivor.id, categoryId)
    }
}

/**
 * Records every spelling that now belongs to a surviving counterparty.
 *
 * A spelling two surviving counterparties both answer to is recorded for neither: two Georgian names
 * differing only by aspiration collapse onto one skeleton, and letting whichever was written first
 * claim it would hand it every future arrival of the other.
 */
private suspend fun WhfinDatabase.recordSpellings(
    merchants: List<MerchantEntity>,
    survivorOf: Map<Long, Long>,
) {
    val claims = mutableMapOf<String, MutableSet<Long>>()
    merchants.forEach { merchant ->
        val owner = survivorOf[merchant.id] ?: merchant.id
        val skeleton = GeorgianLatin.skeleton(merchant.normalizedKey)
        if (skeleton.isNotBlank()) claims.getOrPut(skeleton) { mutableSetOf() } += owner
    }
    claims.forEach { (skeleton, owners) ->
        val owner = owners.singleOrNull() ?: return@forEach
        merchantDao().insertAlias(MerchantAliasEntity(merchantId = owner, pattern = skeleton))
    }
}

/** Groups ids that proof has connected, directly or through another name. */
private class DisjointNames(ids: List<Long>) {
    private val parent = ids.associateWith { it }.toMutableMap()

    private fun root(id: Long): Long {
        var current = id
        while (parent[current] != current) {
            val next = parent.getValue(current)
            parent[current] = parent.getValue(next)
            current = parent.getValue(current)
        }
        return current
    }

    fun join(first: Long, second: Long) {
        if (first !in parent || second !in parent) return
        val a = root(first)
        val b = root(second)
        if (a != b) parent[maxOf(a, b)] = minOf(a, b)
    }

    fun groups(): Collection<List<Long>> = parent.keys.groupBy(::root).values
}
