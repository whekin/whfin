package dev.whekin.whfin.ui.settings

import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.annotation.StringRes
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinField
import dev.whekin.whfin.core.ui.WhfinFilterPill
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinConfirmDialog
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinSwitch
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import dev.whekin.whfin.ui.theme.WhfinTheme
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.preferences.AppThemeMode
import dev.whekin.whfin.data.integrity.IntegrityCheckState
import dev.whekin.whfin.ui.demo.DemoEntrySheet

@Composable
fun SettingsScreen(
    appThemeMode: AppThemeMode = AppThemeMode.System,
    dynamicColorsEnabled: Boolean = false,
    useSystemFont: Boolean = false,
    latinCounterparties: Boolean = true,
    quickExpenseKeypadEnabled: Boolean = true,
    widgetOpenAppButtonEnabled: Boolean = true,
    onAppThemeModeChange: (AppThemeMode) -> Unit = {},
    onDynamicColorsEnabledChange: (Boolean) -> Unit = {},
    onUseSystemFontChange: (Boolean) -> Unit = {},
    onLatinCounterpartiesChange: (Boolean) -> Unit = {},
    onQuickExpenseKeypadEnabledChange: (Boolean) -> Unit = {},
    onWidgetOpenAppButtonEnabledChange: (Boolean) -> Unit = {},
    smsImportEnabled: Boolean,
    hasSmsCardMapping: Boolean = true,
    hasSmsPermission: Boolean,
    canRequestSmsPermission: Boolean,
    onSmsImportEnabledChange: (Boolean) -> Unit,
    onRequestSmsPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenStatements: () -> Unit,
    onOpenSmsDiagnostics: () -> Unit,
    appLockTimeout: AppLockTimeout,
    appLockHasPin: Boolean = false,
    onOpenAppLock: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenCorrections: () -> Unit = {},
    onOpenDataHealth: () -> Unit = {},
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    appVersion: String,
    onOpenCredoSync: () -> Unit = {},
    onOpenTbc: () -> Unit = {},
    onOpenPush: () -> Unit = {},
    onSyncTbc: () -> Unit = onOpenTbc,
    onSyncCredo: () -> Unit = onOpenCredoSync,
    onOpenBankMessages: (dev.whekin.whfin.data.sms.BankSmsBank?) -> Unit = { onOpenSmsDiagnostics() },
    onOpenBankAccount: (Long) -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenCategoryIntelligence: () -> Unit = {},
    onOpenIncomeSources: () -> Unit = {},
    onOpenPeople: () -> Unit = {},
    demoMode: Boolean = false,
    developerMode: Boolean = false,
    runtimeModeBusy: Boolean = false,
    runtimeModeProblem: String? = null,
    onEnterDemo: () -> Unit = {},
    onResetDemoData: () -> Unit = {},
    searchState: SettingsSearchState = rememberSettingsSearchState(),
) {
    val viewModel: SettingsViewModel = viewModel()
    val status by viewModel.status.collectAsState()
    val context = LocalContext.current
    val preferences = remember { dev.whekin.whfin.data.preferences.UiPreferences(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val credoSms by remember(preferences) { preferences.bankSmsEnabled(dev.whekin.whfin.data.sms.BankSmsBank.CREDO) }.collectAsState(initial = smsImportEnabled)
    val tbcSms by remember(preferences) { preferences.bankSmsEnabled(dev.whekin.whfin.data.sms.BankSmsBank.TBC) }.collectAsState(initial = smsImportEnabled)
    var deviceRevision by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) deviceRevision++ }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    val pushSettings = remember { dev.whekin.whfin.data.push.PushSettings(context) }
    var pushEnabled by remember(deviceRevision) { androidx.compose.runtime.mutableStateOf(!demoMode && pushSettings.enabled) }
    val component = remember { android.content.ComponentName(context, dev.whekin.whfin.data.push.TbcPushListener::class.java) }
    val pushPermission = remember(deviceRevision) { context.getSystemService(android.app.NotificationManager::class.java).isNotificationListenerAccessGranted(component) }
    val pushConnected by dev.whekin.whfin.data.push.PushRuntime.connected.collectAsState()
    val pushRevision by dev.whekin.whfin.data.push.PushRuntime.revision.collectAsState()
    val receiverError by dev.whekin.whfin.data.push.PushRuntime.error.collectAsState()
    var journalEntries by remember { androidx.compose.runtime.mutableStateOf<List<dev.whekin.whfin.data.push.PushJournal.Entry>?>(null) }
    var journalError by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(searchState.page, pushRevision, deviceRevision) {
        if (!demoMode && searchState.page == "bank:TBC") {
            try { journalEntries = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dev.whekin.whfin.data.push.PushJournal(context).entries() }; journalError = false }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { journalError = true }
        }
    }
    val remembered = if (demoMode) emptySet() else dev.whekin.whfin.data.sms.BankSmsBank.entries.filter { bank ->
        dev.whekin.whfin.data.security.EncryptedBankCredentialStore(context, bank.name.lowercase(java.util.Locale.ROOT)).hasCredentials() ||
            (bank == dev.whekin.whfin.data.sms.BankSmsBank.TBC && dev.whekin.whfin.data.security.EncryptedBankSessionStore(context, "tbc").hasSaved())
    }.toSet()
    val notificationsPermission = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    val bankActions = ConnectionSettingsActions(
        sync = { when (it) { dev.whekin.whfin.data.sms.BankSmsBank.TBC -> onSyncTbc(); dev.whekin.whfin.data.sms.BankSmsBank.CREDO -> onSyncCredo() } },
        login = { when (it) { dev.whekin.whfin.data.sms.BankSmsBank.TBC -> onOpenTbc(); dev.whekin.whfin.data.sms.BankSmsBank.CREDO -> onOpenCredoSync() } },
        sms = { bank, value -> scope.launch {
            preferences.setBankSmsEnabled(bank, value)
            if (value && !hasSmsPermission) { if (canRequestSmsPermission) onRequestSmsPermission() else onOpenSystemSettings() }
        } },
        push = { value -> pushSettings.enabled = value; pushEnabled = value
            if (value && !pushPermission) notificationsPermission()
            else if (value) android.service.notification.NotificationListenerService.requestRebind(component) },
        smsPermission = { if (hasSmsPermission || !canRequestSmsPermission) onOpenSystemSettings() else onRequestSmsPermission() },
        pushPermission = notificationsPermission, messages = onOpenBankMessages,
        statements = onOpenStatements, journal = onOpenPush, account = onOpenBankAccount,
    )
    SettingsContent(
        searchInHeader = true,
        connections = ConnectionSettingsState(status.bankAccounts,
            mapOf(dev.whekin.whfin.data.sms.BankSmsBank.CREDO to status.lastCredoSyncAt, dev.whekin.whfin.data.sms.BankSmsBank.TBC to status.lastTbcSyncAt),
            remembered, mapOf(dev.whekin.whfin.data.sms.BankSmsBank.CREDO to credoSms, dev.whekin.whfin.data.sms.BankSmsBank.TBC to tbcSms),
            hasSmsPermission, pushEnabled, pushPermission, pushConnected, journalEntries != null,
            journalEntries?.maxOfOrNull { it.capturedAt }, journalEntries.orEmpty().count { it.outcome in setOf("UNRECOGNIZED", "TRUNCATED", "ERROR", "RECEIVED") }, journalError || receiverError),
        connectionActions = bankActions,
        searchState = searchState,
        status = status,
        appThemeMode = appThemeMode,
        dynamicColorsEnabled = dynamicColorsEnabled,
        useSystemFont = useSystemFont,
        latinCounterparties = latinCounterparties,
        quickExpenseKeypadEnabled = quickExpenseKeypadEnabled,
        widgetOpenAppButtonEnabled = widgetOpenAppButtonEnabled,
        onAppThemeModeChange = onAppThemeModeChange,
        onDynamicColorsEnabledChange = onDynamicColorsEnabledChange,
        onUseSystemFontChange = onUseSystemFontChange,
        onLatinCounterpartiesChange = onLatinCounterpartiesChange,
        onQuickExpenseKeypadEnabledChange = onQuickExpenseKeypadEnabledChange,
        onWidgetOpenAppButtonEnabledChange = onWidgetOpenAppButtonEnabledChange,
        smsImportEnabled = smsImportEnabled,
        hasSmsCardMapping = hasSmsCardMapping,
        hasSmsPermission = hasSmsPermission,
        canRequestSmsPermission = canRequestSmsPermission,
        onSmsImportEnabledChange = onSmsImportEnabledChange,
        onRequestSmsPermission = onRequestSmsPermission,
        onOpenSystemSettings = onOpenSystemSettings,
        onOpenStatements = onOpenStatements,
        onOpenSmsDiagnostics = onOpenSmsDiagnostics,
        appLockTimeout = appLockTimeout,
        appLockHasPin = appLockHasPin,
        onOpenAppLock = onOpenAppLock,
        onOpenBackup = onOpenBackup,
        onOpenCorrections = onOpenCorrections,
        onOpenDataHealth = onOpenDataHealth,
        onOpenPrivacy = onOpenPrivacy,
        onOpenAbout = onOpenAbout,
        appVersion = appVersion,
        onOpenCredoSync = onOpenCredoSync,
        onOpenTbc = onOpenTbc,
        onOpenPush = onOpenPush,
        onOpenCategories = onOpenCategories,
        onOpenCategoryIntelligence = onOpenCategoryIntelligence,
        onOpenIncomeSources = onOpenIncomeSources,
        onOpenPeople = onOpenPeople,
        demoMode = demoMode,
        developerMode = developerMode,
        runtimeModeBusy = runtimeModeBusy,
        runtimeModeProblem = runtimeModeProblem,
        onEnterDemo = onEnterDemo,
        onResetDemoData = onResetDemoData,
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    status: SettingsStatus = SettingsStatus(),
    appThemeMode: AppThemeMode = AppThemeMode.System,
    dynamicColorsEnabled: Boolean = false,
    useSystemFont: Boolean = false,
    latinCounterparties: Boolean = true,
    quickExpenseKeypadEnabled: Boolean = true,
    widgetOpenAppButtonEnabled: Boolean = true,
    onAppThemeModeChange: (AppThemeMode) -> Unit = {},
    onDynamicColorsEnabledChange: (Boolean) -> Unit = {},
    onUseSystemFontChange: (Boolean) -> Unit = {},
    onLatinCounterpartiesChange: (Boolean) -> Unit = {},
    onQuickExpenseKeypadEnabledChange: (Boolean) -> Unit = {},
    onWidgetOpenAppButtonEnabledChange: (Boolean) -> Unit = {},
    smsImportEnabled: Boolean,
    hasSmsCardMapping: Boolean = true,
    hasSmsPermission: Boolean,
    canRequestSmsPermission: Boolean,
    onSmsImportEnabledChange: (Boolean) -> Unit,
    onRequestSmsPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenStatements: () -> Unit,
    onOpenSmsDiagnostics: () -> Unit,
    appLockTimeout: AppLockTimeout,
    appLockHasPin: Boolean = false,
    onOpenAppLock: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenCorrections: () -> Unit = {},
    onOpenDataHealth: () -> Unit = {},
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    appVersion: String,
    onOpenCredoSync: () -> Unit = {},
    onOpenTbc: () -> Unit = {},
    onOpenPush: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenCategoryIntelligence: () -> Unit = {},
    onOpenIncomeSources: () -> Unit = {},
    onOpenPeople: () -> Unit = {},
    demoMode: Boolean = false,
    developerMode: Boolean = false,
    runtimeModeBusy: Boolean = false,
    runtimeModeProblem: String? = null,
    onEnterDemo: () -> Unit = {},
    onResetDemoData: () -> Unit = {},
    connections: ConnectionSettingsState? = null,
    connectionActions: ConnectionSettingsActions? = null,
    searchState: SettingsSearchState = rememberSettingsSearchState(),
    searchInHeader: Boolean = false,
) {
    var confirmDemoReset by rememberSaveable { mutableStateOf(false) }
    var showDemoEntry by rememberSaveable { mutableStateOf(false) }
    val query = searchState.query
    val navigationHaptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    val smsProblem = smsImportEnabled && (!hasSmsPermission || !hasSmsCardMapping)
    val baseSections = buildSettingsSections(
        status = status,
        pushEnabled = connections?.pushEnabled ?: false,
        appLockTimeout = appLockTimeout,
        appLockHasPin = appLockHasPin,
        appVersion = appVersion,
        smsImportEnabled = smsImportEnabled,
        hasSmsPermission = hasSmsPermission,
        hasSmsCardMapping = hasSmsCardMapping,
        dynamicColorsEnabled = dynamicColorsEnabled,
        useSystemFont = useSystemFont,
        latinCounterparties = latinCounterparties,
        quickExpenseKeypadEnabled = quickExpenseKeypadEnabled,
        widgetOpenAppButtonEnabled = widgetOpenAppButtonEnabled,
        demoMode = demoMode,
        onOpenBankSettings = { bank -> searchState.open("bank:${bank.name}") },
        onOpenStatements = onOpenStatements,
        onOpenSmsDiagnostics = onOpenSmsDiagnostics,
        onSmsImportEnabledChange = { enabled ->
            onSmsImportEnabledChange(enabled)
            if (enabled && !hasSmsPermission) {
                if (canRequestSmsPermission) onRequestSmsPermission() else onOpenSystemSettings()
            }
        },
        onOpenCategories = onOpenCategories,
        onOpenCategoryIntelligence = onOpenCategoryIntelligence,
        onOpenIncomeSources = onOpenIncomeSources,
        onOpenPeople = onOpenPeople,
        onOpenAppLock = onOpenAppLock,
        onOpenBackup = onOpenBackup,
        onOpenCorrections = onOpenCorrections,
        onOpenDataHealth = onOpenDataHealth,
        onOpenPrivacy = onOpenPrivacy,
        onDynamicColorsEnabledChange = onDynamicColorsEnabledChange,
        onUseSystemFontChange = onUseSystemFontChange,
        onLatinCounterpartiesChange = onLatinCounterpartiesChange,
        onQuickExpenseKeypadEnabledChange = onQuickExpenseKeypadEnabledChange,
        onWidgetOpenAppButtonEnabledChange = onWidgetOpenAppButtonEnabledChange,
        onOpenAbout = onOpenAbout,
        onOpenDemoEntry = { showDemoEntry = true },
        onResetDemo = { confirmDemoReset = true },
    )
    val sections = baseSections.map { section -> if (section.id != SECTION_BANK) section else section.copy(rows = section.rows + listOf(
        SettingsRow("tbc-login", "TBC · " + stringResource(R.string.settings_bank_sign_in), keywords = "TBC login пароль вход OTP", onClick = onOpenTbc, enabled = !demoMode),
        SettingsRow("credo-login", "Credo · " + stringResource(R.string.settings_bank_sign_in), keywords = "Credo login пароль вход OTP", onClick = onOpenCredoSync, enabled = !demoMode),
        SettingsRow("tbc-journal", "TBC · " + stringResource(R.string.settings_bank_diagnostics), keywords = "TBC push журнал диагностика notification log", onClick = onOpenPush, enabled = !demoMode),
    )) }
    val visible = if (query.isNotBlank()) filterSettings(sections, query)
        else sections.filter { it.id == searchState.page || (searchState.page == "about" && it.id == "demo") }
    val searching = query.isNotBlank()

    if (!searching && (searchState.page == "connections" || searchState.page == "add-bank" || searchState.page.startsWith("bank:"))) {
        ConnectionsSettings(connections ?: ConnectionSettingsState(accounts = status.bankAccounts ?: emptyMap(),
            lastSync = mapOf(dev.whekin.whfin.data.sms.BankSmsBank.CREDO to status.lastCredoSyncAt, dev.whekin.whfin.data.sms.BankSmsBank.TBC to status.lastTbcSyncAt),
            sms = dev.whekin.whfin.data.sms.BankSmsBank.entries.associateWith { smsImportEnabled }, smsPermission = hasSmsPermission),
            searchState, connectionActions ?: ConnectionSettingsActions(
                sync = { when (it) { dev.whekin.whfin.data.sms.BankSmsBank.TBC -> onOpenTbc(); dev.whekin.whfin.data.sms.BankSmsBank.CREDO -> onOpenCredoSync() } },
                login = { when (it) { dev.whekin.whfin.data.sms.BankSmsBank.TBC -> onOpenTbc(); dev.whekin.whfin.data.sms.BankSmsBank.CREDO -> onOpenCredoSync() } },
                sms = { _, value -> onSmsImportEnabledChange(value); if (value && !hasSmsPermission) { if (canRequestSmsPermission) onRequestSmsPermission() else onOpenSystemSettings() } }, push = {}, smsPermission = onRequestSmsPermission,
                pushPermission = onOpenSystemSettings, messages = { onOpenSmsDiagnostics() }, statements = onOpenStatements,
                journal = onOpenPush, account = {}), demoMode)
        return
    }
    val catalogScroll = searchState.scroll
    LaunchedEffect(searchState.page) { catalogScroll.scrollTo(0) }
    val searchScope = androidx.compose.runtime.rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val revealMotion = dev.whekin.whfin.core.ui.WhfinMotion.standard<Float>()
    LaunchedEffect(searchState.request) {
        if (!searchInHeader && searchState.request > searchState.handledRequest) {
            searchState.handledRequest = searchState.request
            catalogScroll.animateScrollTo(0, animationSpec = revealMotion)
            searchState.focus.requestFocus()
            keyboard?.show()
        }
    }
    val pullSearch = rememberSettingsSearchPull(searchState)
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().imePadding()
            .testTag("settings-catalog").nestedScroll(pullSearch).verticalScroll(catalogScroll)
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!searchInHeader && ((searchState.page.isBlank() && searchState.searchVisible) || searching)) WhfinField(
            value = query,
            onValueChange = { value ->
                searchState.query = value
                searchScope.launch { catalogScroll.scrollTo(0) }
            },
            label = null,
            leadingIcon = Icons.Default.Search,
            placeholder = stringResource(R.string.settings_search_hint),
            modifier = Modifier.fillMaxWidth().focusRequester(searchState.focus)
                .testTag("settings-search"),
        )
        if (!searching && searchState.page.isBlank()) {
            WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                listOf(
                    Triple("connections", R.string.settings_connections, R.string.settings_connections_summary),
                    Triple("catalog", R.string.settings_accounting, R.string.settings_accounting_summary),
                    Triple("app", R.string.settings_application, R.string.settings_application_summary),
                    Triple("data", R.string.settings_data_security, R.string.settings_data_security_summary),
                    Triple("about", R.string.about_title, R.string.settings_about_summary),
                ).forEach { (page, title, summary) ->
                    dev.whekin.whfin.core.ui.WhfinLedgerRow(stringResource(title), supportingText = stringResource(summary),
                        icon = when (page) {
                            "connections" -> Icons.Default.CloudSync
                            "catalog" -> Icons.Default.Category
                            "app" -> Icons.Default.Tune
                            "data" -> Icons.Default.Lock
                            else -> Icons.Default.Info
                        },
                        onClick = { navigationHaptics.performHapticFeedback(dev.whekin.whfin.core.ui.WhfinHaptics.navigation); searchState.open(page) }, divider = page != "about",
                        trailing = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.ChevronRight, null) })
                }
            }
        }
        if (visible.isEmpty() && searching) {
            Text(
                stringResource(R.string.settings_search_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        visible.forEach { section ->
            if (searching || section.id == "demo") WhfinSectionLabel(section.label)
            // The theme choice is the control itself, so it stands outside the group of rows it
            // belongs to rather than pretending to be a door.
            val themeRow = section.rows.firstOrNull { it.id == ROW_THEME }
            if (themeRow != null) { dev.whekin.whfin.core.ui.WhfinFieldLabel(themeRow.title); ThemeChoice(appThemeMode, onAppThemeModeChange) }
            val rows = section.rows.filterNot { it.id == ROW_THEME }
            if (rows.isNotEmpty()) WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                rows.forEachIndexed { index, row ->
                    SettingsRowContent(row, divider = index != rows.lastIndex)
                }
            }
            // A problem with bank messages is not a state a row can carry: it needs the action that
            // fixes it, and it stays a notice for exactly as long as it is true.
            if (section.id == SECTION_BANK && smsProblem && section.rows.any { it.id == ROW_SMS }) {
                WhfinNotice(
                    title = stringResource(R.string.settings_sms_title),
                    body = stringResource(
                        if (!hasSmsPermission) R.string.settings_sms_permission_body
                        else R.string.settings_sms_unrouted_body,
                    ),
                    icon = Icons.Default.Sms,
                    kind = WhfinNoticeKind.Attention,
                    actionLabel = stringResource(
                        when {
                            !hasSmsPermission && canRequestSmsPermission -> R.string.permission_allow
                            !hasSmsPermission -> R.string.permission_open_settings
                            else -> R.string.sms_review_routing_action
                        },
                    ),
                    onAction = when {
                        !hasSmsPermission ->
                            if (canRequestSmsPermission) onRequestSmsPermission else onOpenSystemSettings
                        else -> onOpenSmsDiagnostics
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (section.id == SECTION_BANK && demoMode && !searching) WhfinNotice(
                title = stringResource(R.string.demo_mode_automation_title),
                body = stringResource(R.string.demo_mode_automation_body),
                icon = Icons.Default.Sms,
                kind = WhfinNoticeKind.Unavailable,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (developerMode && !searching && searchState.page == "about") {
            WhfinSectionLabel(stringResource(R.string.developer_mode_section))
            WhfinNotice(
                title = stringResource(R.string.developer_mode_enabled_title),
                body = stringResource(R.string.developer_mode_enabled_body),
                icon = Icons.Default.BugReport,
                kind = WhfinNoticeKind.Info,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showDemoEntry && !demoMode) {
        DemoEntrySheet(
            busy = runtimeModeBusy,
            problem = runtimeModeProblem,
            onOpenDemo = onEnterDemo,
            onDismiss = { showDemoEntry = false },
        )
    }

    if (confirmDemoReset) WhfinConfirmDialog(
        title = stringResource(R.string.demo_mode_reset_title),
        body = stringResource(R.string.demo_mode_reset_body),
        confirmLabel = stringResource(R.string.demo_mode_reset_confirm),
        dismissLabel = stringResource(R.string.action_cancel),
        onConfirm = {
            confirmDemoReset = false
            onResetDemoData()
        },
        onDismiss = { confirmDemoReset = false },
    )
}

@Composable
private fun SettingsRowContent(row: SettingsRow, divider: Boolean) {
    val control = row.control
    val keyboard = LocalSoftwareKeyboardController.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    WhfinLedgerRow(
        modifier = (if (control is SettingsControl.Toggle) Modifier.toggleable(
            value = control.checked,
            enabled = row.enabled,
            role = Role.Switch,
            onValueChange = { value ->
                haptics.performHapticFeedback(dev.whekin.whfin.core.ui.WhfinHaptics.toggle(value))
                control.onCheckedChange(value)
            },
        ).semantics { contentDescription = control.contentDescription } else Modifier).testTag("settings-row-${row.id}"),
        title = row.title,
        // Reached through its contents, the row answers with what was found in there. Its usual
        // status — when a copy last ran, how long the lock waits — is true but is not the answer to
        // what was asked, and printed here it reads as a refusal to have understood the question.
        supportingText = row.insideMatches.takeIf { it.isNotEmpty() }
            ?.let { stringResource(R.string.settings_inside_label, it.joinToString(" · ")) }
            ?: row.summary,
        icon = row.icon,
        iconTint = when {
            row.destructive -> MaterialTheme.colorScheme.error
            !row.enabled -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.primary
        },
        titleColor = when {
            row.destructive -> MaterialTheme.colorScheme.error
            !row.enabled -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        },
        trailing = when {
            control is SettingsControl.Toggle -> {
                {
                    WhfinSwitch(
                        checked = control.checked,
                        onCheckedChange = null,
                        contentDescription = control.contentDescription,
                        enabled = row.enabled,
                    )
                }
            }
            control is SettingsControl.Navigate && row.enabled && !row.destructive && row.onClick != null -> {
                { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
            }
            else -> null
        },
        onClick = row.onClick?.takeIf { row.enabled && control !is SettingsControl.Toggle }?.let { action -> { keyboard?.hide(); action() } },
        divider = divider,
    )
}

/**
 * Three mutually exclusive answers divide one row between them: as three rows with a sentence each,
 * picking a theme filled a screen and a half to say what three words say.
 */
@Composable
private fun ThemeChoice(mode: AppThemeMode, onChange: (AppThemeMode) -> Unit) {
    dev.whekin.whfin.core.ui.WhfinThemeChoice(
        labels = listOf(stringResource(R.string.settings_theme_system), stringResource(R.string.settings_theme_light), stringResource(R.string.settings_theme_dark)),
        selectedIndex = AppThemeMode.entries.indexOf(mode),
        onSelect = { onChange(AppThemeMode.entries[it]) },
    )
}

/**
 * The catalogue, in the order the screen is opened for.
 *
 * Money arriving on its own comes first, because that is what breaks and what people come to check.
 * What the ledger is made of comes next, then the data itself — locked, copied, verified — and only
 * then how the app looks. Appearance used to be the first screenful; it is the one part of Settings
 * that is decided once.
 */
@Composable
private fun buildSettingsSections(
    status: SettingsStatus,
    pushEnabled: Boolean,
    appLockTimeout: AppLockTimeout,
    appLockHasPin: Boolean,
    appVersion: String,
    smsImportEnabled: Boolean,
    hasSmsPermission: Boolean,
    hasSmsCardMapping: Boolean,
    dynamicColorsEnabled: Boolean,
    useSystemFont: Boolean,
    latinCounterparties: Boolean,
    quickExpenseKeypadEnabled: Boolean,
    widgetOpenAppButtonEnabled: Boolean,
    demoMode: Boolean,
    onOpenBankSettings: (dev.whekin.whfin.data.sms.BankSmsBank) -> Unit,
    onOpenStatements: () -> Unit,
    onOpenSmsDiagnostics: () -> Unit,
    onSmsImportEnabledChange: (Boolean) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenCategoryIntelligence: () -> Unit,
    onOpenIncomeSources: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenAppLock: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenCorrections: () -> Unit,
    onOpenDataHealth: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onDynamicColorsEnabledChange: (Boolean) -> Unit,
    onUseSystemFontChange: (Boolean) -> Unit,
    onLatinCounterpartiesChange: (Boolean) -> Unit,
    onQuickExpenseKeypadEnabledChange: (Boolean) -> Unit,
    onWidgetOpenAppButtonEnabledChange: (Boolean) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDemoEntry: () -> Unit,
    onResetDemo: () -> Unit,
): List<SettingsSection> {
    val demoUnavailable = stringResource(R.string.demo_mode_live_import_unavailable)
    val bank = SettingsSection(
        id = SECTION_BANK,
        label = stringResource(R.string.settings_section_bank),
        rows = listOfNotNull(
            SettingsRow(
                id = "credo",
                title = "Credo",
                summary = when {
                    demoMode -> demoUnavailable
                    status.lastCredoSyncAt != null ->
                        stringResource(R.string.settings_credo_synced, relativeDay(status.lastCredoSyncAt))
                    else -> stringResource(R.string.settings_credo_never)
                },
                keywords = stringResource(R.string.settings_keywords_credo),
                inside = settingsInside(R.string.settings_inside_credo),
                icon = Icons.Default.CloudSync,
                enabled = !demoMode,
                onClick = { onOpenBankSettings(dev.whekin.whfin.data.sms.BankSmsBank.CREDO) },
            ),
            SettingsRow(
                id = "tbc",
                title = "TBC",
                summary = if (demoMode) demoUnavailable else stringResource(R.string.tbc_settings_summary),
                keywords = "TBC", inside = listOf(stringResource(R.string.tbc_settings_summary)),
                icon = Icons.Default.CloudSync, enabled = !demoMode,
                onClick = { onOpenBankSettings(dev.whekin.whfin.data.sms.BankSmsBank.TBC) },
            ),
            SettingsRow(
                id = "statements",
                title = stringResource(R.string.statements_title),
                summary = status.lastStatementImportAt
                    ?.let { stringResource(R.string.settings_statements_last, relativeDay(it)) }
                    ?: stringResource(R.string.statements_settings_summary),
                keywords = stringResource(R.string.settings_keywords_statements),
                inside = settingsInside(R.string.settings_inside_statements),
                icon = Icons.Default.Description,
                onClick = onOpenStatements,
            ),
            SettingsRow(id = "credo-sms", title = "Credo · SMS", keywords = "Credo SMS сообщения", icon = Icons.Default.Sms,
                enabled = !demoMode, onClick = { onOpenBankSettings(dev.whekin.whfin.data.sms.BankSmsBank.CREDO) }),
            SettingsRow(id = "tbc-sms", title = "TBC · SMS", keywords = "TBC SMS сообщения", icon = Icons.Default.Sms,
                enabled = !demoMode, onClick = { onOpenBankSettings(dev.whekin.whfin.data.sms.BankSmsBank.TBC) }),
            SettingsRow(
                id = ROW_SMS,
                title = stringResource(R.string.sms_diagnostics_title),
                summary = when {
                    demoMode -> demoUnavailable
                    !smsImportEnabled -> stringResource(R.string.settings_sms_state_off)
                    !hasSmsPermission -> stringResource(R.string.settings_sms_state_permission)
                    !hasSmsCardMapping -> stringResource(R.string.settings_sms_state_unrouted)
                    else -> stringResource(R.string.settings_sms_state_on)
                },
                keywords = stringResource(R.string.settings_keywords_sms),
                inside = settingsInside(R.string.settings_inside_sms),
                icon = Icons.Default.Sms,
                enabled = !demoMode,
                onClick = onOpenSmsDiagnostics,
            ),
            SettingsRow(
                id = "tbc-push", title = stringResource(R.string.push_title),
                summary = stringResource(if (pushEnabled) R.string.push_on else R.string.push_off), keywords = "TBC push notifications уведомления журнал",
                icon = Icons.Default.Sms, enabled = !demoMode,
                onClick = { onOpenBankSettings(dev.whekin.whfin.data.sms.BankSmsBank.TBC) },
            ),

        ),
    )
    val catalog = SettingsSection(
        id = "catalog",
        label = stringResource(R.string.settings_catalog_section),
        rows = listOf(
            SettingsRow(
                id = "categories",
                title = stringResource(R.string.categories_title),
                summary = stringResource(R.string.categories_settings_summary),
                keywords = stringResource(R.string.settings_keywords_categories),
                inside = settingsInside(R.string.settings_inside_categories),
                icon = Icons.Default.Category,
                onClick = onOpenCategories,
            ),
            SettingsRow(
                id = "category-intelligence",
                title = stringResource(R.string.category_intelligence_title),
                summary = stringResource(R.string.category_intelligence_settings_summary),
                keywords = stringResource(R.string.settings_keywords_category_intelligence),
                inside = settingsInside(R.string.settings_inside_category_intelligence),
                icon = Icons.Default.AutoAwesome,
                onClick = onOpenCategoryIntelligence,
            ),
            SettingsRow(
                id = "income-sources",
                title = stringResource(R.string.income_sources_title),
                keywords = stringResource(R.string.settings_keywords_income),
                icon = Icons.Default.SouthWest,
                onClick = onOpenIncomeSources,
            ),
            SettingsRow(
                id = "people",
                title = stringResource(R.string.people_title),
                keywords = stringResource(R.string.settings_keywords_people),
                inside = settingsInside(R.string.settings_inside_people),
                icon = Icons.Default.Group,
                onClick = onOpenPeople,
            ),
        ),
    )
    val data = SettingsSection(
        id = "data",
        label = stringResource(R.string.settings_section_data),
        rows = listOf(
            SettingsRow(
                id = "app-lock",
                title = stringResource(R.string.app_lock_title),
                // The delay alone answered the wrong question: with a code and no lock screen the
                // row read "Off", which is exactly the state where the code is doing its work.
                summary = when {
                    !appLockHasPin -> stringResource(R.string.app_lock_summary_no_code)
                    !appLockTimeout.enabled -> stringResource(R.string.app_lock_summary_code_only)
                    else -> stringResource(
                        R.string.app_lock_summary_locked,
                        stringResource(appLockTimeout.labelResource()),
                    )
                },
                keywords = stringResource(R.string.settings_keywords_app_lock),
                inside = settingsInside(R.string.settings_inside_app_lock),
                icon = Icons.Default.Lock,
                onClick = onOpenAppLock,
            ),
            SettingsRow(
                id = "backup",
                title = stringResource(R.string.backup_title),
                summary = when {
                    demoMode -> stringResource(R.string.demo_mode_backup_unavailable)
                    status.driveBackupEnabled && status.lastDriveBackupAt != null ->
                        stringResource(R.string.settings_backup_drive, relativeDay(status.lastDriveBackupAt))
                    status.driveBackupEnabled -> stringResource(R.string.settings_backup_drive_pending)
                    else -> stringResource(R.string.settings_backup_manual)
                },
                keywords = stringResource(R.string.settings_keywords_backup),
                inside = settingsInside(R.string.settings_inside_backup),
                icon = Icons.Default.SaveAlt,
                enabled = !demoMode,
                onClick = onOpenBackup,
            ),
            SettingsRow(
                id = "data-health",
                title = stringResource(R.string.data_health_title),
                summary = when (val check = status.integrityCheck) {
                    IntegrityCheckState.NotChecked -> stringResource(R.string.settings_data_health_not_checked)
                    IntegrityCheckState.Checking -> stringResource(R.string.settings_data_health_checking)
                    IntegrityCheckState.Failed -> stringResource(R.string.settings_data_health_failed)
                    is IntegrityCheckState.Complete -> if (check.issueCount > 0) {
                        pluralStringResource(R.plurals.settings_data_health_issues,
                            check.issueCount, check.issueCount)
                    } else {
                        stringResource(R.string.settings_data_health_clean, relativeDay(check.checkedAt))
                    }
                },
                keywords = stringResource(R.string.settings_keywords_data_health),
                inside = settingsInside(R.string.settings_inside_data_health),
                icon = Icons.Default.HealthAndSafety,
                onClick = onOpenDataHealth,
            ),
            SettingsRow(
                id = "corrections",
                title = stringResource(R.string.corrections_title),
                summary = stringResource(R.string.corrections_settings_summary),
                keywords = stringResource(R.string.settings_keywords_corrections),
                icon = Icons.Default.Restore,
                onClick = onOpenCorrections,
            ),
        ),
    )
    val appearance = SettingsSection(
        id = "app",
        label = stringResource(R.string.settings_section_app),
        rows = listOf(
            SettingsRow(
                id = ROW_THEME,
                title = stringResource(R.string.settings_theme_row),
                keywords = stringResource(R.string.settings_keywords_theme),
                control = SettingsControl.Inline,
            ),
            SettingsRow(
                id = "dynamic-colors",
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_dynamic_colors),
                summary = stringResource(R.string.settings_dynamic_colors_body),
                keywords = stringResource(R.string.settings_keywords_theme),
                control = SettingsControl.Toggle(
                    checked = dynamicColorsEnabled,
                    contentDescription = stringResource(R.string.settings_dynamic_colors_toggle),
                    onCheckedChange = onDynamicColorsEnabledChange,
                ),
            ),
            SettingsRow(
                id = "system-font",
                icon = Icons.Default.TextFields,
                title = stringResource(R.string.settings_system_font),
                summary = stringResource(R.string.settings_system_font_body),
                keywords = stringResource(R.string.settings_keywords_font),
                control = SettingsControl.Toggle(
                    checked = useSystemFont,
                    contentDescription = stringResource(R.string.settings_system_font_toggle),
                    onCheckedChange = onUseSystemFontChange,
                ),
            ),
            SettingsRow(
                id = "latin-counterparties",
                icon = Icons.Default.Translate,
                title = stringResource(R.string.settings_latin_counterparties),
                summary = stringResource(R.string.settings_latin_counterparties_body),
                keywords = stringResource(R.string.settings_keywords_latin),
                control = SettingsControl.Toggle(
                    checked = latinCounterparties,
                    contentDescription = stringResource(R.string.settings_latin_counterparties_toggle),
                    onCheckedChange = onLatinCounterpartiesChange,
                ),
            ),
            SettingsRow(
                id = "quick-keypad",
                icon = Icons.Default.Dialpad,
                title = stringResource(R.string.settings_quick_keypad),
                summary = stringResource(R.string.settings_quick_keypad_body),
                keywords = stringResource(R.string.settings_keywords_keypad),
                control = SettingsControl.Toggle(
                    checked = quickExpenseKeypadEnabled,
                    contentDescription = stringResource(R.string.settings_quick_keypad_toggle),
                    onCheckedChange = onQuickExpenseKeypadEnabledChange,
                ),
            ),
            SettingsRow(
                id = "widget-button",
                icon = Icons.Default.Widgets,
                title = stringResource(R.string.settings_widget_open_app),
                summary = stringResource(R.string.settings_widget_open_app_body),
                keywords = stringResource(R.string.settings_keywords_widget),
                control = SettingsControl.Toggle(
                    checked = widgetOpenAppButtonEnabled,
                    contentDescription = stringResource(R.string.settings_widget_open_app_toggle),
                    onCheckedChange = onWidgetOpenAppButtonEnabledChange,
                ),
            ),
        ),
    )
    val about = SettingsSection(
        id = "about",
        label = stringResource(R.string.settings_about_section),
        rows = listOfNotNull(
            SettingsRow(
                id = "about",
                title = stringResource(R.string.about_title),
                summary = appVersion,
                keywords = stringResource(R.string.settings_keywords_about),
                inside = settingsInside(R.string.settings_inside_about),
                icon = Icons.Default.Info,
                onClick = onOpenAbout,
            ),
            SettingsRow(
                id = "privacy",
                title = stringResource(R.string.privacy_title),
                summary = stringResource(R.string.privacy_settings_summary),
                keywords = stringResource(R.string.settings_keywords_privacy),
                inside = settingsInside(R.string.settings_inside_privacy),
                icon = Icons.Default.PrivacyTip,
                onClick = onOpenPrivacy,
            ),
            if (demoMode) null else SettingsRow(
                id = "demo-entry",
                title = stringResource(R.string.demo_entry_title),
                summary = stringResource(R.string.demo_entry_settings_summary),
                keywords = stringResource(R.string.settings_keywords_demo),
                icon = Icons.Default.Science,
                onClick = onOpenDemoEntry,
            ),
        ),
    )
    // Erasing a workspace keeps its own heading: it is not a fact about the app, it is an action on
    // the person's data, and it should never sit one row away from the version number.
    val demo = SettingsSection(
        id = "demo",
        label = stringResource(R.string.demo_workspace_section),
        rows = listOf(
            SettingsRow(
                id = "demo-reset",
                title = stringResource(R.string.demo_mode_reset),
                summary = stringResource(R.string.demo_mode_reset_summary),
                keywords = stringResource(R.string.settings_keywords_demo),
                icon = Icons.Default.Restore,
                destructive = true,
                onClick = onResetDemo,
            ),
        ),
    )
    return listOfNotNull(bank, catalog, data, appearance, if (demoMode) demo else null, about)
}

/** "вчера", "2 days ago" — a settings row cares about the day, never the minute. */
@Composable
private fun relativeDay(millis: Long): String {
    val context = LocalContext.current
    return remember(millis, context) {
        DateUtils.getRelativeTimeSpanString(
            millis,
            System.currentTimeMillis(),
            DateUtils.DAY_IN_MILLIS,
        ).toString()
    }
}

/**
 * The contents of a screen, written as one comma-separated string and read back as a list.
 *
 * One resource per door keeps the index reviewable next to the words it mirrors, and keeps a
 * translator changing one string instead of six.
 */
@Composable
private fun settingsInside(@StringRes resource: Int): List<String> {
    val raw = stringResource(resource)
    return remember(raw) { raw.split(',').map(String::trim).filter(String::isNotEmpty) }
}

private const val SECTION_BANK = "bank"
private const val ROW_SMS = "sms"
private const val ROW_THEME = "theme"

@Preview(name = "Settings light", widthDp = 400, heightDp = 800, showBackground = true)
@Preview(name = "Settings dark", widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Settings font 1.5", widthDp = 400, heightDp = 900, fontScale = 1.5f, showBackground = true)
@Preview(name = "Settings compact", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    WhfinTheme {
        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
            SettingsContent(
                status = SettingsStatus(
                    lastCredoSyncAt = System.currentTimeMillis() - 2 * DateUtils.DAY_IN_MILLIS,
                    lastStatementImportAt = System.currentTimeMillis() - 9 * DateUtils.DAY_IN_MILLIS,
                    driveBackupEnabled = true,
                    lastDriveBackupAt = System.currentTimeMillis() - DateUtils.DAY_IN_MILLIS,
                ),
                smsImportEnabled = true,
                hasSmsPermission = false,
                canRequestSmsPermission = true,
                onSmsImportEnabledChange = {},
                onRequestSmsPermission = {},
                onOpenSystemSettings = {},
                onOpenStatements = {},
                onOpenSmsDiagnostics = {},
                appLockTimeout = AppLockTimeout.OneMinute,
                onOpenAppLock = {},
                onOpenBackup = {},
                onOpenPrivacy = {},
                onOpenAbout = {},
                appVersion = "Version 0.1.0 (1)",
            )
        }
    }
}

@Preview(name = "Settings SMS disabled", widthDp = 400, heightDp = 650, showBackground = true)
@Composable
private fun SettingsSmsDisabledPreview() {
    WhfinTheme {
        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
            SettingsContent(
                smsImportEnabled = false,
                hasSmsPermission = true,
                canRequestSmsPermission = true,
                onSmsImportEnabledChange = {},
                onRequestSmsPermission = {},
                onOpenSystemSettings = {},
                onOpenStatements = {},
                onOpenSmsDiagnostics = {},
                appLockTimeout = AppLockTimeout.Disabled,
                onOpenAppLock = {},
                onOpenBackup = {},
                onOpenPrivacy = {},
                onOpenAbout = {},
                appVersion = "Version 0.1.0 (1)",
            )
        }
    }
}

@Preview(name = "Settings demo active", widthDp = 400, heightDp = 800, showBackground = true)
@Composable
private fun SettingsDemoPreview() {
    WhfinTheme {
        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
            SettingsContent(
                smsImportEnabled = false,
                hasSmsPermission = true,
                canRequestSmsPermission = true,
                onSmsImportEnabledChange = {},
                onRequestSmsPermission = {},
                onOpenSystemSettings = {},
                onOpenStatements = {},
                onOpenSmsDiagnostics = {},
                appLockTimeout = AppLockTimeout.Disabled,
                onOpenAppLock = {},
                onOpenBackup = {},
                onOpenPrivacy = {},
                onOpenAbout = {},
                appVersion = "Version 0.1.0 (1)",
                demoMode = true,
                developerMode = true,
            )
        }
    }
}
