package dev.whekin.whfin.data.rates

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.db.WhfinDatabase
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Valuing more days than one pass is allowed to fetch.
 *
 * One pass is capped so ordinary paths never burst into hundreds of requests; a one-off load of
 * years of history has to see it through instead of leaving the numbers to trickle in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransactionValuationPassesTest {

    private lateinit var db: WhfinDatabase
    private val zone = ZoneId.of("Asia/Tbilisi")
    private var accountId = 0L

    private class CountingProvider(private val failFor: Set<LocalDate> = emptySet()) : HistoricalRateProvider {
        var calls = 0

        override suspend fun quotesOn(date: LocalDate): Map<String, HistoricalRate> {
            calls++
            if (date in failFor) throw RateException("no quote for $date")
            return mapOf("USD" to HistoricalRate("USD", BigDecimal("2.70"), date.toString()))
        }
    }

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountId = db.accountDao().insert(
            AccountEntity(name = "Credo USD", type = AccountType.BANK, currency = "USD"),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun spend(day: LocalDate) {
        db.transactionDao().insert(
            TransactionEntity(
                accountId = accountId,
                amountMinor = -1_000,
                currency = "USD",
                occurredAt = day.atStartOfDay(zone).toInstant().toEpochMilli(),
                status = TxStatus.CONFIRMED,
                source = TxSource.STATEMENT,
                createdAt = 0,
            ),
        )
    }

    @Test
    fun aHistoryLoadPricesEveryDayInsteadOfStoppingAtTheRoutineCap() = runBlocking {
        // Ninety distinct days: more than two passes of forty.
        val start = LocalDate.of(2026, 1, 1)
        repeat(90) { spend(start.plusDays(it.toLong())) }
        val provider = CountingProvider()

        val onePass = TransactionValuationRepository(db, provider).backfill()
        assertEquals(40, onePass.daysFetched)
        assertEquals(40, onePass.valued)

        val rest = TransactionValuationRepository(db, provider).backfillAll()

        assertEquals(50, rest.valued)
        assertEquals(0, db.transactionDao().awaitingValuation("GEL").size)
    }

    @Test
    fun daysThatCannotBePricedEndTheRunInsteadOfLoopingOnThem() = runBlocking {
        val start = LocalDate.of(2026, 3, 1)
        repeat(3) { spend(start.plusDays(it.toLong())) }
        val provider = CountingProvider(failFor = setOf(start, start.plusDays(1), start.plusDays(2)))

        val result = TransactionValuationRepository(db, provider).backfillAll()

        assertEquals(0, result.valued)
        assertTrue(result.unresolved.contains("USD"))
        // One pass tried each day once and the run stopped, rather than retrying ten times over.
        assertEquals(3, provider.calls)
        assertEquals(3, db.transactionDao().awaitingValuation("GEL").size)
    }

    @Test
    fun aRunWithNothingLeftToDoCostsNoRequests() = runBlocking {
        val provider = CountingProvider()

        val result = TransactionValuationRepository(db, provider).backfillAll()

        assertEquals(0, result.valued)
        assertEquals(0, provider.calls)
    }
}
