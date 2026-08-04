package dev.whekin.whfin.ui.setup

import androidx.compose.runtime.Composable
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

private enum class PersonalSetupPage { Home, CredoSync, BankSms, Statements, Backup, AppLock }

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
    var page by rememberSaveable { mutableStateOf(PersonalSetupPage.Home) }
    when (page) {
        PersonalSetupPage.Home -> PersonalSetupScreen(
            state = state,
            onConnectCredo = { page = PersonalSetupPage.CredoSync },
            onEnableSmsMonitoring = onEnableSmsMonitoring,
            onOpenBankSms = { page = PersonalSetupPage.BankSms },
            onImportStatement = { page = PersonalSetupPage.Statements },
            onCreateAccount = { onContinue(1, true) },
            onRestoreBackup = { page = PersonalSetupPage.Backup },
            onContinue = { onContinue(0, false) },
            onExit = onExit,
        )
        PersonalSetupPage.CredoSync -> PersonalSetupSecondaryPage(
            title = stringResource(R.string.credo_sync_title),
            onBack = { page = PersonalSetupPage.Home },
        ) {
            CredoSyncRoute(
                appLockEnabled = appLockHasPin && appLockTimeout.enabled,
                onOpenAppLock = { page = PersonalSetupPage.AppLock },
            )
        }
        PersonalSetupPage.BankSms -> PersonalSetupSecondaryPage(
            title = stringResource(R.string.sms_diagnostics_title),
            onBack = { page = PersonalSetupPage.Home },
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
            )
        }
        PersonalSetupPage.Statements -> PersonalSetupSecondaryPage(
            title = stringResource(R.string.statements_title),
            onBack = { page = PersonalSetupPage.Home },
        ) {
            BankStatementsScreen()
        }
        PersonalSetupPage.Backup -> PersonalSetupSecondaryPage(
            title = stringResource(R.string.backup_title),
            onBack = { page = PersonalSetupPage.Home },
        ) {
            BackupRoute(appVersion = appVersion)
        }
        PersonalSetupPage.AppLock -> PersonalSetupSecondaryPage(
            title = stringResource(R.string.app_lock_title),
            onBack = { page = PersonalSetupPage.CredoSync },
        ) {
            AppLockScreen(
                timeout = appLockTimeout,
                hasPin = appLockHasPin,
                biometricAvailability = biometricAvailability,
                biometricEnabled = biometricUnlockEnabled,
                onTimeoutChange = onAppLockTimeoutChange,
                onPinCreated = onAppLockPinCreated,
                onBiometricEnabledChange = onBiometricUnlockEnabledChange,
                onOpenBiometricSettings = onOpenBiometricSettings,
            )
        }
    }
}
