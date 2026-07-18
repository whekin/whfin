package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CategorySample
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorySuggesterReproTest {
    @Test
    fun reproExactWidgetScenario() {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val samples = buildList {
            repeat(20) { i -> add(CategorySample(1L, -(180L + (i % 3) * 20), "GEL", now - i * day)) }
            repeat(6) { i -> add(CategorySample(2L, -(11_000L + (i % 3) * 1_000), "GEL", now - i * 4 * day)) }
        }
        val suggester = CategorySuggester(samples, now)
        assertEquals(listOf(1L, 2L), suggester.rank(listOf(1L, 2L)))
        assertEquals(listOf(2L, 1L), suggester.rank(listOf(1L, 2L), amountMinor = -12_000, currency = "GEL"))
    }
}
