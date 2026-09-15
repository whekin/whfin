package dev.whekin.whfin.data.credo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CredoHistoryGatewayTest {
    private val account = CredoRemoteAccount("GE00CD0000000000000001", "GEL", 1, "Current", "ACCOUNT")
    private val session = CredoSession("synthetic", null)
    private val today = LocalDate.of(2026, 9, 9)
    private fun row(id: String, blocked: Boolean = false) = JSONObject().put("stmtEntryId", id).put("transactionId", id)
        .put("credit", JSONObject.NULL).put("debit", "4.25").put("currency", "GEL")
        .put("operationDateTime", "2026-09-09 10:00:00").put("isCardBlock", blocked)
        .put("operationType", "Card transaction").put("description", "გადახდა - EXAMPLE CAFE 4.25 GEL 09.09.2026")
    private fun page(total: Int, pages: Int, vararg rows: JSONObject) = JSONObject().put("data", JSONObject().put("transactionPagingList",
        JSONObject().put("pageCount", pages).put("totalItemCount", total).put("itemList", JSONArray(rows.toList())))).toString()
    private fun detail(row: JSONObject) = JSONObject().put("data", JSONObject().put("customer", JSONObject().put("transactions",
        JSONArray().put(JSONObject(row.toString()).put("accountNumber", account.accountNumber)
            .put("contragentAccount", "GE00TB0000000000000001"))))).toString()
    private fun gateway(responses: List<String>, requests: MutableList<JSONObject> = mutableListOf()): CredoGateway {
        val detailResponses = responses.mapNotNull { raw ->
            val item = JSONObject(raw).optJSONObject("data")?.optJSONObject("customer")
                ?.optJSONArray("transactions")?.optJSONObject(0)
            item?.optString("stmtEntryId")?.let { it to raw }
        }.toMap()
        val queue = java.util.ArrayDeque(responses.filterNot { it in detailResponses.values })
        return MyCredoGateway(ApplicationProvider.getApplicationContext<Context>(), object : CredoTransport {
            override fun post(url: String, headers: Map<String, String>, body: String): String {
                val request = JSONObject(body)
                synchronized(requests) { requests += request }
                val id = request.getJSONObject("variables").optString("stmtEntryId")
                return detailResponses[id] ?: synchronized(queue) { queue.removeFirst() }
            }
        }, "synthetic-device")
    }
    @Test fun detailRequestsOverlapWithABoundAndKeepBankOrder() = runBlocking {
        val items = (0..5).map { row("item-$it") }
        val gate = java.util.concurrent.CountDownLatch(3)
        val active = java.util.concurrent.atomic.AtomicInteger()
        val peak = java.util.concurrent.atomic.AtomicInteger()
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val transport = object : CredoTransport {
            override fun post(url: String, headers: Map<String, String>, body: String): String {
                val variables = JSONObject(body).getJSONObject("variables")
                if (!variables.has("stmtEntryId")) return page(6, 1, *items.toTypedArray())
                val index = variables.getString("stmtEntryId").substringAfterLast('-').toInt()
                val running = active.incrementAndGet()
                peak.updateAndGet { maxOf(it, running) }
                calls.incrementAndGet()
                try {
                    gate.countDown()
                    check(gate.await(2, java.util.concurrent.TimeUnit.SECONDS)) { "Details are still fetched serially" }
                    Thread.sleep((3 - index % 3) * 10L)
                    return detail(items[index])
                } finally { active.decrementAndGet() }
            }
        }
        val gateway = MyCredoGateway(ApplicationProvider.getApplicationContext<Context>(), transport, "synthetic-device")
        val result = gateway.history(session, account, today, today)
        assertEquals(3, peak.get())
        assertEquals(6, calls.get())
        assertEquals((0..5).map { CredoRowIdentity.mobileId("item-$it") }, result.map { it.bankTransactionId })
    }
    @Test fun failedDetailStopsBeforeAnotherBatchAndReturnsNoPartialHistory() = runBlocking {
        val items = (0..5).map { row("item-$it") }
        val requested = java.util.concurrent.ConcurrentLinkedQueue<Int>()
        val gateway = MyCredoGateway(ApplicationProvider.getApplicationContext<Context>(), object : CredoTransport {
            override fun post(url: String, headers: Map<String, String>, body: String): String {
                val variables = JSONObject(body).getJSONObject("variables")
                if (!variables.has("stmtEntryId")) return page(6, 1, *items.toTypedArray())
                val index = variables.getString("stmtEntryId").substringAfterLast('-').toInt()
                requested += index
                if (index == 1) throw CredoApiException("NETWORK_ERROR")
                return detail(items[index])
            }
        }, "synthetic-device")
        assertTrue(runCatching { gateway.history(session, account, today, today) }.exceptionOrNull() is CredoApiException)
        assertTrue(requested.all { it < 3 })
        assertTrue(requested.contains(1))
    }
    @Test fun timeoutDiagnosticsContainOnlyDurationAndRequestKind() = runBlocking {
        org.robolectric.shadows.ShadowLog.clear()
        val gateway = MyCredoGateway(ApplicationProvider.getApplicationContext<Context>(), object : CredoTransport {
            override fun post(url: String, headers: Map<String, String>, body: String): String {
                throw CredoApiException("NETWORK_ERROR", java.net.SocketTimeoutException("private-example-body"))
            }
        }, "synthetic-device")
        assertTrue(runCatching { gateway.history(session, account, today, today) }.isFailure)
        val messages = org.robolectric.shadows.ShadowLog.getLogsForTag(CredoSyncDiagnostics.TAG).map { it.msg }
        assertTrue(messages.any { it.startsWith("REQUEST_TIMEOUT") && it.endsWith("secondary=1") })
        assertTrue(messages.all { it.matches(Regex("[A-Z_]+ count=[0-9]+ secondary=[0-9]+")) })
    }
    @Test fun paginationReadsAllPagesExcludesHoldsAndValidatesDetails() = runBlocking {
        val a = row("a"); val b = row("b"); val calls = mutableListOf<JSONObject>()
        val rows = gateway(listOf(page(3, 2, a, row("hold", true)), page(3, 2, b), detail(a), detail(b)), calls)
            .history(session, account, today.minusDays(7), today)
        assertEquals(2, rows.size); assertEquals(-425L, rows.first().amountMinor)
        assertEquals("EXAMPLE CAFE", rows.first().merchantRaw)
        assertEquals("GE00TB0000000000000001", rows.first().beneficiaryAccount)
        assertEquals(2, calls[1].getJSONObject("variables").getJSONObject("data").getInt("pageNumber"))
        assertEquals(1, calls[0].getJSONObject("variables").getJSONObject("data").getJSONArray("accountIdList").getInt(0))
    }
    @Test fun changedPageTotalAndRepeatedIdentityAbort() = runBlocking {
        for (responses in listOf(listOf(page(2, 2, row("a")), page(3, 2, row("b"))),
            listOf(page(2, 2, row("a")), page(2, 2, row("a"))))) {
            val error = runCatching { gateway(responses).history(session, account, today, today) }.exceptionOrNull()
            assertEquals("HISTORY_CHANGED", (error as CredoApiException).code)
        }
    }
    @Test fun mismatchedDetailAccountOrAmountAndFractionalMinorFailClosed() = runBlocking {
        val a = row("a")
        for (detail in listOf(detail(a).replace(account.accountNumber, "GE00CD0000000000000002"),
            detail(a).replace("4.25", "4.26"), detail(a).replace("4.25", "4.251"))) {
            val error = runCatching { gateway(listOf(page(1, 1, a), detail)).history(session, account, today, today) }.exceptionOrNull()
            assertEquals("HISTORY_FORMAT", (error as CredoApiException).code)
        }
    }
    @Test fun conversionPresentationPairRequestsBankStatementInsteadOfInventingLedgerRows() = runBlocking {
        org.robolectric.shadows.ShadowLog.clear()
        val a = row("fx").put("transactionType", "CurrencyExchange")
        val error = runCatching { gateway(listOf(page(2, 1, a, row("payment")))).history(session, account, today, today) }.exceptionOrNull()
        assertEquals("HISTORY_REQUIRES_STATEMENT", (error as CredoApiException).code)
        val messages = org.robolectric.shadows.ShadowLog.getLogsForTag(CredoSyncDiagnostics.TAG).map { it.msg }
        assertTrue(messages.any { it.startsWith("API_PAGE") })
        assertTrue(messages.any { it.startsWith("XLSX_CONVERSION_GROUP") })
        assertTrue(messages.all { it.matches(Regex("[A-Z_]+ count=[0-9]+ secondary=[0-9]+")) })
    }
    @Test fun ordinaryExchangeAtSharedTimestampDoesNotRequireAFile() = runBlocking {
        val a = row("fx").put("transactionType", "CurrencyExchange").put("operationType", "Currency exchange").put("description", "Exchange")
        val b = row("transfer").put("operationType", "Transfer between own accounts").put("description", "Own transfer")
        val result = gateway(listOf(page(2, 1, a, b), detail(a), detail(b))).history(session, account, today, today)
        assertEquals(2, result.size)
    }

    @Test fun extentRequestsTheWholeHistoricalRangeAndIgnoresOlderPendingRows() = runBlocking {
        val old = row("old").put("operationDateTime", "2022-05-10 10:00:00")
        val hold = row("hold", true).put("operationDateTime", "2020-01-01 10:00:00")
        val calls = mutableListOf<JSONObject>()
        val extent = gateway(listOf(page(3, 2, row("recent")), page(3, 2, old, hold)), calls)
            .historyExtent(session, account)!!
        assertEquals(LocalDate.of(2022, 5, 10), extent.oldestDate)
        val from = java.time.Instant.parse(calls.first().getJSONObject("variables").getJSONObject("data").getString("dateFrom"))
        assertEquals(LocalDate.of(1970, 1, 1), from.atZone(dev.whekin.whfin.data.LedgerCalendar.zone).toLocalDate())
        assertEquals(2, calls.size)
    }

}
