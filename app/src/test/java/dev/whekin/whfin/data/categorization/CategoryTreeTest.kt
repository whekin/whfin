package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryTreeTest {

    private fun category(
        id: Long,
        name: String,
        parentId: Long? = null,
        kind: CategoryKind = CategoryKind.EXPENSE,
        isSystem: Boolean = false,
    ) = CategoryEntity(
        id = id,
        name = name,
        parentId = parentId,
        kind = kind,
        icon = "PedalBike",
        color = 0,
        isSystem = isSystem,
    )

    private val bike = category(1, "Bike")
    private val parts = category(2, "Parts", parentId = 1)
    private val lifts = category(3, "Lifts", parentId = 1)
    private val groceries = category(4, "Groceries")
    private val salary = category(5, "Salary", kind = CategoryKind.INCOME)
    private val unaccounted = category(6, "Unaccounted", isSystem = true)
    private val tree = CategoryTree(listOf(bike, parts, lifts, groceries, salary, unaccounted))

    @Test
    fun `a child reports under its parent`() {
        assertEquals(1L, tree.rollupId(parts.id))
        assertEquals(1L, tree.rollupId(lifts.id))
    }

    @Test
    fun `a category without a parent reports under itself`() {
        assertEquals(4L, tree.rollupId(groceries.id))
        assertEquals(1L, tree.rollupId(bike.id))
    }

    @Test
    fun `an uncategorized row stays uncategorized`() {
        assertNull(tree.rollupId(null))
    }

    /**
     * A row whose category is missing from this list still has one. Passing the id through keeps it
     * a category the caller can report as unknown; folding it into null would silently merge real
     * spending into "uncategorized".
     */
    @Test
    fun `a category missing from the list passes through unchanged`() {
        assertEquals(404L, tree.rollupId(404L))
    }

    /** A restored file could carry a deeper chain; reading it must still terminate. */
    @Test
    fun `rollup takes a single hop even if the data nests deeper`() {
        val grandchild = category(7, "Cassettes", parentId = parts.id)
        val deep = CategoryTree(listOf(bike, parts, grandchild))

        assertEquals(parts.id, deep.rollupId(grandchild.id))
        assertEquals(bike.id, deep.rollupId(parts.id))
    }

    @Test
    fun `a group is recognized by having children, not by a flag`() {
        assertTrue(tree.isGroup(bike.id))
        assertFalse(tree.isGroup(parts.id))
        assertFalse(tree.isGroup(groceries.id))
    }

    @Test
    fun `children follow their root so the screen renders one flat list`() {
        assertEquals(
            listOf("Bike", "Parts", "Lifts", "Groceries", "Salary", "Unaccounted"),
            tree.ordered().map { it.name },
        )
    }

    @Test
    fun `a row can be read on its own`() {
        assertEquals("Bike · Parts", tree.qualifiedName(parts.id))
        assertEquals("Groceries", tree.qualifiedName(groceries.id))
    }

    @Test
    fun `income and expense never mix in one group`() {
        assertFalse(tree.canParent(groceries, salary))
        assertTrue(tree.canParent(groceries, category(8, "Street market")))
    }

    @Test
    fun `nesting stops at one level`() {
        // Neither a child adopting, nor a group being adopted.
        assertFalse(tree.canParent(parts, groceries))
        assertFalse(tree.canParent(groceries, bike))
    }

    @Test
    fun `the system category neither groups nor is grouped`() {
        assertFalse(tree.canParent(unaccounted, groceries))
        assertFalse(tree.canParent(groceries, unaccounted))
    }

    @Test
    fun `a category is not its own parent`() {
        assertFalse(tree.canParent(groceries, groceries))
    }
}
