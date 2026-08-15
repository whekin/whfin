package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CategoryEntity

/**
 * The one reading of `categories.parentId`.
 *
 * The tree is deliberately one level deep. A category exists to answer "where did the money go",
 * and two questions of that kind are enough: what kind of spending it was, and which group of
 * spending it belongs to. Deeper nesting multiplies the places a total can be reported under
 * without adding an answer anyone asks for.
 *
 * A parent keeps its own transactions and stays selectable. The alternative — a parent that may only
 * group — reads cleaner but forces every row of an existing category to physically move the moment
 * it gains a child, which is data surgery in exchange for tidiness. Here a split can be gradual:
 * rows stay where they are and merchant rules move them as they are recognized, while
 * [rollupId] keeps every total exact throughout.
 */
class CategoryTree(private val categories: List<CategoryEntity>) {

    private val childrenByParent: Map<Long, List<CategoryEntity>> =
        categories.filter { it.parentId != null }.groupBy { it.parentId!! }

    private val byId: Map<Long, CategoryEntity> = categories.associateBy { it.id }

    /** Categories with no parent, in their stored order. */
    val roots: List<CategoryEntity> = categories.filter { it.parentId == null }

    fun children(categoryId: Long): List<CategoryEntity> = childrenByParent[categoryId].orEmpty()

    fun isGroup(categoryId: Long): Boolean = childrenByParent.containsKey(categoryId)

    fun parentOf(categoryId: Long): CategoryEntity? =
        byId[categoryId]?.parentId?.let(byId::get)

    /**
     * Where a total for this category is reported.
     *
     * Single hop on purpose: one level is the whole contract, and a malformed chain from a restored
     * file must still terminate rather than climb forever.
     */
    fun rollupId(categoryId: Long?): Long? =
        categoryId?.let { byId[it]?.parentId ?: it }

    /** The full name a row can be read by on its own, e.g. "Bike · Parts". */
    fun qualifiedName(categoryId: Long): String? {
        val category = byId[categoryId] ?: return null
        val parent = parentOf(categoryId) ?: return category.name
        return "${parent.name} · ${category.name}"
    }

    /**
     * Roots first, each followed by its children, so one flat list can be rendered as a tree
     * without the screen re-deriving the structure.
     */
    fun ordered(): List<CategoryEntity> = roots.flatMap { root ->
        listOf(root) + children(root.id)
    }

    /** A category may parent another only within its own kind, and only if it is not a child. */
    fun canParent(parent: CategoryEntity, child: CategoryEntity): Boolean =
        parent.id != child.id &&
            parent.kind == child.kind &&
            parent.parentId == null &&
            !parent.isSystem &&
            !child.isSystem &&
            children(child.id).isEmpty()
}
