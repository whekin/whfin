package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.categorization.MerchantCategorizer
import dev.whekin.whfin.data.categorization.OperationCategories
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.ReconciliationIssueEntity
import dev.whekin.whfin.data.db.StatementImportEntity
import dev.whekin.whfin.data.db.StatementImportOrigin
import dev.whekin.whfin.data.db.StatementSourceEntity
import dev.whekin.whfin.data.db.StatementSourceType
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.statement.StatementOperation
import dev.whekin.whfin.data.statement.StatementRow
import java.time.LocalDate
import java.time.ZoneId

/**
 * Writes a decided [ImportPlan]. Decides nothing.
 *
 * The caller runs this inside one transaction, so a file either lands whole or not at all: a
 * half-imported statement would leave a ledger whose balance matches no bank document.
 */
internal class ImportApplier(private val db: WhfinDatabase, private val zone: ZoneId) {

    suspend fun apply(
        plan: ImportPlan,
        account: AccountEntity,
        fileName: String?,
        origin: StatementImportOrigin,
    ): Long {
        val statement = plan.statement
        val now = System.currentTimeMillis()

        plan.entries.forEach { entry ->
            when (entry) {
                is PlannedRow.Duplicate -> Unit
                is PlannedRow.LinkIdentity -> db.transactionDao().byId(entry.transactionId)?.let {
                    db.transactionDao().update(it.copy(externalKey = entry.externalKey))
                }
                is PlannedRow.Insert -> insert(entry, account, statement.currency, now)
                is PlannedRow.Reconcile -> reconcile(entry, statement.currency)
                is PlannedRow.ReconcileDuplicate -> reconcileDuplicate(entry, statement.currency)
                is PlannedRow.Consolidate -> consolidate(entry, statement.currency)
            }
        }

        OpeningAnchor(db, zone).update(account, statement, origin)
        TransferPairing(db, zone).pairWithinPeriod(account, statement.periodFrom, statement.periodTo)
        CrossBankTransfers(db).pair()

        val importId = db.statementImportDao().insert(
            StatementImportEntity(
                accountId = account.id,
                sourceId = sourceId(account),
                fileName = fileName,
                origin = origin,
                periodFrom = statement.periodFrom?.toEpochDay(),
                periodTo = statement.periodTo?.toEpochDay(),
                openingBalanceMinor = statement.openingBalanceMinor,
                closingBalanceMinor = statement.closingBalanceMinor,
                totalRows = plan.totalRows,
                inserted = plan.inserted,
                duplicates = plan.duplicates,
                reconciled = plan.reconciled,
                reviewCount = plan.reviewCandidateIds.size,
                importedAt = now,
            ),
        )

        plan.reviewCandidateIds.forEach { transactionId ->
            val issue = ReconciliationIssueEntity(
                accountId = account.id,
                transactionId = transactionId,
                importId = importId,
                createdAt = now,
            )
            // A row already queued by an earlier import moves to this one rather than being duplicated.
            if (db.reconciliationIssueDao().insert(issue) <= 0) {
                db.reconciliationIssueDao().moveOpenToImport(transactionId, importId)
            }
        }
        return importId
    }

    private suspend fun consolidate(entry: PlannedRow.Consolidate, currency: String) {
        val rows = entry.transactionIds.map { requireNotNull(db.transactionDao().byId(it)) }
        val existing = entry.statementId?.let { requireNotNull(db.transactionDao().byId(it)) }
        val participants = rows + listOfNotNull(existing)
        if (participants.mapNotNull { it.categoryId }.distinct().size > 1 || participants.any {
            it.isVoided || it.transferGroupId != null || it.isTransfer ||
                db.transactionAllocationDao().forTransaction(it.id).isNotEmpty() ||
                db.debtDao().eventsForTransaction(it.id).isNotEmpty()
        }) throw InvalidStatementException("A consolidated charge has protected financial links.")
        val survivor = rows.first()
        val category = participants.firstNotNullOfOrNull { it.categoryId }
        for (retired in participants.filterNot { it.id == survivor.id }) {
            db.transactionDao().update(retired.copy(externalKey = null, isVoided = true, mergedIntoTransactionId = survivor.id))
            for (message in db.smsDiagnosticDao().forTransaction(retired.id))
                db.smsDiagnosticDao().update(message.copy(transactionId = survivor.id))
            db.bankHoldDao().relink(retired.id, survivor.id)
        }
        reconcile(PlannedRow.Reconcile(entry.row, entry.externalKey, survivor.id), currency)
        val updated = requireNotNull(db.transactionDao().byId(survivor.id))
        db.transactionDao().update(updated.copy(categoryId = category ?: updated.categoryId))
    }

