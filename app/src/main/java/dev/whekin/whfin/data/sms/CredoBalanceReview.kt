package dev.whekin.whfin.data.sms

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.*

/** Explicit, previewed metadata repair. Never changes amounts, accounts, groups or transactions' existence. */
internal class CredoBalanceReview(private val db: WhfinDatabase) {
    data class Change(val id: Long,val accountId: Long,val at: Long,val before: Long?,val after: Long?)
    data class Preview internal constructor(internal val rows: List<TransactionEntity>,val changes: List<Change>, val labels: Map<Long,String>, internal val accounts: List<AccountEntity>)
    suspend fun preview(): Preview = db.withTransaction {
        val original = db.transactionDao().allForIntegrity()
        val rows = original.associateBy { it.id }.toMutableMap()
        val accountSnapshot=db.accountDao().allForIntegrity()
        val accounts = accountSnapshot.filter { BankSmsBank.CREDO.accepts(db,it) }.map { it.id }.toSet()
        data class Transfer(val from: TransactionEntity,val to: TransactionEntity,val balance: Long)
        val transfers = original.filter { it.source==TxSource.SMS && it.isTransfer && !it.isVoided && it.transferGroupId!=null }
            .groupBy { it.transferGroupId }.values.mapNotNull { legs ->
                if (legs.size!=2 || legs.any { it.accountId !in accounts }) return@mapNotNull null
                val from=legs.singleOrNull { it.amountMinor<0 } ?: return@mapNotNull null
                val to=legs.singleOrNull { it.amountMinor>0 } ?: return@mapNotNull null
                if (from.currency!=to.currency || from.amountMinor != -to.amountMinor || from.accountId==to.accountId ||
                    from.occurredAt!=to.occurredAt || to.externalKey!=from.externalKey+"|to" ||
                    db.transactionDao().transferGroupById(from.transferGroupId!!)?.note!="Credo SMS transfer") return@mapNotNull null
                val balance=listOfNotNull(from.balanceAfterMinor,to.balanceAfterMinor).distinct().singleOrNull()
                    ?: db.smsDiagnosticDao().forTransaction(from.id).filter { it.kind==SmsDiagnosticKind.OWN_TRANSFER }
                        .mapNotNull { it.balanceMinor }.distinct().singleOrNull() ?: return@mapNotNull null
                Transfer(from,to,balance)
            }.sortedBy { it.from.occurredAt }
        transfers.forEach { t -> rows[t.from.id]=t.from.copy(balanceAfterMinor=null); rows[t.to.id]=t.to.copy(balanceAfterMinor=null) }
        for (t in transfers) {
            val legs=listOf(t.from,t.to)
            val after=legs.associate { leg ->
                val history=rows.values.filter { !it.isVoided && it.id!=t.from.id && it.id!=t.to.id && it.accountId==leg.accountId && it.occurredAt<=leg.occurredAt }
                val anchor=history.filter { it.balanceAfterMinor!=null }.maxWithOrNull(compareBy<TransactionEntity> { it.occurredAt }.thenBy { it.id })
                val predicted=anchor?.let { a -> runCatching { Math.addExact(history.filter { it.occurredAt>a.occurredAt || it.occurredAt==a.occurredAt && it.id>a.id }
                    .fold(a.balanceAfterMinor!!) { sum,row -> Math.addExact(sum,row.amountMinor) },leg.amountMinor) }.getOrNull() }
                leg.accountId to predicted
            }
            val owner=declaredBalanceSide(after,t.balance)
            legs.forEach { leg -> rows[leg.id]=rows.getValue(leg.id).copy(balanceAfterMinor=t.balance.takeIf { leg.accountId==owner }) }
        }
        val labels=db.accountDao().allForIntegrity().associate { it.id to "Credo •${it.iban.orEmpty().takeLast(4)} ${it.currency}" }
        Preview(original,original.mapNotNull { prior -> val next=rows.getValue(prior.id)
            if(prior.balanceAfterMinor==next.balanceAfterMinor) null else Change(prior.id,prior.accountId,prior.occurredAt,prior.balanceAfterMinor,next.balanceAfterMinor) },labels,accountSnapshot)
    }
    /** Called only by the explicit confirmation of this exact preview; no startup or background hook. */
    suspend fun confirm(preview: Preview) = db.withTransaction {
        check(db.accountDao().allForIntegrity()==preview.accounts)
        check(db.transactionDao().allForIntegrity()==preview.rows) { "Ledger changed; reopen the preview" }
        val byId=preview.rows.associateBy { it.id }
        preview.changes.forEach { change -> db.transactionDao().update(byId.getValue(change.id).copy(balanceAfterMinor=change.after)) }
    }
}
