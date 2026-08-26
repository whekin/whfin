package dev.whekin.whfin.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.CloudSync
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
import dev.whekin.whfin.ui.demo.DemoEntrySheet

@Composable
fun SettingsScreen(
    appThemeMode: AppThemeMode = AppThemeMode.System,
    dynamicColorsEnabled: Boolean = false,
    useSystemFont: Boolean = false,
    quickExpenseKeypadEnabled: Boolean = true,
    widgetOpenAppButtonEnabled: Boolean = true,
    onAppThemeModeChange: (AppThemeMode) -> Unit = {},
    onDynamicColorsEnabledChange: (Boolean) -> Unit = {},
    onUseSystemFontChange: (Boolean) -> Unit = {},
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
    onOpenAppLock: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenCorrections: () -> Unit = {},
    onOpenDataHealth: () -> Unit = {},
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    appVersion: String,
    onOpenCredoSync: () -> Unit = {},
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
) {
    val viewModel: SettingsViewModel = viewModel()
    val status by viewModel.status.collectAsState()
    SettingsContent(
        status = status,
        appThemeMode = appThemeMode,
        dynamicColorsEnabled = dynamicColorsEnabled,
        useSystemFont = useSystemFont,
        quickExpenseKeypadEnabled = quickExpenseKeypadEnabled,
        widgetOpenAppButtonEnabled = widgetOpenAppButtonEnabled,
        onAppThemeModeChange = onAppThemeModeChange,
        onDynamicColorsEnabledChange = onDynamicColorsEnabledChange,
        onUseSystemFontChange = onUseSystemFontChange,
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
        onOpenAppLock = onOpenAppLock,
        onOpenBackup = onOpenBackup,
        onOpenCorrections = onOpenCorrections,
        onOpenDataHealth = onOpenDataHealth,
        onOpenPrivacy = onOpenPrivacy,
        onOpenAbout = onOpenAbout,
        appVersion = appVersion,
        onOpenCredoSync = onOpenCredoSync,
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

@Composable
internal fun SettingsContent(
    status: SettingsStatus = SettingsStatus(),
    appThemeMode: AppThemeMode = AppThemeMode.System,
    dynamicColorsEnabled: Boolean = false,
    useSystemFont: Boolean = false,
    quickExpenseKeypadEnabled: Boolean = true,
    widgetOpenAppButtonEnabled: Boolean = true,
    onAppThemeModeChange: (AppThemeMode) -> Unit = {},
    onDynamicColorsEnabledChange: (Boolean) -> Unit = {},
    onUseSystemFontChange: (Boolean) -> Unit = {},
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
    onOpenAppLock: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenCorrections: () -> Unit = {},
    onOpenDataHealth: () -> Unit = {},
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    appVersion: String,
    onOpenCredoSync: () -> Unit = {},
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
) {
    var confirmDemoReset by rememberSaveable { mutableStateOf(false) }
    var showDemoEntry by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val smsProblem = smsImportEnabled && (!hasSmsPermission || !hasSmsCardMapping)
    val sections = buildSettingsSections(
        status = status,
        appLockTimeout = appLockTimeout,
        appVersion = appVersion,
        smsImportEnabled = smsImportEnabled,
        hasSmsPermission = hasSmsPermission,
        hasSmsCardMapping = hasSmsCardMapping,
        dynamicColorsEnabled = dynamicColorsEnabled,
        useSystemFont = useSystemFont,
        quickExpenseKeypadEnabled = quickExpenseKeypadEnabled,
        widgetOpenAppButtonEnabled = widgetOpenAppButtonEnabled,
        demoMode = demoMode,
        onOpenCredoSync = onOpenCredoSync,
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
        onQuickExpenseKeypadEnabledChange = onQuickExpenseKeypadEnabledChange,
        onWidgetOpenAppButtonEnabledChange = onWidgetOpenAppButtonEnabledChange,
        onOpenAbout = onOpenAbout,
        onOpenDemoEntry = { showDemoEntry = true },
        onResetDemo = { confirmDemoReset = true },
    )
    val visible = remember(sections, query) { filterSettings(sections, query) }
    val searching = query.isNotBlank()

    // The search stays put while the catalogue scrolls under it: a field that scrolls away is a
    // field you have to go back for, and this screen is long by nature.
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        WhfinField(
            value = query,
            onValueChange = { query = it },
            label = null,
            leadingIcon = Icons.Default.Search,
            placeholder = stringResource(R.string.settings_search_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("settings-search"),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        if (visible.isEmpty()) {
            Text(
                stringResource(R.string.settings_search_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        visible.forEach { section ->
            WhfinSectionLabel(section.label)
            // The theme choice is the control itself, so it stands outside the group of rows it
            // belongs to rather than pretending to be a door.
            val themeRow = section.rows.firstOrNull { it.id == ROW_THEME }
            if (themeRow != null) ThemeChoice(appThemeMode, onAppThemeModeChange)
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

        if (developerMode && !searching) {
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
    WhfinLedgerRow(
        title = row.title,
        supportingText = row.summary,
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
                        onCheckedChange = control.onCheckedChange,
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
        onClick = row.onClick?.takeIf { row.enabled && control !is SettingsControl.Toggle },
        divider = divider,
    )
}

/**
 * Three mutually exclusive answers divide one row between them: as three rows with a sentence each,
 * picking a theme filled a screen and a half to say what three words say.
 */
@Composable
private fun ThemeChoice(mode: AppThemeMode, onChange: (AppThemeMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            AppThemeMode.System to R.string.settings_theme_system,
            AppThemeMode.Light to R.string.settings_theme_light,
            AppThemeMode.Dark to R.string.settings_theme_dark,
        ).forEach { (option, label) ->
            WhfinFilterPill(
                label = stringResource(label),
                selected = mode == option,
                onClick = { onChange(option) },
                modifier = Modifier.weight(1f),
                centered = true,
            )
        }
    }
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
    appLockTimeout: AppLockTimeout,
    appVersion: String,
    smsImportEnabled: Boolean,
    hasSmsPermission: Boolean,
    hasSmsCardMapping: Boolean,
    dynamicColorsEnabled: Boolean,
    useSystemFont: Boolean,
    quickExpenseKeypadEnabled: Boolean,
    widgetOpenAppButtonEnabled: Boolean,
    demoMode: Boolean,
    onOpenCredoSync: () -> Unit,
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
                title = stringResource(R.string.credo_sync_title),
                summary = when {
                    demoMode -> demoUnavailable
                    status.lastCredoSyncAt != null ->
                        stringResource(R.string.settings_credo_synced, relativeDay(status.lastCredoSyncAt))
                    else -> stringResource(R.string.settings_credo_never)
                },
                keywords = stringResource(R.string.settings_keywords_credo),
                icon = Icons.Default.CloudSync,
                enabled = !demoMode,
                onClick = onOpenCredoSync,
            ),
            SettingsRow(
                id = "statements",
                title = stringResource(R.string.statements_title),
                summary = status.lastStatementImportAt
                    ?.let { stringResource(R.string.settings_statements_last, relativeDay(it)) }
                    ?: stringResource(R.string.statements_settings_summary),
                keywords = stringResource(R.string.settings_keywords_statements),
                icon = Icons.Default.Description,
                onClick = onOpenStatements,
            ),
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
                icon = Icons.Default.Sms,
                enabled = !demoMode,
                onClick = onOpenSmsDiagnostics,
            ),
            // The demo workspace has no live messages to read, so the switch that would read them
            // is absent rather than present and dead.
            if (demoMode) null else SettingsRow(
                id = "sms-toggle",
                title = stringResource(R.string.settings_sms_toggle),
                summary = stringResource(R.string.settings_sms_toggle_body),
                keywords = stringResource(R.string.settings_keywords_sms),
                control = SettingsControl.Toggle(
                    checked = smsImportEnabled,
                    contentDescription = stringResource(R.string.settings_sms_toggle),
                    onCheckedChange = onSmsImportEnabledChange,
                ),
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
                icon = Icons.Default.Category,
                onClick = onOpenCategories,
            ),
            SettingsRow(
                id = "category-intelligence",
                title = stringResource(R.string.category_intelligence_title),
                summary = stringResource(R.string.category_intelligence_settings_summary),
                keywords = stringResource(R.string.settings_keywords_category_intelligence),
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
                summary = stringResource(
                    R.string.app_lock_settings_summary,
                    stringResource(appLockTimeout.labelResource()),
                ),
                keywords = stringResource(R.string.settings_keywords_app_lock),
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
                icon = Icons.Default.SaveAlt,
                enabled = !demoMode,
                onClick = onOpenBackup,
            ),
            SettingsRow(
                id = "data-health",
                title = stringResource(R.string.data_health_title),
                summary = if (status.integrityIssues > 0) {
                    pluralStringResource(
                        R.plurals.settings_data_health_issues,
                        status.integrityIssues,
                        status.integrityIssues,
                    )
                } else {
                    stringResource(R.string.settings_data_health_clean)
                },
                keywords = stringResource(R.string.settings_keywords_data_health),
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
            SettingsRow(
                id = "privacy",
                title = stringResource(R.string.privacy_title),
                summary = stringResource(R.string.privacy_settings_summary),
                keywords = stringResource(R.string.settings_keywords_privacy),
                icon = Icons.Default.PrivacyTip,
                onClick = onOpenPrivacy,
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
                id = "quick-keypad",
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
                icon = Icons.Default.Info,
                onClick = onOpenAbout,
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
            DateUtils.HOUR_IN_MILLIS,
        ).toString()
    }
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
