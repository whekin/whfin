package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.statement.StatementOperation

/**
 * Categories that follow from what the bank says an operation *is*, before anything is known about
 * who paid or was paid.
 *
 * This is deliberately narrow. A bank fee is the bank's own charge and deposit interest is the
 * bank's own payment: no counterparty identity could describe either better, and every bank has
 * both. A bill payment is the opposite case — Credo files a mobile top-up, an electricity bill, an
 * insurance premium and a parking fine under one operation, so a category chosen from it would be
 * invented rather than read. Money arriving as cash or a transfer is left alone for the same
 * reason: whether it is income or the user's own money coming back is not something the operation
 * label can answer.
 */
object OperationCategories {

    private data class Target(val icon: String, val kind: CategoryKind)

    private fun targetFor(operation: StatementOperation): Target? = when (operation) {
        StatementOperation.FEE -> Target("AccountBalance", CategoryKind.EXPENSE)
        StatementOperation.INTEREST -> Target("Percent", CategoryKind.INCOME)
        else -> null
    }

    fun categoryFor(operation: StatementOperation, categories: List<CategoryEntity>): CategoryEntity? {
        val target = targetFor(operation) ?: return null
        return categories.firstOrNull { it.icon == target.icon && it.kind == target.kind }
    }
}
