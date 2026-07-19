package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategoryKind
import dev.whekin.whfin.data.db.CategorySample
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorySuggesterTest {
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private val transport = 1L
    private val groceries = 2L
    private val restaurants = 3L
    private val rare = 4L

    /** Транспорт: очень часто, мелкие суммы. Продукты: часто, средние. Рестораны: реже, крупные. */
    private fun history(): List<CategorySample> = buildList {
        repeat(40) { index ->
            add(CategorySample(transport, -(100L + (index % 3) * 50), "GEL", now - index * day))
        }
        repeat(25) { index ->
            add(CategorySample(groceries, -(3_000L + (index % 5) * 800), "GEL", now - index * 2 * day))
        }
        repeat(10) { index ->
            add(CategorySample(restaurants, -(9_000L + (index % 4) * 2_000), "GEL", now - index * 5 * day))
        }
    }

    private val all = listOf(transport, groceries, restaurants, rare)

    private fun category(id: Long, icon: String) = CategoryEntity(
        id = id,
        name = "Category$id",
        kind = CategoryKind.EXPENSE,
        icon = icon,
        color = 0,
        sortOrder = id.toInt(),
    )

    @Test
    fun withoutAmountFrequencyOrderWins() {
        val ranked = CategorySuggester(history(), now).rank(all)
        assertEquals(listOf(transport, groceries, restaurants, rare), ranked)
    }

    @Test
    fun tinyAmountKeepsTransportOnTop() {
        val ranked = CategorySuggester(history(), now).rank(all, amountMinor = -150, currency = "GEL")
        assertEquals(transport, ranked.first())
    }

    @Test
    fun largeAmountLiftsRestaurantsAboveTransport() {
        val ranked = CategorySuggester(history(), now).rank(all, amountMinor = -11_000, currency = "GEL")
        assertEquals(restaurants, ranked.first())
        assertEquals(true, ranked.indexOf(transport) > ranked.indexOf(groceries))
    }

    @Test
    fun midAmountPrefersGroceries() {
        val ranked = CategorySuggester(history(), now).rank(all, amountMinor = -4_500, currency = "GEL")
        assertEquals(groceries, ranked.first())
    }

    @Test
    fun unknownCurrencyFallsBackToFrequency() {
        val ranked = CategorySuggester(history(), now).rank(all, amountMinor = -11_000, currency = "USD")
        assertEquals(listOf(transport, groceries, restaurants, rare), ranked)
    }

    @Test
    fun recencyDecayDropsStaleHabit() {
        // «Старая» категория с большим количеством операций годичной давности
        // проигрывает живой категории с меньшим, но свежим использованием.
        val samples = buildList {
            repeat(60) { add(CategorySample(rare, -1_000, "GEL", now - 300 * day)) }
            repeat(12) { index -> add(CategorySample(groceries, -1_000, "GEL", now - index * day)) }
        }
        val ranked = CategorySuggester(samples, now).rank(listOf(rare, groceries))
        assertEquals(groceries, ranked.first())
    }

    @Test
    fun emptyHistoryKeepsGivenOrder() {
        val ranked = CategorySuggester(emptyList(), now).rank(all, amountMinor = -500, currency = "GEL")
        assertEquals(all, ranked)
    }

    @Test
    fun categoryIdsWithoutSemanticPriorsRemainFrequencyRanked() {
        val samples = List(4) { CategorySample(rare, -100_000, "GEL", now - it * day) }
        val ranked = CategorySuggester(samples, now).rank(listOf(rare, transport), amountMinor = -100, currency = "GEL")
        assertEquals(rare, ranked.first())
    }

    @Test
    fun coldStartPriorsRerankGelCategoriesWithoutHistory() {
        val categories = listOf(
            category(groceries, "ShoppingCart"),
            category(restaurants, "Restaurant"),
            category(transport, "DirectionsBus"),
            category(rare, "Home"),
        )
        val suggester = CategorySuggester(emptyList(), now)

        assertEquals(
            transport,
            suggester.rankCategories(categories, amountMinor = -200, currency = "GEL").first().id,
        )
        assertEquals(
            groceries,
            suggester.rankCategories(categories, amountMinor = -4_500, currency = "GEL").first().id,
        )
        assertEquals(
            rare,
            suggester.rankCategories(categories, amountMinor = -100_000, currency = "GEL").first().id,
        )
    }

    @Test
    fun coldStartPriorsDoNotGuessForOtherCurrencies() {
        val categories = listOf(
            category(groceries, "ShoppingCart"),
            category(transport, "DirectionsBus"),
            category(rare, "Home"),
        )

        val ranked = CategorySuggester(emptyList(), now)
            .rankCategories(categories, amountMinor = -100_000, currency = "USD")

        assertEquals(categories.map { it.id }, ranked.map { it.id })
    }

    @Test
    fun personalSamplesGraduallyReplaceColdStartPrior() {
        val learnedLargeTransport = List(5) { index ->
            CategorySample(transport, -(90_000L + index * 5_000L), "GEL", now - index * day)
        }
        val categories = listOf(
            category(rare, "Home"),
            category(transport, "DirectionsBus"),
        )

        val coldStart = CategorySuggester(emptyList(), now)
            .rankCategories(categories, amountMinor = -100_000, currency = "GEL")
        val oneSample = CategorySuggester(learnedLargeTransport.take(1), now)
            .rankCategories(categories, amountMinor = -100_000, currency = "GEL")
        val learned = CategorySuggester(learnedLargeTransport, now)
            .rankCategories(categories, amountMinor = -100_000, currency = "GEL")

        assertEquals(rare, coldStart.first().id)
        assertEquals(rare, oneSample.first().id)
        assertEquals(transport, learned.first().id)
    }
}
