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
import dev.whekin.whfin.data.sms.SmsInboxCardLinker
import dev.whekin.whfin.ui.accounts.*
import dev.whekin.whfin.ui.OnFormSaved
import dev.whekin.whfin.ui.settings.*
import dev.whekin.whfin.ui.savings.SavingsRoute
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface SetupCardLinkState {
    data object NotStarted : SetupCardLinkState
    data object WaitingForBank : SetupCardLinkState
    data object Checking : SetupCardLinkState
    data class Checked(val linked: Int) : SetupCardLinkState
    data object Failed : SetupCardLinkState
}

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
    val app = remember(context) { context.applicationContext as dev.whekin.whfin.WhfinApp }
    val runtime = remember(app) { app.runtimeModes }
    val bankSyncStatuses by app.bankSync.statuses.collectAsState()
    val bankWorkActive = bankSyncStatuses.any { it.active }
    var stage by rememberSaveable { mutableStateOf(setupStageFromSaved(runtime.personalSetupStage)) }
    LaunchedEffect(stage) { runtime.personalSetupStage = stage.name }
    var showSteps by rememberSaveable { mutableStateOf(false) }
    var showCashSheet by rememberSaveable { mutableStateOf(false) }
    var cardLinkState by remember { mutableStateOf<SetupCardLinkState>(SetupCardLinkState.NotStarted) }
    var stack by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedTransaction by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedAccount by rememberSaveable { mutableLongStateOf(0L) }
    var smsBank by rememberSaveable { mutableStateOf<BankSmsBank?>(null) }
    var rememberCredo by rememberSaveable { mutableStateOf(false) }
    var createPin by rememberSaveable { mutableStateOf(false) }
    var settingsEntry by rememberSaveable { mutableStateOf("connections") }
    val settings = rememberSettingsSearchState()
    val preferences = remember(context) { UiPreferences(context) }
    val credoSmsEnabled by remember(preferences) { preferences.bankSmsEnabled(BankSmsBank.CREDO) }.collectAsState(initial = false)
    val tbcSmsEnabled by remember(preferences) { preferences.bankSmsEnabled(BankSmsBank.TBC) }.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val destination = stack.lastOrNull()
    val reviewCount = state.unresolvedSmsCount?.let { sms -> state.statementReviewCount?.let { sms + it } }
    LaunchedEffect(stage, hasSmsHistoryPermission, destination, bankWorkActive) {
        if (stage != SetupStage.Sms || !hasSmsHistoryPermission) {
            cardLinkState = SetupCardLinkState.NotStarted
            return@LaunchedEffect
        }
        if (destination != null) return@LaunchedEffect
        if (bankWorkActive) {
            cardLinkState = SetupCardLinkState.WaitingForBank
            return@LaunchedEffect
        }
        cardLinkState = SetupCardLinkState.Checking
        cardLinkState = try {
            val result = withContext(Dispatchers.IO) { SmsInboxCardLinker.run(app, app.userDb) }
            SetupCardLinkState.Checked(result.cardsLinked)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            SetupCardLinkState.Failed
        }
    }
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

    val overviewModel: SetupOverviewViewModel = viewModel()
    val overviewState by overviewModel.state.collectAsState()
    val overview = (overviewState as? SetupOverviewState.Ready)?.value

    if (destination == null) {
        @Composable fun saved(count: Int?) = count?.let { stringResource(R.string.setup_count_saved, it) }
        @Composable fun bankStatus(bank: BankSmsBank): String? = overview?.let {
            when {
                bank in it.bankImports -> stringResource(R.string.setup_history_loaded)
                it.bankAccounts.getValue(bank) > 0 -> stringResource(R.string.setup_count_accounts, it.bankAccounts.getValue(bank))
                else -> stringResource(R.string.setup_not_added)
            }
        }
        @Composable fun smsStatus(enabled: Boolean): String = stringResource(when {
            !enabled -> R.string.setup_sms_off
            !state.hasSmsPermission -> R.string.setup_sms_permission_needed
            else -> R.string.setup_sms_on
        })
        val smsReviewStatus = overview?.unrouted?.takeIf { it > 0 }
            ?.let { stringResource(R.string.setup_needs_account, it) }
            ?: when {
                !hasSmsHistoryPermission -> stringResource(R.string.setup_sms_access_hint)
                cardLinkState == SetupCardLinkState.WaitingForBank -> stringResource(R.string.setup_sms_waiting_for_bank)
                cardLinkState == SetupCardLinkState.Checking -> stringResource(R.string.setup_sms_checking_cards)
                cardLinkState is SetupCardLinkState.Checked -> stringResource(R.string.setup_sms_cards_checked,
                    (cardLinkState as SetupCardLinkState.Checked).linked)
                cardLinkState == SetupCardLinkState.Failed -> stringResource(R.string.setup_sms_check_failed)
                else -> null
            }
        val actions = when (stage) {
            SetupStage.Banks -> listOf(
                SetupAction("Credo", bankStatus(BankSmsBank.CREDO)) { open("credo") },
                SetupAction("TBC", bankStatus(BankSmsBank.TBC)) { open("tbc") },
                SetupAction(stringResource(R.string.app_lock_title), if (appLockHasPin) stringResource(R.string.setup_lock_set) else null) { open("lock") },
                SetupAction(stringResource(R.string.statements_title)) { open("statements") },
                SetupAction(stringResource(R.string.personal_setup_restore_title)) { open("backup") },
            )
            SetupStage.Sms -> listOf(
                SetupAction(stringResource(R.string.setup_sms_bank, "Credo"), smsStatus(credoSmsEnabled)) { messages(BankSmsBank.CREDO) },
                SetupAction(stringResource(R.string.setup_sms_bank, "TBC"), smsStatus(tbcSmsEnabled)) { messages(BankSmsBank.TBC) },
                SetupAction(stringResource(R.string.setup_sms_review), smsReviewStatus) { messages(null) },
                SetupAction(stringResource(R.string.setup_tbc_push)) { openSettings("bank:TBC") },
            )
            SetupStage.Accounts -> listOf(
                SetupAction(stringResource(R.string.personal_setup_cash_add_action),
                    stringResource(R.string.setup_cash_action_body)) { showCashSheet = true },
                SetupAction(stringResource(R.string.setup_review_accounts),
                    overview?.let { stringResource(R.string.setup_count_accounts, it.accounts.size) }) { open("accounts") },
                SetupAction(stringResource(R.string.sms_diagnostics_title), overview?.unrouted?.takeIf { it > 0 }?.let { stringResource(R.string.setup_needs_account, it) }) { messages(null) },
            )
            SetupStage.Categories -> listOf(
                SetupAction(stringResource(R.string.category_setup_title)) { open("suggestions") },
                SetupAction(stringResource(R.string.categories_title), saved(overview?.categories)) { open("categories") },
                SetupAction(stringResource(R.string.category_intelligence_title), overview?.uncategorized?.takeIf { it > 0 }?.let { stringResource(R.string.setup_needs_category, it) }) { open("intelligence") },
            )
            SetupStage.Income -> listOf(SetupAction(stringResource(R.string.income_sources_title), saved(overview?.incomes)) { open("income") })
            SetupStage.Plans -> listOf(
                SetupAction(stringResource(R.string.savings_title), saved(overview?.savingsPlans)) { open("savings") },
                SetupAction(stringResource(R.string.debts_title), saved(overview?.debts)) { open("debts") },
            )
            SetupStage.Preferences -> listOf(
                SetupAction(stringResource(R.string.settings_application)) { openSettings("app") },
                SetupAction(stringResource(R.string.app_lock_title), if (appLockHasPin) stringResource(R.string.setup_lock_set) else null) { open("lock") },
                SetupAction(stringResource(R.string.backup_title)) { open("backup") },
            )
            SetupStage.Ready -> (if (reviewCount != null && reviewCount > 0) listOf(
                SetupAction(stringResource(R.string.data_health_title)) { open("health") },
                SetupAction(stringResource(R.string.sms_diagnostics_title), overview?.unrouted?.takeIf { it > 0 }?.let { stringResource(R.string.setup_needs_account, it) }) { messages(null) },
            ) else emptyList()) + listOf(SetupAction(stringResource(R.string.setup_edit_steps)) { showSteps = !showSteps }) +
                (if (showSteps) SetupStage.entries.filter { it != SetupStage.Ready }.map { target ->
                SetupAction(stringResource(target.title)) { stage = target; showSteps = false }
            } else emptyList())
        }
        SetupStageScreen(stage, actions,
            onBack = { if (stage.ordinal == 0) onExit() else stage = SetupStage.entries[stage.ordinal - 1] },
            onContinue = {
                if (stage == SetupStage.Ready) onContinue(0, false)
                else stage = SetupStage.entries[stage.ordinal + 1]
            },
            continueLabel = if (stage == SetupStage.Ready && overview?.allChecked == false)
                stringResource(R.string.setup_continue_unchecked) else null,
            content = if (stage == SetupStage.Ready) ({
                SetupAccountReviews(overviewState, overviewModel::retry, overviewModel::check, ::account)
            }) else null,
        )
        if (showCashSheet) {
            val accountsModel: AccountsViewModel = viewModel()
            val formState by accountsModel.formSaveState.collectAsState()
            OnFormSaved(formState) { showCashSheet = false }
            AddAccountSheet(
                onDismiss = { showCashSheet = false },
                onImportStatement = {},
                onConfirm = { name, type, currency, provider, opening ->
                    accountsModel.addAccount(name, type, currency, provider, opening)
                },
                cashOnly = true,
                titleOverride = stringResource(R.string.personal_setup_cash_sheet_title),
                formState = formState,
            )
        }
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
    val setupBack: () -> Unit = {
        if (destination != "settings" || settings.page == settingsEntry || !settings.back()) {
            createPin = false
            back()
        }
    }
    PersonalSetupSecondaryPage(title, onBack = setupBack,
        header = if (destination == "settings") ({ SettingsSearchHeader(settings, setupBack) }) else null,
    ) {
        when (destination) {
            "credo" -> CredoSyncRoute(canStoreCredentials = appLockHasPin,
                initialRememberPassword = rememberCredo, onOpenAppLock = ::lockForCredo,
                autoLoadFullHistory = true, onGuidedHistoryComplete = ::back, onDone = ::back,
                onContinueDuringSync = ::back)
            "tbc" -> TbcLoginRoute(appLockHasPin, false, onOpenStatements = { open("statements") }, onDone = ::back)
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
                autoScanHistory = stage == SetupStage.Sms,
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
    val formState by viewModel.formSaveState.collectAsState()
    val ready = state as? AccountsScreenState.Ready
    if (ready == null) dev.whekin.whfin.core.ui.WhfinLoadingIndicator()
    else DebtLedgerDialog(ready.debts, people, ready.accounts.map { it.account }, onBack,
        viewModel::openDebt, viewModel::settleDebt, formState)
}
