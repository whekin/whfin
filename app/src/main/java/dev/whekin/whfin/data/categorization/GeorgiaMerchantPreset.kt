package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.WhfinDatabase

/**
 * Small, reviewable offline preset for common Georgian merchants.
 * User-learned Merchant.categoryId always wins: this only fills null categories.
 *
 * Every rule here has to be true for anyone who shops in Georgia. A counterparty that is only
 * meaningful to one person does not belong: an exchange office paying out a conversion looked
 * exactly like an employer paying a salary until its own ledger was read, and shipping that guess
 * would have taught every install the same wrong thing.
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
        BIKE("PedalBike"),
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
            "go.yandex", "yandex.scooter", "jetshr", "jet shr",
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
        Rule(Target.SUBSCRIPTIONS, listOf(
            "google one", "google habitnow", "google smart launcher", "google youtube",
            "netcup", "chatgpt", "claude", "openai", "anthropic", "songsterr", "spotify",
        )),
        Rule(Target.UTILITIES, listOf(
            "თელმიკო", "სოკარ გაზი", "თბილსერვის", "მაგთი - მაგთი", "მაგთი - ოპტიკური",
            "mysilknetapp", "salerequest.silknet",
        )),
        Rule(Target.BIKE, listOf("bike24")),
    )

    /**
     * Which category this merchant belongs in, named by icon rather than by a row that may not
     * exist yet. A ledger can be asked what categories it has earned before it has any.
     */
    fun iconFor(normalizedKey: String): String? {
        val comparableKey = comparable(normalizedKey)
        return rules.firstOrNull { rule ->
            rule.tokens.any { token -> comparableKey.contains(comparable(token)) }
        }?.target?.icon
    }

    fun categoryFor(normalizedKey: String, categories: List<CategoryEntity>): CategoryEntity? {
        // Statement processors change punctuation more often than merchant names. Treat dots,
        // underscores, asterisks and repeated whitespace as the same separator, so Yandex.Go,
        // YANDEX*GO and Yandex Go remain one offline rule without broad fuzzy matching.
        val comparableKey = comparable(normalizedKey)
        val target = rules.firstOrNull { rule ->
            rule.tokens.any { token -> comparableKey.contains(comparable(token)) }
        }?.target ?: return null
        return categories.firstOrNull { it.icon == target.icon && it.kind == target.kind }
    }

    private fun comparable(value: String): String = value
        .lowercase()
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()

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
