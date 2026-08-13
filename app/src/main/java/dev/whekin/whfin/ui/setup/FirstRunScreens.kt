package dev.whekin.whfin.ui.setup

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.whekin.whfin.R
import dev.whekin.whfin.core.ui.WhfinActionStyle
import dev.whekin.whfin.core.ui.WhfinBackButton
import dev.whekin.whfin.core.ui.WhfinButton
import dev.whekin.whfin.core.ui.WhfinLedgerGroup
import dev.whekin.whfin.core.ui.WhfinLedgerRow
import dev.whekin.whfin.core.ui.WhfinNotice
import dev.whekin.whfin.core.ui.WhfinNoticeKind
import dev.whekin.whfin.core.ui.WhfinSectionLabel
import dev.whekin.whfin.ui.theme.WhfinTheme

@Composable
fun WelcomeChoiceScreen(
    busy: Boolean,
    problem: String?,
    onSetUpPersonal: () -> Unit,
    onExploreDemo: () -> Unit,
    onExit: () -> Unit,
) {
    BackHandler(onBack = onExit)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                WhfinSectionLabel(stringResource(R.string.app_name))
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.welcome_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        stringResource(R.string.welcome_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WhfinLedgerGroup(Modifier.fillMaxWidth(), tonal = true) {
                    WelcomeRow(
                        title = stringResource(R.string.welcome_step_sms_title),
                        body = stringResource(R.string.welcome_step_sms_body),
                        icon = Icons.Default.Sms,
                        divider = true,
                    )
                    WelcomeRow(
                        title = stringResource(R.string.welcome_step_sync_title),
                        body = stringResource(R.string.welcome_step_sync_body),
                        icon = Icons.Default.CloudSync,
                        divider = true,
                    )
                    WelcomeRow(
                        title = stringResource(R.string.welcome_step_review_title),
                        body = stringResource(R.string.welcome_step_review_body),
                        icon = Icons.Default.CheckCircle,
                    )
                }
                if (problem != null) {
                    WhfinNotice(
                        title = stringResource(R.string.demo_mode_problem_title),
                        body = problem,
                        kind = WhfinNoticeKind.Error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Column(
                Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                WhfinButton(
                    label = stringResource(R.string.welcome_personal_action),
                    onClick = onSetUpPersonal,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = !busy,
                    leadingIcon = Icons.Default.Wallet,
                )
                WhfinButton(
                    label = stringResource(R.string.welcome_demo_action),
                    onClick = onExploreDemo,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    style = WhfinActionStyle.Secondary,
                    leadingIcon = Icons.Default.Visibility,
                )
            }
        }
    }
}

@Composable
private fun WelcomeRow(
    title: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    divider: Boolean = false,
) {
    WhfinLedgerRow(
        title = title,
        supportingText = body,
        supportingMaxLines = 3,
        icon = icon,
        divider = divider,
    )
}

data class PersonalSetupState(
    val accountCount: Int? = null,
    val bankLedgerCount: Int? = null,
    val hasCredoImport: Boolean? = null,
    val smsMonitoringEnabled: Boolean = false,
    val hasSmsPermission: Boolean = false,
    val canRequestSmsPermission: Boolean = true,
    val unresolvedSmsCount: Int? = null,
)

@Composable
fun PersonalSetupScreen(
    state: PersonalSetupState,
    onConnectCredo: () -> Unit,
    onEnableSmsMonitoring: () -> Unit,
    onOpenBankSms: () -> Unit,
    onImportStatement: () -> Unit,
    onCreateAccount: () -> Unit,
    onRestoreBackup: () -> Unit,
    onContinue: () -> Unit,
    onExit: () -> Unit,
) {
    BackHandler(onBack = onExit)
    val credoReady = state.hasCredoImport == true
    val smsReady = state.smsMonitoringEnabled && state.hasSmsPermission
    val reviewReady = smsReady && credoReady && state.unresolvedSmsCount == 0
    var otherWaysExpanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.personal_setup_title),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    stringResource(R.string.personal_setup_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item("steps-label") {
                    WhfinSectionLabel(stringResource(R.string.personal_setup_steps_section))
                }
                item("steps") {
                    WhfinLedgerGroup(Modifier.fillMaxWidth(), tonal = true) {
                        SetupActionRow(
                            title = stringResource(R.string.personal_setup_sms_title),
                            status = when {
                                smsReady -> stringResource(R.string.personal_setup_enabled)
                                state.smsMonitoringEnabled -> stringResource(
                                    R.string.personal_setup_permission_needed,
                                )
                                else -> stringResource(R.string.personal_setup_sms_body)
                            },
                            icon = Icons.Default.Sms,
                            completed = smsReady,
                            divider = true,
                            onClick = if (smsReady) onOpenBankSms else onEnableSmsMonitoring,
                        )
                        SetupActionRow(
                            title = stringResource(R.string.personal_setup_connect_title),
                            status = when (state.hasCredoImport) {
                                null -> stringResource(R.string.personal_setup_checking)
                                true -> stringResource(
                                    R.string.personal_setup_ledgers_ready,
                                    state.bankLedgerCount ?: 0,
                                )
                                false -> stringResource(R.string.personal_setup_recommended)
                            },
                            icon = Icons.Default.CloudSync,
                            completed = credoReady,
                            divider = true,
                            onClick = onConnectCredo,
                        )
                        SetupActionRow(
                            title = stringResource(R.string.personal_setup_review_title),
                            status = when (state.unresolvedSmsCount) {
                                null -> stringResource(R.string.personal_setup_checking)
                                0 -> if (smsReady && credoReady) {
                                    stringResource(R.string.personal_setup_review_clear)
                                } else {
                                    stringResource(R.string.personal_setup_review_waiting)
                                }
                                else -> stringResource(R.string.personal_setup_review_count, state.unresolvedSmsCount)
                            },
                            icon = Icons.Default.CheckCircle,
                            completed = reviewReady,
                            onClick = onOpenBankSms,
                        )
                    }
                }
                item("accounts-label") {
                    WhfinSectionLabel(stringResource(R.string.personal_setup_accounts_section))
                }
                item("accounts") {
                    WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                        SetupActionRow(
                            title = stringResource(R.string.personal_setup_manual_title),
                            status = when (state.accountCount) {
                                null -> stringResource(R.string.personal_setup_checking)
                                0 -> stringResource(R.string.personal_setup_manual_body)
                                else -> pluralStringResource(
                                    R.plurals.personal_setup_accounts_ready,
                                    state.accountCount,
                                    state.accountCount,
                                )
                            },
                            icon = Icons.Default.Wallet,
                            onClick = onCreateAccount,
                        )
                    }
                }
                item("other-label") {
                    WhfinSectionLabel(stringResource(R.string.personal_setup_other_section))
                }
                item("other-toggle") {
                    WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                        WhfinLedgerRow(
                            title = stringResource(R.string.personal_setup_other_title),
                            supportingText = stringResource(R.string.personal_setup_other_body),
                            supportingMaxLines = 3,
                            icon = Icons.AutoMirrored.Filled.FactCheck,
                            trailing = {
                                Icon(
                                    if (otherWaysExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = { otherWaysExpanded = !otherWaysExpanded },
                        )
                    }
                }
                if (otherWaysExpanded) item("other") {
                    WhfinLedgerGroup(Modifier.fillMaxWidth()) {
                        SetupActionRow(
                            title = stringResource(R.string.personal_setup_statement_title),
                            status = stringResource(R.string.personal_setup_statement_body),
                            icon = Icons.AutoMirrored.Filled.FactCheck,
                            divider = true,
                            onClick = onImportStatement,
                        )
                        SetupActionRow(
                            title = stringResource(R.string.personal_setup_restore_title),
                            status = stringResource(R.string.personal_setup_restore_body),
                            icon = Icons.Default.Restore,
                            onClick = onRestoreBackup,
                        )
                    }
                }
                item("later") {
                    Text(
                        stringResource(R.string.personal_setup_later_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                when {
                    !smsReady -> WhfinButton(
                        label = stringResource(R.string.personal_setup_enable_sms_action),
                        onClick = onEnableSmsMonitoring,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        leadingIcon = Icons.Default.Sms,
                    )
                    !credoReady -> WhfinButton(
                        label = stringResource(R.string.personal_setup_connect_and_sync_action),
                        onClick = onConnectCredo,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        leadingIcon = Icons.Default.CloudSync,
                    )
                    state.unresolvedSmsCount == null -> WhfinButton(
                        label = stringResource(R.string.personal_setup_checking),
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        enabled = false,
                    )
                    state.unresolvedSmsCount > 0 -> WhfinButton(
                        label = stringResource(
                            R.string.personal_setup_review_action,
                            state.unresolvedSmsCount,
                        ),
                        onClick = onOpenBankSms,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        leadingIcon = Icons.Default.CheckCircle,
                    )
                    else -> WhfinButton(
                        label = stringResource(R.string.personal_setup_continue_action),
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                if (!reviewReady) {
                    WhfinButton(
                        label = stringResource(R.string.personal_setup_skip_action),
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                        style = WhfinActionStyle.Quiet,
                    )
                }
            }
        }
    }
}

@Composable
fun PersonalSetupSecondaryPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onBack)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WhfinBackButton(stringResource(R.string.action_back), onBack)
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
            }
            content()
        }
    }
}

