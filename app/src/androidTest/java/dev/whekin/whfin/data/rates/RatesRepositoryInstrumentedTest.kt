package dev.whekin.whfin.data.rates

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whekin.whfin.data.db.WhfinDatabase
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Quote storage rules: providers run in order because crypto needs the USD pivot, a refresh replaces
 * rather than accumulates, and a failing source leaves the previous quote in place.
 */
@RunWith(AndroidJUnit4::class)
class RatesRepositoryInstrumentedTest {

    private lateinit var db: WhfinDatabase
    private var clock = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun provider(block: (RateContext) -> List<ExchangeRate>) = object : RateProvider {
        override suspend fun quotes(context: RateContext) = block(context)
    }

    private fun repository(vararg providers: RateProvider) =
        RatesRepository(db, providers.toList(), now = { clock })

    @Test
    fun quotesAreStoredAndReadBack() = runBlocking {
        val result = repository(
            provider { listOf(ExchangeRate("USD", BigDecimal("2.6266"), it.now, "2026-07-31", "nbg")) },
        ).refresh()

        assertEquals(1, result.updated)
        assertTrue(result.succeeded)
        val stored = db.exchangeRateDao().all().single()
        assertEquals("USD", stored.code)
        assertEquals("2.6266", stored.gelPerUnit)
        assertEquals(1_000L, stored.observedAt)
        assertEquals("2026-07-31", stored.validOn)
    }

    @Test
    fun aLaterProviderSeesWhatTheEarlierOneProduced() = runBlocking {
        var seenUsd: BigDecimal? = null
        repository(
            provider { listOf(ExchangeRate("USD", BigDecimal("2.5"), it.now)) },
            provider { context ->
                seenUsd = context.known["USD"]?.gelPerUnit
                listOf(ExchangeRate("TRX", BigDecimal("0.8"), context.now))
            },
        ).refresh()

        assertEquals(BigDecimal("2.5"), seenUsd)
        assertEquals(setOf("USD", "TRX"), db.exchangeRateDao().all().map { it.code }.toSet())
    }

    @Test
    fun refreshReplacesAQuoteInsteadOfAppendingAHistory() = runBlocking {
        val source = repository(provider { listOf(ExchangeRate("USD", BigDecimal("2.5"), it.now)) })
        source.refresh()

        clock = 2_000L
        repository(provider { listOf(ExchangeRate("USD", BigDecimal("2.7"), it.now)) }).refresh()

        val stored = db.exchangeRateDao().all().single()
        assertEquals("2.7", stored.gelPerUnit)
        assertEquals(2_000L, stored.observedAt)
    }

    @Test
    fun aFailingSourceKeepsTheLastGoodQuote() = runBlocking {
        repository(provider { listOf(ExchangeRate("USD", BigDecimal("2.5"), it.now)) }).refresh()

        clock = 3_000L
        val result = repository(provider { throw RateException("offline") }).refresh()

        assertEquals(1, result.failed)
        assertTrue(!result.succeeded)
        val stored = db.exchangeRateDao().all().single()
        assertEquals("2.5", stored.gelPerUnit)
        assertEquals(1_000L, stored.observedAt)
    }

    @Test
    fun withoutAnyRefreshThereIsNoQuoteAtAll() = runBlocking {
        assertNull(repository().observedAt())
        assertTrue(db.exchangeRateDao().all().isEmpty())
    }
}
