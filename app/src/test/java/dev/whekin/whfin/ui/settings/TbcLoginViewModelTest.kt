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
        var resumeNetworkError = false
        var passwordRejected = false
        var wait: CompletableDeferred<Unit>? = null
        private val session = TbcSession(mapOf("session" to "example-cookie"), "example-device")
        override fun snapshot() = session
        override fun clear() = Unit
        override suspend fun login(username: String, credential: String): TbcLoginResult {
            loginCalls++; wait?.await()
            if (passwordRejected) throw TbcException("LOGIN")
            return TbcLoginResult.Challenge(TbcChallenge("example-challenge", "NONE", "SMS_OTP"))
        }
        override suspend fun confirm(challenge: TbcChallenge, code: String): TbcSession {
            confirmationCalls++
            if (badCode) throw TbcException("OTP")
            return session
        }
        override suspend fun resume(session: TbcSession): TbcSession {
            if (resumeNetworkError) throw TbcException("NETWORK")
            if (expired) throw TbcException("SESSION")
            return session
        }
        override suspend fun accounts(): List<TbcAccount> {
            if (accountFailure) throw TbcException("NETWORK")
            return listOf(TbcAccount(1, "GE00TB0000000000000001", "GEL", "Everyday"))
        }
    }
    private class Credentials : dev.whekin.whfin.data.security.BankCredentialStore {
        var value: dev.whekin.whfin.data.security.BankCredentials? = null
        var loads = 0
        override fun hasCredentials() = value != null
        override fun load(): dev.whekin.whfin.data.security.BankCredentials? { loads++; return value }
        override fun save(credentials: dev.whekin.whfin.data.security.BankCredentials) { value = credentials }
        override fun clear() { value = null }
    }
    private lateinit var credentials: Credentials
    private lateinit var store: Store
    private lateinit var gateway: Gateway
    private lateinit var vm: TbcLoginViewModel
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = Store(); credentials = Credentials(); gateway = Gateway()
        vm = TbcLoginViewModel(ApplicationProvider.getApplicationContext<Application>(), { gateway }, store, credentialStore = credentials)
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
        gateway.accountFailure = false; vm.syncTransactions()
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
        vm = TbcLoginViewModel(ApplicationProvider.getApplicationContext<Application>(), { gateway }, store, credentialStore = credentials,
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
        vm = TbcLoginViewModel(ApplicationProvider.getApplicationContext<Application>(), { gateway }, store, credentialStore = credentials,
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
    @Test fun rememberedSuccessfulAuthenticationSurvivesAFailedAccountReadAndReopen() {
        vm.storageAllowed(true); vm.setRemember(true)
        vm.login("example-user", "example-credential"); settled()
        gateway.accountFailure = true
        vm.confirm("0000"); settled()
        assertNotNull("Successful bank authentication must be saved before reading accounts", store.value)
        vm.leave()
        assertTrue(vm.state.value.hasSaved)
        gateway.accountFailure = false
        vm.restore()
        assertEquals(TbcLoginStage.Connected, settled().stage)
        assertEquals(1, gateway.loginCalls)
    }

    @Test fun rememberChoiceSurvivesExpiryAndViewModelRecreation() {
        vm.storageAllowed(true); vm.setRemember(true)
        vm.login("example-user", "example-credential"); settled(); vm.confirm("0000"); settled()
        credentials.clear() // The session-only format from an older installation had no password.
        vm.leave(); gateway.expired = true; vm.restore(); settled()
        assertNull(store.value)
        vm.leave()
        vm = TbcLoginViewModel(ApplicationProvider.getApplicationContext<Application>(), { gateway }, store, credentialStore = credentials)
        assertTrue(vm.state.value.remember)
        assertFalse(vm.state.value.hasSaved)
        assertEquals("SESSION", vm.state.value.error)
        vm.forget()
        vm = TbcLoginViewModel(ApplicationProvider.getApplicationContext<Application>(), { gateway }, store, credentialStore = credentials)
        assertFalse(vm.state.value.remember)
    }

    @Test fun expiredSessionUsesSavedPasswordOnceAndAsksOnlyForOtp() {
        vm.storageAllowed(true); vm.setRemember(true)
        vm.login("example-user", "example-credential"); settled(); vm.confirm("0000"); settled()
        assertNotNull(credentials.value)
        vm.leave(); gateway.expired = true
        vm.restore()
        assertEquals(TbcLoginStage.Code, settled().stage)
        assertEquals(2, gateway.loginCalls)
        assertEquals(1, credentials.loads)
        vm.confirm("0000")
        assertEquals(TbcLoginStage.Connected, settled().stage)
        assertTrue(vm.state.value.hasSavedCredentials)
    }
    @Test fun savedCredentialsWorkAfterAProcessWithoutASessionCookie() {
        credentials.value = dev.whekin.whfin.data.security.BankCredentials("example-user", "example-credential")
        vm = TbcLoginViewModel(ApplicationProvider.getApplicationContext<Application>(), { gateway }, store, credentialStore = credentials)
        vm.storageAllowed(true)
        assertTrue(vm.state.value.hasSaved)
        assertEquals(0, credentials.loads)
        vm.restore()
        assertEquals(TbcLoginStage.Code, settled().stage)
        assertEquals(1, gateway.loginCalls)
    }

    @Test fun networkFailureDoesNotTriggerAnotherLoginOrLoseSavedCredentials() {
        credentials.value = dev.whekin.whfin.data.security.BankCredentials("example-user", "example-credential")
        store.value = gateway.snapshot().encode()
        vm.storageAllowed(true); gateway.resumeNetworkError = true; vm.restore()
        assertEquals("NETWORK", settled().error)
        assertEquals(0, gateway.loginCalls)
        assertNotNull(credentials.value)
    }
    @Test fun rejectedSavedPasswordStopsAfterOneAttemptAndOffersManualReplacement() {
        credentials.value = dev.whekin.whfin.data.security.BankCredentials("example-user", "example-credential")
        vm.storageAllowed(true); gateway.passwordRejected = true; vm.restore()
        assertEquals("LOGIN", settled().error)
        assertEquals(1, gateway.loginCalls)
        assertFalse(vm.state.value.hasSavedCredentials)
        assertNull(credentials.value)
    }
    @Test fun disablingStorageAndForgettingClearBothCookiesAndCredentials() {
        credentials.value = dev.whekin.whfin.data.security.BankCredentials("example-user", "example-credential")
        store.value = gateway.snapshot().encode()
        vm.storageAllowed(false)
        assertNull(credentials.value); assertNull(store.value)
        credentials.value = dev.whekin.whfin.data.security.BankCredentials("example-user", "example-credential")
        store.value = gateway.snapshot().encode(); vm.forget()
        assertNull(credentials.value); assertNull(store.value)
    }

}
