package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.statement.BankStatement
import dev.whekin.whfin.data.statement.StatementRow

/**
 * What an import would do, decided before anything is written.
 *
 * Separating the decision from the writing is what makes an import explainable: the counts a user
 * sees afterwards are the plan, not a tally accumulated while rows were already landing in the
 * ledger. It also makes the rules testable without a database full of side effects.
 */
data class ImportPlan(
    val statement: BankStatement,
    val accountId: Long,
    val accountCreated: Boolean,
    val accountAdopted: Boolean,
    val entries: List<PlannedRow>,
    /**
     * Rows WHFIN recorded itself inside the statement's confirmed period that the statement did not
     * confirm. They are never deleted silently — the user decides.
     */
    val reviewCandidateIds: List<Long>,
) {
    val inserted: Int get() = entries.count { it is PlannedRow.Insert }
    val reconciled: Int get() = entries.count { it is PlannedRow.Reconcile }
    val duplicates: Int get() = entries.count { it is PlannedRow.Duplicate }
    val totalRows: Int get() = entries.size

    /** True when re-running this import would leave the ledger exactly as it is. */
    val isNoOp: Boolean get() = inserted == 0 && reconciled == 0 && reviewCandidateIds.isEmpty()
}

sealed interface PlannedRow {
    val row: StatementRow
    val externalKey: String

    /** A movement WHFIN has never seen. */
    data class Insert(override val row: StatementRow, override val externalKey: String) : PlannedRow

    /**
     * A draft WHFIN already recorded — an SMS, or something typed by hand — that this statement
     * line confirms. The draft is upgraded in place so the same payment is never counted twice.
     */
    data class Reconcile(
        override val row: StatementRow,
        override val externalKey: String,
        val transactionId: Long,
    ) : PlannedRow

    /** Already imported under this exact key; importing the same file again changes nothing. */
    data class Duplicate(override val row: StatementRow, override val externalKey: String) : PlannedRow
}
