package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CounterpartyProfile
import dev.whekin.whfin.data.db.PersonEntity
import kotlin.math.ln
import kotlin.math.pow

/**
 * Someone money can go to or come from, as the composer needs to show them.
 *
 * [merchantId] is the identity the ledger already stores; a candidate without one is a name that
 * exists as a person but has never been written on a row, and it becomes a merchant the first time
 * it is used. [categoryId] is where this counterparty is usually filed — the answer the composer
 * borrows so the same payment is not categorised by hand every month.
 */
data class CounterpartyCandidate(
    val name: String,
    val merchantId: Long? = null,
    val personId: Long? = null,
    val categoryId: Long? = null,
    val usageCount: Int = 0,
    val lastUsedAt: Long? = null,
)

/** Which side of the ledger the composer is asking about. */
enum class CounterpartyDirection { OUTGOING, INCOMING }

/**
 * The order the composer offers counterparties in.
 *
 * The ranking is the categories' own, one dimension over: how often, decayed by how long ago
 * (half-life 60 days), so the shop of last summer sinks by winter without ever being deleted. The
 * chosen category is the second dimension and it works in both directions — asked inside "Eating
 * out", the café comes first; picking the café first fills "Eating out" for free.
 *
 * A person the ledger has never paid keeps a small floor so they stay findable, but they cannot
 * outrank someone actually paid last week: an address book is not evidence.
 */
object CounterpartySuggester {
    fun candidates(
        usage: List<CounterpartyProfile>,
        people: List<PersonEntity>,
        direction: CounterpartyDirection,
    ): List<CounterpartyCandidate> {
        val used = usage.mapNotNull { row ->
            val count = when (direction) {
                CounterpartyDirection.OUTGOING -> row.expenseCount
                CounterpartyDirection.INCOMING -> row.incomeCount
            }
            // A name only ever seen on the other side of the ledger is not evidence for this one:
            // an employer is not somebody you pay. It stays reachable through search.
            if (count <= 0) return@mapNotNull null
            CounterpartyCandidate(
                name = row.displayName,
                merchantId = row.merchantId,
                categoryId = row.categoryId,
                usageCount = count,
                lastUsedAt = row.latestAt,
            )
        }
        val knownNames = used.mapTo(mutableSetOf()) { it.name.normalizedForMatch() }
        val fromPeople = people
            .filterNot { it.name.normalizedForMatch() in knownNames }
            .map { CounterpartyCandidate(name = it.name, personId = it.id) }
        return used + fromPeople
    }

    fun rank(
        candidates: List<CounterpartyCandidate>,
        selectedCategoryId: Long?,
        nowMillis: Long,
    ): List<CounterpartyCandidate> = candidates.sortedWith(
        compareByDescending<CounterpartyCandidate> { score(it, selectedCategoryId, nowMillis) }
            .thenBy { it.name.lowercase() },
    )

    /** Substring search over the same set, so a name never seen this year is still one field away. */
    fun search(candidates: List<CounterpartyCandidate>, query: String): List<CounterpartyCandidate> {
        val needle = query.trim().normalizedForMatch()
        if (needle.isEmpty()) return candidates
        return candidates.filter { it.name.normalizedForMatch().contains(needle) }
    }

    private fun score(
        candidate: CounterpartyCandidate,
        selectedCategoryId: Long?,
        nowMillis: Long,
    ): Double {
        val ageDays = candidate.lastUsedAt
            ?.let { ((nowMillis - it).coerceAtLeast(0L) / DAY_MILLIS).toDouble() }
        val frequency = if (candidate.usageCount <= 0 || ageDays == null) {
            PERSON_FLOOR
        } else {
            ln(1.0 + candidate.usageCount) * 0.5.pow(ageDays / HALF_LIFE_DAYS)
        }
        // Habits cluster in time: whoever was paid this morning is a likelier answer than the shop
        // of many quiet months, so the last few days carry their own term rather than being one
        // more sample inside the long decay.
        val recency = ageDays?.let { RECENT_WEIGHT * 0.5.pow(it / RECENT_HALF_LIFE_DAYS) } ?: 0.0
        val categoryBoost = when {
            selectedCategoryId == null -> 1.0
            candidate.categoryId == selectedCategoryId -> CATEGORY_MATCH_BOOST
            // Not a penalty for being unfiled: a name with no category yet is exactly the one the
            // person may be about to file here.
            candidate.categoryId == null -> 1.0
            else -> CATEGORY_MISMATCH_DAMPING
        }
        return (frequency + recency) * categoryBoost
    }

    private fun String.normalizedForMatch(): String = trim().lowercase().replace('ё', 'е')

    private const val HALF_LIFE_DAYS = 60.0
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    private const val CATEGORY_MATCH_BOOST = 2.5
    private const val CATEGORY_MISMATCH_DAMPING = 0.45
    private const val PERSON_FLOOR = 0.15
    private const val RECENT_WEIGHT = 1.0
    private const val RECENT_HALF_LIFE_DAYS = 3.0
}
