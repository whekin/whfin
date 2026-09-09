package dev.whekin.whfin.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.whekin.whfin.data.security.*
import dev.whekin.whfin.data.tbc.*
import dev.whekin.whfin.ui.SecondaryPage
import dev.whekin.whfin.ui.theme.WhfinTheme

/** Production route/receiver, synthetic bank transport; no real credentials or ledger writes. */
class TbcOtpQaActivity : ComponentActivity() {
    var confirmationCalls = 0
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        enableEdgeToEdge()
        val fake = object : TbcGateway {
            override suspend fun login(username: String, credential: String): TbcLoginResult =
                TbcLoginResult.Challenge(TbcChallenge("synthetic", "NONE", "SMS_OTP"))
            override suspend fun confirm(challenge: TbcChallenge, code: String): TbcSession {
                confirmationCalls++
                if (code != "246810") throw TbcException("OTP")
                return snapshot()
            }
            override suspend fun resume(session: TbcSession) = session
            override suspend fun accounts() = listOf(TbcAccount(1, "GE00TB0000000000000001", "GEL", "Example"))
            override fun clear() = Unit
            override fun snapshot() = TbcSession(mapOf("session" to "synthetic"), "synthetic")
        }
        val sessionStore = object : BankSessionStore {
            override fun hasSaved() = false
            override fun load(): String? = null
            override fun save(value: String) = Unit
            override fun clear() = Unit
        }
        val credentials = object : BankCredentialStore {
            override fun hasCredentials() = false
            override fun load(): BankCredentials? = null
            override fun save(credentials: BankCredentials) = Unit
            override fun clear() = Unit
        }
        val choice = object : BankSessionChoice {
            override fun read() = false
            override fun write(remember: Boolean) = Unit
        }
        val vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TbcLoginViewModel(application, { fake }, sessionStore, rememberChoice = choice, credentialStore = credentials) as T
        })[TbcLoginViewModel::class.java]
        setContent { WhfinTheme { androidx.compose.material3.Surface {
            SecondaryPage("TBC OTP QA", {}) { TbcLoginRoute(canStoreSession = false, demoMode = false, viewModelOverride = vm) }
        } } }
    }
}
