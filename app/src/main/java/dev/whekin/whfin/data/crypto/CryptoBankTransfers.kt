package dev.whekin.whfin.data.crypto

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.*
import kotlin.math.abs

data class CryptoBankTransfer(val withdrawal: TransactionEntity, val credit: TransactionEntity)

/** Time is a search window, not proof: every pair requires the owner's explicit choice. */
fun cryptoBankCandidates(
    transactions: List<TransactionEntity>,
    accounts: List<AccountEntity>,
    allocatedIds: Set<Long> = emptySet(),
): List<CryptoBankTransfer> {
    val byId = accounts.associateBy { it.id }
    val free = transactions.filter { !it.isVoided && !it.isTransfer && it.transferGroupId == null &&
        it.id !in allocatedIds && !byId[it.accountId].let { account -> account == null || account.isArchived } }
    val outgoing = free.filter { it.source == TxSource.CRYPTO && it.amountMinor < 0 &&
        byId[it.accountId]?.type == AccountType.CRYPTO }
    val incoming = free.filter { it.amountMinor > 0 && it.source in setOf(TxSource.STATEMENT, TxSource.SMS) &&
        byId[it.accountId]?.type == AccountType.BANK }
    return outgoing.flatMap { out ->
        incoming.filter { abs(it.occurredAt - out.occurredAt) <= 2 * 86_400_000L }
            .map { CryptoBankTransfer(out, it) }
    }.sortedByDescending { it.withdrawal.occurredAt }
}

class CryptoBankTransferRepository(private val db: WhfinDatabase) {
    suspend fun link(withdrawalId: Long, creditId: Long) = db.withTransaction {
        val rows = listOfNotNull(db.transactionDao().byId(withdrawalId), db.transactionDao().byId(creditId))
        val allocated = rows.filter { db.transactionAllocationDao().forTransaction(it.id).isNotEmpty() }
            .mapTo(mutableSetOf()) { it.id }
        allocated += db.debtDao().allEventsForIntegrity().mapNotNull { it.transactionId }
        require(cryptoBankCandidates(rows, db.accountDao().allActive(), allocated).any {
            it.withdrawal.id == withdrawalId && it.credit.id == creditId
        }) { "The selected movements can no longer be linked" }
        val groupId = db.transactionDao().insertTransferGroup(TransferGroupEntity(
            type = TransferGroupType.CRYPTO_BRIDGE, createdAt = System.currentTimeMillis(),
        ))
        db.transactionDao().attachToTransferGroup(listOf(withdrawalId, creditId), groupId)
    }

    suspend fun unlink(groupId: Long) = db.withTransaction {
        val group = db.transactionDao().transferGroupById(groupId)
        require(group?.type == TransferGroupType.CRYPTO_BRIDGE)
        val rows = db.transactionDao().byTransferGroup(groupId)
        require(rows.size == 2 && rows.none { it.isVoided })
        require(rows.any { it.source == TxSource.CRYPTO && it.amountMinor < 0 })
        require(rows.any { it.source in setOf(TxSource.STATEMENT, TxSource.SMS) && it.amountMinor > 0 })
        rows.forEach { db.transactionDao().update(it.copy(transferGroupId = null, isTransfer = false)) }
        db.transactionDao().deleteTransferGroups(listOf(groupId))
    }
}
