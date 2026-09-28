package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.data.security.BankSessionStore
import dev.whekin.whfin.data.security.EncryptedBankSessionStore
import dev.whekin.whfin.data.tbc.*
import dev.whekin.whfin.data.security.BankCredentials
import dev.whekin.whfin.data.security.BankCredentialStore
import dev.whekin.whfin.data.security.EncryptedBankCredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TbcLoginStage { Login, Working, Code, Connected }
data class TbcLoginState(
    val stage: TbcLoginStage = TbcLoginStage.Login,
    val hasSaved: Boolean = false,
    val hasSavedCredentials: Boolean = false,
    val sessionVerified: Boolean = false,
    val remember: Boolean = false,
    val otpApp: Boolean = false,
    val accounts: List<TbcAccount> = emptyList(),
    val error: String? = null,
    val syncResult: dev.whekin.whfin.data.importer.TbcSyncResult? = null,
    val syncProgress: Pair<Int, Int>? = null,
)
/**
 * What the result page says once an owner-entered balance has been applied.
 *
 * The account stops asking and starts reporting: it leaves the attention section and takes its place
 * among the accounts with what the import actually did. Dropping it from both — which is what
 * removing it from `needsStatement` alone used to do — made a finished import look like nothing had
 * happened, and the only visible thing left to try was running the sync again.
 */
internal fun dev.whekin.whfin.data.importer.TbcSyncResult.afterInitialBalance(
    remote: dev.whekin.whfin.data.tbc.TbcLedgerAccount,
    plan: dev.whekin.whfin.data.importer.TbcInitializationResult,
) = copy(
    inserted = inserted + plan.inserted,
    matched = matched + plan.reconciled,
    needsStatement = needsStatement.filterNot { it.key == remote.key },
    initialHistories = initialHistories.filterNot { it.remote.key == remote.key },
    reports = reports.map {
        if (it.label != remote.label) it
        else it.copy(waitingForBalance = false, inserted = plan.inserted, matched = plan.reconciled)
    },
)

