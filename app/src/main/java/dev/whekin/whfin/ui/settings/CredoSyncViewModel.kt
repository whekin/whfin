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
import dev.whekin.whfin.data.credo.CredoHistoryScan
import dev.whekin.whfin.data.credo.CredoSession
import dev.whekin.whfin.data.credo.CredoSyncWindow
import dev.whekin.whfin.data.credo.MyCredoGateway
import dev.whekin.whfin.data.db.StatementImportEntity
import dev.whekin.whfin.data.rates.NbgHistoricalRateProvider
import dev.whekin.whfin.data.rates.TransactionValuationRepository
import dev.whekin.whfin.data.importer.AmbiguousBankLedgerException
import dev.whekin.whfin.data.importer.InvalidStatementException
import dev.whekin.whfin.data.importer.StatementImporter
import dev.whekin.whfin.data.db.StatementImportOrigin
import dev.whekin.whfin.data.statement.MalformedStatementException
import dev.whekin.whfin.data.statement.UnsupportedStatementException
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CredoSyncStage { Disconnected, Connecting, AwaitingOtp, Connected, Syncing }

/**
 * What one account's run came to.
 *
 * Counts rather than one [StatementImporter.Result]: loading history walks an account in several
 * statements, and the user cares about the account, not about how many requests it took.
 */
data class CredoSyncFileResult(
    val accountLabel: String,
    val inserted: Int = 0,
    val duplicates: Int = 0,
    val reconciled: Int = 0,
    val unmappedOperationNames: Set<String> = emptySet(),
    val errorCode: String? = null,
    /** The period this account was asked for, shown only when it failed. Dates carry no amounts. */
    val askedFrom: String? = null,
    val askedTo: String? = null,
    /** Which of our own rules refused the statement, when one did. Never a server message. */
    val detail: String? = null,
    /** Opaque handle into a process-memory copy of the exact XLSX that failed after download. */
    val originalStatementToken: String? = null,
    /** Masked, user-facing SAF suggestion; never contains the full account number. */
    val originalStatementFileName: String? = null,
)

data class CredoSyncUiState(
    val stage: CredoSyncStage = CredoSyncStage.Disconnected,
    val savedUsername: String? = null,
    val hasSavedPassword: Boolean = false,
    val mobileHint: String? = null,
    val accounts: List<CredoRemoteAccount> = emptyList(),
    val currentAccount: Int = 0,
    /** Which year-long statement of a history load is in flight; 0 during a routine sync. */
    val currentChunk: Int = 0,
    /** Days of historical rates fetched so far, while a history load prices what it brought in. */
    val valuedDays: Int = 0,
    val currentPhase: StatementImporter.Phase? = null,
    val results: List<CredoSyncFileResult> = emptyList(),
    /** Accounts whose statement would have added nothing: said once, not as a row each. */
    val unchanged: Int = 0,
    val errorCode: String? = null,
    val isBusy: Boolean = false,
)

