package dev.whekin.whfin.data.categorization

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.WhfinDatabase

/** A category's appearance, group and position are one owner decision. */
class CategoryEditor(private val db: WhfinDatabase) {
    suspend fun save(expected: CategoryEntity, name: String, icon: String, color: Int,
        parentId: Long?, moveBy: Int) = db.withTransaction {
        val all = db.categoryDao().all()
        val current = all.singleOrNull { it.id == expected.id }
        require(current == expected && !expected.isSystem) { "The category changed." }
        require(name.trim().isNotEmpty())
        if (parentId != null) {
            val parent = all.singleOrNull { it.id == parentId }
            require(parent != null && CategoryTree(all).canParent(parent, expected)) { "The group is unavailable." }
        }
        val ordered = all.filter { it.kind == expected.kind && (it.parentId == parentId || it.id == expected.id) }.toMutableList()
        val from = ordered.indexOfFirst { it.id == expected.id }
        val to = Math.addExact(from, moveBy)
        require(from >= 0 && to in ordered.indices)
        if (from != to) ordered.add(to, ordered.removeAt(from))
        ordered.forEachIndexed { index, category ->
            val updated = if (category.id == expected.id) category.copy(name = name.trim(), icon = icon,
                color = color, parentId = parentId, sortOrder = if (moveBy == 0) category.sortOrder else index)
            else if (moveBy == 0) category else category.copy(sortOrder = index)
            if (updated != category) db.categoryDao().update(updated)
        }
    }
}
