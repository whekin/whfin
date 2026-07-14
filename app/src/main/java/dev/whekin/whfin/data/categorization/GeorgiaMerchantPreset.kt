package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.WhfinDatabase

/**
 * Small, reviewable offline preset for common Georgian merchants.
 * User-learned Merchant.categoryId always wins: this only fills null categories.
 */
object GeorgiaMerchantPreset {
    private enum class Target(val icon: String, val kind: CategoryKind = CategoryKind.EXPENSE) {
        GROCERIES("ShoppingCart"),
        EATING_OUT("Restaurant"),
        TRANSPORT("DirectionsBus"),
        HEALTH("MedicalServices"),
        HOME("Chair"),
        TECH("Devices"),
        DELIVERY("DeliveryDining"),
        SUBSCRIPTIONS("Subscriptions"),
        UTILITIES("Bolt"),
        SALARY("Payments", CategoryKind.INCOME),
    }

    private data class Rule(val target: Target, val tokens: List<String>)

    private val rules = listOf(
        Rule(Target.GROCERIES, listOf(
            "nikora", "spar", "libre", "carrefour", "agrohub", "europroduct", "fresco",
            "goodwill", "magniti", "ori nabiji", "ioli", "foodmart", "s market", "smart retail",
            "hypermarket saburtalo", "melisa market", "belmarti",
        )),
        Rule(Target.TRANSPORT, listOf(
            "bus_tbilisi", "metro_tbilisi", "rope_tbilisi", "bolttaxi", "yandex.go",
            "go.yandex", "yandex.scooter",
        )),
        Rule(Target.HEALTH, listOf(
            "aversi", "psp ", "psp n", "gpc ", "caucasus medicine", "medula", "ns dent",
            "ghimilis saagento",
        )),
        Rule(Target.EATING_OUT, listOf(
            "mcdonald", "paul", "costa cafe", "entree", "brunch bake", "wrap master",
            "fast food", "restaurant", "food concept", "pazza", "delisze", "wine ice-cream",
        )),
        Rule(Target.HOME, listOf("gorgia", "domino", "jysk")),
        Rule(Target.TECH, listOf("zoommer", "zoomer georgia", "scroll")),
        Rule(Target.DELIVERY, listOf("wolt georgia", "yandex.deliver", "onex.ge")),
        Rule(Target.SUBSCRIPTIONS, listOf("google one", "google habitnow", "netcup", "chatgpt", "claude")),
        Rule(Target.UTILITIES, listOf(
            "თელმიკო", "სოკარ გაზი", "თბილსერვის", "მაგთი - მაგთი", "მაგთი - ოპტიკური",
        )),
        Rule(Target.SALARY, listOf("shps unotron", "შპს უნოტრონ")),
    )

    fun categoryFor(normalizedKey: String, categories: List<CategoryEntity>): CategoryEntity? {
        val target = rules.firstOrNull { rule -> rule.tokens.any(normalizedKey::contains) }?.target ?: return null
        return categories.firstOrNull { it.icon == target.icon && it.kind == target.kind }
    }

    suspend fun applyToUncategorized(db: WhfinDatabase): Int {
        val categories = db.categoryDao().all()
        var changed = 0
        db.merchantDao().uncategorized().forEach { merchant ->
            val category = categoryFor(merchant.normalizedKey, categories) ?: return@forEach
            db.merchantDao().setCategory(merchant.id, category.id)
            db.transactionDao().categorizeUnassignedForMerchant(merchant.id, category.id)
            changed++
        }
        return changed
    }
}
