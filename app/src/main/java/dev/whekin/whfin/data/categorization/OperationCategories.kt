package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.statement.StatementOperation

/**
 * Categories that follow from what the bank says an operation *is*, before anything is known about
 * who was paid.
 *
 * This is deliberately narrow. A bank fee is the bank's own charge, so no merchant identity could
 * ever describe it better, and every bank has fees. A bill payment is the opposite case: Credo files
 * a mobile top-up, an electricity bill, an insurance premium and a parking fine under one operation,
 * so guessing a category from it would be inventing an answer the statement never gave.
 */
object OperationCategories {

    private const val BANK_FEE_ICON = "AccountBalance"

    private fun iconFor(operation: StatementOperation): String? = when (operation) {
        StatementOperation.FEE -> BANK_FEE_ICON
        else -> null
    }

    fun categoryFor(operation: StatementOperation, categories: List<CategoryEntity>): CategoryEntity? {
        val icon = iconFor(operation) ?: return null
        return categories.firstOrNull { it.icon == icon && it.kind == CategoryKind.EXPENSE }
    }
}
