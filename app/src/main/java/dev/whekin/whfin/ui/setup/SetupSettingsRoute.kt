package dev.whekin.whfin.ui.setup

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.preferences.AppThemeMode
import dev.whekin.whfin.data.preferences.UiPreferences
import dev.whekin.whfin.data.sms.BankSmsBank
import dev.whekin.whfin.ui.settings.SettingsScreen
import dev.whekin.whfin.ui.settings.SettingsSearchState
import kotlinx.coroutines.launch

/** Shares the actual settings controls and persistence; setup owns only their navigation. */
@Composable
internal fun SetupSettingsRoute(
    navigation: SettingsSearchState,
    state: PersonalSetupState,
    appVersion: String,
    timeout: AppLockTimeout,
    hasPin: Boolean,
    onRequestSms: () -> Unit,
    onSystemSettings: () -> Unit,
    onOpen: (SetupPage) -> Unit,
    onMessages: (BankSmsBank?) -> Unit,
    onAccount: (Long) -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) { UiPreferences(context) }
    val scope = rememberCoroutineScope()
    val theme by preferences.appThemeMode.collectAsState(AppThemeMode.System)
    val dynamic by preferences.dynamicColorsEnabled.collectAsState(false)
    val font by preferences.useSystemFont.collectAsState(false)
    val latin by preferences.latinCounterparties.collectAsState(true)
    val keypad by preferences.quickExpenseKeypadEnabled.collectAsState(true)
    val widget by preferences.widgetOpenAppButtonEnabled.collectAsState(true)
    SettingsScreen(
        searchState = navigation,
        appThemeMode = theme, dynamicColorsEnabled = dynamic, useSystemFont = font,
        latinCounterparties = latin, quickExpenseKeypadEnabled = keypad, widgetOpenAppButtonEnabled = widget,
        onAppThemeModeChange = { scope.launch { preferences.setAppThemeMode(it) } },
        onDynamicColorsEnabledChange = { scope.launch { preferences.setDynamicColorsEnabled(it) } },
        onUseSystemFontChange = { scope.launch { preferences.setUseSystemFont(it) } },
        onLatinCounterpartiesChange = { scope.launch { preferences.setLatinCounterparties(it) } },
        onQuickExpenseKeypadEnabledChange = { scope.launch { preferences.setQuickExpenseKeypadEnabled(it) } },
        onWidgetOpenAppButtonEnabledChange = { scope.launch { preferences.setWidgetOpenAppButtonEnabled(it) } },
        smsImportEnabled = state.smsMonitoringEnabled, hasSmsPermission = state.hasSmsPermission,
        canRequestSmsPermission = state.canRequestSmsPermission,
        onSmsImportEnabledChange = { scope.launch { preferences.setSmsImportEnabled(it) } },
        onRequestSmsPermission = onRequestSms, onOpenSystemSettings = onSystemSettings,
        appLockTimeout = timeout, appLockHasPin = hasPin, appVersion = appVersion,
        onOpenStatements = { onOpen(SetupPage.Statements) }, onOpenSmsDiagnostics = { onMessages(null) },
        onOpenBankMessages = onMessages, onOpenBankAccount = onAccount,
        onOpenCredoSync = { onOpen(SetupPage.Credo) }, onOpenTbc = { onOpen(SetupPage.Tbc) },
        onOpenPush = { onOpen(SetupPage.Push) }, onOpenAppLock = { onOpen(SetupPage.Lock) },
        onOpenBackup = { onOpen(SetupPage.Backup) }, onOpenPrivacy = { onOpen(SetupPage.Privacy) },
        onOpenAbout = { onOpen(SetupPage.About) }, onOpenCategories = { onOpen(SetupPage.Categories) },
        onOpenCategoryIntelligence = { onOpen(SetupPage.Intelligence) }, onOpenIncomeSources = { onOpen(SetupPage.Income) },
        onOpenPeople = { onOpen(SetupPage.People) }, onOpenCorrections = { onOpen(SetupPage.Corrections) },
        onOpenDataHealth = { onOpen(SetupPage.Health) },
    )
}
