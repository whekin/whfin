package dev.whekin.whfin.data.importer

import androidx.room.withTransaction
import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.categorization.MerchantCategorizer
import dev.whekin.whfin.data.db.*
import dev.whekin.whfin.data.tbc.*
import java.time.Instant

/** Bank holds are active, provisional spending. A durable alias keeps every later read on one row. */
internal class TbcHoldImporter(private val db: WhfinDatabase) {
    data class Result(val inserted: Int = 0, val attached: Int = 0)
    suspend fun apply(account: AccountEntity, holds: List<TbcHold>): Result = db.withTransaction {
        require(account.iban != null)
        require(holds.all { it.iban == account.iban && it.currency == account.currency })
        val aliases = db.bankHoldDao().forAccount(account.id)
        val existing = db.transactionDao().allForAccountIncludingVoided(account.id)
        val fresh = holds.filter { h -> aliases.none { it.key == h.key } }
        for (hold in fresh) {
            if (existing.any { row -> row.source == TxSource.STATEMENT && !row.isVoided && !row.isTransfer &&
                row.amountMinor == hold.amountMinor && day(row.occurredAt) == day(hold.occurredAt) &&
                MerchantNormalizer.equivalent(row.rawCounterparty, hold.merchant) && aliases.none { it.transactionId == row.id } &&
                TbcHistoryParser.purchaseTime(row.note.orEmpty()) == null }) throw TbcException("HISTORY_CONFLICT")
        }
        val matches = fresh.associateWith { hold ->
            existing.filter { row ->
                !row.isVoided && !row.isTransfer && row.transferGroupId == null &&
                    row.source in setOf(TxSource.SMS, TxSource.BANK_HOLD, TxSource.STATEMENT) &&
                    row.amountMinor == hold.amountMinor && row.currency == hold.currency &&
                    MerchantNormalizer.equivalent(row.rawCounterparty, hold.merchant) &&
                    (if (row.source == TxSource.STATEMENT) TbcHistoryParser.purchaseTime(row.note.orEmpty())?.div(60000) == hold.occurredAt / 60000
                     else row.occurredAt / 60000 == hold.occurredAt / 60000) &&
                    aliases.none { it.transactionId == row.id } &&
                    db.smsDiagnosticDao().forTransaction(row.id).all { evidence ->
                        hold.cardLast4 == null || evidence.cardLast4 == null || evidence.cardLast4 == hold.cardLast4
                    }
            }
        }
        if (matches.values.any { it.size > 1 } || matches.values.flatten().groupBy { it.id }.any { it.value.size > 1 })
            throw TbcException("HISTORY_CONFLICT")
        var inserted = 0
        var attached = 0
        for (hold in holds) {
            val alias = aliases.singleOrNull { it.key == hold.key }
            var row = alias?.let { existing.singleOrNull { row -> row.id == it.transactionId } }
                ?: matches[hold]?.singleOrNull()
            if (row == null) {
                val merchant = MerchantCategorizer.resolve(db, hold.merchant)
                val id = db.transactionDao().insert(TransactionEntity(accountId = account.id,
                    amountMinor = hold.amountMinor, currency = hold.currency, occurredAt = hold.occurredAt,
                    merchantId = merchant?.id, rawCounterparty = hold.merchant, categoryId = merchant?.categoryId,
                    status = TxStatus.PENDING, source = TxSource.BANK_HOLD, externalKey = hold.key,
                    createdAt = System.currentTimeMillis()))
                check(id > 0)
                row = requireNotNull(db.transactionDao().byId(id))
                inserted++
            } else if (!row.isVoided && row.source in setOf(TxSource.SMS, TxSource.BANK_HOLD)) {
                if (row.amountMinor != hold.amountMinor &&
                    (db.transactionAllocationDao().forTransaction(row.id).isNotEmpty() || db.debtDao().eventsForTransaction(row.id).isNotEmpty()))
                    throw TbcException("HISTORY_CONFLICT")
                if (row.source != TxSource.BANK_HOLD || row.amountMinor != hold.amountMinor || row.status != TxStatus.PENDING) attached++
                db.transactionDao().update(row.copy(amountMinor = hold.amountMinor, source = TxSource.BANK_HOLD,
                    status = TxStatus.PENDING,
                    gelValueMinor = row.gelValueMinor.takeIf { row.amountMinor == hold.amountMinor },
                    gelRateOn = row.gelRateOn.takeIf { row.amountMinor == hold.amountMinor }))
            }
            // A settled/voided row is never reverted by a stale hold returned by another API page.
            db.bankHoldDao().upsert(BankHoldEntity(hold.key, account.id, row.id, hold.amountMinor,
                hold.currency, hold.occurredAt, hold.merchant, hold.cardLast4, System.currentTimeMillis()))
        }
        Result(inserted, attached)
    }
    private fun day(time: Long) = Instant.ofEpochMilli(time).atZone(LedgerCalendar.zone).toLocalDate()
}
