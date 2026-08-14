package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.statement.StatementParsers

/**
 * Everything WHFIN can categorize on its own, without asking the user anything.
 *
 * Rules arrive after the history they describe: a merchant recognized by an app update, or an
 * operation kind WHFIN only learned to read later. Both are re-applied here rather than at the
 * moment a row is written, so shipping a rule is enough to fix the past as well as the future.
 * Every pass fills empty categories only — a category the user chose is never revisited.
 */
object CategoryMaintenance {

    data class Result(
        val merchantsMatched: Int = 0,
        val operationsMatched: Int = 0,
        val counterpartiesMatched: Int = 0,
    ) {
        val total: Int get() = merchantsMatched + operationsMatched + counterpartiesMatched
    }

    suspend fun run(db: WhfinDatabase): Result = Result(
        merchantsMatched = GeorgiaMerchantPreset.applyToUncategorized(db),
        operationsMatched = applyOperationKinds(db),
        counterpartiesMatched = applyCounterpartyRules(db),
    )

    /**
     * Recipients the user has already named. A transfer imported after the rule was written is
     * categorized as it lands; this reaches the ones that arrived before it, and the ones whose
     * spelling changed after it.
     */
    private suspend fun applyCounterpartyRules(db: WhfinDatabase): Int {
        var changed = 0
        db.counterpartyRuleDao().all().forEach { rule ->
            val categoryId = rule.categoryId ?: return@forEach
            db.transactionDao().categorizeUnassignedForCounterparty(rule.iban, categoryId)
            changed++
        }
        return changed
    }

    /**
     * Rows the bank itself classified: a fee is a fee whoever charged it.
     *
     * The label is read back through the same adapter that wrote it, so no bank's vocabulary leaks
     * into this pass.
     */
    private suspend fun applyOperationKinds(db: WhfinDatabase): Int {
        val categories = db.categoryDao().all()
        var changed = 0
        db.transactionDao().uncategorizedStatementNotes().forEach { row ->
            val operation = StatementParsers.operationFor(row.note) ?: return@forEach
            val category = OperationCategories.categoryFor(operation, categories) ?: return@forEach
            db.transactionDao().categorizeIfUnassigned(row.id, category.id)
            changed++
        }
        return changed
    }
}
