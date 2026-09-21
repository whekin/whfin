package dev.whekin.whfin.ui.setup

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whekin.whfin.R
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.preferences.UiPreferences
import dev.whekin.whfin.data.security.BiometricAvailability
import dev.whekin.whfin.data.sms.BankSmsBank
import dev.whekin.whfin.ui.accounts.*
import dev.whekin.whfin.ui.settings.*
import dev.whekin.whfin.ui.savings.SavingsRoute
import kotlinx.coroutines.launch

@Composable
fun PersonalSetupFlow(
    state: PersonalSetupState,
    appVersion: String,
    appLockTimeout: AppLockTimeout,
    appLockHasPin: Boolean,
    biometricAvailability: BiometricAvailability,
    biometricUnlockEnabled: Boolean,
    hasSmsHistoryPermission: Boolean,
    canRequestSmsHistoryPermission: Boolean,
    onEnableSmsMonitoring: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onRequestSmsHistoryPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onAppLockTimeoutChange: (AppLockTimeout) -> Unit,
    onAppLockPinCreated: (String, AppLockTimeout) -> Unit,
    onBiometricUnlockEnabledChange: (Boolean) -> Unit,
    onOpenBiometricSettings: () -> Unit,
    onContinue: (initialTab: Int, openAccountAdd: Boolean) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val runtime = remember(context) { (context.applicationContext as dev.whekin.whfin.WhfinApp).runtimeModes }
    var stage by rememberSaveable { mutableStateOf(setupStageFromSaved(runtime.personalSetupStage)) }
    LaunchedEffect(stage) { runtime.personalSetupStage = stage.name }
    var stack by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedTransaction by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedAccount by rememberSaveable { mutableLongStateOf(0L) }
    var smsBank by rememberSaveable { mutableStateOf<BankSmsBank?>(null) }
    var rememberCredo by rememberSaveable { mutableStateOf(false) }
    var createPin by rememberSaveable { mutableStateOf(false) }
    var settingsEntry by rememberSaveable { mutableStateOf("connections") }
    val settings = rememberSettingsSearchState()
    val preferences = remember(context) { UiPreferences(context) }
    val scope = rememberCoroutineScope()
    val destination = stack.lastOrNull()
    val reviewCount = state.unresolvedSmsCount?.let { sms -> state.statementReviewCount?.let { sms + it } }
    fun open(page: String) { stack = stack + page }
    fun back() { stack = stack.dropLast(1) }
    fun openSettings(page: String) {
        settingsEntry = page
        settings.open(page)
        open("settings")
    }
    fun messages(bank: BankSmsBank?) { smsBank = bank; open("messages") }
    fun account(id: Long) { selectedAccount = id; open("account") }
    fun lockForCredo() { rememberCredo = true; createPin = true; open("lock") }

    if (destination == null) {
        val actions = when (stage) {
            SetupStage.Banks -> listOf(
                SetupAction("Credo") { open("credo") },
                SetupAction("TBC") { open("tbc") },
                SetupAction(stringResource(R.string.setup_channels)) { openSettings("connections") },
                SetupAction(stringResource(R.string.app_lock_title)) { open("lock") },
                SetupAction(stringResource(R.string.statements_title)) { open("statements") },
                SetupAction(stringResource(R.string.personal_setup_restore_title)) { open("backup") },
            )
            SetupStage.Accounts -> listOf(
                SetupAction(stringResource(R.string.tab_accounts)) { open("accounts") },
                SetupAction(stringResource(R.string.sms_diagnostics_title)) { messages(null) },
            )
            SetupStage.Categories -> listOf(
                SetupAction(stringResource(R.string.category_setup_title)) { open("suggestions") },
                SetupAction(stringResource(R.string.categories_title)) { open("categories") },
                SetupAction(stringResource(R.string.category_intelligence_title)) { open("intelligence") },
            )
            SetupStage.Income -> listOf(SetupAction(stringResource(R.string.income_sources_title)) { open("income") })
            SetupStage.Plans -> listOf(
                SetupAction(stringResource(R.string.savings_title)) { open("savings") },
                SetupAction(stringResource(R.string.debts_title)) { open("debts") },
            )
            SetupStage.Preferences -> listOf(
                SetupAction(stringResource(R.string.settings_application)) { openSettings("app") },
                SetupAction(stringResource(R.string.app_lock_title)) { open("lock") },
                SetupAction(stringResource(R.string.backup_title)) { open("backup") },
            )
            SetupStage.Ready -> (if (reviewCount != null && reviewCount > 0) listOf(
                SetupAction(stringResource(R.string.data_health_title)) { open("health") },
                SetupAction(stringResource(R.string.sms_diagnostics_title)) { messages(null) },
            ) else emptyList()) + SetupStage.entries.filter { it != SetupStage.Ready }.map { target ->
                SetupAction(stringResource(target.title)) { stage = target }
            }
        }
        SetupStageScreen(stage, actions,
            onBack = { if (stage.ordinal == 0) onExit() else stage = SetupStage.entries[stage.ordinal - 1] },
            onContinue = {
                if (stage == SetupStage.Ready) onContinue(0, false)
                else stage = SetupStage.entries[stage.ordinal + 1]
            },
            summary = if (stage == SetupStage.Ready) state.accountCount?.let {
                stringResource(R.string.setup_saved_accounts, it) + if (reviewCount != null && reviewCount > 0) {
                    "\n" + stringResource(R.string.setup_review_remaining, reviewCount)
                } else ""
            } ?: stringResource(R.string.personal_setup_checking) else null,
        )
        return
    }
    if (destination == "account") {
        AccountTransactionsScreen(selectedAccount, ::back)
        return
    }
    if (destination == "suggestions") {
        CategorySetupStep(onContinue = ::back, onBack = ::back)
        return
    }
    val title = when (destination) {
        "credo" -> stringResource(R.string.credo_sync_title)
        "tbc" -> stringResource(R.string.tbc_title)
        "lock" -> stringResource(R.string.app_lock_title)
        "settings" -> settingsPageTitle(settings)
        "statements" -> stringResource(R.string.statements_title)
        "backup" -> stringResource(R.string.backup_title)
        "messages" -> stringResource(R.string.sms_diagnostics_title)
        "accounts", "overview" -> stringResource(R.string.setup_accounts_title)
        "income" -> stringResource(R.string.setup_income_title)
        "savings" -> stringResource(R.string.savings_title)
        "debts" -> stringResource(R.string.debts_title)
        else -> stringResource(stage.title)
    }
    PersonalSetupSecondaryPage(title, onBack = {
        if (destination != "settings" || settings.page == settingsEntry || !settings.back()) {
            createPin = false
            back()
        }
    }) {
        when (destination) {
            "credo" -> CredoSyncRoute(canStoreCredentials = appLockHasPin,
                initialRememberPassword = rememberCredo, onOpenAppLock = ::lockForCredo,
                autoLoadFullHistory = true, onGuidedHistoryComplete = ::back, onDone = ::back)
            "tbc" -> TbcLoginRoute(appLockHasPin, false, onOpenStatements = { open("statements") })
            "accounts" -> AccountsScreen(
                onConnectBank = { open(if (it == "Credo") "credo" else "tbc") },
                onOpenStatements = { open("statements") }, onOpenSavings = { open("savings") },
                onOpenOverview = { open("overview") }, onOpenSettings = { openSettings("app") },
                onOpenAccountTransactions = ::account,
            )
            "overview" -> AccountOverviewScreen()
            "categories" -> CategoriesRoute()
            "intelligence" -> CategoryIntelligenceRoute()
            "income" -> IncomeSourcesRoute()
            "people" -> PeopleRoute()
            "savings" -> SavingsRoute()
            "debts" -> SetupDebtsRoute(::back)
            "statements" -> BankStatementsScreen()
            "backup" -> BackupRoute(appVersion)
            "privacy" -> PrivacyRoute(onOpenSystemSettings)
            "about" -> AboutScreen(appVersion = appVersion)
            "corrections" -> CorrectionsScreen()
            "health" -> DataHealthRoute(onOpenCorrections = { open("corrections") }, onOpenBackup = { open("backup") },
                onOpenTransaction = { selectedTransaction = it; open("history") })
            "history" -> dev.whekin.whfin.ui.feed.FeedScreen(mode = dev.whekin.whfin.ui.feed.FeedMode.HISTORY,
                showSmsOnboarding = false, onEnableSms = {}, onDismissSmsOnboarding = {},
                openTransactionId = selectedTransaction, onOpenTransactionConsumed = { selectedTransaction = null })
            "push" -> PushJournalRoute(false, { messages(BankSmsBank.TBC) }, diagnosticsOnly = true)
            "messages" -> SmsDiagnosticsRoute(
                appVersion, state.smsMonitoringEnabled, state.hasSmsPermission, state.canRequestSmsPermission,
                hasSmsHistoryPermission, canRequestSmsHistoryPermission,
                onEnableMonitoring = {
                    val bank = smsBank
                    if (bank == null) onEnableSmsMonitoring() else {
                        scope.launch { preferences.setBankSmsEnabled(bank, true) }
                        if (!state.hasSmsPermission) {
                            if (state.canRequestSmsPermission) onRequestSmsPermission() else onOpenSystemSettings()
                        }
                    }
                },
                onRequestReceivePermission = onRequestSmsPermission, onOpenFeed = ::back,
                onRequestHistoryPermission = onRequestSmsHistoryPermission, onOpenSystemSettings = onOpenSystemSettings,
                bankFilter = smsBank,
            )
            "lock" -> AppLockScreen(
                timeout = if (createPin) AppLockTimeout.Immediate else appLockTimeout,
                hasPin = appLockHasPin, biometricAvailability = biometricAvailability,
                biometricEnabled = biometricUnlockEnabled, onTimeoutChange = onAppLockTimeoutChange,
                onPinCreated = { pin, timeout ->
                    onAppLockPinCreated(pin, timeout)
                    if (createPin) { createPin = false; back() }
                },
                onBiometricEnabledChange = onBiometricUnlockEnabledChange,
                onOpenBiometricSettings = onOpenBiometricSettings,
                autoSetupTimeout = if (createPin) AppLockTimeout.Immediate else null,
            )
            "settings" -> SetupSettingsRoute(settings, state, appVersion, appLockTimeout, appLockHasPin,
                onRequestSmsPermission, onOpenSystemSettings, ::open, ::messages, ::account)
        }
    }
}

@Composable
private fun SetupDebtsRoute(onBack: () -> Unit, viewModel: AccountsViewModel = viewModel()) {
    val state by viewModel.screenState.collectAsState()
    val people by viewModel.people.collectAsState()
    val ready = state as? AccountsScreenState.Ready
    if (ready == null) dev.whekin.whfin.core.ui.WhfinLoadingIndicator()
    else DebtLedgerDialog(ready.debts, people, ready.accounts.map { it.account }, onBack,
        viewModel::openDebt, viewModel::settleDebt)
}
