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
        val skeleton = GeorgianLatin.skeleton(key)
        db.merchantDao().byAlias(skeleton)
            ?.takeIf { accepts(it, key, skeleton) }
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

    /**
     * Whether a counterparty found by skeleton really is the one this name belongs to.
     *
     * A spelling that differs from the counterparty's own key was recorded because something proved
     * it theirs — a shared account, or the owner joining them by hand — so it answers whatever
     * alphabet it arrives in. A spelling equal to their own key proves nothing by itself: the
     * skeleton drops distinctions romanization cannot carry, so two Georgian names differing only by
     * aspiration collapse onto it. That one is accepted only across alphabets, which is the case it
     * exists for.
     */
    private fun accepts(candidate: MerchantEntity, key: String, skeleton: String): Boolean =
        skeleton != GeorgianLatin.skeleton(candidate.normalizedKey) ||
            GeorgianRomanization.isGeorgian(candidate.normalizedKey) != GeorgianRomanization.isGeorgian(key)

    private suspend fun categorizeIfSafe(db: WhfinDatabase, merchant: MerchantEntity): MerchantEntity {
        if (merchant.categoryId != null) return merchant
        val category = GeorgiaMerchantPreset.categoryFor(merchant.normalizedKey, db.categoryDao().all())
            ?: return merchant
        db.merchantDao().setCategory(merchant.id, category.id)
        db.transactionDao().categorizeUnassignedForMerchant(merchant.id, category.id)
        return merchant.copy(categoryId = category.id)
    }
}
