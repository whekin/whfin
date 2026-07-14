package dev.whekin.whfin.data.sms

import dev.whekin.whfin.data.sms.CredoSmsParser.CardPayment
import dev.whekin.whfin.data.sms.CredoSmsParser.CurrencyExchange
import dev.whekin.whfin.data.sms.CredoSmsParser.DepositTopUp
import dev.whekin.whfin.data.sms.CredoSmsParser.IncomingTransfer
import dev.whekin.whfin.data.sms.CredoSmsParser.OutgoingTransfer
import dev.whekin.whfin.data.sms.CredoSmsParser.OwnTransfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Golden tests for supported Credo SMS shapes. Every value is fictional;
 * structure and whitespace stay representative of the bank format.
 */
class CredoSmsParserTest {

    @Test
    fun `card payment in GEL`() {
        val sms = """
            Payment: 12.34 GEL
            Card N ****0001
            EXAMPLE MARKET>Tbilisi                 GE
            Balance:  567.89 GEL
            03/04/2026 20:48:05
            Details: https://mycredo.page.link/Pdk
        """.trimIndent()

        val result = CredoSmsParser.parse(sms) as CardPayment
        assertEquals(1234L, result.amountMinor)
        assertEquals("GEL", result.currency)
        assertEquals("0001", result.cardLast4)
        assertEquals("EXAMPLE MARKET", result.merchantRaw)
        assertEquals("Tbilisi                 GE", result.locationRaw)
        assertEquals(56789L, result.balanceMinor)
        assertEquals("GEL", result.balanceCurrency)
        // Payment: dd/MM/yyyy, 24-hour format — 3 April, not 4 March.
        assertEquals(LocalDateTime.of(2026, 4, 3, 20, 48, 5), result.timestamp)
    }

    @Test
    fun `FX card payment - amount in purchase currency, balance in GEL`() {
        val sms = """
            Payment: 19.99 USD
            Card N ****0002
            EXAMPLE SOFTWARE>SEATTLE              US
            Balance:  500.00 GEL
            04/04/2026 21:37:30
            Details: https://mycredo.page.link/Pdk
        """.trimIndent()

        val result = CredoSmsParser.parse(sms) as CardPayment
        assertEquals(1999L, result.amountMinor)
        assertEquals("USD", result.currency)
        assertEquals("0002", result.cardLast4)
        assertEquals("EXAMPLE SOFTWARE", result.merchantRaw)
        assertEquals(50000L, result.balanceMinor)
        assertEquals("GEL", result.balanceCurrency)
    }

    @Test
    fun `card payment with padded merchant and split-the-bill tail`() {
        val sms = """
            Payment: 15.00 GEL
            Card N ****0001
            EXAMPLE RIDE           >Tbilisi       GE
            Balance:  552.89 GEL
            05/04/2026 22:42:15
            Details: https://mycredo.page.link/Pdk
            want to split the bill? : https://moneypot.mycredo.ge/open-app?smsId=9d4f05
        """.trimIndent()

        val result = CredoSmsParser.parse(sms) as CardPayment
        assertEquals("EXAMPLE RIDE", result.merchantRaw)
        assertEquals(1500L, result.amountMinor)
    }

    @Test
    fun `outgoing transfer uses month-first AMPM date`() {
        val sms = """
            Outgoing transfer
            Amount: 100.00 GEL;
            Balance: 1234.56 GEL
            Date:4/5/2026 10:43:19 PM
            Check details in MyCredo: https://mycredo.page.link/Pdkp
        """.trimIndent()

        val result = CredoSmsParser.parse(sms) as OutgoingTransfer
        assertEquals(10000L, result.amountMinor)
        assertEquals(123456L, result.balanceMinor)
        // M/d/yyyy: 5 April, not 4 May.
        assertEquals(LocalDateTime.of(2026, 4, 5, 22, 43, 19), result.timestamp)
    }

    @Test
    fun `deposit top-up parses available deposit balance`() {
        val sms = """
            Deposit top-up
            Amount: 4500.00 GEL
            Available Balance on Deposit 4500.00 GEL.
            Date: 7/12/2026 5:18:36 AM;
            Check details in MyCredo: https://mycredo.page.link/Pdkp
        """.trimIndent()

        val result = CredoSmsParser.parse(sms) as DepositTopUp
        assertEquals(450000L, result.amountMinor)
        assertEquals("GEL", result.currency)
        assertEquals(450000L, result.balanceMinor)
        assertEquals("GEL", result.balanceCurrency)
        assertEquals(LocalDateTime.of(2026, 7, 12, 5, 18, 36), result.timestamp)
    }

