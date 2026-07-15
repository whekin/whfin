package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryRow(val category: CategoryEntity, val uses: Int)

class CategoriesViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as WhfinApp).db

    val rows: StateFlow<List<CategoryRow>?> = combine(
        db.categoryDao().observeAll(),
        db.transactionDao().observeCategoryUsage(),
    ) { categories, usage ->
        val uses = usage.associate { it.categoryId to it.cnt }
        categories.map { CategoryRow(it, uses[it.id] ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String, kind: CategoryKind, icon: String, color: Int) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            db.categoryDao().insert(CategoryEntity(
                name = clean,
                kind = kind,
                icon = icon,
                color = color,
                sortOrder = (db.categoryDao().all().maxOfOrNull { it.sortOrder } ?: 0) + 1,
            ))
        }
    }

    fun update(category: CategoryEntity, name: String, icon: String, color: Int) {
        if (category.isSystem) return
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            db.categoryDao().update(category.copy(name = clean, icon = icon, color = color))
        }
    }

    /** Сдвиг внутри своего kind; sortOrder переписывается по позиции, чужой kind не трогаем. */
    fun move(category: CategoryEntity, delta: Int) {
        viewModelScope.launch {
            db.withTransaction {
                val siblings = db.categoryDao().all().filter { it.kind == category.kind }
                val index = siblings.indexOfFirst { it.id == category.id }
                val target = index + delta
                if (index < 0 || target !in siblings.indices) return@withTransaction
                val reordered = siblings.toMutableList().apply { add(target, removeAt(index)) }
                reordered.forEachIndexed { position, entry ->
                    if (entry.sortOrder != position) {
                        db.categoryDao().update(entry.copy(sortOrder = position))
                    }
                }
            }
        }
    }

    /** DAO сам защищает системные категории (isSystem = 0 в WHERE). */
    fun delete(category: CategoryEntity) {
        viewModelScope.launch { db.categoryDao().delete(category.id) }
    }
}
