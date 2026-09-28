package dev.whekin.whfin.ui.settings

import dev.whekin.whfin.data.db.SmsDiagnosticEntity
import dev.whekin.whfin.data.db.SmsDiagnosticKind
import dev.whekin.whfin.data.db.SmsDiagnosticOutcome
import dev.whekin.whfin.data.sms.SmsImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One card route settles every queued payment of that card, so the screen must ask once per card.
 */
class SmsBatchGroupingTest {

    @Test fun unreadableMessageIsDiagnosticNotAnOwnerDecision() {
        val unreadable = waiting(30, null, SmsDiagnosticOutcome.UNRECOGNIZED)
        assertFalse(unreadable.needsUserAction())
        assertFalse(waiting(31, null, SmsDiagnosticOutcome.ERROR).needsUserAction())
    }

    @Test fun twentyUnreadableMessagesDoNotBecomeTwentyOwnerDecisions() {
        val summary = summarizeSmsPreview(List(20) { SmsImportResult(SmsDiagnosticOutcome.UNRECOGNIZED) })
        assertEquals(0, summary.importable)
        assertEquals(0, summary.needsAttention)
        assertEquals(20, summary.unreadable)
    }

    private fun waiting(
        id: Long,
        card: String?,
        outcome: SmsDiagnosticOutcome = SmsDiagnosticOutcome.NEEDS_CARD_MAPPING,
        receivedAt: Long = id,
    ) = SmsDiagnosticEntity(
        id = id,
        externalKey = "sms|$id",
        kind = SmsDiagnosticKind.CARD_PAYMENT,
        outcome = outcome,
        receivedAt = receivedAt,
        cardLast4 = card,
        amountMinor = -1_000,
        currency = "GEL",
        updatedAt = receivedAt,
    )

    @Test
    fun cardsWithSeveralWaitingPaymentsBecomeOneDecisionEach() {
        val batches = attentionBatches(
            listOf(
                waiting(1, "0001"), waiting(2, "0001"), waiting(3, "0001"),
                waiting(4, "0002"), waiting(5, "0002"),
            ),
        )

        assertEquals(listOf("0001", "0002"), batches.map { it.cardLast4 })
        assertEquals(listOf(3, 2), batches.map { it.items.size })
    }

    @Test
    fun aLoneMessageIsNotABatchBecauseItsOwnRowIsRightBelow() {
        val batches = attentionBatches(listOf(waiting(1, "0001"), waiting(2, "0002"), waiting(3, "0002")))

        assertEquals(listOf("0002"), batches.map { it.cardLast4 })
    }

    @Test
    fun onlyMissingCardRoutesAreBatched() {
        // Choosing between two ledgers is a decision per message; it settles nothing else.
        val batches = attentionBatches(
            listOf(
                waiting(1, "0001", SmsDiagnosticOutcome.CHOOSE_ACCOUNT),
                waiting(2, "0001", SmsDiagnosticOutcome.CHOOSE_ACCOUNT),
                waiting(3, null),
            ),
        )

        assertTrue(batches.isEmpty())
    }
}
