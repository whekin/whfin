package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.statement.BankStatement
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads the ledger and decides what an import would do. Writes nothing.
 *
 * Everything that used to make an import unpredictable lives here as an explicit decision: which key
 * a row gets, whether that key is already present, which draft a line confirms, and what the
 * statement leaves unexplained.
 */
internal class ImportPlanner(private val db: WhfinDatabase, private val zone: ZoneId) {

    suspend fun plan(
        statement: BankStatement,
        account: AccountEntity,
        accountCreated: Boolean,
        accountAdopted: Boolean,
        collectReview: Boolean = true,
    ): ImportPlan {
        val identity = StatementIdentity.of(statement)
        val existingKeys = db.transactionDao().externalKeysForAccount(account.id).toHashSet()
        val sourceBridge = when (statement.bank.provider) {
            "TBC" -> TbcSourceBridge
            "Credo" -> CredoSourceBridge
            else -> null
        }
        val bridge = sourceBridge?.plan(statement, db.transactionDao().allStatementRows(account.id)).orEmpty()
        // One draft may confirm only one statement line: without this the same SMS would be claimed
        // by every similar row in the file and the rest would be inserted as duplicates of it.
        val claimed = mutableSetOf<Long>()
        val candidatesByWindow = mutableMapOf<Pair<LocalDate, Boolean>, List<dev.whekin.whfin.data.db.TransactionEntity>>()

        val entries = statement.rows.map { row ->
            val canonical = identity.rowKey(row)
            bridge[canonical]?.let { return@map it }
            val key = sourceBridge?.existingKey(row, canonical, existingKeys) ?: canonical
            val day = row.purchaseDate ?: row.postedDate
            val crossesMidnight = row.operation.isOwnMovement
            val candidates = candidatesByWindow.getOrPut(day to crossesMidnight) {
                // Credo can stamp a movement made just after midnight on the previous bank posting
                // day. Own movements still require one exact account+amount candidate, so a narrow
                // ±1-day window fixes that boundary without guessing between purchases.
                val fromDay = if (crossesMidnight) day.minusDays(1) else day
                val throughDay = if (crossesMidnight) day.plusDays(1) else day
                db.transactionDao().reconciliationCandidates(
                    account.id,
                    fromDay.atStartOfDay(zone).toInstant().toEpochMilli(),
                    throughDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
                )
            }.filterNot { it.id in claimed }

            val draft = StatementReconciler.match(row, candidates)
            if (key in existingKeys) {
                val existing = db.transactionDao().byExternalKey(key)
                if (row.bankTransactionId != null && existing != null && !existing.isVoided &&
                    (existing.amountMinor != row.amountMinor || existing.balanceAfterMinor != row.balanceAfterMinor ||
                        existing.occurredAt != day.atStartOfDay(zone).toInstant().toEpochMilli() ||
                        existing.postedAt != row.postedDate.atStartOfDay(zone).toInstant().toEpochMilli() ||
                        existing.note != row.description.takeIf { it != row.merchantRaw } ||
                        existing.counterpartyIban != row.beneficiaryAccount ||
                        existing.rawCounterparty != (row.merchantRaw ?: row.beneficiaryName))
                ) {
                    if (existing.amountMinor != row.amountMinor &&
                        (db.transactionAllocationDao().forTransaction(existing.id).isNotEmpty() ||
                            db.debtDao().eventsForTransaction(existing.id).isNotEmpty())
                    ) {
                        throw InvalidStatementException("A changed bank amount is linked to a split or debt. Resolve that link before importing.")
                    }
                    return@map PlannedRow.Reconcile(row, key, existing.id)
                }
                if (draft != null && existing?.source == dev.whekin.whfin.data.db.TxSource.STATEMENT &&
                    !existing.isVoided
                ) {
                    claimed += draft.id
                    return@map PlannedRow.ReconcileDuplicate(row, key, draft.id, existing.id)
                }
                return@map PlannedRow.Duplicate(row, key)
            }
            if (draft != null) {
                claimed += draft.id
                PlannedRow.Reconcile(row, key, draft.id)
            } else {
                PlannedRow.Insert(row, key)
            }
        }

        return ImportPlan(
            statement = statement,
            accountId = account.id,
            accountCreated = accountCreated,
            accountAdopted = accountAdopted,
            entries = entries,
            reviewCandidateIds = if (collectReview) reviewCandidates(statement, account, claimed) else emptyList(),
        )
    }

    /**
     * Drafts inside the confirmed period that the statement never mentions.
     *
     * The last few days are excluded: a card payment reaches the statement a day or two after the
     * purchase, so a fresh draft is late, not wrong.
     */
    private suspend fun reviewCandidates(
        statement: BankStatement,
        account: AccountEntity,
        reconciled: Set<Long>,
    ): List<Long> {
        val from = statement.periodFrom ?: return emptyList()
        val to = statement.periodTo ?: return emptyList()
        val safeTo = to.minusDays(SETTLEMENT_LAG_DAYS)
        if (safeTo < from) return emptyList()
        return db.transactionDao().reconciliationCandidates(
            account.id,
            from.atStartOfDay(zone).toInstant().toEpochMilli(),
            safeTo.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
        ).map { it.id }.filterNot { it in reconciled }
    }

    private companion object {
        const val SETTLEMENT_LAG_DAYS = 3L
    }
}