    @Test
    fun `incoming transfer with sender`() {
        val sms = """
            Incoming transfer
            Amount: 2500.00 GEL;
            From sender: EXAMPLE EMPLOYER;
            Balance: 3000.00 GEL;
            Date:4/6/2026 2:16:07 PM
            Check details in MyCredo:https://mycredo.page.link/Pdkp
        """.trimIndent()

        val result = CredoSmsParser.parse(sms) as IncomingTransfer
        assertEquals(250000L, result.amountMinor)
        assertEquals("EXAMPLE EMPLOYER", result.senderName)
        assertEquals(LocalDateTime.of(2026, 4, 6, 14, 16, 7), result.timestamp)
    }

    @Test
    fun `transfer between own accounts extracts both IBANs`() {
        val sms = """
            Transfer between accounts
            Amount: 200.00 GEL;
            From: GE00CD0000000000000001
            To: GE00CD0000000000000002
            Balance: 1334.56 GEL
            Date: 4/5/2026 10:43:03 PM
            Check details in MyCredo: https://mycredo.page.link/Pdkp
        """.trimIndent()

        val result = CredoSmsParser.parse(sms) as OwnTransfer
        assertEquals(20000L, result.amountMinor)
        assertEquals("GE00CD0000000000000001", result.fromIban)
        assertEquals("GE00CD0000000000000002", result.toIban)
    }

    @Test
    fun `currency exchange with trailing semicolon after date`() {
        val sms = """
            Currency exchange
            Amount: 50.00 GEL
            Received amount: 18.00 USD
            Balance: 18.00 USD
            Date:4/5/2026 9:36:59 PM;
            Check details in MyCredo: https://mycredo.page.link/Pdkp
        """.trimIndent()

        val result = CredoSmsParser.parse(sms) as CurrencyExchange
        assertEquals(5000L, result.amountMinor)
        assertEquals("GEL", result.currency)
        assertEquals(1800L, result.receivedAmountMinor)
        assertEquals("USD", result.receivedCurrency)
        assertEquals(LocalDateTime.of(2026, 4, 5, 21, 36, 59), result.timestamp)
    }

    @Test
    fun `rejected payment is ignored`() {
        val sms = """
            Rejected payment
            Insufficient amount
            Amount: 42.00 GEL
            Card N ****0002
            EXAMPLE SHOP>Tbilisi                  GE
            07/04/2026 17:18:57
        """.trimIndent()

        assertNull(CredoSmsParser.parse(sms))
    }

    @Test
    fun `OTP confirmation code is ignored`() {
        assertNull(
            CredoSmsParser.parse(
                "CODE: 123456 confirms card ***0002 payment of 19.99 EUR at EXAMPLE SHOP",
            ),
        )
    }

    @Test
    fun `unrelated sms is ignored`() {
        assertNull(CredoSmsParser.parse("Your delivery code is 1234"))
        val result = CredoSmsParser.classify("Your delivery code is 1234")
        assertTrue(result is CredoSmsParser.Classification.Ignored)
        assertFalse((result as CredoSmsParser.Classification.Ignored).credoCandidate)
    }

    @Test
    fun `malformed transaction message is visible as unrecognized`() {
        val result = CredoSmsParser.classify("Payment: new Credo format")
        assertEquals(CredoSmsParser.Classification.Unrecognized, result)
        assertTrue(CredoSmsParser.isCredoCandidate("Payment: new Credo format"))
    }

    @Test
    fun `OTP remains a Credo candidate but has an explicit ignored reason`() {
        val result = CredoSmsParser.classify(
            "CODE: 123456 confirms card ***0002 payment of 19.99 EUR at EXAMPLE SHOP",
        ) as CredoSmsParser.Classification.Ignored
        assertEquals(CredoSmsParser.IgnoreReason.OTP, result.reason)
        assertTrue(result.credoCandidate)
    }

    @Test
    fun `thousands separator in amount`() {
        val sms = """
            Incoming transfer
            Amount: 1,083.20 GEL;
            From sender: EXAMPLE EMPLOYER;
            Balance: 1,793.77 GEL;
            Date:7/15/2025 2:16:07 PM
            Check details in MyCredo:https://mycredo.page.link/Pdkp
        """.trimIndent()

        val result = CredoSmsParser.parse(sms) as IncomingTransfer
        assertEquals(108320L, result.amountMinor)
        assertEquals(179377L, result.balanceMinor)
    }
}