    private suspend fun insert(entry: PlannedRow.Insert, account: AccountEntity, currency: String, now: Long) {
        val row = entry.row
        val merchant = merchantFor(row)
        val category = if (row.operation == StatementOperation.FEE) operationCategory(row)
            else merchant?.categoryId ?: counterpartyCategory(row) ?: operationCategory(row)
        db.transactionDao().insert(
            TransactionEntity(
                accountId = account.id,
                amountMinor = row.amountMinor,
                currency = currency,
                occurredAt = (row.purchaseDate ?: row.postedDate).atMillis(),
                postedAt = row.postedDate.atMillis(),
                merchantId = merchant?.id,
                rawCounterparty = row.merchantRaw ?: row.beneficiaryName,
                counterpartyIban = row.beneficiaryAccount,
                categoryId = category,
                note = row.description.takeIf { it != row.merchantRaw },
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
                isTransfer = row.operation.isOwnMovement,
                balanceAfterMinor = row.balanceAfterMinor,
                externalKey = entry.externalKey,
                createdAt = now,
            ),
        )
    }

    private suspend fun reconcile(entry: PlannedRow.Reconcile, currency: String) {
        val row = entry.row
        val draft = db.transactionDao().byId(entry.transactionId) ?: return
        if ((draft.amountMinor != row.amountMinor || draft.currency != currency) &&
            (db.transactionAllocationDao().forTransaction(draft.id).isNotEmpty() ||
                db.debtDao().eventsForTransaction(draft.id).isNotEmpty()))
            throw InvalidStatementException("A changed bank amount has a split or debt link.")
        val explicitBridge = draft.transferGroupId?.let { db.transactionDao().transferGroupById(it) }
            ?.type == dev.whekin.whfin.data.db.TransferGroupType.OWN_LINK
        if (row.bankTransactionId != null && draft.transferGroupId != null && !explicitBridge) {
            val oldGroup = requireNotNull(draft.transferGroupId)
            if (db.transactionDao().transferGroupById(oldGroup)?.note == CrossBankTransfers.MARKER) {
                db.transactionDao().detachCrossBankGroups(listOf(oldGroup))
            } else {
                db.transactionDao().clearTransferGroups(listOf(oldGroup))
            }
            db.transactionDao().deleteTransferGroups(listOf(oldGroup))
        }
        val bankOwn = row.bankTransactionId != null && draft.isTransfer &&
            dev.whekin.whfin.data.tbc.TbcRowIdentity.mobileFromKey(draft.externalKey.orEmpty()) != null &&
            draft.note?.substringBefore('\n')?.trim() in setOf("კონვერტაცია", "Transfer between your accounts") &&
            row.operation !in setOf(StatementOperation.CARD_PAYMENT, StatementOperation.FEE)
        val merchant = merchantFor(row)
        db.transactionDao().update(
            draft.copy(
                amountMinor = row.amountMinor,
                currency = currency,
                occurredAt = (row.purchaseDate ?: row.postedDate).atMillis(),
                postedAt = row.postedDate.atMillis(),
                merchantId = merchant?.id,
                rawCounterparty = row.merchantRaw ?: row.beneficiaryName,
                counterpartyIban = row.beneficiaryAccount,
                // The statement is authoritative about the money, not about what the user decided
                // this row means: a category already on the draft outlives an import that has none.
                categoryId = draft.categoryId
                    ?: (if (row.operation == StatementOperation.FEE) operationCategory(row) else null)
                    ?: merchant?.categoryId
                    ?: counterpartyCategory(row)
                    ?: operationCategory(row)
                    ?: draft.categoryId,
                note = row.description.takeIf { it != row.merchantRaw },
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
                isTransfer = row.operation.isOwnMovement || explicitBridge || bankOwn,
                transferGroupId = if (row.bankTransactionId != null && !explicitBridge) null else draft.transferGroupId,
                balanceAfterMinor = row.balanceAfterMinor,
                externalKey = entry.externalKey,
                // The draft's amount or currency may have changed, so any lari value booked for the
                // old figure no longer describes this row.
                gelValueMinor = null,
                gelRateOn = null,
            ),
        )
    }

