package dev.whekin.whfin.ui.settings

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.*
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.ui.SecondaryPage
import dev.whekin.whfin.ui.theme.WhfinTheme
import java.util.Locale

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class SettingsQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", false)
        val language = intent.getStringExtra("language") ?: "en"
        val config = Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val localized = createConfigurationContext(config)
        val bars = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalResources provides localized.resources,
                LocalConfiguration provides config) {
                val search = rememberSettingsSearchState()
                WhfinTheme(darkTheme = dark) {
                    androidx.compose.material3.Surface {
                    Box(Modifier.fillMaxSize().semantics { contentDescription = "settings-qa-ready"; testTagsAsResourceId = true }) {
                    SecondaryPage(if (language == "ru") "Настройки" else "Settings", {}, actions = { SettingsSearchAction(search) }) {
                        SettingsContent(searchState = search, smsImportEnabled = false, hasSmsPermission = true,
                            canRequestSmsPermission = true, onSmsImportEnabledChange = {}, onRequestSmsPermission = {},
                            onOpenSystemSettings = {}, onOpenStatements = {}, onOpenSmsDiagnostics = {}, appLockTimeout = AppLockTimeout.Disabled,
                            onOpenAppLock = {}, onOpenBackup = {}, onOpenPrivacy = {}, onOpenAbout = {}, appVersion = "Synthetic")
                    }
                    }
                    }
                }
            }
        }
    }
}