class CredoSyncViewModel internal constructor(
    app: Application,
    private val gateway: CredoGateway,
    private val secretStore: CredoSecretStore,
    private val syncDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val retryDelayMillis: List<Long> = DEFAULT_RETRY_DELAYS,
) : AndroidViewModel(app) {
    constructor(app: Application) : this(
        app = app,
        gateway = MyCredoGateway(app),
        secretStore = CredoSecretStore(app),
    )

    private val db = (app as WhfinApp).db
    /** Cleared with this process; deliberately never saved to a Bundle, preferences, or Room. */
    val loginDraft = CredoLoginDraft()
    private val zone = ZoneId.of("Asia/Tbilisi")
    private val _state = MutableStateFlow(
        CredoSyncUiState(
            hasSavedPassword = secretStore.hasCredentials(),
        ),
    )
    val state: StateFlow<CredoSyncUiState> = _state.asStateFlow()

    private var challenge: CredoLoginChallenge? = null
    private var pendingCredentials: CredoCredentials? = null
    private var rememberPassword = false
    private var session: CredoSession? = null
    private val downloadedStatements = mutableMapOf<String, ByteArray>()

    fun revealSavedUsername() {
        if (!_state.value.hasSavedPassword || _state.value.savedUsername != null) return
        viewModelScope.launch(Dispatchers.IO) {
            val username = secretStore.savedUsername()
            _state.value = _state.value.copy(
                savedUsername = username,
                hasSavedPassword = username != null,
            )
        }
    }

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
        viewModelScope.launch(syncDispatcher) {
            clearDownloadedStatements()
            val results = mutableListOf<CredoSyncFileResult>()
            var unchanged = 0
            for ((index, account) in accounts.withIndex()) {
                val (fromIso, toIso) = statementRangeFor(account)
                _state.value = _state.value.copy(
                    stage = CredoSyncStage.Syncing,
                    currentAccount = index + 1,
                    currentPhase = StatementImporter.Phase.READING,
                    results = results.toList(),
                    unchanged = unchanged,
                    errorCode = null,
                )
                var downloadedBytes: ByteArray? = null
                val fileResult = try {
                    val bytes = downloadWithRetry(activeSession, account, fromIso, toIso)
                    downloadedBytes = bytes
                    // A quiet account returns a statement that would add nothing. Importing it
                    // anyway leaves a "0 added" record behind on every run, so read first and let
                    // the run say it once at the end instead. An unreadable file falls through to
                    // the import, which reports why in the words it always has.
                    val preview = runCatching {
                        ByteArrayInputStream(bytes).use { input ->
                            StatementImporter(db).preview(input, account.fileName())
                        }
                    }.getOrNull()
                    if (preview?.changesNothing == true) {
                        if (preview.unmappedOperationNames.isEmpty()) {
                            unchanged += 1
                            _state.value = _state.value.copy(unchanged = unchanged)
                        } else {
                            results += CredoSyncFileResult(
                                accountLabel = account.maskedLabel,
                                unmappedOperationNames = preview.unmappedOperationNames,
                            )
                        }
                        continue
                    }
                    val result = ByteArrayInputStream(bytes).use { input ->
                        StatementImporter(db).import(
                            input = input,
                            fileName = account.fileName(),
                            origin = StatementImportOrigin.CREDO_SYNC,
                        ) { phase ->
                            _state.value = _state.value.copy(currentPhase = phase)
                        }
                    }
                    CredoSyncFileResult(
                        account.maskedLabel,
                        inserted = result.inserted,
                        duplicates = result.duplicates,
                        reconciled = result.reconciled,
                        unmappedOperationNames = result.unmappedOperationNames,
                    )
                } catch (error: CredoSessionExpiredException) {
                    // Сессия умерла посреди прогона: остальные счета не молотим тем же 401,
                    // молчаливый re-login невозможен (нужен OTP) — просим войти заново.
                    results += CredoSyncFileResult(account.maskedLabel, errorCode = "SESSION_EXPIRED")
                    session = null
                    _state.value = _state.value.copy(
                        stage = CredoSyncStage.Disconnected,
                        accounts = emptyList(),
                        currentAccount = 0,
                        currentPhase = null,
                        results = results.toList(),
                        unchanged = unchanged,
                        errorCode = "SESSION_EXPIRED",
                    )
                    return@launch
                } catch (error: Exception) {
                    val code = error.safeCode()
                    if (code in NO_MORE_HISTORY) {
                        // The bank exports nothing for a period an account sat still through. With
                        // a window that can be as short as a month that is ordinary, not a fault.
                        unchanged += 1
                        _state.value = _state.value.copy(unchanged = unchanged)
                        continue
                    }
                    val original = downloadedBytes?.let { bytes ->
                        retainDownloadedStatement(account, bytes)
                    }
                    CredoSyncFileResult(
                        account.maskedLabel,
                        errorCode = code,
                        // Dates only, no amounts: without the window a failure cannot be told apart
                        // from one asked for the wrong period.
                        askedFrom = fromIso.asDate(),
                        askedTo = toIso.asDate(),
                        detail = error.safeDetail(),
                        originalStatementToken = original?.first,
                        originalStatementFileName = original?.second,
                    )
                }
                results += fileResult
            }
            _state.value = _state.value.copy(
                stage = CredoSyncStage.Connected,
                currentAccount = 0,
                currentPhase = null,
                results = results,
                unchanged = unchanged,
                errorCode = null,
            )
        }
    }

    /**
     * Reaches past the year a routine sync covers, one year-long statement at a time.
     *
     * Separate from [sync] on purpose: this is a one-off that walks each account backwards until the
     * statements say there is nothing older, while a sync only asks for what is missing at the near
     * end. Nothing here is automatic — a bank endpoint is not a place to loop unattended.
     */
    fun loadHistory() {
        val activeSession = session ?: return fail("LOGIN_EXPIRED")
        val accounts = _state.value.accounts
        if (accounts.isEmpty()) return fail("NO_ACCOUNTS")
        if (_state.value.stage == CredoSyncStage.Syncing) return
        viewModelScope.launch(syncDispatcher) {
            clearDownloadedStatements()
            val results = mutableListOf<CredoSyncFileResult>()
            var unchanged = 0
            for ((index, account) in accounts.withIndex()) {
                var inserted = 0
                var duplicates = 0
                var reconciled = 0
                val unmappedOperationNames = linkedSetOf<String>()
                var errorCode: String? = null
                var failedWindow: dev.whekin.whfin.data.credo.CredoHistoryChunk? = null
                var failedDetail: String? = null
                var failedOriginal: Pair<String, String>? = null
                var earliest = earliestKnownFor(account) ?: LocalDate.now(zone).plusDays(1)

                for (chunk in 1..CredoHistoryScan.MAX_CHUNKS) {
                    val window = CredoHistoryScan.chunkBefore(earliest)
                    _state.value = _state.value.copy(
                        stage = CredoSyncStage.Syncing,
                        currentAccount = index + 1,
                        currentChunk = chunk,
                        currentPhase = StatementImporter.Phase.READING,
                        results = results.toList(),
                        unchanged = unchanged,
                        errorCode = null,
                    )
                    var downloadedBytes: ByteArray? = null
                    val statement = try {
                        val bytes = downloadWithRetry(
                            activeSession,
                            account,
                            DateTimeFormatter.ISO_INSTANT.format(window.from.atStartOfDay(zone).toInstant()),
                            DateTimeFormatter.ISO_INSTANT.format(window.to.atTime(23, 59, 59).atZone(zone).toInstant()),
                        )
                        downloadedBytes = bytes
                        val preview = ByteArrayInputStream(bytes).use { input ->
                            StatementImporter(db).preview(input, account.fileName())
                        }
                        unmappedOperationNames += preview.unmappedOperationNames
                        if (!preview.changesNothing) {
                            val result = ByteArrayInputStream(bytes).use { input ->
                                StatementImporter(db).import(
                                    input = input,
                                    fileName = account.fileName(),
                                    origin = StatementImportOrigin.CREDO_SYNC,
                                ) { phase -> _state.value = _state.value.copy(currentPhase = phase) }
                            }
                            inserted += result.inserted
                            duplicates += result.duplicates
                            reconciled += result.reconciled
                        }
                        preview.statement
                    } catch (error: CredoSessionExpiredException) {
                        results += CredoSyncFileResult(account.maskedLabel, errorCode = "SESSION_EXPIRED")
                        session = null
                        _state.value = _state.value.copy(
                            stage = CredoSyncStage.Disconnected,
                            accounts = emptyList(),
                            currentAccount = 0,
                            currentChunk = 0,
                            currentPhase = null,
                            results = results.toList(),
                            unchanged = unchanged,
                            errorCode = "SESSION_EXPIRED",
                        )
                        return@launch
                    } catch (error: Exception) {
                        // Walking off the start of an account's life is the normal way this ends,
                        // not a failure: before it existed there is no statement to export, and the
                        // bank says so with an empty one. Anything else stops the walk too, but is
                        // worth reporting — unless this account already got history out of it.
                        val code = error.safeCode()
                        if (code !in NO_MORE_HISTORY) {
                            errorCode = code.takeIf { inserted == 0 && reconciled == 0 }
                            failedWindow = window
                            failedDetail = error.safeDetail()
                            failedOriginal = downloadedBytes?.let { bytes ->
                                retainDownloadedStatement(account, bytes)
                            }
                        }
                        break
                    }
                    if (CredoHistoryScan.reachedBottom(window, statement)) break
                    earliest = statement.periodFrom ?: window.from
                }

                when {
                    errorCode != null -> results += CredoSyncFileResult(
                        account.maskedLabel,
                        errorCode = errorCode,
                        askedFrom = failedWindow?.from?.toString(),
                        askedTo = failedWindow?.to?.toString(),
                        detail = failedDetail,
                        originalStatementToken = failedOriginal?.first,
                        originalStatementFileName = failedOriginal?.second,
                    )
                    inserted == 0 && reconciled == 0 && unmappedOperationNames.isEmpty() -> unchanged += 1
                    else -> results += CredoSyncFileResult(
                        account.maskedLabel,
                        inserted = inserted,
                        duplicates = duplicates,
                        reconciled = reconciled,
                        unmappedOperationNames = unmappedOperationNames,
                        askedFrom = failedWindow?.from?.toString(),
                        askedTo = failedWindow?.to?.toString(),
                        detail = failedDetail,
                        originalStatementToken = failedOriginal?.first,
                        originalStatementFileName = failedOriginal?.second,
                    )
                }
            }
            // Years of foreign rows arrive at once, and each day of them needs its own historical
            // rate. The routine cap would spread that over as many visits to statistics as it takes,
            // so this path sees it through while the user is still here to watch.
            runCatching {
                TransactionValuationRepository(db, NbgHistoricalRateProvider()).backfillAll { pass ->
                    _state.value = _state.value.copy(valuedDays = pass.daysFetched)
                }
            }

            _state.value = _state.value.copy(
                stage = CredoSyncStage.Connected,
                currentAccount = 0,
                currentChunk = 0,
                currentPhase = null,
                valuedDays = 0,
                results = results,
                unchanged = unchanged,
                errorCode = null,
            )
        }
    }

    /** The date part of a request instant, for showing which window a failure was asked for. */
    private fun String.asDate(): String =
        runCatching { java.time.Instant.parse(this).atZone(zone).toLocalDate().toString() }
            .getOrDefault(this)

    /** Where this account's history already begins, if any of it is held. */
    private suspend fun earliestKnownFor(account: CredoRemoteAccount): LocalDate? {
        val ledger = db.accountDao().byIbanAndCurrency(account.accountNumber, account.currency) ?: return null
        return db.statementImportDao().forAccount(ledger.id)
            .mapNotNull(StatementImportEntity::periodFrom)
            .minOrNull()
            ?.let(LocalDate::ofEpochDay)
    }

    /**
     * Скачивание с ограниченными повторами: только на transient сетевых сбоях/5xx.
     * HTTP 403/429 (защита сайта) не ретраим, чтобы не выглядеть ботом; истёкшая
     * авторизация конвертируется в [CredoSessionExpiredException] и прерывает sync.
     */
    private suspend fun downloadWithRetry(
        session: CredoSession,
        account: CredoRemoteAccount,
        fromIso: String,
        toIso: String,
    ): ByteArray {
        var attempt = 0
        while (true) {
            try {
                return gateway.downloadStatement(session, account, fromIso, toIso)
            } catch (error: CredoApiException) {
                when {
                    error.code.isCredoAuthError() -> throw CredoSessionExpiredException(error)
                    error.code.isCredoTransientError() && attempt < retryDelayMillis.size -> {
                        kotlinx.coroutines.delay(retryDelayMillis[attempt])
                        attempt += 1
                    }
                    else -> throw error
                }
            }
        }
    }

    fun disconnect() {
        secretStore.clear()
        challenge = null
        pendingCredentials = null
        session = null
        clearDownloadedStatements()
        loginDraft.username = ""
        loginDraft.credential = ""
        _state.value = CredoSyncUiState()
    }

    /** App Lock is the product gate for persisted bank credentials; without it, forget them. */
    fun forgetSavedCredentials() {
        viewModelScope.launch(Dispatchers.IO) {
            secretStore.clear()
            _state.value = _state.value.copy(savedUsername = null, hasSavedPassword = false)
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(errorCode = null)
    }

    /** Copies an explicitly selected failed download; the bytes never leave process memory otherwise. */
    internal fun writeDownloadedStatement(token: String, output: OutputStream): Boolean {
        val bytes = synchronized(downloadedStatements) { downloadedStatements[token] } ?: return false
        output.write(bytes)
        return true
    }

    private fun clearDownloadedStatements() = synchronized(downloadedStatements) {
        downloadedStatements.clear()
    }

    private fun retainDownloadedStatement(
        account: CredoRemoteAccount,
        bytes: ByteArray,
    ): Pair<String, String> {
        val token = UUID.randomUUID().toString()
        val fileName = account.fileName()
        synchronized(downloadedStatements) { downloadedStatements[token] = bytes }
        return token to fileName
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
            loginDraft.username = credentials.username
            loginDraft.credential = ""
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

    /**
     * A code the screen can explain, never a raw message.
     *
     * A statement the bank sent but WHFIN could not read used to land here as `UNKNOWN_ERROR`,
     * which told the user nothing and left no way to tell a parser problem from a broken balance
     * chain. The file import has always named these; sync now does too.
     */
    private fun Throwable.safeCode(): String = when (this) {
        is CredoApiException -> code
        is UnsupportedStatementException -> "UNSUPPORTED_STATEMENT"
        // Distinct from the gateway's INVALID_STATEMENT, which means the download itself would not
        // decode. These two mean the bytes arrived and our own reading of them refused.
        is MalformedStatementException -> "STATEMENT_UNREADABLE"
        is InvalidStatementException -> "STATEMENT_REJECTED"
        is AmbiguousBankLedgerException -> "AMBIGUOUS_LEDGER"
        else -> "UNKNOWN_ERROR"
    }

    /**
     * The rule that refused a statement, when the refusal was ours.
     *
     * These messages are written by WHFIN and name a rule and a row number — "balance chain breaks
     * at row 12" — never a value out of the file. A server message is never repeated: it is not ours
     * to trust or to show.
     */
    private fun Throwable.safeDetail(): String? = when (this) {
        is MalformedStatementException, is InvalidStatementException -> message
        else -> null
    }

    /**
     * The window is decided per account, because coverage is.
     *
     * One account synced yesterday and another added today have nothing in common; a single range
     * for the whole run would make the first re-read a year to insert nothing.
     */
    private suspend fun statementRangeFor(account: CredoRemoteAccount): Pair<String, String> {
        val now = ZonedDateTime.now(zone)
        val ledger = db.accountDao().byIbanAndCurrency(account.accountNumber, account.currency)
        val imports = ledger?.let { db.statementImportDao().forAccount(it.id) }.orEmpty()
        return credoStatementRange(now, CredoSyncWindow.startFor(now, imports))
    }

    private fun CredoRemoteAccount.fileName(): String =
        "mycredo_${currency.lowercase()}_${accountNumber.takeLast(4)}.xlsx"

    private companion object {
        const val OTP_LENGTH = 4
        val TERMINAL_LOGIN_ERRORS = setOf("UNAUTHORIZED", "LOGIN_EXPIRED", "USER_IS_BLOCKED", "USER_OTP_BLOCKED")

        /** How the far end of an account's history announces itself: there is nothing to export. */
        val NO_MORE_HISTORY = setOf("EMPTY_STATEMENT")
        val DEFAULT_RETRY_DELAYS = listOf(2_000L, 5_000L)
    }
}

/** Авторизация протухла в середине sync — весь прогон останавливается. */
internal class CredoSessionExpiredException(cause: Throwable) : Exception(cause)

internal fun String.isCredoAuthError(): Boolean =
    this == "HTTP_401" || this == "UNAUTHORIZED" || this == "AUTH_NOT_AUTHENTICATED" || this == "AUTH_NOT_AUTHORIZED"

internal fun String.isCredoTransientError(): Boolean =
    this == "NETWORK_ERROR" || this == "HTTP_500" || this == "HTTP_502" || this == "HTTP_503" || this == "HTTP_504"

internal fun credoStatementRange(now: ZonedDateTime, from: ZonedDateTime): Pair<String, String> =
    DateTimeFormatter.ISO_INSTANT.format(from.toInstant()) to
        DateTimeFormatter.ISO_INSTANT.format(now.toInstant())