    /**
     * Repairs the historical failure mode where a statement row landed beside, rather than on top
     * of, an SMS row. The SMS row remains canonical because it can already carry the user's edits,
     * diagnostics, and the other leg of an SMS transfer group.
     */
    private suspend fun reconcileDuplicate(entry: PlannedRow.ReconcileDuplicate, currency: String) {
        val duplicate = db.transactionDao().byId(entry.duplicateStatementId) ?: return
        val sms = db.transactionDao().byId(entry.transactionId) ?: return
        if ((duplicate.categoryId != null && sms.categoryId != null && duplicate.categoryId != sms.categoryId) ||
            db.transactionAllocationDao().forTransaction(duplicate.id).isNotEmpty() ||
            db.debtDao().eventsForTransaction(duplicate.id).isNotEmpty())
            throw InvalidStatementException("Duplicate evidence has conflicting categories, splits or debt links.")

        val duplicateBridge = duplicate.transferGroupId?.let { db.transactionDao().transferGroupById(it) }
            ?.takeIf { it.type == dev.whekin.whfin.data.db.TransferGroupType.OWN_LINK }
        if (duplicateBridge != null) {
            // The owner may have linked the statement copy before its SMS duplicate was found.
            // Move that decision to the surviving row; it is not a derived bank pairing.
            require(sms.transferGroupId == null || sms.transferGroupId == duplicateBridge.id)
            db.transactionDao().attachToTransferGroup(listOf(sms.id), duplicateBridge.id)
        }

        duplicate.transferGroupId
            ?.takeUnless { duplicateBridge != null }
            ?.takeIf { it != sms.transferGroupId }
            ?.let { derivedGroupId ->
                // Statement pairings are derived and can be rebuilt. Clearing the whole group avoids
                // leaving its other leg attached to a group whose matched leg is about to be retired.
                db.transactionDao().clearTransferGroups(listOf(derivedGroupId))
                db.transactionDao().deleteTransferGroups(listOf(derivedGroupId))
            }
        db.transactionDao().update(
            duplicate.copy(
                externalKey = null,
                transferGroupId = null,
                isVoided = true,
                // Naming the survivor is what makes this a merge rather than a row voided for no
                // reason: nothing else in the ledger could say why this copy stopped counting.
                mergedIntoTransactionId = entry.transactionId,
            ),
        )
        db.bankHoldDao().relink(duplicate.id, sms.id)
        for (message in db.smsDiagnosticDao().forTransaction(duplicate.id))
            db.smsDiagnosticDao().update(message.copy(transactionId = sms.id))
        reconcile(
            PlannedRow.Reconcile(entry.row, entry.externalKey, entry.transactionId),
            currency,
        )
        val updated = requireNotNull(db.transactionDao().byId(sms.id))
        db.transactionDao().update(updated.copy(categoryId = sms.categoryId ?: duplicate.categoryId ?: updated.categoryId))
    }

    /**
     * The counterparty worth remembering. A transfer between own accounts has none: filing yourself
     * as a merchant would teach the category dictionary nonsense.
     */
    private suspend fun merchantFor(row: StatementRow): MerchantEntity? =
        row.merchantRaw?.let { resolveMerchant(it) }
            ?: row.beneficiaryName
                ?.takeIf { row.operation != StatementOperation.OWN_TRANSFER }
                ?.let { resolveMerchant(it) }

    private suspend fun resolveMerchant(raw: String): MerchantEntity? {
        return MerchantCategorizer.resolve(db, raw)
    }

    /**
     * The recipient this account was already identified as. Their name changes spelling between
     * statements; the account they were paid into does not.
     */
    private suspend fun counterpartyCategory(row: StatementRow): Long? = row.beneficiaryAccount
        ?.takeIf { row.operation != StatementOperation.OWN_TRANSFER }
        ?.let { db.counterpartyRuleDao().byIban(it)?.categoryId }

    /**
     * What the bank's own classification of the row already says, when no merchant does better.
     * A fee names its own category; nobody was paid for it.
     */
    private suspend fun operationCategory(row: StatementRow): Long? =
        OperationCategories.categoryFor(row.operation, db.categoryDao().all())?.id

    private suspend fun sourceId(account: AccountEntity): Long {
        db.statementSourceDao().forAccount(account.id)?.let { return it.id }
        return db.statementSourceDao().insert(
            StatementSourceEntity(
                groupId = requireNotNull(account.groupId),
                type = StatementSourceType.ACCOUNT,
                accountId = account.id,
                label = account.iban ?: account.name,
            ),
        )
    }

    private fun LocalDate.atMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()
}
