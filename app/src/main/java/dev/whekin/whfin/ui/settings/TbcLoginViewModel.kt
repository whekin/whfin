package dev.whekin.whfin.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.whekin.whfin.data.security.BankSessionStore
import dev.whekin.whfin.data.security.EncryptedBankSessionStore
import dev.whekin.whfin.data.tbc.*
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
    val remember: Boolean = false,
    val otpApp: Boolean = false,
    val accounts: List<TbcAccount> = emptyList(),
    val error: String? = null,
)
class TbcLoginViewModel internal constructor(
    app: Application,
    private val factory: () -> TbcGateway,
    private val store: BankSessionStore,
) : AndroidViewModel(app) {
    constructor(app: Application) : this(app, { MobileTbcGateway() }, EncryptedBankSessionStore(app, "tbc"))
    private val mutable = MutableStateFlow(TbcLoginState(hasSaved = store.hasSaved()))
    val state = mutable.asStateFlow()
    private var gateway: TbcGateway? = null
    private var challenge: TbcChallenge? = null
    private var session: TbcSession? = null
    private var work: Job? = null
    private var canRemember = false

    fun storageAllowed(allowed: Boolean) {
        canRemember = allowed
        if (!allowed) {
            store.clear()
            mutable.value = mutable.value.copy(hasSaved = false, remember = false)
        }
    }
    fun setRemember(value: Boolean) {
        mutable.value = mutable.value.copy(remember = value && canRemember)
    }
    fun login(username: String, credential: String) {
        if (username.isBlank() || credential.isEmpty()) return
        run(TbcLoginStage.Login) {
            val client = factory().also { gateway?.clear(); gateway = it }
            when (val result = client.login(username.trim(), credential)) {
                is TbcLoginResult.Challenge -> {
                    challenge = result.value
                    mutable.value = mutable.value.copy(stage = TbcLoginStage.Code, otpApp = result.value.type == "TOKEN_VASCO")
                }
                is TbcLoginResult.Authenticated -> connected(result.session)
            }
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
            val saved = withContext(Dispatchers.IO) { store.load() } ?: throw TbcException("SESSION")
            val client = factory().also { gateway?.clear(); gateway = it }
            mutable.value = mutable.value.copy(remember = true)
            connected(client.resume(TbcSession.decode(saved)))
        }
    }
    fun refreshAccounts() {
        if (session == null) return
        run(TbcLoginStage.Connected) { connected(requireNotNull(session)) }
    }
    private suspend fun connected(value: TbcSession) {
        session = value
        challenge = null
        // Account fetch must succeed before showing Connected. It never writes the ledger.
        val accounts = requireNotNull(gateway).accounts()
        session = requireNotNull(gateway).snapshot()
        val remember = mutable.value.remember && canRemember
        withContext(Dispatchers.IO) {
            if (remember) store.save(requireNotNull(session).encode()) else store.clear()
        }
        mutable.value = mutable.value.copy(stage = TbcLoginStage.Connected, accounts = accounts, hasSaved = store.hasSaved(), error = null)
    }
    private fun run(failureStage: TbcLoginStage, block: suspend () -> Unit) {
        if ((getApplication<Application>() as? dev.whekin.whfin.WhfinApp)?.isDemoMode == true) return
        if (work?.isActive == true) return
        mutable.value = mutable.value.copy(stage = TbcLoginStage.Working, error = null)
        work = viewModelScope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                val code = (error as? TbcException)?.code ?: "RESPONSE"
                val expired = code == "SESSION"
                if (expired) { gateway?.clear(); session = null; challenge = null; store.clear() }
                mutable.value = mutable.value.copy(stage = if (expired) TbcLoginStage.Login else if (session != null) TbcLoginStage.Connected else failureStage,
                    hasSaved = store.hasSaved(), error = code)
            }
        }
    }
    fun leave() {
        work?.cancel(); work = null
        gateway?.clear(); gateway = null; session = null; challenge = null
        mutable.value = TbcLoginState(hasSaved = store.hasSaved())
    }
    fun forget() { leave(); store.clear(); mutable.value = TbcLoginState() }
    override fun onCleared() { gateway?.clear() }
}
