package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.importer.MerchantNormalizer

/**
 * The one path from a bank descriptor to WHFIN's remembered merchant dictionary.
 *
 * User-learned categories win. A local preset may fill only a missing category, both for a new
 * merchant and for an old merchant that became recognizable after an app update.
 */
object MerchantCategorizer {
    suspend fun resolve(db: WhfinDatabase, raw: String): MerchantEntity? {
        val key = MerchantNormalizer.normalize(raw)
        if (key.isEmpty()) return null

        val existing = db.merchantDao().resolve(key)
        if (existing != null) return categorizeIfSafe(db, existing)

        val category = GeorgiaMerchantPreset.categoryFor(key, db.categoryDao().all())
        val id = db.merchantDao().insert(
            MerchantEntity(
                normalizedKey = key,
                displayName = MerchantNormalizer.displayName(raw),
                categoryId = category?.id,
            ),
        )
        val inserted = if (id > 0) db.merchantDao().byKey(key) else db.merchantDao().resolve(key)
        return inserted?.let { categorizeIfSafe(db, it) }
    }

    private suspend fun categorizeIfSafe(db: WhfinDatabase, merchant: MerchantEntity): MerchantEntity {
        if (merchant.categoryId != null) return merchant
        val category = GeorgiaMerchantPreset.categoryFor(merchant.normalizedKey, db.categoryDao().all())
            ?: return merchant
        db.merchantDao().setCategory(merchant.id, category.id)
        db.transactionDao().categorizeUnassignedForMerchant(merchant.id, category.id)
        return merchant.copy(categoryId = category.id)
    }
}
