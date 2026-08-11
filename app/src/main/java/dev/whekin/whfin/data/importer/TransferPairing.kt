package dev.whekin.whfin.data.importer

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TransferGroupEntity
import dev.whekin.whfin.data.db.TransferGroupType
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.statement.StatementParsers
import java.time.LocalDate
import java.time.ZoneId

/**
 * Joins the two halves of one movement between the owner's own accounts.
 *
 * A statement covers a single currency ledger, so a transfer or a conversion arrives as two separate
 * files and can only be recognised as one movement afterwards, from stored rows. Until both halves
 * are paired the feed honestly shows them apart; pairing them later is what stops one transfer from
 * reading as an expense on one side and an income on the other.
 */
internal class TransferPairing(private val db: WhfinDatabase, private val zone: ZoneId) {

    /** Pairs around the period a freshly imported statement covers. */
    suspend fun pairWithinPeriod(account: AccountEntity, from: LocalDate?, to: LocalDate?) {
        val groupId = account.groupId ?: return
        val fromMillis = (from ?: return).minusDays(3).atMillis()
        val toMillis = (to ?: return).plusDays(4).atMillis() - 1
        pair(groupId, fromMillis, toMillis)
    }

    /**
     * Re-runs pairing over recent history for every bank.
     *
     * A currency ledger imported later can reveal the true other half of a conversion that was
     * previously matched to the closest available row, so old groups are rebuilt rather than trusted.
     */
    suspend fun repairAll() {
        db.accountDao().allActive()
            .filter { it.type == AccountType.BANK && it.groupId != null }
            .groupBy { it.groupId!! }
            .forEach { (groupId, _) ->
                db.withTransaction {
                    pair(
                        groupId = groupId,
                        // Full-history imports can hold conversions well beyond the former
                        // three-year startup window. Rebuilding only the recent side left old
                        // boundary pairs permanently single-legged.
                        fromMillis = 0L,
                        toMillis = LocalDate.now().plusDays(2).atMillis() - 1,
                        conversionsOnly = true,
                    )
                }
            }
    }

    private suspend fun pair(
        groupId: Long,
        fromMillis: Long,
        toMillis: Long,
        conversionsOnly: Boolean = false,
    ) {
        // Existing conversions are dissolved first: a later statement may hold a closer, correct leg
        // than the best candidate available when they were first matched.
        val existingGroupIds = db.transactionDao().conversionTransfers(groupId, fromMillis, toMillis)
            .mapNotNull { it.transferGroupId }
            .distinct()
        if (existingGroupIds.isNotEmpty()) {
            // A period boundary may cut through a pair. Clear by group, not by the rows returned for
            // this window, or the other side stays attached to an old group on its own.
            db.transactionDao().clearTransferGroups(existingGroupIds)
            db.transactionDao().deleteTransferGroups(existingGroupIds)
        }

        val accountsById = db.accountDao().byGroup(groupId).associateBy { it.id }
        val candidates = (
            if (conversionsOnly) db.transactionDao().conversionTransfers(groupId, fromMillis, toMillis)
            else db.transactionDao().ungroupedTransfers(groupId, fromMillis, toMillis)
            )
            // Deterministic order, and rows that name the other account go first: a stated IBAN is
            // stronger evidence than proximity in time.
            .sortedWith(
                compareBy<TransactionEntity> { it.occurredAt }
                    .thenBy { if (it.counterpartyIban != null) 0 else 1 },
            )
            .toMutableList()

        while (candidates.isNotEmpty()) {
            val first = candidates.removeAt(0)
            val matchIndex = candidates.indices
                .filter { index -> matches(first, candidates[index], accountsById, conversionsOnly) }
                .minByOrNull { index -> distance(first, candidates[index], accountsById) }
                ?: continue
            val second = candidates.removeAt(matchIndex)
            val transferGroupId = db.transactionDao().insertTransferGroup(
                TransferGroupEntity(
                    type = if (first.currency == second.currency) TransferGroupType.TRANSFER
                    else TransferGroupType.CONVERSION,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            db.transactionDao().setTransferGroup(first.id, transferGroupId)
            db.transactionDao().setTransferGroup(second.id, transferGroupId)
        }
    }

    private fun matches(
        first: TransactionEntity,
        other: TransactionEntity,
        accountsById: Map<Long, AccountEntity>,
        conversionsOnly: Boolean,
    ): Boolean {
        if (other.accountId == first.accountId) return false
        val mutualIbanEvidence = first.counterpartyIban == accountsById[other.accountId]?.iban ||
            other.counterpartyIban == accountsById[first.accountId]?.iban
        val oppositeSigns = (first.amountMinor < 0) != (other.amountMinor < 0)
        val distance = kotlin.math.abs(other.occurredAt - first.occurredAt)

        val conversion = first.currency != other.currency && oppositeSigns && mutualIbanEvidence &&
            (conversionsOnly || isExchange(first) || isExchange(other)) &&
            distance <= CONVERSION_WINDOW_MILLIS
        if (conversionsOnly) return conversion

        val plainTransfer = other.currency == first.currency &&
            other.amountMinor == -first.amountMinor &&
            distance <= TRANSFER_WINDOW_MILLIS
        return plainTransfer || conversion
    }

    /** Mutual IBAN evidence wins over closeness in time; otherwise the nearest row is taken. */
    private fun distance(
        first: TransactionEntity,
        other: TransactionEntity,
        accountsById: Map<Long, AccountEntity>,
    ): Long {
        val mutual = first.counterpartyIban == accountsById[other.accountId]?.iban &&
            other.counterpartyIban == accountsById[first.accountId]?.iban
        return (if (mutual) 0L else MUTUAL_IBAN_BONUS_MILLIS) +
            kotlin.math.abs(other.occurredAt - first.occurredAt)
    }

    /** Pairing runs over stored rows, so the vocabulary of a conversion comes from the adapters. */
    private fun isExchange(tx: TransactionEntity): Boolean = tx.note?.let { note ->
        StatementParsers.conversionNoteMarkers.any { note.contains(it, ignoreCase = true) }
    } == true

    private fun LocalDate.atMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

    private companion object {
        const val TRANSFER_WINDOW_MILLIS = 3L * 24 * 60 * 60 * 1000
        const val CONVERSION_WINDOW_MILLIS = 12L * 60 * 60 * 1000
        const val MUTUAL_IBAN_BONUS_MILLIS = 10L * 24 * 60 * 60 * 1000
    }
}