class TbcLoginViewModel internal constructor(
    app: Application,
    private val factory: () -> TbcGateway,
    private val store: BankSessionStore,
    private val synchronize: (suspend (TbcGateway, (Int, Int) -> Unit) -> dev.whekin.whfin.data.importer.TbcSyncResult)? = null,
    private val rememberChoice: dev.whekin.whfin.data.security.BankSessionChoice =
        dev.whekin.whfin.data.security.DeviceBankSessionChoice(app, "tbc"),
    private val credentialStore: BankCredentialStore = EncryptedBankCredentialStore(app, "tbc"),
    private val backgroundExecution: Boolean = false,
) : AndroidViewModel(app) {
    constructor(app: Application) : this(app, { MobileTbcGateway() }, EncryptedBankSessionStore(app, "tbc"),
        { gateway, progress -> dev.whekin.whfin.data.importer.TbcHistorySync((app as dev.whekin.whfin.WhfinApp).userDb)
            .sync(gateway, progress = progress) }, backgroundExecution = true)
    private fun hasSavedSignIn() = store.hasSaved() || credentialStore.hasCredentials()
    private fun loginState() = TbcLoginState(hasSaved = hasSavedSignIn(), hasSavedCredentials = credentialStore.hasCredentials(),
        remember = rememberChoice.read() ?: hasSavedSignIn(), error = rememberChoice.problem())
    private val mutable = MutableStateFlow(loginState())
    val state = mutable.asStateFlow()
    private var gateway: TbcGateway? = null
    private var challenge: TbcChallenge? = null
    private var session: TbcSession? = null
    private var work: Job? = null
    private var canRemember = false
    private var pendingCredentials: BankCredentials? = null
    private var usingSavedCredentials = false

    fun storageAllowed(allowed: Boolean) {
        canRemember = allowed
        if (!allowed) {
            store.clear(); credentialStore.clear()
            rememberChoice.write(false)
            mutable.value = mutable.value.copy(hasSaved = false, hasSavedCredentials = false, remember = false)
        }
    }
    fun setRemember(value: Boolean) {
        val remember = value && canRemember
        rememberChoice.write(remember)
        if (!remember) { store.clear(); credentialStore.clear() }
        mutable.value = mutable.value.copy(remember = remember, hasSaved = hasSavedSignIn(), hasSavedCredentials = credentialStore.hasCredentials())
    }
    fun login(username: String, credential: String) {
        if (username.isBlank() || credential.isEmpty()) return
        run(TbcLoginStage.Login) { passwordLogin(BankCredentials(username.trim(), credential), fromSaved = false) }
    }
    private suspend fun passwordLogin(credentials: BankCredentials, fromSaved: Boolean) {
        mutable.value = mutable.value.copy(sessionVerified = false)
        usingSavedCredentials = fromSaved
        pendingCredentials = credentials
        session = null; challenge = null
        val client = factory().also { gateway?.clear(); gateway = it }
        when (val result = client.login(credentials.username, credentials.credential)) {
            is TbcLoginResult.Challenge -> {
                challenge = result.value
                mutable.value = mutable.value.copy(stage = TbcLoginStage.Code, otpApp = result.value.type == "TOKEN_VASCO")
            }
            is TbcLoginResult.Authenticated -> connected(result.session)
        }
    }
    fun confirm(code: String) {
        val pending = challenge ?: return
        if (!code.matches(Regex("[0-9]{4,8}"))) return
        run(TbcLoginStage.Code) { connected(requireNotNull(gateway).confirm(pending, code)) }
    }
    /** Only called after the sensitive-action gate succeeds. */
    fun restore() {
        if (!canRemember) return
        run(TbcLoginStage.Login) {
            rememberChoice.write(true)
            mutable.value = mutable.value.copy(remember = true)
            val saved = withContext(Dispatchers.IO) { store.load() }
            val resumed = if (saved != null) {
                val client = factory().also { gateway?.clear(); gateway = it }
                try { client.resume(TbcSession.decode(saved)) }
                catch (error: TbcException) {
                    if (error.code != "SESSION") throw error
                    store.clear(); client.clear(); session = null
                    null
                }
            } else null
            if (resumed != null) connected(resumed)
            else {
                val credentials = withContext(Dispatchers.IO) { credentialStore.load() } ?: throw TbcException("SESSION")
                passwordLogin(credentials, fromSaved = true)
            }
        }
    }
    fun refreshAccounts() {
        if (session == null) return
        run(TbcLoginStage.Connected) { connected(requireNotNull(session)) }
    }
    private suspend fun connected(value: TbcSession) {
        mutable.value = mutable.value.copy(sessionVerified = true)
        session = value
        challenge = null
        // Persist verified authentication before any unrelated account/history request can fail.
        persistSession()
        val accounts = requireNotNull(gateway).accounts()
        persistSession() // Account responses can rotate the cookie too.
        mutable.value = mutable.value.copy(accounts = accounts, hasSaved = store.hasSaved(), error = null)
        syncCurrent()
    }

    private suspend fun persistSession() {
        session = requireNotNull(gateway).snapshot()
        val remember = mutable.value.remember && canRemember
        rememberChoice.write(remember)
        withContext(Dispatchers.IO) {
            try {
                if (remember) {
                    pendingCredentials?.let(credentialStore::save)
                    store.save(requireNotNull(session).encode())
                } else { store.clear(); credentialStore.clear() }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { throw TbcException("STORAGE") }
        }
        pendingCredentials = null; usingSavedCredentials = false
        rememberChoice.reportProblem(null)
        mutable.value = mutable.value.copy(hasSaved = hasSavedSignIn(), hasSavedCredentials = credentialStore.hasCredentials())
    }

    fun confirmBalances(balances: List<Pair<String, Long>>) {
        val previous = mutable.value.syncResult ?: return
        if (balances.isEmpty() || balances.map { it.first }.distinct().size != balances.size) return
        if (balances.map { it.first }.toSet() != previous.needsStatement.map { it.key }.toSet()) return
        val initials = balances.map { (key, amount) ->
            (previous.initialHistories.singleOrNull { it.remote.key == key } ?: return) to amount
        }
        run(TbcLoginStage.Connected, preserveSyncResult = true) {
            val app = getApplication<Application>() as dev.whekin.whfin.WhfinApp
            val plans = withContext(Dispatchers.IO) {
                dev.whekin.whfin.data.importer.TbcHistorySync(app.userDb).initializeBatch(initials)
            }
            val result = initials.zip(plans).fold(previous) { current, (entry, plan) ->
                current.afterInitialBalance(entry.first.remote, plan)
            }
            mutable.value = mutable.value.copy(stage = TbcLoginStage.Connected, syncResult = result)
            if (result.needsStatement.isEmpty() && result.errors.isEmpty())
                dev.whekin.whfin.data.preferences.UiPreferences(app).setLastTbcSyncAt(System.currentTimeMillis())
        }
    }

    fun syncTransactions() {
        val verified = session ?: return
        run(TbcLoginStage.Connected) {
            if (mutable.value.accounts.isEmpty()) connected(verified) else syncCurrent()
        }
    }

    private suspend fun syncCurrent() {
        if (backgroundExecution) getApplication<dev.whekin.whfin.WhfinApp>().bankSync.markDataStarted("TBC")
        val client = requireNotNull(gateway)
        val result = synchronize?.invoke(client) { current, total ->
            mutable.value = mutable.value.copy(syncProgress = current to total)
        }
        if (result != null && result.errors.isEmpty() && result.needsStatement.isEmpty()) {
            dev.whekin.whfin.data.preferences.UiPreferences(getApplication<Application>()).setLastTbcSyncAt(System.currentTimeMillis())
        }
        persistSession()
        if (result != null && result.inserted > 0) {
            val app = getApplication<Application>() as? dev.whekin.whfin.WhfinApp
            if (app != null) withContext(Dispatchers.IO) {
                try { dev.whekin.whfin.data.rates.TransactionValuationRepository(app.userDb,
                    dev.whekin.whfin.data.rates.NbgHistoricalRateProvider()).backfill() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Valuation remains best effort; bank rows are already imported. */ }
            }
        }
        mutable.value = mutable.value.copy(stage = TbcLoginStage.Connected, syncProgress = null, syncResult = result)
    }
    private fun run(failureStage: TbcLoginStage, preserveSyncResult: Boolean = false, block: suspend () -> Unit) {
        if ((getApplication<Application>() as? dev.whekin.whfin.WhfinApp)?.isDemoMode == true) return
        if (work?.isActive == true) return
        mutable.value = mutable.value.copy(stage = TbcLoginStage.Working, error = null,
            syncResult = if (preserveSyncResult) mutable.value.syncResult else null, syncProgress = null)
        work = launchBankWork {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                val code = (error as? TbcException)?.code ?: "RESPONSE"
                val expired = code == "SESSION"
                if (code == "LOGIN" && usingSavedCredentials) credentialStore.clear()
                if (code == "LOGIN" || expired) { pendingCredentials = null; usingSavedCredentials = false }
                if (expired) { gateway?.clear(); session = null; challenge = null; store.clear(); rememberChoice.reportProblem("SESSION") }
                if (code == "STORAGE") rememberChoice.reportProblem("STORAGE")
                mutable.value = mutable.value.copy(stage = if (expired) TbcLoginStage.Login else if (session != null) TbcLoginStage.Connected else failureStage,
                    hasSaved = hasSavedSignIn(), hasSavedCredentials = credentialStore.hasCredentials(), sessionVerified = session != null, error = code, syncProgress = null)
            }
        }
    }
    private fun launchBankWork(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit): Job =
        if (backgroundExecution) getApplication<dev.whekin.whfin.WhfinApp>().bankSync.launch("TBC", Dispatchers.Main.immediate, {
            if (mutable.value.stage == TbcLoginStage.Working)
                {
                challenge = null; pendingCredentials = null; usingSavedCredentials = false
                mutable.value = mutable.value.copy(stage = if (session != null) TbcLoginStage.Connected else TbcLoginStage.Login,
                    error = "SYNC_INTERRUPTED", syncProgress = null)
                }
        }, block) else viewModelScope.launch(block = block)
    fun leave() {
        work?.cancel(); work = null
        gateway?.clear(); gateway = null; session = null; challenge = null
        pendingCredentials = null; usingSavedCredentials = false
        mutable.value = loginState()
    }
    fun forget() { leave(); store.clear(); credentialStore.clear(); rememberChoice.write(false); rememberChoice.reportProblem(null); mutable.value = TbcLoginState() }
    override fun onCleared() { gateway?.clear(); pendingCredentials = null }
}
