package dev.whekin.whfin.data.integrity

import dev.whekin.whfin.data.db.DebtEventKind
import dev.whekin.whfin.data.db.DebtStatus
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.WhfinDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class IntegritySeverity { ERROR, WARNING }

data class IntegrityIssue(
    val code: String,
    val severity: IntegritySeverity,
    val entity: String,
    val entityId: Long?,
    val message: String,
)

data class IntegrityReport(val issues: List<IntegrityIssue>) {
    val isHealthy: Boolean get() = issues.none { it.severity == IntegritySeverity.ERROR }
}

/**
 * Read-only checks for invariants that Room foreign keys cannot express.  It is deliberately a
 * separate module so backup/release diagnostics can run it without mutating the user's ledger.
 */
class DataIntegrityChecker(
    private val db: WhfinDatabase,
    private val zone: ZoneId = ZoneId.of("Asia/Tbilisi"),
) {
    private fun Long.dayIn(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

    suspend fun run(): IntegrityReport {
        val issues = buildList {
            val accounts = db.accountDao().allForIntegrity().associateBy { it.id }
            val transactions = db.transactionDao().allForIntegrity()
            val byTransaction = transactions.associateBy { it.id }
            val allocations = db.transactionAllocationDao().allForIntegrity().groupBy { it.transactionId }

            // Categories nest exactly one level, and only within their own kind. Nothing in the app
            // can build anything else, but a restored file can carry it, and a deeper chain would
            // report the same money under a group the user never sees.
            val categories = db.categoryDao().all()
            val categoryById = categories.associateBy { it.id }
            categories.mapNotNull { child -> child.parentId?.let { child to it } }
                .forEach { (child, parentId) ->
                    val parent = categoryById[parentId]
                    when {
                        parent == null ->
                            add(error("orphan_category_parent", "categories", child.id, "Category points to a missing group."))
                        parent.id == child.id ->
                            add(error("category_own_parent", "categories", child.id, "Category is its own group."))
                        parent.parentId != null ->
                            add(error("category_nested_too_deep", "categories", child.id, "Category groups are one level deep."))
                        parent.kind != child.kind ->
                            add(error("category_kind_mismatch", "categories", child.id, "Category and its group disagree about income or expense."))
                    }
                }

            allocations.forEach { (transactionId, rows) ->
                val transaction = byTransaction[transactionId]
                if (transaction == null) {
                    add(error("orphan_allocation", "transaction_allocations", transactionId, "Allocation points to a missing transaction."))
                } else {
                    if (rows.sumOf { it.amountMinor } != transaction.amountMinor) {
                        add(error("allocation_total_mismatch", "transactions", transactionId, "Allocation total does not equal the parent amount."))
                    }
                    // A share pointing the other way would subtract from the parent instead of
                    // dividing it, so an over-allocation could hide behind a matching total.
                    rows.filter { it.amountMinor == 0L || (it.amountMinor < 0) != (transaction.amountMinor < 0) }
                        .forEach { row ->
                            add(error("allocation_sign_mismatch", "transaction_allocations", row.id, "Allocation does not divide its transaction."))
                        }
                    if (transaction.transferGroupId != null || transaction.isTransfer) {
                        add(error("allocation_on_transfer", "transactions", transactionId, "A transfer leg cannot be split between people."))
                    }
                }
            }

            transactions.forEach { transaction ->
                transaction.accountId.let { accountId ->
                    val account = accounts[accountId]
                    if (account == null) {
                        add(error("orphan_transaction", "transactions", transaction.id, "Transaction points to a missing account."))
                    } else if (account.currency != transaction.currency) {
                        add(error("currency_mismatch", "transactions", transaction.id, "Transaction currency differs from its ledger currency."))
                    }
                }
                if (
                    transaction.isVoided &&
                    transaction.source in setOf(TxSource.STATEMENT, TxSource.SMS) &&
                    // A merged copy states its own reason, and it is not a correction: the row was
                    // never a separate operation, so there is nothing for an audit record to undo.
                    transaction.mergedIntoTransactionId == null &&
                    // A cancellation carries the immutable key of the bank message that withdrew
                    // an SMS row; diagnostics remain device-local and are intentionally not backed up.
                    transaction.canceledBySmsExternalKey == null
                ) {
                    val corrections = transactions.filter {
                        it.correctionOfTransactionId == transaction.id && it.correctionRevokedAt == null
                    }
                    if (corrections.none { it.isVoided && it.source == TxSource.ADJUSTMENT }) {
                        add(error("missing_transaction_correction", "transactions", transaction.id, "Voided imported transaction has no active audit correction."))
                    }
                }
                transaction.mergedIntoTransactionId?.let { survivorId ->
                    val survivor = byTransaction[survivorId]
                    // The pointer is the whole justification for retiring the row, so it has to lead
                    // somewhere that still counts. Otherwise the money simply disappeared.
                    if (survivor == null || survivor.isVoided) {
                        add(error("orphan_merged_transaction", "transactions", transaction.id, "Merged copy points at a missing or retired row."))
                    }
                    if (!transaction.isVoided) {
                        add(error("active_merged_transaction", "transactions", transaction.id, "A merged copy must stay out of active balances."))
                    }
                }
                if (transaction.canceledBySmsExternalKey != null) {
                    if (transaction.source != TxSource.SMS || !transaction.isVoided) {
                        add(error("invalid_sms_cancellation", "transactions", transaction.id, "SMS cancellation must retire an SMS-sourced transaction."))
                    }
                }
                if (transaction.correctionOfTransactionId != null) {
                    val source = byTransaction[transaction.correctionOfTransactionId]
                    // Only a correction that has not been revoked claims its source is withdrawn.
                    // A revoked one is history: the same row may since have been corrected again by
                    // a newer correction, so it must not constrain the current state.
                    if (source == null || (transaction.correctionRevokedAt == null && !source.isVoided)) {
                        add(error("orphan_transaction_correction", "transactions", transaction.id, "Active correction does not point to a voided source row."))
                    }
                    if (transaction.source != TxSource.ADJUSTMENT || !transaction.isVoided) {
                        add(error("active_transaction_correction", "transactions", transaction.id, "Correction rows must stay voided adjustment audit records."))
                    }
                } else if (transaction.correctionRevokedAt != null) {
                    add(error("revoked_without_correction", "transactions", transaction.id, "Only a correction row can carry a revocation."))
                }
            }

            // The two layers describe the same money, so an SMS row that stands beside a statement
            // row of the same ledger, amount and day is one purchase counted twice. The automatic
            // paths no longer create these, but a ledger that already holds a pair from an older
            // build has no other way to learn of it: nothing about either row looks wrong alone.
            val statementByDay = transactions
                .filterNot { it.isVoided }
                .filter { it.source == TxSource.STATEMENT }
                .groupBy { Triple(it.accountId, it.amountMinor, it.occurredAt.dayIn(zone)) }
            transactions
                .filterNot { it.isVoided }
                .filter { it.source == TxSource.SMS }
                .forEach { sms ->
                    val twins = statementByDay[
                        Triple(sms.accountId, sms.amountMinor, sms.occurredAt.dayIn(zone)),
                    ].orEmpty()
                    if (twins.isNotEmpty()) {
                        add(
                            error(
                                "duplicate_statement_row",
                                "transactions",
                                sms.id,
                                "An SMS row stands beside a statement row of the same money.",
                            ),
                        )
                    }
                }

            // A transfer group is the only thing that keeps the legs of one movement together, so a
            // group left with a single active leg means the other side was lost and the money now
            // looks like a plain expense or income. Multi-part transfers may legitimately hold more.
            transactions.filterNot { it.isVoided }
                .filter { it.transferGroupId != null }
                .groupBy { it.transferGroupId!! }
                .forEach { (groupId, legs) ->
                    if (legs.size < 2) {
                        add(
                            error(
                                "incomplete_transfer_group",
                                "transfer_groups",
                                groupId,
                                "Transfer group has a single active leg instead of a pair.",
                            ),
                        )
                    }
                }

            val cases = db.debtDao().allCasesForIntegrity().associateBy { it.id }
            val events = db.debtDao().allEventsForIntegrity()
            val eventsByCase = events.groupBy { it.debtCaseId }
            events.forEach { event ->
                if (event.debtCaseId !in cases) {
                    add(error("orphan_debt_event", "debt_events", event.id, "Debt event points to a missing case."))
                }
                if (event.correctionOfEventId != null) {
                    val source = events.firstOrNull { it.id == event.correctionOfEventId }
                    if (source == null || !source.isVoided) {
                        add(error("orphan_debt_correction", "debt_events", event.id, "Debt correction does not point to a voided event."))
                    }
                    if (!event.isVoided || event.kind != DebtEventKind.ADJUSTMENT) {
                        add(error("active_debt_correction", "debt_events", event.id, "Debt correction rows must stay voided adjustments."))
                    }
                }
            }
            cases.values.forEach { debt ->
                val activeCredit = eventsByCase[debt.id].orEmpty().filterNot { it.isVoided }.sumOf { it.debtValueMinor }
                if (activeCredit < 0 || activeCredit > debt.originalAmountMinor) {
                    add(error("debt_credit_out_of_range", "debt_cases", debt.id, "Debt events credit more than the original debt or go negative."))
                }
                if (debt.status == DebtStatus.CLOSED && debt.closedAt == null) {
                    add(error("closed_debt_without_timestamp", "debt_cases", debt.id, "Closed debt case has no closedAt timestamp."))
                }
                if (debt.status == DebtStatus.OPEN && debt.closedAt != null) {
                    add(error("open_debt_with_close_timestamp", "debt_cases", debt.id, "Open debt case still has a closedAt timestamp."))
                }
            }
        }
        return IntegrityReport(issues)
    }

    private fun MutableList<IntegrityIssue>.error(code: String, entity: String, id: Long?, message: String) =
        IntegrityIssue(code, IntegritySeverity.ERROR, entity, id, message)
}
