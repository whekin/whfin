package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.credo.CredoApiException
import dev.whekin.whfin.data.credo.CredoCredentials
import dev.whekin.whfin.data.credo.CredoGateway
import dev.whekin.whfin.data.credo.CredoLoginChallenge
import dev.whekin.whfin.data.credo.CredoRemoteAccount
import dev.whekin.whfin.data.credo.CredoSecretStore
import dev.whekin.whfin.data.credo.CredoSession
import dev.whekin.whfin.data.statement.SyntheticCredoWorkbook
import dev.whekin.whfin.data.statement.SyntheticCredoWorkbook.Row
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Walking an account back through the years the bank still holds.
 *
 * [dev.whekin.whfin.data.credo.CredoHistoryScan] owns when to stop; this covers that the walk
 * actually moves, that its windows abut, and that it does not keep asking once it has the bottom.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CredoHistoryLoadTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val zone = ZoneId.of("Asia/Tbilisi")

    /** Serves one year per call; the API extent independently names the oldest available row. */
    private inner class HistoryGateway(private val years: Long = 2) : CredoGateway {
        val windows = mutableListOf<Pair<LocalDate, LocalDate>>()

        override suspend fun initiateLogin(credentials: CredoCredentials) = CredoLoginChallenge(
            operationId = "op",
            requiresOtp = false,
            mobileHint = null,
            directConfirmationSalt = "salt",
        )

        override suspend fun sendOtp(operationId: String) = Unit

        override suspend fun confirmLogin(
            challenge: CredoLoginChallenge,
            username: String,
            otp: String?,
        ) = CredoSession(accessToken = "token", refreshToken = null)

        override suspend fun accounts(session: CredoSession) = listOf(
            CredoRemoteAccount(SyntheticCredoWorkbook.IBAN, "GEL", 1, null, null),
        )

        override suspend fun downloadStatement(
            session: CredoSession,
            account: CredoRemoteAccount,
            fromIso: String,
            toIso: String,
        ): ByteArray {
            val from = fromIso.toLocalDate()
            val to = toIso.toLocalDate()
            windows += from to to
            val first = windows.size == 1
            if (years > 2 && windows.size == 3) throw CredoApiException("EMPTY_STATEMENT")
            if (years > 2 && windows.size == 4) return SyntheticCredoWorkbook.build(
                periodFrom = from, periodTo = to, openingBalance = "0.00", closingBalance = "0.00",
                rows = listOf(
                    Row(date = from, operation = "სხვა ბანკიდან ჩარიცხვა", credit = "50.00", balance = "50.00", description = "Earlier income"),
                    Row(date = from.plusDays(1), operation = "თანხის გადარიცხვა", debit = "50.00", balance = "0.00", description = "Earlier withdrawal")))
            return SyntheticCredoWorkbook.build(
                periodFrom = from,
                periodTo = to,
                openingBalance = if (first) "100.00" else "0.00",
                closingBalance = if (first) "92.86" else "100.00",
                rows = listOf(
                    if (first) Row(
                        date = from.plusDays(1),
                        operation = "საბარათე ოპერაცია",
                        debit = "7.14",
                        balance = "92.86",
                        description = "გადახდა - SYNTHETIC SHOP 7.14 GEL",
                    ) else Row(
                        date = from.plusDays(1),
                        operation = "სხვა ბანკიდან ჩარიცხვა",
                        credit = "100.00",
                        balance = "100.00",
                        description = "Opening deposit",
                    ),
                ),
            )
        }

        override suspend fun historyExtent(session: CredoSession, account: CredoRemoteAccount) =
            dev.whekin.whfin.data.credo.CredoHistoryExtent(LocalDate.now(zone).minusYears(years))

        private fun String.toLocalDate(): LocalDate =
            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(this)).atZone(zone).toLocalDate()
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        val database = db().openHelper.writableDatabase
        database.execSQL(
            "DELETE FROM transactions WHERE accountId IN " +
                "(SELECT id FROM accounts WHERE iban LIKE 'GE00WH%')",
        )
        database.execSQL(
            "DELETE FROM statement_imports WHERE accountId IN " +
                "(SELECT id FROM accounts WHERE iban LIKE 'GE00WH%')",
        )
        database.execSQL("DELETE FROM accounts WHERE iban LIKE 'GE00WH%'")
        database.execSQL("DELETE FROM financial_groups WHERE provider = 'Credo'")
        ApplicationProvider.getApplicationContext<Application>()
            .getSharedPreferences("whfin_credo_secrets", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun db() = ApplicationProvider.getApplicationContext<WhfinApp>().db

    @Test
    fun theWalkStopsAtTheYearTheLedgerOpensAndLeavesNoHoleBehindIt() {
        val gateway = HistoryGateway()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = CredoSyncViewModel(
            app = app,
            gateway = gateway,
            secretStore = CredoSecretStore(app),
            syncDispatcher = dispatcher,
            retryDelayMillis = listOf(0L, 0L),
        )
        vm.connect("user", "password", remember = false)
        await { vm.state.value.stage == CredoSyncStage.Connected }

        vm.loadHistory()
        await { vm.state.value.stage == CredoSyncStage.Connected && vm.state.value.results.isNotEmpty() }

        // Two years asked for, reaching the API extent — the zero opening is not the stop signal.
        assertEquals(2, gateway.windows.size)
        val (firstFrom, _) = gateway.windows[0]
        val (_, secondTo) = gateway.windows[1]
        assertEquals(firstFrom.minusDays(1), secondTo)

        // One row per year, reported once for the account rather than once per request.
        val result = vm.state.value.results.single()
        assertEquals(2, result.inserted)
        assertEquals(0, vm.state.value.unchanged)
        assertEquals(0, vm.state.value.currentChunk)
    }

    @Test fun firstConnectionAutomaticallyWalksPastZeroAndAnEmptyYear() {
        val gateway = HistoryGateway(years = 4)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = CredoSyncViewModel(app, gateway, CredoSecretStore(app), syncDispatcher = dispatcher, retryDelayMillis = emptyList())
        vm.connect("user", "password", remember = false, syncWhenConnected = true)
        await { vm.state.value.stage == CredoSyncStage.Connected && !vm.state.value.canLoadOlderHistory }
        assertEquals(4, gateway.windows.size)
        assertEquals(4, vm.state.value.results.single().inserted)
        assertNull(vm.state.value.results.single().errorCode)
        val account = kotlinx.coroutines.runBlocking { db().accountDao().byIbanAndCurrency(SyntheticCredoWorkbook.IBAN, "GEL")!! }
        assertEquals(9286L, kotlinx.coroutines.runBlocking { db().transactionDao().allForIntegrity().filter { it.accountId == account.id }.sumOf { it.amountMinor } })
    }

    /** Serves one usable year and then reports the period as empty, the way a real account starts. */
    private inner class ShortHistoryGateway(private val failure: String) : CredoGateway {
        var downloads = 0

        override suspend fun initiateLogin(credentials: CredoCredentials) = CredoLoginChallenge(
            operationId = "op",
            requiresOtp = false,
            mobileHint = null,
            directConfirmationSalt = "salt",
        )

        override suspend fun sendOtp(operationId: String) = Unit

        override suspend fun confirmLogin(
            challenge: CredoLoginChallenge,
            username: String,
            otp: String?,
        ) = CredoSession(accessToken = "token", refreshToken = null)

        override suspend fun accounts(session: CredoSession) = listOf(
            CredoRemoteAccount(SyntheticCredoWorkbook.IBAN, "GEL", 1, null, null),
        )

        override suspend fun downloadStatement(
            session: CredoSession,
            account: CredoRemoteAccount,
            fromIso: String,
            toIso: String,
        ): ByteArray {
            downloads += 1
            if (downloads > 1) throw CredoApiException(failure)
            val from = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(fromIso)).atZone(zone).toLocalDate()
            val to = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(toIso)).atZone(zone).toLocalDate()
            return SyntheticCredoWorkbook.build(
                periodFrom = from,
                periodTo = to,
                openingBalance = "100.00",
                closingBalance = "92.86",
                rows = listOf(
                    Row(
                        date = from.plusDays(1),
                        operation = "საბარათე ოპერაცია",
                        debit = "7.14",
                        balance = "92.86",
                        description = "გადახდა - SYNTHETIC SHOP 7.14 GEL",
                    ),
                ),
            )
        }
    }

    private fun runHistory(gateway: CredoGateway): CredoSyncViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = CredoSyncViewModel(
            app = app,
            gateway = gateway,
            secretStore = CredoSecretStore(app),
            syncDispatcher = dispatcher,
            retryDelayMillis = listOf(0L, 0L),
        )
        vm.connect("user", "password", remember = false)
        await { vm.state.value.stage == CredoSyncStage.Connected }
        vm.loadHistory()
        // The stage is Connected before and after, so wait for the run's own output instead.
        await { vm.state.value.results.isNotEmpty() || vm.state.value.unchanged > 0 }
        return vm
    }

    @Test
    fun emptyExportDoesNotProveThatOlderHistoryIsUnavailable() {
        // Before an account existed there is nothing to export, and the bank says so.
        val gateway = ShortHistoryGateway("EMPTY_STATEMENT")

        val vm = runHistory(gateway)

        assertEquals(2, gateway.downloads)
        val result = vm.state.value.results.single()
        assertEquals("HISTORY_INCOMPLETE", result.errorCode)
        assertEquals(1, result.inserted)
    }

    @Test
    fun aRealFailureKeepsImportedCountsAndReportsPartialHistory() {
        val gateway = ShortHistoryGateway("NETWORK_ERROR")

        val vm = runHistory(gateway)

        val result = vm.state.value.results.single()
        assertEquals("NETWORK_ERROR", result.errorCode)
        assertEquals(1, result.inserted)
    }

    private fun await(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            check(System.currentTimeMillis() - start < timeoutMillis) { "Timed out waiting for state" }
            Thread.sleep(20)
        }
    }
}
