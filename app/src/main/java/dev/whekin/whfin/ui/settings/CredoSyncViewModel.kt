package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.credo.CredoApiException
import dev.whekin.whfin.data.credo.CredoCredentials
import dev.whekin.whfin.data.credo.CredoGateway
import dev.whekin.whfin.data.credo.CredoLoginChallenge
import dev.whekin.whfin.data.credo.CredoRemoteAccount
import dev.whekin.whfin.data.credo.CredoSecretStore
import dev.whekin.whfin.data.credo.CredoSession
import dev.whekin.whfin.data.credo.MyCredoGateway
import dev.whekin.whfin.data.importer.StatementImporter
import dev.whekin.whfin.data.db.StatementImportOrigin
import java.io.ByteArrayInputStream
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CredoSyncStage { Disconnected, Connecting, AwaitingOtp, Connected, Syncing }

data class CredoSyncFileResult(
    val accountLabel: String,
    val result: StatementImporter.Result? = null,
    val errorCode: String? = null,
)

data class CredoSyncUiState(
    val stage: CredoSyncStage = CredoSyncStage.Disconnected,
    val savedUsername: String? = null,
    val hasSavedPassword: Boolean = false,
    val mobileHint: String? = null,
    val accounts: List<CredoRemoteAccount> = emptyList(),
    val currentAccount: Int = 0,
    val currentPhase: StatementImporter.Phase? = null,
    val results: List<CredoSyncFileResult> = emptyList(),
    val errorCode: String? = null,
    val isBusy: Boolean = false,
)

