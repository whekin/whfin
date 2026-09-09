package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.categorization.OperationCategories
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.statement.StatementOperation
import dev.whekin.whfin.data.statement.StatementParsers
import java.util.Locale

/** Reciprocal statement evidence across banks. Amount or a matching personal name is never enough. */
internal class CrossBankTransfers(private val db: WhfinDatabase) {
    /** Called inside the import transaction; explicit OWN_LINK decisions are never rebuilt. */
    suspend fun pair() {
        val old = db.transactionDao().crossBankGroupIds()
        if (old.isNotEmpty()) {
            db.transactionDao().detachCrossBankGroups(old)
            db.transactionDao().deleteTransferGroups(old)
        }
        val accounts = db.accountDao().allActive().associateBy { it.id }
        val feeCategory = OperationCategories.categoryFor(StatementOperation.FEE, db.categoryDao().all())?.id
        val candidates = db.transactionDao().crossBankCandidates().filter {
            it.accountId in accounts && (feeCategory == null || it.categoryId != feeCategory) &&
                db.transactionAllocationDao().forTransaction(it.id).isEmpty() &&
                db.debtDao().eventsForTransaction(it.id).isEmpty()
        }
        fun matches(a: TransactionEntity, b: TransactionEntity): Boolean {
            val source = accounts.getValue(a.accountId)
            val target = accounts.getValue(b.accountId)
            if (source.groupId == null || target.groupId == null || source.groupId == target.groupId) return false
            if (a.amountMinor >= 0 || b.amountMinor <= 0 || a.currency != b.currency || a.amountMinor != -b.amountMinor) return false
            if (kotlin.math.abs((a.postedAt ?: a.occurredAt) - (b.postedAt ?: b.occurredAt)) > WINDOW) return false
            val sourceIban = normalize(source.iban) ?: return false
            val targetIban = normalize(target.iban) ?: return false
            if (normalize(a.counterpartyIban) != targetIban) return false
            if (normalize(b.counterpartyIban) == sourceIban) return true
            // Keep the processor intact; bank-specific note evidence is not a replacement counterparty.
            return StatementParsers.originAccountFromNote(targetIban, b.note.orEmpty()) == sourceIban
        }
        val debits = candidates.filter { it.amountMinor < 0 }
        val credits = candidates.filter { it.amountMinor > 0 }.groupBy { it.currency to it.amountMinor }
        val edges = debits.associateWith { debit ->
            credits[debit.currency to -debit.amountMinor].orEmpty().filter { matches(debit, it) }
        }
        edges.forEach { (debit, possible) ->
            val credit = possible.singleOrNull() ?: return@forEach
            if (edges.values.count { credit in it } != 1) return@forEach
            val id = db.transactionDao().insertTransferGroup(TransferGroupEntity(
                type = TransferGroupType.TRANSFER, note = MARKER, createdAt = System.currentTimeMillis(),
            ))
            db.transactionDao().attachToTransferGroup(listOf(debit.id, credit.id), id)
        }
    }

    private fun normalize(value: String?) = value?.filterNot(Char::isWhitespace)?.uppercase(Locale.ROOT)?.takeIf { it.length == 22 }

    companion object {
        const val MARKER = "Bank statement transfer"
        private const val WINDOW = 3L * 24 * 60 * 60 * 1000
    }
}
