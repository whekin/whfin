package dev.whekin.whfin.data.transfer

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TransferGroupEntity
import dev.whekin.whfin.data.db.TransferGroupType
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import kotlin.math.abs

/** How far apart two sides of the same movement may sit while still being offered as a pair. */
private const val SEARCH_WINDOW_MILLIS = 4 * 86_400_000L

/** One side of a movement, as it will be shown next to the row the owner started from. */
data class OwnTransferSide(val transaction: TransactionEntity, val account: AccountEntity?)

/**
 * Money the owner moved between accounts they already own, joined by hand.
 *
 * Two things make this necessary and neither is about crypto. Money can leave one account and
 * arrive in another through somebody in the middle — an exchange, a neighbour, a kiosk — where the
 * amounts, the currencies and the dates all differ and nothing in either row names the other. And
 * money can arrive somewhere the app cannot read at all, so one side of the movement simply does
 * not exist as a row until somebody writes it.
 *
 * The link is never inferred. Closeness in time only decides what is *offered*; the owner reads both
 * amounts, both accounts and both dates and says yes. Both sides keep their own amount, currency,
 * date and provenance — nothing is converted, averaged or rewritten — and joining them only stops
 * both from being counted as new money. The difference between what left and what arrived is left
 * as the difference it is: a fee is only a fee when somebody says so, and a spread is not a fee.
 */
object OwnTransfers {

    /**
     * Rows on other accounts that could be the far side of [transaction].
     *
     * Direction is the only hard rule: money that left must be met by money that arrived, on a
     * different account. Everything else — dates, amounts, currencies — is left to the person, who
     * knows what they did.
     */
    fun candidatesFor(
        transaction: TransactionEntity,
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        allocatedIds: Set<Long> = emptySet(),
    ): List<OwnTransferSide> {
        val byId = accounts.associateBy { it.id }
        if (!transaction.isLinkable(byId, allocatedIds)) return emptyList()
        return transactions
            .filter { candidate ->
                candidate.id != transaction.id &&
                    candidate.accountId != transaction.accountId &&
                    candidate.isLinkable(byId, allocatedIds) &&
                    (candidate.amountMinor > 0) != (transaction.amountMinor > 0) &&
                    abs(candidate.occurredAt - transaction.occurredAt) <= SEARCH_WINDOW_MILLIS
            }
            .sortedBy { abs(it.occurredAt - transaction.occurredAt) }
            .map { OwnTransferSide(it, byId[it.accountId]) }
    }

    /**
     * A row can be joined only while it is plainly its own, un-spoken-for movement.
     *
     * A split or a debt already says who the money belongs to, and a row inside another group is
     * already part of a movement. Voided and archived rows describe the past and must not be
     * rearranged.
     */
    private fun TransactionEntity.isLinkable(
        accounts: Map<Long, AccountEntity>,
        allocatedIds: Set<Long>,
    ): Boolean = !isVoided && !isTransfer && transferGroupId == null && id !in allocatedIds &&
        amountMinor != 0L && source != TxSource.ADJUSTMENT &&
        accounts[accountId]?.isArchived == false
}

/**
 * Writes and withdraws the owner's answer that several rows are one movement.
 *
 * Everything here is reversible, because the answer is a judgement and judgements are revised. The
 * rows themselves are never edited by linking, so unlinking has nothing to undo: only the group
 * that held them together goes away.
 */
class OwnTransferRepository(private val db: WhfinDatabase) {

    /**
     * Joins two or more rows into one movement.
     *
     * More than two sides is a real shape, not a generalisation for its own sake: one withdrawal
     * often comes back as several credits, and forcing those into separate pairs would either
     * invent legs that do not exist or leave the remainder looking like income.
     */
    suspend fun link(transactionIds: List<Long>): Long = db.withTransaction {
        require(transactionIds.size >= 2) { "A movement needs at least two sides" }
        require(transactionIds.distinct().size == transactionIds.size) { "A row cannot be two sides" }
        val rows = transactionIds.map { id ->
            requireNotNull(db.transactionDao().byId(id)) { "That movement no longer exists" }
        }
        val accounts = db.accountDao().allActive()
        val allocated = allocatedIds()
        val first = rows.first()
        val reachable = OwnTransfers
            .candidatesFor(first, rows.drop(1), accounts, allocated)
            .mapTo(mutableSetOf()) { it.transaction.id }
        require(rows.drop(1).all { it.id in reachable }) {
            "The selected movements can no longer be linked"
        }
        val groupId = db.transactionDao().insertTransferGroup(
            TransferGroupEntity(type = TransferGroupType.OWN_LINK, createdAt = System.currentTimeMillis()),
        )
        db.transactionDao().attachToTransferGroup(transactionIds, groupId)
        groupId
    }

    /**
     * Records the side of a movement no statement will ever bring.
     *
     * Cash handed over has no ledger behind it, so somebody has to write it down; the row is marked
     * `MANUAL` exactly so it never looks like something a bank confirmed. It is created and linked
     * in one transaction — a half-written movement would read as new money on one side.
     */
    suspend fun linkToNewLeg(
        transactionId: Long,
        accountId: Long,
        amountMinor: Long,
        currency: String,
        occurredAt: Long,
        note: String? = null,
    ): Long = db.withTransaction {
        val known = requireNotNull(db.transactionDao().byId(transactionId)) { "That movement no longer exists" }
        require(accountId != known.accountId) { "Both sides cannot be the same account" }
        require(amountMinor != 0L) { "The other side needs an amount" }
        require((amountMinor > 0) != (known.amountMinor > 0)) {
            "The other side must be the opposite direction"
        }
        val newId = db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                amountMinor = amountMinor,
                currency = currency.uppercase(),
                occurredAt = occurredAt,
                status = TxStatus.MANUAL,
                source = TxSource.MANUAL,
                note = note?.takeIf(String::isNotBlank),
                createdAt = System.currentTimeMillis(),
            ),
        )
        link(listOf(transactionId, newId))
    }

    /** Takes the link back. Both rows return to being ordinary movements, unchanged. */
    suspend fun unlink(groupId: Long) = db.withTransaction {
        val group = db.transactionDao().transferGroupById(groupId)
        require(group?.type == TransferGroupType.OWN_LINK) { "Only a hand-made link can be undone here" }
        val rows = db.transactionDao().byTransferGroup(groupId)
        rows.forEach { db.transactionDao().update(it.copy(transferGroupId = null, isTransfer = false)) }
        db.transactionDao().deleteTransferGroups(listOf(groupId))
    }

    private suspend fun allocatedIds(): Set<Long> =
        db.transactionAllocationDao().allForIntegrity().mapTo(mutableSetOf()) { it.transactionId } +
            db.debtDao().allEventsForIntegrity().mapNotNull { it.transactionId }
}
