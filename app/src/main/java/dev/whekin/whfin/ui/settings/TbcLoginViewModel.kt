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
class TbcLoginViewModel internal constructor(
    app: Application,
    private val factory: () -> TbcGateway,
    private val store: BankSessionStore,
    private val synchronize: (suspend (TbcGateway, (Int, Int) -> Unit) -> dev.whekin.whfin.data.importer.TbcSyncResult)? = null,
    private val rememberChoice: dev.whekin.whfin.data.security.BankSessionChoice =
        dev.whekin.whfin.data.security.DeviceBankSessionChoice(app, "tbc"),
    private val credentialStore: BankCredentialStore = EncryptedBankCredentialStore(app, "tbc"),
) : AndroidViewModel(app) {
    constructor(app: Application) : this(app, { MobileTbcGateway() }, EncryptedBankSessionStore(app, "tbc"),
        { gateway, progress -> dev.whekin.whfin.data.importer.TbcHistorySync((app as dev.whekin.whfin.WhfinApp).db)
            .sync(gateway, progress = progress) })
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

    fun confirmBalance(key: String, amountMinor: Long) {
        val previous = mutable.value.syncResult ?: return
        val initial = previous.initialHistories.singleOrNull { it.remote.key == key } ?: return
        run(TbcLoginStage.Connected) {
            val app = getApplication<Application>() as dev.whekin.whfin.WhfinApp
            val plan = withContext(Dispatchers.IO) { dev.whekin.whfin.data.importer.TbcHistorySync(app.db).initialize(initial, amountMinor) }
            val result = previous.copy(inserted = previous.inserted + plan.inserted, matched = previous.matched + plan.reconciled,
                needsStatement = previous.needsStatement.filterNot { it.key == key },
                initialHistories = previous.initialHistories.filterNot { it.remote.key == key })
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
        val client = requireNotNull(gateway)
        val result = synchronize?.invoke(client) { current, total ->
            mutable.value = mutable.value.copy(syncProgress = current to total)
        }
        if (result != null && result.errors.isEmpty() && result.needsStatement.isEmpty()) {
            dev.whekin.whfin.data.preferences.UiPreferences(getApplication<Application>()).setLastTbcSyncAt(System.currentTimeMillis())
        }
        persistSession()
        mutable.value = mutable.value.copy(stage = TbcLoginStage.Connected, syncProgress = null, syncResult = result)
        if (result != null && result.inserted > 0) {
            val app = getApplication<Application>() as? dev.whekin.whfin.WhfinApp
            if (app != null) viewModelScope.launch(Dispatchers.IO) {
                runCatching { dev.whekin.whfin.data.rates.TransactionValuationRepository(app.db,
                    dev.whekin.whfin.data.rates.NbgHistoricalRateProvider()).backfill() }
            }
        }
    }
    private fun run(failureStage: TbcLoginStage, block: suspend () -> Unit) {
        if ((getApplication<Application>() as? dev.whekin.whfin.WhfinApp)?.isDemoMode == true) return
        if (work?.isActive == true) return
        mutable.value = mutable.value.copy(stage = TbcLoginStage.Working, error = null, syncResult = null, syncProgress = null)
        work = viewModelScope.launch {
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
    fun leave() {
        work?.cancel(); work = null
        gateway?.clear(); gateway = null; session = null; challenge = null
        pendingCredentials = null; usingSavedCredentials = false
        mutable.value = loginState()
    }
    fun forget() { leave(); store.clear(); credentialStore.clear(); rememberChoice.write(false); rememberChoice.reportProblem(null); mutable.value = TbcLoginState() }
    override fun onCleared() { gateway?.clear(); pendingCredentials = null }
}
