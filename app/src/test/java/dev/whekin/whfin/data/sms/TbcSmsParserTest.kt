package dev.whekin.whfin.data.sms

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class TbcSmsParserTest {
    private fun parsed(text: String) = (TbcSmsParser.classify(text) as BankSmsMessage.Classification.Parsed).sms
    @Test fun paymentWithoutBalanceAndLegacyCardNameKeepMerchantCardAndDay() {
        for (text in listOf(
            "2.00 GEL (*0001) EXAMPLE BUS 08/09/26 22:22 https://example.invalid/details",
            "2.00 GEL VISA GOLD (*0001) EXAMPLE BUS 08/09/2026 22:22",
            "Card transaction: 2.00 GEL MC STANDARD (*0001) EXAMPLE BUS 08/09/2026 22:22 Balance: 100 GEL",
        )) {
            val sms = parsed(text) as BankSmsMessage.CardPayment
            assertEquals(200L, sms.amountMinor)
            assertEquals("0001", sms.cardLast4)
            assertEquals("EXAMPLE BUS", sms.merchantRaw)
            assertEquals(LocalDateTime.of(2026, 9, 8, 22, 22), sms.timestamp)
            assertNull(sms.balanceMinor)
        }
    }
    @Test fun availableBalanceAndBonusNeverBecomeLedgerMoney() {
        val sms = parsed("4.50USD (*0001) EXAMPLE SHOP Nashti: -15.20GEL You've received: 0.20GEL In rewards 08/09/26 20:00") as BankSmsMessage.CardPayment
        assertEquals(450L, sms.amountMinor)
        assertEquals("USD", sms.currency)
        assertEquals("GEL", sms.balanceCurrency)
        assertNull(sms.balanceMinor)
        assertEquals("EXAMPLE SHOP", sms.merchantRaw)
    }
    @Test fun reversalIsCreditNotCancellationOfAnUnprovenOriginal() {
        val sms = parsed("Reversal: 4.50 GEL (*0001) EXAMPLE SHOP Balance: 80.00GEL 08/09/26 20:00") as BankSmsMessage.IncomingTransfer
        assertEquals(450L, sms.amountMinor)
        assertEquals("0001", sms.cardLast4)
        assertNull(sms.balanceMinor)
    }
    @Test fun cashDepositTransferAndUtilityAreDistinctOperations() {
        assertTrue(parsed("Cash Deposit: 50.00 USD TBC CARD 08/09/2026") is BankSmsMessage.CashDeposit)
        assertTrue(parsed("Money Transfer 50.00 GEL Account: MC STANDARD 08/09/2026") is BankSmsMessage.OutgoingTransfer)
        val bill = parsed("Payment 250.00GEL Example Energy ID: example 08/09/2026") as BankSmsMessage.BillPayment
        assertEquals(25000L, bill.amountMinor)
        assertEquals("Example Energy", bill.serviceRaw)
    }
    @Test fun mobileBalanceRechargeFromBankSmsIsABill() {
        val sms = parsed("""
            Mobile Balance Recharge
            40.00GEL
            Silknet account
            ID:000000000
            12/09/2026
        """.trimIndent()) as BankSmsMessage.BillPayment
        assertEquals(4000L, sms.amountMinor)
        assertEquals("GEL", sms.currency)
        assertEquals("Silknet account", sms.serviceRaw)
        assertEquals(LocalDateTime.of(2026, 9, 12, 0, 0), sms.timestamp)
    }
    @Test fun groupedThousandsRemainOneAmount() {
        assertEquals(123450L, parsed("1 234.50 GEL (*0001) EXAMPLE SHOP 08/09/26 20:00").amountMinor)
    }
    @Test fun otpOffersAndOtherCountryMessagesAreNotTransactions() {
        for (body in listOf("TBC SMS Code: 0000", "Your PIN code is 0000", "Earn 10 GEL with our offer", "100 UZS было переведено со вклада на счёт *0001")) {
            assertTrue(TbcSmsParser.classify(body) is BankSmsMessage.Classification.Ignored)
        }
    }
    @Test fun missingAccountEvidenceInvalidDatesAndAmountsDoNotInventTransactions() {
        for (body in listOf("Transfer between Accounts 50 GEL From: MC GOLD To: MC STANDARD 08/09/26",
            "Conversion: 50 USD 130 GEL Rate: 2.60 08/09/26",
            "2.00 GEL (*0001) EXAMPLE SHOP 31/02/26 20:00",
            "2.001 GEL (*0001) EXAMPLE SHOP 08/09/26 20:00")) {
            assertEquals(BankSmsMessage.Classification.Unrecognized, TbcSmsParser.classify(body))
        }
    }
    @Test fun bankSenderAndKeyKeepTheChannelsSeparate() {
        assertEquals(BankSmsBank.TBC, BankSmsBank.fromSender("TBC SMS"))
        assertEquals(BankSmsBank.CREDO, BankSmsBank.fromSender("Credo Bank"))
        assertNull(BankSmsBank.fromSender("Example Friend"))
        assertEquals(smsExternalKey("example"), BankSmsBank.CREDO.key("example"))
        assertNotEquals(BankSmsBank.CREDO.key("example"), BankSmsBank.TBC.key("example"))
    }
}
