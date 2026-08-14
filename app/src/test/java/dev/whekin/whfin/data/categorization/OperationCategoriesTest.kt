package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.statement.StatementOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a category may be derived from the bank's own classification, and what may not. */
class OperationCategoriesTest {

    private val bankFees = CategoryEntity(
        id = 7,
        name = "Bank fees",
        kind = CategoryKind.EXPENSE,
        icon = "AccountBalance",
        color = 0xFF90A4AE.toInt(),
    )
    private val groceries = CategoryEntity(
        id = 8,
        name = "Groceries",
        kind = CategoryKind.EXPENSE,
        icon = "ShoppingCart",
        color = 0xFF78906F.toInt(),
    )

    @Test
    fun `a fee names its own category`() {
        assertEquals(
            bankFees,
            OperationCategories.categoryFor(StatementOperation.FEE, listOf(groceries, bankFees)),
        )
    }

    @Test
    fun `a bill payment is not guessed`() {
        // Credo files a mobile top-up, an electricity bill, an insurance premium and a parking fine
        // under one operation. Any category chosen here would be invented, not read.
        assertNull(
            OperationCategories.categoryFor(
                StatementOperation.BILL_PAYMENT,
                listOf(groceries, bankFees),
            ),
        )
    }

    @Test
    fun `no operation other than a fee claims a category`() {
        val claimed = StatementOperation.entries.filter {
            OperationCategories.categoryFor(it, listOf(groceries, bankFees)) != null
        }

        assertEquals(listOf(StatementOperation.FEE), claimed)
    }

    @Test
    fun `a ledger without the seeded fee category is left alone`() {
        assertNull(OperationCategories.categoryFor(StatementOperation.FEE, listOf(groceries)))
    }

    @Test
    fun `an income category never absorbs an expense operation`() {
        val income = bankFees.copy(id = 9, kind = CategoryKind.INCOME)

        assertNull(OperationCategories.categoryFor(StatementOperation.FEE, listOf(income)))
    }
}
