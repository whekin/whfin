package dev.whekin.whfin.data.rates

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Booking a foreign row in lari: the rate of its own day, fetched once per day, and never a guess.
 */
@RunWith(AndroidJUnit4::class)
class TransactionValuationInstrumentedTest {

    private lateinit var db: WhfinDatabase
    private val zone = ZoneId.of("Asia/Tbilisi")
    private val requestedDays = mutableListOf<LocalDate>()
    private var failingDays = mutableSetOf<LocalDate>()

    private val provider = object : HistoricalRateProvider {
        override suspend fun quotesOn(date: LocalDate): Map<String, HistoricalRate> {
            requestedDays += date
            if (date in failingDays) throw RateException("SCRIPTED")
            return mapOf(
                "USD" to HistoricalRate("USD", BigDecimal("2.70"), date.toString()),
                "EUR" to HistoricalRate("EUR", BigDecimal("3.00"), date.toString()),
            )
        }
    }

    private lateinit var repository: TransactionValuationRepository
    private var accountId = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhfinDatabase::class.java).build()
        repository = TransactionValuationRepository(db, provider, zone) { 1_000L }
        accountId = db.accountDao().insert(
            AccountEntity(name = "Everyday", type = AccountType.BANK, currency = "USD"),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun add(
        minor: Long,
        currency: String,
        date: LocalDate,
        isTransfer: Boolean = false,
        key: String = "k$minor$currency$date$isTransfer",
    ): Long = db.transactionDao().insert(
        TransactionEntity(
            accountId = accountId,
            amountMinor = minor,
            currency = currency,
            occurredAt = date.atStartOfDay(zone).toInstant().toEpochMilli() + 12 * 3_600_000,
            status = TxStatus.CONFIRMED,
            source = TxSource.STATEMENT,
            isTransfer = isTransfer,
            externalKey = key,
            createdAt = 0,
        ),
    )

    @Test
    fun aForeignRowIsBookedAtTheRateOfItsOwnDay() = runBlocking {
        val id = add(-2_500, "USD", LocalDate.of(2026, 3, 14))

        val result = repository.backfill()

        assertEquals(1, result.valued)
        val row = db.transactionDao().byId(id)!!
        assertEquals(-6_750L, row.gelValueMinor)
        assertEquals("2026-03-14", row.gelRateOn)
    }

    @Test
    fun lariRowsAreNeverValuedAgain() = runBlocking {
        val id = add(-4_000, "GEL", LocalDate.of(2026, 3, 14))

        val result = repository.backfill()

        assertEquals(0, result.valued)
        assertTrue(requestedDays.isEmpty())
        assertNull(db.transactionDao().byId(id)!!.gelValueMinor)
    }

    @Test
    fun oneDayIsFetchedOnceHoweverManyRowsItHolds() = runBlocking {
        val day = LocalDate.of(2026, 3, 14)
        add(-100, "USD", day, key = "a")
        add(-200, "USD", day, key = "b")
        add(-300, "EUR", day, key = "c")

        val result = repository.backfill()

        assertEquals(3, result.valued)
        assertEquals(listOf(day), requestedDays)
    }

    @Test
    fun asecondPassAsksNothingWhenTheDayIsAlreadyKnown() = runBlocking {
        val day = LocalDate.of(2026, 3, 14)
        add(-100, "USD", day, key = "a")
        repository.backfill()
        requestedDays.clear()

        add(-500, "USD", day, key = "b")
        val result = repository.backfill()

        assertEquals(1, result.valued)
        assertTrue("a cached day must not be fetched again", requestedDays.isEmpty())
    }

    @Test
    fun anUnpricedDayLeavesTheRowUnvaluedAndNamesTheCurrency() = runBlocking {
        val day = LocalDate.of(2026, 3, 14)
        failingDays += day
        val id = add(-100, "USD", day)

        val result = repository.backfill()

        assertEquals(0, result.valued)
        assertEquals(setOf("USD"), result.unresolved)
        assertNull(db.transactionDao().byId(id)!!.gelValueMinor)
    }

    @Test
    fun transfersAreNotPricedBecauseTheyNeverReachTheTotals() = runBlocking {
        val id = add(-900, "USD", LocalDate.of(2026, 3, 14), isTransfer = true)

        val result = repository.backfill()

        assertEquals(0, result.valued)
        assertTrue(requestedDays.isEmpty())
        assertNull(db.transactionDao().byId(id)!!.gelValueMinor)
    }

    @Test
    fun oneCappedPassLeavesTheRestForTheNextOne() = runBlocking {
        (1..5).forEach { day -> add(-100, "USD", LocalDate.of(2026, 3, day), key = "d$day") }

        val first = repository.backfill(maxDays = 2)
        val second = repository.backfill(maxDays = 10)

        assertEquals(2, first.valued)
        assertEquals(3, second.valued)
        assertEquals(0, repository.backfill().valued)
    }
}
