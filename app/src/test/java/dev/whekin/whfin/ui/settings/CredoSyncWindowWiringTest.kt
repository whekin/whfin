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
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.db.StatementImportEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
 * The window a sync actually asks the bank for, per account.
 *
 * [dev.whekin.whfin.data.credo.CredoSyncWindow] owns the rule; this covers the wiring around it —
 * that coverage is looked up for the right ledger and that each account gets its own range.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CredoSyncWindowWiringTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val zone = ZoneId.of("Asia/Tbilisi")

    private val synced = "GE00WW0000000000000011"
    private val known = "GE00WW0000000000000012"
    private val unknown = "GE00WW0000000000000013"

    private class RecordingGateway(private val accounts: List<CredoRemoteAccount>) : CredoGateway {
        val requestedFrom = mutableMapOf<String, String>()

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

        override suspend fun accounts(session: CredoSession) = accounts

        override suspend fun downloadStatement(
            session: CredoSession,
            account: CredoRemoteAccount,
            fromIso: String,
            toIso: String,
        ): ByteArray {
            requestedFrom[account.accountNumber] = fromIso
            // Nothing is imported: this test is only about what was asked for.
            throw CredoApiException("EMPTY_STATEMENT")
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        runBlocking {
            val db = db()
            val accountId = db.accountDao().insert(
                AccountEntity(name = "Synced", type = AccountType.BANK, currency = "GEL", iban = synced),
            )
            db.statementImportDao().insert(
                StatementImportEntity(
                    accountId = accountId,
                    periodFrom = LocalDate.now(zone).minusMonths(6).toEpochDay(),
                    periodTo = LocalDate.now(zone).minusDays(3).toEpochDay(),
                    openingBalanceMinor = 0,
                    closingBalanceMinor = 100,
                    totalRows = 4,
                    inserted = 4,
                    duplicates = 0,
                    reconciled = 0,
                    reviewCount = 0,
                    importedAt = System.currentTimeMillis(),
                ),
            )
            db.accountDao().insert(
                AccountEntity(name = "Known", type = AccountType.BANK, currency = "GEL", iban = known),
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        // Precise cleanup: this is the app's shared database, not a private in-memory one.
        db().openHelper.writableDatabase.execSQL(
            "DELETE FROM statement_imports WHERE accountId IN " +
                "(SELECT id FROM accounts WHERE iban LIKE 'GE00WW%')",
        )
        db().openHelper.writableDatabase.execSQL("DELETE FROM accounts WHERE iban LIKE 'GE00WW%'")
        ApplicationProvider.getApplicationContext<Application>()
            .getSharedPreferences("whfin_credo_secrets", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun db() = ApplicationProvider.getApplicationContext<WhfinApp>().db

    @Test
    fun eachAccountIsAskedForItsOwnWindow() {
        val gateway = RecordingGateway(
            listOf(
                CredoRemoteAccount(synced, "GEL", 1, null, null),
                CredoRemoteAccount(known, "GEL", 2, null, null),
                CredoRemoteAccount(unknown, "GEL", 3, null, null),
            ),
        )
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
        await { vm.state.value.results.size == 3 }

        val now = ZonedDateTime.now(zone)
        assertEquals(3, gateway.requestedFrom.size)
        // Covered up to three days ago, so the minimum month of overlap decides.
        assertNear(now.minusMonths(1), gateway.requestedFrom.getValue(synced))
        // A ledger WHFIN has but never imported into, and one it has never seen at all: full year.
        assertNear(now.minusMonths(12), gateway.requestedFrom.getValue(known))
        assertNear(now.minusMonths(12), gateway.requestedFrom.getValue(unknown))
    }

    /** Within a day: the window is built from "now", which moves while the test runs. */
    private fun assertNear(expected: ZonedDateTime, actualIso: String) {
        val actual = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(actualIso))
        val drift = Duration.between(expected.toInstant(), actual).abs()
        assertTrue("$actualIso is not within a day of $expected", drift < Duration.ofDays(1))
    }

    private fun await(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            check(System.currentTimeMillis() - start < timeoutMillis) { "Timed out waiting for state" }
            Thread.sleep(20)
        }
    }
}
