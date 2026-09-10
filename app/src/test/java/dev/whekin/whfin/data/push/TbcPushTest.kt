package dev.whekin.whfin.data.push

import dev.whekin.whfin.data.sms.*
import org.junit.Test
import org.junit.Assert.*

class TbcPushTest {
    private fun push(body: String) = BankPush(TbcPush.PACKAGE, "synthetic-key", 1000, title = "TBC", text = body)
    private val payment = "2.00 GEL\n(*0001)\nEXAMPLE BUS 10/09/26 14:07\nhttps://example.invalid/detail"
    @Test fun bothObservedShapesAreExpensesWithoutBookBalanceAnchors() {
        for (body in listOf(payment, "12.50 GEL\nMC TBC CARD (***0001)\nEXAMPLE CAFE 10/09/26 13:46\nBalance: 120.00 GEL")) {
            val parsed = (TbcPush.classify(push(body)) as BankSmsMessage.Classification.Parsed).sms as BankSmsMessage.CardPayment
            assertEquals("0001", parsed.cardLast4)
            assertNull(parsed.balanceMinor)
        }
    }
    @Test fun incomingAndUnknownMessagesNeverUseTheExpenseFallback() {
        for (body in listOf("Deposit 20 GEL (*0001) EXAMPLE 10/09/26 12:00", "Your payment was rejected", "New card features", "2.00 GEL (*0001) REFUND EXAMPLE CAFE 10/09/26 12:00"))
            assertFalse(TbcPush.classify(push(body)) is BankSmsMessage.Classification.Parsed)
    }
    @Test fun authenticationFieldsAreExcludedBeforeJournalCapture() {
        assertTrue(TbcPush.sensitive(push(payment).copy(subText = "Login code: 0000")))
        assertTrue(TbcPush.sensitive(push("000000")))
        assertTrue(TbcPush.sensitive(push("ერთჯერადი კოდი: 0000")))
        assertFalse(TbcPush.sensitive(push(payment)))
    }
    @Test fun packageSummaryAndTruncationCannotCreateExpenses() {
        for (entry in listOf(push(payment).copy(packageName = "example.other"), push(payment).copy(groupSummary = true), push(payment).copy(truncated = true)))
            assertFalse(TbcPush.classify(entry) is BankSmsMessage.Classification.Parsed)
    }
    @Test fun richerUpdatesKeepLedgerIdentityAndConflictingFieldsAreNotGuessed() {
        val first = push(payment)
        val richer = first.copy(bigText = payment + "\nBalance: 120 GEL", postedAt = 2000)
        assertEquals(TbcPush.ledgerKey(first, TbcPush.classify(first)), TbcPush.ledgerKey(richer, TbcPush.classify(richer)))
        assertFalse(TbcPush.classify(first.copy(bigText = payment.replace("2.00", "4.00"))) is BankSmsMessage.Classification.Parsed)
    }
}
