package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.ReconciliationIssueEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
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
                is PlannedRow.Insert -> insert(entry, account, statement.currency, now)
                is PlannedRow.Reconcile -> reconcile(entry, statement.currency)
            }
        }

        OpeningAnchor(db, zone).update(account, statement)
        TransferPairing(db, zone).pairWithinPeriod(account, statement.periodFrom, statement.periodTo)

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

    private suspend fun insert(entry: PlannedRow.Insert, account: AccountEntity, currency: String, now: Long) {
        val row = entry.row
        val merchant = merchantFor(row)
        val id = db.transactionDao().insert(
            TransactionEntity(
                accountId = account.id,
                amountMinor = row.amountMinor,
                currency = currency,
                occurredAt = (row.purchaseDate ?: row.postedDate).atMillis(),
                postedAt = row.postedDate.atMillis(),
                merchantId = merchant?.id,
                rawCounterparty = row.merchantRaw ?: row.beneficiaryName,
                counterpartyIban = row.beneficiaryAccount,
                categoryId = merchant?.categoryId,
                note = row.description.takeIf { it != row.merchantRaw },
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
                isTransfer = row.operation.isOwnMovement,
                balanceAfterMinor = row.balanceAfterMinor,
                externalKey = entry.externalKey,
                createdAt = now,
            ),
        )
        if (id > 0) {
            attachUnroutedCardEvidence(account, id, row.amountMinor, currency, row.purchaseDate ?: row.postedDate, row.merchantRaw)
        }
    }

    private suspend fun reconcile(entry: PlannedRow.Reconcile, currency: String) {
        val row = entry.row
        val draft = db.transactionDao().byId(entry.transactionId) ?: return
        val merchant = merchantFor(row)
        db.transactionDao().update(
            draft.copy(
                amountMinor = row.amountMinor,
                currency = currency,
                occurredAt = (row.purchaseDate ?: row.postedDate).atMillis(),
                postedAt = row.postedDate.atMillis(),
                merchantId = merchant?.id,
                rawCounterparty = row.merchantRaw,
                counterpartyIban = row.beneficiaryAccount,
                categoryId = merchant?.categoryId,
                note = row.description.takeIf { it != row.merchantRaw },
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
                isTransfer = row.operation.isOwnMovement,
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
     * The counterparty worth remembering. A transfer between own accounts has none: filing yourself
     * as a merchant would teach the category dictionary nonsense.
     */
    private suspend fun merchantFor(row: StatementRow): MerchantEntity? =
        row.merchantRaw?.let { resolveMerchant(it) }
            ?: row.beneficiaryName
                ?.takeIf { row.operation != StatementOperation.OWN_TRANSFER }
                ?.let { resolveMerchant(it) }

    private suspend fun resolveMerchant(raw: String): MerchantEntity? {
        val key = MerchantNormalizer.normalize(raw)
        if (key.isEmpty()) return null
        db.merchantDao().resolve(key)?.let { return it }
        val id = db.merchantDao().insert(
            MerchantEntity(normalizedKey = key, displayName = MerchantNormalizer.displayName(raw)),
        )
        // A concurrent insert is ignored rather than failing, so the winner is read back.
        return if (id > 0) db.merchantDao().byKey(key) else db.merchantDao().resolve(key)
    }

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

    /**
     * Links the SMS that announced this payment but had no ledger to land in.
     *
     * The statement is what finally says which account it belonged to, so the message becomes
     * evidence attached to a confirmed row instead of a second pending draft of the same money.
     */
    private suspend fun attachUnroutedCardEvidence(
        account: AccountEntity,
        transactionId: Long,
        amountMinor: Long,
        currency: String,
        purchaseDate: LocalDate,
        merchantRaw: String?,
    ) {
        val wanted = MerchantNormalizer.normalize(merchantRaw ?: return)
        if (wanted.isEmpty()) return
        val candidates = db.smsDiagnosticDao().unroutedBetween(
            purchaseDate.atMillis(),
            purchaseDate.plusDays(1).atMillis() - 1,
        ).filter { diagnostic ->
            diagnostic.kind == SmsDiagnosticKind.CARD_PAYMENT &&
                diagnostic.counterparty?.let(MerchantNormalizer::normalize) == wanted
        }
        val exact = candidates.filter { diagnostic ->
            diagnostic.currency == currency &&
                diagnostic.amountMinor?.let { -kotlin.math.abs(it) } == amountMinor
        }
        val match = exact.singleOrNull() ?: candidates.singleOrNull() ?: return
        db.smsDiagnosticDao().update(
            match.copy(
                outcome = SmsDiagnosticOutcome.ATTACHED,
                reason = null,
                transactionId = transactionId,
                accountId = account.id,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun LocalDate.atMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()
}
