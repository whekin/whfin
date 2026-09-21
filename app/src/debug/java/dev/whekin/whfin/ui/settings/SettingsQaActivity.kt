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
    var openedAccount: Long? = null
    var syncedBank: dev.whekin.whfin.data.sms.BankSmsBank? = null
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
                androidx.activity.compose.BackHandler(search.page.isNotBlank() || search.query.isNotBlank() || search.searchVisible) { search.back() }
                var themeMode by remember { mutableStateOf(dev.whekin.whfin.data.preferences.AppThemeMode.System) }
                val actualDark = when (themeMode) {
                    dev.whekin.whfin.data.preferences.AppThemeMode.System -> dark
                    dev.whekin.whfin.data.preferences.AppThemeMode.Light -> false
                    dev.whekin.whfin.data.preferences.AppThemeMode.Dark -> true
                }
                SideEffect {
                    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !actualDark
                        isAppearanceLightNavigationBars = !actualDark
                    }
                }
                WhfinTheme(darkTheme = actualDark) {
                    androidx.compose.material3.Surface {
                    Box(Modifier.fillMaxSize().semantics { contentDescription = "settings-qa-ready"; testTagsAsResourceId = true }) {
                    SettingsPage(search, { search.back() }) {
                        SettingsContent(searchState = search, searchInHeader = true,
                            appThemeMode = themeMode, onAppThemeModeChange = { themeMode = it },
                            connections = ConnectionSettingsState(accounts = dev.whekin.whfin.data.sms.BankSmsBank.entries.associateWith { bank ->
                                    if (bank == dev.whekin.whfin.data.sms.BankSmsBank.TBC) listOf("GEL", "USD", "EUR", "GBP").mapIndexed { index, currency ->
                                        dev.whekin.whfin.data.db.AccountEntity(id = index + 1L, name = "Everyday", type = dev.whekin.whfin.data.db.AccountType.BANK,
                                            currency = currency, iban = "GE00TB0000000000000001")
                                    } else emptyList()
                                },
                                lastSync = dev.whekin.whfin.data.sms.BankSmsBank.entries.associateWith { System.currentTimeMillis() },
                                remembered = dev.whekin.whfin.data.sms.BankSmsBank.entries.toSet(), pushEnabled = true, pushPermission = true, pushConnected = true), smsImportEnabled = false, hasSmsPermission = true,
                            connectionActions = ConnectionSettingsActions(sync = { syncedBank = it }, login = {}, sms = { _, _ -> }, push = {},
                                smsPermission = {}, pushPermission = {}, messages = {}, statements = {}, journal = {}, account = { openedAccount = it }),
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