@Composable
private fun SetupActionRow(
    title: String,
    status: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    completed: Boolean = false,
    divider: Boolean = false,
    onClick: () -> Unit,
) {
    WhfinLedgerRow(
        title = title,
        supportingText = status,
        supportingMaxLines = 3,
        icon = icon,
        iconTint = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        divider = divider,
        onClick = onClick,
    )
}

@Preview(name = "Welcome light", widthDp = 400, heightDp = 850, showBackground = true)
@Preview(name = "Welcome dark", widthDp = 400, heightDp = 850, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Welcome font 1.5", widthDp = 400, heightDp = 950, fontScale = 1.5f)
@Preview(name = "Welcome compact", widthDp = 400, heightDp = 560)
@Composable
private fun WelcomeChoicePreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            WelcomeChoiceScreen(false, null, {}, {}, {})
        }
    }
}

@Preview(name = "Personal setup light", widthDp = 400, heightDp = 850, showBackground = true)
@Preview(name = "Personal setup dark", widthDp = 400, heightDp = 850, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Personal setup font 1.5", widthDp = 400, heightDp = 950, fontScale = 1.5f)
@Preview(name = "Personal setup compact", widthDp = 400, heightDp = 560)
@Composable
private fun PersonalSetupPreview() {
    WhfinTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            PersonalSetupScreen(
                state = PersonalSetupState(
                    accountCount = 2,
                    bankLedgerCount = 2,
                    hasCredoImport = false,
                    unresolvedSmsCount = 0,
                ),
                onConnectCredo = {},
                onEnableSmsMonitoring = {},
                onOpenBankSms = {},
                onImportStatement = {},
                onCreateAccount = {},
                onRestoreBackup = {},
                onContinue = {},
                onExit = {},
            )
        }
    }
}
