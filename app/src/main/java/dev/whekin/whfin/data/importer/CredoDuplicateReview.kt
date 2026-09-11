package dev.whekin.whfin.data.importer

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.*

/** Suggestions only. A pair is retired only after the owner confirms that exact preview. */
internal class CredoDuplicateReview(private val db: WhfinDatabase) {
    data class Pairing(val original: TransactionEntity, val newer: TransactionEntity, val accountLabel: String)
    data class Preview internal constructor(val pairs: List<Pairing>, internal val rows: List<TransactionEntity>, internal val accounts: List<AccountEntity>)
    data class Applied internal constructor(internal val before: Preview, internal val after: List<TransactionEntity>)

    suspend fun preview(): Preview = db.withTransaction {
        val rows = db.transactionDao().allForIntegrity()
        val accounts = db.accountDao().allForIntegrity()
        val credo = accounts.filter { it.iban?.uppercase()?.takeIf { value -> value.length >= 6 }?.substring(4, 6) == "CD" }.associateBy { it.id }
        val eligible = rows.filter { tx ->
            tx.accountId in credo && tx.source == TxSource.STATEMENT && !tx.isVoided &&
                tx.mergedIntoTransactionId == null && tx.correctionOfTransactionId == null &&
                tx.amountMinor < 0 && !(tx.currency == "GEL" && tx.amountMinor == -100L) &&
                !tx.isTransfer && tx.transferGroupId == null && tx.postedAt != null &&
                !tx.note.isNullOrBlank() && tx.externalKey?.startsWith("stmt|") == true &&
                "|id|" !in tx.externalKey && "|credoapi|" !in tx.externalKey
        }
        val pairs = eligible.groupBy { listOf(it.accountId, it.currency, it.amountMinor, it.occurredAt,
            it.postedAt, it.note, it.rawCounterparty, it.counterpartyIban) }.values.mapNotNull { group ->
            if (group.size != 2) return@mapNotNull null
            val ordered = group.sortedBy { it.createdAt }
            val old = ordered[0]; val fresh = ordered[1]
            if (old.createdAt == fresh.createdAt || old.balanceAfterMinor == fresh.balanceAfterMinor ||
                old.balanceAfterMinor == null || fresh.balanceAfterMinor == null ||
                old.categoryId != null && fresh.categoryId != null && old.categoryId != fresh.categoryId ||
                group.any { !unlinked(it) }) return@mapNotNull null
            val account = credo.getValue(old.accountId)
            Pairing(old, fresh, "Credo •${account.iban.orEmpty().takeLast(4)} ${account.currency}")
        }.sortedByDescending { it.original.occurredAt }
        Preview(pairs, rows, accounts)
    }

    private suspend fun unlinked(tx: TransactionEntity) =
        db.transactionAllocationDao().forTransaction(tx.id).isEmpty() &&
            db.debtDao().eventsForTransaction(tx.id).isEmpty() &&
            db.transactionDao().activeCorrectionsFor(tx.id).isEmpty()

    suspend fun confirm(preview: Preview, originalId: Long): Applied = confirm(preview, setOf(originalId))

    suspend fun confirm(preview: Preview, originalIds: Set<Long>): Applied = db.withTransaction {
        require(originalIds.isNotEmpty())
        check(db.transactionDao().allForIntegrity() == preview.rows && db.accountDao().allForIntegrity() == preview.accounts) { "Ledger changed; reopen review" }
        val pairs = preview.pairs.filter { it.original.id in originalIds }
        require(pairs.size == originalIds.size)
        check(pairs.all { unlinked(it.original) && unlinked(it.newer) }) { "Links changed; reopen review" }
        // One atomic decision for the whole selection; preserve original IDs and categories.
        for (pair in pairs) {
            db.transactionDao().update(pair.newer.copy(externalKey = null, isVoided = true, mergedIntoTransactionId = pair.original.id))
            db.transactionDao().update(pair.original.copy(externalKey = pair.newer.externalKey,
                balanceAfterMinor = pair.newer.balanceAfterMinor, categoryId = pair.original.categoryId ?: pair.newer.categoryId))
        }
        Applied(preview, db.transactionDao().allForIntegrity())
    }

    suspend fun undo(applied: Applied) = db.withTransaction {
        check(db.transactionDao().allForIntegrity() == applied.after && db.accountDao().allForIntegrity() == applied.before.accounts) { "Ledger changed; cannot undo this snapshot" }
        // Clear changed keys first so restoring the two original unique keys is atomic.
        val changed = applied.before.rows.filter { it != applied.after.singleOrNull { after -> after.id == it.id } }
        check(changed.all { unlinked(it) }) { "Links changed; cannot undo" }
        changed.forEach { db.transactionDao().update(db.transactionDao().byId(it.id)!!.copy(externalKey = null)) }
        changed.forEach { db.transactionDao().update(it) }
    }
}
