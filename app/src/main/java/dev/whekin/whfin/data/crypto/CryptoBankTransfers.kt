package dev.whekin.whfin.data.crypto

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.transfer.OwnTransfers

data class CryptoBankTransfer(val withdrawal: TransactionEntity, val credit: TransactionEntity)

/**
 * The wallet-to-bank shape of a hand-made own transfer, listed where the owner will look for it.
 *
 * Nothing here is a separate mechanism: linking, unlinking and the rules about what may be joined
 * all live in [OwnTransfers]. This only narrows the offer to the pair the income screen is about —
 * money that left a watch-only wallet and turned up in a bank — because that page is answering
 * "where did the salary go", not "what movements exist".
 *
 * Time is a search window, not proof: every pair still requires the owner's explicit choice.
 */
fun cryptoBankCandidates(
    transactions: List<TransactionEntity>,
    accounts: List<AccountEntity>,
    allocatedIds: Set<Long> = emptySet(),
): List<CryptoBankTransfer> {
    val byId = accounts.associateBy { it.id }
    return transactions
        .filter { it.source == TxSource.CRYPTO && it.amountMinor < 0 && byId[it.accountId]?.type == AccountType.CRYPTO }
        .flatMap { withdrawal ->
            OwnTransfers.candidatesFor(withdrawal, transactions, accounts, allocatedIds)
                .filter { side ->
                    side.account?.type == AccountType.BANK &&
                        side.transaction.source in setOf(TxSource.STATEMENT, TxSource.SMS)
                }
                .map { CryptoBankTransfer(withdrawal, it.transaction) }
        }
        .sortedByDescending { it.withdrawal.occurredAt }
}
