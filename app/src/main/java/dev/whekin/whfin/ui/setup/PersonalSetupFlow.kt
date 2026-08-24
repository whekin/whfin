package dev.whekin.whfin.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.whekin.whfin.R
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.security.BiometricAvailability
import dev.whekin.whfin.ui.settings.AppLockScreen
import dev.whekin.whfin.ui.settings.BackupRoute
import dev.whekin.whfin.ui.settings.BankStatementsScreen
import dev.whekin.whfin.ui.settings.CredoSyncRoute
import dev.whekin.whfin.ui.settings.IncomeSourceSheet
import dev.whekin.whfin.ui.settings.SmsDiagnosticsRoute
import dev.whekin.whfin.ui.accounts.AddAccountSheet

internal enum class PersonalSetupPage {
    Bank,
    Categories,
    /** Legacy generic-account page retained for restored state; new setup uses Cash then Salary. */
    Accounts,
    Cash,
    Salary,
    Ready,
    Alternative,
    CredoSync,
    BankSms,
    Statements,
    Backup,
    AppLock,
}

internal fun personalSetupPageAfterAppLock(): PersonalSetupPage = PersonalSetupPage.CredoSync

internal fun personalSetupPageAfterCash(): PersonalSetupPage = PersonalSetupPage.Salary

internal fun personalSetupPageAfterSalary(): PersonalSetupPage = PersonalSetupPage.Ready

/**
 * Categories come after the ambiguities and before the optional accounts.
 *
 * The order is what makes the proposals worth anything: they are read from the history the previous
 * steps just finished importing, so asking earlier would offer a preset with no evidence behind it.
 */
internal fun personalSetupResolutionPage(state: PersonalSetupState): PersonalSetupPage? = when {
    state.reviewCount == null -> null
    (state.unresolvedSmsCount ?: 0) > 0 -> PersonalSetupPage.BankSms
    (state.statementReviewCount ?: 0) > 0 -> PersonalSetupPage.Statements
    else -> PersonalSetupPage.Categories
}

