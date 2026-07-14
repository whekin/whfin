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
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.core.ui.WhfinSwitch
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import dev.whekin.whfin.ui.theme.WhfinTheme
import dev.whekin.whfin.data.preferences.AppLockTimeout
import dev.whekin.whfin.data.preferences.WidgetColorMode

@Composable
fun SettingsScreen(
    widgetColorMode: WidgetColorMode,
    onWidgetColorModeChange: (WidgetColorMode) -> Unit,
    smsImportEnabled: Boolean,
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
) {
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WhfinSectionLabel(stringResource(R.string.settings_appearance))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
            WhfinLedgerRow(
                title = stringResource(R.string.settings_widget_system_colors),
                supportingText = stringResource(
                    if (widgetColorMode == WidgetColorMode.System) {
                        R.string.settings_widget_system_colors_body
                    } else {
                        R.string.settings_widget_whfin_colors_body
                    },
                ),
                supportingMaxLines = 3,
                icon = Icons.Default.Palette,
                trailing = {
                    WhfinSwitch(
                        checked = widgetColorMode == WidgetColorMode.System,
                        onCheckedChange = { enabled ->
                            onWidgetColorModeChange(
                                if (enabled) WidgetColorMode.System else WidgetColorMode.Whfin,
                            )
                        },
                        contentDescription = stringResource(R.string.settings_widget_system_colors_toggle),
                    )
                },
            )
        }

        WhfinSectionLabel(stringResource(R.string.settings_automation))
        WhfinLedgerGroup(Modifier.fillMaxWidth()) {
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
                supportingText = stringResource(R.string.sms_diagnostics_settings_summary),
                icon = Icons.Default.Sms,
                trailing = { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                onClick = onOpenSmsDiagnostics,
            )
        }
        WhfinNotice(
            title = stringResource(R.string.settings_sms_title),
            body = stringResource(
                if (smsImportEnabled) R.string.settings_sms_body else R.string.settings_sms_disabled_body,
            ),
            icon = Icons.Default.Sms,
            kind = when {
                !smsImportEnabled -> WhfinNoticeKind.Unavailable
                hasSmsPermission -> WhfinNoticeKind.Info
                else -> WhfinNoticeKind.Attention
            },
            actionLabel = if (!smsImportEnabled || hasSmsPermission) null else stringResource(
                if (canRequestSmsPermission) R.string.permission_allow else R.string.permission_open_settings,
            ),
            onAction = if (!smsImportEnabled || hasSmsPermission) null else if (canRequestSmsPermission) onRequestSmsPermission else onOpenSystemSettings,
            modifier = Modifier.fillMaxWidth(),
            trailing = {
                WhfinSwitch(
                    checked = smsImportEnabled,
                    onCheckedChange = { enabled ->
                        onSmsImportEnabledChange(enabled)
                        if (enabled && !hasSmsPermission) {
                            if (canRequestSmsPermission) onRequestSmsPermission() else onOpenSystemSettings()
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
                supportingText = stringResource(R.string.backup_settings_summary),
                icon = Icons.Default.SaveAlt,
                trailing = { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                onClick = onOpenBackup,
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
                widgetColorMode = WidgetColorMode.System,
                onWidgetColorModeChange = {},
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
                widgetColorMode = WidgetColorMode.Whfin,
                onWidgetColorModeChange = {},
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
