package dev.whekin.whfin.data.integrity

import dev.whekin.whfin.data.db.DebtEventKind
import dev.whekin.whfin.data.db.DebtStatus
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.WhfinDatabase

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
class DataIntegrityChecker(private val db: WhfinDatabase) {
    suspend fun run(): IntegrityReport {
        val issues = buildList {
            val accounts = db.accountDao().allForIntegrity().associateBy { it.id }
            val transactions = db.transactionDao().allForIntegrity()
            val byTransaction = transactions.associateBy { it.id }
            val allocations = db.transactionAllocationDao().allForIntegrity().groupBy { it.transactionId }

            allocations.forEach { (transactionId, rows) ->
                val transaction = byTransaction[transactionId]
                if (transaction == null) {
                    add(error("orphan_allocation", "transaction_allocations", transactionId, "Allocation points to a missing transaction."))
                } else if (rows.sumOf { it.amountMinor } != transaction.amountMinor) {
                    add(error("allocation_total_mismatch", "transactions", transactionId, "Allocation total does not equal the parent amount."))
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
                if (transaction.isVoided && transaction.source in setOf(TxSource.STATEMENT, TxSource.SMS)) {
                    val corrections = transactions.filter { it.correctionOfTransactionId == transaction.id }
                    if (corrections.none { it.isVoided && it.source == TxSource.ADJUSTMENT }) {
                        add(error("missing_transaction_correction", "transactions", transaction.id, "Voided imported transaction has no audit correction."))
                    }
                }
                if (transaction.correctionOfTransactionId != null) {
                    val source = byTransaction[transaction.correctionOfTransactionId]
                    if (source == null || !source.isVoided) {
                        add(error("orphan_transaction_correction", "transactions", transaction.id, "Correction does not point to a voided source row."))
                    }
                    if (transaction.source != TxSource.ADJUSTMENT || !transaction.isVoided) {
                        add(error("active_transaction_correction", "transactions", transaction.id, "Correction rows must stay voided adjustment audit records."))
                    }
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
