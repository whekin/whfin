package dev.whekin.whfin.data.tbc

import dev.whekin.whfin.data.LedgerCalendar
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TbcHistoryGatewayTest {
    private val day = LocalDate.of(2026, 9, 8)
    private val account = TbcLedgerAccount("10", "GE00TB0000000000000001", "GEL", "Everyday")
    private fun page(id: Long, movement: String, amount: String = "-2.00", date: LocalDate = day): String = JSONArray().put(JSONObject()
        .put("date", date.atStartOfDay(LedgerCalendar.zone).toInstant().toEpochMilli())
        .put("transactions", JSONArray().put(JSONObject().put("transactionId", id).put("movementId", movement)
            .put("entryType", "StandardMovement").put("transactionStatus", "Green").put("accountId", 10)
            .put("title", "POS - EXAMPLE BUS, 2.00 GEL, Sep  8 2026 10:22PM, MCC: 4131, 000000******0001")
            .put("subTitle", "").put("categoryCode", "TRANSPORT").put("amount", amount).put("currency", "GEL")))).toString()
    private class Script(vararg responses: String) : TbcTransport {
        val remaining = ArrayDeque(responses.toList())
        val bodies = mutableListOf<JSONObject>()
        override fun exchange(url: String, method: String, headers: Map<String, String>, body: String?): TbcResponse {
            if (body != null) bodies += JSONObject(body)
            return TbcResponse(200, remaining.removeFirst())
        }
    }
    @Test fun sameDayAcrossPagesContinuesUntilEmptyAndKeepsMovementIdsDistinct() = runBlocking {
        val script = Script(page(200, "d_first"), page(100, "d_second"), "[]")
        val rows = MobileTbcGateway(script).history(account, day.minusDays(1), day.plusDays(1))
        assertEquals(2, rows.size)
        assertEquals(setOf("d_first", "d_second"), rows.map { it.movementId }.toSet())
        assertEquals(200, script.bodies[1].getInt("lastSortColKey"))
        assertEquals("10", script.bodies.first().getJSONArray("coreAccountIds").getJSONObject(0).getString("id"))
        assertTrue(rows.all { it.row.amountMinor == -200L && it.row.balanceAfterMinor == null })
    }
    @Test fun readCountersDistinguishEmptyResponseFromRowsOutsideRequestedPeriod() = runBlocking {
        val emptyGateway = MobileTbcGateway(Script("[]"))
        assertTrue(emptyGateway.history(account, LocalDate.MIN, day).isEmpty())
        assertEquals(TbcHistoryReadStats(pages = 1, firstPageEmpty = true), emptyGateway.historyReadStats())
        val olderGateway = MobileTbcGateway(Script(page(100, "old", date = day.minusMonths(2))))
        assertTrue(olderGateway.history(account, day.minusMonths(1), day).isEmpty())
        assertEquals(TbcHistoryReadStats(pages = 1, parsed = 1), olderGateway.historyReadStats())
        olderGateway.clear()
        assertNull(olderGateway.historyReadStats())
    }

    @Test fun blockedRowsAreScopedByTheirOwnCurrencyAndIban() = runBlocking {
        val blocked = JSONObject().put("transactionId", 0).put("entryType", "BlockedTransaction")
            .put("blockedMovementDate", 1789045632000L).put("blockedMovementCardId", 70)
            .put("blockedMovementIban", account.iban).put("title", "EXAMPLE CAFE>Tbilisi GE")
            .put("subTitle", "Blocked amount").put("amount", -12.0).put("currency", "GEL").put("transactionStatus", "Green")
        val json = JSONArray().put(JSONObject().put("date", 1788998400000L).put("transactions", JSONArray().put(blocked)))
        val gel = account.copy(cardSuffixes = mapOf("70" to "0001"))
        val gateway = MobileTbcGateway(Script(json.toString(), "[]"))
        assertTrue(gateway.history(gel, LocalDate.MIN, day.plusDays(2)).isEmpty())
        assertEquals(1, gateway.pendingHolds().size)
        assertEquals(-1200L, gateway.pendingHolds().single().amountMinor)
        assertEquals("0001", gateway.pendingHolds().single().cardLast4)
        val usd = TbcHistoryParser.page(json, gel.copy(currency = "USD"))
        assertEquals(0, usd.holds.size)
        assertEquals(1, usd.blockedCount)
        val changed = TbcHistoryParser.page(JSONArray(json.toString().replace("-12", "-13")), gel)
        assertEquals(gateway.pendingHolds().single().key, changed.holds.single().key)
    }

    @Test fun repeatedCursorFailsInsteadOfPretendingHistoryIsComplete() = runBlocking {
        val script = Script(page(100, "d_first"), page(100, "d_first"))
        try { MobileTbcGateway(script).history(account, day.minusDays(1), day); fail() }
        catch (e: TbcException) { assertEquals("HISTORY_PAGE", e.code) }
    }
    @Test fun changedMovementDuringPagingAbortsTheSnapshot() = runBlocking {
        val script = Script(page(100, "d_first"), page(90, "d_first", "-3.00"))
        try { MobileTbcGateway(script).history(account, day.minusDays(1), day); fail() }
        catch (e: TbcException) { assertEquals("HISTORY_CHANGED", e.code) }
    }
    @Test fun fractionalMinorUnitsWrongCurrencyAndUnknownStatusAreRejected() {
        for (input in listOf(page(100, "d_first", "-2.001"), page(100, "d_first").replace("\"currency\":\"GEL\"", "\"currency\":\"USD\""),
            page(100, "d_first").replace("Green", "Unknown"))) {
            try { TbcHistoryParser.page(JSONArray(input), account); fail() } catch (_: Exception) { }
        }
    }
    @Test fun emptyGroupedPageIsNotAnEndOfHistorySignal() = runBlocking {
        val script = Script("""[{"date":1788825600000,"transactions":[]}]""")
        try { MobileTbcGateway(script).history(account, day.minusDays(1), day); fail() }
        catch (e: TbcException) { assertEquals("HISTORY_PAGE", e.code) }
    }
    @Test fun ledgerDiscoveryUsesProductAccountIdAndKeepsDashboardSavings() = runBlocking {
        val products = """[{"iban":"GE00TB0000000000000001","accounts":[{"id":10,"coreAccountId":99,"currency":"GEL","balance":20},{"id":11,"coreAccountId":98,"currency":"USD","balance":0}]}]"""
        val dashboard = """{"accountsAndDebitCards":[{"type":"Card","id":500,"iban":"GE00TB0000000000000001","currency":"GEL"},{"type":"Saving","id":20,"iban":"GE00TB0000000000000002","currency":"GEL","amount":50,"name":"Reserve"}]}"""
        val result = MobileTbcGateway(Script(products, dashboard)).ledgerAccounts()
        assertEquals(listOf("10", "11", "20"), result.map { it.id })
        assertEquals(listOf("GEL", "USD", "GEL"), result.map { it.currency })
    }
    @Test fun firstReadPagesPastTheLastYearUntilTheBankReturnsNoMoreRows() = runBlocking {
        val old = day.minusYears(4)
        val script = Script(page(300, "recent"), page(200, "old", date = old), "[]")
        val rows = MobileTbcGateway(script).history(account, LocalDate.MIN, day)
        assertEquals(listOf(day, old), rows.map { it.row.postedDate })
        assertEquals(3, script.bodies.size)
        assertFalse(script.bodies.first().has("startDate"))
    }

}
