package dev.whekin.whfin.data.importer

import dev.whekin.whfin.data.db.TransactionEntity
import dev.whekin.whfin.data.db.TxSource
import dev.whekin.whfin.data.db.TxStatus
import dev.whekin.whfin.data.statement.StatementOperation
import dev.whekin.whfin.data.statement.StatementRow
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Whether a statement line and a draft describe the same purchase. */
class StatementReconcilerTest {

    private fun row(merchant: String? = "NIKORA", amountMinor: Long = -1_250) = StatementRow(
        postedDate = LocalDate.of(2026, 3, 14),
        operation = StatementOperation.CARD_PAYMENT,
        operationRaw = "card",
        amountMinor = amountMinor,
        balanceAfterMinor = null,
        description = "payment",
        beneficiaryName = null,
        beneficiaryAccount = null,
        merchantRaw = merchant,
    )

    private fun draft(id: Long, counterparty: String?, amountMinor: Long) = TransactionEntity(
        id = id,
        accountId = 1,
        amountMinor = amountMinor,
        currency = "GEL",
        occurredAt = 1_000,
        rawCounterparty = counterparty,
        status = TxStatus.PENDING,
        source = TxSource.SMS,
    )

    @Test
    fun aDraftForTheSameMerchant_isTheSamePurchase() {
        // The same shop reaches WHFIN differently from an SMS and from a statement: another case,
        // padded spacing, and a location suffix the message appends.
        val match = StatementReconciler.match(
            row(),
            listOf(draft(7, " nikora >Didgori", -1_250)),
        )

        assertEquals(7L, match?.id)
    }

    @Test
    fun theSameCounterpartyInTwoAlphabets_isOneOperation() {
        // Credo files an incoming transfer under the Georgian name and announces it romanized. As
        // strings the two share no character, so before this the statement line was inserted beside
        // the message that already recorded it and the money was counted twice.
        val match = StatementReconciler.match(
            row(
                merchant = "\u10e8\u10de\u10e1 \u10e3\u10dc\u10dd\u10e2\u10e0\u10dd\u10dc",
                amountMinor = 694_800,
            ).copy(operation = StatementOperation.TRANSFER_IN),
            listOf(draft(11, "SHPS UNOTRON", 694_800)),
        )

        assertEquals(11L, match?.id)
    }

    @Test
    fun twoDifferentGeorgianCounterparties_stayApart() {
        assertNull(
            StatementReconciler.match(
                row(merchant = "\u10dc\u10d8\u10d9\u10dd\u10e0\u10d0", amountMinor = -1_250)
                    .copy(operation = StatementOperation.TRANSFER_IN),
                listOf(draft(12, "SHPS UNOTRON", -1_250)),
            ),
        )
    }

    @Test
    fun theAmountOnlyBreaksTies() {
        // A card paid abroad is charged in another currency than the SMS announced, so the amounts
        // routinely differ; a single merchant match still identifies the purchase.
        val match = StatementReconciler.match(
            row(amountMinor = -6_346),
            listOf(draft(9, "NIKORA", -2_400)),
        )

        assertEquals(9L, match?.id)
    }

    @Test
    fun aStatementMerchantPrefixMatchesTheLongerSmsProcessorName() {
        val match = StatementReconciler.match(
            row(merchant = "ANTHROPIC", amountMinor = -6_346),
            listOf(draft(10, "ANTHROPIC* CLAUDE.AI", -2_400)),
        )

        assertEquals(10L, match?.id)
    }

    @Test
    fun betweenTwoDraftsOfTheSameMerchant_theExactAmountWins() {
        val match = StatementReconciler.match(
            row(amountMinor = -1_250),
            listOf(draft(1, "NIKORA", -900), draft(2, "NIKORA", -1_250)),
        )

        assertEquals(2L, match?.id)
    }

    @Test
    fun twoIndistinguishableDrafts_areLeftAlone() {
        // Picking one at random would silently overwrite the wrong draft and lose a payment.
        val match = StatementReconciler.match(
            row(amountMinor = -1_250),
            listOf(draft(1, "NIKORA", -900), draft(2, "NIKORA", -400)),
        )

        assertNull(match)
    }

    @Test
    fun aRowWithoutAMerchant_matchesNothing() {
        assertNull(StatementReconciler.match(row(merchant = null), listOf(draft(1, "NIKORA", -1_250))))
        assertNull(StatementReconciler.match(row(merchant = "   "), listOf(draft(1, "NIKORA", -1_250))))
    }

    @Test
    fun ownMovementMatchesOneExactSmsLegWithoutMerchant() {
        val smsLeg = draft(12, counterparty = null, amountMinor = -5_000).copy(
            status = TxStatus.CONFIRMED,
            isTransfer = true,
            transferGroupId = 4,
        )

        val match = StatementReconciler.match(
            row(merchant = null, amountMinor = -5_000).copy(
                operation = StatementOperation.OWN_TRANSFER,
            ),
            listOf(smsLeg),
        )

        assertEquals(12L, match?.id)
    }

    @Test
    fun ambiguousExactOwnMovementLegsAreNotGuessed() {
        val first = draft(12, counterparty = null, amountMinor = -5_000).copy(
            status = TxStatus.CONFIRMED,
            isTransfer = true,
        )
        val second = first.copy(id = 13)

        assertNull(
            StatementReconciler.match(
                row(merchant = null, amountMinor = -5_000).copy(
                    operation = StatementOperation.OWN_TRANSFER,
                ),
                listOf(first, second),
            ),
        )
    }

    @Test
    fun anotherMerchantIsAnotherPurchase() {
        assertNull(StatementReconciler.match(row(), listOf(draft(1, "SPAR", -1_250))))
    }
}
