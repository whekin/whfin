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
    val credoConnection by app.bankSync.credo.state.collectAsState()
    val tbcConnection by app.bankSync.tbc.state.collectAsState()
    val bankWorkActive = bankSyncStatuses.any { it.active }
    var stage by rememberSaveable { mutableStateOf(setupStageFromSaved(runtime.personalSetupStage)) }
    LaunchedEffect(stage) { runtime.personalSetupStage = stage.name }
    var showSteps by rememberSaveable { mutableStateOf(false) }
    var showCashSheet by rememberSaveable { mutableStateOf(false) }
    var cardLinkState by remember { mutableStateOf<SetupCardLinkState>(SetupCardLinkState.NotStarted) }
    var cardsAttempted by remember { mutableStateOf(false) }
    var cardLinkRetryKey by remember { mutableIntStateOf(0) }
    var stack by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var autoAdvanceBank by rememberSaveable { mutableStateOf<SetupPage?>(null) }
    var bankEntryRunId by rememberSaveable { mutableLongStateOf(0L) }
    var selectedTransaction by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedAccount by rememberSaveable { mutableLongStateOf(0L) }
    var categoryQueue by rememberSaveable { mutableStateOf<CategoryQueue?>(null) }
    var smsBank by rememberSaveable { mutableStateOf<BankSmsBank?>(null) }
    var rememberCredo by rememberSaveable { mutableStateOf(false) }
    var createPin by rememberSaveable { mutableStateOf(false) }
    var settingsEntry by rememberSaveable { mutableStateOf("connections") }
    val settings = rememberSettingsSearchState()
    val preferences = remember(context) { UiPreferences(context) }
    val credoSmsEnabled by remember(preferences) { preferences.bankSmsEnabled(BankSmsBank.CREDO) }.collectAsState(initial = false)
    val tbcSmsEnabled by remember(preferences) { preferences.bankSmsEnabled(BankSmsBank.TBC) }.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val destination = SetupPage.fromSaved(stack.lastOrNull())
    val reviewCount = state.unresolvedSmsCount?.let { sms -> state.statementReviewCount?.let { sms + it } }
    LaunchedEffect(stage, hasSmsHistoryPermission, destination, bankWorkActive, cardLinkRetryKey) {
        if (bankWorkActive) {
            cardsAttempted = false
            if (stage == SetupStage.Sms) cardLinkState = SetupCardLinkState.WaitingForBank
            return@LaunchedEffect
        }
        if (stage != SetupStage.Sms) return@LaunchedEffect
        if (!hasSmsHistoryPermission) {
            cardsAttempted = false
            cardLinkState = SetupCardLinkState.NotStarted
            return@LaunchedEffect
        }
        if (destination != null || cardsAttempted) return@LaunchedEffect
        cardLinkState = SetupCardLinkState.Checking
        cardLinkState = try {
            val result = withContext(Dispatchers.IO) { SmsInboxCardLinker.run(app, app.userDb) }
            cardsAttempted = true
            SetupCardLinkState.Checked(result.cardsLinked)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            cardsAttempted = true
            SetupCardLinkState.Failed
        }
    }
    fun open(page: SetupPage) {
        if (page == SetupPage.Intelligence) categoryQueue = null
        if (page == SetupPage.Credo || page == SetupPage.Tbc) {
            val needsSignIn = if (page == SetupPage.Credo)
                credoConnection.stage == CredoSyncStage.Disconnected
            else tbcConnection.stage == TbcLoginStage.Login
            autoAdvanceBank = page.takeIf { needsSignIn }
            bankEntryRunId = bankSyncStatuses.firstOrNull {
                it.bank == if (page == SetupPage.Credo) "Credo" else "TBC"
            }?.runId ?: 0L
        }
        stack = stack + page.savedKey
    }
    fun back() { stack = stack.dropLast(1) }
    fun finishBank(page: SetupPage) {
        val firstConnection = autoAdvanceBank == page
        autoAdvanceBank = null
        val next = returnFromBankPage(stack, stage, page, firstConnection)
        stack = next.stack
        stage = next.stage
    }
    fun openSettings(page: String) {
        settingsEntry = page
        settings.open(page)
        open(SetupPage.Settings)
    }
    fun messages(bank: BankSmsBank?) { smsBank = bank; open(SetupPage.Messages) }
    fun account(id: Long) { selectedAccount = id; open(SetupPage.Account) }
    fun lockForCredo() { rememberCredo = true; createPin = true; open(SetupPage.Lock) }

    val overviewModel: SetupOverviewViewModel = viewModel()
    val overviewState by overviewModel.state.collectAsState()
    val overview = (overviewState as? SetupOverviewState.Ready)?.value
    val credoRuntime = bankSyncStatuses.firstOrNull { it.bank == "Credo" }
    val tbcRuntime = bankSyncStatuses.firstOrNull { it.bank == "TBC" }
    val credoProgress = setupBankProgress(credoRuntime,
        overview?.bankAccounts?.get(BankSmsBank.CREDO) ?: 0,
        BankSmsBank.CREDO in overview?.bankImports.orEmpty())
    val tbcProgress = setupBankProgress(tbcRuntime,
        overview?.bankAccounts?.get(BankSmsBank.TBC) ?: 0,
        BankSmsBank.TBC in overview?.bankImports.orEmpty(),
        tbcConnection.syncResult?.needsStatement?.size ?: 0)
    val credoProgressText = credoProgress.label()
    val tbcProgressText = tbcProgress.label()
    LaunchedEffect(autoAdvanceBank, destination, bankEntryRunId, bankSyncStatuses) {
        val page = autoAdvanceBank ?: return@LaunchedEffect
        val status = if (page == SetupPage.Credo) credoRuntime else tbcRuntime
        if (shouldAdvanceAfterBankSignIn(page, destination, bankEntryRunId, status)) finishBank(page)
    }

    if (destination == null) {
        @Composable fun saved(count: Int?) = count?.let { stringResource(R.string.setup_count_saved, it) }
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
                SetupAction("Credo", credoProgressText) { open(SetupPage.Credo) },
                SetupAction("TBC", tbcProgressText) { open(SetupPage.Tbc) },
                SetupAction(stringResource(R.string.app_lock_title), if (appLockHasPin) stringResource(R.string.setup_lock_set) else null) { open(SetupPage.Lock) },
                SetupAction(stringResource(R.string.statements_title)) { open(SetupPage.Statements) },
                SetupAction(stringResource(R.string.personal_setup_restore_title)) { open(SetupPage.Backup) },
            )
            SetupStage.Sms -> listOf(
                SetupAction(stringResource(R.string.setup_sms_bank, "Credo"), smsStatus(credoSmsEnabled)) { messages(BankSmsBank.CREDO) },
                SetupAction(stringResource(R.string.setup_sms_bank, "TBC"), smsStatus(tbcSmsEnabled)) { messages(BankSmsBank.TBC) },
                SetupAction(stringResource(R.string.setup_sms_review), smsReviewStatus) { messages(null) },
                SetupAction(stringResource(R.string.setup_tbc_push)) { openSettings("bank:TBC") },
            ) + if (cardLinkState == SetupCardLinkState.Failed) listOf(
                SetupAction(stringResource(R.string.setup_sms_retry_cards)) {
                    cardsAttempted = false
                    cardLinkRetryKey++
                },
            ) else emptyList()
            SetupStage.Accounts -> listOf(
                SetupAction(stringResource(R.string.personal_setup_cash_add_action),
                    stringResource(R.string.setup_cash_action_body)) { showCashSheet = true },
                SetupAction(stringResource(R.string.setup_review_accounts),
                    overview?.let { stringResource(R.string.setup_count_accounts, it.accounts.size) }) { open(SetupPage.Accounts) },
                SetupAction(stringResource(R.string.sms_diagnostics_title), overview?.unrouted?.takeIf { it > 0 }?.let { stringResource(R.string.setup_needs_account, it) }) { messages(null) },
            )
            SetupStage.Categories -> listOf(
                SetupAction(stringResource(R.string.category_setup_title)) { open(SetupPage.Suggestions) },
                SetupAction(stringResource(R.string.categories_title), saved(overview?.categories)) { open(SetupPage.Categories) },
                SetupAction(stringResource(R.string.category_intelligence_title), overview?.uncategorized?.takeIf { it > 0 }?.let { stringResource(R.string.setup_needs_category, it) }) { open(SetupPage.Intelligence) },
            )
            SetupStage.Income -> listOf(SetupAction(stringResource(R.string.income_sources_title), saved(overview?.incomes)) { open(SetupPage.Income) })
            SetupStage.Plans -> listOf(
                SetupAction(stringResource(R.string.savings_title), saved(overview?.savingsPlans)) { open(SetupPage.Savings) },
                SetupAction(stringResource(R.string.debts_title), saved(overview?.debts)) { open(SetupPage.Debts) },
            )
            SetupStage.Preferences -> listOf(
                SetupAction(stringResource(R.string.settings_application)) { openSettings("app") },
                SetupAction(stringResource(R.string.app_lock_title), if (appLockHasPin) stringResource(R.string.setup_lock_set) else null) { open(SetupPage.Lock) },
                SetupAction(stringResource(R.string.backup_title)) { open(SetupPage.Backup) },
            )
            SetupStage.Ready -> (if (reviewCount != null && reviewCount > 0) listOf(
                SetupAction(stringResource(R.string.data_health_title)) { open(SetupPage.Health) },
                SetupAction(stringResource(R.string.sms_diagnostics_title), overview?.unrouted?.takeIf { it > 0 }?.let { stringResource(R.string.setup_needs_account, it) }) { messages(null) },
            ) else emptyList()) + listOf(SetupAction(stringResource(R.string.setup_edit_steps)) { showSteps = !showSteps }) +
                (if (showSteps) SetupStage.entries.filter { it != SetupStage.Ready }.map { target ->
                SetupAction(stringResource(target.title)) { stage = target; showSteps = false }
            } else emptyList())
        }
        val oneBankConnected = credoProgress.kind != SetupBankProgressKind.NOT_CONNECTED ||
            tbcProgress.kind != SetupBankProgressKind.NOT_CONNECTED
        val followUps = if (stage == SetupStage.Banks) emptyList() else listOfNotNull(
            SetupAction("Credo", credoProgressText) { open(SetupPage.Credo) }
                .takeIf { credoProgress.needsAction || (stage == SetupStage.Sms && oneBankConnected &&
                    credoProgress.kind == SetupBankProgressKind.NOT_CONNECTED) },
            SetupAction("TBC", tbcProgressText) { open(SetupPage.Tbc) }
                .takeIf { tbcProgress.needsAction || (stage == SetupStage.Sms && oneBankConnected &&
                    tbcProgress.kind == SetupBankProgressKind.NOT_CONNECTED) },
        )
        val bankSummary = listOfNotNull(
            credoProgressText.takeIf { credoProgress.isNotable }
                ?.let { stringResource(R.string.setup_bank_progress_line, "Credo", it) },
            tbcProgressText.takeIf { tbcProgress.isNotable }
                ?.let { stringResource(R.string.setup_bank_progress_line, "TBC", it) },
        ).joinToString("\n").takeIf(String::isNotEmpty)
        SetupStageScreen(stage, followUps + actions,
            onBack = { if (stage.ordinal == 0) onExit() else stage = SetupStage.entries[stage.ordinal - 1] },
            onContinue = {
                if (stage == SetupStage.Ready) onContinue(0, false)
                else stage = SetupStage.entries[stage.ordinal + 1]
            },
            continueLabel = when {
                stage != SetupStage.Ready -> null
                credoProgress.needsAction || tbcProgress.needsAction ->
                    stringResource(R.string.setup_start_with_bank_pending)
                bankWorkActive -> stringResource(R.string.setup_start_while_bank_loads)
                overview?.allChecked == false -> stringResource(R.string.setup_continue_unchecked)
                else -> null
            },
            summary = bankSummary.takeUnless { stage == SetupStage.Banks },
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
    if (destination == SetupPage.Account) {
        AccountTransactionsScreen(selectedAccount, ::back)
        return
    }
    if (destination == SetupPage.Suggestions) {
        CategorySetupStep(onContinue = { back(); stage = SetupStage.Income }, onBack = ::back,
            bankHistoryPending = credoProgress.historyPending || tbcProgress.historyPending)
        return
    }
    val title = when (destination) {
        SetupPage.Account, SetupPage.Accounts, SetupPage.Overview -> stringResource(R.string.setup_accounts_title)
        SetupPage.Suggestions -> stringResource(R.string.category_setup_title)
        SetupPage.Credo -> stringResource(R.string.credo_sync_title)
        SetupPage.Tbc -> stringResource(R.string.tbc_title)
        SetupPage.Lock -> stringResource(R.string.app_lock_title)
        SetupPage.Settings -> settingsPageTitle(settings)
        SetupPage.Statements -> stringResource(R.string.statements_title)
        SetupPage.Backup -> stringResource(R.string.backup_title)
        SetupPage.Messages -> stringResource(R.string.sms_diagnostics_title)
        SetupPage.Income -> stringResource(R.string.setup_income_title)
        SetupPage.Savings -> stringResource(R.string.savings_title)
        SetupPage.Debts -> stringResource(R.string.debts_title)
        SetupPage.Categories -> stringResource(R.string.categories_title)
        SetupPage.Intelligence -> categoryQueue?.let { categoryQueueTitle(it) }
            ?: stringResource(R.string.category_intelligence_title)
        SetupPage.People -> stringResource(R.string.people_title)
        SetupPage.Privacy -> stringResource(R.string.privacy_title)
        SetupPage.About -> stringResource(R.string.about_title)
        SetupPage.Corrections -> stringResource(R.string.corrections_title)
        SetupPage.Health -> stringResource(R.string.data_health_title)
        SetupPage.History -> stringResource(R.string.transactions_history_title)
        SetupPage.Push -> stringResource(R.string.push_title)
    }
    val setupBack: () -> Unit = {
        if (destination == SetupPage.Intelligence && categoryQueue != null) {
            categoryQueue = null
        } else if (destination != SetupPage.Settings || settings.page == settingsEntry || !settings.back()) {
            if (destination == SetupPage.Credo || destination == SetupPage.Tbc) autoAdvanceBank = null
            createPin = false
            back()
        }
    }
    PersonalSetupSecondaryPage(title, onBack = setupBack,
        header = if (destination == SetupPage.Settings) ({ SettingsSearchHeader(settings, setupBack) }) else null,
    ) {
        when (destination) {
            SetupPage.Credo -> CredoSyncRoute(canStoreCredentials = appLockHasPin,
                initialRememberPassword = rememberCredo, onOpenAppLock = ::lockForCredo,
                autoLoadFullHistory = true, onGuidedHistoryComplete = { finishBank(SetupPage.Credo) },
                onDone = { finishBank(SetupPage.Credo) },
                onContinueDuringSync = { finishBank(SetupPage.Credo) })
            SetupPage.Tbc -> TbcLoginRoute(appLockHasPin, false,
                onOpenStatements = { open(SetupPage.Statements) },
                onDone = { finishBank(SetupPage.Tbc) })
            SetupPage.Accounts -> AccountsScreen(
                onConnectBank = { open(if (it == "Credo") SetupPage.Credo else SetupPage.Tbc) },
                onOpenStatements = { open(SetupPage.Statements) }, onOpenSavings = { open(SetupPage.Savings) },
                onOpenOverview = { open(SetupPage.Overview) }, onOpenSettings = { openSettings("app") },
                onOpenAccountTransactions = ::account,
            )
            SetupPage.Overview -> AccountOverviewScreen()
            SetupPage.Categories -> CategoriesRoute()
            SetupPage.Intelligence -> CategoryIntelligenceRoute(
                queue = categoryQueue,
                onOpenQueue = { categoryQueue = it },
            )
            SetupPage.Income -> IncomeSourcesRoute(onOpenAccounts = { open(SetupPage.Accounts) })
            SetupPage.People -> PeopleRoute()
            SetupPage.Savings -> SavingsRoute(onOpenAccounts = { open(SetupPage.Accounts) })
            SetupPage.Debts -> SetupDebtsRoute(::back)
            SetupPage.Statements -> BankStatementsScreen()
            SetupPage.Backup -> BackupRoute(appVersion)
            SetupPage.Privacy -> PrivacyRoute(onOpenSystemSettings)
            SetupPage.About -> AboutScreen(appVersion = appVersion)
            SetupPage.Corrections -> CorrectionsScreen()
            SetupPage.Health -> DataHealthRoute(onOpenCorrections = { open(SetupPage.Corrections) }, onOpenBackup = { open(SetupPage.Backup) },
                onOpenTransaction = { selectedTransaction = it; open(SetupPage.History) })
            SetupPage.History -> dev.whekin.whfin.ui.feed.FeedScreen(mode = dev.whekin.whfin.ui.feed.FeedMode.HISTORY,
                showSmsOnboarding = false, onEnableSms = {}, onDismissSmsOnboarding = {},
                openTransactionId = selectedTransaction, onOpenTransactionConsumed = { selectedTransaction = null })
            SetupPage.Push -> PushJournalRoute(false, { messages(BankSmsBank.TBC) }, diagnosticsOnly = true)
            SetupPage.Messages -> SmsDiagnosticsRoute(
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
            SetupPage.Lock -> AppLockScreen(
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
            SetupPage.Settings -> SetupSettingsRoute(settings, state, appVersion, appLockTimeout, appLockHasPin,
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
