package dev.whekin.whfin.data.mutation

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.*

/** A target balance is valid only for the ledger the owner was shown. */
class BalanceAdjustment(private val db: WhfinDatabase) {
    data class Snapshot(val account: AccountEntity, val rows: List<TransactionEntity>) {
        val accountId: Long get() = account.id
        val balance: Long get() = rows.fold(0L) { total, row -> Math.addExact(total, row.amountMinor) }
    }
    suspend fun read(accountId: Long): Snapshot = db.withTransaction {
        val account = checkNotNull(db.accountDao().byId(accountId))
        Snapshot(account, db.transactionDao().activeForAccount(accountId))
    }
    suspend fun save(snapshot: Snapshot, target: Long): Long = db.withTransaction {
        check(read(snapshot.accountId) == snapshot) { "Ledger changed" }
        val delta = Math.subtractExact(target, snapshot.balance)
        TransactionMutationModule(db).createAdjustment(snapshot.accountId, delta,
            db.categoryDao().systemByName(CategorySeeder.UNACCOUNTED)?.id, System.currentTimeMillis())
    }
    suspend fun undo(id: Long) = db.withTransaction {
        val row = checkNotNull(db.transactionDao().byId(id))
        check(row.isBalanceAdjustment())
        check(TransactionMutationModule(db).delete(listOf(MutationSelection(id))).changed == 1)
    }
}
