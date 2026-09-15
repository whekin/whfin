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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TbcDepositGatewayTest {
    private val iban = "GE00TB0000000000000009"
    private fun at(date: LocalDate) = date.atStartOfDay(LedgerCalendar.zone).toInstant().toEpochMilli()
    private fun deposits(vararg items: JSONObject, nextPage: Any? = JSONObject.NULL) = JSONObject()
        .put("items", JSONArray().apply { items.forEach(::put) }).put("nextPageId", nextPage).toString()
    private fun deposit(id: Long = 7, amount: Double = 300.0, topUp: Boolean = true) = JSONObject()
        .put("id", id).put("subType", 1).put("typeText", "Deposit").put("subTypeText", "My Safe")
        .put("friendlyName", "My Safe").put("currentAmount", amount).put("targetAmount", 1000.0)
        .put("currency", "GEL").put("externalAccountId", 555).put("accountNo", iban)
        .put("addAmountPossibility", topUp)
    private fun movement(date: LocalDate, balance: Double, deposited: Double = 0.0,
        interest: Double = 0.0, withdrawn: Double = 0.0) = JSONObject()
        .put("movementDate", at(date)).put("depositAmount", deposited).put("interestedAmount", interest)
        .put("withdrawnDepositAmount", withdrawn).put("balance", balance)
    private class Script(vararg responses: String) : TbcTransport {
        val remaining = ArrayDeque(responses.toList())
        val paths = mutableListOf<String>()
        override fun exchange(url: String, method: String, headers: Map<String, String>, body: String?): TbcResponse {
            paths += url
            return TbcResponse(200, remaining.removeFirst())
        }
    }

    @Test fun depositsAreReadFromTheirOwnListingWithTheProductsOwnFields() = runBlocking {
        val script = Script(deposits(deposit()))
        val read = MobileTbcGateway(script).deposits()
        assertEquals("https://rmbgw.tbconline.ge/deposits/api/v1/deposits", script.paths.single())
        val single = read.single()
        assertEquals("7", single.id)
        assertEquals(iban, single.accountNo)
        assertEquals("GEL", single.currency)
        assertEquals("My Safe", single.name)
        assertEquals(30000L, single.balanceMinor)
        assertEquals(true, single.acceptsTopUp)
        assertEquals("My Safe · GEL · •0009", single.label)
    }

    @Test fun aSecondPageOfDepositsIsRefusedInsteadOfSilentlyDroppingProducts() = runBlocking {
        val gateway = MobileTbcGateway(Script(deposits(deposit(), nextPage = "more")))
        val error = runCatching { gateway.deposits() }.exceptionOrNull()
        assertEquals("DEPOSIT_FORMAT", (error as TbcException).code)
    }

    @Test fun newestFirstMovementsBecomeAnOldestFirstChainWithTheBanksOwnOpening() = runBlocking {
        val day = LocalDate.of(2026, 9, 10)
        val json = JSONArray()
            .put(movement(day, 300.0, interest = 1.0))
            .put(movement(day.minusDays(1), 299.0, withdrawn = 50.0))
            .put(movement(day.minusDays(5), 349.0, deposited = 149.0))
        val script = Script(json.toString())
        val statement = MobileTbcGateway(script).depositStatement(
            TbcDepositAccount("7", iban, "GEL", "My Safe", 30000L, true))
        assertEquals("https://rmbgw.tbconline.ge/deposits/api/v1/statements/7", script.paths.single())
        assertEquals(20000L, statement.openingMinor)
        assertEquals(30000L, statement.closingMinor)
        assertEquals(listOf(14900L, -5000L, 100L), statement.rows.map { it.amountMinor })
        assertEquals(listOf(34900L, 29900L, 30000L), statement.rows.map { it.balanceAfterMinor })
        assertEquals(listOf(StatementOperation.SAVINGS_TOPUP, StatementOperation.SAVINGS_TOPUP,
            StatementOperation.INTEREST), statement.rows.map { it.operation })
        assertEquals(day.minusDays(5), statement.rows.first().postedDate)
    }

    @Test fun aMovementThatMovesNothingIsNotAFeedRowButStillHasToFitTheChain() = runBlocking {
        val day = LocalDate.of(2026, 9, 10)
        val gateway = MobileTbcGateway(Script(JSONArray()
            .put(movement(day.minusDays(2), 100.0, deposited = 100.0))
            .put(movement(day.minusDays(1), 100.0))
            .put(movement(day, 120.0, deposited = 20.0)).toString()))
        val statement = gateway.depositStatement(TbcDepositAccount("7", iban, "GEL", "My Safe", null, true))
        assertEquals(listOf(10000L, 2000L), statement.rows.map { it.amountMinor })
        assertEquals(0L, statement.openingMinor)
        assertEquals(12000L, statement.closingMinor)

        val broken = MobileTbcGateway(Script(JSONArray()
            .put(movement(day.minusDays(2), 100.0, deposited = 100.0))
            .put(movement(day.minusDays(1), 140.0))
            .put(movement(day, 120.0, deposited = 20.0)).toString()))
        val error = runCatching { broken.depositStatement(TbcDepositAccount("7", iban, "GEL", "My Safe", null, true)) }
        assertEquals("DEPOSIT_CHAIN", (error.exceptionOrNull() as TbcException).code)
    }

    @Test fun anAlreadySignedWithdrawalIsAcceptedOnlyWhenTheChainItselfProvesIt() = runBlocking {
        val day = LocalDate.of(2026, 9, 10)
        val signed = MobileTbcGateway(Script(JSONArray()
            .put(movement(day.minusDays(1), 100.0, deposited = 100.0))
            .put(movement(day, 60.0, withdrawn = -40.0)).toString()))
        val statement = signed.depositStatement(TbcDepositAccount("7", iban, "GEL", "My Safe", null, true))
        assertEquals(listOf(10000L, -4000L), statement.rows.map { it.amountMinor })

        // One row cannot prove a sign convention, so the documented one is not abandoned for it.
        val alone = MobileTbcGateway(Script(JSONArray().put(movement(day, 60.0, withdrawn = 40.0)).toString()))
        val single = alone.depositStatement(TbcDepositAccount("7", iban, "GEL", "My Safe", null, true))
        assertEquals(listOf(-4000L), single.rows.map { it.amountMinor })
        assertEquals(10000L, single.openingMinor)
    }

    @Test fun anEmptyDepositReadsAsNothingToImportRatherThanAnError() = runBlocking {
        val statement = MobileTbcGateway(Script("[]"))
            .depositStatement(TbcDepositAccount("7", iban, "GEL", "My Safe", 0L, true))
        assertTrue(statement.rows.isEmpty())
    }

    @Test fun anIdThatIsNotABankNumberNeverReachesTheUrl() = runBlocking {
        val script = Script("[]")
        val error = runCatching {
            MobileTbcGateway(script).depositStatement(TbcDepositAccount("7/../cards", iban, "GEL", "My Safe", null, true))
        }.exceptionOrNull()
        assertEquals("DEPOSIT_FORMAT", (error as TbcException).code)
        assertTrue(script.paths.isEmpty())
    }
}
