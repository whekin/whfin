package dev.whekin.whfin.data.categorization

import androidx.room.withTransaction
import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.mutation.TransactionMutationModule

/** Explicit categorization, remembered rule and history update commit together. */
class TransactionCategorizer(private val db: WhfinDatabase) {
    private val mutations = TransactionMutationModule(db)

    suspend fun assign(transactionId: Long, merchantId: Long?, categoryId: Long) = db.withTransaction {
        mutations.assignCategory(transactionId, categoryId)
        merchantId?.let {
            db.merchantDao().setCategory(it, categoryId)
            db.transactionDao().categorizeUnassignedForMerchant(it, categoryId)
        }
    }

    suspend fun createAndAssign(transactionId: Long, merchantId: Long?, name: String, kind: CategoryKind,
        icon: String, color: Int): CategoryEntity = db.withTransaction {
        require(name.trim().isNotEmpty())
        val category = CategoryEntity(name = name.trim(), kind = kind, icon = icon, color = color,
            sortOrder = (db.categoryDao().all().maxOfOrNull { it.sortOrder } ?: 0) + 1)
        val created = category.copy(id = db.categoryDao().insert(category))
        assign(transactionId, merchantId, created.id)
        created
    }
}
