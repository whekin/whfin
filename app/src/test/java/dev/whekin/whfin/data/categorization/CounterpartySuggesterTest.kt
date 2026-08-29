package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CounterpartyProfile
import dev.whekin.whfin.data.db.PersonEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterpartySuggesterTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun aNamePaidTodayOutranksTheShopOfManyQuietMonths() {
        val recent = candidate("Alisa", usageCount = 1, ageDays = 0)
        val frequentButStale = candidate("Sunroom Grocer", usageCount = 12, ageDays = 90)

        val ranked = CounterpartySuggester.rank(listOf(frequentButStale, recent), null, now)

        assertEquals(listOf("Alisa", "Sunroom Grocer"), ranked.map { it.name })
    }

    @Test
    fun frequencyStillWinsOnceTheRecentPaymentIsNoLongerRecent() {
        val onceAWeekAgo = candidate("Alisa", usageCount = 1, ageDays = 14)
        val everyWeek = candidate("Sunroom Grocer", usageCount = 12, ageDays = 3)

        val ranked = CounterpartySuggester.rank(listOf(onceAWeekAgo, everyWeek), null, now)

        assertEquals(listOf("Sunroom Grocer", "Alisa"), ranked.map { it.name })
    }

    /** The two rails answer each other: inside a category, the names filed there come first. */
    @Test
    fun theChosenCategoryLiftsTheNamesUsuallyFiledThere() {
        val cafe = candidate("Copper Table", usageCount = 3, ageDays = 20, categoryId = EATING_OUT)
        val grocer = candidate("Sunroom Grocer", usageCount = 8, ageDays = 20, categoryId = GROCERIES)

        val neutral = CounterpartySuggester.rank(listOf(cafe, grocer), null, now)
        val inEatingOut = CounterpartySuggester.rank(listOf(cafe, grocer), EATING_OUT, now)

        assertEquals("Sunroom Grocer", neutral.first().name)
        assertEquals("Copper Table", inEatingOut.first().name)
    }

    @Test
    fun aPersonNeverPaidIsFindableButDoesNotOutrankSomeoneActuallyPaid() {
        val profiles = listOf(profile(1, "Sunroom Grocer", expenseCount = 4, ageDays = 10))
        val people = listOf(person(1, "Misho"), person(2, "Sunroom Grocer"))

        val candidates = CounterpartySuggester.candidates(
            profiles, people, CounterpartyDirection.OUTGOING,
        )
        val ranked = CounterpartySuggester.rank(candidates, null, now)

        // The person duplicating a name the ledger already knows is not offered twice.
        assertEquals(listOf("Sunroom Grocer", "Misho"), ranked.map { it.name })
        assertEquals(1L, ranked.last().personId)
    }

    @Test
    fun aNameOnlySeenOnTheOtherSideOfTheLedgerIsNotOfferedHere() {
        val employer = profile(1, "Anthropic", expenseCount = 0, incomeCount = 6, ageDays = 5)
        val grocer = profile(2, "Sunroom Grocer", expenseCount = 9, ageDays = 5)

        val outgoing = CounterpartySuggester.candidates(
            listOf(employer, grocer), emptyList(), CounterpartyDirection.OUTGOING,
        )
        val incoming = CounterpartySuggester.candidates(
            listOf(employer, grocer), emptyList(), CounterpartyDirection.INCOMING,
        )

        assertEquals(listOf("Sunroom Grocer"), outgoing.map { it.name })
        assertEquals(listOf("Anthropic"), incoming.map { it.name })
    }

    @Test
    fun searchIgnoresCaseAndTheLetterPeopleTypeWithoutItsDots() {
        val candidates = listOf(candidate("Пётр", usageCount = 1, ageDays = 1))

        assertTrue(CounterpartySuggester.search(candidates, "петр").isNotEmpty())
        assertTrue(CounterpartySuggester.search(candidates, "ПЁТ").isNotEmpty())
        assertTrue(CounterpartySuggester.search(candidates, "misho").isEmpty())
    }

    private fun candidate(
        name: String,
        usageCount: Int,
        ageDays: Long,
        categoryId: Long? = null,
    ) = CounterpartyCandidate(
        name = name,
        merchantId = name.hashCode().toLong(),
        categoryId = categoryId,
        usageCount = usageCount,
        lastUsedAt = now - ageDays * day,
    )

    private fun profile(
        merchantId: Long,
        name: String,
        expenseCount: Int = 0,
        incomeCount: Int = 0,
        ageDays: Long = 0,
        categoryId: Long? = null,
    ) = CounterpartyProfile(
        merchantId = merchantId,
        displayName = name,
        categoryId = categoryId,
        expenseCount = expenseCount,
        incomeCount = incomeCount,
        latestAt = now - ageDays * day,
    )

    private fun person(id: Long, name: String) = PersonEntity(id = id, name = name, color = 0)

    private companion object {
        const val EATING_OUT = 7L
        const val GROCERIES = 3L
    }
}
