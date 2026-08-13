package dev.whekin.whfin.data.sms

import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.importer.MerchantNormalizer
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsCancellationMatcherTest {
    private val payment = CredoSmsParser.CardPayment(
        amountMinor = 1_234,
        currency = "GEL",
        cardLast4 = "0001",
        merchantRaw = "EXAMPLE MARKET",
        locationRaw = null,
        balanceMinor = null,
        balanceCurrency = null,
        timestamp = LocalDateTime.of(2026, 4, 3, 20, 48, 5),
    )

    @Test
    fun `merchant identity chooses the cancellation target`() {
        assertEquals(true, MerchantNormalizer.equivalent("EXAMPLE MARKET>Tbilisi", payment.merchantRaw))
        assertEquals(false, MerchantNormalizer.equivalent("ANOTHER MARKET", payment.merchantRaw))
        val match = SmsCancellationMatcher.match(
            payment = payment,
            occurredAt = 1_000,
            candidates = listOf(
                diagnostic(id = 1, merchant = "ANOTHER MARKET"),
                diagnostic(id = 2, merchant = "EXAMPLE MARKET>Tbilisi"),
            ),
        )

        assertEquals(2L, match?.id)
    }

    @Test
    fun `two indistinguishable payments are not guessed`() {
        val match = SmsCancellationMatcher.match(
            payment = payment,
            occurredAt = 1_000,
            candidates = listOf(
                diagnostic(id = 1, merchant = "EXAMPLE MARKET"),
                diagnostic(id = 2, merchant = "EXAMPLE MARKET>Tbilisi"),
            ),
        )

        assertNull(match)
    }

    private fun diagnostic(id: Long, merchant: String) = SmsDiagnosticEntity(
        id = id,
        externalKey = "sms|$id",
        kind = SmsDiagnosticKind.CARD_PAYMENT,
        outcome = SmsDiagnosticOutcome.IMPORTED,
        receivedAt = 1_000,
        occurredAt = 1_000,
        amountMinor = 1_234,
        currency = "GEL",
        cardLast4 = "0001",
        counterparty = merchant,
        transactionId = id,
        accountId = 1,
        updatedAt = 1_000,
    )
}
