package dev.whekin.whfin.ui.settings

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.whekin.whfin.data.tbc.TbcAccount
import dev.whekin.whfin.ui.SecondaryPage
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.util.Locale

/** Synthetic, non-exported form host. No gateway, session store, credentials or ledger access. */
class TbcLoginQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", false)
        val language = intent.getStringExtra("language") ?: "en"
        val config = Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val localized = createConfigurationContext(config)
        val font = intent.getFloatExtra("fontScale", 1f)
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        val accounts = listOf(TbcAccount(1, "GE00TB0000000000000001", "GEL", "Everyday"))
        val initial = TbcLoginState(
            stage = TbcLoginStage.valueOf(intent.getStringExtra("stage") ?: "Login"),
            accounts = accounts, error = intent.getStringExtra("error"),
            syncResult = if (intent.getStringExtra("stage") == "Connected") {
                if (intent.getBooleanExtra("initial", false)) dev.whekin.whfin.data.importer.TbcSyncResult(needsStatement = listOf(
                    dev.whekin.whfin.data.tbc.TbcLedgerAccount("10", "GE00TB0000000000000001", "GEL", "Everyday")))
                else dev.whekin.whfin.data.importer.TbcSyncResult(inserted = 3, matched = 4, unchanged = 1)
            } else null,
        )
        setContent {
            var state by remember { mutableStateOf(initial) }
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, font)) {
                WhfinTheme(darkTheme = dark) {
                    androidx.compose.material3.Surface {
                    SecondaryPage(if (language == "ru") "Подключение TBC" else "TBC connection", {}) {
                        TbcLoginScreen(state, canStoreSession = true,
                            onLogin = { _, _ -> state = state.copy(stage = TbcLoginStage.Code) },
                            onCode = { state = state.copy(stage = TbcLoginStage.Connected) },
                            onRemember = { state = state.copy(remember = it) })
                    }
                    }
                }
            }
        }
    }
}
