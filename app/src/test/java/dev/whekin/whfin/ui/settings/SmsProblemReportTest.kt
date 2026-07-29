package dev.whekin.whfin.ui.settings

import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.db.SmsDiagnosticReason
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsProblemReportTest {
    private val diagnostic = SmsDiagnosticEntity(
        id = 7,
        externalKey = "sms|private-fingerprint",
        kind = SmsDiagnosticKind.UNRECOGNIZED,
        outcome = SmsDiagnosticOutcome.UNRECOGNIZED,
        reason = SmsDiagnosticReason.PARSE_FAILURE,
        receivedAt = 1_000,
        amountMinor = 123_45,
        currency = "GEL",
        balanceMinor = 456_78,
        balanceCurrency = "GEL",
        cardLast4 = "0001",
        counterparty = "Private Person",
        fromIban = "GE00CD0000000000000001",
        updatedAt = 1_000,
    )

    @Test
    fun redactedReport_containsVersionsAndNoDiagnosticIdentifiers() {
        val report = SmsProblemReport.redacted("0.1.0 (1)", diagnostic)

        assertTrue(report.contains("0.1.0 (1)"))
        assertTrue(report.contains("Credo SMS / schema 1"))
        assertTrue(report.contains("UNRECOGNIZED"))
        assertTrue(report.contains("PARSE_FAILURE"))
        listOf(
            diagnostic.externalKey,
            diagnostic.cardLast4!!,
            diagnostic.counterparty!!,
            diagnostic.fromIban!!,
            "123.45",
            "456.78",
        ).forEach { sensitive -> assertFalse(report.contains(sensitive)) }
    }

    @Test
    fun originalBody_isOnlyAddedByExplicitRawVariant() {
        val raw = "Credo changed format for Private Person, card ****0001"
        val redacted = SmsProblemReport.redacted("0.1.0 (1)", diagnostic)

        assertFalse(redacted.contains(raw))
        assertTrue(SmsProblemReport.withOriginal(redacted, raw).endsWith(raw))
    }
}
