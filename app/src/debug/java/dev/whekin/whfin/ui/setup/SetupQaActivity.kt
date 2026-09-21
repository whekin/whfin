package dev.whekin.whfin.ui.setup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import dev.whekin.whfin.WhfinApp
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.security.BiometricAvailability
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.util.Locale

/** Runs the real setup routes against the disposable debug workspace. Never signs into a bank. */
class SetupQaActivity : ComponentActivity() {
    override fun attachBaseContext(base: android.content.Context) {
        val configuration = android.content.res.Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        configuration.fontScale = fontScale
        super.attachBaseContext(base.createConfigurationContext(configuration))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        if (savedInstanceState == null) (application as WhfinApp).runtimeModes.personalSetupStage = null
        enableEdgeToEdge()
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !dark
        setContent {
            var complete by remember { mutableStateOf(false) }
            WhfinTheme(darkTheme = dark) {
                if (complete) Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) { Text("Setup finished") } else PersonalSetupFlow(
                    state = PersonalSetupState(accountCount = 0,
                        unresolvedSmsCount = 0, statementReviewCount = 0),
                    appVersion = "QA", appLockTimeout = AppLockTimeout.Disabled, appLockHasPin = false,
                    biometricAvailability = BiometricAvailability.Unsupported, biometricUnlockEnabled = false,
                    hasSmsHistoryPermission = false, canRequestSmsHistoryPermission = true,
                    onEnableSmsMonitoring = {}, onRequestSmsPermission = {}, onRequestSmsHistoryPermission = {},
                    onOpenSystemSettings = {}, onAppLockTimeoutChange = {}, onAppLockPinCreated = { _, _ -> },
                    onBiometricUnlockEnabledChange = {}, onOpenBiometricSettings = {},
                    onContinue = { _, _ -> complete = true }, onExit = ::finish,
                )
            }
        }
    }
    companion object { var language = "en"; var fontScale = 1f; var dark = false }
}
