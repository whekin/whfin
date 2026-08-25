package dev.whekin.whfin.data.recurring

import dev.whekin.whfin.data.db.MerchantEntity
import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringChargesTest {

    private val today = LocalDate.of(2026, 8, 25)

    @Test
    fun `a steady monthly payment is recognised with its typical amount and day`() {
        val observations = monthly(
            key = "merchant:1",
            label = "Landlord",
            months = listOf(4, 5, 6, 7),
            day = 3,
            amountMinor = 120_000,
        )

        val charges = detectRecurringCharges(observations, today)

        assertEquals(1, charges.size)
        assertEquals("Landlord", charges[0].label)
        assertEquals(120_000L, charges[0].typicalMinor)
        assertEquals(3, charges[0].expectedDay)
    }

    @Test
    fun `two months of history is not yet a pattern`() {
        val observations = monthly(
            key = "merchant:1",
            label = "Gym",
            months = listOf(6, 7),
            day = 10,
            amountMinor = 8_000,
        )

        assertTrue(detectRecurringCharges(observations, today).isEmpty())
    }

    @Test
    fun `a shop visited every week is a habit, not a bill`() {
        val observations = (5..7).flatMap { month ->
            listOf(2, 9, 16, 23).map { day ->
                RecurringObservation("merchant:2", "Supermarket", LocalDate.of(2026, month, day), 6_000)
            }
        }

        assertTrue(detectRecurringCharges(observations, today).isEmpty())
    }

    @Test
    fun `an amount that jumps around is not treated as an obligation`() {
        val observations = listOf(
            RecurringObservation("merchant:3", "Shop", LocalDate.of(2026, 5, 4), 10_000),
            RecurringObservation("merchant:3", "Shop", LocalDate.of(2026, 6, 4), 90_000),
            RecurringObservation("merchant:3", "Shop", LocalDate.of(2026, 7, 4), 30_000),
        )

        assertTrue(detectRecurringCharges(observations, today).isEmpty())
    }

    @Test
    fun `a bill already paid this month is finished business`() {
        val observations = monthly(
            key = "merchant:1",
            label = "Landlord",
            months = listOf(4, 5, 6, 7),
            day = 3,
            amountMinor = 120_000,
        ) + RecurringObservation("merchant:1", "Landlord", LocalDate.of(2026, 8, 3), 120_000)

        assertTrue(recurringDue(observations, today).isEmpty())
    }

    @Test
    fun `a bill whose usual day has passed is still owed`() {
        val observations = monthly(
            key = "merchant:1",
            label = "Landlord",
            months = listOf(4, 5, 6, 7),
            day = 3,
            amountMinor = 120_000,
        )

        assertEquals(listOf("Landlord"), recurringDue(observations, today).map { it.label })
    }

    @Test
    fun `observations keep own spending and its stable identity`() {
        val merchant = MerchantEntity(id = 7, normalizedKey = "landlord", displayName = "Landlord")
        val transactions = listOf(
            transaction(id = 1, amountMinor = -120_000, day = LocalDate.of(2026, 7, 3), merchantId = 7),
            // Money moved between own accounts, a correction and income are not spending.
            transaction(id = 2, amountMinor = -50_000, day = LocalDate.of(2026, 7, 4), merchantId = 7, isTransfer = true),
            transaction(id = 3, amountMinor = -900, day = LocalDate.of(2026, 7, 5), merchantId = 7, source = TxSource.ADJUSTMENT),
            transaction(id = 4, amountMinor = 300_000, day = LocalDate.of(2026, 7, 6), merchantId = 7),
            // A foreign row whose day has no quote cannot be compared against its own past.
            transaction(id = 5, amountMinor = -2_000, day = LocalDate.of(2026, 7, 7), merchantId = 7, currency = "USD"),
            // Payee known only by the receiving account still has a stable identity.
            transaction(id = 6, amountMinor = -40_000, day = LocalDate.of(2026, 7, 8), iban = "GE00TEST0001", counterparty = "N. Surname"),
        )

        val observations = recurringObservations(transactions, listOf(merchant), ZoneOffset.UTC)

        assertEquals(
            listOf("merchant:7" to 120_000L, "iban:GE00TEST0001" to 40_000L),
            observations.map { it.key to it.amountMinor },
        )
    }

    @Test
    fun `a foreign bill is compared through its booked lari value`() {
        val transactions = (5..7).map { month ->
            transaction(
                id = month.toLong(),
                amountMinor = -1_000,
                day = LocalDate.of(2026, month, 12),
                merchantId = 7,
                currency = "USD",
                gelValueMinor = -2_700,
            )
        }
        val merchant = MerchantEntity(id = 7, normalizedKey = "cloud", displayName = "Cloud")

        val charges = detectRecurringCharges(
            recurringObservations(transactions, listOf(merchant), ZoneOffset.UTC),
            today,
        )

        assertEquals(listOf(2_700L), charges.map { it.typicalMinor })
    }

    private fun monthly(
        key: String,
        label: String,
        months: List<Int>,
        day: Int,
        amountMinor: Long,
    ) = months.map { month ->
        RecurringObservation(key, label, LocalDate.of(2026, month, day), amountMinor)
    }

    private fun transaction(
        id: Long,
        amountMinor: Long,
        day: LocalDate,
        merchantId: Long? = null,
        currency: String = "GEL",
        gelValueMinor: Long? = null,
        isTransfer: Boolean = false,
        source: TxSource = TxSource.STATEMENT,
        iban: String? = null,
        counterparty: String? = null,
    ) = TransactionEntity(
        id = id,
        accountId = 1,
        amountMinor = amountMinor,
        currency = currency,
        gelValueMinor = gelValueMinor,
        occurredAt = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        status = TxStatus.CONFIRMED,
        source = source,
        isTransfer = isTransfer,
        merchantId = merchantId,
        counterpartyIban = iban,
        rawCounterparty = counterparty,
    )
}
