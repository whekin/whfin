package dev.whekin.whfin.data.importer

/**
 * What a batch of picked statement files still has to do, and what it must ask first.
 *
 * @param confirmLedgers ledgers that do not exist yet, named once each. Empty means no question.
 * @param toImport the files worth writing, in the order they were picked.
 * @param unchanged how many files were dropped because importing them would change nothing.
 */
data class StatementBatchPlan<T>(
    val confirmLedgers: List<String>,
    val toImport: List<T>,
    val unchanged: Int,
)

/**
 * Decides a whole batch at once.
 *
 * Importing several statements is routine, so the flow stays one gesture: a file that adds nothing is
 * dropped here rather than imported into a "0 added" history row, and the only interruption is the
 * one mistake a result screen cannot undo — a wrong file that leaves a new account behind. Two files
 * for the same missing ledger create it once, so it is named once.
 *
 * A file whose [preview] is null could not be read. It is kept: the import itself reports why, in the
 * same words it always has.
 */
fun <T> planStatementBatch(
    files: List<T>,
    preview: (T) -> StatementImporter.Preview?,
): StatementBatchPlan<T> {
    val toImport = files.filterNot { preview(it)?.changesNothing == true }
    return StatementBatchPlan(
        confirmLedgers = toImport
            .mapNotNull { preview(it)?.takeIf { plan -> plan.createsAccount }?.ledgerName }
            .distinct(),
        toImport = toImport,
        unchanged = files.size - toImport.size,
    )
}