internal fun personalSetupPageAfterBankConsent(state: PersonalSetupState): PersonalSetupPage? = when {
    !state.smsReady -> null
    state.hasCredoImport != true -> PersonalSetupPage.CredoSync
    else -> personalSetupResolutionPage(state)
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
    /** Personal ledgers shown in the salary declaration's account rail. */
    accounts: List<AccountEntity> = emptyList(),
    onSaveCash: (String, String, Long?) -> Unit = { _, _, _ -> },
    onSaveSalary: (String, Long, String, Long?, Int, Int) -> Unit = { _, _, _, _, _, _ -> },
    onContinue: (initialTab: Int, openAccountAdd: Boolean) -> Unit,
    onExit: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(PersonalSetupPage.Bank) }
    var advanceAfterSmsPermission by rememberSaveable { mutableStateOf(false) }
    var guidedResolutionActive by rememberSaveable { mutableStateOf(false) }
    var statementsReturnPage by rememberSaveable { mutableStateOf(PersonalSetupPage.Alternative) }
    var cashEditorOpen by rememberSaveable { mutableStateOf(false) }
    var salaryEditorOpen by rememberSaveable { mutableStateOf(false) }
    var rememberPasswordRequested by rememberSaveable { mutableStateOf(false) }
    var appLockSetupTarget by rememberSaveable { mutableStateOf<AppLockTimeout?>(null) }

    LaunchedEffect(page) {
        cashEditorOpen = page == PersonalSetupPage.Cash
        salaryEditorOpen = page == PersonalSetupPage.Salary
    }

    fun continueBankSetup() {
        val next = personalSetupPageAfterBankConsent(state)
        if (next != null) {
            if (state.hasCredoImport == true) {
                guidedResolutionActive = true
            }
            page = next
        } else {
            advanceAfterSmsPermission = true
            onEnableSmsMonitoring()
        }
    }

    fun continueAfterHistory() {
        guidedResolutionActive = true
        personalSetupResolutionPage(state)?.let { page = it }
    }

    LaunchedEffect(page, advanceAfterSmsPermission, state.smsReady) {
        if (page == PersonalSetupPage.Bank && advanceAfterSmsPermission && state.smsReady) {
            advanceAfterSmsPermission = false
            personalSetupPageAfterBankConsent(state)?.let { next ->
                if (state.hasCredoImport == true) {
                    guidedResolutionActive = true
                }
                page = next
            }
        }
    }
    LaunchedEffect(
        page,
        guidedResolutionActive,
        state.unresolvedSmsCount,
        state.statementReviewCount,
    ) {
        if (!guidedResolutionActive) return@LaunchedEffect
        val next = personalSetupResolutionPage(state) ?: return@LaunchedEffect
        val shouldAdvance = when (page) {
            PersonalSetupPage.CredoSync -> true
            PersonalSetupPage.BankSms -> next != PersonalSetupPage.BankSms
            PersonalSetupPage.Statements -> next == PersonalSetupPage.Categories
            else -> false
        }
        if (shouldAdvance) page = next
    }

    when (page) {
        PersonalSetupPage.Bank -> PersonalSetupScreen(
            step = PersonalSetupStep.Bank,
            state = state,
            onConnectBank = ::continueBankSetup,
            onShowAlternatives = { page = PersonalSetupPage.Alternative },
            onImportStatement = {},
            onCreateAccount = { onContinue(1, true) },
            onRestoreBackup = {},
            onSkip = { page = PersonalSetupPage.Cash },
            onContinue = { onContinue(0, false) },
            onBack = onExit,
        )
        PersonalSetupPage.Categories -> CategorySetupStep(
            onContinue = {
                guidedResolutionActive = false
                page = PersonalSetupPage.Cash
            },
            onBack = {
                guidedResolutionActive = false
                page = PersonalSetupPage.Bank
            },
        )
        PersonalSetupPage.Accounts -> PersonalSetupScreen(
            step = PersonalSetupStep.Accounts,
            state = state,
            onConnectBank = {},
            onShowAlternatives = {},
            onImportStatement = {},
            onCreateAccount = { onContinue(1, true) },
            onRestoreBackup = {},
            onSkip = { page = PersonalSetupPage.Cash },
            onContinue = { onContinue(0, false) },
            onBack = { page = PersonalSetupPage.Categories },
        )
        PersonalSetupPage.Cash -> PersonalSetupScreen(
            step = PersonalSetupStep.Cash,
            state = state,
            onConnectBank = {},
            onShowAlternatives = {},
            onImportStatement = {},
            onCreateAccount = { cashEditorOpen = true },
            onRestoreBackup = {},
            onSkip = {
                cashEditorOpen = false
                page = personalSetupPageAfterCash()
            },
            onContinue = { onContinue(0, false) },
            onBack = {
                cashEditorOpen = false
                page = PersonalSetupPage.Categories
            },
        )
        PersonalSetupPage.Salary -> PersonalSetupScreen(
            step = PersonalSetupStep.Salary,
            state = state,
            onConnectBank = {},
            onShowAlternatives = {},
            onImportStatement = {},
            onCreateAccount = { salaryEditorOpen = true },
            onRestoreBackup = {},
            onSkip = {
                salaryEditorOpen = false
                page = personalSetupPageAfterSalary()
            },
            onContinue = { onContinue(0, false) },
            onBack = {
                salaryEditorOpen = false
                page = PersonalSetupPage.Cash
            },
        )
        PersonalSetupPage.Ready -> PersonalSetupScreen(
            step = PersonalSetupStep.Ready,
            state = state,
            onConnectBank = {},
            onShowAlternatives = {},
            onImportStatement = {},
            onCreateAccount = { onContinue(1, true) },
            onRestoreBackup = {},
            onSkip = {},
            onContinue = { onContinue(0, false) },
            onBack = { page = PersonalSetupPage.Salary },
        )
        PersonalSetupPage.Alternative -> PersonalSetupScreen(
            step = PersonalSetupStep.Alternative,
            state = state,
            onConnectBank = {},
            onShowAlternatives = {},
            onImportStatement = {
                statementsReturnPage = PersonalSetupPage.Alternative
                page = PersonalSetupPage.Statements
            },
            onCreateAccount = {},
            onRestoreBackup = { page = PersonalSetupPage.Backup },
            onSkip = { page = PersonalSetupPage.Cash },
            onContinue = {},
            onBack = { page = PersonalSetupPage.Bank },
        )
        PersonalSetupPage.CredoSync -> PersonalSetupSecondaryPage(
            title = stringResource(R.string.credo_sync_title),
            onBack = { page = PersonalSetupPage.Bank },
        ) {
            CredoSyncRoute(
                appLockEnabled = appLockHasPin && appLockTimeout.enabled,
                initialRememberPassword = rememberPasswordRequested,
                onOpenAppLock = {
                    rememberPasswordRequested = true
                    appLockSetupTarget = AppLockTimeout.Immediate
                    page = PersonalSetupPage.AppLock
                },
                autoLoadFullHistory = true,
                onGuidedHistoryComplete = ::continueAfterHistory,
                onDone = ::continueAfterHistory,
            )
        }
        PersonalSetupPage.BankSms -> PersonalSetupSecondaryPage(
            title = stringResource(R.string.sms_diagnostics_title),
            onBack = {
                guidedResolutionActive = false
                page = PersonalSetupPage.Bank
            },
        ) {
            SmsDiagnosticsRoute(
                appVersion = appVersion,
                smsImportEnabled = state.smsMonitoringEnabled,
                hasReceivePermission = state.hasSmsPermission,
                canRequestReceivePermission = state.canRequestSmsPermission,
                hasHistoryPermission = hasSmsHistoryPermission,
                canRequestHistoryPermission = canRequestSmsHistoryPermission,
                onEnableMonitoring = onEnableSmsMonitoring,
                onRequestReceivePermission = onRequestSmsPermission,
                onOpenFeed = { onContinue(0, false) },
                onRequestHistoryPermission = onRequestSmsHistoryPermission,
                onOpenSystemSettings = onOpenSystemSettings,
                onCardLinked = {},
            )
        }
        PersonalSetupPage.Statements -> PersonalSetupSecondaryPage(
            title = stringResource(R.string.statements_title),
            onBack = {
                if (guidedResolutionActive) {
                    guidedResolutionActive = false
                    page = PersonalSetupPage.Bank
                } else {
                    page = statementsReturnPage
                }
            },
        ) {
            BankStatementsScreen()
        }
        PersonalSetupPage.Backup -> PersonalSetupSecondaryPage(
            title = stringResource(R.string.backup_title),
            onBack = { page = PersonalSetupPage.Alternative },
        ) {
            BackupRoute(appVersion = appVersion)
        }
        PersonalSetupPage.AppLock -> PersonalSetupSecondaryPage(
            title = stringResource(R.string.app_lock_title),
            onBack = {
                appLockSetupTarget = null
                page = personalSetupPageAfterAppLock()
            },
        ) {
            AppLockScreen(
                timeout = appLockSetupTarget ?: appLockTimeout,
                hasPin = appLockHasPin,
                biometricAvailability = biometricAvailability,
                biometricEnabled = biometricUnlockEnabled,
                onTimeoutChange = onAppLockTimeoutChange,
                onPinCreated = { pin, timeout ->
                    onAppLockPinCreated(pin, timeout)
                    appLockSetupTarget = null
                    page = personalSetupPageAfterAppLock()
                },
                onBiometricEnabledChange = onBiometricUnlockEnabledChange,
                onOpenBiometricSettings = onOpenBiometricSettings,
                autoSetupTimeout = appLockSetupTarget,
            )
        }
    }

    if (page == PersonalSetupPage.Cash && cashEditorOpen) {
        AddAccountSheet(
            onDismiss = { cashEditorOpen = false },
            onImportStatement = {},
            initialType = AccountType.CASH,
            cashOnly = true,
            titleOverride = stringResource(R.string.personal_setup_cash_sheet_title),
            onConfirm = { name, _, currency, _, openingMinor ->
                onSaveCash(name, currency, openingMinor)
                cashEditorOpen = false
                page = personalSetupPageAfterCash()
            },
        )
    }

    if (page == PersonalSetupPage.Salary && salaryEditorOpen) {
        IncomeSourceSheet(
            source = null,
            accounts = accounts,
            onDismiss = { salaryEditorOpen = false },
            onSave = { label, amountMinor, currency, accountId, dayFrom, dayTo ->
                onSaveSalary(label, amountMinor, currency, accountId, dayFrom, dayTo)
                salaryEditorOpen = false
                page = personalSetupPageAfterSalary()
            },
            onEnd = null,
            onDelete = null,
            initialLabel = stringResource(R.string.personal_setup_salary_default_label),
            initialCurrency = "GEL",
        )
    }
}
