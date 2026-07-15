package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.credo.CredoApiException
import dev.whekin.whfin.data.credo.CredoCredentials
import dev.whekin.whfin.data.credo.CredoGateway
import dev.whekin.whfin.data.credo.CredoLoginChallenge
import dev.whekin.whfin.data.credo.CredoRemoteAccount
import dev.whekin.whfin.data.credo.CredoSecretStore
import dev.whekin.whfin.data.credo.CredoSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CredoSyncHardeningTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private class ScriptedGateway(
        /** Скрипт download-исходов по ключу счёта: очередь бросаемых кодов, null = успех недостижим. */
        private val downloadScript: MutableMap<String, ArrayDeque<String>>,
    ) : CredoGateway {
        val downloadCalls = mutableListOf<String>()

        override suspend fun initiateLogin(credentials: CredoCredentials) =
            CredoLoginChallenge(operationId = "op", requiresOtp = false, mobileHint = null, directConfirmationSalt = "salt")

        override suspend fun sendOtp(operationId: String) = Unit

        override suspend fun confirmLogin(
            challenge: CredoLoginChallenge,
            username: String,
            otp: String?,
        ) = CredoSession(accessToken = "token", refreshToken = null)

        override suspend fun accounts(session: CredoSession) = listOf(
            CredoRemoteAccount("GE00XX0000000000000001", "GEL", 1, null, null),
            CredoRemoteAccount("GE00XX0000000000000001", "USD", 2, null, null),
            CredoRemoteAccount("GE00XX0000000000000002", "GEL", 3, null, null),
        )

        override suspend fun downloadStatement(
            session: CredoSession,
            account: CredoRemoteAccount,
            fromIso: String,
            toIso: String,
        ): ByteArray {
            val key = "${account.accountNumber}:${account.currency}"
            downloadCalls += key
            val queue = downloadScript[key] ?: throw CredoApiException("EMPTY_STATEMENT")
            val code = queue.removeFirstOrNull() ?: throw CredoApiException("EMPTY_STATEMENT")
            throw CredoApiException(code)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ApplicationProvider.getApplicationContext<Application>()
            .getSharedPreferences("whfin_credo_secrets", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun viewModel(gateway: CredoGateway): CredoSyncViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return CredoSyncViewModel(
            app = app,
            gateway = gateway,
            secretStore = CredoSecretStore(app),
            syncDispatcher = dispatcher,
            retryDelayMillis = listOf(0L, 0L),
        )
    }

    /** connect/sync прыгают через реальный IO-пул — ждём состояние с таймаутом. */
    private fun await(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            check(System.currentTimeMillis() - start < timeoutMillis) { "Timed out waiting for state" }
            Thread.sleep(20)
        }
    }

    @Test
    fun transientNetworkErrorIsRetriedPerAccountBeforeGivingUp() {
        val gateway = ScriptedGateway(
            mutableMapOf(
                // Первый счёт: NETWORK_ERROR трижды (исчерпывает 2 ретрая) → ошибка.
                "GE00XX0000000000000001:GEL" to ArrayDeque(listOf("NETWORK_ERROR", "NETWORK_ERROR", "NETWORK_ERROR")),
                // Второй счёт: permanent ошибка — ровно один вызов, без ретраев.
                "GE00XX0000000000000001:USD" to ArrayDeque(listOf("EMPTY_STATEMENT")),
                // Третий: HTTP_429 — защита сайта, тоже без ретраев.
                "GE00XX0000000000000002:GEL" to ArrayDeque(listOf("HTTP_429")),
            ),
        )
        val vm = viewModel(gateway)
        vm.connect("user", "password", remember = false)
        await { vm.state.value.stage == CredoSyncStage.Connected }

        vm.sync()
        await { vm.state.value.results.size == 3 && vm.state.value.stage == CredoSyncStage.Connected }

        val state = vm.state.value
        assertEquals(3, state.results.size)
        assertEquals("NETWORK_ERROR", state.results[0].errorCode)
        assertEquals("EMPTY_STATEMENT", state.results[1].errorCode)
        assertEquals("HTTP_429", state.results[2].errorCode)
        // 3 попытки по первому счёту, по одной на остальные.
        assertEquals(5, gateway.downloadCalls.size)
        assertEquals(3, gateway.downloadCalls.count { it == "GE00XX0000000000000001:GEL" })
    }

    @Test
    fun expiredSessionStopsRunAndRequiresFreshLogin() {
        val gateway = ScriptedGateway(
            mutableMapOf(
                "GE00XX0000000000000001:GEL" to ArrayDeque(listOf("HTTP_401")),
            ),
        )
        val vm = viewModel(gateway)
        vm.connect("user", "password", remember = false)
        await { vm.state.value.stage == CredoSyncStage.Connected }
        vm.sync()
        await { vm.state.value.stage == CredoSyncStage.Disconnected }

        val state = vm.state.value
        // Прогон остановлен на первом счёте: остальные не трогались.
        assertEquals(1, gateway.downloadCalls.size)
        assertEquals(CredoSyncStage.Disconnected, state.stage)
        assertEquals("SESSION_EXPIRED", state.errorCode)
        assertEquals("SESSION_EXPIRED", state.results.single().errorCode)
        assertTrue(state.accounts.isEmpty())

        // Повторный sync без нового входа не запускает сеть.
        vm.dismissError()
        vm.sync()
        await { vm.state.value.errorCode == "LOGIN_EXPIRED" }
        assertEquals(1, gateway.downloadCalls.size)
    }

    @Test
    fun authErrorClassificationIsExplicit() {
        assertTrue("HTTP_401".isCredoAuthError())
        assertTrue("UNAUTHORIZED".isCredoAuthError())
        assertTrue("AUTH_NOT_AUTHENTICATED".isCredoAuthError())
        assertFalse("HTTP_403".isCredoAuthError())
        assertFalse("NETWORK_ERROR".isCredoAuthError())

        assertTrue("NETWORK_ERROR".isCredoTransientError())
        assertTrue("HTTP_503".isCredoTransientError())
        assertFalse("HTTP_429".isCredoTransientError())
        assertFalse("HTTP_403".isCredoTransientError())
        assertFalse("EMPTY_STATEMENT".isCredoTransientError())
    }

    @Test
    fun partialFailureKeepsConnectedStateForImmediateRetry() {
        val gateway = ScriptedGateway(
            mutableMapOf(
                "GE00XX0000000000000001:GEL" to ArrayDeque(listOf("EMPTY_STATEMENT")),
                "GE00XX0000000000000001:USD" to ArrayDeque(listOf("EMPTY_STATEMENT")),
                "GE00XX0000000000000002:GEL" to ArrayDeque(listOf("EMPTY_STATEMENT")),
            ),
        )
        val vm = viewModel(gateway)
        vm.connect("user", "password", remember = false)
        await { vm.state.value.stage == CredoSyncStage.Connected }
        vm.sync()
        await { vm.state.value.results.size == 3 && vm.state.value.stage == CredoSyncStage.Connected }

        assertNull(vm.state.value.errorCode)
        assertEquals(3, vm.state.value.results.size)
    }
}
