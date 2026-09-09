package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.whekin.whfin.data.security.BankSessionStore
import dev.whekin.whfin.data.tbc.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TbcLoginViewModelTest {
    private class Store : BankSessionStore {
        var value: String? = null
        var loads = 0
        override fun hasSaved() = value != null
        override fun load(): String? { loads++; return value }
        override fun save(value: String) { this.value = value }
        override fun clear() { value = null }
    }
    private class Gateway : TbcGateway {
        var loginCalls = 0
        var confirmationCalls = 0
        var badCode = false
        var expired = false
        var accountFailure = false
        var wait: CompletableDeferred<Unit>? = null
        private val session = TbcSession(mapOf("session" to "example-cookie"), "example-device")
        override fun snapshot() = session
        override fun clear() = Unit
        override suspend fun login(username: String, credential: String): TbcLoginResult {
            loginCalls++; wait?.await()
            return TbcLoginResult.Challenge(TbcChallenge("example-challenge", "NONE", "SMS_OTP"))
        }
        override suspend fun confirm(challenge: TbcChallenge, code: String): TbcSession {
            confirmationCalls++
            if (badCode) throw TbcException("OTP")
            return session
        }
        override suspend fun resume(session: TbcSession): TbcSession {
            if (expired) throw TbcException("SESSION")
            return session
        }
        override suspend fun accounts(): List<TbcAccount> {
            if (accountFailure) throw TbcException("NETWORK")
            return listOf(TbcAccount(1, "GE00TB0000000000000001", "GEL", "Everyday"))
        }
    }
    private lateinit var store: Store
    private lateinit var gateway: Gateway
    private lateinit var vm: TbcLoginViewModel
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = Store(); gateway = Gateway()
        vm = TbcLoginViewModel(ApplicationProvider.getApplicationContext<Application>(), { gateway }, store)
    }
    @After fun cleanup() { vm.leave(); Dispatchers.resetMain() }
    private fun settled() = runBlocking { withTimeout(5000) { vm.state.first { it.stage != TbcLoginStage.Working } } }
    @Test fun passwordChallengeDoesNotPersistAnythingAndBadCodeCanBeRetried() {
        vm.storageAllowed(true); vm.setRemember(true)
        vm.login("example-user", "example-credential")
        assertEquals(TbcLoginStage.Code, settled().stage)
        assertNull(store.value)
        gateway.badCode = true; vm.confirm("0000")
        assertEquals("OTP", settled().error)
        assertEquals(TbcLoginStage.Code, vm.state.value.stage)
        gateway.badCode = false; vm.confirm("0000")
        assertEquals(TbcLoginStage.Connected, settled().stage)
        assertNotNull(store.value)
        assertFalse(store.value!!.contains("example-credential"))
        assertEquals(2, gateway.confirmationCalls)
    }
    @Test fun sessionIsNeverLoadedAutomaticallyAndRequiresStoragePermission() {
        store.value = gateway.snapshot().encode()
        assertEquals(0, store.loads)
        vm.restore(); assertEquals(0, store.loads)
        vm.storageAllowed(true); vm.restore()
        assertEquals(TbcLoginStage.Connected, settled().stage)
        assertEquals(1, store.loads)
    }
    @Test fun expiryDropsSavedSessionAndReturnsToPasswordForm() {
        store.value = gateway.snapshot().encode()
        vm.storageAllowed(true); gateway.expired = true; vm.restore()
        assertEquals(TbcLoginStage.Login, settled().stage)
        assertEquals("SESSION", vm.state.value.error)
        assertNull(store.value)
    }
    @Test fun accountFetchFailureKeepsAUsableRetryAfterSuccessfulOtp() {
        vm.login("example-user", "example-credential"); settled()
        gateway.accountFailure = true; vm.confirm("0000")
        assertEquals(TbcLoginStage.Connected, settled().stage)
        assertEquals("NETWORK", vm.state.value.error)
        gateway.accountFailure = false; vm.refreshAccounts()
        assertEquals(1, settled().accounts.size)
        assertNull(vm.state.value.error)
        assertNull(store.value)
    }
    @Test fun leavingCancelsPendingLoginAndDuplicateTapsNeverIssueAnotherRequest() {
        gateway.wait = CompletableDeferred()
        vm.login("example-user", "example-credential")
        vm.login("example-user", "example-credential")
        assertEquals(1, gateway.loginCalls)
        vm.leave(); gateway.wait!!.complete(Unit)
        assertEquals(TbcLoginStage.Login, settled().stage)
        assertNull(store.value)
    }
    @Test fun successfulLoginRunsSyncAndTheButtonRunsItAgain() {
        var calls = 0
        vm = TbcLoginViewModel(ApplicationProvider.getApplicationContext<Application>(), { gateway }, store,
            synchronize = { _, progress ->
                calls++; progress(1, 1)
                dev.whekin.whfin.data.importer.TbcSyncResult(matched = 4)
            })
        vm.login("example-user", "example-credential"); settled()
        vm.confirm("0000")
        assertEquals(4, settled().syncResult?.matched)
        assertEquals(1, calls)
        vm.syncTransactions()
        settled()
        assertEquals(2, calls)
    }
    @Test fun missingOpeningIsAVisibleResultNotAFalseSuccessfulImport() {
        val remote = TbcLedgerAccount("10", "GE00TB0000000000000001", "GEL", "Everyday")
        vm = TbcLoginViewModel(ApplicationProvider.getApplicationContext<Application>(), { gateway }, store,
            synchronize = { _, _ -> dev.whekin.whfin.data.importer.TbcSyncResult(needsStatement = listOf(remote)) })
        vm.login("example-user", "example-credential"); settled(); vm.confirm("0000")
        assertEquals(listOf(remote), settled().syncResult?.needsStatement)
        assertEquals(0, vm.state.value.syncResult?.inserted)
    }
    @Test fun disablingStorageClearsTheSavedSession() {
        store.value = gateway.snapshot().encode()
        vm.storageAllowed(true); vm.setRemember(true)
        vm.storageAllowed(false)
        assertNull(store.value)
        assertFalse(vm.state.value.remember)
    }
}
