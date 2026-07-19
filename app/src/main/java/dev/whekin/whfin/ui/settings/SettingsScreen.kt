package dev.whekin.whfin.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
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

@Composable
fun SettingsScreen(
    appThemeMode: AppThemeMode = AppThemeMode.System,
    dynamicColorsEnabled: Boolean = true,
    onAppThemeModeChange: (AppThemeMode) -> Unit = {},
    onDynamicColorsEnabledChange: (Boolean) -> Unit = {},
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
    onOpenAppLock: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    appVersion: String,
    onOpenCredoSync: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenPeople: () -> Unit = {},
    demoMode: Boolean = false,
    developerMode: Boolean = false,
    runtimeModeBusy: Boolean = false,
    runtimeModeProblem: String? = null,
    onDemoModeChange: (Boolean) -> Unit = {},
    onResetDemoData: () -> Unit = {},
) {
    var confirmDemoReset by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WhfinNotice(
            title = stringResource(R.string.demo_mode_title),
            body = stringResource(if (demoMode) R.string.demo_mode_active_body else R.string.demo_mode_body),
            icon = Icons.Default.Science,
            kind = if (demoMode) WhfinNoticeKind.Info else WhfinNoticeKind.Unavailable,
            actionLabel = if (demoMode) stringResource(R.string.demo_mode_reset) else null,
            onAction = if (demoMode) ({ confirmDemoReset = true }) else null,
            trailing = {
                WhfinSwitch(
                    checked = demoMode,
                    onCheckedChange = onDemoModeChange,
                    contentDescription = stringResource(R.string.demo_mode_toggle),
                    enabled = !runtimeModeBusy,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (runtimeModeProblem != null) WhfinNotice(
            title = stringResource(R.string.demo_mode_problem_title),
            body = runtimeModeProblem,
            kind = WhfinNoticeKind.Error,
            modifier = Modifier.fillMaxWidth(),
        )

        WhfinSectionLabel(stringResource(R.string.settings_appearance))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            listOf(
                Triple(AppThemeMode.System, Icons.Default.BrightnessAuto, R.string.settings_theme_system),
                Triple(AppThemeMode.Light, Icons.Default.LightMode, R.string.settings_theme_light),
                Triple(AppThemeMode.Dark, Icons.Default.DarkMode, R.string.settings_theme_dark),
            ).forEachIndexed { index, (mode, icon, label) ->
                WhfinLedgerRow(
                    title = stringResource(label),
                    supportingText = stringResource(
                        when (mode) {
                            AppThemeMode.System -> R.string.settings_theme_system_body
                            AppThemeMode.Light -> R.string.settings_theme_light_body
                            AppThemeMode.Dark -> R.string.settings_theme_dark_body
                        },
                    ),
                    icon = icon,
                    trailing = if (appThemeMode == mode) {
                        { androidx.compose.material3.Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    onClick = { onAppThemeModeChange(mode) },
                    divider = true,
                )
            }
            WhfinLedgerRow(
                title = stringResource(R.string.settings_dynamic_colors),
                supportingText = stringResource(R.string.settings_dynamic_colors_body),
                supportingMaxLines = 3,
                icon = Icons.Default.Palette,
                trailing = {
                    WhfinSwitch(
                        checked = dynamicColorsEnabled,
                        onCheckedChange = onDynamicColorsEnabledChange,
                        contentDescription = stringResource(R.string.settings_dynamic_colors_toggle),
                    )
                },
            )
        }

        WhfinSectionLabel(stringResource(R.string.settings_catalog_section))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            WhfinLedgerRow(
                title = stringResource(R.string.categories_title),
                supportingText = stringResource(R.string.categories_settings_summary),
                icon = Icons.Default.Category,
                trailing = { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                onClick = onOpenCategories,
                divider = true,
            )
            WhfinLedgerRow(
                title = stringResource(R.string.people_title),
                supportingText = stringResource(R.string.people_settings_summary),
                icon = Icons.Default.Group,
                trailing = { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                onClick = onOpenPeople,
            )
        }

        WhfinSectionLabel(stringResource(R.string.settings_automation))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            WhfinLedgerRow(
                title = stringResource(R.string.credo_sync_title),
                supportingText = stringResource(
                    if (demoMode) R.string.demo_mode_live_import_unavailable else R.string.credo_sync_settings_summary,
                ),
                icon = Icons.Default.CloudSync,
                titleColor = if (demoMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                trailing = if (demoMode) null else ({ androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }),
                onClick = if (demoMode) null else onOpenCredoSync,
                divider = true,
            )
            WhfinLedgerRow(
                title = stringResource(R.string.statements_title),
                supportingText = stringResource(R.string.statements_settings_summary),
                icon = Icons.Default.Description,
                trailing = { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                onClick = onOpenStatements,
                divider = true,
            )
            WhfinLedgerRow(
                title = stringResource(R.string.sms_diagnostics_title),
                supportingText = stringResource(
                    if (demoMode) R.string.demo_mode_live_import_unavailable else R.string.sms_diagnostics_settings_summary,
                ),
                icon = Icons.Default.Sms,
                titleColor = if (demoMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                trailing = if (demoMode) null else ({ androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }),
                onClick = if (demoMode) null else onOpenSmsDiagnostics,
            )
        }
        if (demoMode) WhfinNotice(
            title = stringResource(R.string.demo_mode_automation_title),
            body = stringResource(R.string.demo_mode_automation_body),
            icon = Icons.Default.Sms,
            kind = WhfinNoticeKind.Unavailable,
            modifier = Modifier.fillMaxWidth(),
        ) else WhfinNotice(
            title = stringResource(R.string.settings_sms_title),
            body = stringResource(
                when {
                    !hasSmsCardMapping -> R.string.settings_sms_card_required_body
                    smsImportEnabled -> R.string.settings_sms_body
                    else -> R.string.settings_sms_disabled_body
                },
            ),
            icon = Icons.Default.Sms,
            kind = when {
                !hasSmsCardMapping -> WhfinNoticeKind.Attention
                !smsImportEnabled -> WhfinNoticeKind.Unavailable
                hasSmsPermission -> WhfinNoticeKind.Info
                else -> WhfinNoticeKind.Attention
            },
            actionLabel = when {
                !hasSmsCardMapping -> stringResource(R.string.sms_add_card_action)
                !smsImportEnabled || hasSmsPermission -> null
                else -> stringResource(
                    if (canRequestSmsPermission) R.string.permission_allow else R.string.permission_open_settings,
                )
            },
            onAction = when {
                !hasSmsCardMapping -> onOpenSmsDiagnostics
                !smsImportEnabled || hasSmsPermission -> null
                canRequestSmsPermission -> onRequestSmsPermission
                else -> onOpenSystemSettings
            },
            modifier = Modifier.fillMaxWidth(),
            trailing = {
                WhfinSwitch(
                    checked = smsImportEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !hasSmsCardMapping) {
                            onOpenSmsDiagnostics()
                        } else {
                            onSmsImportEnabledChange(enabled)
                            if (enabled && !hasSmsPermission) {
                                if (canRequestSmsPermission) onRequestSmsPermission() else onOpenSystemSettings()
                            }
                        }
                    },
                    contentDescription = stringResource(R.string.settings_sms_toggle),
                )
            },
        )

        WhfinSectionLabel(stringResource(R.string.settings_privacy_section))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            WhfinLedgerRow(
                title = stringResource(R.string.app_lock_title),
                supportingText = stringResource(
                    R.string.app_lock_settings_summary,
                    stringResource(appLockTimeout.labelResource()),
                ),
                icon = Icons.Default.Lock,
                trailing = { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                onClick = onOpenAppLock,
                divider = true,
            )
            WhfinLedgerRow(
                title = stringResource(R.string.backup_title),
                supportingText = stringResource(
                    if (demoMode) R.string.demo_mode_backup_unavailable else R.string.backup_settings_summary,
                ),
                icon = Icons.Default.SaveAlt,
                titleColor = if (demoMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                trailing = if (demoMode) null else ({ androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }),
                onClick = if (demoMode) null else onOpenBackup,
                divider = true,
            )
            WhfinLedgerRow(
                title = stringResource(R.string.privacy_title),
                supportingText = stringResource(R.string.privacy_settings_summary),
                icon = Icons.Default.PrivacyTip,
                trailing = { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                onClick = onOpenPrivacy,
            )
        }

        if (developerMode) {
            WhfinSectionLabel(stringResource(R.string.developer_mode_section))
            WhfinNotice(
                title = stringResource(R.string.developer_mode_enabled_title),
                body = stringResource(R.string.developer_mode_enabled_body),
                icon = Icons.Default.BugReport,
                kind = WhfinNoticeKind.Info,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        WhfinSectionLabel(stringResource(R.string.settings_about_section))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            WhfinLedgerRow(
                title = stringResource(R.string.about_title),
                supportingText = appVersion,
                icon = Icons.Default.Info,
                trailing = { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                onClick = onOpenAbout,
            )
        }
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

@Preview(name = "Settings light", widthDp = 400, heightDp = 800, showBackground = true)
@Preview(name = "Settings dark", widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Settings font 1.5", widthDp = 400, heightDp = 900, fontScale = 1.5f, showBackground = true)
@Preview(name = "Settings compact", widthDp = 400, heightDp = 500, showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    WhfinTheme {
        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
            SettingsScreen(
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
            SettingsScreen(
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
            SettingsScreen(
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
