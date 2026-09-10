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

        val withdrawnHolds = if (statement.bank.provider == "TBC") db.bankHoldDao().forAccount(account.id)
            .mapNotNull { db.transactionDao().byId(it.transactionId) }.distinctBy { it.id }
            .filter { it.source == dev.whekin.whfin.data.db.TxSource.BANK_HOLD && it.isVoided && it.mergedIntoTransactionId == null }
            else emptyList()
        val entries = statement.rows.map { row ->
            val canonical = identity.rowKey(row)
            val purchaseDay = row.purchaseDate ?: row.postedDate
            val withdrawn = withdrawnHolds.filter { held -> held.id !in claimed &&
                java.time.Instant.ofEpochMilli(held.occurredAt).atZone(zone).toLocalDate() == purchaseDay &&
                MerchantNormalizer.equivalent(held.rawCounterparty, row.merchantRaw) &&
                dev.whekin.whfin.data.tbc.TbcHistoryParser.purchaseTime(row.description)?.let {
                    it / 60000 == held.occurredAt / 60000
                } != false }
            if (withdrawn.isNotEmpty()) {
                val held = withdrawn.singleOrNull()?.takeIf { it.amountMinor == row.amountMinor }
                    ?: throw InvalidStatementException("An owner-withdrawn hold has an ambiguous settlement.")
                claimed += held.id
                if (held.externalKey == canonical) return@map PlannedRow.Duplicate(row, canonical)
                if (canonical in existingKeys) throw InvalidStatementException("A withdrawn hold conflicts with an existing settlement.")
                return@map PlannedRow.LinkIdentity(row, canonical, held.id)
            }
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
            }.filterNot { it.id in claimed }.filter { candidate ->
                candidate.source != dev.whekin.whfin.data.db.TxSource.BANK_HOLD ||
                    dev.whekin.whfin.data.tbc.TbcHistoryParser.purchaseTime(row.description)?.let {
                        it / 60000 == candidate.occurredAt / 60000
                    } != false
            }

            val draft = StatementReconciler.match(row, candidates)
            if (draft?.source == dev.whekin.whfin.data.db.TxSource.BANK_HOLD &&
                dev.whekin.whfin.data.tbc.TbcHistoryParser.purchaseTime(row.description) == null &&
                statement.rows.count { other -> (other.purchaseDate ?: other.postedDate) == day &&
                    other.amountMinor == row.amountMinor && MerchantNormalizer.equivalent(other.merchantRaw, row.merchantRaw) } > 1)
                throw InvalidStatementException("Ambiguous settlement without purchase time.")
            val possibleHolds = candidates.filter { it.source == dev.whekin.whfin.data.db.TxSource.BANK_HOLD &&
                MerchantNormalizer.equivalent(it.rawCounterparty, row.merchantRaw) }
            if (possibleHolds.isNotEmpty() && draft == null)
                throw InvalidStatementException("Ambiguous pending bank purchase.")
            if (draft?.source == dev.whekin.whfin.data.db.TxSource.BANK_HOLD && draft.amountMinor != row.amountMinor &&
                (db.transactionAllocationDao().forTransaction(draft.id).isNotEmpty() || db.debtDao().eventsForTransaction(draft.id).isNotEmpty()))
                throw InvalidStatementException("A changed pending purchase is linked to a split or debt.")
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
