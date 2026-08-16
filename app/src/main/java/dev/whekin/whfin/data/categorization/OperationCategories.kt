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
 * both. Money arriving as cash or a transfer is left alone: whether it is income or the user's own
 * money coming back is not something the operation label can answer.
 *
 * A bill payment sits between those. Credo files a mobile top-up, an electricity bill, an insurance
 * premium and a parking fine under one operation, so naming any of those would be inventing detail
 * the statement does not carry — but "a bill was paid" is exactly what it does carry, and leaving
 * the row blank claims less than the bank actually said. It is filed under one category named for
 * the operation itself, which the user can split later if the distinction turns out to matter.
 */
object OperationCategories {

    private data class Target(val icon: String, val kind: CategoryKind)

    private fun targetFor(operation: StatementOperation): Target? = when (operation) {
        StatementOperation.FEE -> Target("AccountBalance", CategoryKind.EXPENSE)
        StatementOperation.INTEREST -> Target("Percent", CategoryKind.INCOME)
        StatementOperation.BILL_PAYMENT -> Target("ReceiptLong", CategoryKind.EXPENSE)
        else -> null
    }

    fun categoryFor(operation: StatementOperation, categories: List<CategoryEntity>): CategoryEntity? {
        val target = targetFor(operation) ?: return null
        return categories.firstOrNull { it.icon == target.icon && it.kind == target.kind }
    }
}
