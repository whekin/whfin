package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.credo.CredoCredentials
import dev.whekin.whfin.data.credo.CredoGateway
import dev.whekin.whfin.data.credo.CredoLoginChallenge
import dev.whekin.whfin.data.credo.CredoRemoteAccount
import dev.whekin.whfin.data.credo.CredoSecretStore
import dev.whekin.whfin.data.credo.CredoSession
import dev.whekin.whfin.data.statement.SyntheticCredoWorkbook
import dev.whekin.whfin.data.statement.SyntheticCredoWorkbook.Row
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A sync that would change nothing must leave nothing behind.
 *
 * MyCredo re-downloads the same period every run, so without this an untouched account files a
 * "0 added" record on every sync and the statement history fills with them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CredoSyncSkipTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val statement = SyntheticCredoWorkbook.build(
        openingBalance = "100.00",
        closingBalance = "92.86",
        rows = listOf(
            Row(
                date = LocalDate.of(2026, 1, 12),
                operation = "საბარათე ოპერაცია",
                debit = "7.14",
                balance = "92.86",
                description = "გადახდა - SYNTHETIC SHOP 7.14 GEL 09.01.2026",
            ),
        ),
    )

    private inner class FixedGateway : CredoGateway {
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
            return statement
        }
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
    fun anAccountWithNothingNewIsCountedOnceAndFilesNoRecord() {
        val gateway = FixedGateway()
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

        vm.sync()
        await { vm.state.value.stage == CredoSyncStage.Connected && vm.state.value.results.size == 1 }
        assertEquals(1, vm.state.value.results.single().result?.inserted)
        assertEquals(0, vm.state.value.unchanged)
        assertEquals(1, importRecords())

        // The same statement again: still downloaded, but nothing is written and nothing is filed.
        vm.sync()
        await { vm.state.value.stage == CredoSyncStage.Connected && vm.state.value.unchanged == 1 }

        assertTrue(vm.state.value.results.isEmpty())
        assertEquals(2, gateway.downloads)
        assertEquals(1, importRecords())
    }

    private fun importRecords(): Int = runBlocking {
        val account = db().accountDao().byIbanAndCurrency(SyntheticCredoWorkbook.IBAN, "GEL")
            ?: return@runBlocking 0
        db().statementImportDao().forAccount(account.id).size
    }

    private fun await(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            check(System.currentTimeMillis() - start < timeoutMillis) { "Timed out waiting for state" }
            Thread.sleep(20)
        }
    }
}
