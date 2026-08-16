package dev.whekin.whfin.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.whekin.whfin.R
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.security.BiometricAvailability
import dev.whekin.whfin.ui.settings.AppLockScreen
import dev.whekin.whfin.ui.settings.BackupRoute
import dev.whekin.whfin.ui.settings.BankStatementsScreen
import dev.whekin.whfin.ui.settings.CredoSyncRoute
import dev.whekin.whfin.ui.settings.SmsDiagnosticsRoute

internal enum class PersonalSetupPage {
    Bank,
    Categories,
    Accounts,
    Ready,
    Alternative,
    CredoSync,
    BankSms,
    Statements,
    Backup,
    AppLock,
}

internal fun personalSetupPageAfterAppLock(): PersonalSetupPage = PersonalSetupPage.CredoSync

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
    onContinue: (initialTab: Int, openAccountAdd: Boolean) -> Unit,
    onExit: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(PersonalSetupPage.Bank) }
    var advanceAfterSmsPermission by rememberSaveable { mutableStateOf(false) }
    var guidedResolutionActive by rememberSaveable { mutableStateOf(false) }
    var statementsReturnPage by rememberSaveable { mutableStateOf(PersonalSetupPage.Alternative) }

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
            onSkip = { page = PersonalSetupPage.Accounts },
            onContinue = { onContinue(0, false) },
            onBack = onExit,
        )
        PersonalSetupPage.Categories -> CategorySetupStep(
            onContinue = {
                guidedResolutionActive = false
                page = PersonalSetupPage.Accounts
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
            onSkip = { page = PersonalSetupPage.Ready },
            onContinue = { onContinue(0, false) },
            onBack = { page = PersonalSetupPage.Categories },
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
            onBack = { page = PersonalSetupPage.Accounts },
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
            onSkip = { page = PersonalSetupPage.Accounts },
            onContinue = {},
            onBack = { page = PersonalSetupPage.Bank },
        )
        PersonalSetupPage.CredoSync -> PersonalSetupSecondaryPage(
            title = stringResource(R.string.credo_sync_title),
            onBack = { page = PersonalSetupPage.Bank },
        ) {
            CredoSyncRoute(
                appLockEnabled = appLockHasPin && appLockTimeout.enabled,
                onOpenAppLock = { page = PersonalSetupPage.AppLock },
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
            onBack = { page = personalSetupPageAfterAppLock() },
        ) {
            AppLockScreen(
                timeout = appLockTimeout,
                hasPin = appLockHasPin,
                biometricAvailability = biometricAvailability,
                biometricEnabled = biometricUnlockEnabled,
                onTimeoutChange = onAppLockTimeoutChange,
                onPinCreated = { pin, timeout ->
                    onAppLockPinCreated(pin, timeout)
                    page = personalSetupPageAfterAppLock()
                },
                onBiometricEnabledChange = onBiometricUnlockEnabledChange,
                onOpenBiometricSettings = onOpenBiometricSettings,
            )
        }
    }
}
