package dev.whekin.whfin.data.categorization

import android.content.Context
import android.content.SharedPreferences
import dev.whekin.whfin.data.sync.BankSyncStatus
import dev.whekin.whfin.data.sync.SyncPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** Installation-local reminder for category types that arrived after the setup choice. */
class DeferredCategoryReview(
    private val preferences: SharedPreferences,
    proposals: Flow<List<CategoryProposals.Proposal>>,
    scope: CoroutineScope,
) {
    constructor(context: Context, proposals: Flow<List<CategoryProposals.Proposal>>, scope: CoroutineScope) : this(
        context.getSharedPreferences("deferred_category_review", Context.MODE_PRIVATE), proposals, scope)

    private val armed = MutableStateFlow(preferences.getBoolean(ARMED, false))
    private val seen = MutableStateFlow(preferences.getStringSet(SEEN, emptySet()).orEmpty().toSet())

    /** Null means the first database snapshot has not arrived; never present it as no suggestions. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val pending: StateFlow<List<CategoryProposals.Proposal>?> = armed.flatMapLatest { enabled ->
        if (!enabled) flowOf(emptyList())
        else combine(proposals, seen) { all, reviewed -> all.filterNot { key(it) in reviewed } }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun arm() {
        if (armed.value || preferences.edit().putBoolean(ARMED, true).commit()) armed.value = true
    }

    /** Viewing or setting aside an offer quiets that category type until a genuinely new one arrives. */
    fun markSeen(proposals: List<CategoryProposals.Proposal>) {
        val keys = proposals.map(::key).toSet()
        if (keys.isEmpty()) return
        val updated = seen.value + keys
        if (updated == seen.value || preferences.edit().putStringSet(SEEN, updated).commit()) seen.value = updated
    }

    /** A restored ledger must not inherit decisions about a previous ledger on this installation. */
    fun resetAfterRestore() {
        if (preferences.edit().clear().commit()) {
            armed.value = false
            seen.value = emptySet()
        }
    }

    private fun key(proposal: CategoryProposals.Proposal): String =
        "${proposal.definition.kind}:${proposal.definition.icon}"

    private companion object {
        const val ARMED = "armed_after_setup"
        const val SEEN = "seen_category_types"
    }
}

/** Wait for active reads and explicit interruptions; already saved proposals remain honest evidence. */
fun deferredCategoryReviewVisible(
    pending: List<CategoryProposals.Proposal>?,
    bankStatuses: List<BankSyncStatus>,
): Boolean = !pending.isNullOrEmpty() && bankStatuses.none { it.active || it.phase == SyncPhase.INTERRUPTED }
