package dev.whekin.whfin.data.tbc

import dev.whekin.whfin.data.LedgerCalendar
import dev.whekin.whfin.data.statement.StatementOperation
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Funding a deposit is the owner's own money moving, and the bank says so in Georgian. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TbcOwnTransferParsingTest {
    private val day = LocalDate.of(2026, 9, 12)
    private val account = TbcLedgerAccount("10", "GE00TB0000000000000001", "GEL", "Everyday")
    private val ownAccounts = "საკუთარ ანგარიშებს შორის გადარიცხვა"

    private fun page(title: String, subtitle: String, amount: String = "-1500.00") = JSONArray().put(JSONObject()
        .put("date", day.atStartOfDay(LedgerCalendar.zone).toInstant().toEpochMilli())
        .put("transactions", JSONArray().put(JSONObject().put("transactionId", 501).put("movementId", "m1")
            .put("entryType", "StandardMovement").put("transactionStatus", "Green").put("accountId", 10)
            .put("title", title).put("subTitle", subtitle).put("categoryCode", "TRANSFERS")
            .put("amount", amount).put("currency", "GEL")))).toString()

    private class Script(vararg responses: String) : TbcTransport {
        val remaining = ArrayDeque(responses.toList())
        override fun exchange(url: String, method: String, headers: Map<String, String>, body: String?) =
            TbcResponse(200, remaining.removeFirst())
    }

    private fun read(title: String, subtitle: String, amount: String = "-1500.00") = runBlocking {
        MobileTbcGateway(Script(page(title, subtitle, amount), "[]"))
            .history(account, day.minusDays(1), day.plusDays(1)).single().row
    }

    @Test fun depositFundingIsOwnMoneyMoving() {
        val row = read("დეპოზიტზე თანხის ჩარიცხვა (ხელშ. #000000000-000000000)", ownAccounts)
        assertEquals(StatementOperation.OWN_TRANSFER, row.operation)
        assertTrue(row.operation.isOwnMovement)
        assertEquals(-150_000L, row.amountMinor)
    }

    @Test fun theSameSubtitleOnTheReceivingSideIsAlsoOwnMovement() {
        val row = read("დეპოზიტზე თანხის ჩარიცხვა (ხელშ. #000000000-000000000)", ownAccounts, "1500.00")
        assertEquals(StatementOperation.OWN_TRANSFER, row.operation)
    }

    @Test fun aConversionKeepsItsOwnMeaningUnderTheGeorgianSubtitle() {
        assertEquals(StatementOperation.CURRENCY_EXCHANGE, read("კონვერტაცია", ownAccounts).operation)
    }

    @Test fun theEnglishWordingStillWorks() {
        assertEquals(StatementOperation.OWN_TRANSFER, read("Transfer between your accounts", "internal transfer").operation)
    }

    @Test fun aPaymentToSomebodyElseIsNotAnOwnTransfer() {
        val row = read("ალისა ანიტსკაია", "Transfers")
        assertNotEquals(StatementOperation.OWN_TRANSFER, row.operation)
        assertFalse(row.operation.isOwnMovement)
    }
}