class CredoSyncViewModel internal constructor(
    app: Application,
    private val gateway: CredoGateway,
    private val secretStore: CredoSecretStore,
) : AndroidViewModel(app) {
    constructor(app: Application) : this(
        app = app,
        gateway = MyCredoGateway(app),
        secretStore = CredoSecretStore(app),
    )

    private val db = (app as WhfinApp).db
    private val _state = MutableStateFlow(
        CredoSyncUiState(
            savedUsername = secretStore.savedUsername(),
            hasSavedPassword = secretStore.hasCredentials(),
        ),
    )
    val state: StateFlow<CredoSyncUiState> = _state.asStateFlow()

    private var challenge: CredoLoginChallenge? = null
    private var pendingCredentials: CredoCredentials? = null
    private var rememberPassword = false
    private var session: CredoSession? = null

    fun connect(username: String, credential: String, remember: Boolean) {
        if (_state.value.stage == CredoSyncStage.Connecting) return
        viewModelScope.launch {
            val credentials = resolveCredentials(username, credential) ?: run {
                fail("CREDENTIALS_REQUIRED")
                return@launch
            }
            _state.value = _state.value.copy(
                stage = CredoSyncStage.Connecting,
                errorCode = null,
                results = emptyList(),
            )
            runCatching {
                gateway.initiateLogin(credentials)
            }.onSuccess { loginChallenge ->
                challenge = loginChallenge
                pendingCredentials = credentials
                rememberPassword = remember
                if (loginChallenge.requiresOtp) {
                    runCatching { gateway.sendOtp(loginChallenge.operationId) }
                        .onSuccess {
                            _state.value = _state.value.copy(
                                stage = CredoSyncStage.AwaitingOtp,
                                mobileHint = loginChallenge.mobileHint,
                                errorCode = null,
                                isBusy = false,
                            )
                        }
                        .onFailure(::fail)
                } else {
                    finishLogin(loginChallenge, credentials, otp = null)
                }
            }.onFailure(::fail)
        }
    }

    fun submitOtp(otp: String) {
        val loginChallenge = challenge ?: return fail("LOGIN_EXPIRED")
        val credentials = pendingCredentials ?: return fail("LOGIN_EXPIRED")
        if (otp.length != OTP_LENGTH || !otp.all(Char::isDigit)) return fail("INVALID_OTP")
        if (_state.value.stage != CredoSyncStage.AwaitingOtp) return
        _state.value = _state.value.copy(errorCode = null, isBusy = true)
        viewModelScope.launch { finishLogin(loginChallenge, credentials, otp) }
    }

    fun resendOtp() {
        val operationId = challenge?.operationId ?: return fail("LOGIN_EXPIRED")
        viewModelScope.launch {
            _state.value = _state.value.copy(errorCode = null, isBusy = true)
            runCatching { gateway.sendOtp(operationId) }
                .onSuccess {
                    _state.value = _state.value.copy(stage = CredoSyncStage.AwaitingOtp, isBusy = false)
                }
                .onFailure(::fail)
        }
    }

    fun sync() {
        val activeSession = session ?: return fail("LOGIN_EXPIRED")
        val accounts = _state.value.accounts
        if (accounts.isEmpty()) return fail("NO_ACCOUNTS")
        if (_state.value.stage == CredoSyncStage.Syncing) return
        viewModelScope.launch(Dispatchers.IO) {
            val (fromIso, toIso) = statementRange()
            val results = mutableListOf<CredoSyncFileResult>()
            accounts.forEachIndexed { index, account ->
                _state.value = _state.value.copy(
                    stage = CredoSyncStage.Syncing,
                    currentAccount = index + 1,
                    currentPhase = StatementImporter.Phase.READING,
                    results = results.toList(),
                    errorCode = null,
                )
                val fileResult = try {
                    val bytes = gateway.downloadStatement(activeSession, account, fromIso, toIso)
                    val result = ByteArrayInputStream(bytes).use { input ->
                        StatementImporter(db).import(
                            input = input,
                            fileName = account.fileName(),
                            origin = StatementImportOrigin.CREDO_SYNC,
                        ) { phase ->
                            _state.value = _state.value.copy(currentPhase = phase)
                        }
                    }
                    CredoSyncFileResult(account.maskedLabel, result = result)
                } catch (error: Exception) {
                    CredoSyncFileResult(account.maskedLabel, errorCode = error.safeCode())
                }
                results += fileResult
            }
            _state.value = _state.value.copy(
                stage = CredoSyncStage.Connected,
                currentAccount = 0,
                currentPhase = null,
                results = results,
                errorCode = null,
            )
        }
    }

    fun disconnect() {
        secretStore.clear()
        challenge = null
        pendingCredentials = null
        session = null
        _state.value = CredoSyncUiState()
    }

    fun dismissError() {
        _state.value = _state.value.copy(errorCode = null)
    }

    private suspend fun finishLogin(
        loginChallenge: CredoLoginChallenge,
        credentials: CredoCredentials,
        otp: String?,
    ) {
        runCatching {
            val activeSession = gateway.confirmLogin(loginChallenge, credentials.username, otp)
            val remoteAccounts = gateway.accounts(activeSession)
            if (remoteAccounts.isEmpty()) throw CredoApiException("NO_ACCOUNTS")
            activeSession to remoteAccounts
        }.onSuccess { (activeSession, remoteAccounts) ->
            session = activeSession
            if (rememberPassword) secretStore.save(credentials) else secretStore.clear()
            challenge = null
            pendingCredentials = null
            _state.value = CredoSyncUiState(
                stage = CredoSyncStage.Connected,
                savedUsername = credentials.username,
                hasSavedPassword = rememberPassword,
                accounts = remoteAccounts,
            )
        }.onFailure(::fail)
    }

    private suspend fun resolveCredentials(username: String, credential: String): CredoCredentials? =
        withContext(Dispatchers.IO) {
            val normalizedUsername = username.trim().lowercase()
            when {
                normalizedUsername.isBlank() -> null
                credential.isNotBlank() -> CredoCredentials(normalizedUsername, credential)
                _state.value.hasSavedPassword -> secretStore.load()?.takeIf {
                    it.username.equals(normalizedUsername, ignoreCase = true)
                }
                else -> null
            }
        }

    private fun fail(error: Throwable) = fail(error.safeCode())

    private fun fail(code: String) {
        val connected = session != null && _state.value.accounts.isNotEmpty()
        if (!connected && code in TERMINAL_LOGIN_ERRORS) {
            challenge = null
            pendingCredentials = null
        }
        val awaitingOtp = !connected && challenge != null && pendingCredentials != null
        _state.value = _state.value.copy(
            stage = when {
                connected -> CredoSyncStage.Connected
                awaitingOtp -> CredoSyncStage.AwaitingOtp
                else -> CredoSyncStage.Disconnected
            },
            errorCode = code,
            isBusy = false,
        )
    }

    private fun Throwable.safeCode(): String = when (this) {
        is CredoApiException -> code
        else -> "UNKNOWN_ERROR"
    }

    private fun statementRange(): Pair<String, String> {
        val zone = ZoneId.of("Asia/Tbilisi")
        return credoStatementRange(ZonedDateTime.now(zone))
    }

    private fun CredoRemoteAccount.fileName(): String =
        "mycredo_${currency.lowercase()}_${accountNumber.takeLast(4)}.xlsx"

    private companion object {
        const val OTP_LENGTH = 4
        val TERMINAL_LOGIN_ERRORS = setOf("UNAUTHORIZED", "LOGIN_EXPIRED", "USER_IS_BLOCKED", "USER_OTP_BLOCKED")
    }
}

internal fun credoStatementRange(now: ZonedDateTime): Pair<String, String> =
    DateTimeFormatter.ISO_INSTANT.format(now.minusMonths(12).toInstant()) to
        DateTimeFormatter.ISO_INSTANT.format(now.toInstant())
