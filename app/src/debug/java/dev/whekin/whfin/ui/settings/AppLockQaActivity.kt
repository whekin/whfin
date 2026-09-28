package dev.whekin.whfin.ui.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.security.BiometricAvailability
import dev.whekin.whfin.data.security.PinVerificationResult
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.util.Locale

/** Synthetic PIN host for layout QA; it never opens or changes the app credential store. */
class AppLockQaActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            fontScale = scale
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            WhfinTheme(darkTheme = dark) {
                Surface {
                    if (gate) AppLockGate(
                        biometricAvailable = false,
                        problem = null,
                        onVerifyPin = { PinVerificationResult.Invalid(2) },
                        onBiometric = {},
                    ) else AppLockScreen(
                        timeout = AppLockTimeout.Immediate,
                        hasPin = false,
                        biometricAvailability = BiometricAvailability.Unsupported,
                        biometricEnabled = false,
                        onTimeoutChange = {},
                        onPinCreated = { _, _ -> },
                        onBiometricEnabledChange = {},
                        onOpenBiometricSettings = {},
                        autoSetupTimeout = AppLockTimeout.Immediate,
                    )
                }
            }
        }
    }

    companion object {
        var language = "en"
        var scale = 1f
        var dark = false
        var gate = false
    }
}
