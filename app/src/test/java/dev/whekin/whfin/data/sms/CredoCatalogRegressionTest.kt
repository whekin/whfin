package dev.whekin.whfin.data.sms

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class CredoCatalogRegressionTest {
    private val payment = "Payment: 4.50 GEL Card N ****0001 EXAMPLE SHOP>Tbilisi GE Balance: -20.10 GEL 08/09/2026 20:30:00"
    private fun parsed(text: String) = requireNotNull(CredoSmsParser.parse(text))
    @Test fun oneLinePaymentPreservesMerchantDateAndNegativeBalance() {
        val sms = parsed(payment) as BankSmsMessage.CardPayment
        assertEquals("EXAMPLE SHOP", sms.merchantRaw)
        assertEquals(-2010L, sms.balanceMinor)
        assertEquals(LocalDateTime.of(2026, 9, 8, 20, 30), sms.timestamp)
    }
    @Test fun singleLineCancellationStillRetractsRatherThanCreatesCredit() {
        assertTrue(CredoSmsParser.classify("Canceled operation $payment") is BankSmsMessage.Classification.Canceled)
        assertEquals(BankSmsMessage.Classification.Unrecognized, CredoSmsParser.classify("Canceled operation Payment: malformed"))
    }
    @Test fun prefixTransliteratedLabelsAndNoBalancePreserveCardIdentity() {
        val sms = parsed("Credo Bank: Gadaxda 4.50 GEL Baratis N ****0001 EXAMPLE SHOP>Tbilisi GE 08/09/2026 20:30:00") as BankSmsMessage.CardPayment
        assertEquals("0001", sms.cardLast4)
        assertEquals("EXAMPLE SHOP", sms.merchantRaw)
        assertNull(sms.balanceMinor)
    }
    @Test fun oldIncomingPreservesSenderIntegerBalanceAndExplicitUsDate() {
        val sms = parsed("Incoming transfer 9/8/2026 8:30:00 PM; Amount 50 USD; Sender: Example Sender; Available Balance: 100 USD") as BankSmsMessage.IncomingTransfer
        assertEquals("Example Sender", sms.senderName)
        assertEquals(10000L, sms.balanceMinor)
        assertEquals(LocalDateTime.of(2026, 9, 8, 20, 30), sms.timestamp)
    }
    @Test fun lowercaseExchangeAmountWithUnlabelledDateIsNotLost() {
        val sms = parsed("Currency exchange: 9/8/2026 8:30:00 PM Exchanged amount: 50 USD Received amount: 130 GEL Balance: 200 GEL") as BankSmsMessage.CurrencyExchange
        assertEquals(5000L, sms.amountMinor)
        assertEquals(13000L, sms.receivedAmountMinor)
        assertEquals(LocalDateTime.of(2026, 9, 8, 20, 30), sms.timestamp)
    }
    @Test fun malformedAmountCannotFallThroughToTheBalance() {
        assertNull(CredoSmsParser.parse(payment.replace("4.50 GEL", "4.501 GEL")))
        assertNull(CredoSmsParser.parse(payment.replace("08/09/2026", "31/02/2026")))
    }
    @Test fun cashWithdrawalIsNotRelabeledAsSpending() {
        assertEquals(BankSmsMessage.Classification.Unrecognized, CredoSmsParser.classify(
            "Ganagdeba 50 GEL Baratis N ****0001 ATM EXAMPLE>Tbilisi GE Nashti: 100 GEL 08/09/2026 22:00:00"))
    }
    @Test fun transliteratedCodeIsExplicitlyIgnored() {
        val outcome = CredoSmsParser.classify("Ertjeradi kodi 0000 tanxa 50 GEL Baratis N ****0001") as BankSmsMessage.Classification.Ignored
        assertEquals(BankSmsMessage.IgnoreReason.OTP, outcome.reason)
    }
}
