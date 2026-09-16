package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.MerchantAliasEntity
import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.importer.GeorgianLatin
import dev.whekin.whfin.data.importer.GeorgianRomanization
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

        db.merchantDao().byKey(key)?.let { return categorizeIfSafe(db, it) }
        // The same counterparty reaches WHFIN in two alphabets, because the name belongs to their
        // own bank rather than to the statement. Only a cross-script match is accepted: the
        // skeleton drops distinctions romanization cannot carry, so two Georgian keys that collapse
        // onto each other are two names, not one name written twice.
        val skeleton = GeorgianLatin.skeleton(key)
        db.merchantDao().byAlias(skeleton)
            ?.takeIf { GeorgianRomanization.isGeorgian(it.normalizedKey) != GeorgianRomanization.isGeorgian(key) }
            ?.let { return categorizeIfSafe(db, it) }

        val category = GeorgiaMerchantPreset.categoryFor(key, db.categoryDao().all())
        val id = db.merchantDao().insert(
            MerchantEntity(
                normalizedKey = key,
                displayName = MerchantNormalizer.displayName(raw),
                categoryId = category?.id,
            ),
        )
        val inserted = db.merchantDao().byKey(key) ?: return null
        if (id > 0 && skeleton.isNotBlank()) {
            db.merchantDao().insertAlias(
                MerchantAliasEntity(merchantId = inserted.id, pattern = skeleton),
            )
        }
        return categorizeIfSafe(db, inserted)
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
